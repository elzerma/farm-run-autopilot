package com.farmrunautopilot.data.poh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Where the house portal can be moved to, with the Construction level needed (SPEC 8.3).
 */
@Getter
@RequiredArgsConstructor
public enum HousePortal
{
	RIMMINGTON("Rimmington", 1),
	TAVERLEY("Taverley", 10),
	POLLNIVNEACH("Pollnivneach", 20),
	HOSIDIUS("Hosidius", 25),
	RELLEKKA("Rellekka", 30),
	ALDARIN("Aldarin", 35),
	BRIMHAVEN("Brimhaven", 40),
	YANILLE("Yanille", 50),
	PRIFDDINAS("Prifddinas", 70);

	private final String displayName;
	private final int constructionLevel;
}
