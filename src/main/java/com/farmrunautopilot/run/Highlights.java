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
	public static final Highlights NONE = new Highlights(null, false, Collections.emptySet());

	/** The patch to outline and point the hint arrow at, or null while travelling. */
	Patch patch;
	/** Outline the patch's gardener (paying them). */
	boolean gardener;
	/** Items to outline in the inventory and equipment. */
	Set<Integer> itemIds;
}
