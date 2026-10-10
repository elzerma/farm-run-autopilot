package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * What to do next at a patch, worked out from its live state (SPEC 13.4). Working from the state rather
 * than a fixed checklist means the guide keeps up if the player does things in another order or the game
 * does them (a gardener clearing a tree, weeds growing back).
 */
public final class StepAdvisor
{
	public enum Action
	{
		/** No data for the patch yet; walk up to it. */
		INSPECT,
		RAKE,
		CLEAR_DEAD,
		CURE,
		CHECK_HEALTH,
		PICK,
		PAY_TO_CLEAR,
		CHOP,
		DIG_STUMP,
		COMPOST,
		PLANT,
		PAY,
		DONE
	}

	@Value
	public static class Advice
	{
		Action action;
		String text;
	}

	/** What the run plan wants at a patch, and what has happened there this run. */
	@Value
	public static class PatchGoal
	{
		Patch patch;
		Crop crop;
		/** Seed or sapling item name, e.g. "Ranarr seed". */
		String plantName;
		Compost compost;
		/** The gardener must be paid for this patch. */
		boolean protectionNeeded;
		/** e.g. "25 x Coconut (noted is fine)". */
		String paymentText;
		boolean payToClear;
		boolean composted;
		boolean paid;
		/** Seen empty, ready or dead during this run, so a growing crop was planted this run. */
		boolean plantedThisRun;
	}

	private StepAdvisor()
	{
	}

	public static Advice advise(PatchGoal goal, PatchPrediction live)
	{
		final Patch patch = goal.getPatch();
		if (live == null)
		{
			return new Advice(Action.INSPECT, "Walk to the " + patch.getDisplayName() + " patch");
		}
		final String gardener = patch.getGardenerName() != null ? patch.getGardenerName() : "the gardener";
		final String what = live.getCrop() != null ? live.getCrop().getDisplayName().toLowerCase() : "plant";
		final boolean tree = patch.getType().isProtectable();

		switch (live.getState())
		{
			case GROWING:
				// Compost and payment are only asked for if it was planted this run; otherwise it was already
				// growing. Compost works before or after planting, so planting first still leaves it to do.
				if (goal.isPlantedThisRun() && wantsCompost(goal))
				{
					return compost(goal);
				}
				if (goal.isPlantedThisRun() && goal.isProtectionNeeded() && !goal.isPaid())
				{
					return new Advice(Action.PAY, "Pay " + gardener + ": " + goal.getPaymentText());
				}
				return new Advice(Action.DONE, "Done");
			case CHECK_HEALTH:
				return new Advice(Action.CHECK_HEALTH, "Check the health of the " + (live.getCrop() != null
					? what + " tree" : patch.getType().getDisplayName().toLowerCase()) + " (replanting: "
					+ goal.getPlantName() + ")");
			case HARVESTABLE:
				if (patch.getType() == PatchType.HERB)
				{
					return new Advice(Action.PICK, "Pick the " + what);
				}
				if (patch.getType() == PatchType.FRUIT_TREE && live.getStage() > 0)
				{
					return new Advice(Action.PICK, "Pick the " + what + " fruit (" + live.getStage() + " left)");
				}
				return clearTree(goal, gardener);
			case STUMP:
				return goal.isPayToClear()
					? new Advice(Action.PAY_TO_CLEAR, "Ask " + gardener + " to remove the stump (200 coins)")
					: new Advice(Action.DIG_STUMP, "Dig up the stump with your spade");
			case DEAD:
				return new Advice(Action.CLEAR_DEAD, "Clear the dead " + what + (tree ? " tree" : "") + " (spade)");
			case DISEASED:
				return new Advice(Action.CURE, "Cure the diseased " + what + " with plant cure, or clear it");
			case WEEDS:
				return new Advice(Action.RAKE, "Rake the patch");
			case EMPTY:
				if (wantsCompost(goal))
				{
					return compost(goal);
				}
				return new Advice(Action.PLANT, "Plant: " + goal.getPlantName());
			default:
				return new Advice(Action.INSPECT, "Inspect the patch");
		}
	}

	/**
	 * A short plan for a patch, for hovering a route stop, e.g. "Herb: harvest guam and plant ranarr" or
	 * "Fruit tree: check health of papaya, clear and plant papaya".
	 *
	 * @param live what's in the patch now, or null if unknown
	 * @param plant what to plant, or null if not decided
	 */
	public static String objectives(Patch patch, PatchPrediction live, Crop plant)
	{
		final List<String> steps = new ArrayList<>();
		final boolean tree = patch.getType() == PatchType.TREE || patch.getType() == PatchType.FRUIT_TREE;
		boolean replant = true;
		if (live == null)
		{
			steps.add("check the patch");
		}
		else
		{
			final String what = live.getCrop() != null ? live.getCrop().getDisplayName().toLowerCase() : "the crop";
			switch (live.getState())
			{
				case GROWING:
					steps.add(what + " still growing");
					replant = false;
					break;
				case CHECK_HEALTH:
					steps.add("check health of " + what);
					if (tree)
					{
						steps.add("clear");
					}
					break;
				case HARVESTABLE:
					if (patch.getType() == PatchType.FRUIT_TREE)
					{
						steps.add((live.getStage() > 0 ? "pick " : "") + what);
						steps.add("clear");
					}
					else
					{
						steps.add((tree ? "clear " : "harvest ") + what);
					}
					break;
				case STUMP:
					steps.add("clear the stump");
					break;
				case DEAD:
					steps.add("clear dead " + what);
					break;
				case DISEASED:
					steps.add("cure or clear " + what);
					break;
				case WEEDS:
					steps.add("rake");
					break;
				default:
					break;
			}
		}
		if (replant)
		{
			steps.add("plant " + (plant != null ? plant.getDisplayName().toLowerCase() : "a seed"));
		}
		String text = steps.get(0);
		for (int i = 1; i < steps.size(); i++)
		{
			text += (i == steps.size() - 1 ? " and " : ", ") + steps.get(i);
		}
		return patch.getType().getDisplayName() + ": " + text;
	}

	private static boolean wantsCompost(PatchGoal goal)
	{
		return goal.getCompost() != null && goal.getCompost() != Compost.NONE && !goal.isComposted();
	}

	private static Advice compost(PatchGoal goal)
	{
		return new Advice(Action.COMPOST, "Use " + goal.getCompost().getDisplayName().toLowerCase() + " on the patch");
	}

	private static Advice clearTree(PatchGoal goal, String gardener)
	{
		return goal.isPayToClear()
			? new Advice(Action.PAY_TO_CLEAR, "Ask " + gardener + " to remove the tree (200 coins)")
			: new Advice(Action.CHOP, "Chop down the tree");
	}
}
