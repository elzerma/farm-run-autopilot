package com.farmrunautopilot.supply;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Allotments protected by a flower (docs/plans/crop-types.md, Sprint 1a). A fully grown flower in a set's flower patch
 * protects both allotments of that set while it stays there (wiki: Flower patch, Disease), instead of paying the
 * gardener for each. The player chooses this per set (elzerma), so the rules live here, in one place, for the run
 * selection, the supply list and the step guide.
 */
public final class FlowerProtection
{
	private FlowerProtection()
	{
	}

	/** The flower patch of an allotment's set, or null (Harmony Island has none). */
	public static Patch flowerPatchOf(Patch allotment)
	{
		for (Patch mate : allotment.getSetMates())
		{
			if (mate.getType() == PatchType.FLOWER)
			{
				return mate;
			}
		}
		return null;
	}

	/**
	 * Whether this flower patch is kept for protecting its set's allotments: the player can use it and hasn't unticked
	 * it, and an allotment there is in their runs and set to flower protection. Its flower is then chosen to suit the
	 * allotments, and a grown one is left in place.
	 */
	public static boolean isProtecting(Patch flowerPatch, RunConfig config, AccessSnapshot access)
	{
		if (flowerPatch.getType() != PatchType.FLOWER || config.getDisabledPatches().contains(flowerPatch)
			|| !access.missingFor(flowerPatch).isEmpty())
		{
			return false;
		}
		for (Patch mate : flowerPatch.getSetMates())
		{
			if (mate.getType() == PatchType.ALLOTMENT && config.isPatchSelected(mate) && access.missingFor(mate).isEmpty()
				&& config.protectionFor(mate) == Protection.FLOWER)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a protecting flower patch needs a visit: anything but a growing or grown flower that protects
	 * {@code allotmentCrop} (any flower counts while that's unknown). A grown one is left alone, since picking it ends
	 * the protection.
	 */
	public static boolean isDue(PatchPrediction prediction, Crop allotmentCrop)
	{
		if (prediction == null)
		{
			return true;
		}
		final PatchState state = prediction.getState();
		if (state != PatchState.GROWING && state != PatchState.HARVESTABLE)
		{
			return true;
		}
		final Crop flower = prediction.getCrop();
		return flower == null || (allotmentCrop != null && !allotmentCrop.getProtectingFlowers().contains(flower));
	}

	/**
	 * The flower to plant so it protects every one of these allotment crops: the cheapest the player can plant and
	 * holds, else the cheapest they can plant (shown as missing). Null if none protects them all at their level (then
	 * the gardener is paid instead).
	 */
	public static Crop flowerFor(Collection<Crop> allotmentCrops, int farmingLevel, ToIntFunction<Crop> held)
	{
		List<Crop> common = null;
		for (Crop crop : allotmentCrops)
		{
			if (crop == null)
			{
				continue;
			}
			if (common == null)
			{
				common = new ArrayList<>(crop.getProtectingFlowers());
			}
			else
			{
				common.retainAll(crop.getProtectingFlowers());
			}
		}
		if (common == null)
		{
			return null;
		}
		common.removeIf(flower -> flower.getFarmingLevel() > farmingLevel);
		for (Crop flower : common)
		{
			if (held.applyAsInt(flower) > 0)
			{
				return flower;
			}
		}
		return common.isEmpty() ? null : common.get(0);
	}

	/**
	 * How a patch is protected this run. Allotments set to flower protection are protected by it only when their set's
	 * flower patch is kept for them and holds (or gets) a flower that protects their crop; otherwise their gardener is
	 * paid. Patches without a gardener can only be composted.
	 *
	 * @param plantings what is planted where this run
	 * @param predictions what each patch holds now
	 */
	public static Protection effective(Patch patch, RunConfig config, AccessSnapshot access, Map<Patch, Crop> plantings,
		Function<Patch, PatchPrediction> predictions)
	{
		final Protection chosen = config.protectionFor(patch);
		final Protection fallback = patch.hasGardener() ? Protection.PAY_GARDENER : Protection.COMPOST_ONLY;
		if (chosen == null)
		{
			return Protection.COMPOST_ONLY;
		}
		if (chosen != Protection.FLOWER)
		{
			return chosen == Protection.PAY_GARDENER ? fallback : chosen;
		}
		final Patch flowerPatch = patch.getType() == PatchType.ALLOTMENT ? flowerPatchOf(patch) : null;
		if (flowerPatch == null || !isProtecting(flowerPatch, config, access))
		{
			return fallback;
		}
		final Crop allotmentCrop = cropIn(patch, plantings, predictions);
		final Crop flower = cropIn(flowerPatch, plantings, predictions);
		if (flower == null)
		{
			return fallback;
		}
		return allotmentCrop == null || allotmentCrop.getProtectingFlowers().contains(flower) ? Protection.FLOWER : fallback;
	}

	/** What's planted this run, or else what's growing (or grown) there now, or null. */
	private static Crop cropIn(Patch patch, Map<Patch, Crop> plantings, Function<Patch, PatchPrediction> predictions)
	{
		final Crop planted = plantings.get(patch);
		if (planted != null)
		{
			return planted;
		}
		final PatchPrediction prediction = predictions.apply(patch);
		if (prediction == null)
		{
			return null;
		}
		final PatchState state = prediction.getState();
		return state == PatchState.GROWING || state == PatchState.HARVESTABLE ? prediction.getCrop() : null;
	}
}
