package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.FarmingContract;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.FlowerProtection;
import com.farmrunautopilot.tracking.ContractTracker.Contract;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchStatusText;
import net.runelite.api.Skill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides which patches the next run covers (SPEC 10).
 *
 * <ul>
 * <li>A patch is due unless it is still growing; never-seen patches count as due.</li>
 * <li>A ticked run type is included when at least the threshold percentage of its usable patches are due
 * (100% by default), unless the player overrides it for this run.</li>
 * <li>Within an included type, patches that aren't due are left out with a reason.</li>
 * <li>A flower patch kept for protecting its allotments (docs/plans/crop-types.md) joins whenever the allotments do
 * and it doesn't already hold a flower that protects them, whether or not flowers are ticked.</li>
 * </ul>
 */
public final class RunSelector
{
	private RunSelector()
	{
	}

	/**
	 * @param fullRun include every selected patch, ignoring due rules (testing)
	 */
	public static RunSelection select(RunConfig config, AccessSnapshot access,
		Function<Patch, PatchPrediction> predictions, long now, boolean fullRun, Map<PatchType, TypeOverride> overrides)
	{
		return select(config, access, predictions, now, fullRun, overrides, Contract.NONE);
	}

	/**
	 * As above, with the player's Farming Guild contract: with contracts on, the guild's contract patches are run for
	 * them (docs/plans/farming-contracts.md), whatever run types are ticked.
	 */
	public static RunSelection select(RunConfig config, AccessSnapshot access,
		Function<Patch, PatchPrediction> predictions, long now, boolean fullRun, Map<PatchType, TypeOverride> overrides,
		Contract contract)
	{
		final List<Patch> guild = contractPatches(config, access);
		final List<Patch> patches = new ArrayList<>();
		final Set<PatchType> included = EnumSet.noneOf(PatchType.class);
		final Map<PatchType, Long> skipped = new EnumMap<>(PatchType.class);
		final List<String> notDue = new ArrayList<>();
		final Map<PatchType, Integer> dueCounts = new EnumMap<>(PatchType.class);

		for (PatchType type : PatchType.values())
		{
			if (!config.getEnabledTypes().contains(type))
			{
				continue;
			}
			final List<Patch> usable = new ArrayList<>();
			for (Patch patch : Patch.values())
			{
				if (patch.getType() == type && config.isPatchSelected(patch) && access.missingFor(patch).isEmpty()
					&& !FlowerProtection.isProtecting(patch, config, access) && !guild.contains(patch))
				{
					usable.add(patch);
				}
			}
			if (usable.isEmpty())
			{
				continue;
			}

			int due = 0;
			final List<Long> readyTimes = new ArrayList<>();
			for (Patch patch : usable)
			{
				final PatchPrediction prediction = predictions.apply(patch);
				if (isDue(prediction, config.keepsGrowing(type)))
				{
					due++;
					readyTimes.add(now);
				}
				else
				{
					readyTimes.add(prediction.getDoneAt());
				}
			}
			dueCounts.put(type, due);

			final int needed = neededDue(usable.size(), config.getDueThresholdPercent());
			final TypeOverride override = overrides.get(type);
			final boolean include = fullRun
				|| override == TypeOverride.INCLUDE
				|| (override != TypeOverride.SKIP && due >= needed && due > 0);
			if (!include)
			{
				Collections.sort(readyTimes);
				final long readyAt = override == TypeOverride.SKIP ? 0 : readyTimes.get(Math.max(needed, 1) - 1);
				skipped.put(type, readyAt);
				continue;
			}

			included.add(type);
			for (Patch patch : usable)
			{
				final PatchPrediction prediction = predictions.apply(patch);
				if (fullRun || isDue(prediction, config.keepsGrowing(type)))
				{
					patches.add(patch);
				}
				else
				{
					notDue.add(patch.getDisplayName() + ": " + PatchStatusText.describe(prediction, now));
				}
			}
		}

		if (included.contains(PatchType.SPIRIT_TREE) && !fullRun)
		{
			limitSpiritTrees(patches, notDue, predictions, access);
		}

		// The guild's patches for contracts: those with something to do join, a waiting pre-plant doesn't, and when only
		// Guildmaster Jane is to see, the first patch anchors the guild stop with nothing done there
		ContractPlanner.Plan contractPlan = ContractPlanner.Plan.OFF;
		if (!guild.isEmpty())
		{
			final int farming = access.isKnown() ? access.level(Skill.FARMING) : 99;
			contractPlan = ContractPlanner.plan(contract, farming, guild, predictions);
			boolean any = false;
			for (Patch patch : guild)
			{
				final ContractPlanner.Role role = contractPlan.role(patch);
				if (role == ContractPlanner.Role.LEAVE)
				{
					notDue.add(patch.getDisplayName() + ": kept for a farming contract");
					continue;
				}
				// Nothing to plant (no pre-plant at this level, no crop chosen for the type): left alone
				if (role == ContractPlanner.Role.PLANT && ContractPlanner.crop(patch, contract, farming, guild, predictions,
					crop -> 0, type -> config.cropFor(type, farming)) == null)
				{
					continue;
				}
				patches.add(patch);
				included.add(patch.getType());
				any = true;
			}
			if (!any && contractPlan.getJane() != ContractPlanner.Jane.NONE)
			{
				patches.add(guild.get(0));
			}
		}

		if (included.contains(PatchType.ALLOTMENT))
		{
			final int farming = access.isKnown() ? access.level(Skill.FARMING) : 99;
			final Crop allotmentCrop = config.cropFor(PatchType.ALLOTMENT, farming);
			for (Patch patch : Patch.values())
			{
				if (FlowerProtection.isProtecting(patch, config, access) && !guild.contains(patch)
					&& (fullRun || FlowerProtection.isDue(predictions.apply(patch), allotmentCrop)))
				{
					patches.add(patch);
					included.add(PatchType.FLOWER);
				}
			}
		}
		return new RunSelection(patches, included, skipped, notDue, dueCounts, contractPlan);
	}

