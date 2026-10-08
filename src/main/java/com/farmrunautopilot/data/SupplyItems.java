package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.travel.Rune.AIR;
import static com.farmrunautopilot.data.travel.Rune.EARTH;
import static com.farmrunautopilot.data.travel.Rune.FIRE;
import static com.farmrunautopilot.data.travel.Rune.SOUL;
import static com.farmrunautopilot.data.travel.Rune.WATER;
import com.farmrunautopilot.data.travel.Rune;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Item facts the supply calculator needs (SPEC 11): tools, axes, rune sources and potions.
 */
public final class SupplyItems
{
	public static final int[] AXES = {
		ItemID.BRONZE_AXE, ItemID.IRON_AXE, ItemID.STEEL_AXE, ItemID.BLACK_AXE, ItemID.MITHRIL_AXE, ItemID.ADAMANT_AXE,
		ItemID.RUNE_AXE, ItemID.DRAGON_AXE, ItemID.INFERNAL_AXE, ItemID.INFERNAL_AXE_EMPTY, ItemID._3A_AXE,
		ItemID.TRAIL_GILDED_AXE, ItemID.CRYSTAL_AXE, ItemID.TRAILBLAZER_AXE, ItemID.TRAILBLAZER_AXE_EMPTY,
		ItemID.TRAILBLAZER_AXE_NO_INFERNAL, ItemID.TRAILBLAZER_RELOADED_AXE, ItemID.TRAILBLAZER_RELOADED_AXE_EMPTY,
		ItemID.TRAILBLAZER_RELOADED_AXE_NO_INFERNAL,
		// Felling axes
		ItemID.BRONZE_AXE_2H, ItemID.IRON_AXE_2H, ItemID.STEEL_AXE_2H, ItemID.BLACK_AXE_2H, ItemID.MITHRIL_AXE_2H,
		ItemID.ADAMANT_AXE_2H, ItemID.RUNE_AXE_2H, ItemID.DRAGON_AXE_2H, ItemID.CRYSTAL_AXE_2H, ItemID._3A_AXE_2H
	};

	/** Secateurs of any kind; magic secateurs can also be worn. */
	public static final int[] SECATEURS = {ItemID.SECATEURS, ItemID.FAIRY_ENCHANTED_SECATEURS};
	public static final int[] MAGIC_SECATEURS = {ItemID.FAIRY_ENCHANTED_SECATEURS};

	/** Needed for fairy rings unless the Elite Lumbridge & Draynor diary is done. */
	public static final int[] FAIRY_RING_STAFFS = {ItemID.DRAMEN_STAFF, ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF};

	public static final int[] RUNE_POUCHES = {
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
	};

