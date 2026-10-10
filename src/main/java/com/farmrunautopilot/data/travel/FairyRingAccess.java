package com.farmrunautopilot.data.travel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Ways to reach a fairy ring before dialling a code, other than the house ring (which comes from My POH).
 * Tile counts are from the wiki's fairy ring page (teleport arrival to the ring). The player picks one in
 * Rules &gt; Travel, or Auto; the ring by the last stop is always used when it's quicker.
 */
@Getter
public enum FairyRingAccess
{
	/** The ring next to the stop just finished, walking the same distance as arriving there by ring. */
	NEARBY("Fairy ring by the last stop", "from the ring by the last stop", 0),
	// Every cloak tier teleports to the Monastery as often as wanted
	ARDOUGNE_CLOAK("Ardougne cloak (Monastery), ring DJP", "from DJP by Ardougne cloak", 51,
		TravelItem.ARDOUGNE_CLOAK,
		TravelItem.ARDOUGNE_CLOAK_1),
	SLAYER_RING("Slayer ring (Fremennik Slayer Dungeon), ring AJR", "from AJR by slayer ring", 16,
		TravelItem.SLAYER_RING),
	QUEST_CAPE("Quest point cape (Legends' Guild), ring BLR", "from BLR by quest point cape", 12,
		TravelItem.QUEST_POINT_CAPE);

	/** Which teleport to use and the ring it reaches. */
	private final String displayName;
	/** Short, after the ring being travelled to, e.g. "Fairy ring CIR (from AJR by slayer ring)". */
	private final String via;
	/** Walk from the teleport's arrival to the ring. */
	private final int tiles;
	/** Teleport items that work, best first; empty for walking. */
	private final List<TravelItem> items;

	FairyRingAccess(String displayName, String via, int tiles, TravelItem... items)
	{
		this.displayName = displayName;
		this.via = via;
		this.tiles = tiles;
		this.items = Collections.unmodifiableList(Arrays.asList(items));
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
