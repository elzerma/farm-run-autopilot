package com.farmrunautopilot.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Route optimisation modes (SPEC 12.1), named in Travel > Route as the Run tab says them (docs/plans/fresh-eyes-audit.md,
 * T3). Saved by the constant's name, so these can change.
 */
@Getter
@RequiredArgsConstructor
public enum RouteMode
{
	AUTOPILOT("Fastest (default)"),
	META("The wiki's order"),
	OFF("My own order");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
