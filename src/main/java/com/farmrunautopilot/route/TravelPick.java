package com.farmrunautopilot.route;

import com.farmrunautopilot.data.travel.Spell;
import lombok.Value;

/**
 * How a location is reached: the leg (teleport and how: directly, through the house, ...) and, for a spell
 * cast directly, whether its tablet is used. For the Travel tab's "Auto (best)" and override labels.
 */
@Value
public class TravelPick
{
	RouteStop stop;
	/** A spell taken from its tablet rather than cast from runes. */
	boolean tablet;

	/** e.g. "Camelot Teleport (tablet)", "House portal nexus: Catherby Teleport", "Walk". */
	public String describe()
	{
		final String travel = stop.describeTravel();
		final Spell spell = stop.getMethod() != null ? stop.getMethod().getSpell() : null;
		if (spell == null || stop.getDeparture() != Departure.DIRECT)
		{
			return travel;
		}
		return travel + (tablet ? " (tablet)" : " (runes)");
	}
}
