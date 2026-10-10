package com.farmrunautopilot.data.travel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * How many teleports each charged item holds. Each charge level is its own item (a games necklace (8) is a
 * different item from a games necklace (7)), so the charges a player has can be read from what they hold.
 * Items not listed here never run out (capes, the Ectophial, eternal and infinite versions), or their charges
 * aren't known this way yet.
 */
public final class ItemCharges
{
	private static final Map<Integer, Integer> CHARGES = new HashMap<>();
	/** Can be recharged; the rest crumble or turn into something else when used up. */
	private static final Set<TravelItem> RECHARGEABLE = Collections.unmodifiableSet(EnumSet.of(
		TravelItem.RING_OF_WEALTH, TravelItem.SKILLS_NECKLACE, TravelItem.COMBAT_BRACELET,
		TravelItem.AMULET_OF_GLORY, TravelItem.TELEPORT_CRYSTAL));

	static
	{
		numbered(ItemID.RING_OF_WEALTH_1, ItemID.RING_OF_WEALTH_2, ItemID.RING_OF_WEALTH_3, ItemID.RING_OF_WEALTH_4,
			ItemID.RING_OF_WEALTH_5);
		numbered(ItemID.RING_OF_WEALTH_I1, ItemID.RING_OF_WEALTH_I2, ItemID.RING_OF_WEALTH_I3,
			ItemID.RING_OF_WEALTH_I4, ItemID.RING_OF_WEALTH_I5);
		numbered(ItemID.JEWL_NECKLACE_OF_SKILLS_1, ItemID.JEWL_NECKLACE_OF_SKILLS_2, ItemID.JEWL_NECKLACE_OF_SKILLS_3,
			ItemID.JEWL_NECKLACE_OF_SKILLS_4, ItemID.JEWL_NECKLACE_OF_SKILLS_5, ItemID.JEWL_NECKLACE_OF_SKILLS_6);
		numbered(ItemID.NECKLACE_OF_MINIGAMES_1, ItemID.NECKLACE_OF_MINIGAMES_2, ItemID.NECKLACE_OF_MINIGAMES_3,
			ItemID.NECKLACE_OF_MINIGAMES_4, ItemID.NECKLACE_OF_MINIGAMES_5, ItemID.NECKLACE_OF_MINIGAMES_6,
			ItemID.NECKLACE_OF_MINIGAMES_7, ItemID.NECKLACE_OF_MINIGAMES_8);
		numbered(ItemID.JEWL_BRACELET_OF_COMBAT_1, ItemID.JEWL_BRACELET_OF_COMBAT_2, ItemID.JEWL_BRACELET_OF_COMBAT_3,
			ItemID.JEWL_BRACELET_OF_COMBAT_4, ItemID.JEWL_BRACELET_OF_COMBAT_5, ItemID.JEWL_BRACELET_OF_COMBAT_6);
		numbered(ItemID.AMULET_OF_GLORY_1, ItemID.AMULET_OF_GLORY_2, ItemID.AMULET_OF_GLORY_3, ItemID.AMULET_OF_GLORY_4,
			ItemID.AMULET_OF_GLORY_5, ItemID.AMULET_OF_GLORY_6);
		numbered(ItemID.SLAYER_RING_1, ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_4,
			ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_6, ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_8);
		numbered(ItemID.MOURNING_TELEPORT_CRYSTAL_1, ItemID.MOURNING_TELEPORT_CRYSTAL_2,
			ItemID.MOURNING_TELEPORT_CRYSTAL_3, ItemID.MOURNING_TELEPORT_CRYSTAL_4, ItemID.MOURNING_TELEPORT_CRYSTAL_5);
		// Used up on teleporting
		CHARGES.put(ItemID.STRONGHOLD_TELEPORT_BASALT, 1);
		CHARGES.put(ItemID.WEISS_TELEPORT_BASALT, 1);
	}

	private ItemCharges()
	{
	}

	/** The listed items hold 1, 2, 3, ... charges in order. */
	private static void numbered(int... itemIds)
	{
		for (int i = 0; i < itemIds.length; i++)
		{
			CHARGES.put(itemIds[i], i + 1);
		}
	}

	/** Charges one of this item holds, or null if it never runs out (or isn't tracked this way). */
	public static Integer chargesOf(int itemId)
	{
		return CHARGES.get(itemId);
	}

	/** Whether any version of this item has limited charges. */
	public static boolean isLimited(TravelItem item)
	{
		for (int id : item.getItemIds())
		{
			if (CHARGES.containsKey(id))
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isRechargeable(TravelItem item)
	{
		return RECHARGEABLE.contains(item);
	}
}
