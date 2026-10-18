package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.route.MenuPick;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * What the current step points at in the game (SPEC 13.4): the patch, the gardener, the items to use, the spell to
 * cast and the place to pick on a travel map. Immutable, so overlays can read it every frame without locking.
 */
@Value
public class Highlights
{
	public static final Highlights NONE = new Highlights(null, false, null, Collections.emptySet(), null);

	/** The patch to outline and point the hint arrow at, or null while travelling. */
	Patch patch;
	/** Outline the patch's gardener (paying them). */
	boolean gardener;
	/** The name of an NPC to outline while travelling (Renu, the Civitas quetzal), or null. */
	String travelNpc;
	/** Items to outline in the inventory and equipment. */
	Set<Integer> itemIds;
	/** The spell to outline in the spellbook, or null. */
	Spell spell;
	/** Items to put on, outlined in their own colour with a "Wear" label, never as an item to use. */
	Set<Integer> wearItemIds;
	/** Where to pick on Renu's, the quetzal whistle's or a hot air balloon's map while travelling; empty if nowhere. */
	List<MenuPick> menuPicks;

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell)
	{
		this(patch, gardener, travelNpc, itemIds, spell, Collections.emptySet());
	}

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell,
		Set<Integer> wearItemIds)
	{
		this(patch, gardener, travelNpc, itemIds, spell, wearItemIds, Collections.emptyList());
	}

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell,
		Set<Integer> wearItemIds, List<MenuPick> menuPicks)
	{
		this.patch = patch;
		this.gardener = gardener;
		this.travelNpc = travelNpc;
		this.itemIds = itemIds;
		this.spell = spell;
		this.wearItemIds = wearItemIds;
		this.menuPicks = menuPicks;
	}
}
