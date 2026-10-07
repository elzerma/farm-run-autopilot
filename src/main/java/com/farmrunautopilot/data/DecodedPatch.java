package com.farmrunautopilot.data;

import lombok.Value;

@Value
public class DecodedPatch
{
	/**
	 * The crop in the patch, or null when the patch is empty or weedy, when it holds something that is
	 * not a v1 crop (goutweed), or when a dead herb's type is not encoded.
	 */
	Crop crop;
	PatchState state;
	/**
	 * Meaning depends on the state, following RuneLite core: growth stage when GROWING, weed level
	 * (0 = clear, 3 = fully overgrown) for EMPTY/WEEDS, fruit count for a HARVESTABLE fruit tree,
	 * RuneLite's harvest stage (0-2) for HARVESTABLE herbs, otherwise the disease or death stage.
	 */
	int stage;
}
