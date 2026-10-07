package com.farmrunautopilot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AchievementDiary
{
	ARDOUGNE("Ardougne"),
	FALADOR("Falador"),
	FREMENNIK("Fremennik"),
	KANDARIN("Kandarin"),
	KARAMJA("Karamja"),
	KOUREND_KEBOS("Kourend & Kebos"),
	LUMBRIDGE_DRAYNOR("Lumbridge & Draynor"),
	MORYTANIA("Morytania"),
	VARROCK("Varrock"),
	WESTERN_PROVINCES("Western Provinces");

	private final String displayName;

	public enum Tier
	{
		EASY, MEDIUM, HARD, ELITE
	}
}
