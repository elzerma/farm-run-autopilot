package com.farmrunautopilot.data.travel.atlas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * The travel atlas (docs/plans/travel-rework.md): ways between every patch, bank, teleport landing and hop, worked out
 * by tools/atlas with Shortest Path's pathfinder (BSD-2, see THIRD_PARTY_NOTICES) and shipped as data. Read once from
 * the plugin's resources; any thread afterwards.
 */
@Slf4j
public final class TravelAtlas
{
	private static final String DIR = "/com/farmrunautopilot/atlas/";
	/** Transports that move the player somewhere else (a boat, a portal) rather than through a door or up a ladder. */
	public static final Set<String> RIDES = Set.of("SHIP", "BOAT", "TELEPORTATION_PORTAL", "TELEPORTATION_LEVER");
	/** A transport that moves the player further than this carries them (the guide's "moved" is over 10 tiles a tick). */
	public static final int CARRIED_TILES = 10;
	/**
	 * Transports a farm run can't take, by the start of their menu text: the Grand Exchange's Castle Wars recruiters,
	 * whose "Join Castle Wars" puts a player in a team's waiting room, and only with no cape and nothing but combat
	 * items. Shortest Path has them with no requirement, as its data can't say that (its PR #580). Every way through one
	 * is left out when the atlas is read; tools/atlas should leave them out at its next rebuild, so the place pairs that
	 * had only that way get the next best (docs/plans/travel-rework.md).
	 */
	static final List<String> CANT_TAKE = List.of("Join Castle Wars ");

	/** Where a way starts or ends. */
	public enum Kind
	{
		PATCH, BANK, LANDING, HUB
	}

	@Getter
	public static final class Place
	{
		private final int id;
		private final Kind kind;
		private final WorldPoint point;
		private final String stop;
		/** For a patch place, the patches there (Patch names and atlas-only ids); otherwise what lands or leaves here. */
		private final List<String> names;

		Place(int id, Kind kind, WorldPoint point, String stop, List<String> names)
		{
			this.id = id;
			this.kind = kind;
			this.point = point;
			this.stop = stop;
			this.names = names;
		}
	}

	/** One way from a place to another: walking tiles, transport ticks, its transports and what it needs. */
	@Getter
	public static final class Way
	{
		private final int from;
		private final int to;
		private final int tiles;
		private final int ticks;
		private final int[] steps;
		private final int[] needs;

		Way(int from, int to, int tiles, int ticks, int[] steps, int[] needs)
		{
			this.from = from;
			this.to = to;
			this.tiles = tiles;
			this.ticks = ticks;
			this.steps = steps;
			this.needs = needs;
		}
	}

	/** A teleport (from anywhere, or from the house) or a hop (from a hub): where it lands, its ticks, what it needs. */
	@Getter
	public static final class Jump
	{
		private final int id;
		private final String type;
		/** The hub it's taken from, or -1 for a teleport. */
		private final int from;
		private final int to;
		private final int ticks;
		private final boolean consumable;
		private final int[] needs;
		private final String name;
		private final String menu;

		Jump(int id, String type, int from, int to, int ticks, boolean consumable, int[] needs, String name, String menu)
		{
			this.id = id;
			this.type = type;
			this.from = from;
			this.to = to;
			this.ticks = ticks;
			this.consumable = consumable;
			this.needs = needs;
			this.name = name;
			this.menu = menu;
		}
	}

	/** A transport a way uses, for describing the way. */
	@Getter
	public static final class Step
	{
		private final String type;
		private final WorldPoint origin;
		private final WorldPoint destination;
		private final int ticks;
		private final String menu;
		private final String name;

		Step(String type, WorldPoint origin, WorldPoint destination, int ticks, String menu, String name)
		{
			this.type = type;
			this.origin = origin;
			this.destination = destination;
			this.ticks = ticks;
			this.menu = menu;
			this.name = name;
		}
	}

	/** Set once, when read; volatile so the client thread can check it without waiting on the read. */
	private static volatile TravelAtlas loaded;

