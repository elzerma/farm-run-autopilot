package com.farmrunautopilot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

/**
 * A grimy herb from a herb patch, its clean form and the Herblore level to clean it (OSRS Wiki, Herb, checked
 * 2026-10-30), for cleaning herbs at the patch before noting them (docs/plans/patch-actions.md, P2).
 */
@Getter
@RequiredArgsConstructor
public enum GrimyHerb
{
	GUAM(ItemID.UNIDENTIFIED_GUAM, ItemID.GUAM_LEAF, 3),
	MARRENTILL(ItemID.UNIDENTIFIED_MARENTILL, ItemID.MARENTILL, 5),
	TARROMIN(ItemID.UNIDENTIFIED_TARROMIN, ItemID.TARROMIN, 11),
	HARRALANDER(ItemID.UNIDENTIFIED_HARRALANDER, ItemID.HARRALANDER, 20),
	RANARR(ItemID.UNIDENTIFIED_RANARR, ItemID.RANARR_WEED, 25),
	TOADFLAX(ItemID.UNIDENTIFIED_TOADFLAX, ItemID.TOADFLAX, 30),
	IRIT(ItemID.UNIDENTIFIED_IRIT, ItemID.IRIT_LEAF, 40),
	AVANTOE(ItemID.UNIDENTIFIED_AVANTOE, ItemID.AVANTOE, 48),
	KWUARM(ItemID.UNIDENTIFIED_KWUARM, ItemID.KWUARM, 54),
	HUASCA(ItemID.UNIDENTIFIED_HUASCA, ItemID.HUASCA, 58),
	SNAPDRAGON(ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.SNAPDRAGON, 59),
	CADANTINE(ItemID.UNIDENTIFIED_CADANTINE, ItemID.CADANTINE, 65),
	LANTADYME(ItemID.UNIDENTIFIED_LANTADYME, ItemID.LANTADYME, 67),
	DWARF_WEED(ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.DWARF_WEED, 70),
	TORSTOL(ItemID.UNIDENTIFIED_TORSTOL, ItemID.TORSTOL, 75);

	private final int grimyItemId;
	private final int cleanItemId;
	private final int herbloreLevel;

	/** The grimy herb with this item ID, or null for anything else. */
	public static GrimyHerb of(int itemId)
	{
		for (GrimyHerb herb : values())
		{
			if (herb.grimyItemId == itemId)
			{
				return herb;
			}
		}
		return null;
	}
}