	/** Graceful outfit, one entry per piece, each listing every recolour. */
	public static final int[][] GRACEFUL = {
		graceful(ItemID.GRACEFUL_HOOD, ItemID.ZEAH_GRACEFUL_HOOD_ARCEUUS, ItemID.ZEAH_GRACEFUL_HOOD_PISCARILIUS,
			ItemID.ZEAH_GRACEFUL_HOOD_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_HOOD_SHAYZIEN, ItemID.ZEAH_GRACEFUL_HOOD_HOSIDIUS,
			ItemID.ZEAH_GRACEFUL_HOOD_KOUREND, ItemID.GRACEFUL_HOOD_SKILLCAPECOLOUR, ItemID.GRACEFUL_HOOD_HALLOWED,
			ItemID.GRACEFUL_HOOD_TRAILBLAZER, ItemID.GRACEFUL_HOOD_ADVENTURER, ItemID.GRACEFUL_HOOD_WYRM),
		graceful(ItemID.GRACEFUL_CAPE, ItemID.ZEAH_GRACEFUL_CAPE_ARCEUUS, ItemID.ZEAH_GRACEFUL_CAPE_PISCARILIUS,
			ItemID.ZEAH_GRACEFUL_CAPE_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_CAPE_SHAYZIEN, ItemID.ZEAH_GRACEFUL_CAPE_HOSIDIUS,
			ItemID.ZEAH_GRACEFUL_CAPE_KOUREND, ItemID.GRACEFUL_CAPE_SKILLCAPECOLOUR, ItemID.GRACEFUL_CAPE_HALLOWED,
			ItemID.GRACEFUL_CAPE_TRAILBLAZER, ItemID.GRACEFUL_CAPE_ADVENTURER, ItemID.GRACEFUL_CAPE_WYRM),
		graceful(ItemID.GRACEFUL_TOP, ItemID.ZEAH_GRACEFUL_TOP_ARCEUUS, ItemID.ZEAH_GRACEFUL_TOP_PISCARILIUS,
			ItemID.ZEAH_GRACEFUL_TOP_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_TOP_SHAYZIEN, ItemID.ZEAH_GRACEFUL_TOP_HOSIDIUS,
			ItemID.ZEAH_GRACEFUL_TOP_KOUREND, ItemID.GRACEFUL_TOP_SKILLCAPECOLOUR, ItemID.GRACEFUL_TOP_HALLOWED,
			ItemID.GRACEFUL_TOP_TRAILBLAZER, ItemID.GRACEFUL_TOP_ADVENTURER, ItemID.GRACEFUL_TOP_WYRM),
		graceful(ItemID.GRACEFUL_LEGS, ItemID.ZEAH_GRACEFUL_LEGS_ARCEUUS, ItemID.ZEAH_GRACEFUL_LEGS_PISCARILIUS,
			ItemID.ZEAH_GRACEFUL_LEGS_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_LEGS_SHAYZIEN, ItemID.ZEAH_GRACEFUL_LEGS_HOSIDIUS,
			ItemID.ZEAH_GRACEFUL_LEGS_KOUREND, ItemID.GRACEFUL_LEGS_SKILLCAPECOLOUR, ItemID.GRACEFUL_LEGS_HALLOWED,
			ItemID.GRACEFUL_LEGS_TRAILBLAZER, ItemID.GRACEFUL_LEGS_ADVENTURER, ItemID.GRACEFUL_LEGS_WYRM),
		graceful(ItemID.GRACEFUL_GLOVES, ItemID.ZEAH_GRACEFUL_GLOVES_ARCEUUS, ItemID.ZEAH_GRACEFUL_GLOVES_PISCARILIUS,
			ItemID.ZEAH_GRACEFUL_GLOVES_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_GLOVES_SHAYZIEN, ItemID.ZEAH_GRACEFUL_GLOVES_HOSIDIUS,
			ItemID.ZEAH_GRACEFUL_GLOVES_KOUREND, ItemID.GRACEFUL_GLOVES_SKILLCAPECOLOUR, ItemID.GRACEFUL_GLOVES_HALLOWED,
			ItemID.GRACEFUL_GLOVES_TRAILBLAZER, ItemID.GRACEFUL_GLOVES_ADVENTURER, ItemID.GRACEFUL_GLOVES_WYRM),
		graceful(ItemID.GRACEFUL_BOOTS, ItemID.ZEAH_GRACEFUL_BOOTS_ARCEUUS, ItemID.ZEAH_GRACEFUL_BOOTS_PISCARILIUS,
			ItemID.ZEAH_GRACEFUL_BOOTS_LOVAKENGJ, ItemID.ZEAH_GRACEFUL_BOOTS_SHAYZIEN, ItemID.ZEAH_GRACEFUL_BOOTS_HOSIDIUS,
			ItemID.ZEAH_GRACEFUL_BOOTS_KOUREND, ItemID.GRACEFUL_BOOTS_SKILLCAPECOLOUR, ItemID.GRACEFUL_BOOTS_HALLOWED,
			ItemID.GRACEFUL_BOOTS_TRAILBLAZER, ItemID.GRACEFUL_BOOTS_ADVENTURER, ItemID.GRACEFUL_BOOTS_WYRM)
	};

	/** Farmer's outfit (Tithe Farm), one entry per piece, male and female versions. */
	public static final int[][] FARMERS_OUTFIT = {
		{ItemID.TITHE_REWARD_HAT_MALE, ItemID.TITHE_REWARD_HAT_FEMALE},
		{ItemID.TITHE_REWARD_TORSO_MALE, ItemID.TITHE_REWARD_TORSO_FEMALE},
		{ItemID.TITHE_REWARD_LEGS_MALE, ItemID.TITHE_REWARD_LEGS_FEMALE},
		{ItemID.TITHE_REWARD_FEET_MALE, ItemID.TITHE_REWARD_FEET_FEMALE}
	};

	/** Stamina potion item to the doses it holds. */
	public static final Map<Integer, Integer> STAMINA_DOSES;

	/** Equipped items that supply unlimited runes of these types (staves, battlestaves, tomes). */
	public static final Map<Integer, Set<Rune>> INFINITE_RUNE_ITEMS;

	/**
	 * Combination runes and the runes each one counts as. Sunfire counts as fire; aether as soul (and cosmic,
	 * which no v1 spell uses).
	 */
	public static final Map<Integer, Set<Rune>> COMBINATION_RUNES;

