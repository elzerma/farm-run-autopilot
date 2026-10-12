package com.farmrunautopilot.run;

import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
	 * Inventory items this part of the run doesn't need (produce, empty buckets, the last part's leftovers), to
	 * deposit. The holdings count noted items as unnoted, so each is also given noted, to outline whichever the
	 * player has.
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
		final Set<Integer> deposit = new LinkedHashSet<>();
		for (Map.Entry<Integer, Integer> e : holdings.in(Holdings.Source.INVENTORY).entrySet())
		{
			final int id = e.getKey();
			if (e.getValue() > 0 && !needed.contains(id))
			{
				deposit.add(id);
				deposit.add(noted.applyAsInt(id));
			}
		}
		return deposit;
	}

	/**
	 * What to put on for this part of the run: its teleport items marked to wear and its outfit, that are in the
	 * inventory but not worn. Items still in the bank are left to the bank tab.
	 */
	static Wear wear(SupplyPlan part, Holdings holdings, Outfit outfit)
	{
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> worn = holdings.in(Holdings.Source.WORN);
		final List<String> names = new ArrayList<>();
		final Set<Integer> ids = new LinkedHashSet<>();
		for (SupplyLine line : part.getLines())
		{
			// Teleport items, the staff and magic secateurs; the outfit is handled below, piece by piece
			if (!line.isWear() || line.getName().equals(outfit.getDisplayName()) || any(line.getItemIds(), worn))
			{
				continue;
			}
			if (any(line.getItemIds(), inventory))
			{
				names.add(line.getName());
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
			if (!any(piece, worn) && any(piece, inventory))
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
