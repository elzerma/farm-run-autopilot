package com.farmrunautopilot.data;

public enum PatchState
{
	/** Raked and empty, ready to plant. */
	EMPTY,
	/** Weeds growing; needs raking before planting. */
	WEEDS,
	GROWING,
	DISEASED,
	DEAD,
	/** Tree or fruit tree fully grown, waiting for "Check-health". */
	CHECK_HEALTH,
	/** Herbs ready to pick, a checked tree ready to chop, or a fruit tree with fruit to pick. */
	HARVESTABLE,
	/** Tree stump left after chopping; dig it up (or pay the gardener) before replanting. */
	STUMP,
	/** A value RuneLite does not recognise either. */
	UNKNOWN
}
