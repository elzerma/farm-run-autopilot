package com.farmrunautopilot.data.travel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Ways to reach a fairy ring before dialling a code, other than the house ring (which comes from My POH): the ring by
 * the last stop, or a teleport item that lands by a ring. The travel atlas times the walk to the ring; these name the
 * way for the guide and the item to bring.
 */
@Getter
public enum FairyRingAccess
{
	/** The ring next to the stop just finished. */
	NEARBY("Fairy ring by the last stop", "from the ring by the last stop", null),
	// Every cloak tier teleports to the Monastery as often as wanted
	ARDOUGNE_CLOAK("Ardougne cloak (Monastery), ring DJP", "from DJP by Ardougne cloak", "DJP",
		TravelItem.ARDOUGNE_CLOAK,
		TravelItem.ARDOUGNE_CLOAK_1),
	SLAYER_RING("Slayer ring (Fremennik Slayer Dungeon), ring AJR", "from AJR by slayer ring", "AJR",
		TravelItem.SLAYER_RING),
	QUEST_CAPE("Quest point cape (Legends' Guild), ring BLR", "from BLR by quest point cape", "BLR",
		TravelItem.QUEST_POINT_CAPE);

	/** Which teleport to use and the ring it reaches. */
	private final String displayName;
	/** Short, after the ring being travelled to, e.g. "Fairy ring CIR (from AJR by slayer ring)". */
	private final String via;
	/** The ring it reaches, e.g. "AJR"; null for the ring by the last stop. */
	private final String ring;
	/** Teleport items that work, best first; empty for walking. */
	private final List<TravelItem> items;

	FairyRingAccess(String displayName, String via, String ring, TravelItem... items)
	{
		this.displayName = displayName;
		this.via = via;
		this.ring = ring;
		this.items = Collections.unmodifiableList(Arrays.asList(items));
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
