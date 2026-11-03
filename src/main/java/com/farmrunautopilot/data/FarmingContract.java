package com.farmrunautopilot.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * Farming Guild contracts (docs/plans/farming-contracts.md, option (c), elzerma 2026-10-17 and 2026-11-02): Guildmaster
 * Jane asks for one crop at a time, grown in the guild's patch of its type, and gives a seed pack once its health is
 * checked or it's fully picked. The tables here are from the OSRS Wiki (Farming contracts; Ironman Guide/Farming, Farming
 * contracts) and RuneLite's Time Tracking (Produce's contract values, BSD-2, see THIRD_PARTY_NOTICES).
 */
public final class FarmingContract
{
	/** Guildmaster Jane's name in game, to outline her (wiki: Guildmaster Jane, NPC ids 8586 and 8587). */
	public static final String JANE = "Guildmaster Jane";
	/** Where she stands, in the guild's central area (wiki: Guildmaster Jane). */
	public static final WorldPoint JANE_POINT = new WorldPoint(1249, 3727, 0);
	/**
	 * The game's message once the crop is checked or picked (other plugins match it; when exactly it shows is
	 * UNVERIFIED): "You've completed a Farming Guild Contract. You should return to Guildmaster Jane."
	 */
	public static final Pattern COMPLETED = Pattern.compile("You've completed a Farming Guild Contract\\..*");

	/** A crop that grows longer than a bush (2 h 40 min) is slow: rather than clear a pre-plant for it, ask for another. */
	public static final int QUICK_MINUTES = 160;

	/** The contracts Jane offers, with the Farming level each needs (wiki: Farming contracts). */
	@Getter
	public enum Tier
	{
		EASY("Easy", 45), MEDIUM("Medium", 65), HARD("Hard", 85);

		private final String displayName;
		private final int level;

		Tier(String displayName, int level)
		{
			this.displayName = displayName;
			this.level = level;
		}

		/** The highest tier this Farming level can ask for (elzerma, 2026-11-02), or null below 45. */
		public static Tier highest(int farming)
		{
			return farming >= HARD.level ? HARD : farming >= MEDIUM.level ? MEDIUM : farming >= EASY.level ? EASY : null;
		}

		/**
		 * The tier as FARMGUILD_CONTRACT_LEVEL has it, or null. UNVERIFIED: 1 Easy, 2 Medium, 3 Hard is a guess the dev
		 * check is to confirm (TestItem.CONTRACT_VALUES).
		 */
		public static Tier fromVarbit(int value)
		{
			return value == 1 ? EASY : value == 2 ? MEDIUM : value == 3 ? HARD : null;
		}
	}

	/** The guild's patches a contract can use (not its spirit tree, anima or Hespori patches). */
	public static final List<Patch> PATCHES = Collections.unmodifiableList(Arrays.asList(Patch.FARMING_GUILD_HERB,
		Patch.FARMING_GUILD_TREE, Patch.FARMING_GUILD_FRUIT_TREE, Patch.FARMING_GUILD_CELASTRUS,
		Patch.FARMING_GUILD_REDWOOD, Patch.FARMING_GUILD_BUSH, Patch.FARMING_GUILD_CACTUS, Patch.FARMING_GUILD_FLOWER,
		Patch.FARMING_GUILD_ALLOTMENT_N, Patch.FARMING_GUILD_ALLOTMENT_S));

	/** Each contract value's crop, as FARMGUILD_CONTRACT_TYPE has it (RuneLite's Produce.contractVarbitValue). */
	private static final Map<Integer, Crop> BY_VALUE = new HashMap<>();
	/** The tiers each crop can be asked for in (wiki: Farming contracts: 31 Easy, 22 Medium, 18 Hard). */
	private static final Map<Crop, Set<Tier>> TIERS = new EnumMap<>(Crop.class);
	/** The wiki's pre-plants for each patch, by tier (Ironman Guide/Farming: hard and medium contracts). */
	private static final Map<Tier, Map<Patch, List<Crop>>> PRE_PLANTS = new EnumMap<>(Tier.class);

