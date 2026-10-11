package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Unlock;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Measured journeys for each travel method, from wiki research (docs/plans/travel-research.md, checked
 * 2026-10-10): the real walking path in tiles from where it lands to the nearest of the stop's patches, and
 * extra seconds for anything that isn't walking (a boat, a quetzal, Elkoy, a ladder). Every method is measured (a
 * test checks). Below them, every other distance the route uses: walks between stops, spirit trees, Renu,
 * fairy rings and banks near a stop, and the seconds each kind of travel takes.
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
		// CIQ is north-west of Yanille: the walk round to the village is long. Sean: "SO slow"; his recorded leg
		// took 118 s (2026-10-10), so 40 s on top of the walk
		tiles(TravelMethod.FAIRY_RING_CIQ, 100);
		extra(TravelMethod.FAIRY_RING_CIQ, 40);
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

	// Everything else the route needs to know about distances, in one place (docs/REBUILD.md, Sprint 1)

	/** Running speed: 2 tiles a game tick. */
	public static final double SECONDS_PER_TILE = 0.3;
	/** Casting a standard teleport, rubbing jewellery, a fairy ring dial, a spirit tree, a quetzal: seconds. */
	public static final double TELEPORT = 3.0;
	public static final double ITEM_TELEPORT = 3.6;
	public static final double HOME_TELEPORT = 10.5;
	public static final double QUETZAL = 6.0;
	public static final double FAIRY_RING = 4.0;
	public static final double SPIRIT_TREE = 4.5;
	/** Walking out of the house to its portal, or into it from outside. */
	public static final double HOUSE_DOOR = 5.0;
	/** Walking from the house's arrival point to the nexus, jewellery box, fairy ring or spirit tree. */
	public static final double HOUSE_WALK = 2.0;
	/**
	 * Every leg but a walk between neighbouring stops: finishing the last patch, opening the menu or spellbook,
	 * the animation and the load. Sean's recorded legs (2026-10-10) ran 1-1.3x the estimates with it.
	 */
	public static final double LEG_OVERHEAD = 8;
	/** A walk between neighbouring stops still means finishing the last patch and setting off. */
	public static final double WALK_OVERHEAD = 3;

	/** Falador Park to Taverley over the climbing rocks north-west of Falador. */
	public static final int TAVERLEY_ROCKS_AGILITY = 66;
	private static final int TAVERLEY_ROCKS_TILES = 75;

	/** Walks between neighbouring stops, in tiles. */
	private static final Map<Location, Map<Location, Integer>> WALKS = new EnumMap<>(Location.class);
	/** Spirit trees close to a stop's patches, and the walk to them. */
	private static final Map<Location, Integer> SPIRIT_TREE_WALK = new EnumMap<>(Location.class);
	/** Spirit trees the player has to plant first. */
	private static final Map<Location, Unlock> PLANTED_SPIRIT_TREES = new EnumMap<>(Location.class);
	/** Varlamore stops with a quetzal landing pad: tiles from the patch back to the pad (for Renu). */
	private static final Map<Location, Integer> RENU_PAD_WALK = new EnumMap<>(Location.class);
	/** Tiles from a stop's patch to its nearest bank; stops without a practical bank are missing. */
	private static final Map<Location, Integer> BANK_TILES = new EnumMap<>(Location.class);
	/** For "finish near a bank": a stop without a bank nearby counts as this far from one. */
	public static final int NO_BANK_TILES = 70;

	static
	{
		walk(Location.FALADOR_PARK, Location.FALADOR_FARM, 75);
		// Round to Taverley's gate (wiki research: about 90-110); over the rocks with 66 Agility, 75
		walk(Location.FALADOR_PARK, Location.TAVERLEY, 100);

		SPIRIT_TREE_WALK.put(Location.GNOME_STRONGHOLD, 14);
		// The village tree is inside the maze: back in past Elkoy
		SPIRIT_TREE_WALK.put(Location.TREE_GNOME_VILLAGE, 60);
		SPIRIT_TREE_WALK.put(Location.FARMING_GUILD, 11);
		SPIRIT_TREE_WALK.put(Location.BRIMHAVEN, 38);
		SPIRIT_TREE_WALK.put(Location.FALADOR_FARM, 55);
		SPIRIT_TREE_WALK.put(Location.VARROCK, 60);
		PLANTED_SPIRIT_TREES.put(Location.FARMING_GUILD, Unlock.SPIRIT_TREE_FARMING_GUILD);
		PLANTED_SPIRIT_TREES.put(Location.BRIMHAVEN, Unlock.SPIRIT_TREE_BRIMHAVEN);
		PLANTED_SPIRIT_TREES.put(Location.FALADOR_FARM, Unlock.SPIRIT_TREE_PORT_SARIM);

		RENU_PAD_WALK.put(Location.CIVITAS_ILLA_FORTIS, 39);
		RENU_PAD_WALK.put(Location.KASTORI, 36);
		RENU_PAD_WALK.put(Location.NEMUS_RETREAT, 60);

		// Estimates from the map (UNVERIFIED unless noted); the old Walk sizes were 10 / 28 / 55
		BANK_TILES.put(Location.VARROCK, 55);
		BANK_TILES.put(Location.FALADOR_PARK, 28);
		// The Grand Tree bank from the tree patch
		BANK_TILES.put(Location.GNOME_STRONGHOLD, 10);
		// Bank chest on site
		BANK_TILES.put(Location.FARMING_GUILD, 10);
		// The bank buffalo on the east side of the camp
		BANK_TILES.put(Location.NEMUS_RETREAT, 25);
		BANK_TILES.put(Location.CATHERBY, 28);
		BANK_TILES.put(Location.LLETYA, 28);
		// Across town from the patch, through the gate (Sean: banking there is slow)
		BANK_TILES.put(Location.PORT_PHASMATYS, 95);
		// The Hunter Guild bank chest (open to anyone)
		BANK_TILES.put(Location.CIVITAS_ILLA_FORTIS, 48);
	}

	private TravelTimes()
	{
	}

	private static void walk(Location a, Location b, int tiles)
	{
		WALKS.computeIfAbsent(a, k -> new EnumMap<>(Location.class)).put(b, tiles);
		WALKS.computeIfAbsent(b, k -> new EnumMap<>(Location.class)).put(a, tiles);
	}

	/** Tiles to walk from one stop to another, or null if they aren't neighbours. */
	public static Integer walkTiles(Location from, Location to, int agility)
	{
		final Integer tiles = WALKS.getOrDefault(from, Collections.emptyMap()).get(to);
		final boolean parkAndTaverley = (from == Location.FALADOR_PARK && to == Location.TAVERLEY)
			|| (from == Location.TAVERLEY && to == Location.FALADOR_PARK);
		return tiles != null && parkAndTaverley && agility >= TAVERLEY_ROCKS_AGILITY ? Integer.valueOf(TAVERLEY_ROCKS_TILES) : tiles;
	}

	/** Tiles from a stop's patch to a nearby spirit tree, or null if there's none. */
	public static Integer spiritTreeTiles(Location from)
	{
		return SPIRIT_TREE_WALK.get(from);
	}

	/** The unlock a stop's spirit tree needs (a player-planted one), or null. */
	public static Unlock plantedSpiritTree(Location from)
	{
		return PLANTED_SPIRIT_TREES.get(from);
	}

	/** Tiles from a Varlamore stop's patch back to its quetzal landing pad, or null if it has none. */
	public static Integer renuPadTiles(Location from)
	{
		return RENU_PAD_WALK.get(from);
	}

	/** Tiles from a stop's patch to its fairy ring (the same walk as arriving by that ring), or null. */
	public static Integer fairyRingTiles(Location location)
	{
		Integer tiles = null;
		for (Map.Entry<TravelMethod, Integer> e : TILES.entrySet())
		{
			if (e.getKey().getKind() == TravelKind.FAIRY_RING && e.getKey().getDestination() == location)
			{
				tiles = tiles == null ? e.getValue() : Math.min(tiles, e.getValue());
			}
		}
		return tiles;
	}

	/** Tiles from a stop's patch to its nearest bank, or null if there's no practical bank nearby. */
	public static Integer bankTiles(Location location)
	{
		return BANK_TILES.get(location);
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
