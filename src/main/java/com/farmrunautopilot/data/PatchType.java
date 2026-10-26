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
	HOPS("Hops", "Hops", true, false, true),
	// Sprint 1b. Compost raises a seaweed patch's harvest (a life per tier, wiki: Seaweed patch); on bushes and cacti
	// it's taken as lowering disease only (UNVERIFIED), and mushroom and belladonna can't be protected
	BUSH("Bush", "Bushes", true, true, false),
	CACTUS("Cactus", "Cacti", true, true, false),
	MUSHROOM("Mushroom", "Mushrooms", false, false, false),
	BELLADONNA("Belladonna", "Belladonna", false, false, false),
	SEAWEED("Seaweed", "Seaweed", true, false, true),
	// Sprint 1c, the special trees. Compost only lowers disease on them, but raises the crystal tree's shards (wiki: Crystal
	// tree), which nobody can protect
	HARDWOOD("Hardwood tree", "Hardwood trees", true, true, false),
	CALQUAT("Calquat", "Calquats", true, true, false),
	CELASTRUS("Celastrus", "Celastrus trees", true, true, false),
	REDWOOD("Redwood", "Redwoods", true, true, false),
	SPIRIT_TREE("Spirit tree", "Spirit trees", true, true, false),
	CRYSTAL_TREE("Crystal tree", "Crystal trees", false, true, true);

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

	/**
	 * A grown crop regrows what's picked (fruit, berries, spines), so it can be picked and left growing rather than
	 * replanted each run: a choice per type (elzerma, 2026-10-24).
	 */
	public boolean regrows()
	{
		return this == FRUIT_TREE || this == BUSH || this == CACTUS || this == CALQUAT;
	}

	/**
	 * A grown crop can be left as it is rather than replanted each run: those that regrow, and spirit trees, kept for
	 * travel (sprint 1c). A choice per type.
	 */
	public boolean keepable()
	{
		return regrows() || this == SPIRIT_TREE;
	}

	/** The run types every player had before more were added; new players start with only these ticked. */
	public static boolean isOriginal(PatchType type)
	{
		return type == TREE || type == FRUIT_TREE || type == HERB;
	}
}
