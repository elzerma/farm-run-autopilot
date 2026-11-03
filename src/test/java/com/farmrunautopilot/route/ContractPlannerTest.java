package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.FarmingContract;
import com.farmrunautopilot.data.FarmingContract.Tier;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.route.ContractPlanner.Jane;
import com.farmrunautopilot.route.ContractPlanner.Plan;
import com.farmrunautopilot.route.ContractPlanner.Role;
import com.farmrunautopilot.tracking.ContractTracker.Contract;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Contracts in runs (docs/plans/farming-contracts.md; elzerma, 2026-11-02): pre-plants left alone until a contract asks for
 * them, the contract's crop taken once grown, planted where it's missing, and Guildmaster Jane at the end of the stop.
 */
public class ContractPlannerTest
{
	private static final List<Patch> GUILD = FarmingContract.PATCHES;
	private final Map<Patch, PatchPrediction> held = new EnumMap<>(Patch.class);

	private void grow(Patch patch, Crop crop, PatchState state)
	{
		held.put(patch, new PatchPrediction(patch, crop, state, 0, crop != null ? crop.getStages() : 0, 0, 0, 0,
			PatchPrediction.Source.THIS_PLUGIN));
	}

	private Plan plan(Contract contract, int farming)
	{
		return ContractPlanner.plan(contract, farming, GUILD, held::get);
	}

	private static Contract contract(Crop crop, int level)
	{
		for (int value = 1; value <= 51; value++)
		{
			if (FarmingContract.crop(value) == crop)
			{
				return new Contract(value, crop, 0, level, 0);
			}
		}
		throw new IllegalArgumentException(crop.name());
	}

	/** The wiki's Hard pre-plants, all waiting. */
	private void prePlanted()
	{
		grow(Patch.FARMING_GUILD_HERB, Crop.SNAPDRAGON, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_TREE, Crop.MAPLE, PatchState.CHECK_HEALTH);
		grow(Patch.FARMING_GUILD_FRUIT_TREE, Crop.PALM, PatchState.CHECK_HEALTH);
		grow(Patch.FARMING_GUILD_CELASTRUS, Crop.CELASTRUS, PatchState.CHECK_HEALTH);
		grow(Patch.FARMING_GUILD_REDWOOD, Crop.REDWOOD, PatchState.GROWING);
		grow(Patch.FARMING_GUILD_BUSH, Crop.POISON_IVY, PatchState.CHECK_HEALTH);
		grow(Patch.FARMING_GUILD_CACTUS, Crop.POTATO_CACTUS, PatchState.CHECK_HEALTH);
		grow(Patch.FARMING_GUILD_FLOWER, Crop.WHITE_LILY, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_ALLOTMENT_N, Crop.WATERMELON, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_ALLOTMENT_S, Crop.SNAPE_GRASS, PatchState.HARVESTABLE);
	}

	@Test
	public void theContractsCropIsTakenAndTheRestLeftAlone()
	{
		prePlanted();
		final Plan plan = plan(contract(Crop.MAPLE, 3), 90);
		assertEquals(Role.TAKE, plan.role(Patch.FARMING_GUILD_TREE));
		for (Patch patch : GUILD)
		{
			if (patch != Patch.FARMING_GUILD_TREE)
			{
				assertEquals(patch.name(), Role.LEAVE, plan.role(patch));
			}
		}
		assertEquals(Jane.NONE, plan.getJane());
		assertEquals("Contract: Maple is grown: checked at the guild this run", plan.getLine());
	}

	@Test
	public void aHarvestCropIsTakenOnceReadyToPick()
	{
		prePlanted();
		final Plan plan = plan(contract(Crop.WATERMELON, 3), 90);
		assertEquals(Role.TAKE, plan.role(Patch.FARMING_GUILD_ALLOTMENT_N));
		assertEquals(Role.LEAVE, plan.role(Patch.FARMING_GUILD_ALLOTMENT_S));
		assertEquals("Contract: Watermelon is grown: picked at the guild this run", plan.getLine());
	}

	@Test
	public void janeHandsInTakesAnotherOrGivesOne()
	{
		prePlanted();
		final Plan done = plan(new Contract(9, Crop.MAPLE, 1, 3, 5), 90);
		assertEquals(Jane.HAND_IN, done.getJane());
		assertEquals(Role.LEAVE, done.role(Patch.FARMING_GUILD_TREE));
		// Handed in, none taken since (COMPLETE 2, TYPE still the maple's, as elzerma's dev check saw it): ask for one,
		// the maple left alone
		final Plan handedIn = plan(new Contract(9, Crop.MAPLE, 2, 3, 6), 90);
		assertEquals(Jane.NEW, handedIn.getJane());
		assertEquals(Role.LEAVE, handedIn.role(Patch.FARMING_GUILD_TREE));
		assertEquals("No farming contract: ask Guildmaster Jane for a Hard one", handedIn.getLine());
		final Plan none = plan(Contract.NONE, 90);
		assertEquals(Jane.NEW, none.getJane());
		assertEquals(Tier.HARD, none.getAsk());
		assertEquals("No farming contract: ask Guildmaster Jane for a Hard one", none.getLine());
		assertEquals(Tier.MEDIUM, plan(Contract.NONE, 70).getAsk());
	}

