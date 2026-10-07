package com.farmrunautopilot.data;

import java.util.EnumMap;
import java.util.Map;

/**
 * Turns a patch varbit value into crop, state and stage (SPEC 6.1).
 *
 * <p>The value ranges are copied from RuneLite core {@code timetracking/farming/PatchImplementation}
 * (BSD-2, see THIRD_PARTY_NOTICES), including Huasca (growing 60-63, harvestable 64-66, diseased
 * 173-175). Values RuneLite does not recognise decode to {@link PatchState#UNKNOWN}.
 */
public final class PatchStateDecoder
{
	private static final int MAX_VALUE = 255;
	private static final DecodedPatch UNKNOWN = new DecodedPatch(null, PatchState.UNKNOWN, 0);
	private static final Map<PatchType, DecodedPatch[]> TABLES = new EnumMap<>(PatchType.class);

	static
	{
		TABLES.put(PatchType.HERB, herbTable());
		TABLES.put(PatchType.FRUIT_TREE, fruitTreeTable());
		TABLES.put(PatchType.TREE, treeTable());
	}

	private PatchStateDecoder()
	{
	}

	public static DecodedPatch decode(PatchType type, int value)
	{
		if (value < 0 || value > MAX_VALUE)
		{
			return UNKNOWN;
		}
		final DecodedPatch decoded = TABLES.get(type)[value];
		return decoded != null ? decoded : UNKNOWN;
	}

	private static DecodedPatch[] herbTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		herb(t, 4, Crop.GUAM);
		herb(t, 11, Crop.MARRENTILL);
		herb(t, 18, Crop.TARROMIN);
		herb(t, 25, Crop.HARRALANDER);
		herb(t, 32, Crop.RANARR);
		herb(t, 39, Crop.TOADFLAX);
		herb(t, 46, Crop.IRIT);
		herb(t, 53, Crop.AVANTOE);
		herb(t, 60, Crop.HUASCA);
		t.overgrown(67, 67);
		herb(t, 68, Crop.KWUARM);
		herb(t, 75, Crop.SNAPDRAGON);
		herb(t, 82, Crop.CADANTINE);
		herb(t, 89, Crop.LANTADYME);
		herb(t, 96, Crop.DWARF_WEED);
		herb(t, 103, Crop.TORSTOL);
		// 110-127: not recognised by RuneLite

		final Crop[] diseasedOrder = {
			Crop.GUAM, Crop.MARRENTILL, Crop.TARROMIN, Crop.HARRALANDER, Crop.RANARR, Crop.TOADFLAX, Crop.IRIT,
			Crop.AVANTOE, Crop.KWUARM, Crop.SNAPDRAGON, Crop.CADANTINE, Crop.LANTADYME, Crop.DWARF_WEED, Crop.TORSTOL
		};
		for (int i = 0; i < diseasedOrder.length; i++)
		{
			final int lo = 128 + 3 * i;
			t.counting(lo, lo + 2, diseasedOrder[i], PatchState.DISEASED, lo - 1);
		}
		// Dead herbs don't say which herb they were.
		t.counting(170, 172, null, PatchState.DEAD, 169);
		t.counting(173, 175, Crop.HUASCA, PatchState.DISEASED, 172);
		t.overgrown(176, 191);

		// Goutweed (not a v1 crop)
		t.counting(192, 195, null, PatchState.GROWING, 192);
		t.countingDown(196, 197, null, PatchState.HARVESTABLE);
		t.counting(198, 200, null, PatchState.DISEASED, 197);
		t.counting(201, 203, null, PatchState.DEAD, 200);

