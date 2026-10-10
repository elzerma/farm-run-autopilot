package com.farmrunautopilot.data.travel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Ways to reach a fairy ring before dialling a code, other than the house ring (which comes from My POH).
 * Tile counts are from the wiki's fairy ring page (teleport arrival to the ring); the Lumbridge route is an
 * estimate (UNVERIFIED). Each can be switched off in Rules &gt; Travel.
 */
@Getter
public enum FairyRingAccess
{
	/** The ring next to the stop just finished, walking the same distance as arriving there by ring. */
	NEARBY("Fairy ring by the last stop", null, 0),
	// Every cloak tier teleports to the Monastery as often as wanted
	ARDOUGNE_CLOAK("Ardougne cloak (Monastery), ring DJP", null, 51, TravelItem.ARDOUGNE_CLOAK,
		TravelItem.ARDOUGNE_CLOAK_1),
	SLAYER_RING("Slayer ring (Fremennik Slayer Dungeon), ring AJR", null, 16, TravelItem.SLAYER_RING),
	QUEST_CAPE("Quest point cape (Legends' Guild), ring BLR", null, 12, TravelItem.QUEST_POINT_CAPE),
	// UNVERIFIED: about 55 tiles to the swamp shed and 40 in Zanaris to the ring
	LUMBRIDGE("Lumbridge Teleport, then Zanaris through the swamp shed", Spell.LUMBRIDGE_TELEPORT, 95);

	private final String displayName;
	/** The teleport spell (or its tablet), or null. */
	private final Spell spell;
	/** Walk from the teleport's arrival to the ring. */
	private final int tiles;
	/** Teleport items that work, best first; empty for spells and walking. */
	private final List<TravelItem> items;

	FairyRingAccess(String displayName, Spell spell, int tiles, TravelItem... items)
	{
		this.displayName = displayName;
		this.spell = spell;
		this.tiles = tiles;
		this.items = Collections.unmodifiableList(Arrays.asList(items));
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
