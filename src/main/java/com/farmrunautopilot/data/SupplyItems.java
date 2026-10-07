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

	private SupplyItems()
	{
	}
}
