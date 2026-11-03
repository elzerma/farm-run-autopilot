package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Which patches the next run covers (SPEC 10 "due rules").
 */
@Value
public class RunSelection
{
	public static final RunSelection EMPTY = new RunSelection(Collections.emptyList(), Collections.emptySet(),
		Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap());

	/** Patches to visit, in catalogue order. */
	List<Patch> patches;
	/** Run types in this run. */
	Set<PatchType> includedTypes;
	/**
	 * Ticked run types left out because too few of their patches are due, with when enough will be
	 * (epoch seconds; 0 if unknown).
	 */
	Map<PatchType, Long> skippedTypes;
	/** Patches of included types left out because they aren't due, e.g. "Taverley tree: Magic - ready in 1h". */
	List<String> notDue;
	/** How many of each ticked type's usable patches are due right now. */
	Map<PatchType, Integer> dueCounts;
	/** The Farming Guild's contract work, {@link ContractPlanner.Plan#OFF} with contracts off. */
	ContractPlanner.Plan contract;

	public RunSelection(List<Patch> patches, Set<PatchType> includedTypes, Map<PatchType, Long> skippedTypes,
		List<String> notDue, Map<PatchType, Integer> dueCounts)
	{
		this(patches, includedTypes, skippedTypes, notDue, dueCounts, ContractPlanner.Plan.OFF);
	}

	public RunSelection(List<Patch> patches, Set<PatchType> includedTypes, Map<PatchType, Long> skippedTypes,
		List<String> notDue, Map<PatchType, Integer> dueCounts, ContractPlanner.Plan contract)
	{
		this.patches = patches;
		this.includedTypes = includedTypes;
		this.skippedTypes = skippedTypes;
		this.notDue = notDue;
		this.dueCounts = dueCounts;
		this.contract = contract;
	}
}