		t.overgrown(204, 219);
		// 220: not recognised by RuneLite
		t.overgrown(221, 255);
		return t.values;
	}

	/** 4 growing values, then 3 harvestable values. */
	private static void herb(Table t, int base, Crop crop)
	{
		t.counting(base, base + 3, crop, PatchState.GROWING, base);
		t.countingDown(base + 4, base + 6, crop, PatchState.HARVESTABLE);
	}

	private static DecodedPatch[] fruitTreeTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		fruitTree(t, 8, Crop.APPLE);
		fruitTree(t, 35, Crop.BANANA);
		t.overgrown(62, 71);
		fruitTree(t, 72, Crop.ORANGE);
		fruitTree(t, 99, Crop.CURRY);
		t.overgrown(126, 135);
		fruitTree(t, 136, Crop.PINEAPPLE);
		fruitTree(t, 163, Crop.PAPAYA);
		t.overgrown(190, 199);
		fruitTree(t, 200, Crop.PALM);
		fruitTree(t, 227, Crop.DRAGONFRUIT);
		t.overgrown(254, 255);
		return t.values;
	}

	/**
	 * 6 growing, 7 harvestable (stage = fruit on the tree, 0-6), 6 diseased, 6 dead, then the stump and
	 * finally the check-health value.
	 */
	private static void fruitTree(Table t, int base, Crop crop)
	{
		t.counting(base, base + 5, crop, PatchState.GROWING, base);
		t.counting(base + 6, base + 12, crop, PatchState.HARVESTABLE, base + 6);
		t.counting(base + 13, base + 18, crop, PatchState.DISEASED, base + 12);
		t.counting(base + 19, base + 24, crop, PatchState.DEAD, base + 18);
		t.single(base + 25, crop, PatchState.STUMP, 0);
		t.single(base + 26, crop, PatchState.CHECK_HEALTH, crop.getStages() - 1);
	}

	private static DecodedPatch[] treeTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		tree(t, 8, Crop.OAK);
		tree(t, 15, Crop.WILLOW);
		tree(t, 24, Crop.MAPLE);
		tree(t, 35, Crop.YEW);
		tree(t, 48, Crop.MAGIC);
		t.overgrown(63, 72);

		treeDiseasedOrDead(t, 73, Crop.OAK, PatchState.DISEASED);
		t.overgrown(78, 79);
		treeDiseasedOrDead(t, 80, Crop.WILLOW, PatchState.DISEASED);
		t.overgrown(87, 88);
		treeDiseasedOrDead(t, 89, Crop.MAPLE, PatchState.DISEASED);
		t.overgrown(98, 99);
		treeDiseasedOrDead(t, 100, Crop.YEW, PatchState.DISEASED);
		t.overgrown(111, 112);
		treeDiseasedOrDead(t, 113, Crop.MAGIC, PatchState.DISEASED);
		t.overgrown(126, 136);

		treeDiseasedOrDead(t, 137, Crop.OAK, PatchState.DEAD);
		t.overgrown(142, 143);
		treeDiseasedOrDead(t, 144, Crop.WILLOW, PatchState.DEAD);
		t.overgrown(151, 152);
		treeDiseasedOrDead(t, 153, Crop.MAPLE, PatchState.DEAD);
		t.overgrown(162, 163);
		treeDiseasedOrDead(t, 164, Crop.YEW, PatchState.DEAD);
		t.overgrown(175, 176);
		treeDiseasedOrDead(t, 177, Crop.MAGIC, PatchState.DEAD);
		t.overgrown(190, 191);

		// A second set of grown willow values (RuneLite maps these to HARVESTABLE too).
		for (int v = 192; v <= 197; v++)
		{
			t.single(v, Crop.WILLOW, PatchState.HARVESTABLE, 0);
		}
		t.overgrown(198, 255);
		return t.values;
	}

	/** Growing values, then check-health, then grown (choppable), then stump. */
	private static void tree(Table t, int base, Crop crop)
	{
		final int checkHealth = base + crop.getStages() - 1;
		t.counting(base, checkHealth - 1, crop, PatchState.GROWING, base);
		t.single(checkHealth, crop, PatchState.CHECK_HEALTH, crop.getStages() - 1);
		t.single(checkHealth + 1, crop, PatchState.HARVESTABLE, 0);
		t.single(checkHealth + 2, crop, PatchState.STUMP, 0);
	}

	/**
	 * A run of diseased (or dead) growth stages starting at 1, then one value RuneLite skips, then the
	 * final stage.
	 */
	private static void treeDiseasedOrDead(Table t, int base, Crop crop, PatchState state)
	{
		final int finalStage = crop.getStages() - 1;
		t.counting(base, base + finalStage - 2, crop, state, base - 1);
		t.single(base + finalStage, crop, state, finalStage);
	}

	private static final class Table
	{
		private final DecodedPatch[] values = new DecodedPatch[MAX_VALUE + 1];

		/** stage = value - zero */
		void counting(int lo, int hi, Crop crop, PatchState state, int zero)
		{
			for (int v = lo; v <= hi; v++)
			{
				single(v, crop, state, v - zero);
			}
		}

		/** stage = hi - value */
		void countingDown(int lo, int hi, Crop crop, PatchState state)
		{
			for (int v = lo; v <= hi; v++)
			{
				single(v, crop, state, hi - v);
			}
		}

		/** Weed level goes 3 (overgrown) down to 0 (clear) as the player rakes. */
		void weedsBeingRaked(int lo, int hi)
		{
			for (int v = lo; v <= hi; v++)
			{
				final int weeds = hi - v;
				single(v, null, weeds == 0 ? PatchState.EMPTY : PatchState.WEEDS, weeds);
			}
		}

		void overgrown(int lo, int hi)
		{
			for (int v = lo; v <= hi; v++)
			{
				single(v, null, PatchState.WEEDS, 3);
			}
		}

		void single(int value, Crop crop, PatchState state, int stage)
		{
			if (values[value] != null)
			{
				throw new IllegalStateException("Patch value " + value + " defined twice");
			}
			values[value] = new DecodedPatch(crop, state, stage);
		}
	}
}
