package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.DataConstants.NONE;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

/**
 * Plantable crops (SPEC 7). Growth ticks and stages are copied from RuneLite core
 * {@code timetracking/farming/Produce} (BSD-2, see THIRD_PARTY_NOTICES): growth time is
 * {@code tickMinutes x (stages - 1)}.
 *
 * <p>Fruit payments must be baskets (loose fruit is not accepted). Gardeners accept noted payment, which
 * is the preferred form because it takes one inventory slot (confirmed in-game).
 */
@Getter
public enum Crop
{
	OAK(PatchType.TREE, "Oak", 15, ItemID.PLANTPOT_OAK_SAPLING, 40, 5, ItemID.BASKET_TOMATO_5, 1),
	WILLOW(PatchType.TREE, "Willow", 30, ItemID.PLANTPOT_WILLOW_SAPLING, 40, 7, ItemID.BASKET_APPLE_5, 1),
	MAPLE(PatchType.TREE, "Maple", 45, ItemID.PLANTPOT_MAPLE_SAPLING, 40, 9, ItemID.BASKET_ORANGE_5, 1),
	YEW(PatchType.TREE, "Yew", 60, ItemID.PLANTPOT_YEW_SAPLING, 40, 11, ItemID.CACTUS_SPINE, 10),
	MAGIC(PatchType.TREE, "Magic", 75, ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 40, 13, ItemID.COCONUT, 25),

	APPLE(PatchType.FRUIT_TREE, "Apple", 27, ItemID.PLANTPOT_APPLE_SAPLING, 160, 7, ItemID.SWEETCORN, 9),
	BANANA(PatchType.FRUIT_TREE, "Banana", 33, ItemID.PLANTPOT_BANANA_SAPLING, 160, 7, ItemID.BASKET_APPLE_5, 4),
	ORANGE(PatchType.FRUIT_TREE, "Orange", 39, ItemID.PLANTPOT_ORANGE_SAPLING, 160, 7, ItemID.BASKET_STRAWBERRY_5, 3),
	CURRY(PatchType.FRUIT_TREE, "Curry", 42, ItemID.PLANTPOT_CURRY_SAPLING, 160, 7, ItemID.BASKET_BANANA_5, 5),
	PINEAPPLE(PatchType.FRUIT_TREE, "Pineapple", 51, ItemID.PLANTPOT_PINEAPPLE_SAPLING, 160, 7, ItemID.WATERMELON, 10),
	PAPAYA(PatchType.FRUIT_TREE, "Papaya", 57, ItemID.PLANTPOT_PAPAYA_SAPLING, 160, 7, ItemID.PINEAPPLE, 10),
	PALM(PatchType.FRUIT_TREE, "Palm", 68, ItemID.PLANTPOT_PALM_SAPLING, 160, 7, ItemID.PAPAYA, 15),
	DRAGONFRUIT(PatchType.FRUIT_TREE, "Dragonfruit", 81, ItemID.PLANTPOT_DRAGONFRUIT_SAPLING, 160, 7, ItemID.COCONUT, 15),

	GUAM(PatchType.HERB, "Guam", 9, ItemID.GUAM_SEED, 20, 5, NONE, 0),
	MARRENTILL(PatchType.HERB, "Marrentill", 14, ItemID.MARRENTILL_SEED, 20, 5, NONE, 0),
	TARROMIN(PatchType.HERB, "Tarromin", 19, ItemID.TARROMIN_SEED, 20, 5, NONE, 0),
	HARRALANDER(PatchType.HERB, "Harralander", 26, ItemID.HARRALANDER_SEED, 20, 5, NONE, 0),
	RANARR(PatchType.HERB, "Ranarr", 32, ItemID.RANARR_SEED, 20, 5, NONE, 0),
	TOADFLAX(PatchType.HERB, "Toadflax", 38, ItemID.TOADFLAX_SEED, 20, 5, NONE, 0),
	IRIT(PatchType.HERB, "Irit", 44, ItemID.IRIT_SEED, 20, 5, NONE, 0),
	AVANTOE(PatchType.HERB, "Avantoe", 50, ItemID.AVANTOE_SEED, 20, 5, NONE, 0),
	KWUARM(PatchType.HERB, "Kwuarm", 56, ItemID.KWUARM_SEED, 20, 5, NONE, 0),
	SNAPDRAGON(PatchType.HERB, "Snapdragon", 62, ItemID.SNAPDRAGON_SEED, 20, 5, NONE, 0),
	HUASCA(PatchType.HERB, "Huasca", 65, ItemID.HUASCA_SEED, 20, 5, NONE, 0),
	CADANTINE(PatchType.HERB, "Cadantine", 67, ItemID.CADANTINE_SEED, 20, 5, NONE, 0),
	LANTADYME(PatchType.HERB, "Lantadyme", 73, ItemID.LANTADYME_SEED, 20, 5, NONE, 0),
	DWARF_WEED(PatchType.HERB, "Dwarf weed", 79, ItemID.DWARF_WEED_SEED, 20, 5, NONE, 0),
	TORSTOL(PatchType.HERB, "Torstol", 85, ItemID.TORSTOL_SEED, 20, 5, NONE, 0),

