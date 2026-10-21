package com.farmrunautopilot.run;

import java.util.Set;
import java.util.function.IntPredicate;
import lombok.Getter;
import net.runelite.api.gameval.InterfaceID;

/**
 * The side-panel tabs a step can need open (elzerma, 2026-10-20): the inventory for an item to use, worn equipment for
 * jewellery or a cape to operate, the spellbook for a spell. When the tab isn't open its icon gets the vine, so a player
 * on the prayer tab sees where to go; once it's open, the item or spell itself is highlighted as before.
 */
@Getter
public enum SideTab
{
	INVENTORY("Inventory", 3, InterfaceID.Toplevel.STONE3, InterfaceID.ToplevelOsrsStretch.STONE3,
		InterfaceID.ToplevelPreEoc.STONE3),
	EQUIPMENT("Worn Equipment", 4, InterfaceID.Toplevel.STONE4, InterfaceID.ToplevelOsrsStretch.STONE4,
		InterfaceID.ToplevelPreEoc.STONE4),
	SPELLBOOK("Spellbook", 6, InterfaceID.Toplevel.STONE6, InterfaceID.ToplevelOsrsStretch.STONE6,
		InterfaceID.ToplevelPreEoc.STONE6);

	private final String label;
	/** {@code VarClientID.TOPLEVEL_PANEL} while this tab is open. */
	private final int panel;
	/** The tab's icon in fixed, resizable classic and resizable modern layouts; only one is shown at a time. */
	private final int[] icons;

	SideTab(String label, int panel, int... icons)
	{
		this.label = label;
		this.panel = panel;
		this.icons = icons;
	}

	/**
	 * The tab the step needs open, or null: the spellbook for a spell to cast, else the inventory if an item to use or
	 * put on is in it, else worn equipment if an item to use is worn.
	 *
	 * @param items whether items are highlighted at all ("Highlight items to use")
	 * @param spells whether spells are highlighted at all
	 */
	public static SideTab needed(Highlights highlights, IntPredicate inventory, IntPredicate worn, boolean items,
		boolean spells)
	{
		if (spells && highlights.getSpell() != null)
		{
			return SPELLBOOK;
		}
		if (!items)
		{
			return null;
		}
		if (anyIn(highlights.getItemIds(), inventory) || anyIn(highlights.getWearItemIds(), inventory))
		{
			return INVENTORY;
		}
		return anyIn(highlights.getItemIds(), worn) ? EQUIPMENT : null;
	}

	/** Whether this tab is the one open, given {@code VarClientID.TOPLEVEL_PANEL} (-1 when the panel is closed). */
	public boolean isOpen(int panel)
	{
		return this.panel == panel;
	}

	private static boolean anyIn(Set<Integer> ids, IntPredicate held)
	{
		for (int id : ids)
		{
			if (held.test(id))
			{
				return true;
			}
		}
		return false;
	}
}
