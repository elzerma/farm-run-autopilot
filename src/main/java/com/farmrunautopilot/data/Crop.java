package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.DataConstants.NONE;
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
	TORSTOL(PatchType.HERB, "Torstol", 85, ItemID.TORSTOL_SEED, 20, 5, NONE, 0);

	/**
	 * Minutes for one fruit to regrow on a grown fruit tree. RuneLite uses 45; the wiki says 40
	 * (UNVERIFIED).
	 */
	public static final int FRUIT_REGROW_MINUTES = 45;
	/** Coins a gardener charges to remove a grown tree or fruit tree (wiki). */
	public static final int CLEAR_PATCH_COINS = 200;

	private final PatchType type;
	private final String displayName;
	private final int farmingLevel;
	/** Sapling for trees and fruit trees, seed for herbs. */
	private final int plantItemId;
	private final int tickMinutes;
	private final int stages;
	/** Gardener protection payment, or {@link DataConstants#NONE} for herbs. */
	private final int paymentItemId;
	private final int paymentQuantity;

	Crop(PatchType type, String displayName, int farmingLevel, int plantItemId, int tickMinutes, int stages,
		int paymentItemId, int paymentQuantity)
	{
		this.type = type;
		this.displayName = displayName;
		this.farmingLevel = farmingLevel;
		this.plantItemId = plantItemId;
		this.tickMinutes = tickMinutes;
		this.stages = stages;
		this.paymentItemId = paymentItemId;
		this.paymentQuantity = paymentQuantity;
	}

	public int getGrowthMinutes()
	{
		return tickMinutes * (stages - 1);
	}

	public boolean hasPayment()
	{
		return paymentItemId != NONE;
	}
}