	// Allotments: 3 seeds a patch; payment per patch (wiki: Allotment patch/Seeds)
	POTATO(PatchType.ALLOTMENT, "Potato", 1, ItemID.POTATO_SEED, 3, 10, 5, ItemID.BUCKET_COMPOST, 2),
	ONION(PatchType.ALLOTMENT, "Onion", 5, ItemID.ONION_SEED, 3, 10, 5, ItemID.SACK_POTATO_10, 1),
	CABBAGE(PatchType.ALLOTMENT, "Cabbage", 7, ItemID.CABBAGE_SEED, 3, 10, 5, ItemID.SACK_ONION_10, 1),
	TOMATO(PatchType.ALLOTMENT, "Tomato", 12, ItemID.TOMATO_SEED, 3, 10, 5, ItemID.SACK_CABBAGE_10, 2),
	SWEETCORN(PatchType.ALLOTMENT, "Sweetcorn", 20, ItemID.SWEETCORN_SEED, 3, 10, 7, ItemID.JUTE_FIBRE, 10),
	STRAWBERRY(PatchType.ALLOTMENT, "Strawberry", 31, ItemID.STRAWBERRY_SEED, 3, 10, 7, ItemID.BASKET_APPLE_5, 1),
	WATERMELON(PatchType.ALLOTMENT, "Watermelon", 47, ItemID.WATERMELON_SEED, 3, 10, 9, ItemID.CURRY_LEAF, 10),
	SNAPE_GRASS(PatchType.ALLOTMENT, "Snape grass", 61, ItemID.SNAPE_GRASS_SEED, 3, 10, 8, ItemID.JANGERBERRIES, 5),

	// Flowers: 1 seed, no gardener protects them
	MARIGOLD(PatchType.FLOWER, "Marigold", 2, ItemID.MARIGOLD_SEED, 1, 5, 5, NONE, 0),
	ROSEMARY(PatchType.FLOWER, "Rosemary", 11, ItemID.ROSEMARY_SEED, 1, 5, 5, NONE, 0),
	// Placed rather than grown (made at 23 Farming); protects sweetcorn
	SCARECROW(PatchType.FLOWER, "Scarecrow", 23, ItemID.SCARECROW_COMPLETE, 1, 0, 1, NONE, 0),
	NASTURTIUM(PatchType.FLOWER, "Nasturtium", 24, ItemID.NASTURTIUM_SEED, 1, 5, 5, NONE, 0),
	WOAD(PatchType.FLOWER, "Woad", 25, ItemID.WOAD_SEED, 1, 5, 5, NONE, 0),
	LIMPWURT(PatchType.FLOWER, "Limpwurt", 26, ItemID.LIMPWURT_SEED, 1, 5, 5, NONE, 0),
	WHITE_LILY(PatchType.FLOWER, "White lily", 58, ItemID.WHITE_LILY_SEED, 1, 5, 5, NONE, 0),

