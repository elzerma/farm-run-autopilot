package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.tracking.PatchPrediction;
import org.junit.Test;

public class StepAdvisorTest
{
	private static PatchPrediction live(Patch patch, Crop crop, PatchState state, int stage)
	{
		return new PatchPrediction(patch, crop, state, stage, 7, 0, 0, 0, PatchPrediction.Source.THIS_PLUGIN);
	}

	private static StepAdvisor.PatchGoal herb(boolean composted)
	{
		return new StepAdvisor.PatchGoal(Patch.CATHERBY_HERB, Crop.RANARR, "Ranarr seed", Compost.ULTRACOMPOST,
			false, null, false, composted, false, true);
	}

	private static StepAdvisor.PatchGoal tree(boolean payToClear, boolean paid)
	{
		return new StepAdvisor.PatchGoal(Patch.TAVERLEY_TREE, Crop.MAGIC, "Magic sapling", Compost.NONE,
			true, "25 x Coconut (noted is fine)", payToClear, false, paid, true);
	}

	private static StepAdvisor.Action action(StepAdvisor.PatchGoal goal, PatchPrediction live)
	{
		return StepAdvisor.advise(goal, live).getAction();
	}

	@Test
	public void herbRunSequence()
	{
		final Patch p = Patch.CATHERBY_HERB;
		assertEquals(StepAdvisor.Action.INSPECT, action(herb(false), null));
		assertEquals(StepAdvisor.Action.PICK, action(herb(false), live(p, Crop.RANARR, PatchState.HARVESTABLE, 2)));
		assertEquals(StepAdvisor.Action.RAKE, action(herb(false), live(p, null, PatchState.WEEDS, 2)));
		assertEquals(StepAdvisor.Action.COMPOST, action(herb(false), live(p, null, PatchState.EMPTY, 0)));
		assertEquals("Plant: Ranarr seed", StepAdvisor.advise(herb(true), live(p, null, PatchState.EMPTY, 0)).getText());
		assertEquals(StepAdvisor.Action.DONE, action(herb(true), live(p, Crop.RANARR, PatchState.GROWING, 0)));
		assertEquals(StepAdvisor.Action.CLEAR_DEAD, action(herb(false), live(p, null, PatchState.DEAD, 1)));
	}

	@Test
	public void compostAfterPlantingIsStillAskedFor()
	{
		final Patch p = Patch.CATHERBY_HERB;
		assertEquals(StepAdvisor.Action.COMPOST, action(herb(false), live(p, Crop.RANARR, PatchState.GROWING, 0)));
		// Already growing before this run: nothing to do
		final StepAdvisor.PatchGoal alreadyGrowing = new StepAdvisor.PatchGoal(p, Crop.RANARR, "Ranarr seed",
			Compost.ULTRACOMPOST, false, null, false, false, false, false);
		assertEquals(StepAdvisor.Action.DONE, action(alreadyGrowing, live(p, Crop.RANARR, PatchState.GROWING, 1)));
	}

	@Test
	public void treeRunSequence()
	{
		final Patch p = Patch.TAVERLEY_TREE;
		assertEquals(StepAdvisor.Action.CHECK_HEALTH,
			action(tree(true, false), live(p, Crop.MAGIC, PatchState.CHECK_HEALTH, 12)));
		assertEquals("Ask Alain to remove the tree (200 coins)",
			StepAdvisor.advise(tree(true, false), live(p, Crop.MAGIC, PatchState.HARVESTABLE, 0)).getText());
		assertEquals(StepAdvisor.Action.CHOP, action(tree(false, false), live(p, Crop.MAGIC, PatchState.HARVESTABLE, 0)));
		assertEquals(StepAdvisor.Action.DIG_STUMP, action(tree(false, false), live(p, Crop.MAGIC, PatchState.STUMP, 0)));
		assertEquals("Pay Alain: 25 x Coconut (noted is fine)",
			StepAdvisor.advise(tree(true, false), live(p, Crop.MAGIC, PatchState.GROWING, 0)).getText());
		assertEquals(StepAdvisor.Action.DONE, action(tree(true, true), live(p, Crop.MAGIC, PatchState.GROWING, 0)));
	}

	@Test
	public void fruitIsPickedBeforeClearing()
	{
		final Patch p = Patch.CATHERBY_FRUIT_TREE;
		final StepAdvisor.PatchGoal goal = new StepAdvisor.PatchGoal(p, Crop.PALM, "Palm sapling", Compost.NONE, true,
			"15 x Papaya fruit", true, false, false, true);
		assertEquals(StepAdvisor.Action.PICK, action(goal, live(p, Crop.PALM, PatchState.HARVESTABLE, 6)));
		assertEquals(StepAdvisor.Action.PAY_TO_CLEAR, action(goal, live(p, Crop.PALM, PatchState.HARVESTABLE, 0)));
	}

	@Test
	public void objectivesAreShort()
	{
		assertEquals("Herb: harvest guam and plant ranarr", StepAdvisor.objectives(Patch.CATHERBY_HERB,
			live(Patch.CATHERBY_HERB, Crop.GUAM, PatchState.HARVESTABLE, 4), Crop.RANARR));
		assertEquals("Fruit tree: pick papaya, clear and plant papaya", StepAdvisor.objectives(
			Patch.CATHERBY_FRUIT_TREE, live(Patch.CATHERBY_FRUIT_TREE, Crop.PAPAYA, PatchState.HARVESTABLE, 6),
			Crop.PAPAYA));
		assertEquals("Tree: check health of yew, clear and plant magic", StepAdvisor.objectives(Patch.TAVERLEY_TREE,
			live(Patch.TAVERLEY_TREE, Crop.YEW, PatchState.CHECK_HEALTH, 0), Crop.MAGIC));
		assertEquals("Herb: check the patch and plant ranarr",
			StepAdvisor.objectives(Patch.CATHERBY_HERB, null, Crop.RANARR));
	}

	@Test
	public void checkHealthNamesTheTree()
	{
		assertEquals("Check the health of the yew tree (replanting: Magic sapling)",
			StepAdvisor.advise(tree(false, false), live(Patch.TAVERLEY_TREE, Crop.YEW, PatchState.CHECK_HEALTH, 0))
				.getText());
	}
@Test
	public void noPaymentForATreeThatWasAlreadyGrowing()
	{
		final StepAdvisor.PatchGoal alreadyGrowing = new StepAdvisor.PatchGoal(Patch.TAVERLEY_TREE, Crop.MAGIC,
			"Magic sapling", Compost.NONE, true, "25 x Coconut", false, false, false, false);
		assertEquals(StepAdvisor.Action.DONE,
			action(alreadyGrowing, live(Patch.TAVERLEY_TREE, Crop.MAGIC, PatchState.GROWING, 3)));
		assertEquals(StepAdvisor.Action.PAY,
			action(tree(false, false), live(Patch.TAVERLEY_TREE, Crop.MAGIC, PatchState.GROWING, 0)));
	}
}