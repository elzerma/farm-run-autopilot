package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.Compost;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Everything the player has, by where it is (SPEC 11). Item IDs are canonical: noted items are counted
 * as their unnoted item. Immutable; built on the client thread, read anywhere.
 */
@Value
public class Holdings
{
	public enum Source
	{
		INVENTORY("inventory"),
		WORN("worn"),
		BANK("bank"),
		LEPRECHAUN("leprechaun"),
		GROUP_STORAGE("group storage"),
		SEED_VAULT("seed vault (Farming Guild)");

		private final String label;

		Source(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public static final Holdings EMPTY = new Holdings(Collections.emptyMap(), Collections.emptyMap(),
		Collections.emptySet(), false, false);

	/** Item counts per source. Sources that are switched off or never seen are absent. */
	Map<Source, Map<Integer, Integer>> items;
	/** Rune pouch contents by rune item (combination runes included), only when a pouch is in the inventory. */
	Map<Integer, Integer> runePouch;
	/** Runes supplied without limit by an equipped staff or tome. */
	Set<Rune> infiniteRunes;
	/** The bank has been opened at least once on this account, so its contents are known. */
	boolean bankKnown;
	/** Tithe Farm Auto-weed is owned and switched on. */
	boolean autoweedOn;
	/** Uses left in the bottomless compost bucket, or -1 if not known. */
	int bucketUses;
	/** What the bottomless compost bucket holds, or null if not known. */
	Compost bucketCompost;
	/** Today's uses of teleports limited per day (Ardougne cloak, Explorer's ring). */
	Map<TravelMethod, Integer> usedToday;
	/** Known charges of self-charged items (quetzal whistle, Xeric's talisman, pendant of Ates). */
	Map<TravelItem, Integer> itemCharges;

	public Holdings(Map<Source, Map<Integer, Integer>> items, Map<Integer, Integer> runePouch, Set<Rune> infiniteRunes,
		boolean bankKnown, boolean autoweedOn, int bucketUses, Compost bucketCompost,
		Map<TravelMethod, Integer> usedToday, Map<TravelItem, Integer> itemCharges)
	{
		this.items = items;
		this.runePouch = runePouch;
		this.infiniteRunes = infiniteRunes;
		this.bankKnown = bankKnown;
		this.autoweedOn = autoweedOn;
		this.bucketUses = bucketUses;
		this.bucketCompost = bucketCompost;
		this.usedToday = usedToday;
		this.itemCharges = itemCharges;
	}

	public Holdings(Map<Source, Map<Integer, Integer>> items, Map<Integer, Integer> runePouch, Set<Rune> infiniteRunes,
		boolean bankKnown, boolean autoweedOn, int bucketUses, Compost bucketCompost,
		Map<TravelMethod, Integer> usedToday)
	{
		this(items, runePouch, infiniteRunes, bankKnown, autoweedOn, bucketUses, bucketCompost, usedToday,
			Collections.emptyMap());
	}

	public Holdings(Map<Source, Map<Integer, Integer>> items, Map<Integer, Integer> runePouch, Set<Rune> infiniteRunes,
		boolean bankKnown, boolean autoweedOn, int bucketUses, Compost bucketCompost)
	{
		this(items, runePouch, infiniteRunes, bankKnown, autoweedOn, bucketUses, bucketCompost,
			Collections.emptyMap());
	}

	/** Without anything known about a bottomless compost bucket. */
	public Holdings(Map<Source, Map<Integer, Integer>> items, Map<Integer, Integer> runePouch, Set<Rune> infiniteRunes,
		boolean bankKnown, boolean autoweedOn)
	{
		this(items, runePouch, infiniteRunes, bankKnown, autoweedOn, -1, null);
	}

	public int count(int itemId)
	{
		int total = runePouch.getOrDefault(itemId, 0);
		for (Map<Integer, Integer> source : items.values())
		{
			total += source.getOrDefault(itemId, 0);
		}
		return total;
	}

	/** Total held of any of these items. */
	public int countAny(int[] itemIds)
	{
		int total = 0;
		for (int id : itemIds)
		{
			total += count(id);
		}
		return total;
	}

	/** Where these items are, e.g. {BANK=12, INVENTORY=3}. Rune pouch contents count as inventory. */
	public Map<Source, Integer> where(int... itemIds)
	{
		final Map<Source, Integer> where = new EnumMap<>(Source.class);
		int pouch = 0;
		for (int id : itemIds)
		{
			pouch += runePouch.getOrDefault(id, 0);
		}
		items.forEach((source, counts) ->
		{
			int n = 0;
			for (int id : itemIds)
			{
				n += counts.getOrDefault(id, 0);
			}
			if (n > 0)
			{
				where.put(source, n);
			}
		});
		if (pouch > 0)
		{
			where.merge(Source.INVENTORY, pouch, Integer::sum);
		}
		return where;
	}

	/** Only what the player is carrying: inventory (with rune pouch) and worn items. */
	public Holdings carriedOnly()
	{
		final Map<Source, Map<Integer, Integer>> carried = new EnumMap<>(Source.class);
		carried.put(Source.INVENTORY, in(Source.INVENTORY));
		carried.put(Source.WORN, in(Source.WORN));
		return new Holdings(carried, runePouch, infiniteRunes, bankKnown, autoweedOn, bucketUses, bucketCompost,
			usedToday,
			itemCharges);
	}

	/**
	 * The same, but these items count only where they're carried (inventory or worn): for a run in progress, where an
	 * item in the bank can't be fetched without a bank visit.
	 */
	public Holdings carriedOnly(Set<Integer> itemIds)
	{
		final Map<Source, Map<Integer, Integer>> left = new EnumMap<>(Source.class);
		items.forEach((source, counts) ->
		{
			if (source == Source.INVENTORY || source == Source.WORN)
			{
				left.put(source, counts);
				return;
			}
			final Map<Integer, Integer> kept = new HashMap<>(counts);
			kept.keySet().removeAll(itemIds);
			left.put(source, kept);
		});
		return new Holdings(left, runePouch, infiniteRunes, bankKnown, autoweedOn, bucketUses, bucketCompost, usedToday,
			itemCharges);
	}

	public Map<Integer, Integer> in(Source source)
	{
		return items.getOrDefault(source, Collections.emptyMap());
	}

	/**
	 * What's left after using up {@code amount} of these items (any of them, in order), taken from what's carried
	 * first, then the rune pouch, then the bank and other storage. For planning later parts of a run.
	 */
	public Holdings use(int[] itemIds, int amount)
	{
		final Map<Source, Map<Integer, Integer>> left = new EnumMap<>(Source.class);
		items.forEach((source, counts) -> left.put(source, new HashMap<>(counts)));
		final Map<Integer, Integer> pouch = new HashMap<>(runePouch);
		int remaining = amount;
		for (Source source : Source.values())
		{
			remaining = take(left.get(source), itemIds, remaining);
			if (source == Source.WORN)
			{
				remaining = take(pouch, itemIds, remaining);
			}
		}
		return new Holdings(left, pouch, infiniteRunes, bankKnown, autoweedOn, bucketUses, bucketCompost, usedToday,
			itemCharges);
	}

	/** Takes up to {@code amount} of these items from {@code counts}; returns how many are still to take. */
	private static int take(Map<Integer, Integer> counts, int[] itemIds, int amount)
	{
		if (counts == null)
		{
			return amount;
		}
		for (int id : itemIds)
		{
			if (amount <= 0)
			{
				break;
			}
			final int have = counts.getOrDefault(id, 0);
			final int used = Math.min(have, amount);
			if (used > 0)
			{
				counts.put(id, have - used);
				amount -= used;
			}
		}
		return amount;
	}
}
