package com.farmrunautopilot.run;

import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import lombok.Value;

/**
 * Banking and getting dressed for a part of the run (docs/REBUILD.md, Sprint 4): what to deposit at a bank visit,
 * and which teleport items and outfit to put on. Pure, so it can be tested without a client.
 */
final class BankGuide
{
	/** Items to put on, and how to say so. */
	@Value
	static class Wear
	{
		static final Wear NONE = new Wear(null, Collections.emptySet());

		/** e.g. "Wear: Graceful outfit, Ring of wealth", or null when there's nothing to put on. */
		String text;
		/** Inventory items to put on, to highlight in the wear colour. */
		Set<Integer> itemIds;
	}

	private BankGuide()
	{
	}

	/**
	 * Inventory items this part of the run doesn't need (produce, empty buckets, the last part's leftovers), and the
	 * seeds and saplings beyond what it plants, to deposit. The holdings count noted items as unnoted, so each is also
	 * given noted, to outline whichever the player has.
	 *
	 * @param noted the noted version of an item (or the same item if it has none)
	 */
	static Set<Integer> toDeposit(SupplyPlan part, Holdings holdings, IntUnaryOperator noted)
	{
		final Set<Integer> needed = new HashSet<>();
		for (SupplyLine line : part.getLines())
		{
			for (int id : line.getItemIds())
			{
				needed.add(id);
			}
		}
		final Map<Integer, Integer> extras = extras(part, holdings);
		final Set<Integer> deposit = new LinkedHashSet<>();
		for (Map.Entry<Integer, Integer> e : holdings.in(Holdings.Source.INVENTORY).entrySet())
		{
			final int id = e.getKey();
			if (e.getValue() > 0 && (!needed.contains(id) || extras.containsKey(id)))
			{
				deposit.add(id);
				deposit.add(noted.applyAsInt(id));
			}
		}
		return deposit;
	}

	/**
	 * Seeds and saplings in the inventory beyond what this part of the run plants, by item, with how many to put in
	 * the bank (elzerma, 2026-10-18: take only what each part needs).
	 */
	static Map<Integer, Integer> extras(SupplyPlan part, Holdings holdings)
	{
		final Map<Integer, Integer> planted = new LinkedHashMap<>();
		for (SupplyLine line : part.getLines())
		{
			if (line.getGroup() == SupplyLine.Group.SEEDS)
			{
				for (int id : line.getItemIds())
				{
					planted.merge(id, line.getNeed(), Integer::sum);
				}
			}
		}
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> extras = new LinkedHashMap<>();
		planted.forEach((id, need) ->
		{
			final int over = inventory.getOrDefault(id, 0) - need;
			if (over > 0)
			{
				extras.put(id, over);
			}
		});
		return extras;
	}

	/**
	 * What to put on for this part of the run: its teleport items marked to wear and its outfit, that are in the
	 * inventory but not worn. Items still in the bank are left to the bank tab.
	 */
	static Wear wear(SupplyPlan part, Holdings holdings, Outfit outfit)
	{
		return wear(part, holdings, outfit, Collections.emptySet());
	}

	/** As above, leaving out these items (the staves swapped on the way, which have a step of their own). */
	static Wear wear(SupplyPlan part, Holdings holdings, Outfit outfit, Set<Integer> skip)
	{
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> worn = holdings.in(Holdings.Source.WORN);
		final List<String> names = new ArrayList<>();
		final Set<Integer> ids = new LinkedHashSet<>();
		for (SupplyLine line : part.getLines())
		{
			// Teleport items, the staff and magic secateurs; the outfit is handled below, piece by piece
			if (!line.isWear() || line.getName().equals(outfit.getDisplayName()) || any(line.getItemIds(), worn)
				|| java.util.Arrays.stream(line.getItemIds()).anyMatch(skip::contains))
			{
				continue;
			}
			if (any(line.getItemIds(), inventory))
			{
				// The item's own name: "Ring of dueling", not "Ring of dueling (bank partway)"
				names.add(line.getName().replaceFirst(" \\(.*\\)$", ""));
				for (int id : line.getItemIds())
				{
					if (inventory.getOrDefault(id, 0) > 0)
					{
						ids.add(id);
					}
				}
			}
		}
		// The outfit: every piece held in the inventory and not already worn
		boolean outfitToWear = false;
		for (int[] piece : outfit.getPieces())
		{
			if (!any(piece, worn) && any(piece, inventory) && java.util.Arrays.stream(piece).noneMatch(skip::contains))
			{
				outfitToWear = true;
				for (int id : piece)
				{
					if (inventory.getOrDefault(id, 0) > 0)
					{
						ids.add(id);
					}
				}
			}
		}
		if (outfitToWear)
		{
			names.add(0, outfit.getDisplayName());
		}
		return names.isEmpty() ? Wear.NONE : new Wear("Wear: " + String.join(", ", names), ids);
	}

	private static boolean any(int[] ids, Map<Integer, Integer> counts)
	{
		for (int id : ids)
		{
			if (counts.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}
}