	// Hops: 4 seeds, or 3 for the fibre crops (wiki: Hops patch/Seeds)
	BARLEY(PatchType.HOPS, "Barley", 3, ItemID.BARLEY_SEED, 4, 10, 5, ItemID.BUCKET_COMPOST, 3),
	HAMMERSTONE(PatchType.HOPS, "Hammerstone", 4, ItemID.HAMMERSTONE_HOP_SEED, 4, 10, 5, ItemID.MARIGOLD, 1),
	ASGARNIAN(PatchType.HOPS, "Asgarnian", 8, ItemID.ASGARNIAN_HOP_SEED, 4, 10, 6, ItemID.SACK_ONION_10, 1),
	JUTE(PatchType.HOPS, "Jute", 13, ItemID.JUTE_SEED, 3, 10, 6, ItemID.BARLEY_MALT, 6),
	YANILLIAN(PatchType.HOPS, "Yanillian", 16, ItemID.YANILLIAN_HOP_SEED, 4, 10, 7, ItemID.BASKET_TOMATO_5, 1),
	FLAX(PatchType.HOPS, "Flax", 18, ItemID.FLAX_SEED, 3, 20, 4, ItemID.GRAIN, 6),
	KRANDORIAN(PatchType.HOPS, "Krandorian", 21, ItemID.KRANDORIAN_HOP_SEED, 4, 10, 8, ItemID.SACK_CABBAGE_10, 3),
	WILDBLOOD(PatchType.HOPS, "Wildblood", 28, ItemID.WILDBLOOD_HOP_SEED, 4, 10, 9, ItemID.NASTURTIUM, 1),
	HEMP(PatchType.HOPS, "Hemp", 37, ItemID.HEMP_SEED, 3, 20, 5, ItemID.FLAX, 6),
	COTTON(PatchType.HOPS, "Cotton", 71, ItemID.COTTON_SEED, 3, 20, 6, ItemID.HEMP, 6),

	// Sprint 1b. Bushes: one seed; the berries regrow every 20 minutes, up to 4 (wiki: Bush patch/Seeds; payments there)
	REDBERRY(PatchType.BUSH, "Redberry", 10, ItemID.REDBERRY_BUSH_SEED, 20, 6, ItemID.SACK_CABBAGE_10, 4),
	CADAVABERRY(PatchType.BUSH, "Cadavaberry", 22, ItemID.CADAVABERRY_BUSH_SEED, 20, 7, ItemID.BASKET_TOMATO_5, 3),
	DWELLBERRY(PatchType.BUSH, "Dwellberry", 36, ItemID.DWELLBERRY_BUSH_SEED, 20, 8, ItemID.BASKET_STRAWBERRY_5, 3),
	JANGERBERRY(PatchType.BUSH, "Jangerberry", 48, ItemID.JANGERBERRY_BUSH_SEED, 20, 9, ItemID.WATERMELON, 6),
	WHITEBERRY(PatchType.BUSH, "Whiteberry", 59, ItemID.WHITEBERRY_BUSH_SEED, 20, 9, ItemID.BITTERCAP_MUSHROOM, 8),
	// Immune to disease: no payment
	POISON_IVY(PatchType.BUSH, "Poison ivy", 70, ItemID.POISONIVY_BUSH_SEED, 20, 9, NONE, 0),
	// Cacti (wiki: Special patches/Seeds): one seed (UNVERIFIED)
	CACTUS(PatchType.CACTUS, "Cactus", 55, ItemID.CACTUS_SEED, 80, 8, ItemID.CADAVABERRIES, 6),
	POTATO_CACTUS(PatchType.CACTUS, "Potato cactus", 64, ItemID.POTATO_CACTUS_SEED, 10, 8, ItemID.SNAPE_GRASS, 8),
	// Can't be protected: one spore or seed (UNVERIFIED for the spore)
	BITTERCAP(PatchType.MUSHROOM, "Bittercap", 53, ItemID.MUSHROOM_SEED, 40, 7, NONE, 0),
	BELLADONNA(PatchType.BELLADONNA, "Belladonna", 63, ItemID.BELLADONNA_SEED, 80, 5, NONE, 0),
	// Mernia protects each underwater patch for 200 numulites (wiki: Mernia); one spore (UNVERIFIED)
	GIANT_SEAWEED(PatchType.SEAWEED, "Giant seaweed", 23, ItemID.SEAWEED_SEED, 10, 5, ItemID.FOSSIL_NUMULITE, 200),

