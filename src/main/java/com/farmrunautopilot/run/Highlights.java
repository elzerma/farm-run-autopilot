package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.route.MenuPick;
import java.util.Collections;
import java.util.LinkedHashSet;
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
	/**
	 * The option to pick on the teleport item (in {@link #itemIds}) in its right-click menu or its list of places, e.g.
	 * "Crafting Guild"; null if none.
	 */
	String teleportOption;
	/**
	 * Items the step needs that aren't clicked, such as the rake for raking or the payment for the gardener: not
	 * outlined (elzerma, 2026-10-19: tools only when they need clicking), but still checked as carried.
	 */
	Set<Integer> neededIds;
	/**
	 * Items in {@link #itemIds} outlined in every slot holding them, as each is clicked on its own (grimy herbs to clean),
	 * rather than in one (elzerma, 2026-10-30: "one was highlighted. Can we highlight all of them").
	 */
	Set<Integer> everySlotIds;

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell)
	{
		this(patch, gardener, travelNpc, itemIds, spell, Collections.emptySet());
	}

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell,
		Set<Integer> wearItemIds)
	{
		this(patch, gardener, travelNpc, itemIds, spell, wearItemIds, Collections.emptyList(), null);
	}

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell,
		Set<Integer> wearItemIds, List<MenuPick> menuPicks, String teleportOption)
	{
		this(patch, gardener, travelNpc, itemIds, spell, wearItemIds, menuPicks, teleportOption,
			Collections.emptySet());
	}

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell,
		Set<Integer> wearItemIds, List<MenuPick> menuPicks, String teleportOption, Set<Integer> neededIds)
	{
		this(patch, gardener, travelNpc, itemIds, spell, wearItemIds, menuPicks, teleportOption, neededIds,
			Collections.emptySet());
	}

	public Highlights(Patch patch, boolean gardener, String travelNpc, Set<Integer> itemIds, Spell spell,
		Set<Integer> wearItemIds, List<MenuPick> menuPicks, String teleportOption, Set<Integer> neededIds,
		Set<Integer> everySlotIds)
	{
		this.patch = patch;
		this.gardener = gardener;
		this.travelNpc = travelNpc;
		this.itemIds = itemIds;
		this.spell = spell;
		this.wearItemIds = wearItemIds;
		this.menuPicks = menuPicks;
		this.teleportOption = teleportOption;
		this.neededIds = neededIds;
		this.everySlotIds = everySlotIds;
	}

	/** The same, with these items to put on. */
	public Highlights withWear(Set<Integer> wear)
	{
		return new Highlights(patch, gardener, travelNpc, itemIds, spell, wear, menuPicks, teleportOption, neededIds,
			everySlotIds);
	}

	/** Everything the step needs, outlined or not, the main item first. */
	public Set<Integer> getStepItemIds()
	{
		if (neededIds.isEmpty())
		{
			return itemIds;
		}
		final Set<Integer> all = new LinkedHashSet<>(itemIds);
		all.addAll(neededIds);
		return all;
	}
}
