package com.farmrunautopilot.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How the work at a stop's patches is ordered (patch actions P1). elzerma, 2026-10-27: patch by patch by default;
 * grouping nearby patches is elective, and suits players without a bottomless compost bucket.
 */
@Getter
@RequiredArgsConstructor
public enum StopOrder
{
	/** Harvest, treat and plant each patch before the next; the stop's produce is noted before leaving. */
	PATCH_BY_PATCH("Patch by patch"),
	/** Nearby patches picked together, their produce noted and compost taken, then treated and planted together. */
	GROUP_NEARBY("Group nearby patches");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
