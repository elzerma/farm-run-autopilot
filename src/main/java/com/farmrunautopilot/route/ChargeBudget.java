package com.farmrunautopilot.route;

import com.farmrunautopilot.data.travel.ItemCharges;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.supply.Holdings;
import java.util.EnumMap;
import java.util.Map;

/**
 * Teleport charges available for one run, pooled per item: every use of the same item in the run draws from
 * one pool, whichever teleport or fairy ring it's used for.
 */
public final class ChargeBudget
{
	/** Charges left per limited item; items not here never run out. */
	private final Map<TravelItem, Integer> left = new EnumMap<>(TravelItem.class);

	/**
	 * @param keepLastCharge hold back the last charge of rechargeable jewellery
	 */
	ChargeBudget(Holdings holdings, boolean keepLastCharge)
	{
		for (TravelItem item : TravelItem.values())
		{
			final Integer held = held(item, holdings);
			if (held != null)
			{
				left.put(item, keepLastCharge && ItemCharges.isRechargeable(item) ? Math.max(0, held - 1) : held);
			}
		}
	}

	/**
	 * Charges the player has of an item, across every piece held, or null if it never runs out (an unlimited
	 * version is held, or the item isn't limited).
	 */
	public static Integer held(TravelItem item, Holdings holdings)
	{
		if (!ItemCharges.isLimited(item))
		{
			return null;
		}
		int total = 0;
		for (int id : item.getItemIds())
		{
			final int count = holdings.count(id);
			if (count == 0)
			{
				continue;
			}
			final Integer charges = ItemCharges.chargesOf(id);
			if (charges == null)
			{
				// An unlimited version, e.g. an eternal slayer ring
				return null;
			}
			total += count * charges;
		}
		return total;
	}

	boolean hasCharge(TravelItem item)
	{
		final Integer charges = left.get(item);
		return charges == null || charges > 0;
	}

	void spend(TravelItem item)
	{
		left.computeIfPresent(item, (k, n) -> n - 1);
	}
}
