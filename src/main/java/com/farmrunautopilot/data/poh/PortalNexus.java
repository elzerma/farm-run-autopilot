package com.farmrunautopilot.data.poh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Portal nexus tiers and the farming-relevant destinations it can hold (SPEC 8.3).
 */
public final class PortalNexus
{
	private PortalNexus()
	{
	}

	@Getter
	@RequiredArgsConstructor
	public enum Tier
	{
		MARBLE(72, 4),
		GILDED(82, 8),
		CRYSTALLINE(92, 41);

		private final int constructionLevel;
		private final int maxDestinations;
	}

	@Getter
	@RequiredArgsConstructor
	public enum Destination
	{
		ARDOUGNE("Ardougne"),
		CATHERBY("Catherby"),
		CIVITAS_ILLA_FORTIS("Civitas illa Fortis"),
		FENKENSTRAINS_CASTLE("Fenkenstrain's Castle"),
		FISHING_GUILD("Fishing Guild"),
		HARMONY_ISLAND("Harmony Island"),
		KHARYRLL("Kharyrll"),
		TROLL_STRONGHOLD("Troll Stronghold"),
		WEISS("Weiss");

		private final String displayName;
	}
}
