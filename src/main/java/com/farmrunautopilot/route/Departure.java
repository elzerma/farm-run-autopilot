package com.farmrunautopilot.route;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How a leg starts: straight from wherever the player is, or through their house first.
 */
@Getter
@RequiredArgsConstructor
public enum Departure
{
	/** Use the travel method directly (cast, rub, walk to a nearby spirit tree, ...). */
	DIRECT(false),
	/** Teleport to House, then the portal nexus. */
	POH_NEXUS(true),
	/** Teleport to House, then the jewellery box. */
	POH_JEWELLERY_BOX(true),
	/** Teleport to House, then the house fairy ring. */
	POH_FAIRY_RING(true),
	/** Teleport to House, then the house spirit tree. */
	POH_SPIRIT_TREE(true),
	/** Just walk from the previous stop. */
	WALK(false),
	/** No unlocked way to get there. */
	NONE(false);

	/** Needs a Teleport to House first. */
	private final boolean viaHouse;
}
