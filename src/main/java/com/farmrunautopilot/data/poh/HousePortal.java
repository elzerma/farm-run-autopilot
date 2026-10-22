package com.farmrunautopilot.data.poh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where the house portal can be (SPEC 8.3), with the game's POH_HOUSE_LOCATION value for a house there. UNVERIFIED:
 * the values are Shortest Path's (its Teleport to House rows in the travel atlas), not yet seen in game; the help
 * list's "Where your house is" checks them.
 */
@Getter
@RequiredArgsConstructor
public enum HousePortal
{
	RIMMINGTON("Rimmington", 1),
	TAVERLEY("Taverley", 2),
	POLLNIVNEACH("Pollnivneach", 3),
	HOSIDIUS("Hosidius", 8),
	RELLEKKA("Rellekka", 4),
	ALDARIN("Aldarin", 13),
	BRIMHAVEN("Brimhaven", 5),
	YANILLE("Yanille", 6),
	PRIFDDINAS("Prifddinas", 9);

	private final String displayName;
	/** The game's POH_HOUSE_LOCATION value for a house here. */
	private final int gameValue;

	/** Where a POH_HOUSE_LOCATION value puts the house, or null: 0 is no house, and others a place not known here. */
	public static HousePortal of(int gameValue)
	{
		for (HousePortal portal : values())
		{
			if (portal.gameValue == gameValue)
			{
				return portal;
			}
		}
		return null;
	}
}
