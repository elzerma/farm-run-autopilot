package com.farmrunautopilot.data.poh;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Other POH builds that matter for travel (SPEC 8.3).
 */
@Getter
@RequiredArgsConstructor
public enum PohAmenity
{
	FAIRY_RING("Fairy ring", 85, 0),
	SPIRIT_TREE("Spirit tree", 75, 83),
	SPIRIT_TREE_AND_FAIRY_RING("Spirit tree & fairy ring", 95, 83);

	private final String displayName;
	private final int constructionLevel;
	private final int farmingLevel;
}
