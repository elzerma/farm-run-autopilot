package com.farmrunautopilot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Rough walking distance buckets (SPEC 8). These are starting estimates only; milestone M8 replaces
 * them with the player's own timings.
 */
@Getter
@RequiredArgsConstructor
public enum Walk
{
	/** About 0-15 tiles, patch on screen. */
	SHORT(10),
	/** About 15-40 tiles. */
	MEDIUM(28),
	/** Over 40 tiles, or obstacles / NPC transport on the way. */
	LONG(55);

	private final int estimatedTiles;
}