	/**
	 * The guild's contract patches the run uses for contracts: with Build farming contracts into runs on, those the
	 * player can use and hasn't unticked on the Farm tab; none with it off.
	 */
	public static List<Patch> contractPatches(RunConfig config, AccessSnapshot access)
	{
		final List<Patch> guild = new ArrayList<>();
		if (!config.isFarmingContracts())
		{
			return guild;
		}
		for (Patch patch : FarmingContract.PATCHES)
		{
			if (!config.getDisabledPatches().contains(patch) && access.missingFor(patch).isEmpty())
			{
				guild.add(patch);
			}
		}
		return guild;
	}

	/** A patch is due unless it is still growing (SPEC 10). Never-seen patches count as due. */
	public static boolean isDue(PatchPrediction prediction)
	{
		return isDue(prediction, false);
	}

	/**
	 * As above; a crop picked and left growing (elzerma, 2026-10-24) is due again only once it has regrown in full, not
	 * as soon as it's been picked.
	 *
	 * @param keepGrowing its type is picked and left growing ({@link RunConfig#keepsGrowing})
	 */
	public static boolean isDue(PatchPrediction prediction, boolean keepGrowing)
	{
		if (prediction == null || prediction.getState() == PatchState.GROWING)
		{
			return prediction == null;
		}
		if (keepGrowing && prediction.getState() == PatchState.HARVESTABLE && prediction.getCrop() != null)
		{
			// A kept spirit tree has nothing to pick: it's due again only if it dies or the crop changes
			if (prediction.getCrop().getHarvestStages() == 0)
			{
				return false;
			}
			return prediction.getStage() >= prediction.getCrop().getHarvestStages() - 1;
		}
		return true;
	}

	/**
	 * Leaves out empty spirit tree patches beyond what the player's Farming level allows to stand at once (wiki: Spirit
	 * Tree (Farming)): those already standing count, wherever they are; a grown one replanted is still one.
	 */
	static void limitSpiritTrees(List<Patch> patches, List<String> notDue, Function<Patch, PatchPrediction> predictions,
		AccessSnapshot access)
	{
		final int limit = spiritTreeLimit(access.isKnown() ? access.level(Skill.FARMING) : 99);
		int standing = 0;
		for (Patch patch : Patch.values())
		{
			if (patch.getType() == PatchType.SPIRIT_TREE && standing(predictions.apply(patch)))
			{
				standing++;
			}
		}
		for (Iterator<Patch> it = patches.iterator(); it.hasNext(); )
		{
			final Patch patch = it.next();
			final PatchPrediction live = predictions.apply(patch);
			// Never seen: it may hold one, so it's still visited
			if (patch.getType() != PatchType.SPIRIT_TREE || live == null || standing(live))
			{
				continue;
			}
			if (standing >= limit)
			{
				it.remove();
				notDue.add(patch.getDisplayName() + ": your Farming level allows " + limit + " spirit tree"
					+ (limit == 1 ? "" : "s") + " at once");
			}
			else
			{
				standing++;
			}
		}
	}

	/** A spirit tree is growing or grown in the patch (a diseased one counts: it can be pruned). */
	private static boolean standing(PatchPrediction live)
	{
		return live != null && live.getCrop() != null && (live.getState() == PatchState.GROWING
			|| live.getState() == PatchState.CHECK_HEALTH || live.getState() == PatchState.HARVESTABLE
			|| live.getState() == PatchState.DISEASED);
	}

	/**
	 * How many spirit trees may stand at once at this Farming level (boosts count): 1 at 83, 2 at 88, 3 at 93, 4 at 96,
	 * any number at 99 (wiki: Spirit Tree (Farming)/Patches).
	 */
	public static int spiritTreeLimit(int farming)
	{
		return farming >= 99 ? Integer.MAX_VALUE : farming >= 96 ? 4 : farming >= 93 ? 3 : farming >= 88 ? 2
			: farming >= 83 ? 1 : 0;
	}

	/** How many of {@code total} patches must be due to reach {@code percent}, rounded up. */
	static int neededDue(int total, int percent)
	{
		return (total * percent + 99) / 100;
	}
}
