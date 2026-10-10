package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Everything worked out for the next run: which patches, the route, and what to bring.
 */
@Value
@AllArgsConstructor
public class RunPlan
{
	public static final RunPlan EMPTY = new RunPlan(RunSelection.EMPTY, Route.EMPTY, SupplyPlan.EMPTY,
		Collections.emptyMap());

	RunSelection selection;
	Route route;
	/** What to bring at the start: with a bank stop, only what's needed before it. */
	SupplyPlan supplies;
	/** What to do at each stop, one line per patch, e.g. "Herb: harvest guam and plant ranarr". */
	Map<Location, List<String>> objectives;
	/** A bank visit partway, when one inventory can't hold everything; null if not needed. */
	BankStop bankStop;

	public RunPlan(RunSelection selection, Route route, SupplyPlan supplies, Map<Location, List<String>> objectives)
	{
		this(selection, route, supplies, objectives, null);
	}
}
