package com.farmrunautopilot.route;

import com.farmrunautopilot.data.travel.FairyRingAccess;
import lombok.Getter;

/**
 * How a leg starts: straight from wherever the player is, through their house first, or by getting to a
 * fairy ring some other way.
 */
@Getter
public enum Departure
{
	/** Use the travel method directly (cast, rub, walk to a nearby spirit tree, ...). */
	DIRECT(false, null),
	/** Teleport to House, then the portal nexus. */
	POH_NEXUS(true, null),
	/** Teleport to House, then the jewellery box. */
	POH_JEWELLERY_BOX(true, null),
	/** Teleport to House, then the house fairy ring. */
	POH_FAIRY_RING(true, null),
	/** Teleport to House, then the house spirit tree. */
	POH_SPIRIT_TREE(true, null),
	/** Walk to the fairy ring by the stop just finished. */
	FAIRY_RING_NEARBY(false, FairyRingAccess.NEARBY),
	FAIRY_RING_ARDOUGNE_CLOAK(false, FairyRingAccess.ARDOUGNE_CLOAK),
	FAIRY_RING_SLAYER_RING(false, FairyRingAccess.SLAYER_RING),
	FAIRY_RING_QUEST_CAPE(false, FairyRingAccess.QUEST_CAPE),
	/** Just walk from the previous stop. */
	WALK(false, null),
	/** No unlocked way to get there. */
	NONE(false, null);

	/** Needs a Teleport to House first. */
	private final boolean viaHouse;
	/** How the fairy ring is reached, for the fairy ring departures; otherwise null. */
	private final FairyRingAccess fairyRingAccess;

	Departure(boolean viaHouse, FairyRingAccess fairyRingAccess)
	{
		this.viaHouse = viaHouse;
		this.fairyRingAccess = fairyRingAccess;
	}

	/** The departure that reaches a fairy ring this way. */
	public static Departure of(FairyRingAccess access)
	{
		for (Departure departure : values())
		{
			if (departure.fairyRingAccess == access)
			{
				return departure;
			}
		}
		throw new IllegalArgumentException(access.name());
	}
}