	@Test
	public void emptyPatchesGetTheirPrePlantOrTheContractsCrop()
	{
		grow(Patch.FARMING_GUILD_HERB, null, PatchState.WEEDS);
		final Contract torstol = contract(Crop.TORSTOL, 3);
		assertEquals(Role.PLANT, plan(torstol, 90).role(Patch.FARMING_GUILD_HERB));
		assertEquals(Crop.TORSTOL, crop(Patch.FARMING_GUILD_HERB, torstol, 90));
		// No contract for herbs: the Hard table's first choice the player has seeds for, else its first
		final Contract maple = contract(Crop.MAPLE, 3);
		assertEquals(Crop.LANTADYME, ContractPlanner.crop(Patch.FARMING_GUILD_HERB, maple, 90, GUILD, held::get,
			c -> c == Crop.LANTADYME ? 4 : 0, type -> null));
		assertEquals(Crop.CADANTINE, crop(Patch.FARMING_GUILD_HERB, maple, 90));
		// Below 85, the Medium table
		assertEquals(Crop.IRIT, crop(Patch.FARMING_GUILD_HERB, maple, 70));
	}

	@Test
	public void aQuickBlockedContractClearsAPatchForIt()
	{
		prePlanted();
		final Contract torstol = contract(Crop.TORSTOL, 3);
		final Plan plan = plan(torstol, 90);
		assertEquals(Role.CLEAR_FOR_CONTRACT, plan.role(Patch.FARMING_GUILD_HERB));
		assertEquals(Jane.NONE, plan.getJane());
		assertEquals(Crop.TORSTOL, crop(Patch.FARMING_GUILD_HERB, torstol, 90));
	}

	@Test
	public void aSlowBlockedContractIsSwappedUnlessEasy()
	{
		prePlanted();
		grow(Patch.FARMING_GUILD_TREE, Crop.YEW, PatchState.GROWING);
		final Plan hard = plan(contract(Crop.MAGIC, 3), 90);
		assertEquals(Role.LEAVE, hard.role(Patch.FARMING_GUILD_TREE));
		assertEquals(Jane.EASIER, hard.getJane());
		assertTrue(hard.getLine(), hard.getLine().startsWith("Contract: Magic (8 h). Its patch holds another crop"));
		// Easy can't be swapped: the yew makes way
		assertEquals(Role.CLEAR_FOR_CONTRACT, plan(contract(Crop.MAPLE, 1), 90).role(Patch.FARMING_GUILD_TREE));
		// An oak contract is only ever Easy, whatever the level varbit holds
		assertEquals(Role.CLEAR_FOR_CONTRACT, plan(contract(Crop.OAK, 0), 90).role(Patch.FARMING_GUILD_TREE));
	}

	@Test
	public void spentAndDiseasedPatches()
	{
		prePlanted();
		// A tree whose health is checked can't count any more: cleared and replanted
		grow(Patch.FARMING_GUILD_TREE, Crop.MAPLE, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_FRUIT_TREE, Crop.PALM, PatchState.DISEASED);
		final Plan plan = plan(contract(Crop.TORSTOL, 3), 90);
		assertEquals(Role.PLANT, plan.role(Patch.FARMING_GUILD_TREE));
		assertEquals(Role.TAKE, plan.role(Patch.FARMING_GUILD_FRUIT_TREE));
		// The contract's crop still growing: left, and said so
		grow(Patch.FARMING_GUILD_HERB, Crop.TORSTOL, PatchState.GROWING);
		final Plan growing = plan(contract(Crop.TORSTOL, 3), 90);
		assertEquals(Role.LEAVE, growing.role(Patch.FARMING_GUILD_HERB));
		assertEquals("Contract: Torstol, growing at the guild", growing.getLine());
	}

