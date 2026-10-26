package com.farmrunautopilot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.run.StepAdvisor;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The special trees (docs/plans/crop-types.md, sprint 1c): their data, tables and rules. */
public class SpecialTreesDataTest
{
	private static void assertDecodes(PatchType type, int value, Crop crop, PatchState state, int stage)
	{
		final DecodedPatch d = PatchStateDecoder.decode(type, value);
		assertEquals(type + " " + value, crop, d.getCrop());
		assertEquals(type + " " + value, state, d.getState());
		assertEquals(type + " " + value, stage, d.getStage());
	}

	@Test
	public void decodersFollowRuneLite()
	{
		// Hardwood: teak growing, check-health, grown, stump, diseased, dead; rosewood at the end
		assertDecodes(PatchType.HARDWOOD, 8, Crop.TEAK, PatchState.GROWING, 0);
		assertDecodes(PatchType.HARDWOOD, 15, Crop.TEAK, PatchState.CHECK_HEALTH, 7);
		assertDecodes(PatchType.HARDWOOD, 16, Crop.TEAK, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.HARDWOOD, 17, Crop.TEAK, PatchState.STUMP, 0);
		assertDecodes(PatchType.HARDWOOD, 18, Crop.TEAK, PatchState.DISEASED, 1);
		assertDecodes(PatchType.HARDWOOD, 29, Crop.TEAK, PatchState.DEAD, 6);
		assertDecodes(PatchType.HARDWOOD, 38, Crop.MAHOGANY, PatchState.CHECK_HEALTH, 8);
		assertDecodes(PatchType.HARDWOOD, 114, Crop.ROSEWOOD, PatchState.CHECK_HEALTH, 9);
		assertDecodes(PatchType.HARDWOOD, 132, Crop.ROSEWOOD, PatchState.DEAD, 8);
		// Calquat: fruit picked from a checked tree, check-health last
		assertDecodes(PatchType.CALQUAT, 18, Crop.CALQUAT, PatchState.HARVESTABLE, 6);
		assertDecodes(PatchType.CALQUAT, 34, Crop.CALQUAT, PatchState.CHECK_HEALTH, 8);
		// Spirit tree: 20 is the grown tree, 44 check-health
		assertDecodes(PatchType.SPIRIT_TREE, 20, Crop.SPIRIT_TREE, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.SPIRIT_TREE, 44, Crop.SPIRIT_TREE, PatchState.CHECK_HEALTH, 12);
		// Redwood: grown at 18 and while chopped
		assertDecodes(PatchType.REDWOOD, 37, Crop.REDWOOD, PatchState.CHECK_HEALTH, 10);
		assertDecodes(PatchType.REDWOOD, 50, Crop.REDWOOD, PatchState.HARVESTABLE, 0);
		// Crystal tree: no diseased or dead values
		assertDecodes(PatchType.CRYSTAL_TREE, 14, Crop.CRYSTAL_TREE, PatchState.CHECK_HEALTH, 6);
		assertDecodes(PatchType.CRYSTAL_TREE, 15, Crop.CRYSTAL_TREE, PatchState.HARVESTABLE, 0);
		assertEquals(PatchState.UNKNOWN, PatchStateDecoder.decode(PatchType.CRYSTAL_TREE, 20).getState());
	}

	@Test
	public void growthMatchesThePlan()
	{
		assertEquals(4480, Crop.TEAK.getGrowthMinutes());
		assertEquals(5760, Crop.ROSEWOOD.getGrowthMinutes());
		assertEquals(1280, Crop.CALQUAT.getGrowthMinutes());
		assertEquals(800, Crop.CELASTRUS.getGrowthMinutes());
		assertEquals(6400, Crop.REDWOOD.getGrowthMinutes());
		assertEquals(3840, Crop.SPIRIT_TREE.getGrowthMinutes());
		assertEquals(480, Crop.CRYSTAL_TREE.getGrowthMinutes());
	}

	@Test
	public void saplingsDontStackAndPayments()
	{
		assertFalse(Crop.TEAK.isSeed());
		assertFalse(Crop.CRYSTAL_TREE.isSeed());
		assertTrue(Crop.REDBERRY.isSeed());
		assertFalse(Crop.CRYSTAL_TREE.hasPayment());
		assertEquals(5, Crop.SPIRIT_TREE.getPaymentQuantity());
		assertEquals(Integer.valueOf(1), Crop.SPIRIT_TREE.getExtraPayments().get(ItemID.MM_MONKEY_BAR));
		assertEquals(Integer.valueOf(1), Crop.SPIRIT_TREE.getExtraPayments().get(ItemID.LUNAR_GROUNDTOOTH));
		assertTrue(Crop.TEAK.getExtraPayments().isEmpty());
	}

