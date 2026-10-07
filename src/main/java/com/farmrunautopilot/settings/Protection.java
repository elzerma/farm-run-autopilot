package com.farmrunautopilot.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How a tree or fruit tree patch is protected from disease (SPEC 2, 13.2).
 */
@Getter
@RequiredArgsConstructor
public enum Protection
{
	PAY_GARDENER("Pay gardener"),
	COMPOST_ONLY("Compost only");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
