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
	/**
	 * Into the house by the spirit tree near the stop just finished ("Your house", once a spirit tree grows in the
	 * house), then out through its portal (issue #16).
	 */
	TREE_HOUSE_PORTAL(false, null, true),
	/** Into the house by a spirit tree, then the portal nexus. */
	TREE_HOUSE_NEXUS(false, null, true),
	/** Into the house by a spirit tree, then the jewellery box. */
	TREE_HOUSE_JEWELLERY_BOX(false, null, true),
	/** Into the house by a spirit tree, then the house fairy ring. */
	TREE_HOUSE_FAIRY_RING(false, null, true),
	/** Walk to the fairy ring by the stop just finished. */
	FAIRY_RING_NEARBY(false, FairyRingAccess.NEARBY),
	FAIRY_RING_ARDOUGNE_CLOAK(false, FairyRingAccess.ARDOUGNE_CLOAK),
	FAIRY_RING_SLAYER_RING(false, FairyRingAccess.SLAYER_RING),
	FAIRY_RING_QUEST_CAPE(false, FairyRingAccess.QUEST_CAPE),
	/** Back to the quetzal landing pad by the stop just finished, then Renu (no whistle charge). */
	RENU(false, null),
	/** A way from the travel atlas that isn't one of the plugin's travel methods: see the stop's {@link AtlasLeg}. */
	ATLAS(false, null),
	/** Just walk from the previous stop. */
	WALK(false, null),
	/** No unlocked way to get there. */
	NONE(false, null);

	/** Needs a Teleport to House first. */
	private final boolean viaHouse;
	/** How the fairy ring is reached, for the fairy ring departures; otherwise null. */
	private final FairyRingAccess fairyRingAccess;
	/** Into the house by a spirit tree rather than Teleport to House: needs no tablet, rune or cape. */
	private final boolean byTree;

	Departure(boolean viaHouse, FairyRingAccess fairyRingAccess)
	{
		this(viaHouse, fairyRingAccess, false);
	}

	Departure(boolean viaHouse, FairyRingAccess fairyRingAccess, boolean byTree)
	{
		this.viaHouse = viaHouse;
		this.fairyRingAccess = fairyRingAccess;
		this.byTree = byTree;
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
