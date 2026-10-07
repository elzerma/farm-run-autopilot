package com.farmrunautopilot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Run types. New patch types (hardwood, calquat, allotment, ...) are added here, together with their
 * patches, crops and a decoder table, without changing the rest of the data model.
 */
@Getter
@RequiredArgsConstructor
public enum PatchType
{
	TREE("Tree", true),
	FRUIT_TREE("Fruit tree", true),
	HERB("Herb", false);

	private final String displayName;
	/** Gardeners can protect this patch type for a payment. */
	private final boolean protectable;
}