	@Getter
	private final List<Place> places = new ArrayList<>();
	@Getter
	private final List<AtlasNeed> needs = new ArrayList<>();
	@Getter
	private final List<Step> steps = new ArrayList<>();
	@Getter
	private final List<Jump> teleports = new ArrayList<>();
	@Getter
	private final List<Jump> hops = new ArrayList<>();
	/** Ways by where they start, then where they end; each list quickest first. */
	private final Map<Integer, Map<Integer, List<Way>>> ways = new HashMap<>();
	/** Places with a way to each place. */
	private final Map<Integer, List<Integer>> into = new HashMap<>();
	/** Hops by where they land. */
	private final Map<Integer, List<Jump>> hopsInto = new HashMap<>();
	/** Patch places by patch name. */
	private final Map<String, Place> patchPlaces = new HashMap<>();
	/** Who hops and transports are used on, worked out once. */
	private Set<String> targets;

	private TravelAtlas()
	{
	}

	/** The atlas, read on first use (in the background, never on the client thread); empty if it's missing. */
	public static synchronized TravelAtlas get()
	{
		if (loaded == null)
		{
			loaded = load();
		}
		return loaded;
	}

	/** The atlas if it has been read already, else null; never waits, so the client thread never reads it. */
	public static TravelAtlas ifLoaded()
	{
		return loaded;
	}

	/** Whether there's anything in it. */
	public boolean isEmpty()
	{
		return places.isEmpty();
	}

	public Place place(int id)
	{
		return id >= 0 && id < places.size() ? places.get(id) : null;
	}

	/** The place of a patch (a Patch enum name), or null. */
	public Place patchPlace(String patch)
	{
		return patchPlaces.get(patch);
	}

	public AtlasNeed need(int id)
	{
		return needs.get(id);
	}

	public Step step(int id)
	{
		return steps.get(id);
	}

	/** The ways from one place to another, quickest first; empty if there are none. */
	public List<Way> ways(int from, int to)
	{
		return ways.getOrDefault(from, Collections.emptyMap()).getOrDefault(to, Collections.emptyList());
	}

	/** Every place with a way to this one. */
	public List<Integer> into(int to)
	{
		return into.getOrDefault(to, Collections.emptyList());
	}

	/** The hops that land at this place. */
	public List<Jump> hopsInto(int landing)
	{
		return hopsInto.getOrDefault(landing, Collections.emptyList());
	}

	/**
	 * Who or what a transport's menu option is used on, e.g. "Renu" from "Travel Renu 13350" or "Trader Crewmember" from
	 * "Charter Trader Crewmember 1330" (Shortest Path's menu text: the option, the target, its id); null if it has none.
	 */
	public static String target(String menu)
	{
		final String[] words = menu == null ? new String[0] : menu.trim().split(" ");
		if (words.length < 3)
		{
			return null;
		}
		return String.join(" ", Arrays.asList(words).subList(1, words.length - 1));
	}

	/**
	 * A step on a way that carries the player off rather than being walked through: a ship, a boat, a portal or a lever,
	 * or a transport that moves them more than {@link #CARRIED_TILES} tiles (an NPC who leads the way, a cart). Doors,
	 * stairs and shortcuts aren't.
	 */
	public static boolean carries(Step step)
	{
		return RIDES.contains(step.getType()) || step.getType().equals("TRANSPORT")
			&& step.getOrigin().getPlane() == step.getDestination().getPlane()
			&& step.getOrigin().distanceTo2D(step.getDestination()) > CARRIED_TILES;
	}

	/** Everyone a hop or a transport on a way is used on (Renu, a ship's crew, a guide), for spotting them nearby. */
	public synchronized Set<String> targets()
	{
		if (targets == null)
		{
			final Set<String> found = new HashSet<>();
			for (Jump hop : hops)
			{
				final String target = target(hop.getMenu());
				if (target != null)
				{
					found.add(target);
				}
			}
			for (Step step : steps)
			{
				final String target = target(step.getMenu());
				if (target != null)
				{
					found.add(target);
				}
			}
			targets = Collections.unmodifiableSet(found);
		}
		return targets;
	}

