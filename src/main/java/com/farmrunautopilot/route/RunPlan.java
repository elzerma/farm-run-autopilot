package com.farmrunautopilot.route;

import com.farmrunautopilot.supply.SupplyPlan;
import lombok.Value;

/**
 * Everything worked out for the next run: which patches, the route, and what to bring.
 */
@Value
public class RunPlan
{
	public static final RunPlan EMPTY = new RunPlan(RunSelection.EMPTY, Route.EMPTY, SupplyPlan.EMPTY);

	RunSelection selection;
	Route route;
	SupplyPlan supplies;
}
