package com.farmrunautopilot.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How Auto (best) picks each stop's teleport. */
@Getter
@RequiredArgsConstructor
public enum TravelStyle
{
	FASTEST("Fastest", "The quickest way, whatever it uses up"),
	PREFER_FREE("Prefer free", "Capes, unlimited items, spirit trees, fairy rings, and your house when a construction "
		+ "or max cape gets you there, when only a little slower"),
	SAVE_CHARGES("Save charges", "Avoid using up jewellery charges and daily teleports when something else is only "
		+ "a little slower"),
	FEWEST_ITEMS("Fewest items to bring", "Ways that need fewer separate items in your inventory, e.g. one house "
		+ "tablet for every nexus stop instead of a tablet per stop");

	private final String displayName;
	private final String description;

	@Override
	public String toString()
	{
		return displayName;
	}
}
