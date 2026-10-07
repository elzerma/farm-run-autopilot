package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.travel.Rune;
import java.util.Collections;
import java.util.EnumMap;
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
		SEED_VAULT("seed vault");

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

	public Map<Integer, Integer> in(Source source)
	{
		return items.getOrDefault(source, Collections.emptyMap());
	}
}
