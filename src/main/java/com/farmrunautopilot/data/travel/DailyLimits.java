package com.farmrunautopilot.data.travel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Diary teleports limited to a number of uses per day (wiki, checked 2026-10-10): Ardougne cloak 2 (3) and 3 (5)
 * to the farm, and Explorer's ring 2 (3) to the cabbage patch. Higher tiers are unlimited. (Rada's blessing 3
 * also has 3 a day to Mount Karuulm, but nobody spends those on a farm run, so it isn't tracked.)
 */
public final class DailyLimits
{
	/**
	 * The game value counting today's uses of each: they matched "You have used 1 of your 3 Ardougne Farm teleports
	 * for today." and "You have used 1 of your 3 Cabbage teleports for today." But with all 3 of an Ardougne cloak 2's
	 * used, a route still counted on it (elzerma, 2026-10-18; by its name the cloak's may hold only the count's low
	 * bits), so what the game says in chat is kept too and the higher counts ({@code supply.DailyUseTracker}); the dev
	 * log says when the two differ.
	 */
	private static final Map<TravelMethod, Integer> USED_TODAY = new EnumMap<>(TravelMethod.class);
	/**
	 * The varbit holding the rest of a count split in two. The Ardougne cloak's is: ARDOUGNE_CLOAK_LOWBITS is one bit
	 * (varp 635 bit 18, from when cloak 2 gave one farm teleport a day), so all 3 of a cloak 2's uses read as 1 and a
	 * used-up cloak was planned (elzerma, 2026-10-18). Its high bits aren't in varp 635 (full) nor varbit 6070 (0 with
	 * 3 used); {@code supply.DailyUseTracker} logs the game variables that change on a farm teleport, so the second use
	 * of a day shows where they are. Until then, none: the low bit and the chat count.
	 */
	private static final Map<TravelMethod, Integer> HIGH_BITS = new EnumMap<>(TravelMethod.class);

	static
	{
		USED_TODAY.put(TravelMethod.ARDOUGNE_CLOAK_FARM, VarbitID.ARDOUGNE_CLOAK_LOWBITS);
		USED_TODAY.put(TravelMethod.EXPLORERS_RING_CABBAGE_PATCH, VarbitID.LUMBRIDGE_CABBAGE_TELEPORT);
	}

	private DailyLimits()
	{
	}

	/** Teleports limited per day, with the game value that counts today's uses (its low bits, when split). */
	public static Map<TravelMethod, Integer> usedTodayVarbits()
	{
		return Collections.unmodifiableMap(USED_TODAY);
	}

	/** The varbit with the high bits of a count split in two, for the teleports whose count is. */
	public static Map<TravelMethod, Integer> usedTodayHighBits()
	{
		return Collections.unmodifiableMap(HIGH_BITS);
	}

	/**
	 * A count split in two, put back together.
	 *
	 * @param lowWidth how many bits the low part has
	 */
	public static int combine(int low, int high, int lowWidth)
	{
		return low | high << lowWidth;
	}

	/**
	 * Uses per day of a teleport with the best tier the player holds, or null if unlimited (or not limited).
	 *
	 * @param holds whether the player holds an item ID anywhere
	 */
	public static Integer perDay(TravelMethod method, IntPredicate holds)
	{
		switch (method)
		{
			case ARDOUGNE_CLOAK_FARM:
				if (holds.test(ItemID.ARDY_CAPE_ELITE))
				{
					return null;
				}
				if (holds.test(ItemID.ARDY_CAPE_HARD))
				{
					return 5;
				}
				return holds.test(ItemID.ARDY_CAPE_MEDIUM) ? Integer.valueOf(3) : null;
			case EXPLORERS_RING_CABBAGE_PATCH:
				if (holds.test(ItemID.LUMBRIDGE_RING_ELITE) || holds.test(ItemID.LUMBRIDGE_RING_HARD))
				{
					return null;
				}
				return holds.test(ItemID.LUMBRIDGE_RING_MEDIUM) ? Integer.valueOf(3) : null;
			default:
				return null;
		}
	}
}
