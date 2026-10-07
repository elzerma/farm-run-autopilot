package com.farmrunautopilot.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Route optimisation modes (SPEC 12.1).
 */
@Getter
@RequiredArgsConstructor
public enum RouteMode
{
	AUTOPILOT("On - Autopilot"),
	META("On - Meta (wiki order)"),
	OFF("Off (my own order)");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
