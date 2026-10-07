package com.farmrunautopilot.tracking;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import lombok.Value;

/**
 * Best guess at a patch's state right now, worked forward from the last observation.
 */
@Value
public class PatchPrediction
{
	public enum Source
	{
		/** Seen by this plugin. */
		THIS_PLUGIN,
		/** Borrowed from RuneLite's Time Tracking plugin (patch not seen by us yet). */
		TIME_TRACKING
	}

	Patch patch;
	/** Null for empty or weedy patches and crops outside the v1 catalogue. */
	Crop crop;
	PatchState state;
	/** Growth stage, weed level or fruit count; see {@link com.farmrunautopilot.data.DecodedPatch}. */
	int stage;
	int stages;
	/**
	 * When growth (or weed regrowth, or fruit regrowth) finishes, in epoch seconds; 0 if nothing is
	 * changing on its own.
	 */
	long doneAt;
	long observedAt;
	/** When we saw this crop planted, in epoch seconds; 0 if unknown. */
	long plantedAt;
	Source source;
}