	/**
	 * elzerma's guild on 2026-11-02 at Farming 69, the Medium table (watermelon in the north allotment, strawberries in the
	 * south, white lilies in the flower patch): "We just want to pick anything that isn't in alignment with the
	 * pre-planting plan".
	 */
	@Test
	public void cropsOffThePlanArePickedOnceGrownAndThePatchPlantedToPlan()
	{
		grow(Patch.FARMING_GUILD_ALLOTMENT_N, Crop.WATERMELON, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_ALLOTMENT_S, Crop.WATERMELON, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_FLOWER, Crop.NASTURTIUM, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_HERB, Crop.RANARR, PatchState.GROWING);
		final Contract jangerberry = contract(Crop.JANGERBERRY, 2);
		final Plan plan = plan(jangerberry, 69);
		assertEquals(Role.LEAVE, plan.role(Patch.FARMING_GUILD_ALLOTMENT_N));
		assertEquals(Role.TAKE, plan.role(Patch.FARMING_GUILD_ALLOTMENT_S));
		assertEquals(Role.TAKE, plan.role(Patch.FARMING_GUILD_FLOWER));
		assertEquals(Crop.STRAWBERRY, crop(Patch.FARMING_GUILD_ALLOTMENT_S, jangerberry, 69));
		assertEquals(Crop.WHITE_LILY, crop(Patch.FARMING_GUILD_FLOWER, jangerberry, 69));
		// Picked once grown, not dug up
		assertEquals(Role.LEAVE, plan.role(Patch.FARMING_GUILD_HERB));
		// Any of the table's choices is on the plan: at 85 the Hard table, lantadyme the second of its herbs
		grow(Patch.FARMING_GUILD_HERB, Crop.LANTADYME, PatchState.HARVESTABLE);
		assertEquals(Role.LEAVE, plan(jangerberry, 90).role(Patch.FARMING_GUILD_HERB));
		assertEquals(Role.TAKE, plan(jangerberry, 69).role(Patch.FARMING_GUILD_HERB));
	}

	@Test
	public void aContractsCropGoesWhereACropOffThePlanMakesWay()
	{
		grow(Patch.FARMING_GUILD_ALLOTMENT_N, Crop.WATERMELON, PatchState.HARVESTABLE);
		grow(Patch.FARMING_GUILD_ALLOTMENT_S, Crop.WATERMELON, PatchState.GROWING);
		// A quick crop with no room: the south allotment's watermelons, off the plan, make way, not the north's pre-plant
		final Contract strawberry = contract(Crop.STRAWBERRY, 2);
		final Plan strawberries = plan(strawberry, 69);
		assertEquals(Role.LEAVE, strawberries.role(Patch.FARMING_GUILD_ALLOTMENT_N));
		assertEquals(Role.CLEAR_FOR_CONTRACT, strawberries.role(Patch.FARMING_GUILD_ALLOTMENT_S));
		assertEquals(Crop.STRAWBERRY, crop(Patch.FARMING_GUILD_ALLOTMENT_S, strawberry, 69));
		// A slow one where a grown crop off the plan is picked this run: planted there, with no easier contract to ask for
		grow(Patch.FARMING_GUILD_TREE, Crop.WILLOW, PatchState.CHECK_HEALTH);
		final Contract yew = contract(Crop.YEW, 2);
		final Plan yews = plan(yew, 69);
		assertEquals(Role.TAKE, yews.role(Patch.FARMING_GUILD_TREE));
		assertEquals(Jane.NONE, yews.getJane());
		assertEquals(Crop.YEW, crop(Patch.FARMING_GUILD_TREE, yew, 69));
	}

	@Test
	public void theTables()
	{
		assertEquals(Crop.REDBERRY, FarmingContract.crop(1));
		assertEquals(Crop.TORSTOL, FarmingContract.crop(33));
		assertEquals(Crop.WATERMELON, FarmingContract.crop(46));
		assertEquals(Crop.REDWOOD, FarmingContract.crop(50));
		assertEquals(Crop.CELASTRUS, FarmingContract.crop(51));
		assertNull(FarmingContract.crop(0));
		int easy = 0;
		int medium = 0;
		int hard = 0;
		for (Crop crop : Crop.values())
		{
			easy += FarmingContract.tiers(crop).contains(Tier.EASY) ? 1 : 0;
			medium += FarmingContract.tiers(crop).contains(Tier.MEDIUM) ? 1 : 0;
			hard += FarmingContract.tiers(crop).contains(Tier.HARD) ? 1 : 0;
		}
		assertEquals(31, easy);
		assertEquals(22, medium);
		assertEquals(18, hard);
		assertEquals("8 h", ContractPlanner.clock(480));
		assertEquals("1 h 20 min", ContractPlanner.clock(80));
		assertEquals("40 min", ContractPlanner.clock(40));
	}

	private Crop crop(Patch patch, Contract contract, int farming)
	{
		return ContractPlanner.crop(patch, contract, farming, GUILD, held::get, c -> 0, type -> null);
	}
}
