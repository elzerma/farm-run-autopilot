package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * The full checklist for the next run, plus the summary shown around it in the Run tab.
 */
@Value
public class SupplyPlan
{
	public static final SupplyPlan EMPTY = new SupplyPlan(Collections.emptyList(), Collections.emptyMap(),
		Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, "", 0, Collections.emptyMap());

	List<SupplyLine> lines;
	/** Patches in this run per type. */
	Map<PatchType, Integer> patchCounts;
	/** Selected patches left out because they aren't due, e.g. "Taverley tree: Magic - ready in 1h 40m". */
	List<String> notDue;
	/** How each stop will be reached, e.g. "Catherby: Catherby Teleport (tablet)". */
	List<String> travelPlan;
	List<String> warnings;
	int coins;
	/** e.g. "7 law, 3 water (staff covers air)". */
	String runeSummary;
	/** Rough inventory slots needed at the start; M8 refines this with a full simulation. */
	int slots;
	/** Which crop goes in which patch (used by the step guide in M7). */
	Map<Patch, Crop> plantings;

	public int patchTotal()
	{
		int total = 0;
		for (int n : patchCounts.values())
		{
			total += n;
		}
		return total;
	}
}