	@Test
	public void spiritTreeLimitByLevel()
	{
		assertEquals(0, RunSelector.spiritTreeLimit(82));
		assertEquals(1, RunSelector.spiritTreeLimit(83));
		assertEquals(2, RunSelector.spiritTreeLimit(88));
		assertEquals(3, RunSelector.spiritTreeLimit(93));
		assertEquals(4, RunSelector.spiritTreeLimit(96));
		assertEquals(Integer.MAX_VALUE, RunSelector.spiritTreeLimit(99));
	}

	@Test
	public void clearingRules()
	{
		final RunConfig config = new RunConfig().sanitise();
		// Only Alexandra clears a redwood, for 2,000 coins; spirit trees are dug up and the crystal tree has no gardener
		assertTrue(config.paysToClear(PatchType.REDWOOD));
		assertFalse(config.paysToClear(PatchType.SPIRIT_TREE));
		assertFalse(config.paysToClear(PatchType.CRYSTAL_TREE));
		assertTrue(config.paysToClear(PatchType.HARDWOOD));
		assertEquals(2000, Crop.clearCoins(PatchType.REDWOOD));
		assertEquals(200, Crop.clearCoins(PatchType.HARDWOOD));
		// Compost raises the crystal tree's shards, so it gets ultracompost by default
		assertEquals(Compost.ULTRACOMPOST, config.getCompost().get(PatchType.CRYSTAL_TREE));
		assertTrue(PatchType.SPIRIT_TREE.keepable());
		assertTrue(PatchType.CALQUAT.regrows());
	}

	@Test
	public void keptSpiritTreeIsNotDue()
	{
		final PatchPrediction grown = new PatchPrediction(Patch.PORT_SARIM_SPIRIT_TREE, Crop.SPIRIT_TREE,
			PatchState.HARVESTABLE, 0, 13, 0, 0, 0, PatchPrediction.Source.THIS_PLUGIN);
		assertFalse(RunSelector.isDue(grown, true));
		assertTrue(RunSelector.isDue(grown, false));
	}

	@Test
	public void stepsForTheSpecialTrees()
	{
		final StepAdvisor.PatchGoal redwood = new StepAdvisor.PatchGoal(Patch.FARMING_GUILD_REDWOOD, Crop.REDWOOD,
			"Redwood sapling", Compost.NONE, false, null, true, false, false, false);
		assertEquals("Ask Alexandra to remove the redwood tree (2,000 coins)",
			StepAdvisor.advise(redwood, live(Crop.REDWOOD, PatchState.HARVESTABLE)).getText());
		final StepAdvisor.PatchGoal spirit = new StepAdvisor.PatchGoal(Patch.PORT_SARIM_SPIRIT_TREE, Crop.SPIRIT_TREE,
			"Spirit sapling", Compost.NONE, false, null, false, false, false, false);
		assertEquals("Dig up the spirit tree with your spade to replant",
			StepAdvisor.advise(spirit, live(Crop.SPIRIT_TREE, PatchState.HARVESTABLE)).getText());
		assertEquals("Check the health of the spirit tree (replanting: Spirit sapling)",
			StepAdvisor.advise(spirit, live(Crop.SPIRIT_TREE, PatchState.CHECK_HEALTH)).getText());
		final StepAdvisor.PatchGoal crystal = new StepAdvisor.PatchGoal(Patch.PRIFDDINAS_CRYSTAL_TREE, Crop.CRYSTAL_TREE,
			"Crystal sapling", Compost.ULTRACOMPOST, false, null, false, false, false, false);
		assertEquals(StepAdvisor.Action.CHOP,
			StepAdvisor.advise(crystal, live(Crop.CRYSTAL_TREE, PatchState.HARVESTABLE)).getAction());
	}

	private static PatchPrediction live(Crop crop, PatchState state)
	{
		return new PatchPrediction(null, crop, state, 0, crop.getStages(), 0, 0, 0, PatchPrediction.Source.THIS_PLUGIN);
	}
}