	// Sprint 1c, the special trees: one sapling each; growth from RuneLite Produce (master, 2026-10-25), levels and payments
	// from the wiki's sapling pages (docs/plans/crop-types.md, section 4). Rosewood is RuneLite's 5,760 minutes
	TEAK(PatchType.HARDWOOD, "Teak", 35, ItemID.PLANTPOT_TEAK_SAPLING, 640, 8, ItemID.LIMPWURT_ROOT, 15),
	MAHOGANY(PatchType.HARDWOOD, "Mahogany", 55, ItemID.PLANTPOT_MAHOGANY_SAPLING, 640, 9, ItemID.YANILLIAN_HOPS, 25),
	CAMPHOR(PatchType.HARDWOOD, "Camphor", 66, ItemID.PLANTPOT_CAMPHOR_SAPLING, 640, 9, ItemID.WHITE_BERRIES, 10),
	IRONWOOD(PatchType.HARDWOOD, "Ironwood", 80, ItemID.PLANTPOT_IRONWOOD_SAPLING, 640, 9, ItemID.CURRY_LEAF, 10),
	ROSEWOOD(PatchType.HARDWOOD, "Rosewood", 92, ItemID.PLANTPOT_ROSEWOOD_SAPLING, 640, 10, ItemID.DRAGONFRUIT, 8),
	CALQUAT(PatchType.CALQUAT, "Calquat", 72, ItemID.PLANTPOT_CALQUAT_SAPLING, 160, 9, ItemID.POISONIVY_BERRIES, 8),
	CELASTRUS(PatchType.CELASTRUS, "Celastrus", 85, ItemID.PLANTPOT_CELASTRUS_TREE_SAPLING, 160, 6, ItemID.CACTUS_POTATO, 8),
	REDWOOD(PatchType.REDWOOD, "Redwood", 90, ItemID.PLANTPOT_REDWOOD_TREE_SAPLING, 640, 11, ItemID.DRAGONFRUIT, 6),
	// Also a monkey bar and a ground tooth ({@link #getExtraPayments})
	SPIRIT_TREE(PatchType.SPIRIT_TREE, "Spirit tree", 83, ItemID.PLANTPOT_SPIRIT_TREE_SAPLING, 320, 13, ItemID.MM_MONKEY_NUTS,
		5),
	// Can't get diseased: no payment (wiki: Crystal acorn)
	CRYSTAL_TREE(PatchType.CRYSTAL_TREE, "Crystal tree", 74, ItemID.PLANTPOT_CRYSTAL_TREE_SAPLING, 80, 7, NONE, 0);

	/**
	 * Minutes for one fruit to regrow on a grown fruit tree. RuneLite uses 45; the wiki says 40
	 * (UNVERIFIED).
	 */
	public static final int FRUIT_REGROW_MINUTES = 45;
	/** Coins a gardener charges to remove a grown tree or fruit tree (wiki). */
	public static final int CLEAR_PATCH_COINS = 200;
	/** Coins Alexandra charges to clear a redwood, the only way to (wiki: Redwood tree). */
	public static final int CLEAR_REDWOOD_COINS = 2000;

	private final PatchType type;
	private final String displayName;
	private final int farmingLevel;
	/** Sapling for trees and fruit trees, seed for the rest (the scarecrow item for a scarecrow). */
	private final int plantItemId;
	private final int tickMinutes;
	private final int stages;
	/** Gardener protection payment, or {@link DataConstants#NONE} for crops no gardener protects. */
	private final int paymentItemId;
	private final int paymentQuantity;

	/** Seeds planted in one patch (allotments 3, hops 4 or 3), 1 for saplings and most seeds. */
	private final int seedsPerPatch;

	Crop(PatchType type, String displayName, int farmingLevel, int plantItemId, int tickMinutes, int stages,
		int paymentItemId, int paymentQuantity)
	{
		this(type, displayName, farmingLevel, plantItemId, 1, tickMinutes, stages, paymentItemId, paymentQuantity);
	}

