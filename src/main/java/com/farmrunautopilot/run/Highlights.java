package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.Spell;
import java.util.Collections;
import java.util.Set;
import lombok.Value;

/**
 * What the current step points at in the game (SPEC 13.4): the patch, the gardener, the items to use and the
 * spell to cast. Immutable, so overlays can read it every frame without locking.
 */
@Value
public class Highlights
{
	public static final Highlights NONE = new Highlights(null, false, -1, Collections.emptySet(), null);

	/** The patch to outline and point the hint arrow at, or null while travelling. */
	Patch patch;
	/** Outline the patch's gardener (paying them). */
	boolean gardener;
	/** An NPC to outline while travelling (the Civitas quetzal), or -1. */
	int travelNpcId;
	/** Items to outline in the inventory and equipment. */
	Set<Integer> itemIds;
	/** The spell to outline in the spellbook, or null. */
	Spell spell;
}
