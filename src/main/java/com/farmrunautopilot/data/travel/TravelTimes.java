package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Unlock;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

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

	// Walking tiles from where each method lands to the nearest patch of its stop, measured on Shortest Path's
	// collision map (commit 7e81a40, BSD-2, see THIRD_PARTY_NOTICES) with its landing tiles, 2026-10-13. A few the map
	// can't measure keep their estimates, marked "estimate". Journeys add up the walks before and after the ride.
	static
	{
		// Lumbridge
		tiles(TravelMethod.LUMBRIDGE_TELEPORT, 45);
		tiles(TravelMethod.LUMBRIDGE_HOME_TELEPORT, 45);
		tiles(TravelMethod.DIARY_CAPE_LUMBRIDGE, 51);
		// Varrock
		tiles(TravelMethod.VARROCK_TELEPORT, 37);
		tiles(TravelMethod.DIARY_CAPE_VARROCK, 46);
		tiles(TravelMethod.SKILLS_NECKLACE_COOKS_GUILD, 93);
		tiles(TravelMethod.RING_OF_WEALTH_GRAND_EXCHANGE, 82);
		tiles(TravelMethod.SPIRIT_TREE_GRAND_EXCHANGE, 82);
		// Falador Park
		tiles(TravelMethod.RING_OF_WEALTH_FALADOR_PARK, 7);
		tiles(TravelMethod.FALADOR_TELEPORT_TO_PARK, 39);
		tiles(TravelMethod.SKILLS_NECKLACE_MINING_GUILD, 101);
		extra(TravelMethod.SKILLS_NECKLACE_MINING_GUILD, 3);
		// Taverley
		tiles(TravelMethod.HOUSE_PORTAL_TAVERLEY, 47);
		tiles(TravelMethod.TAVERLEY_TABLET, 47);
		tiles(TravelMethod.CONSTRUCTION_CAPE_TAVERLEY, 48);
		// 70 over the 66 Agility rocks, about 100 round by the gate
		tiles(TravelMethod.FALADOR_TELEPORT_TO_TAVERLEY, 89);
		tiles(TravelMethod.HOT_AIR_BALLOON_TAVERLEY, 16);
		tiles(TravelMethod.GAMES_NECKLACE_BURTHORPE, 117);
		tiles(TravelMethod.COMBAT_BRACELET_WARRIORS_GUILD, 115);
		// Gnome Stronghold (nearest patch: tree by the Slayer Cave, fruit tree by the spirit tree)
		tiles(TravelMethod.SLAYER_RING_STRONGHOLD_CAVE, 5);
		tiles(TravelMethod.SPIRIT_TREE_GNOME_STRONGHOLD, 12);
		tiles(TravelMethod.ROYAL_SEED_POD, 50); // estimate: the Grand Tree's door isn't on the map
		// Farming Guild
		tiles(TravelMethod.FARMING_CAPE, 8);
		tiles(TravelMethod.SKILLS_NECKLACE_FARMING_GUILD, 8);
		tiles(TravelMethod.MAX_CAPE_FARMING_GUILD, 8);
		tiles(TravelMethod.SPIRIT_TREE_FARMING_GUILD, 7);
		tiles(TravelMethod.FAIRY_RING_CIR, 82);
		tiles(TravelMethod.BATTLEFRONT_TELEPORT, 121);
		tiles(TravelMethod.RADAS_BLESSING_MOUNT_KARUULM, 100); // estimate: the stairs down Mount Karuulm aren't on the map
		// Nemus Retreat
		tiles(TravelMethod.PENDANT_OF_ATES_NEMUS_RETREAT, 54);
		// Pad to the 33 Agility tunnel, then a step to the patch; about 100 round without it
		tiles(TravelMethod.QUETZAL_AUBURNVALE, 71);
		tiles(TravelMethod.FAIRY_RING_AIS, 65);
		// Tree Gnome Village: out of the maze behind Elkoy, then a short walk
		tiles(TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, 15);
		extra(TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, 12);
		// CIQ is north-west of Yanille: the walk round to the village is long. elzerma: "SO slow"; their recorded leg
		// took 118 s (2026-10-10), so 47 s on top of the measured walk
		tiles(TravelMethod.FAIRY_RING_CIQ, 77);
		extra(TravelMethod.FAIRY_RING_CIQ, 47);
		// Catherby (nearest patch: the herb patch from Camelot)
		tiles(TravelMethod.CAMELOT_TELEPORT, 57);
		tiles(TravelMethod.CATHERBY_TELEPORT, 19);
		// Brimhaven
		tiles(TravelMethod.HOUSE_PORTAL_BRIMHAVEN, 32);
		tiles(TravelMethod.BRIMHAVEN_TABLET, 31);
		tiles(TravelMethod.CONSTRUCTION_CAPE_BRIMHAVEN, 34);
		tiles(TravelMethod.SPIRIT_TREE_BRIMHAVEN, 33);
		tiles(TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT, 62);
		extra(TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT, 12);
		tiles(TravelMethod.GLORY_KARAMJA, 164);
		// Lletya
		tiles(TravelMethod.TELEPORT_CRYSTAL_LLETYA, 15);
		// Kastori
		tiles(TravelMethod.QUETZAL_KASTORI, 32);
		tiles(TravelMethod.PENDANT_OF_ATES_NORTH_KASTORI, 33);
		// Falador farm
		tiles(TravelMethod.EXPLORERS_RING_CABBAGE_PATCH, 19);
		tiles(TravelMethod.DRAYNOR_MANOR_TELEPORT, 75);
		tiles(TravelMethod.SPIRIT_TREE_PORT_SARIM, 54);
		tiles(TravelMethod.GLORY_DRAYNOR, 95);
		tiles(TravelMethod.FALADOR_TELEPORT_TO_FARM, 127);
		// Port Phasmatys
		tiles(TravelMethod.FAIRY_RING_ALQ, 41);
		tiles(TravelMethod.ECTOPHIAL, 57);
		tiles(TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT, 55);
		tiles(TravelMethod.KHARYRLL_TELEPORT, 134);
		// Ardougne farm
		tiles(TravelMethod.ARDOUGNE_CLOAK_FARM, 3);
		tiles(TravelMethod.SKILLS_NECKLACE_FISHING_GUILD, 60);
		tiles(TravelMethod.MAX_CAPE_FISHING_GUILD, 73);
		tiles(TravelMethod.FISHING_GUILD_TELEPORT, 58);
		tiles(TravelMethod.COMBAT_BRACELET_RANGING_GUILD, 69);
		tiles(TravelMethod.FAIRY_RING_BLR, 71);
		tiles(TravelMethod.ARDOUGNE_TELEPORT, 95);
		// Hosidius
		tiles(TravelMethod.XERICS_TALISMAN_GLADE, 21);
		tiles(TravelMethod.HOUSE_PORTAL_HOSIDIUS, 31);
		tiles(TravelMethod.HOSIDIUS_TABLET, 31);
		tiles(TravelMethod.CONSTRUCTION_CAPE_HOSIDIUS, 31);
		tiles(TravelMethod.KHAREDSTS_MEMOIRS_LANCALLIUMS, 72);
		tiles(TravelMethod.FAIRY_RING_AKR, 87);
		// 67 tiles to the 45 Agility stepping stone just west of the farm (1720, 3551 in Shortest Path's shortcuts), 2 ticks
		// across, then 12 to the patch; 121 without it. Was an estimate of 76 until measured (elzerma, 2026-10-17)
		tiles(TravelMethod.SKILLS_NECKLACE_WOODCUTTING_GUILD, 79);
		extra(TravelMethod.SKILLS_NECKLACE_WOODCUTTING_GUILD, 1.2);
		// Troll Stronghold: the roof is a few steps from the patch; Trollheim is the long walk down the mountain
		tiles(TravelMethod.STONY_BASALT_ROOF, 7);
		tiles(TravelMethod.STONY_BASALT_ENTRANCE, 35); // estimate: in and up through the stronghold
		tiles(TravelMethod.TROLLHEIM_TELEPORT, 174);
		extra(TravelMethod.TROLLHEIM_TELEPORT, 10);
		// Harmony Island, Weiss
		tiles(TravelMethod.HARMONY_ISLAND_TELEPORT, 25);
		tiles(TravelMethod.ICY_BASALT, 3);
		// Civitas illa Fortis / Ortus Farm
		tiles(TravelMethod.QUETZAL_HUNTER_GUILD, 46);
		tiles(TravelMethod.HUNTER_CAPE, 73);
		tiles(TravelMethod.MAX_CAPE_HUNTER_GUILD, 73);
		tiles(TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT, 60);
		extra(TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT, 8);
		tiles(TravelMethod.FAIRY_RING_AJP, 91);
		// Allotment and hops stops (Sprint 1a), measured the same way on Shortest Path c7a4937 (2026-10-17) to the
		// wiki's map point for each patch (not yet logged in game)
		tiles(TravelMethod.TELEPORT_CRYSTAL_PRIFDDINAS, 59);
		tiles(TravelMethod.HOUSE_PORTAL_PRIFDDINAS, 75);
		tiles(TravelMethod.LUMBRIDGE_TELEPORT_HOPS, 119);
		tiles(TravelMethod.VARROCK_TELEPORT_HOPS, 109);
		tiles(TravelMethod.COMBAT_BRACELET_CHAMPIONS_GUILD, 56);
		tiles(TravelMethod.CAMELOT_TELEPORT_SEERS_HOPS, 91);
		tiles(TravelMethod.FAIRY_RING_ALS, 82);
		tiles(TravelMethod.COMBAT_BRACELET_RANGING_GUILD_HOPS, 98);
		tiles(TravelMethod.HOUSE_PORTAL_YANILLE, 33);
		tiles(TravelMethod.FAIRY_RING_CIQ_HOPS, 82);
		tiles(TravelMethod.FAIRY_RING_CKQ, 4);
		tiles(TravelMethod.QUETZAL_ALDARIN, 43);
		tiles(TravelMethod.HOUSE_PORTAL_ALDARIN, 61);
	}

	// Everything else the route needs to know about distances, in one place (docs/REBUILD.md, Sprint 1)

	/** Running speed: 2 tiles a game tick. */
	public static final double SECONDS_PER_TILE = 0.3;
	/** Casting a standard teleport, rubbing jewellery, a fairy ring dial, a spirit tree, a quetzal: seconds. */
	public static final double TELEPORT = 3.0;
	public static final double ITEM_TELEPORT = 3.6;
	public static final double HOME_TELEPORT = 10.5;
	/** Renu's flight from a landing pad (no whistle charge). */
	public static final double QUETZAL = 6.0;
	/**
	 * Blowing the quetzal whistle, waiting for the quetzal and flying: slower than a teleport. elzerma's recorded whistle
	 * leg to the Hunter Guild (2026-10-11) had about 21 s on top of the walk, overhead 8 included.
	 */
	public static final double QUETZAL_WHISTLE = 13.0;
	public static final double FAIRY_RING = 4.0;
	public static final double SPIRIT_TREE = 4.5;
	/** A hot air balloon flight: the basket, the log and the map (Shortest Path: 5 ticks to fly). */
	public static final double BALLOON = 4.5;
	/** Walking out of the house to its portal, or into it from outside. */
	public static final double HOUSE_DOOR = 5.0;
	/** Walking from the house's arrival point to the nexus, jewellery box, fairy ring or spirit tree. */
	public static final double HOUSE_WALK = 2.0;
	/**
	 * Every leg but a walk between neighbouring stops: finishing the last patch, opening the menu or spellbook,
	 * the animation and the load. elzerma's recorded legs (2026-10-10) ran 1-1.3x the estimates with it.
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
	/** Tiles from a stop's nearest patch to a hot air balloon basket (Enlightened Journey). */
	private static final Map<Location, Integer> BALLOON_WALK = new EnumMap<>(Location.class);
	/** Tiles from a stop's patch to its nearest bank; stops without a practical bank are missing. */
	private static final Map<Location, Integer> BANK_TILES = new EnumMap<>(Location.class);
	/** Where each stop's nearest bank is, for the walking line to it (Shortest Path's bank tiles). */
	private static final Map<Location, WorldPoint> BANK_POINTS = new EnumMap<>(Location.class);
	/** For "finish near a bank": a stop without a bank nearby counts as this far from one. */
	public static final int NO_BANK_TILES = 70;

	static
	{
		// Measured on Shortest Path's collision map (2026-10-13), patch to patch; over Taverley's rocks with 66
		// Agility it's 75
		walk(Location.FALADOR_PARK, Location.FALADOR_FARM, 108);
		walk(Location.FALADOR_PARK, Location.TAVERLEY, 112);
		walk(Location.LUMBRIDGE, Location.LUMBRIDGE_HOPS, 108);

		// From the stop's nearest patch to the tree, measured (Shortest Path's map and tree tiles)
		SPIRIT_TREE_WALK.put(Location.GNOME_STRONGHOLD, 11);
		// The village tree is inside the maze: back in past Elkoy (estimate; the maze walk isn't the way in)
		SPIRIT_TREE_WALK.put(Location.TREE_GNOME_VILLAGE, 60);
		SPIRIT_TREE_WALK.put(Location.FARMING_GUILD, 6);
		SPIRIT_TREE_WALK.put(Location.BRIMHAVEN, 32);
		SPIRIT_TREE_WALK.put(Location.FALADOR_FARM, 53);
		SPIRIT_TREE_WALK.put(Location.VARROCK, 81);
		PLANTED_SPIRIT_TREES.put(Location.FARMING_GUILD, Unlock.SPIRIT_TREE_FARMING_GUILD);
		PLANTED_SPIRIT_TREES.put(Location.BRIMHAVEN, Unlock.SPIRIT_TREE_BRIMHAVEN);
		PLANTED_SPIRIT_TREES.put(Location.FALADOR_FARM, Unlock.SPIRIT_TREE_PORT_SARIM);

		// The Grand Tree's basket, measured from the Stronghold fruit tree
		BALLOON_WALK.put(Location.GNOME_STRONGHOLD, 6);

		// From the patch back to the quetzal pad, measured; Nemus Retreat through the 33 Agility tunnel
		RENU_PAD_WALK.put(Location.CIVITAS_ILLA_FORTIS, 45);
		RENU_PAD_WALK.put(Location.KASTORI, 31);
		RENU_PAD_WALK.put(Location.NEMUS_RETREAT, 71);
		RENU_PAD_WALK.put(Location.ALDARIN_HOPS, 42);

		// From the stop's patch furthest from the bank (the one most likely done last, elzerma) to the nearest bank,
		// measured on Shortest Path's map with its bank tiles. Catherby: herb 38, fruit tree 54. Farming Guild:
		// fruit tree 2, herb 32, tree 38.
		BANK_TILES.put(Location.VARROCK, 49);
		BANK_TILES.put(Location.FALADOR_PARK, 34);
		// The Tree Gnome Stronghold bank by the tree patch, upstairs (estimate: the ladder isn't on the map)
		BANK_TILES.put(Location.GNOME_STRONGHOLD, 10);
		// Bank chest by the fruit tree
		BANK_TILES.put(Location.FARMING_GUILD, 38);
		// The bank buffalo on the east side of the camp
		BANK_TILES.put(Location.NEMUS_RETREAT, 25);
		BANK_TILES.put(Location.CATHERBY, 54);
		BANK_TILES.put(Location.LLETYA, 33);
		// Across town from the patch, through the gate (estimate; elzerma: banking there is slow)
		BANK_TILES.put(Location.PORT_PHASMATYS, 95);
		// The Hunter Guild bank (open to anyone)
		BANK_TILES.put(Location.CIVITAS_ILLA_FORTIS, 89);
		BANK_TILES.put(Location.SEERS_VILLAGE_HOPS, 66);
		BANK_TILES.put(Location.YANILLE_HOPS, 33);
		BANK_TILES.put(Location.ALDARIN_HOPS, 32);
		BANK_TILES.put(Location.PRIFDDINAS, 42);

		// Bank booths and chests, from Shortest Path's bank list
		BANK_POINTS.put(Location.CATHERBY, new WorldPoint(2807, 3441, 0));
		BANK_POINTS.put(Location.GNOME_STRONGHOLD, new WorldPoint(2445, 3424, 1));
		BANK_POINTS.put(Location.FALADOR_PARK, new WorldPoint(3010, 3355, 0));
		BANK_POINTS.put(Location.VARROCK, new WorldPoint(3251, 3420, 0));
		BANK_POINTS.put(Location.LLETYA, new WorldPoint(2352, 3162, 0));
		BANK_POINTS.put(Location.PORT_PHASMATYS, new WorldPoint(3687, 3466, 0));
		BANK_POINTS.put(Location.FARMING_GUILD, new WorldPoint(1248, 3758, 0));
		BANK_POINTS.put(Location.NEMUS_RETREAT, new WorldPoint(1387, 3309, 0));
		BANK_POINTS.put(Location.CIVITAS_ILLA_FORTIS, new WorldPoint(1542, 3040, 0));
		BANK_POINTS.put(Location.SEERS_VILLAGE_HOPS, new WorldPoint(2724, 3493, 0));
		BANK_POINTS.put(Location.YANILLE_HOPS, new WorldPoint(2613, 3092, 0));
		BANK_POINTS.put(Location.ALDARIN_HOPS, new WorldPoint(1399, 2927, 0));
		BANK_POINTS.put(Location.PRIFDDINAS, new WorldPoint(3256, 6106, 0));
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

	/** Tiles from a stop's patch to a hot air balloon basket, or null if there's none by it. */
	public static Integer balloonTiles(Location from)
	{
		return BALLOON_WALK.get(from);
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

	/** Where a stop's nearest bank is, or null if not known. */
	public static WorldPoint bankPoint(Location location)
	{
		return BANK_POINTS.get(location);
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
