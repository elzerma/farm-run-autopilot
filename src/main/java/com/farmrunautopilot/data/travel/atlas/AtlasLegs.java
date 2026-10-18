package com.farmrunautopilot.data.travel.atlas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Legs from the atlas for one player (docs/plans/travel-rework.md, section 4): the quickest of the atlas's way from
 * here, a teleport and the way from where it lands, or a hop (a fairy ring, spirit tree, quetzal...) taken on foot or
 * after a teleport, then the way from where it lands. Only ways, teleports and hops whose needs the player meets.
 * Times are walking tiles x 0.3 s and ticks x 0.6 s, before the leg's set-off overhead.
 */
public final class AtlasLegs
{
	private static final double SECONDS_PER_TILE = 0.3;
	private static final double SECONDS_PER_TICK = 0.6;
	/** Teleports with a cooldown (minigame and home teleports) aren't planned (elzerma, 2026-10-17). */
	private static final Set<String> COOLDOWN = Set.of("TELEPORTATION_MINIGAME", "TELEPORTATION_SPELL_HOME");
	/** The house's exits: planned with the player's own house set-up, not from the atlas alone. */
	private static final Set<String> HOUSE = Set.of("TELEPORTATION_PORTAL_POH", "TELEPORTATION_BOX");

	private final TravelAtlas atlas;
	private final AtlasAccount account;
	/** The player's house as a way out, or null. */
	private final AtlasHouse house;
	private final List<TravelAtlas.Jump> teleports = new ArrayList<>();

	/** One leg: its seconds (before overhead), what it is, and whether it's only walking (no teleport or hop). */
	@Value
	public static class Leg
	{
		double seconds;
		String how;
		boolean walking;
	}

	public AtlasLegs(TravelAtlas atlas, AtlasAccount account)
	{
		this(atlas, account, null);
	}

	/**
	 * @param house the player's house as a way out (AtlasHouse.of), or null
	 */
	public AtlasLegs(TravelAtlas atlas, AtlasAccount account, AtlasHouse house)
	{
		this.atlas = atlas;
		this.account = account;
		this.house = house;
		for (TravelAtlas.Jump t : atlas.getTeleports())
		{
			if (!COOLDOWN.contains(t.getType()) && !HOUSE.contains(t.getType()) && account.meetsAll(atlas, t.getNeeds()))
			{
				teleports.add(t);
			}
		}
	}

	/** How many teleports this player can use, for the log. */
	public int usableTeleports()
	{
		return teleports.size();
	}

	/**
	 * The quickest leg from a place (or from anywhere, {@code from} &lt; 0) to any of these places; null if there's
	 * none.
	 */
	public Leg quickest(int from, Collection<Integer> to)
	{
		Leg best = null;
		final Map<Integer, Leg> reachHub = new HashMap<>();
		for (int target : to)
		{
			if (from >= 0)
			{
				best = better(best, walk(from, target, 0, ""));
			}
			for (TravelAtlas.Jump t : teleports)
			{
				best = better(best, walk(t.getTo(), target, t.getTicks() * SECONDS_PER_TICK, t.getName() + " > "));
			}
			if (house != null)
			{
				for (AtlasHouse.Exit exit : house.getExits())
				{
					best = better(best, walk(exit.getLanding(), target,
						house.getSeconds() + exit.getTicks() * SECONDS_PER_TICK, "House > " + exit.getName() + " > "));
				}
			}
			// A hop: to its hub on foot or after a teleport, the hop, then the way from where it lands
			for (int landing : atlas.into(target))
			{
				final TravelAtlas.Place place = atlas.place(landing);
				if (place == null || place.getKind() != TravelAtlas.Kind.LANDING)
				{
					continue;
				}
				final TravelAtlas.Way last = usable(atlas.ways(landing, target));
				if (last == null)
				{
					continue;
				}
				for (TravelAtlas.Jump hop : atlas.hopsInto(landing))
				{
					if (!account.meetsAll(atlas, hop.getNeeds()))
					{
						continue;
					}
					final Leg reach = reachHub.computeIfAbsent(hop.getFrom(), hub -> toHub(from, hub));
					if (reach == null)
					{
						continue;
					}
					final double seconds = reach.seconds + hop.getTicks() * SECONDS_PER_TICK + seconds(last);
					if (best == null || seconds < best.seconds)
					{
						best = new Leg(seconds, reach.how + " > " + hop.getName() + " > " + describe(last), false);
					}
				}
			}
		}
		return best;
	}

	/** The quickest way to a hub: on foot from {@code from}, or a teleport and a walk. */
	private Leg toHub(int from, int hub)
	{
		Leg best = from >= 0 ? walk(from, hub, 0, "") : null;
		for (TravelAtlas.Jump t : teleports)
		{
			best = better(best, walk(t.getTo(), hub, t.getTicks() * SECONDS_PER_TICK, t.getName() + " > "));
		}
		return best;
	}

	/** The first usable way from one place to another, as a leg with {@code before} seconds in front; or null. */
	private Leg walk(int from, int to, double before, String prefix)
	{
		final TravelAtlas.Way way = usable(atlas.ways(from, to));
		return way == null ? null : new Leg(before + seconds(way), prefix + describe(way), prefix.isEmpty());
	}

	/** The ways are quickest first, and each slower one needs less: the first the player can use is the best. */
	private TravelAtlas.Way usable(List<TravelAtlas.Way> ways)
	{
		for (TravelAtlas.Way way : ways)
		{
			if (account.meetsAll(atlas, way.getNeeds()))
			{
				return way;
			}
		}
		return null;
	}

	private static double seconds(TravelAtlas.Way way)
	{
		return way.getTiles() * SECONDS_PER_TILE + way.getTicks() * SECONDS_PER_TICK;
	}

	/** e.g. "walk 81 tiles, Cross Stepping stone". */
	private String describe(TravelAtlas.Way way)
	{
		final StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "walk %d tiles", way.getTiles()));
		for (int step : way.getSteps())
		{
			s.append(", ").append(atlas.step(step).getName());
		}
		return s.toString();
	}

	private static Leg better(Leg best, Leg candidate)
	{
		return candidate != null && (best == null || candidate.seconds < best.seconds) ? candidate : best;
	}
}
