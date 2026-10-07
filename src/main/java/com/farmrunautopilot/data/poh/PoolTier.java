package com.farmrunautopilot.data.poh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * POH superior garden pools (SPEC 8.3). The restoration pool does not restore run energy.
 */
@Getter
@RequiredArgsConstructor
public enum PoolTier
{
	RESTORATION(65, false),
	REVITALISATION(70, true),
	REJUVENATION(80, true),
	FANCY_REJUVENATION(85, true),
	ORNATE_REJUVENATION(90, true);

	private final int constructionLevel;
	private final boolean restoresRunEnergy;
}