	static
	{
		values(1, Crop.REDBERRY, Crop.CADAVABERRY, Crop.DWELLBERRY, Crop.JANGERBERRY, Crop.WHITEBERRY, Crop.POISON_IVY);
		values(7, Crop.OAK, Crop.WILLOW, Crop.MAPLE, Crop.YEW, Crop.MAGIC);
		values(12, Crop.APPLE, Crop.BANANA, Crop.ORANGE, Crop.CURRY, Crop.PINEAPPLE, Crop.PAPAYA, Crop.PALM,
			Crop.DRAGONFRUIT);
		values(20, Crop.GUAM, Crop.MARRENTILL, Crop.TARROMIN, Crop.HARRALANDER, Crop.RANARR, Crop.TOADFLAX, Crop.IRIT,
			Crop.AVANTOE, Crop.KWUARM, Crop.SNAPDRAGON, Crop.CADANTINE, Crop.LANTADYME, Crop.DWARF_WEED, Crop.TORSTOL);
		values(34, Crop.MARIGOLD, Crop.ROSEMARY, Crop.NASTURTIUM, Crop.WOAD, Crop.LIMPWURT, Crop.WHITE_LILY);
		values(40, Crop.POTATO, Crop.ONION, Crop.CABBAGE, Crop.TOMATO, Crop.SWEETCORN, Crop.STRAWBERRY, Crop.WATERMELON,
			Crop.SNAPE_GRASS);
		values(48, Crop.CACTUS, Crop.POTATO_CACTUS);
		values(50, Crop.REDWOOD, Crop.CELASTRUS);

		tier(Tier.EASY, Crop.POTATO, Crop.ONION, Crop.CABBAGE, Crop.TOMATO, Crop.SWEETCORN, Crop.STRAWBERRY,
			Crop.MARIGOLD, Crop.ROSEMARY, Crop.NASTURTIUM, Crop.WOAD, Crop.LIMPWURT, Crop.REDBERRY, Crop.CADAVABERRY,
			Crop.DWELLBERRY, Crop.JANGERBERRY, Crop.CACTUS, Crop.GUAM, Crop.MARRENTILL, Crop.TARROMIN, Crop.HARRALANDER,
			Crop.RANARR, Crop.TOADFLAX, Crop.IRIT, Crop.AVANTOE, Crop.OAK, Crop.WILLOW, Crop.MAPLE, Crop.APPLE,
			Crop.BANANA, Crop.ORANGE, Crop.CURRY);
		tier(Tier.MEDIUM, Crop.STRAWBERRY, Crop.WATERMELON, Crop.SNAPE_GRASS, Crop.WHITE_LILY, Crop.JANGERBERRY,
			Crop.WHITEBERRY, Crop.POISON_IVY, Crop.CACTUS, Crop.POTATO_CACTUS, Crop.IRIT, Crop.AVANTOE, Crop.KWUARM,
			Crop.SNAPDRAGON, Crop.CADANTINE, Crop.LANTADYME, Crop.MAPLE, Crop.YEW, Crop.MAGIC, Crop.CURRY,
			Crop.PINEAPPLE, Crop.PAPAYA, Crop.PALM);
		tier(Tier.HARD, Crop.WATERMELON, Crop.SNAPE_GRASS, Crop.WHITE_LILY, Crop.WHITEBERRY, Crop.POISON_IVY,
			Crop.POTATO_CACTUS, Crop.SNAPDRAGON, Crop.CADANTINE, Crop.LANTADYME, Crop.DWARF_WEED, Crop.TORSTOL,
			Crop.MAPLE, Crop.YEW, Crop.MAGIC, Crop.PALM, Crop.DRAGONFRUIT, Crop.CELASTRUS, Crop.REDWOOD);

		final Map<Patch, List<Crop>> hard = new EnumMap<>(Patch.class);
		hard.put(Patch.FARMING_GUILD_HERB, Arrays.asList(Crop.CADANTINE, Crop.LANTADYME, Crop.SNAPDRAGON));
		hard.put(Patch.FARMING_GUILD_TREE, Arrays.asList(Crop.MAPLE, Crop.YEW));
		hard.put(Patch.FARMING_GUILD_FRUIT_TREE, Collections.singletonList(Crop.PALM));
		hard.put(Patch.FARMING_GUILD_CELASTRUS, Collections.singletonList(Crop.CELASTRUS));
		hard.put(Patch.FARMING_GUILD_REDWOOD, Collections.singletonList(Crop.REDWOOD));
		hard.put(Patch.FARMING_GUILD_BUSH, Arrays.asList(Crop.POISON_IVY, Crop.WHITEBERRY));
		hard.put(Patch.FARMING_GUILD_CACTUS, Collections.singletonList(Crop.POTATO_CACTUS));
		hard.put(Patch.FARMING_GUILD_FLOWER, Arrays.asList(Crop.WHITE_LILY, Crop.NASTURTIUM));
		hard.put(Patch.FARMING_GUILD_ALLOTMENT_N, Collections.singletonList(Crop.WATERMELON));
		hard.put(Patch.FARMING_GUILD_ALLOTMENT_S, Collections.singletonList(Crop.SNAPE_GRASS));
		PRE_PLANTS.put(Tier.HARD, hard);
		final Map<Patch, List<Crop>> medium = new EnumMap<>(Patch.class);
		medium.put(Patch.FARMING_GUILD_HERB, Collections.singletonList(Crop.IRIT));
		medium.put(Patch.FARMING_GUILD_TREE, Collections.singletonList(Crop.MAPLE));
		medium.put(Patch.FARMING_GUILD_BUSH, Arrays.asList(Crop.WHITEBERRY, Crop.JANGERBERRY));
		medium.put(Patch.FARMING_GUILD_CACTUS, Collections.singletonList(Crop.CACTUS));
		medium.put(Patch.FARMING_GUILD_FLOWER, Collections.singletonList(Crop.WHITE_LILY));
		medium.put(Patch.FARMING_GUILD_ALLOTMENT_N, Collections.singletonList(Crop.WATERMELON));
		medium.put(Patch.FARMING_GUILD_ALLOTMENT_S, Collections.singletonList(Crop.STRAWBERRY));
		PRE_PLANTS.put(Tier.MEDIUM, medium);
	}

