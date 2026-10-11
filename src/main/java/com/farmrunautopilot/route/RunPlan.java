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
	/** What to bring at the start: with bank stops, only what's needed before the first. */
	SupplyPlan supplies;
	/** What to do at each stop, one line per patch, e.g. "Herb: harvest guam and plant ranarr". */
	Map<Location, List<String>> objectives;
	/** Bank visits partway, in route order, when one inventory can't hold everything; empty if not needed. */
	List<BankStop> bankStops;

	public RunPlan(RunSelection selection, Route route, SupplyPlan supplies, Map<Location, List<String>> objectives)
	{
		this(selection, route, supplies, objectives, Collections.emptyList());
	}

	/** The bank visit right after this stop, or null. */
	public BankStop bankAfter(int stopIndex)
	{
		for (BankStop bank : bankStops)
		{
			if (bank.getAfterStop() == stopIndex)
			{
				return bank;
			}
		}
		return null;
	}
}
