package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.route.BankStop;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.VaultBanking;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class RunSessionTest
{
	private static SupplyLine line(SupplyLine.Group group, int need, int carried, int leprechaun)
	{
		final Map<Holdings.Source, Integer> where = new EnumMap<>(Holdings.Source.class);
		if (carried > 0)
		{
			where.put(Holdings.Source.INVENTORY, carried);
		}
		if (leprechaun > 0)
		{
			where.put(Holdings.Source.LEPRECHAUN, leprechaun);
		}
		return new SupplyLine(group, "x", need, carried + leprechaun, carried, where, null, 1, false, null,
			new int[]{1});
	}

	private static RunPlan plan(List<SupplyLine> lines)
	{
		final Route route = new Route(Collections.singletonList(
			new RouteStop(Location.CATHERBY, null, Departure.WALK, 10, false)), RouteMode.AUTOPILOT, 10, 20);
		final SupplyPlan supplies = new SupplyPlan(lines, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), 0, "", 0, Collections.emptyMap());
		return new RunPlan(RunSelection.EMPTY, route, supplies, Collections.emptyMap());
	}

	private static final int RANARR = 1;
	private static final int SNAPDRAGON = 2;

	/** A seed line, with some carried and some in the seed vault. */
	private static SupplyLine seeds(int id, int need, int carried, int vault)
	{
		final Map<Holdings.Source, Integer> where = new EnumMap<>(Holdings.Source.class);
		if (carried > 0)
		{
			where.put(Holdings.Source.INVENTORY, carried);
		}
		if (vault > 0)
		{
			where.put(Holdings.Source.SEED_VAULT, vault);
		}
		return new SupplyLine(SupplyLine.Group.SEEDS, "x", need, carried + vault, carried, where, null, 1, false, null,
			new int[]{id}, true);
	}

	private static SupplyPlan part(SupplyLine... lines)
	{
		return new SupplyPlan(Arrays.asList(lines), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), 0, "", 0, Collections.emptyMap());
	}

	/** A run that banks once, after its first stop. */
	private static RunPlan banked(SupplyPlan first, SupplyPlan later)
	{
		final Route route = new Route(Arrays.asList(new RouteStop(Location.CATHERBY, null, Departure.WALK, 10, false),
			new RouteStop(Location.FARMING_GUILD, null, Departure.WALK, 10, false)), RouteMode.AUTOPILOT, 20, 40);
		return new RunPlan(RunSelection.EMPTY, route, first, Collections.emptyMap(),
			Collections.singletonList(new BankStop(0, Location.CATHERBY, later, null)));
	}

	private static Holdings carrying(int id, int count)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.INVENTORY, Collections.singletonMap(id, count));
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	@Test
	public void takingAllSeedsAtTheStartIsOnlyForRunsThatBankPartway()
	{
		final RunConfig config = new RunConfig();
		final RunPlan banked = banked(part(seeds(RANARR, 6, 0, 12)), part(seeds(RANARR, 6, 0, 6)));
		assertTrue(RunSession.takesAllAtStart(banked, config));
		assertFalse(RunSession.takesAllAtStart(plan(Collections.singletonList(seeds(RANARR, 6, 0, 12))), config));
		config.setVaultBanking(VaultBanking.BANK_AT_GUILD);
		assertFalse(RunSession.takesAllAtStart(banked, config));
		config.setVaultBanking(VaultBanking.TAKE_ALL_AT_START);
		config.setUseSeedVault(false);
		assertFalse(RunSession.takesAllAtStart(banked, config));
	}

	@Test
	public void theLaterPartsSeedsOnlyInTheVaultAddUpByItem()
	{
		// The first part plants 6 of the 12 in the vault; the later part needs the other 6, and 3 snapdragon
		final RunPlan run = banked(part(seeds(RANARR, 6, 0, 12)),
			part(seeds(RANARR, 6, 0, 6), seeds(SNAPDRAGON, 3, 0, 3)));
		final Map<Integer, Integer> later = RunSession.laterFromVault(run);
		assertEquals(Integer.valueOf(6), later.get(RANARR));
		assertEquals(Integer.valueOf(3), later.get(SNAPDRAGON));
		// Once they're carried, nothing's left to take
		assertTrue(RunSession.laterFromVault(banked(part(seeds(RANARR, 6, 12, 0)), part(seeds(RANARR, 6, 6, 0))))
			.isEmpty());
	}

	@Test
	public void theSeedsForLaterGoInTheBankBeforeTheRunStarts()
	{
		final RunPlan run = banked(part(seeds(RANARR, 6, 0, 0)), part(seeds(RANARR, 6, 0, 0)));
		// All 12 carried (from the vault or not, elzerma 2026-10-18): the 6 for later go in the bank
		assertEquals(Collections.singletonMap(RANARR, 6), RunSession.toBankForLater(run, carrying(RANARR, 12)));
		// Put away: the first part's 6 are kept
		assertTrue(RunSession.toBankForLater(run, carrying(RANARR, 6)).isEmpty());
		// Everything beyond the first part's, in one go
		assertEquals(Collections.singletonMap(RANARR, 14), RunSession.toBankForLater(run, carrying(RANARR, 20)));
	}

	@Test
	public void aSeedOnlyTheLaterPartsPlantGoesInTheBankWhole()
	{
		final RunPlan run = banked(part(seeds(RANARR, 6, 6, 0)), part(seeds(SNAPDRAGON, 3, 0, 0)));
		assertEquals(Collections.singletonMap(SNAPDRAGON, 3), RunSession.toBankForLater(run, carrying(SNAPDRAGON, 3)));
		// A seed no later part plants is left alone at the start
		assertTrue(RunSession.toBankForLater(run, carrying(RANARR, 20)).isEmpty());
	}

	@Test
	public void readyWhenCarriedOrAtTheLeprechaun()
	{
		assertTrue(RunSession.isReady(plan(java.util.Arrays.asList(
			line(SupplyLine.Group.SEEDS, 8, 8, 0),
			line(SupplyLine.Group.TOOLS, 8, 0, 128)))));
	}

	@Test
	public void notReadyWhenSomethingIsStillInTheBank()
	{
		assertFalse(RunSession.isReady(plan(Collections.singletonList(line(SupplyLine.Group.SEEDS, 8, 3, 0)))));
	}

	@Test
	public void optionalItemsDontBlock()
	{
		assertTrue(RunSession.isReady(plan(java.util.Arrays.asList(
			line(SupplyLine.Group.SEEDS, 1, 1, 0),
			line(SupplyLine.Group.OPTIONAL, 4, 0, 0)))));
	}

	@Test
	public void harvestSpaceForHerbsAndFruit()
	{
		final PatchPrediction fruit = new PatchPrediction(Patch.CATHERBY_FRUIT_TREE, Crop.PAPAYA,
			PatchState.HARVESTABLE, 4, 7, 0, 0, 0, PatchPrediction.Source.THIS_PLUGIN);
		assertEquals(4, PatchGuide.expectedHarvest(Patch.CATHERBY_FRUIT_TREE, StepAdvisor.Action.PICK, fruit));
		assertEquals(6, PatchGuide.expectedHarvest(Patch.CATHERBY_FRUIT_TREE, StepAdvisor.Action.CHECK_HEALTH, null));
		assertEquals(8, PatchGuide.expectedHarvest(Patch.CATHERBY_HERB, StepAdvisor.Action.PICK, null));
		assertEquals(0, PatchGuide.expectedHarvest(Patch.CATHERBY_HERB, StepAdvisor.Action.PLANT, null));
		assertEquals(0, PatchGuide.expectedHarvest(Patch.TAVERLEY_TREE, StepAdvisor.Action.CHECK_HEALTH, null));
	}

	@Test
	public void notReadyWithNoRoute()
	{
		assertFalse(RunSession.isReady(RunPlan.EMPTY));
	}

	@Test
	public void countsOnlyRequiredLinesStillInTheBank()
	{
		assertEquals(2, RunSession.missingCount(plan(java.util.Arrays.asList(
			line(SupplyLine.Group.SEEDS, 8, 3, 0),
			line(SupplyLine.Group.TRAVEL, 1, 0, 0),
			line(SupplyLine.Group.TOOLS, 1, 0, 1),
			line(SupplyLine.Group.OPTIONAL, 4, 0, 0)))));
	}

	@Test
	public void teleportIsALongJumpInOneTick()
	{
		final WorldPoint bank = new WorldPoint(3092, 3245, 0);
		assertFalse(RunSession.isTeleport(bank, new WorldPoint(3094, 3245, 0)));
		assertFalse(RunSession.isTeleport(null, bank));
		assertTrue(RunSession.isTeleport(bank, new WorldPoint(2813, 3463, 0)));
	}
}
