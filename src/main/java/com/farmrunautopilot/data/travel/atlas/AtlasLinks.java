package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ties an atlas teleport to the plugin's own travel item or spell (docs/plans/travel-rework.md, section 4), so the
 * planner can keep what it tracks for them: charges, daily uses, runes and tablets, Spellbook Swap and highlights. A
 * teleport with neither is planned from its needs alone.
 */
public final class AtlasLinks
{
	private static final Map<Integer, TravelItem> ITEMS = new HashMap<>();
	private static final Map<Integer, Spell> TABLETS = new HashMap<>();
	private static final Map<String, Spell> SPELLS = new HashMap<>();

	static
	{
		for (TravelItem item : TravelItem.values())
		{
			for (int id : item.getItemIds())
			{
				ITEMS.putIfAbsent(id, item);
			}
		}
		for (Spell spell : Spell.values())
		{
			SPELLS.put(spell.getDisplayName().toLowerCase(Locale.ROOT), spell);
			if (spell.hasTablet())
			{
				TABLETS.put(spell.getTabletItemId(), spell);
			}
		}
	}

	private AtlasLinks()
	{
	}

	/** The plugin's teleport item this teleport uses (any item it needs that a TravelItem lists), or null. */
	public static TravelItem item(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		for (int id : itemIds(atlas, teleport))
		{
			final TravelItem item = ITEMS.get(id);
			if (item != null)
			{
				return item;
			}
		}
		return null;
	}

	/**
	 * The plugin's spell this teleport casts, matched by name ("Camelot Teleport: Seers'" is Camelot Teleport), or
	 * whose tablet it breaks; null if neither.
	 */
	public static Spell spell(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		if (teleport.getType().startsWith("TELEPORTATION_SPELL"))
		{
			String name = teleport.getName();
			final int colon = name.indexOf(':');
			name = colon >= 0 ? name.substring(0, colon) : name;
			final int bracket = name.indexOf(" (");
			name = bracket >= 0 ? name.substring(0, bracket) : name;
			return SPELLS.get(name.trim().toLowerCase(Locale.ROOT));
		}
		for (int id : itemIds(atlas, teleport))
		{
			final Spell spell = TABLETS.get(id);
			if (spell != null)
			{
				return spell;
			}
		}
		return null;
	}

	/** Every item id the teleport's item needs name (any branch). */
	private static int[] itemIds(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		int count = 0;
		for (int needId : teleport.getNeeds())
		{
			final AtlasNeed need = atlas.need(needId);
			for (AtlasNeed.Branch branch : need.getBranches())
			{
				count += branch.getItems().length;
			}
		}
		final int[] ids = new int[count];
		int i = 0;
		for (int needId : teleport.getNeeds())
		{
			for (AtlasNeed.Branch branch : atlas.need(needId).getBranches())
			{
				for (int id : branch.getItems())
				{
					ids[i++] = id;
				}
			}
		}
		return ids;
	}
}
