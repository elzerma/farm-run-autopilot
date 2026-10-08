package com.farmrunautopilot.route;

import com.farmrunautopilot.settings.RouteMode;
import java.util.Collections;
import java.util.List;
import lombok.Value;

@Value
public class Route
{
	public static final Route EMPTY = new Route(Collections.emptyList(), RouteMode.AUTOPILOT, 0, 0);

	List<RouteStop> stops;
	RouteMode mode;
	/** Estimated seconds of travel, including the walk to a bank at the end when that's switched on. */
	double travelSeconds;
	/** Rough seconds spent at the patches themselves. */
	double patchSeconds;
}
