package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Everything worked out for the next run: which patches, the route, and what to bring.
 */
@Value
public class RunPlan
{
	public static final RunPlan EMPTY = new RunPlan(RunSelection.EMPTY, Route.EMPTY, SupplyPlan.EMPTY,
		Collections.emptyMap());

	RunSelection selection;
	Route route;
	SupplyPlan supplies;
	/** What to do at each stop, one line per patch, e.g. "Herb: harvest guam and plant ranarr". */
	Map<Location, List<String>> objectives;
}
