package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.settings.RouteMode;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class Route
{
	public static final Route EMPTY = new Route(Collections.emptyList(), RouteMode.AUTOPILOT, 0, 0);

	List<RouteStop> stops;
	RouteMode mode;
	/** Estimated seconds of travel, including the walk to a bank at the end when that's switched on. */
	double travelSeconds;
	/** Rough seconds spent at the patches themselves. */
	double patchSeconds;
	/**
	 * Where the first leg sets off from when that isn't one of the stops: the Farming Guild, for seeds only in its
	 * seed vault or when the player is in the guild (issue #8). Null when it's from wherever the player is.
	 */
	Location startsFrom;

	public Route(List<RouteStop> stops, RouteMode mode, double travelSeconds, double patchSeconds)
	{
		this(stops, mode, travelSeconds, patchSeconds, null);
	}

	/** The run starts at this place: it's the first stop, or the first leg sets off from it. */
	public boolean setsOffFrom(Location location)
	{
		return startsFrom == location || !stops.isEmpty() && stops.get(0).getLocation() == location;
	}
}
