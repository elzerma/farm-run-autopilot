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
		/** A diseased tree or fruit tree: pruned with secateurs, not cured with plant cure. */
		PRUNE,
		CHECK_HEALTH,
		PICK,
		PAY_TO_CLEAR,
		CHOP,
		DIG_STUMP,
		COMPOST,
		PLANT,
		PAY,
		/** Note the stop's produce on the tool leprechaun, once everything here is picked (patch actions P1). */
		NOTE,
		/** Take the compost this stop's patches need from the tool leprechaun (P1). */
		TAKE_COMPOST,
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
		/** A grown crop is picked and left growing, not cleared and replanted (RunConfig.keepsGrowing). */
		boolean keepGrowing;

		public PatchGoal(Patch patch, Crop crop, String plantName, Compost compost, boolean protectionNeeded,
			String paymentText, boolean payToClear, boolean composted, boolean paid, boolean plantedThisRun)
		{
			this(patch, crop, plantName, compost, protectionNeeded, paymentText, payToClear, composted, paid,
				plantedThisRun, false);
		}

		public PatchGoal(Patch patch, Crop crop, String plantName, Compost compost, boolean protectionNeeded,
			String paymentText, boolean payToClear, boolean composted, boolean paid, boolean plantedThisRun,
			boolean keepGrowing)
		{
			this.patch = patch;
			this.crop = crop;
			this.plantName = plantName;
			this.compost = compost;
			this.protectionNeeded = protectionNeeded;
			this.paymentText = paymentText;
			this.payToClear = payToClear;
			this.composted = composted;
			this.paid = paid;
			this.plantedThisRun = plantedThisRun;
			this.keepGrowing = keepGrowing;
		}
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
		final boolean tree = patch.getType().isHealthChecked();

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
					return new Advice(Action.PAY, "Pay " + gardener + (patch.getPart() != null ? " for the " + patch.getPart() + " "
						+ patch.getType().getDisplayName().toLowerCase() : "") + ": " + goal.getPaymentText());
				}
				return new Advice(Action.DONE, "Done");
			case CHECK_HEALTH:
				return new Advice(Action.CHECK_HEALTH, "Check the health of the " + (live.getCrop() != null
					? named(patch.getType(), what) : patch.getType().getDisplayName().toLowerCase())
					+ (goal.isKeepGrowing() ? "" : " (replanting: " + goal.getPlantName() + ")"));
			case HARVESTABLE:
				// Herbs, allotments, flowers, hops and the specials are picked, then replanted
				if (!tree)
				{
					return new Advice(Action.PICK, "Pick the " + what);
				}
				if (patch.getType().regrows() && live.getStage() > 0)
				{
					return new Advice(Action.PICK, "Pick the " + picked(patch.getType(), what) + " (" + live.getStage()
						+ " left)");
				}
				// Picked and left growing (elzerma, 2026-10-24): nothing more until it regrows
				if (goal.isKeepGrowing())
				{
					return new Advice(Action.DONE, "Done");
				}
				switch (patch.getType())
				{
					case BUSH:
					case CACTUS:
					case SPIRIT_TREE:
						// Dug up, not chopped (a spirit tree can't be)
						return new Advice(Action.CLEAR_DEAD, "Dig up the " + named(patch.getType(), what)
							+ " with your spade to replant");
					case CELASTRUS:
						return new Advice(Action.CHOP, "Cut the celastrus bark with your axe");
					case CRYSTAL_TREE:
						// Its shards come from chopping it down, which leaves the patch empty
						return new Advice(Action.CHOP, "Chop down the crystal tree with your axe");
					default:
						return clearTree(goal, gardener, named(patch.getType(), what));
				}
			case STUMP:
				final String stump = patch.getType() == PatchType.CELASTRUS ? "bare celastrus" : "stump";
				return goal.isPayToClear()
					? new Advice(Action.PAY_TO_CLEAR, "Ask " + gardener + " to remove the " + stump + " ("
						+ coins(patch.getType()) + " coins)")
					: new Advice(Action.DIG_STUMP, "Dig up the " + stump + " with your spade");
			case DEAD:
				return new Advice(Action.CLEAR_DEAD, "Clear the dead " + (tree ? named(patch.getType(), what) : what)
					+ " (spade)");
			case DISEASED:
				return tree
					? new Advice(Action.PRUNE, "Prune the diseased " + named(patch.getType(), what) + " with secateurs")
					: new Advice(Action.CURE, "Cure the diseased " + what + " with plant cure, or clear it");
			case WEEDS:
				return new Advice(Action.RAKE, "Rake the patch");
			case EMPTY:
				if (wantsCompost(goal))
				{
					return compost(goal);
				}
				if (goal.getCrop() == Crop.SCARECROW)
				{
					return new Advice(Action.PLANT, "Put the scarecrow in the flower patch: it protects the sweetcorn");
				}
				return new Advice(Action.PLANT, "Plant: " + goal.getPlantName() + (goal.getCrop() != null
					&& goal.getCrop().getSeedsPerPatch() > 1 ? " (" + goal.getCrop().getSeedsPerPatch() + ")" : ""));
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
		return objectives(patch, live, plant, false);
	}

	/**
	 * As above.
	 *
	 * @param keepGrowing the type's grown crop is picked and left growing (RunConfig.keepsGrowing)
	 */
	public static String objectives(Patch patch, PatchPrediction live, Crop plant, boolean keepGrowing)
	{
		final List<String> steps = new ArrayList<>();
		final boolean tree = patch.getType().isHealthChecked();
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
					if (keepGrowing)
					{
						steps.add("pick");
						replant = false;
					}
					else if (tree)
					{
						steps.add("clear");
					}
					break;
				case HARVESTABLE:
					if (patch.getType().regrows())
					{
						steps.add((live.getStage() > 0 ? "pick " : "") + what);
						if (keepGrowing)
						{
							replant = false;
						}
						else
						{
							steps.add("clear");
						}
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
					steps.add((tree ? "prune " : "cure or clear ") + what);
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

	/** What a grown, health-checked crop is: "tree", "bush" or "cactus". */
	static String grown(PatchType type)
	{
		switch (type)
		{
			case BUSH:
				return "bush";
			case CACTUS:
				return "cactus";
			default:
				return "tree";
		}
	}

	/** What's picked from a crop that regrows: "papaya fruit", "redberries", "potato cacti". */
	private static String picked(PatchType type, String what)
	{
		switch (type)
		{
			case BUSH:
				return what.endsWith("berry") ? what.substring(0, what.length() - 1) + "ies" : what + " berries";
			case CACTUS:
				return what.equals("cactus") ? "cactus spines" : "potato cacti";
			default:
				return what + " fruit";
		}
	}

	/** A grown crop with what it is: "papaya tree", "redberry bush", but "spirit tree" as it is. */
	private static String named(PatchType type, String what)
	{
		return what.endsWith("tree") ? what : what + " " + grown(type);
	}

	/** What the gardener charges to clear a grown tree, as written in a step: "200", "2,000" for a redwood. */
	private static String coins(PatchType type)
	{
		return String.format("%,d", Crop.clearCoins(type));
	}

	private static Advice clearTree(PatchGoal goal, String gardener, String tree)
	{
		return goal.isPayToClear()
			? new Advice(Action.PAY_TO_CLEAR, "Ask " + gardener + " to remove the " + tree + " ("
				+ coins(goal.getPatch().getType()) + " coins)")
			: new Advice(Action.CHOP, "Chop down the " + tree);
	}
}
