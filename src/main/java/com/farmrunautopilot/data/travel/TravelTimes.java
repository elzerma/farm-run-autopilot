package com.farmrunautopilot.data.travel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Measured journeys for each travel method, from wiki research (docs/plans/travel-research.md, checked
 * 2026-10-10): the real walking path in tiles from where it lands to the nearest of the stop's patches, and
 * extra seconds for anything that isn't walking (a boat, a quetzal, Elkoy, a ladder). These replace the rough
 * {@link com.farmrunautopilot.data.Walk} sizes; a method missing here falls back to its Walk size.
 */
public final class TravelTimes
{
	private static final Map<TravelMethod, Integer> TILES = new EnumMap<>(TravelMethod.class);
	private static final Map<TravelMethod, Double> EXTRA_SECONDS = new EnumMap<>(TravelMethod.class);

	static
	{
		// Lumbridge
		tiles(TravelMethod.LUMBRIDGE_TELEPORT, 35);
		tiles(TravelMethod.LUMBRIDGE_HOME_TELEPORT, 35);
		tiles(TravelMethod.DIARY_CAPE_LUMBRIDGE, 50);
		// Varrock
		tiles(TravelMethod.VARROCK_TELEPORT, 45);
		tiles(TravelMethod.DIARY_CAPE_VARROCK, 50);
		tiles(TravelMethod.SKILLS_NECKLACE_COOKS_GUILD, 95);
		tiles(TravelMethod.RING_OF_WEALTH_GRAND_EXCHANGE, 80);
		tiles(TravelMethod.SPIRIT_TREE_GRAND_EXCHANGE, 60);
		// Falador Park
		tiles(TravelMethod.RING_OF_WEALTH_FALADOR_PARK, 9);
		tiles(TravelMethod.FALADOR_TELEPORT_TO_PARK, 45);
		tiles(TravelMethod.SKILLS_NECKLACE_MINING_GUILD, 70);
		extra(TravelMethod.SKILLS_NECKLACE_MINING_GUILD, 3);
		// Taverley
		tiles(TravelMethod.HOUSE_PORTAL_TAVERLEY, 43);
		tiles(TravelMethod.TAVERLEY_TABLET, 43);
		tiles(TravelMethod.CONSTRUCTION_CAPE_TAVERLEY, 43);
		// 70 over the 66 Agility rocks, about 100 round by the gate
		tiles(TravelMethod.FALADOR_TELEPORT_TO_TAVERLEY, 85);
		tiles(TravelMethod.GAMES_NECKLACE_BURTHORPE, 120);
		tiles(TravelMethod.COMBAT_BRACELET_WARRIORS_GUILD, 115);
		// Gnome Stronghold (nearest patch: tree by the Slayer Cave, fruit tree by the spirit tree)
		tiles(TravelMethod.SLAYER_RING_STRONGHOLD_CAVE, 8);
		tiles(TravelMethod.SPIRIT_TREE_GNOME_STRONGHOLD, 14);
		tiles(TravelMethod.ROYAL_SEED_POD, 50);
		// Farming Guild
		tiles(TravelMethod.FARMING_CAPE, 10);
		tiles(TravelMethod.SKILLS_NECKLACE_FARMING_GUILD, 10);
		tiles(TravelMethod.MAX_CAPE_FARMING_GUILD, 10);
		tiles(TravelMethod.SPIRIT_TREE_FARMING_GUILD, 11);
		tiles(TravelMethod.FAIRY_RING_CIR, 90);
		tiles(TravelMethod.BATTLEFRONT_TELEPORT, 110);
		tiles(TravelMethod.RADAS_BLESSING_MOUNT_KARUULM, 100);
		// Nemus Retreat
		tiles(TravelMethod.PENDANT_OF_ATES_NEMUS_RETREAT, 44);
		// 44 through the 33 Agility tunnel, 80+ without
		tiles(TravelMethod.QUETZAL_AUBURNVALE, 60);
		tiles(TravelMethod.FAIRY_RING_AIS, 64);
		// Tree Gnome Village: out of the maze behind Elkoy, then a short walk
		tiles(TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, 15);
		extra(TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, 12);
		tiles(TravelMethod.FAIRY_RING_CIQ, 60);
		// Catherby (nearest patch: the herb patch from Camelot)
		tiles(TravelMethod.CAMELOT_TELEPORT, 56);
		tiles(TravelMethod.CATHERBY_TELEPORT, 30);
		// Brimhaven
		tiles(TravelMethod.HOUSE_PORTAL_BRIMHAVEN, 34);
		tiles(TravelMethod.BRIMHAVEN_TABLET, 34);
		tiles(TravelMethod.CONSTRUCTION_CAPE_BRIMHAVEN, 34);
		tiles(TravelMethod.SPIRIT_TREE_BRIMHAVEN, 38);
		tiles(TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT, 57);
		extra(TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT, 12);
		tiles(TravelMethod.GLORY_KARAMJA, 165);
		// Lletya
		tiles(TravelMethod.TELEPORT_CRYSTAL_LLETYA, 17);
		// Kastori
		tiles(TravelMethod.QUETZAL_KASTORI, 36);
		tiles(TravelMethod.PENDANT_OF_ATES_NORTH_KASTORI, 30);
		// Falador farm
		tiles(TravelMethod.EXPLORERS_RING_CABBAGE_PATCH, 25);
		tiles(TravelMethod.DRAYNOR_MANOR_TELEPORT, 60);
		tiles(TravelMethod.SPIRIT_TREE_PORT_SARIM, 55);
		tiles(TravelMethod.GLORY_DRAYNOR, 70);
		tiles(TravelMethod.FALADOR_TELEPORT_TO_FARM, 100);
		// Port Phasmatys
		tiles(TravelMethod.FAIRY_RING_ALQ, 40);
		tiles(TravelMethod.ECTOPHIAL, 60);
		tiles(TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT, 60);
		tiles(TravelMethod.KHARYRLL_TELEPORT, 120);
		// Ardougne farm
		tiles(TravelMethod.ARDOUGNE_CLOAK_FARM, 3);
		tiles(TravelMethod.SKILLS_NECKLACE_FISHING_GUILD, 59);
		tiles(TravelMethod.MAX_CAPE_FISHING_GUILD, 62);
		tiles(TravelMethod.FISHING_GUILD_TELEPORT, 59);
		tiles(TravelMethod.COMBAT_BRACELET_RANGING_GUILD, 67);
		tiles(TravelMethod.FAIRY_RING_BLR, 70);
		tiles(TravelMethod.ARDOUGNE_TELEPORT, 69);
		// Hosidius
		tiles(TravelMethod.XERICS_TALISMAN_GLADE, 16);
		tiles(TravelMethod.HOUSE_PORTAL_HOSIDIUS, 33);
		tiles(TravelMethod.HOSIDIUS_TABLET, 33);
		tiles(TravelMethod.CONSTRUCTION_CAPE_HOSIDIUS, 33);
		tiles(TravelMethod.KHAREDSTS_MEMOIRS_LANCALLIUMS, 62);
		tiles(TravelMethod.FAIRY_RING_AKR, 89);
		tiles(TravelMethod.SKILLS_NECKLACE_WOODCUTTING_GUILD, 76);
		// Troll Stronghold: 16 over the 73 Agility rocks, about 55 through the inside
		tiles(TravelMethod.STONY_BASALT_ROOF, 11);
		tiles(TravelMethod.STONY_BASALT_ENTRANCE, 35);
		tiles(TravelMethod.TROLLHEIM_TELEPORT, 150);
		extra(TravelMethod.TROLLHEIM_TELEPORT, 10);
		// Harmony Island, Weiss
		tiles(TravelMethod.HARMONY_ISLAND_TELEPORT, 32);
		tiles(TravelMethod.ICY_BASALT, 7);
		// Civitas illa Fortis / Ortus Farm
		tiles(TravelMethod.QUETZAL_HUNTER_GUILD, 39);
		tiles(TravelMethod.HUNTER_CAPE, 48);
		tiles(TravelMethod.MAX_CAPE_HUNTER_GUILD, 48);
		tiles(TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT, 54);
		extra(TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT, 8);
		tiles(TravelMethod.FAIRY_RING_AJP, 84);
	}

	private TravelTimes()
	{
	}

	private static void tiles(TravelMethod method, int tiles)
	{
		TILES.put(method, tiles);
	}

	private static void extra(TravelMethod method, double seconds)
	{
		EXTRA_SECONDS.put(method, seconds);
	}

	/** Measured tiles from landing to the patch, or null if not measured. */
	public static Integer tiles(TravelMethod method)
	{
		return TILES.get(method);
	}

	/** Seconds on top of walking for rides and the like (0 if none). */
	public static double extraSeconds(TravelMethod method)
	{
		return EXTRA_SECONDS.getOrDefault(method, 0.0);
	}

	/** Every measured method, for tests. */
	public static Map<TravelMethod, Integer> allTiles()
	{
		return Collections.unmodifiableMap(TILES);
	}
}
