package com.farmrunautopilot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Run types. New patch types (hardwood, calquat, ...) are added here, together with their patches, crops and a
 * decoder table, without changing the rest of the data model.
 */
@Getter
@RequiredArgsConstructor
public enum PatchType
{
	TREE("Tree", "Trees", true, true, false),
	FRUIT_TREE("Fruit tree", "Fruit trees", true, true, false),
	HERB("Herb", "Herbs", false, false, true),
	// Sprint 1a (docs/plans/crop-types.md)
	ALLOTMENT("Allotment", "Allotments", true, false, true),
	FLOWER("Flower", "Flowers", false, false, false),
	HOPS("Hops", "Hops", true, false, true);

	private final String displayName;
	private final String pluralName;
	/** Gardeners can protect this patch type for a payment (where the patch has a gardener). */
	private final boolean protectable;
	/** A grown crop waits for "Check-health" before it can be harvested (trees). */
	private final boolean healthChecked;
	/**
	 * Compost raises the harvest (more herbs, vegetables or hops), so a patch the gardener protects is still composted;
	 * on the others it only lowers the chance of disease (elzerma, 2026-10-18).
	 */
	private final boolean compostRaisesYield;

	/** The run types every player had before more were added; new players start with only these ticked. */
	public static boolean isOriginal(PatchType type)
	{
		return type == TREE || type == FRUIT_TREE || type == HERB;
	}
}
