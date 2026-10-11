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
	/** Where to change this, e.g. "Farm > Crops", or null. */
	String changeIn;
	/** Items that satisfy this line (any of them), e.g. every charge of a ring. Used by the bank tab. */
	int[] itemIds;
	/**
	 * Used up on the run (seeds, payments, runes, tablets, compost, potions), as opposed to kept (tools, teleport
	 * jewellery): with bank visits, a later part of the run can't count on what an earlier part used.
	 */
	boolean consumed;
	/** Worn rather than carried (a teleport ring or cape, the chosen outfit): the guide says to put it on. */
	boolean wear;

	public SupplyLine(Group group, String name, int need, int have, int carried, Map<Holdings.Source, Integer> where,
		String note, int slots, boolean coveredOtherwise, String changeIn, int[] itemIds)
	{
		this(group, name, need, have, carried, where, note, slots, coveredOtherwise, changeIn, itemIds, false);
	}

	public SupplyLine(Group group, String name, int need, int have, int carried, Map<Holdings.Source, Integer> where,
		String note, int slots, boolean coveredOtherwise, String changeIn, int[] itemIds, boolean consumed)
	{
		this(group, name, need, have, carried, where, note, slots, coveredOtherwise, changeIn, itemIds, consumed, false);
	}

	public SupplyLine(Group group, String name, int need, int have, int carried, Map<Holdings.Source, Integer> where,
		String note, int slots, boolean coveredOtherwise, String changeIn, int[] itemIds, boolean consumed, boolean wear)
	{
		this.group = group;
		this.name = name;
		this.need = need;
		this.have = have;
		this.carried = carried;
		this.where = where;
		this.note = note;
		this.slots = slots;
		this.coveredOtherwise = coveredOtherwise;
		this.changeIn = changeIn;
		this.itemIds = itemIds;
		this.consumed = consumed;
		this.wear = wear;
	}

	/** The same line, counted against what the player holds now (e.g. during a run, after banking). */
	public SupplyLine counted(Holdings holdings)
	{
		return new SupplyLine(group, name, need, holdings.countAny(itemIds), holdings.carriedOnly().countAny(itemIds),
			holdings.where(itemIds), note, slots, coveredOtherwise, changeIn, itemIds, consumed, wear);
	}

	/** The same line marked as something to wear. */
	public SupplyLine worn()
	{
		return new SupplyLine(group, name, need, have, carried, where, note, slots, coveredOtherwise, changeIn, itemIds,
			consumed, true);
	}

	/** The same line marked as used up on the run. */
	public SupplyLine usedUp()
	{
		return new SupplyLine(group, name, need, have, carried, where, note, slots, coveredOtherwise, changeIn, itemIds,
			true, wear);
	}

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