	private static TravelAtlas load()
	{
		final TravelAtlas atlas = new TravelAtlas();
		try
		{
			read("places.tsv", r -> atlas.places.add(new Place(Integer.parseInt(r[0]), Kind.valueOf(r[1]),
				new WorldPoint(Integer.parseInt(r[2]), Integer.parseInt(r[3]), Integer.parseInt(r[4])), r[5],
				r.length > 6 && !r[6].isEmpty() ? List.of(r[6].split(" \\| ")) : List.of())));
			read("requirements.tsv", r -> atlas.needs.add(AtlasNeed.parse(r[1], r[2], r.length > 3 ? r[3] : "")));
			read("transports.tsv", r -> atlas.steps.add(new Step(r[1], point(r[2]), point(r[3]), Integer.parseInt(r[4]),
				r.length > 7 ? r[7] : "", r.length > 8 ? r[8] : "")));
			final Set<Integer> cantTake = cantTake(atlas.steps);
			read("teleports.tsv", r -> atlas.teleports.add(new Jump(Integer.parseInt(r[0]), r[1], -1, Integer.parseInt(r[2]),
				Integer.parseInt(r[3]), Boolean.parseBoolean(r[4]), ids(r[6]), r.length > 7 ? r[7] : "", r.length > 8 ? r[8] : "")));
			read("hops.tsv", r ->
			{
				final Jump hop = new Jump(Integer.parseInt(r[0]), r[1], Integer.parseInt(r[2]), Integer.parseInt(r[3]),
					Integer.parseInt(r[4]), false, ids(r[5]), r.length > 6 ? r[6] : "", r.length > 7 ? r[7] : "");
				atlas.hops.add(hop);
				atlas.hopsInto.computeIfAbsent(hop.to, k -> new ArrayList<>()).add(hop);
			});
			read("ways.tsv", r ->
			{
				final Way way = new Way(Integer.parseInt(r[0]), Integer.parseInt(r[1]), Integer.parseInt(r[2]),
					Integer.parseInt(r[3]), ids(r[4]), ids(r.length > 5 ? r[5] : ""));
				for (int step : way.steps)
				{
					if (cantTake.contains(step))
					{
						return;
					}
				}
				final List<Way> list = atlas.ways.computeIfAbsent(way.from, k -> new HashMap<>())
					.computeIfAbsent(way.to, k -> new ArrayList<>());
				if (list.isEmpty())
				{
					atlas.into.computeIfAbsent(way.to, k -> new ArrayList<>()).add(way.from);
				}
				list.add(way);
			});
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("The travel atlas couldn't be read; no route can be planned without it", e);
			return new TravelAtlas();
		}
		for (Place p : atlas.places)
		{
			if (p.kind == Kind.PATCH)
			{
				for (String name : p.names)
				{
					atlas.patchPlaces.put(name, p);
				}
			}
		}
		log.debug("Travel atlas: {} places, {} teleports, {} hops", atlas.places.size(), atlas.teleports.size(),
			atlas.hops.size());
		return atlas;
	}

	/** The transports (by id) a farm run can't take: see {@link #CANT_TAKE}. */
	private static Set<Integer> cantTake(List<Step> steps)
	{
		final Set<Integer> ids = new HashSet<>();
		for (int i = 0; i < steps.size(); i++)
		{
			for (String menu : CANT_TAKE)
			{
				if (steps.get(i).getMenu().startsWith(menu))
				{
					ids.add(i);
				}
			}
		}
		return ids;
	}

	/** Each row of an atlas file, without comments and the header. */
	private static void read(String file, Consumer<String[]> row) throws IOException
	{
		try (InputStream in = TravelAtlas.class.getResourceAsStream(DIR + file))
		{
			if (in == null)
			{
				throw new IOException("Missing " + DIR + file);
			}
			final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			boolean header = true;
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith("#") || line.isEmpty())
				{
					continue;
				}
				if (header)
				{
					header = false;
					continue;
				}
				row.accept(line.split("\t", -1));
			}
		}
	}

	private static int[] ids(String list)
	{
		if (list == null || list.isEmpty())
		{
			return new int[0];
		}
		final String[] parts = list.split(",");
		final int[] ids = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			ids[i] = Integer.parseInt(parts[i]);
		}
		return ids;
	}

	private static WorldPoint point(String text)
	{
		final String[] xyz = text.split(" ");
		return new WorldPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
	}
}
