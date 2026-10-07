package com.farmrunautopilot.supply;

import java.util.Map;
import lombok.Value;

/**
 * One checklist row (SPEC 11), e.g. "Ranarr seed - have 3 / need 8".
 */
@Value
public class SupplyLine
{
	public enum Group
	{
		TRAVEL("Travel"),
		RUNES("Runes"),
		SEEDS("Seeds & saplings"),
		PAYMENTS("Payments"),
		TOOLS("Tools & compost"),
		OPTIONAL("Optional");

		private final String displayName;

		Group(String displayName)
		{
			this.displayName = displayName;
		}

		public String getDisplayName()
		{
			return displayName;
		}
	}

	Group group;
	String name;
	int need;
	int have;
	/** Where the items are; empty if none held. */
	Map<Holdings.Source, Integer> where;
	/** Extra explanation for the tooltip, or null. */
	String note;
	/** Inventory slots this line takes at the start of the run. */
	int slots;
	/** Covered some other way (e.g. a bottomless compost bucket), even if {@link #have} is short. */
	boolean coveredOtherwise;

	public boolean isMet()
	{
		return coveredOtherwise || have >= need;
	}
}
