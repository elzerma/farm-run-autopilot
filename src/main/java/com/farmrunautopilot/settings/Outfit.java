package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.SupplyItems;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Clothing to suggest bringing for a run (added by Sean, 2026-10-07).
 */
@Getter
@RequiredArgsConstructor
public enum Outfit
{
	NONE("None", new int[0][]),
	GRACEFUL("Graceful outfit", SupplyItems.GRACEFUL),
	FARMERS("Farmer's outfit", SupplyItems.FARMERS_OUTFIT);

	private final String displayName;
	/** One entry per piece, each listing every variant of that piece. */
	private final int[][] pieces;

	@Override
	public String toString()
	{
		return displayName;
	}
}