	static
	{
		final Map<Integer, Integer> stamina = new HashMap<>();
		stamina.put(ItemID._1DOSESTAMINA, 1);
		stamina.put(ItemID._2DOSESTAMINA, 2);
		stamina.put(ItemID._3DOSESTAMINA, 3);
		stamina.put(ItemID._4DOSESTAMINA, 4);
		STAMINA_DOSES = Collections.unmodifiableMap(stamina);

		final Map<Integer, Set<Rune>> staves = new HashMap<>();
		staves.put(ItemID.STAFF_OF_AIR, EnumSet.of(AIR));
		staves.put(ItemID.AIR_BATTLESTAFF, EnumSet.of(AIR));
		staves.put(ItemID.MYSTIC_AIR_STAFF, EnumSet.of(AIR));
		staves.put(ItemID.STAFF_OF_WATER, EnumSet.of(WATER));
		staves.put(ItemID.WATER_BATTLESTAFF, EnumSet.of(WATER));
		staves.put(ItemID.MYSTIC_WATER_STAFF, EnumSet.of(WATER));
		staves.put(ItemID.KODAI_WAND, EnumSet.of(WATER));
		staves.put(ItemID.TOME_OF_WATER, EnumSet.of(WATER));
		staves.put(ItemID.STAFF_OF_EARTH, EnumSet.of(EARTH));
		staves.put(ItemID.EARTH_BATTLESTAFF, EnumSet.of(EARTH));
		staves.put(ItemID.MYSTIC_EARTH_STAFF, EnumSet.of(EARTH));
		staves.put(ItemID.TOME_OF_EARTH, EnumSet.of(EARTH));
		staves.put(ItemID.STAFF_OF_FIRE, EnumSet.of(FIRE));
		staves.put(ItemID.FIRE_BATTLESTAFF, EnumSet.of(FIRE));
		staves.put(ItemID.MYSTIC_FIRE_STAFF, EnumSet.of(FIRE));
		staves.put(ItemID.TOME_OF_FIRE, EnumSet.of(FIRE));
		staves.put(ItemID.LAVA_BATTLESTAFF, EnumSet.of(EARTH, FIRE));
		staves.put(ItemID.LAVA_BATTLESTAFF_PRETTY, EnumSet.of(EARTH, FIRE));
		staves.put(ItemID.MYSTIC_LAVA_STAFF, EnumSet.of(EARTH, FIRE));
		staves.put(ItemID.MUD_BATTLESTAFF, EnumSet.of(WATER, EARTH));
		staves.put(ItemID.MYSTIC_MUD_STAFF, EnumSet.of(WATER, EARTH));
		staves.put(ItemID.STEAM_BATTLESTAFF, EnumSet.of(WATER, FIRE));
		staves.put(ItemID.STEAM_BATTLESTAFF_PRETTY, EnumSet.of(WATER, FIRE));
		staves.put(ItemID.MYSTIC_STEAM_BATTLESTAFF, EnumSet.of(WATER, FIRE));
		staves.put(ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY, EnumSet.of(WATER, FIRE));
		staves.put(ItemID.SMOKE_BATTLESTAFF, EnumSet.of(AIR, FIRE));
		staves.put(ItemID.MYSTIC_SMOKE_BATTLESTAFF, EnumSet.of(AIR, FIRE));
		staves.put(ItemID.MIST_BATTLESTAFF, EnumSet.of(AIR, WATER));
		staves.put(ItemID.MYSTIC_MIST_BATTLESTAFF, EnumSet.of(AIR, WATER));
		staves.put(ItemID.DUST_BATTLESTAFF, EnumSet.of(AIR, EARTH));
		staves.put(ItemID.MYSTIC_DUST_BATTLESTAFF, EnumSet.of(AIR, EARTH));
		INFINITE_RUNE_ITEMS = Collections.unmodifiableMap(staves);

		final Map<Integer, Set<Rune>> combos = new HashMap<>();
		combos.put(ItemID.DUSTRUNE, EnumSet.of(AIR, EARTH));
		combos.put(ItemID.MISTRUNE, EnumSet.of(AIR, WATER));
		combos.put(ItemID.MUDRUNE, EnumSet.of(WATER, EARTH));
		combos.put(ItemID.LAVARUNE, EnumSet.of(EARTH, FIRE));
		combos.put(ItemID.STEAMRUNE, EnumSet.of(WATER, FIRE));
		combos.put(ItemID.SMOKERUNE, EnumSet.of(AIR, FIRE));
		combos.put(ItemID.SUNFIRERUNE, EnumSet.of(FIRE));
		combos.put(ItemID.AETHERRUNE, EnumSet.of(SOUL));
		COMBINATION_RUNES = Collections.unmodifiableMap(combos);
	}

	private static int[] graceful(int... variants)
	{
		return variants;
	}

	private SupplyItems()
	{
	}
}
