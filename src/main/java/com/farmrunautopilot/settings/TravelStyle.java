package com.farmrunautopilot.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How each stop's way is chosen when ways are close (Travel > Ways to travel > Travel style). "Fewest items to bring"
 * went in T4 (elzerma, 2026-10-23), as Fastest weighs a slot against the bank visit it saves; a save naming it loads as
 * null, which {@link RunConfig#sanitise()} makes Fastest.
 */
@Getter
@RequiredArgsConstructor
public enum TravelStyle
{
	FASTEST("Fastest", "The quickest way, whatever it uses up"),
	PREFER_FREE("Prefer free", "Capes, unlimited items, spirit trees, fairy rings, and your house when a construction "
		+ "or max cape gets you there, when only a little slower"),
	SAVE_CHARGES("Save charges", "Avoid using up jewellery charges and daily teleports when something else is only "
		+ "a little slower");

	private final String displayName;
	private final String description;

	@Override
	public String toString()
	{
		return displayName;
	}
}
