package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Patch;
import java.util.Collections;
import java.util.Set;
import lombok.Value;

/**
 * What the current step points at in the game (SPEC 13.4): the patch, the gardener and the items to use.
 * Immutable, so overlays can read it every frame without locking.
 */
@Value
public class Highlights
{
	public static final Highlights NONE = new Highlights(null, -1, Collections.emptySet());

	/** The patch to outline and point the hint arrow at, or null while travelling. */
	Patch patch;
	/** The gardener NPC to outline (paying them), or -1. */
	int npcId;
	/** Items to outline in the inventory and equipment. */
	Set<Integer> itemIds;
}
