package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Decides which crop goes in which patch of one run type.
 *
 * <p>Safe patches (disease-free or protected) are filled first. They take reserved crops first (e.g. the
 * player's valuable herbs for disease-free patches), then the normal choices. Other patches never get
 * reserved crops. Each patch gets the best crop still in stock; if everything has run out it gets its
 * first allowed choice, and that crop shows as short.
 */
public final class CropAllocator
{
	private CropAllocator()
	{
	}

	/**
	 * @param patches the patches of one run type in this run
	 * @param choices crops in order of preference (1st choice first)
	 * @param held how many of a crop's seed or sapling the player holds
	 * @param safe whether a patch is disease-free or protected
	 * @param reservedForSafe crops only for safe patches, best first; may be empty
	 * @return crop per patch, in the order patches were given
	 */
	public static Map<Patch, Crop> allocate(List<Patch> patches, List<Crop> choices, ToIntFunction<Crop> held,
		Predicate<Patch> safe, List<Crop> reservedForSafe)
	{
		final List<Patch> order = new ArrayList<>();
		for (Patch patch : patches)
		{
			if (safe.test(patch))
			{
				order.add(patch);
			}
		}
		for (Patch patch : patches)
		{
			if (!safe.test(patch))
			{
				order.add(patch);
			}
		}

		final Map<Crop, Integer> stock = new HashMap<>();
		for (Crop crop : choices)
		{
			stock.put(crop, held.applyAsInt(crop));
		}
		for (Crop crop : reservedForSafe)
		{
			stock.put(crop, held.applyAsInt(crop));
		}

		// Normal choices for patches that aren't safe: never a reserved crop, unless every choice is reserved.
		final List<Crop> unsafeChoices = new ArrayList<>();
		for (Crop crop : choices)
		{
			if (!reservedForSafe.contains(crop))
			{
				unsafeChoices.add(crop);
			}
		}
		if (unsafeChoices.isEmpty())
		{
			unsafeChoices.add(choices.get(0));
		}

		final Map<Patch, Crop> byPatch = new HashMap<>();
		for (Patch patch : order)
		{
			final List<Crop> candidates = new ArrayList<>();
			if (safe.test(patch))
			{
				candidates.addAll(reservedForSafe);
				candidates.addAll(choices);
			}
			else
			{
				candidates.addAll(unsafeChoices);
			}

			Crop pick = null;
			for (Crop crop : candidates)
			{
				if (stock.get(crop) > 0)
				{
					pick = crop;
					break;
				}
			}
			if (pick == null)
			{
				// Everything is out: use the first normal choice this patch may have.
				pick = safe.test(patch) ? choices.get(0) : unsafeChoices.get(0);
			}
			stock.merge(pick, -1, Integer::sum);
			byPatch.put(patch, pick);
		}

		final Map<Patch, Crop> result = new LinkedHashMap<>();
		for (Patch patch : patches)
		{
			result.put(patch, byPatch.get(patch));
		}
		return result;
	}
}
