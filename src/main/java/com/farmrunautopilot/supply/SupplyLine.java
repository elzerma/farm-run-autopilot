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

	public enum Status
	{
		/** Enough in the inventory or worn (rune pouch counts as inventory). */
		CARRIED,
		/** Enough in total, but some must be taken out of the bank or other storage. */
		IN_STORAGE,
		/** Not enough anywhere. */
		MISSING
	}

	Group group;
	String name;
	int need;
	/** Held anywhere the plugin can see. */
	int have;
	/** Held in the inventory or worn. */
	int carried;
	/** Where the items are; empty if none held. */
	Map<Holdings.Source, Integer> where;
	/** Extra explanation for the tooltip, or null. */
	String note;
	/** Inventory slots this line takes at the start of the run. */
	int slots;
	/** Covered some other way (e.g. a bottomless compost bucket), even if {@link #have} is short. */
	boolean coveredOtherwise;
	/** Where to change this, e.g. "Setup > Crops", or null. */
	String changeIn;
	/** Items that satisfy this line (any of them), e.g. every charge of a ring. Used by the bank tab. */
	int[] itemIds;

	public boolean isMet()
	{
		return coveredOtherwise || have >= need;
	}

	public Status getStatus()
	{
		if (carried >= need)
		{
			return Status.CARRIED;
		}
		return isMet() ? Status.IN_STORAGE : Status.MISSING;
	}
}