	private FarmingContract()
	{
	}

	private static void values(int first, Crop... crops)
	{
		for (int i = 0; i < crops.length; i++)
		{
			BY_VALUE.put(first + i, crops[i]);
		}
	}

	private static void tier(Tier tier, Crop... crops)
	{
		for (Crop crop : crops)
		{
			TIERS.computeIfAbsent(crop, k -> EnumSet.noneOf(Tier.class)).add(tier);
		}
	}

	/** The crop FARMGUILD_CONTRACT_TYPE names, or null for none (0) or a value the table doesn't have. */
	public static Crop crop(int value)
	{
		return BY_VALUE.get(value);
	}

	/** The tiers a crop can be asked for in; empty for one that's never a contract. */
	public static Set<Tier> tiers(Crop crop)
	{
		final Set<Tier> tiers = crop != null ? TIERS.get(crop) : null;
		return tiers != null ? Collections.unmodifiableSet(tiers) : Collections.emptySet();
	}

	/**
	 * The wiki's pre-plants for a guild patch, best first (one is chosen by what the player has): the Hard table at
	 * 85 Farming, the Medium one below it, as far as the level can plant them; empty when the table has none.
	 */
	public static List<Crop> prePlants(Patch patch, int farming)
	{
		final Map<Patch, List<Crop>> table = PRE_PLANTS.get(farming >= Tier.HARD.getLevel() ? Tier.HARD : Tier.MEDIUM);
		final List<Crop> crops = table.getOrDefault(patch, Collections.emptyList());
		final List<Crop> plantable = new ArrayList<>();
		for (Crop crop : crops)
		{
			if (crop.getFarmingLevel() <= farming)
			{
				plantable.add(crop);
			}
		}
		return plantable;
	}

	/** This guild contract patch is of the crop's type, so a contract for the crop can be grown in it. */
	public static boolean patchTakes(Patch patch, Crop crop)
	{
		return crop != null && PATCHES.contains(patch) && patch.getType() == crop.getType();
	}

	/** It grows in no more time than a bush, so a pre-plant can make way for it. */
	public static boolean quick(Crop crop)
	{
		return crop.getGrowthMinutes() <= QUICK_MINUTES;
	}
}
