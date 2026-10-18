package com.farmrunautopilot.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.ShortestPathPlugin;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Where the atlas searches from and to: every patch (by tile), every bank near a patch, and every place a teleport or a
 * hop (fairy ring, spirit tree, quetzal, the house's portals and jewellery box) lands.
 */
final class Places
{
	enum Kind
	{
		/** A patch (or patches close together). */
		PATCH,
		/** A bank near a patch. */
		BANK,
		/** Where a teleport or a hop lands. */
		LANDING,
		/** Where a hop is taken: a fairy ring, spirit tree, quetzal pad, glider, charter ship, carpet... */
		HUB
	}

	/**
	 * Transport networks the plugin hops itself, like a teleport: the atlas's ways don't use them; instead each hop's
	 * start is a place ways go to, and where it lands a place ways start from.
	 */
	static final Set<TransportType> HUB_TYPES = java.util.EnumSet.of(TransportType.FAIRY_RING, TransportType.SPIRIT_TREE,
		TransportType.QUETZAL, TransportType.GNOME_GLIDER, TransportType.CHARTER_SHIP, TransportType.MAGIC_CARPET,
		TransportType.MAGIC_MUSHTREE, TransportType.CANOE, TransportType.MINECART, TransportType.HOT_AIR_BALLOON,
		TransportType.WILDERNESS_OBELISK);

	/** One row of tools/atlas/patches.tsv. */
	static final class Patch
	{
		final String id;
		final String type;
		final String stop;
		final int tile;
		final boolean plugin;

		Patch(String id, String type, String stop, int tile, boolean plugin)
		{
			this.id = id;
			this.type = type;
			this.stop = stop;
			this.tile = tile;
			this.plugin = plugin;
		}
	}

	static final class Place
	{
		final Kind kind;
		/** The object or landing tile. */
		final int tile;
		/** A tile a player stands on to set off from here. */
		final int start;
		/** Tiles a player can stand on to use this place (patches and banks). */
		final Set<Integer> standing;
		final String stop;
		final List<String> names = new ArrayList<>();
		int id;

		Place(Kind kind, int tile, int start, Set<Integer> standing, String stop)
		{
			this.kind = kind;
			this.tile = tile;
			this.start = start;
			this.standing = standing;
			this.stop = stop;
		}

		String label()
		{
			return kind + " " + (names.isEmpty() ? Tsv.text(tile) : names.get(0));
		}
	}

	/** Banks further than this from every patch aren't needed. */
	private static final int BANK_RANGE = 120;
	/** Patches of one type at one stop this close share a place. */
	private static final int SAME_SPOT = 8;
	/** Landings this close are one place. */
	private static final int SAME_LANDING = 2;

	final List<Place> all = new ArrayList<>();
	final List<Patch> patches = new ArrayList<>();
	final Map<String, Place> placeOfPatch = new HashMap<>();
	/** Teleports usable from anywhere, and the house's exits, each with where it lands. */
	final Map<Transport, Place> teleports = new LinkedHashMap<>();
	/** Every hop of the networks in {@link #HUB_TYPES}. */
	final List<Hop> hops = new ArrayList<>();

	private Places()
	{
	}

	static Places build(BubbleConfig.World world, CollisionMap map, Tsv patchFile)
	{
		final Places places = new Places();
		final int id = patchFile.col("patch");
		final int type = patchFile.col("type");
		final int stop = patchFile.col("stop");
		final int x = patchFile.col("x");
		final int y = patchFile.col("y");
		final int plane = patchFile.col("plane");
		final int runs = patchFile.col("runs");
		final List<Place> patchPlaces = new ArrayList<>();
		final Map<Place, Set<String>> placeTypes = new HashMap<>();
		for (String[] r : patchFile.rows)
		{
			final int tile = WorldPointUtil.packWorldPoint(Integer.parseInt(r[x]), Integer.parseInt(r[y]),
				Integer.parseInt(r[plane]));
			final Patch patch = new Patch(r[id], r[type], r[stop], tile, r[runs].equals("plugin"));
			places.patches.add(patch);
			// One place for patches on the same tile, or of one type at one stop close together (an allotment pair,
			// the vinery's grapes, the seaweed pair): the run does them together
			Place place = null;
			for (Place p : patchPlaces)
			{
				if (p.tile == tile || p.stop.equals(patch.stop) && placeTypes.get(p).contains(patch.type)
					&& distance(p.tile, tile) <= SAME_SPOT)
				{
					place = p;
					break;
				}
			}
			final Set<Integer> standing = standingTiles(map, List.of(unpack(tile)));
			if (place == null)
			{
				place = new Place(Kind.PATCH, tile, standing.isEmpty() ? walkableNear(map, unpack(tile)) : first(standing),
					standing, patch.stop);
				patchPlaces.add(place);
				placeTypes.put(place, new LinkedHashSet<>());
			}
			else
			{
				place.standing.addAll(standing);
			}
			placeTypes.get(place).add(patch.type);
			place.names.add(patch.id);
			places.placeOfPatch.put(patch.id, place);
		}
		places.all.addAll(patchPlaces);

		final Set<Integer> bankTiles = new LinkedHashSet<>();
		for (Map.Entry<String, Set<Integer>> e : world.destinations.entrySet())
		{
			if (e.getKey().toLowerCase().contains("bank"))
			{
				bankTiles.addAll(e.getValue());
			}
		}
		for (int tile : bankTiles)
		{
			final Patch near = nearestPatch(places.patches, tile);
			if (near == null || distance(near.tile, tile) > BANK_RANGE)
			{
				continue;
			}
			final Set<Integer> standing = around(map, tile, 1);
			if (standing.isEmpty())
			{
				continue;
			}
			final Place bank = new Place(Kind.BANK, tile, walkableNear(map, unpack(tile)), standing, near.stop);
			bank.names.add("bank " + Tsv.text(tile));
			places.all.add(bank);
		}

		// Landings within 2 tiles of each other count as one (a jewellery box and the item, a ring and its tablet)
		final Map<Integer, Place> landings = new LinkedHashMap<>();
		for (Set<Transport> set : world.transports.values())
		{
			for (Transport t : set)
			{
				if (!isHop(t) || insidePoh(t.getDestination()))
				{
					continue;
				}
				Place landing = landings.get(t.getDestination());
				if (landing == null)
				{
					for (Place l : landings.values())
					{
						if (distance(l.tile, t.getDestination()) <= SAME_LANDING)
						{
							landing = l;
							break;
						}
					}
				}
				if (landing == null)
				{
					landing = new Place(Kind.LANDING, t.getDestination(), walkableNear(map, unpack(t.getDestination())),
						Set.of(), "");
				}
				landings.put(t.getDestination(), landing);
				final String name = Way.name(t);
				if (!landing.names.contains(name))
				{
					landing.names.add(name);
				}
				if (isTeleport(t))
				{
					places.teleports.put(t, landing);
				}
			}
		}
		places.all.addAll(new LinkedHashSet<>(landings.values()));

		// Where each hop is taken (merged within 2 tiles, as landings), and the hops themselves
		final Map<Integer, Place> hubs = new LinkedHashMap<>();
		for (Set<Transport> set : world.transports.values())
		{
			for (Transport t : set)
			{
				if (!HUB_TYPES.contains(t.getType()) || insidePoh(t.getOrigin()) || insidePoh(t.getDestination())
					|| t.getOrigin() == Transport.UNDEFINED_ORIGIN)
				{
					continue;
				}
				Place hub = hubs.get(t.getOrigin());
				if (hub == null)
				{
					for (Place h : hubs.values())
					{
						if (distance(h.tile, t.getOrigin()) <= SAME_LANDING)
						{
							hub = h;
							break;
						}
					}
				}
				if (hub == null)
				{
					Set<Integer> standing = around(map, t.getOrigin(), 1);
					if (standing.isEmpty())
					{
						standing = around(map, t.getOrigin(), 2);
					}
					if (standing.isEmpty())
					{
						continue;
					}
					hub = new Place(Kind.HUB, t.getOrigin(), walkableNear(map, unpack(t.getOrigin())), standing, "");
					hub.names.add(t.getType().name().toLowerCase().replace('_', ' ') + " " + Tsv.text(t.getOrigin()));
				}
				hubs.put(t.getOrigin(), hub);
				places.hops.add(new Hop(t, hub, landings.get(t.getDestination())));
			}
		}
		places.all.addAll(new LinkedHashSet<>(hubs.values()));
		for (int i = 0; i < places.all.size(); i++)
		{
			places.all.get(i).id = i;
		}
		return places;
	}

	/** One hop: taken at a hub, landing at a landing. */
	static final class Hop
	{
		final Transport transport;
		final Place from;
		final Place to;

		Hop(Transport transport, Place from, Place to)
		{
			this.transport = transport;
			this.from = from;
			this.to = to;
		}
	}

	/** Used from anywhere, or from inside the house (its portals, nexus and jewellery box). */
	static boolean isTeleport(Transport t)
	{
		final TransportType type = t.getType();
		if (type == TransportType.SEASONAL_TRANSPORTS)
		{
			return false;
		}
		return t.getOrigin() == Transport.UNDEFINED_ORIGIN || type == TransportType.TELEPORTATION_PORTAL_POH
			|| type == TransportType.TELEPORTATION_BOX;
	}

	/** A teleport, or a hop the plugin takes itself (see {@link #HUB_TYPES}). */
	private static boolean isHop(Transport t)
	{
		return isTeleport(t) || HUB_TYPES.contains(t.getType());
	}

	static boolean insidePoh(int packed)
	{
		return packed != Transport.UNDEFINED_ORIGIN
			&& ShortestPathPlugin.isInsidePoh(WorldPointUtil.unpackWorldX(packed), WorldPointUtil.unpackWorldY(packed));
	}

	/** Patch object tiles by stop and by patch name (tools/atlas/patches.tsv or tools/walk/patches.tsv). */
	static Map<String, List<int[]>> patchTilesByStopAndName(Tsv file)
	{
		final int patch = file.col("patch");
		final int stop = file.col("stop", "location");
		final int x = file.col("x");
		final int y = file.col("y");
		final int plane = file.col("plane");
		final Map<String, List<int[]>> tiles = new HashMap<>();
		for (String[] r : file.rows)
		{
			final int[] p = {Integer.parseInt(r[x]), Integer.parseInt(r[y]), Integer.parseInt(r[plane])};
			tiles.computeIfAbsent(r[stop], k -> new ArrayList<>()).add(p);
			tiles.computeIfAbsent(r[patch], k -> new ArrayList<>()).add(p);
		}
		return tiles;
	}

	/** The tiles a player can stand on to use these patches: around each patch object. */
	static Set<Integer> standingTiles(CollisionMap map, List<int[]> patches)
	{
		final Set<Integer> tiles = new LinkedHashSet<>();
		for (int[] p : patches)
		{
			for (int dx = -1; dx <= 3; dx++)
			{
				for (int dy = -1; dy <= 3; dy++)
				{
					if (!map.isBlocked(p[0] + dx, p[1] + dy, p[2]))
					{
						tiles.add(WorldPointUtil.packWorldPoint(p[0] + dx, p[1] + dy, p[2]));
					}
				}
			}
		}
		return tiles;
	}

	/** Walkable tiles within {@code r} of a tile. */
	static Set<Integer> around(CollisionMap map, int packed, int r)
	{
		final int[] p = unpack(packed);
		final Set<Integer> tiles = new LinkedHashSet<>();
		for (int dx = -r; dx <= r; dx++)
		{
			for (int dy = -r; dy <= r; dy++)
			{
				if (!map.isBlocked(p[0] + dx, p[1] + dy, p[2]))
				{
					tiles.add(WorldPointUtil.packWorldPoint(p[0] + dx, p[1] + dy, p[2]));
				}
			}
		}
		return tiles;
	}

	/** The nearest tile a player can stand on, for starts given as an object's tile. */
	static int walkableNear(CollisionMap map, int[] p)
	{
		for (int r = 0; r <= 4; r++)
		{
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) == r && !map.isBlocked(p[0] + dx, p[1] + dy, p[2]))
					{
						return WorldPointUtil.packWorldPoint(p[0] + dx, p[1] + dy, p[2]);
					}
				}
			}
		}
		return WorldPointUtil.packWorldPoint(p[0], p[1], p[2]);
	}

	static int[] unpack(int packed)
	{
		return new int[]{WorldPointUtil.unpackWorldX(packed), WorldPointUtil.unpackWorldY(packed),
			WorldPointUtil.unpackWorldPlane(packed)};
	}

	private static int first(Set<Integer> tiles)
	{
		return tiles.iterator().next();
	}

	/** Straight-line tiles on one plane; far apart if on different planes. */
	static int distance(int a, int b)
	{
		if (WorldPointUtil.unpackWorldPlane(a) != WorldPointUtil.unpackWorldPlane(b))
		{
			return Integer.MAX_VALUE;
		}
		return Math.max(Math.abs(WorldPointUtil.unpackWorldX(a) - WorldPointUtil.unpackWorldX(b)),
			Math.abs(WorldPointUtil.unpackWorldY(a) - WorldPointUtil.unpackWorldY(b)));
	}

	private static Patch nearestPatch(List<Patch> patches, int tile)
	{
		Patch best = null;
		for (Patch p : patches)
		{
			if (best == null || distance(p.tile, tile) < distance(best.tile, tile))
			{
				best = p;
			}
		}
		return best;
	}
}