	Crop(PatchType type, String displayName, int farmingLevel, int plantItemId, int seedsPerPatch, int tickMinutes,
		int stages, int paymentItemId, int paymentQuantity)
	{
		this.type = type;
		this.displayName = displayName;
		this.farmingLevel = farmingLevel;
		this.plantItemId = plantItemId;
		this.seedsPerPatch = seedsPerPatch;
		this.tickMinutes = tickMinutes;
		this.stages = stages;
		this.paymentItemId = paymentItemId;
		this.paymentQuantity = paymentQuantity;
	}

	public int getGrowthMinutes()
	{
		return tickMinutes * (stages - 1);
	}

	/**
	 * Minutes for one fruit, berry or spine to regrow on a grown crop that regrows, 0 for the rest (RuneLite Produce:
	 * bushes and the cactus 20, the potato cactus 5; fruit trees {@link #FRUIT_REGROW_MINUTES}).
	 */
	public int getRegrowMinutes()
	{
		switch (type)
		{
			case FRUIT_TREE:
				return FRUIT_REGROW_MINUTES;
			case BUSH:
				return 20;
			case CACTUS:
				return this == POTATO_CACTUS ? 5 : 20;
			// RuneLite has no regrow timer for it; the wiki says one fruit per 20 minutes (UNVERIFIED)
			case CALQUAT:
				return 20;
			default:
				return 0;
		}
	}

	/**
	 * How many a grown crop that regrows holds at most, as the harvestable stages count (RuneLite: fruit 0-6, berries
	 * 0-4, cactus spines 0-3, potato cacti 0-6); 0 for crops that don't regrow.
	 */
	public int getHarvestStages()
	{
		switch (type)
		{
			case FRUIT_TREE:
				return 7;
			case BUSH:
				return 5;
			case CACTUS:
				return this == POTATO_CACTUS ? 7 : 4;
			// RuneLite: calquat fruit 0-6
			case CALQUAT:
				return 7;
			default:
				return 0;
		}
	}

	/** Coins the gardener charges to clear a grown tree of this type (the hardwood squirrels and Argo too, wiki). */
	public static int clearCoins(PatchType type)
	{
		return type == PatchType.REDWOOD ? CLEAR_REDWOOD_COINS : CLEAR_PATCH_COINS;
	}

	/** Offered as a crop to grow; the scarecrow is only placed to protect sweetcorn. */
	public boolean isChoosable()
	{
		return this != SCARECROW;
	}

	/**
	 * Planted from a stack of seeds with a dibber; saplings (every tree, the special trees too) don't stack and need a
	 * spade. Bushes and cacti are health-checked but grown from seed.
	 */
	public boolean isSeed()
	{
		return !type.isHealthChecked() || type == PatchType.BUSH || type == PatchType.CACTUS;
	}

	/**
	 * Items the gardener takes besides {@link #getPaymentItemId}: a spirit tree's monkey bar and ground tooth, with its
	 * 5 monkey nuts (wiki: Spirit Tree (Farming)). Empty for the rest.
	 */
	public Map<Integer, Integer> getExtraPayments()
	{
		if (this == SPIRIT_TREE)
		{
			final Map<Integer, Integer> extras = new LinkedHashMap<>();
			extras.put(ItemID.MM_MONKEY_BAR, 1);
			extras.put(ItemID.LUNAR_GROUNDTOOTH, 1);
			return extras;
		}
		return Collections.emptyMap();
	}

	/**
	 * The flowers that protect this allotment crop once fully grown in its set's flower patch, the cheapest first: the
	 * crop's own flower, then white lily, which protects every allotment crop (wiki: Flower patch, Allotment patch/Seeds).
	 * Empty for anything that isn't an allotment crop.
	 */
	public List<Crop> getProtectingFlowers()
	{
		switch (this)
		{
			case POTATO:
			case ONION:
			case TOMATO:
				return Arrays.asList(MARIGOLD, WHITE_LILY);
			case CABBAGE:
				return Arrays.asList(ROSEMARY, WHITE_LILY);
			case WATERMELON:
				return Arrays.asList(NASTURTIUM, WHITE_LILY);
			case SWEETCORN:
				return Arrays.asList(SCARECROW, WHITE_LILY);
			case STRAWBERRY:
			case SNAPE_GRASS:
				return Collections.singletonList(WHITE_LILY);
			default:
				return Collections.emptyList();
		}
	}

	public boolean hasPayment()
	{
		return paymentItemId != NONE;
	}
}
