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
		TABLES.put(PatchType.ALLOTMENT, allotmentTable());
		TABLES.put(PatchType.FLOWER, flowerTable());
		TABLES.put(PatchType.HOPS, hopsTable());
		TABLES.put(PatchType.BUSH, bushTable());
		TABLES.put(PatchType.CACTUS, cactusTable());
		TABLES.put(PatchType.MUSHROOM, mushroomTable());
		TABLES.put(PatchType.BELLADONNA, belladonnaTable());
		TABLES.put(PatchType.SEAWEED, seaweedTable());
		TABLES.put(PatchType.HARDWOOD, hardwoodTable());
		TABLES.put(PatchType.CALQUAT, calquatTable());
		TABLES.put(PatchType.CELASTRUS, celastrusTable());
		TABLES.put(PatchType.REDWOOD, redwoodTable());
		TABLES.put(PatchType.SPIRIT_TREE, spiritTreeTable());
		TABLES.put(PatchType.CRYSTAL_TREE, crystalTreeTable());
	}

	/**
	 * Sprint 1b, from RuneLite's BUSH (master, 2026-10-24). Check-health is RuneLite's last growing stage (250-255), read
	 * here as CHECK_HEALTH as for trees; harvestable counts the berries (0-4).
	 */
	private static DecodedPatch[] bushTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 4);
		bushGrowing(t, 5, 10, 14, Crop.REDBERRY);
		bushGrowing(t, 15, 21, 25, Crop.CADAVABERRY);
		bushGrowing(t, 26, 33, 37, Crop.DWELLBERRY);
		bushGrowing(t, 38, 46, 50, Crop.JANGERBERRY);
		bushGrowing(t, 51, 59, 63, Crop.WHITEBERRY);
		t.overgrown(64, 69);
		t.counting(70, 74, Crop.REDBERRY, PatchState.DISEASED, 69);
		t.overgrown(75, 79);
		t.counting(80, 85, Crop.CADAVABERRY, PatchState.DISEASED, 79);
		t.overgrown(86, 90);
		t.counting(91, 97, Crop.DWELLBERRY, PatchState.DISEASED, 90);
		t.overgrown(98, 102);
		t.counting(103, 110, Crop.JANGERBERRY, PatchState.DISEASED, 102);
		t.overgrown(111, 115);
		t.counting(116, 123, Crop.WHITEBERRY, PatchState.DISEASED, 115);
		t.overgrown(124, 133);
		t.counting(134, 138, Crop.REDBERRY, PatchState.DEAD, 133);
		t.overgrown(139, 143);
		t.counting(144, 149, Crop.CADAVABERRY, PatchState.DEAD, 143);
		t.overgrown(150, 154);
		t.counting(155, 161, Crop.DWELLBERRY, PatchState.DEAD, 154);
		t.overgrown(162, 166);
		t.counting(167, 174, Crop.JANGERBERRY, PatchState.DEAD, 166);
		t.overgrown(175, 179);
		t.counting(180, 187, Crop.WHITEBERRY, PatchState.DEAD, 179);
		t.overgrown(188, 196);
		bushGrowing(t, 197, 205, 209, Crop.POISON_IVY);
		t.counting(210, 216, Crop.POISON_IVY, PatchState.DISEASED, 209);
		t.counting(217, 224, Crop.POISON_IVY, PatchState.DEAD, 216);
		t.single(225, Crop.POISON_IVY, PatchState.DISEASED, 8);
		t.overgrown(226, 249);
		final Crop[] checked = {Crop.REDBERRY, Crop.CADAVABERRY, Crop.DWELLBERRY, Crop.JANGERBERRY, Crop.WHITEBERRY,
			Crop.POISON_IVY};
		for (int i = 0; i < checked.length; i++)
		{
			t.single(250 + i, checked[i], PatchState.CHECK_HEALTH, checked[i].getStages() - 1);
		}
		return t.values;
	}

	/** A bush's growing values from {@code grow}, then its harvestable ones (berries, counting up). */
	private static void bushGrowing(Table t, int grow, int ripe, int ripeEnd, Crop crop)
	{
		t.counting(grow, ripe - 1, crop, PatchState.GROWING, grow);
		t.counting(ripe, ripeEnd, crop, PatchState.HARVESTABLE, ripe);
	}

	/** Sprint 1b, from RuneLite's CACTUS: check-health is 31 for the cactus and 58 for the potato cactus. */
	private static DecodedPatch[] cactusTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		t.counting(8, 14, Crop.CACTUS, PatchState.GROWING, 8);
		t.counting(15, 18, Crop.CACTUS, PatchState.HARVESTABLE, 15);
		t.counting(19, 24, Crop.CACTUS, PatchState.DISEASED, 18);
		t.counting(25, 30, Crop.CACTUS, PatchState.DEAD, 24);
		t.single(31, Crop.CACTUS, PatchState.CHECK_HEALTH, Crop.CACTUS.getStages() - 1);
		t.counting(32, 38, Crop.POTATO_CACTUS, PatchState.GROWING, 32);
		t.counting(39, 45, Crop.POTATO_CACTUS, PatchState.HARVESTABLE, 39);
		t.counting(46, 51, Crop.POTATO_CACTUS, PatchState.DISEASED, 45);
		t.counting(52, 57, Crop.POTATO_CACTUS, PatchState.DEAD, 51);
		t.single(58, Crop.POTATO_CACTUS, PatchState.CHECK_HEALTH, Crop.POTATO_CACTUS.getStages() - 1);
		t.overgrown(59, 255);
		return t.values;
	}

	/** Sprint 1b, from RuneLite's MUSHROOM: harvestable counts the mushrooms picked (0-5). */
	private static DecodedPatch[] mushroomTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.counting(4, 9, Crop.BITTERCAP, PatchState.GROWING, 4);
		t.counting(10, 15, Crop.BITTERCAP, PatchState.HARVESTABLE, 10);
		t.counting(16, 20, Crop.BITTERCAP, PatchState.DISEASED, 15);
		t.counting(21, 25, Crop.BITTERCAP, PatchState.DEAD, 20);
		t.overgrown(26, 255);
		return t.values;
	}

	/** Sprint 1b, from RuneLite's BELLADONNA: one harvestable value. */
	private static DecodedPatch[] belladonnaTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.counting(4, 7, Crop.BELLADONNA, PatchState.GROWING, 4);
		t.single(8, Crop.BELLADONNA, PatchState.HARVESTABLE, 0);
		t.counting(9, 11, Crop.BELLADONNA, PatchState.DISEASED, 8);
		t.counting(12, 14, Crop.BELLADONNA, PatchState.DEAD, 11);
		t.overgrown(15, 255);
		return t.values;
	}

	/** Sprint 1b, from RuneLite's SEAWEED (giant seaweed): harvestable counts up (0-2). */
	private static DecodedPatch[] seaweedTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.counting(4, 7, Crop.GIANT_SEAWEED, PatchState.GROWING, 4);
		t.counting(8, 10, Crop.GIANT_SEAWEED, PatchState.HARVESTABLE, 8);
		t.counting(11, 13, Crop.GIANT_SEAWEED, PatchState.DISEASED, 10);
		t.counting(14, 16, Crop.GIANT_SEAWEED, PatchState.DEAD, 13);
		t.overgrown(17, 255);
		return t.values;
	}

	/**
	 * Sprint 1c, from RuneLite's HARDWOOD_TREE (master, 2026-10-25). Its two grown values after the check-health one are
	 * read as grown (to chop) then stump, as for trees.
	 */
	private static DecodedPatch[] hardwoodTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		hardwood(t, 8, Crop.TEAK);
		hardwood(t, 30, Crop.MAHOGANY);
		hardwood(t, 55, Crop.CAMPHOR);
		hardwood(t, 80, Crop.IRONWOOD);
		hardwood(t, 105, Crop.ROSEWOOD);
		t.overgrown(133, 255);
		return t.values;
	}

	/** Growing, check-health, grown, stump, then as many diseased and dead values as growing stages less one. */
	private static void hardwood(Table t, int base, Crop crop)
	{
		tree(t, base, crop);
		final int checkHealth = base + crop.getStages() - 1;
		final int diseased = checkHealth + 3;
		final int dead = diseased + crop.getStages() - 2;
		t.counting(diseased, dead - 1, crop, PatchState.DISEASED, diseased - 1);
		t.counting(dead, dead + crop.getStages() - 3, crop, PatchState.DEAD, dead - 1);
	}

	/** Sprint 1c, from RuneLite's CALQUAT: like a fruit tree, its fruit (0-6) picked from a checked tree. */
	private static DecodedPatch[] calquatTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.counting(4, 11, Crop.CALQUAT, PatchState.GROWING, 4);
		t.counting(12, 18, Crop.CALQUAT, PatchState.HARVESTABLE, 12);
		t.counting(19, 25, Crop.CALQUAT, PatchState.DISEASED, 18);
		t.counting(26, 33, Crop.CALQUAT, PatchState.DEAD, 25);
		t.single(34, Crop.CALQUAT, PatchState.CHECK_HEALTH, Crop.CALQUAT.getStages() - 1);
		t.overgrown(35, 255);
		return t.values;
	}

	/**
	 * Sprint 1c, from RuneLite's CELASTRUS: bark is harvested from a checked tree (14-16); 17 and 28, which RuneLite also
	 * calls harvestable, are read as the bare tree to clear (UNVERIFIED).
	 */
	private static DecodedPatch[] celastrusTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		t.counting(8, 12, Crop.CELASTRUS, PatchState.GROWING, 8);
		t.single(13, Crop.CELASTRUS, PatchState.CHECK_HEALTH, Crop.CELASTRUS.getStages() - 1);
		t.counting(14, 16, Crop.CELASTRUS, PatchState.HARVESTABLE, 14);
		t.single(17, Crop.CELASTRUS, PatchState.STUMP, 0);
		t.counting(18, 22, Crop.CELASTRUS, PatchState.DISEASED, 17);
		t.counting(23, 27, Crop.CELASTRUS, PatchState.DEAD, 22);
		t.single(28, Crop.CELASTRUS, PatchState.STUMP, 0);
		t.overgrown(29, 255);
		return t.values;
	}

	/**
	 * Sprint 1c, from RuneLite's REDWOOD: grown at 18 and while being chopped (41-55); 38-40 and above 55 RuneLite doesn't
	 * know either.
	 */
	private static DecodedPatch[] redwoodTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		t.counting(8, 17, Crop.REDWOOD, PatchState.GROWING, 8);
		t.single(18, Crop.REDWOOD, PatchState.HARVESTABLE, 0);
		t.counting(19, 27, Crop.REDWOOD, PatchState.DISEASED, 18);
		t.counting(28, 36, Crop.REDWOOD, PatchState.DEAD, 27);
		t.single(37, Crop.REDWOOD, PatchState.CHECK_HEALTH, Crop.REDWOOD.getStages() - 1);
		for (int v = 41; v <= 55; v++)
		{
			t.single(v, Crop.REDWOOD, PatchState.HARVESTABLE, 0);
		}
		return t.values;
	}

	/**
	 * Sprint 1c, from RuneLite's SPIRIT_TREE: 20 is the grown tree, checked and usable for travel, read as grown; 44 is
	 * check-health. Above 63 RuneLite doesn't know either.
	 */
	private static DecodedPatch[] spiritTreeTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		t.counting(8, 19, Crop.SPIRIT_TREE, PatchState.GROWING, 8);
		t.single(20, Crop.SPIRIT_TREE, PatchState.HARVESTABLE, 0);
		t.counting(21, 31, Crop.SPIRIT_TREE, PatchState.DISEASED, 20);
		t.counting(32, 43, Crop.SPIRIT_TREE, PatchState.DEAD, 31);
		t.single(44, Crop.SPIRIT_TREE, PatchState.CHECK_HEALTH, Crop.SPIRIT_TREE.getStages() - 1);
		t.overgrown(45, 63);
		return t.values;
	}

	/**
	 * Sprint 1c, from RuneLite's CRYSTAL_TREE: it can't get diseased, so no diseased or dead values; 15 is the checked
	 * tree to chop down, which leaves the patch empty (wiki: Crystal tree). 4-7 and above 15 RuneLite doesn't know.
	 */
	private static DecodedPatch[] crystalTreeTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.counting(8, 13, Crop.CRYSTAL_TREE, PatchState.GROWING, 8);
		t.single(14, Crop.CRYSTAL_TREE, PatchState.CHECK_HEALTH, Crop.CRYSTAL_TREE.getStages() - 1);
		t.single(15, Crop.CRYSTAL_TREE, PatchState.HARVESTABLE, 0);
		return t.values;
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

	/**
	 * Allotments. After the plain growing and harvestable values come the same growing stages again once watered,
	 * then the diseased and dead ones, with snape grass (added later) fitted into the gaps.
	 */
	private static DecodedPatch[] allotmentTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 5);
		allotment(t, 6, 10, 12, Crop.POTATO);
		allotment(t, 13, 17, 19, Crop.ONION);
		allotment(t, 20, 24, 26, Crop.CABBAGE);
		allotment(t, 27, 31, 33, Crop.TOMATO);
		allotment(t, 34, 40, 42, Crop.SWEETCORN);
		allotment(t, 43, 49, 51, Crop.STRAWBERRY);
		allotment(t, 52, 60, 62, Crop.WATERMELON);
		t.counting(63, 69, Crop.SNAPE_GRASS, PatchState.GROWING, 63);

		// Watered
		t.counting(70, 73, Crop.POTATO, PatchState.GROWING, 70);
		t.overgrown(74, 76);
		t.counting(77, 80, Crop.ONION, PatchState.GROWING, 77);
		t.overgrown(81, 83);
		t.counting(84, 87, Crop.CABBAGE, PatchState.GROWING, 84);
		t.overgrown(88, 90);
		t.counting(91, 94, Crop.TOMATO, PatchState.GROWING, 91);
		t.overgrown(95, 97);
		t.counting(98, 103, Crop.SWEETCORN, PatchState.GROWING, 98);
		t.overgrown(104, 106);
		t.counting(107, 112, Crop.STRAWBERRY, PatchState.GROWING, 107);
		t.overgrown(113, 115);
		t.counting(116, 123, Crop.WATERMELON, PatchState.GROWING, 116);
		t.overgrown(124, 127);
		t.counting(128, 134, Crop.SNAPE_GRASS, PatchState.GROWING, 128);

		t.counting(135, 137, Crop.POTATO, PatchState.DISEASED, 134);
		t.counting(138, 140, Crop.SNAPE_GRASS, PatchState.HARVESTABLE, 138);
		t.overgrown(141, 141);
		t.counting(142, 144, Crop.ONION, PatchState.DISEASED, 141);
		t.overgrown(145, 148);
		t.counting(149, 151, Crop.CABBAGE, PatchState.DISEASED, 148);
		t.overgrown(152, 155);
		t.counting(156, 158, Crop.TOMATO, PatchState.DISEASED, 155);
		t.overgrown(159, 162);
		t.counting(163, 167, Crop.SWEETCORN, PatchState.DISEASED, 162);
		t.overgrown(168, 171);
		t.counting(172, 176, Crop.STRAWBERRY, PatchState.DISEASED, 171);
		t.overgrown(177, 180);
		t.counting(181, 187, Crop.WATERMELON, PatchState.DISEASED, 180);
		t.overgrown(188, 192);
		t.counting(193, 195, Crop.SNAPE_GRASS, PatchState.DEAD, 192);
		t.counting(196, 198, Crop.SNAPE_GRASS, PatchState.DISEASED, 195);
		t.counting(199, 201, Crop.POTATO, PatchState.DEAD, 198);
		t.counting(202, 204, Crop.SNAPE_GRASS, PatchState.DISEASED, 198);
		t.overgrown(205, 205);
		t.counting(206, 208, Crop.ONION, PatchState.DEAD, 205);
		t.counting(209, 211, Crop.SNAPE_GRASS, PatchState.DEAD, 205);
		t.overgrown(212, 212);
		t.counting(213, 215, Crop.CABBAGE, PatchState.DEAD, 212);
		t.overgrown(216, 219);
		t.counting(220, 222, Crop.TOMATO, PatchState.DEAD, 219);
		t.overgrown(223, 226);
		t.counting(227, 231, Crop.SWEETCORN, PatchState.DEAD, 226);
		t.overgrown(232, 235);
		t.counting(236, 240, Crop.STRAWBERRY, PatchState.DEAD, 235);
		t.overgrown(241, 244);
		t.counting(245, 251, Crop.WATERMELON, PatchState.DEAD, 244);
		t.overgrown(252, 255);
		return t.values;
	}

	/** Growing values up to {@code harvest}, then harvestable values up to {@code last}. */
	private static void allotment(Table t, int base, int harvest, int last, Crop crop)
	{
		t.counting(base, harvest - 1, crop, PatchState.GROWING, base);
		t.counting(harvest, last, crop, PatchState.HARVESTABLE, harvest);
	}

	/** Flowers: growing, then one grown value; watered growing values, diseased and dead further up. */
	private static DecodedPatch[] flowerTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		t.overgrown(4, 7);
		flower(t, 8, Crop.MARIGOLD);
		flower(t, 13, Crop.ROSEMARY);
		flower(t, 18, Crop.NASTURTIUM);
		flower(t, 23, Crop.WOAD);
		flower(t, 28, Crop.LIMPWURT);
		// A placed scarecrow: RuneLite counts these as growing; it stays until dug up, so it never comes due
		t.counting(33, 36, Crop.SCARECROW, PatchState.GROWING, 33);
		flower(t, 37, Crop.WHITE_LILY);
		t.overgrown(42, 71);

		// Watered
		t.counting(72, 75, Crop.MARIGOLD, PatchState.GROWING, 72);
		t.overgrown(76, 76);
		t.counting(77, 80, Crop.ROSEMARY, PatchState.GROWING, 77);
		t.overgrown(81, 81);
		t.counting(82, 85, Crop.NASTURTIUM, PatchState.GROWING, 82);
		t.overgrown(86, 86);
		t.counting(87, 90, Crop.WOAD, PatchState.GROWING, 87);
		t.overgrown(91, 91);
		t.counting(92, 95, Crop.LIMPWURT, PatchState.GROWING, 92);
		t.overgrown(96, 100);
		t.counting(101, 104, Crop.WHITE_LILY, PatchState.GROWING, 101);
		t.overgrown(105, 136);

		t.counting(137, 139, Crop.MARIGOLD, PatchState.DISEASED, 136);
		t.overgrown(140, 141);
		t.counting(142, 144, Crop.ROSEMARY, PatchState.DISEASED, 141);
		t.overgrown(145, 146);
		t.counting(147, 149, Crop.NASTURTIUM, PatchState.DISEASED, 146);
		t.overgrown(150, 151);
		t.counting(152, 154, Crop.WOAD, PatchState.DISEASED, 151);
		t.overgrown(155, 156);
		t.counting(157, 159, Crop.LIMPWURT, PatchState.DISEASED, 156);
		t.overgrown(160, 165);
		t.counting(166, 168, Crop.WHITE_LILY, PatchState.DISEASED, 165);
		t.overgrown(169, 200);

		t.counting(201, 204, Crop.MARIGOLD, PatchState.DEAD, 200);
		t.overgrown(205, 205);
		t.counting(206, 209, Crop.ROSEMARY, PatchState.DEAD, 205);
		t.overgrown(210, 210);
		t.counting(211, 214, Crop.NASTURTIUM, PatchState.DEAD, 210);
		t.overgrown(215, 215);
		t.counting(216, 219, Crop.WOAD, PatchState.DEAD, 215);
		t.overgrown(220, 220);
		t.counting(221, 224, Crop.LIMPWURT, PatchState.DEAD, 220);
		t.overgrown(225, 229);
		t.counting(230, 233, Crop.WHITE_LILY, PatchState.DEAD, 229);
		t.overgrown(234, 255);
		return t.values;
	}

	/** 4 growing values, then the grown flower. */
	private static void flower(Table t, int base, Crop crop)
	{
		t.counting(base, base + 3, crop, PatchState.GROWING, base);
		t.single(base + 4, crop, PatchState.HARVESTABLE, 0);
	}

	/** Hops: growing, harvestable (lives left) and diseased per crop; watered growing and dead values further up. */
	private static DecodedPatch[] hopsTable()
	{
		final Table t = new Table();
		t.weedsBeingRaked(0, 3);
		hops(t, 4, 8, 11, 13, Crop.HAMMERSTONE);
		hops(t, 14, 19, 22, 25, Crop.ASGARNIAN);
		hops(t, 26, 32, 35, 39, Crop.YANILLIAN);
		hops(t, 40, 47, 50, 55, Crop.KRANDORIAN);
		hops(t, 56, 64, 67, 73, Crop.WILDBLOOD);
		hops(t, 74, 78, 81, 83, Crop.BARLEY);
		hops(t, 84, 89, 92, 95, Crop.JUTE);
		hops(t, 96, 99, 102, 103, Crop.FLAX);
		hops(t, 104, 108, 111, 113, Crop.HEMP);
		hops(t, 114, 119, 122, 125, Crop.COTTON);
		t.overgrown(126, 131);

		// Watered growing, then dead
		hopsWateredAndDead(t, 132, 136, 139, 141, Crop.HAMMERSTONE);
		hopsWateredAndDead(t, 142, 147, 150, 153, Crop.ASGARNIAN);
		hopsWateredAndDead(t, 154, 160, 163, 167, Crop.YANILLIAN);
		hopsWateredAndDead(t, 168, 175, 178, 183, Crop.KRANDORIAN);
		hopsWateredAndDead(t, 184, 192, 195, 201, Crop.WILDBLOOD);
		hopsWateredAndDead(t, 202, 206, 209, 211, Crop.BARLEY);
		hopsWateredAndDead(t, 212, 217, 220, 223, Crop.JUTE);
		hopsWateredAndDead(t, 224, 227, 230, 231, Crop.FLAX);
		hopsWateredAndDead(t, 232, 236, 239, 240, Crop.HEMP);
		t.single(241, Crop.HEMP, PatchState.DEAD, 3);
		hopsWateredAndDead(t, 242, 247, 250, 253, Crop.COTTON);
		t.overgrown(254, 255);
		return t.values;
	}

	private static void hops(Table t, int base, int harvest, int diseased, int last, Crop crop)
	{
		t.counting(base, harvest - 1, crop, PatchState.GROWING, base);
		t.countingDown(harvest, diseased - 1, crop, PatchState.HARVESTABLE);
		t.counting(diseased, last, crop, PatchState.DISEASED, diseased - 1);
	}

	/** Watered growing values, a few weed values, then the dead ones up to {@code last}. */
	private static void hopsWateredAndDead(Table t, int base, int weeds, int dead, int last, Crop crop)
	{
		t.counting(base, weeds - 1, crop, PatchState.GROWING, base);
		t.overgrown(weeds, dead - 1);
		t.counting(dead, last, crop, PatchState.DEAD, dead - 1);
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
