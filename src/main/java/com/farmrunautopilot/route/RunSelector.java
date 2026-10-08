package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchStatusText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
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
				if (patch.getType() == type && config.isPatchSelected(patch) && access.missingFor(patch).isEmpty())
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
				if (isDue(prediction))
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
				if (fullRun || isDue(prediction))
				{
					patches.add(patch);
				}
				else
				{
					notDue.add(patch.getDisplayName() + ": " + PatchStatusText.describe(prediction, now));
				}
			}
		}
		return new RunSelection(patches, included, skipped, notDue, dueCounts);
	}

	/** A patch is due unless it is still growing (SPEC 10). Never-seen patches count as due. */
	public static boolean isDue(PatchPrediction prediction)
	{
		return prediction == null || prediction.getState() != PatchState.GROWING;
	}

	/** How many of {@code total} patches must be due to reach {@code percent}, rounded up. */
	static int neededDue(int total, int percent)
	{
		return (total * percent + 99) / 100;
	}
}
