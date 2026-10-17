package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.Location;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * The staves a part of the run swaps between (issue #7): which to wield before setting off for which stop. Worked out by
 * {@link StaffPlanner}; {@link #NONE} when one staff (or none) stays on all the way.
 */
@Value
public class StaffSwaps
{
	public static final StaffSwaps NONE = new StaffSwaps(Collections.emptyMap(), null, Collections.emptySet());

	/** A staff to wield. */
	@Value
	public static class Swap
	{
		/** As the guide says it, e.g. "mist battlestaff". */
		String name;
		/** Any of these will do (the dramen or the lunar staff). */
		int[] itemIds;
	}

	/**
	 * Before setting off for each of these stops, the staff to wield, from the first stop that needs one: the first is
	 * the staff put on at the bank.
	 */
	Map<Location, Swap> wield;
	/** The staff for fairy rings when it's one of the staves swapped (so a trip to a bank by fairy ring needs it too). */
	Swap fairyRing;
	/** Every staff swapped between: none of them is put on at the bank once the run is under way. */
	Set<Integer> itemIds;

	public boolean isEmpty()
	{
		return wield.isEmpty();
	}
}
