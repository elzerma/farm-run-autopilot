package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import java.util.ArrayList;
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
	/** Rough inventory slots needed at the start. */
	int slots;
	/** Which crop goes in which patch (used by the step guide). */
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

	/**
	 * The same checklist counted against what the player holds now: during a run the plan stays as it was when
	 * the run started, while the player banks.
	 */
	public SupplyPlan counted(Holdings holdings)
	{
		final List<SupplyLine> now = new ArrayList<>();
		for (SupplyLine line : lines)
		{
			now.add(line.counted(holdings));
		}
		return new SupplyPlan(Collections.unmodifiableList(now), patchCounts, notDue, travelPlan, warnings, coins,
			runeSummary, slots, plantings);
	}

	/**
	 * Required lines that are neither carried nor stored at the tool leprechaun (every stop has one), nor in the
	 * seed vault when it can be used here.
	 *
	 * @param vaultHere the seed vault can be used here (the Farming Guild)
	 */
	public int missingCount(boolean vaultHere)
	{
		int missing = 0;
		for (SupplyLine line : lines)
		{
			if (line.getGroup() == SupplyLine.Group.OPTIONAL || line.isCoveredOtherwise())
			{
				continue;
			}
			final int leprechaun = line.getWhere().getOrDefault(Holdings.Source.LEPRECHAUN, 0);
			final int vault = vaultHere ? line.getWhere().getOrDefault(Holdings.Source.SEED_VAULT, 0) : 0;
			if (line.getCarried() + leprechaun + vault < line.getNeed())
			{
				missing++;
			}
		}
		return missing;
	}

	/** What the player has left once this part of the run has used up its seeds, payments, runes and so on. */
	public Holdings leftAfter(Holdings holdings)
	{
		Holdings left = holdings;
		for (SupplyLine line : lines)
		{
			if (line.isConsumed())
			{
				left = left.use(line.getItemIds(), line.getNeed());
			}
		}
		return left;
	}
}
