package com.farmrunautopilot.route;

import com.farmrunautopilot.data.travel.DailyLimits;
import com.farmrunautopilot.data.travel.ItemCharges;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.ItemChargeTracker;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Teleport charges available for one run, pooled per item: every use of the same item in the run draws from
 * one pool, whichever teleport or fairy ring it's used for.
 */
public final class ChargeBudget
{
	/** Charges left per limited item; items not here never run out. */
	private final Map<TravelItem, Integer> left = new EnumMap<>(TravelItem.class);
	/** Uses left today per daily-limited teleport; teleports not here are unlimited. */
	private final Map<TravelMethod, Integer> leftToday = new EnumMap<>(TravelMethod.class);

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
		for (TravelMethod method : DailyLimits.usedTodayVarbits().keySet())
		{
			final Integer uses = leftToday(method, holdings);
			if (uses != null)
			{
				leftToday.put(method, uses);
			}
		}
	}

	/** The perfected quetzal whistle (i) never runs out. */
	private static boolean holdsInfinite(TravelItem item, Holdings holdings)
	{
		return item == TravelItem.QUETZAL_WHISTLE && holdings.count(ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE) > 0;
	}

	/** A self-charged item is held but its charges aren't known yet: the player should Check it. */
	public static boolean chargesUnknown(TravelItem item, Holdings holdings)
	{
		return ItemChargeTracker.TRACKED.contains(item) && holdings.countAny(item.getItemIds()) > 0
			&& !holdsInfinite(item, holdings) && !holdings.getItemCharges().containsKey(item);
	}

	/** Uses left today of a daily-limited teleport, or null if it's unlimited for the item held. */
	public static Integer leftToday(TravelMethod method, Holdings holdings)
	{
		final Integer perDay = DailyLimits.perDay(method, id -> holdings.count(id) > 0);
		if (perDay == null)
		{
			return null;
		}
		final int used = holdings.getUsedToday().getOrDefault(method, 0);
		return Math.max(0, perDay - used);
	}

	boolean hasUseToday(TravelMethod method)
	{
		final Integer uses = leftToday.get(method);
		return uses == null || uses > 0;
	}

	void spendUseToday(TravelMethod method)
	{
		leftToday.computeIfPresent(method, (k, n) -> n - 1);
	}

	/**
	 * Charges the player has of an item, across every piece held, or null if it never runs out (an unlimited
	 * version is held, or the item isn't limited).
	 */
	public static Integer held(TravelItem item, Holdings holdings)
	{
		if (ItemChargeTracker.TRACKED.contains(item))
		{
			// Self-charged: known from chat once checked; unknown counts as usable
			return holdings.countAny(item.getItemIds()) == 0 || holdsInfinite(item, holdings) ? null
				: holdings.getItemCharges().get(item);
		}
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
