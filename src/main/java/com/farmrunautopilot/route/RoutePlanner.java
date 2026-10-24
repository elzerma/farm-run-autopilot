package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.MetaOrder;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.AtlasBanks;
import com.farmrunautopilot.data.travel.atlas.AtlasHouse;
import com.farmrunautopilot.data.travel.atlas.AtlasLegs;
import com.farmrunautopilot.data.travel.atlas.AtlasLinks;
import com.farmrunautopilot.data.travel.atlas.AtlasOption;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.TravelStyle;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyCalculator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Orders the stops and picks how to reach each one (SPEC 12).
 *
 * <p>Every leg is in seconds. On the travel atlas (docs/plans/travel-rework.md, sprint T3) every way the player can use
 * is weighed: any teleport, hop or way out of their house, from where they are, timed by the atlas's walking tiles and
 * transport ticks with the plugin's own timing for each kind of teleport (the quetzal whistle's wait). Without the atlas
 * (not read yet, or it couldn't be) no stop has a way. The player's recorded times never choose (elzerma, 2026-10-17:
 * times are for the estimate and personal bests). Preferences (prefer walking, the travel style)
 * only choose between ways of making a leg; they never change its seconds. A leg the player can't make yet carries a
 * penalty for the ordering so it goes where it hurts least, but its seconds stay honest.
 */
public final class RoutePlanner
{
	static final double SECONDS_PER_TILE = TravelTimes.SECONDS_PER_TILE;
	/** With "prefer walking" on, a walk up to this much slower than a teleport is chosen instead. */
	static final double PREFER_WALKING_SECONDS = 8.0;
	/**
	 * Renu from a nearby pad (free: no runes, no whistle charge) wins a near tie with a teleport into Varlamore (elzerma:
	 * run back to the Kastori pad for Renu). A whistle charge is no tie-break of its own: a charge to reach the Hunter
	 * Guild is fine (elzerma, 2026-10-13), and at 4 s, then 1 s, it lost their quicker whistle flights to the Civitas illa
	 * Fortis Teleport, which costs runes (2026-10-15, 2026-10-22).
	 */
	static final double RENU_PREFERENCE = 2.0;
	/** Switching spellbook at a house altar and back again later (two trips to the altar). */
	static final double SPELLBOOK_SWAP = 2 * (TravelTimes.TELEPORT + TravelTimes.HOUSE_WALK + 3.0);
	/** For the ordering only: a leg needing an item or tablet the player doesn't have goes last if it can. */
	static final double MISSING_PENALTY = 60;
	/** For the ordering only: a stop with no unlocked way there at all. */
	static final double NO_WAY_PENALTY = 300;
	/** Rough time at each patch (harvest, clear, plant, pay). */
	static final double SECONDS_PER_PATCH = 20;
	/** Above this many stops, use a fast near-optimal search instead of an exact one. */
	static final int EXACT_LIMIT = 14;
	/** The first leg also starts from the atlas's places this close to where the player stands (straight-line tiles). */
	static final int FIRST_LEG_TILES = 20;
	/** For "finish near a bank": a stop with no bank by it counts as this far from one (70 tiles' walk). */
	static final double NO_BANK_SECONDS = 70 * TravelTimes.SECONDS_PER_TILE;

	private final RunConfig config;
	private final AccessSnapshot access;
	private final Holdings holdings;
	private final PohSetup poh;
	/** The travel atlas for this player, or null if it hasn't been read (no stop has a way then). */
	private final RouteAtlas atlas;
	/** Charges held before the run, for ruling out items with none to spare. */
	private final ChargeBudget startCharges;
	/** Items whose charges ran out earlier in the run being planned. */
	private final Set<TravelItem> usedUp = EnumSet.noneOf(TravelItem.class);
	/** Daily-limited teleports with no uses left today, found while planning. */
	private final Set<TravelMethod> usedUpToday = EnumSet.noneOf(TravelMethod.class);
	/** Kinds of cooldown teleport (home, minigame) already used once in the run being planned. */
	private final Set<String> cooldownsUsed = new HashSet<>();
	/** A stop the player is close to but not at, so the run can start by walking there; null if none. */
	private Location near;
	private int nearTiles;
	/** Where the run sets off from for a reason (the Farming Guild, for its seed vault), or null. */
	private Location startFrom;
	/** The run's patches: a stop's legs go to and from its patches in the run (all its patches if it has none). */
	private List<Patch> patches = Collections.emptyList();
	/** The atlas's places near where the player stands, with the seconds to walk there, for the first leg. */
	private Map<Integer, Double> nearby = Collections.emptyMap();
	/** Each stop's atlas places, worked out once. */
	private final Map<Location, List<Integer>> places = new EnumMap<>(Location.class);
	/** Legs from the atlas for this player, rebuilt when an item or a daily teleport runs out partway. */
	private AtlasLegs atlasLegs;
	private int atlasLegsFor = -1;

	private RoutePlanner(RunConfig config, AccessSnapshot access, Holdings holdings, PohSetup poh, RouteAtlas atlas)
	{
		this.config = config;
		this.access = access;
		this.holdings = holdings;
		this.poh = poh;
		this.atlas = atlas != null && !atlas.getAtlas().isEmpty() ? atlas : null;
		this.startCharges = new ChargeBudget(holdings, config.isKeepLastCharge());
	}

	/**
	 * Whether a stop is quicker to reach onward from another stop (a spirit tree, a walk) than from anywhere,
	 * e.g. Tree Gnome Village from the Gnome Stronghold.
	 */
	static boolean chainsFrom(Location from, Location to, RunConfig config, AccessSnapshot access,
		Holdings holdings, PohSetup poh, RouteAtlas atlas)
	{
		return chainSaving(from, to, config, access, holdings, poh, atlas) > 0;
	}

	/** How much quicker a stop is to reach onward from another stop than from anywhere; 0 if it isn't. */
	static double chainSaving(Location from, Location to, RunConfig config, AccessSnapshot access,
		Holdings holdings, PohSetup poh, RouteAtlas atlas)
	{
		final RoutePlanner planner = new RoutePlanner(config, access, holdings, poh, atlas);
		return Math.max(0, planner.bestLeg(null, to).seconds - planner.bestLeg(from, to).seconds);
	}

	/** The best way to a stop from anywhere, e.g. after a bank teleport. */
	static RouteStop fromAnywhere(Location to, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, RouteAtlas atlas)
	{
		return between(null, to, config, access, holdings, poh, atlas);
	}

	/** The best way from one place to another, e.g. from a stop to the Farming Guild's bank; from anywhere if null. */
	static RouteStop between(Location from, Location to, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, RouteAtlas atlas)
	{
		return new RoutePlanner(config, access, holdings, poh, atlas).bestLeg(from, to).stop(to);
	}

	/**
	 * @param patches the patches in this run
	 * @param atlas the travel atlas for this player; without it no stop has a way
	 * @param here the stop the player is standing at, or null: the run starts there with no travel (GitHub #1)
	 */
	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, RouteAtlas atlas, Location here)
	{
		return plan(patches, config, access, holdings, poh, atlas, here, null, 0);
	}

	/**
	 * @param near a stop the player is close to but not at (e.g. by the Grand Tree for the Stronghold), or null
	 * @param nearTiles how far the player is from that stop's nearest patch
	 */
	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, RouteAtlas atlas, Location here, Location near, int nearTiles)
	{
		return plan(patches, config, access, holdings, poh, atlas, here, near, nearTiles, null);
	}

	/**
	 * @param startFrom where the run sets off from, or null (issue #8): the Farming Guild, for seeds only in its seed
	 *     vault or when the player is in the guild. The run starts there if it's a stop; otherwise the first legs are
	 *     from there. Standing at a stop or a start picked in the Run options comes first.
	 */
	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, RouteAtlas atlas, Location here, Location near, int nearTiles, Location startFrom)
	{
		return plan(patches, config, access, holdings, poh, atlas, here, near, nearTiles, startFrom, null);
	}

	/**
	 * @param tile where the player stands, or null: with the atlas, the first leg can also start on foot from there
	 *     (a walk, the spirit tree or fairy ring beside them) rather than by a teleport from anywhere
	 */
	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, RouteAtlas atlas, Location here, Location near, int nearTiles, Location startFrom, WorldPoint tile)
	{
		final List<Location> stops = new ArrayList<>();
		for (Location location : Location.values())
		{
			for (Patch patch : patches)
			{
				if (patch.getLocation() == location)
				{
					stops.add(location);
					break;
				}
			}
		}
		if (stops.isEmpty())
		{
			return new Route(new ArrayList<>(), config.getRouteMode(), 0, 0);
		}
		final RoutePlanner planner = new RoutePlanner(config, access, holdings, poh, atlas);
		planner.near = near;
		planner.nearTiles = nearTiles;
		planner.startFrom = startFrom;
		planner.patches = patches;
		planner.nearby = planner.nearbyPlaces(tile);
		return planner.plan(stops, patches.size(), here);
	}

	/**
	 * Order the stops, then spend charges along that order. If an item or daily teleport ran out partway, the
	 * order was chosen as if it hadn't, so order again knowing it's gone and keep whichever is quicker.
	 */
	private Route plan(List<Location> stops, int patchCount, Location here)
	{
		final Route first = order(stops, patchCount, here);
		if (usedUp.isEmpty() && usedUpToday.isEmpty() && cooldownsUsed.isEmpty())
		{
			return first;
		}
		final Route second = order(stops, patchCount, here);
		return penalised(second) < penalised(first) ? second : first;
	}

	/** Travel time, with legs the player can't make yet counted as much worse, for comparing two orders. */
	private static double penalised(Route route)
	{
		double total = route.getTravelSeconds();
		for (RouteStop stop : route.getStops())
		{
			total += stop.isNeedsSupplies() ? MISSING_PENALTY : 0;
		}
		return total;
	}

	private Route order(List<Location> stops, int patchCount, Location here)
	{
		final int n = stops.size();
		final Leg[] firstLeg = new Leg[n];
		final Leg[][] legs = new Leg[n][n];
		for (int j = 0; j < n; j++)
		{
			firstLeg[j] = bestLeg(null, stops.get(j));
			for (int i = 0; i < n; i++)
			{
				if (i != j)
				{
					legs[i][j] = bestLeg(stops.get(i), stops.get(j));
				}
			}
		}
		final double[] endCost = new double[n];
		for (int j = 0; j < n; j++)
		{
			endCost[j] = config.isEndNearBank() ? bankSeconds(stops.get(j)) : 0;
		}
		// Already standing at a stop: start there, with nothing to travel or bring for it
		final int standingAt = here == null ? -1 : stops.indexOf(here);
		final int chosenStart = stops.indexOf(config.getStartLocation());
		// Setting off from the Farming Guild for its seed vault (issue #8): there if it's a stop, otherwise the first
		// legs are from there
		final int fromStop = startFrom == null ? -1 : stops.indexOf(startFrom);
		final boolean offStop = startFrom != null && fromStop < 0 && standingAt < 0 && chosenStart < 0;
		int walkFirst = -1;
		if (standingAt >= 0)
		{
			firstLeg[standingAt] = new Leg(null, Departure.WALK, 0, false, "standing here");
		}
		else if (offStop)
		{
			for (int j = 0; j < n; j++)
			{
				firstLeg[j] = bestLeg(startFrom, stops.get(j));
			}
		}
		else if (near != null && stops.indexOf(near) >= 0)
		{
			// Close to a stop already: start there, walking if that beats its teleport. Fixed rather than left to
			// the search, which would trade it for a second or two (e.g. ending the run at the Stronghold's bank);
			// a player near a patch expects to do it first.
			final int j = stops.indexOf(near);
			final double walk = nearTiles * SECONDS_PER_TILE + TravelTimes.WALK_OVERHEAD;
			// Teleports carry the walking preference, as everywhere else
			final boolean travels = firstLeg[j].departure != Departure.WALK && firstLeg[j].departure != Departure.NONE;
			if (walk < firstLeg[j].seconds + (travels ? teleportPreference() : 0))
			{
				firstLeg[j] = new Leg(null, Departure.WALK, walk, false, nearTiles + " tiles from here");
			}
			walkFirst = j;
		}
		final int start = standingAt >= 0 ? standingAt : chosenStart >= 0 ? chosenStart : fromStop >= 0 ? fromStop
			: walkFirst;

		final int[] order;
		switch (config.getRouteMode())
		{
			case META:
				order = fixedOrder(stops, MetaOrder.combined(), start);
				break;
			case OFF:
				order = fixedOrder(stops, customThenMeta(), -1);
				break;
			default:
				order = n <= EXACT_LIMIT
					? exactOrder(firstLeg, legs, endCost, start)
					: nearestNeighbourThenImprove(firstLeg, legs, endCost, start);
				break;
		}

		final Leg[] chosen = spendCharges(stops, order, firstLeg[order[0]], legs, offStop ? startFrom : null);
		final List<RouteStop> result = new ArrayList<>();
		double travel = 0;
		for (int k = 0; k < order.length; k++)
		{
			result.add(chosen[k].stop(stops.get(order[k])));
			travel += chosen[k].seconds;
		}
		travel += endCost[order[order.length - 1]];
		return new Route(result, config.getRouteMode(), travel, patchCount * SECONDS_PER_PATCH,
			offStop ? startFrom : null);
	}

	// Ordering

	/** Exact shortest order (Held-Karp dynamic programming over subsets). */
	static int[] exactOrder(Leg[] firstLeg, Leg[][] legs, double[] endCost, int start)
	{
		final int n = firstLeg.length;
		final int full = (1 << n) - 1;
		final double[][] best = new double[1 << n][n];
		final int[][] from = new int[1 << n][n];
		for (double[] row : best)
		{
			java.util.Arrays.fill(row, Double.MAX_VALUE);
		}
		for (int j = 0; j < n; j++)
		{
			if (start < 0 || j == start)
			{
				best[1 << j][j] = firstLeg[j].cost;
				from[1 << j][j] = -1;
			}
		}
		for (int mask = 1; mask <= full; mask++)
		{
			for (int j = 0; j < n; j++)
			{
				if ((mask & (1 << j)) == 0 || best[mask][j] == Double.MAX_VALUE)
				{
					continue;
				}
				for (int k = 0; k < n; k++)
				{
					if ((mask & (1 << k)) != 0)
					{
						continue;
					}
					final int next = mask | (1 << k);
					final double cost = best[mask][j] + legs[j][k].cost;
					if (cost < best[next][k])
					{
						best[next][k] = cost;
						from[next][k] = j;
					}
				}
			}
		}

		int last = 0;
		double bestTotal = Double.MAX_VALUE;
		for (int j = 0; j < n; j++)
		{
			final double total = best[full][j] + endCost[j];
			if (best[full][j] != Double.MAX_VALUE && total < bestTotal)
			{
				bestTotal = total;
				last = j;
			}
		}
		final int[] order = new int[n];
		int mask = full;
		for (int k = n - 1; k >= 0; k--)
		{
			order[k] = last;
			final int prev = from[mask][last];
			mask &= ~(1 << last);
			last = prev;
		}
		return order;
	}

	/**
	 * Fast near-optimal order for many stops. From each possible first stop (just the start, if fixed): nearest
	 * neighbour, then improve by reversing stretches (2-opt) and moving runs of 1-3 stops elsewhere (or-opt)
	 * until neither helps; the best result wins. Moving runs is what keeps chains together, e.g. the
	 * Stronghold and Tree Gnome Village by spirit tree, which reversals alone can't reach.
	 */
	static int[] nearestNeighbourThenImprove(Leg[] firstLeg, Leg[][] legs, double[] endCost, int start)
	{
		final int n = firstLeg.length;
		int[] best = null;
		double bestCost = Double.MAX_VALUE;
		for (int first = 0; first < n; first++)
		{
			if (start >= 0 && first != start)
			{
				continue;
			}
			final int[] order = nearestNeighbour(legs, n, first);
			improve(order, firstLeg, legs, endCost, start >= 0);
			final double cost = cost(order, firstLeg, legs, endCost);
			if (cost < bestCost)
			{
				best = order;
				bestCost = cost;
			}
		}
		return best;
	}

	private static int[] nearestNeighbour(Leg[][] legs, int n, int first)
	{
		final int[] order = new int[n];
		final boolean[] used = new boolean[n];
		int current = first;
		order[0] = current;
		used[current] = true;
		for (int k = 1; k < n; k++)
		{
			int next = -1;
			for (int j = 0; j < n; j++)
			{
				if (!used[j] && (next < 0 || legs[current][j].cost < legs[current][next].cost))
				{
					next = j;
				}
			}
			order[k] = next;
			used[next] = true;
			current = next;
		}
		return order;
	}

	/** 2-opt and or-opt on the full (asymmetric) route cost until neither helps; a fixed start stays first. */
	private static void improve(int[] order, Leg[] firstLeg, Leg[][] legs, double[] endCost, boolean fixedStart)
	{
		final int n = order.length;
		final int from = fixedStart ? 1 : 0;
		boolean improved = true;
		while (improved)
		{
			improved = false;
			// Reverse a stretch
			for (int i = from; i < n - 1; i++)
			{
				for (int j = i + 1; j < n; j++)
				{
					final double before = cost(order, firstLeg, legs, endCost);
					reverse(order, i, j);
					if (cost(order, firstLeg, legs, endCost) + 1e-9 < before)
					{
						improved = true;
					}
					else
					{
						reverse(order, i, j);
					}
				}
			}
			// Move a run of 1-3 stops to another place
			for (int length = 1; length <= 3; length++)
			{
				for (int i = from; i + length <= n; i++)
				{
					final double before = cost(order, firstLeg, legs, endCost);
					int[] bestMove = null;
					double bestMoveCost = before - 1e-9;
					for (int at = from; at <= n - length; at++)
					{
						if (at == i)
						{
							continue;
						}
						final int[] moved = move(order, i, length, at);
						final double cost = cost(moved, firstLeg, legs, endCost);
						if (cost < bestMoveCost)
						{
							bestMove = moved;
							bestMoveCost = cost;
						}
					}
					if (bestMove != null)
					{
						System.arraycopy(bestMove, 0, order, 0, n);
						improved = true;
					}
				}
			}
		}
	}

	/** The order with the run of {@code length} stops at {@code i} taken out and put back starting at {@code at}. */
	private static int[] move(int[] order, int i, int length, int at)
	{
		final int n = order.length;
		final int[] rest = new int[n - length];
		int r = 0;
		for (int k = 0; k < n; k++)
		{
			if (k < i || k >= i + length)
			{
				rest[r++] = order[k];
			}
		}
		final int[] moved = new int[n];
		System.arraycopy(rest, 0, moved, 0, at);
		System.arraycopy(order, i, moved, at, length);
		System.arraycopy(rest, at, moved, at + length, rest.length - at);
		return moved;
	}

	private static double cost(int[] order, Leg[] firstLeg, Leg[][] legs, double[] endCost)
	{
		double total = firstLeg[order[0]].cost;
		for (int k = 1; k < order.length; k++)
		{
			total += legs[order[k - 1]][order[k]].cost;
		}
		return total + endCost[order[order.length - 1]];
	}

	private static void reverse(int[] order, int i, int j)
	{
		while (i < j)
		{
			final int tmp = order[i];
			order[i++] = order[j];
			order[j--] = tmp;
		}
	}

	/** Stops in the given preferred order; anything missing goes at the end. The start goes first if set. */
	static int[] fixedOrder(List<Location> stops, List<Location> preferred, int start)
	{
		final List<Integer> order = new ArrayList<>();
		if (start >= 0)
		{
			order.add(start);
		}
		for (Location location : preferred)
		{
			final int index = stops.indexOf(location);
			if (index >= 0 && !order.contains(index))
			{
				order.add(index);
			}
		}
		for (int i = 0; i < stops.size(); i++)
		{
			if (!order.contains(i))
			{
				order.add(i);
			}
		}
		return order.stream().mapToInt(Integer::intValue).toArray();
	}

	private List<Location> customThenMeta()
	{
		final List<Location> order = new ArrayList<>(config.getCustomOrder());
		for (Location location : MetaOrder.combined())
		{
			if (!order.contains(location))
			{
				order.add(location);
			}
		}
		return order;
	}

	/**
	 * Seconds from a stop to its nearest bank, for "finish near a bank": the atlas's way from the stop's patches in the
	 * run to any bank it knows; else the bank by the stop the atlas can't reach (AtlasBanks), else 70 tiles' worth.
	 */
	private double bankSeconds(Location location)
	{
		final AtlasLegs legs = legs();
		Double best = null;
		if (legs != null)
		{
			for (int from : places(location))
			{
				for (TravelAtlas.Place bank : atlas.getAtlas().getPlaces())
				{
					if (bank.getKind() != TravelAtlas.Kind.BANK)
					{
						continue;
					}
					final AtlasOption walk = legs.walk(from, bank.getId());
					if (walk != null && (best == null || walk.getSeconds() < best))
					{
						best = walk.getSeconds();
					}
				}
			}
		}
		if (best != null)
		{
			return best;
		}
		final AtlasBanks.Bank bank = AtlasBanks.near(location);
		return bank != null ? bank.getSeconds() : NO_BANK_SECONDS;
	}

	// Legs

	/** How one leg is travelled and what it costs. */
	static final class Leg
	{
		final TravelMethod method;
		final Departure departure;
		/** Estimated time, as shown to the player: honest seconds, nothing added for preferences. */
		final double seconds;
		final boolean needsSupplies;
		/** Added for the ordering only, for legs the player can't make yet. */
		final double penalty;
		/** What the route ordering minimises: the seconds plus any penalty. */
		final double cost;
		/** How the seconds were worked out, for the log. */
		final String breakdown;
		/** The way, for a leg only the atlas knows ({@link Departure#ATLAS}); otherwise null. */
		final AtlasLeg atlas;
		/** What the leg uses that the player can turn off on the Travel tab (AtlasWays keys), from the atlas. */
		final List<String> ways;
		/** The kind of cooldown teleport it uses (a home or minigame teleport, one of each per run), or null. */
		final String cooldown;

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies, String breakdown)
		{
			this(method, departure, seconds, needsSupplies, 0, breakdown);
		}

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies, double penalty,
			String breakdown)
		{
			this(method, departure, seconds, needsSupplies, penalty, breakdown, null);
		}

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies, double penalty,
			String breakdown, AtlasLeg atlas)
		{
			this(method, departure, seconds, needsSupplies, penalty, breakdown, atlas, Collections.emptyList(), null);
		}

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies, double penalty,
			String breakdown, AtlasLeg atlas, List<String> ways, String cooldown)
		{
			this.method = method;
			this.departure = departure;
			this.seconds = seconds;
			this.needsSupplies = needsSupplies;
			this.penalty = penalty;
			this.cost = seconds + penalty;
			this.breakdown = breakdown;
			this.atlas = atlas;
			this.ways = ways;
			this.cooldown = cooldown;
		}

		RouteStop stop(Location to)
		{
			return new RouteStop(to, method, departure, seconds, needsSupplies, breakdown, atlas, ways);
		}
	}

	/** Preference against teleporting when the player prefers walking: saves charges and clicks for little time. */
	private double teleportPreference()
	{
		return config.isPreferWalking() ? PREFER_WALKING_SECONDS : 0;
	}

	/**
	 * The quickest way from {@code from} (null at the start of the run) to {@code to}: every way the player can use,
	 * directly or through the house. A teleport a guided test pins ({@link RunConfig#getTestTravel()}) is used
	 * whenever it can be (cast, or its item or tablet owned), otherwise the rest are (GitHub #2: a Lunar spell on the
	 * standard book). Methods the player lacks the item or tablet for only win if nothing else is possible. No way at all
	 * without the atlas.
	 */
	Leg bestLeg(Location from, Location to)
	{
		final AtlasLegs legs = legs();
		if (legs == null)
		{
			return noWay();
		}
		final List<Integer> toPlaces = places(to);
		final Map<Integer, Double> fromPlaces = from == null ? nearby : at(from);
		if (toPlaces.isEmpty() || from != null && fromPlaces.isEmpty())
		{
			return noWay();
		}
		final Leg leg = atlasLeg(legs, fromPlaces, toPlaces, to);
		return leg != null ? leg : missingLeg(legs, to, config.getTestTravel().get(to), unlockedTo(to));
	}

	/**
	 * The leg the player would pick among the atlas's options: the quickest, with the walking preference, the travel
	 * style and Renu as tie-breaks; a guided test's pinned teleport, used directly, whenever an option is it. Null if
	 * the atlas has no way at all for this player.
	 */
	private Leg atlasLeg(AtlasLegs legs, Map<Integer, Double> from, List<Integer> to, Location location)
	{
		final TravelMethod chosen = config.getTestTravel().get(location);
		final boolean chosenUnlocked = chosen != null && access.missingFor(chosen).isEmpty();
		final TravelAtlas data = atlas.getAtlas();
		AtlasOption best = null;
		AtlasTravel.Match bestMatch = null;
		double bestRank = Double.MAX_VALUE;
		AtlasOption picked = null;
		AtlasTravel.Match pickedMatch = null;
		double pickedRank = Double.MAX_VALUE;
		// A pinned teleport can be any way slower, so everything is looked at; otherwise only what could win
		for (AtlasOption option : legs.options(from, to, chosenUnlocked ? Double.MAX_VALUE : OPTION_SLACK))
		{
			// Quickest first, and nothing takes more off than Renu's preference: the rest can't win
			final double floor = option.getSeconds() + TravelTimes.WALK_OVERHEAD - RENU_PREFERENCE;
			if (floor > bestRank && (!chosenUnlocked || floor > pickedRank))
			{
				break;
			}
			final AtlasTravel.Match match = AtlasTravel.match(data, option, location);
			final double rank = rank(option, match, location);
			if (rank < bestRank)
			{
				best = option;
				bestMatch = match;
				bestRank = rank;
			}
			if (chosenUnlocked && match.getMethod() == chosen && match.getDeparture() == Departure.DIRECT
				&& rank < pickedRank)
			{
				picked = option;
				pickedMatch = match;
				pickedRank = rank;
			}
		}
		if (picked != null)
		{
			return atlasLeg(legs, picked, pickedMatch);
		}
		return best == null ? null : atlasLeg(legs, best, bestMatch);
	}

	/** What the player would weigh an option by: its seconds and overhead, with the preferences and style. */
	private double rank(AtlasOption option, AtlasTravel.Match match, Location to)
	{
		final boolean walking = match.getDeparture() == Departure.WALK;
		final double seconds = option.getSeconds() + (walking ? TravelTimes.WALK_OVERHEAD : TravelTimes.LEG_OVERHEAD);
		if (walking)
		{
			return seconds;
		}
		final double style = config.getTravelStyle() == TravelStyle.FASTEST ? 0
			: stylePenalty(config.getTravelStyle(), match.getMethod() != null
			? cost(new Leg(match.getMethod(), match.getDeparture(), 0, false, ""), to) : atlasCost(option, to));
		return seconds + preference(option, match) + teleportPreference() + style;
	}

	/**
	 * Renu from a pad needs no runes and no whistle charge, so it wins a near tie (elzerma: the run back to Kastori's pad
	 * is short); a whistle flight is weighed by its time alone (see {@link #RENU_PREFERENCE}).
	 */
	private static double preference(AtlasOption option, AtlasTravel.Match match)
	{
		if (option.getTeleport() != null && option.getTeleport().getType().equals("QUETZAL_WHISTLE"))
		{
			return 0;
		}
		final boolean renu = match.getDeparture() == Departure.RENU
			|| option.getKind() == AtlasOption.Kind.HOP && option.getHop().getType().equals("QUETZAL");
		return renu ? -RENU_PREFERENCE : 0;
	}

	/**
	 * The planner's leg for an atlas option, with its overhead, the way described if it's only the atlas's, and what it
	 * uses that the player can turn off (the Run tab's "Don't use ...").
	 */
	private Leg atlasLeg(AtlasLegs legs, AtlasOption option, AtlasTravel.Match match)
	{
		final boolean walking = match.getDeparture() == Departure.WALK;
		final double overhead = walking ? TravelTimes.WALK_OVERHEAD : TravelTimes.LEG_OVERHEAD;
		final AtlasLeg described = match.getDeparture() == Departure.ATLAS
			? AtlasTravel.leg(atlas.getAtlas(), option, holdings, this::owns) : null;
		final TravelAtlas.Jump teleport = option.getTeleport();
		final String cooldown = teleport != null && AtlasWays.family(teleport) == TravelFamily.COOLDOWN
			? teleport.getType() : null;
		return new Leg(match.getMethod(), match.getDeparture(), option.getSeconds() + overhead, false,
			walking ? 0 : preference(option, match), legs.breakdown(option) + " + overhead " + round(overhead), described,
			AtlasWays.keys(atlas.getAtlas(), option), cooldown);
	}

	/** What an option the plugin doesn't know as one of its methods costs besides time, for the travel styles. */
	private Cost atlasCost(AtlasOption option, Location to)
	{
		switch (option.getKind())
		{
			case WALK:
			case HOP:
			case TREE_HOUSE:
				return Cost.FREE;
			case HOUSE:
				return capeHome() ? Cost.FREE : Cost.HOUSE;
			default:
				final TravelAtlas data = atlas.getAtlas();
				final TravelAtlas.Jump teleport = option.getTeleport();
				final Spell spell = AtlasLinks.spell(data, teleport);
				if (spell != null)
				{
					return SupplyCalculator.usesTablet(spell, to, config, access, holdings) ? Cost.TABLET : Cost.RUNES;
				}
				final TravelItem item = AtlasTravel.item(data, teleport, this::owns);
				if (item != null)
				{
					return isLimited(item, AtlasTravel.daily(data, teleport)) ? Cost.CHARGES : Cost.FREE;
				}
				return teleport.isConsumable() ? Cost.TABLET : Cost.FREE;
		}
	}

	/** The atlas's legs for this player now, or null without the atlas. */
	private AtlasLegs legs()
	{
		if (atlas == null)
		{
			return null;
		}
		// Rebuilt once an item, a daily teleport or a cooldown teleport is found to run out partway, so it's ruled out;
		// until then the same as every other plan and question for this player, so shared
		final int version = (usedUp.size() * 100 + usedUpToday.size()) * 10 + cooldownsUsed.size();
		if (atlasLegs == null || atlasLegsFor != version)
		{
			atlasLegs = version == 0
				? atlas.freshLegs(new Object[]{config, access, holdings, poh}, this::newLegs) : newLegs();
			atlasLegsFor = version;
		}
		return atlasLegs;
	}

	/** The atlas's legs for this player as they are in this plan (what's used up so far, the house). */
	private AtlasLegs newLegs()
	{
		final boolean trees = access.isMet(Requirement.unlock(Unlock.SPIRIT_TREES));
		final Double teleportIn = canTeleportHome() ? houseTeleportSeconds()
			+ (access.isHouseTeleportOutside() ? TravelTimes.HOUSE_DOOR : 0) + TravelTimes.HOUSE_WALK : null;
		final AtlasHouse house = AtlasHouse.of(atlas.getAtlas(), atlas.getAccount(), poh, teleportIn, trees);
		return new AtlasLegs(atlas.getAtlas(), atlas.getAccount(), house, this::teleportExtra);
	}

	/**
	 * The plugin's own rules for its items and spells, for the atlas's teleports: an item needs a charge to spare and
	 * a use left today, and isn't used up earlier in this run; a spell needs casting (levels, quests, a spellbook it
	 * can get to), and costs a spellbook swap off its book. Null if the player can't use it.
	 */
	private Double teleportExtra(TravelAtlas.Jump teleport)
	{
		if (cooldownsUsed.contains(teleport.getType()))
		{
			return null;
		}
		final TravelAtlas data = atlas.getAtlas();
		final Spell spell = AtlasLinks.spell(data, teleport);
		if (spell != null && teleport.getType().equals("TELEPORTATION_SPELL"))
		{
			return access.canCast(spell, config.isSwitchSpellbooks()) ? castSwap(spell) : null;
		}
		if (!AtlasLinks.items(data, teleport).isEmpty())
		{
			if (!owns(AtlasTravel.item(data, teleport, this::owns)))
			{
				return null;
			}
			final TravelMethod daily = AtlasTravel.daily(data, teleport);
			if (daily != null && (!startCharges.hasUseToday(daily) || usedUpToday.contains(daily)))
			{
				return null;
			}
		}
		return 0.0;
	}

	/** Switching spellbook to cast a spell: none on its book, one Spellbook Swap on Lunar, or two altar trips. */
	private double castSwap(Spell spell)
	{
		if (access.isOnSpellbook(spell))
		{
			return 0;
		}
		return access.needsLunarSwap(spell) ? TravelTimes.TELEPORT : SPELLBOOK_SWAP;
	}

	/** A stop's patches in this run (all its patches if it has none in the run) as atlas places. */
	private List<Integer> places(Location location)
	{
		return places.computeIfAbsent(location, l ->
		{
			final List<Integer> found = new ArrayList<>();
			final boolean inRun = patches.stream().anyMatch(p -> p.getLocation() == l);
			for (Patch patch : inRun ? patches : java.util.Arrays.asList(Patch.values()))
			{
				final TravelAtlas.Place place = patch.getLocation() == l ? atlas.getAtlas().patchPlace(patch.name()) : null;
				if (place != null && !found.contains(place.getId()))
				{
					found.add(place.getId());
				}
			}
			return found;
		});
	}

	/** At a stop: its places, nothing to walk first (the player finishes at whichever patch suits the next leg). */
	private Map<Integer, Double> at(Location location)
	{
		final Map<Integer, Double> from = new HashMap<>();
		for (int place : places(location))
		{
			from.put(place, 0.0);
		}
		return from;
	}

	/** A bank the atlas knows is within {@link #FIRST_LEG_TILES} of where the player stands. */
	static boolean bankNear(RouteAtlas atlas, WorldPoint tile)
	{
		for (TravelAtlas.Place place : atlas.getAtlas().getPlaces())
		{
			final WorldPoint p = place.getPoint();
			if (place.getKind() == TravelAtlas.Kind.BANK && p.getPlane() == tile.getPlane()
				&& Math.max(Math.abs(p.getX() - tile.getX()), Math.abs(p.getY() - tile.getY())) <= FIRST_LEG_TILES)
			{
				return true;
			}
		}
		return false;
	}

	/** The atlas's places near where the player stands, with the seconds to walk there (straight-line tiles). */
	private Map<Integer, Double> nearbyPlaces(WorldPoint tile)
	{
		if (tile == null || atlas == null)
		{
			return Collections.emptyMap();
		}
		final Map<Integer, Double> found = new HashMap<>();
		for (TravelAtlas.Place place : atlas.getAtlas().getPlaces())
		{
			final WorldPoint p = place.getPoint();
			final int tiles = Math.max(Math.abs(p.getX() - tile.getX()), Math.abs(p.getY() - tile.getY()));
			if (p.getPlane() == tile.getPlane() && tiles <= FIRST_LEG_TILES)
			{
				found.put(place.getId(), tiles * SECONDS_PER_TILE);
			}
		}
		return found;
	}

	/** Every travel method to a stop whose requirements are met. */
	private List<TravelMethod> unlockedTo(Location to)
	{
		final List<TravelMethod> candidates = new ArrayList<>();
		for (TravelMethod method : TravelMethod.values())
		{
			if (method.getDestination() == to && access.missingFor(method).isEmpty())
			{
				candidates.add(method);
			}
		}
		return candidates;
	}

	/**
	 * Nothing usable: the pinned method, or the quickest unlocked one, for the supply list to ask for, timed on the atlas
	 * from where it lands; or no way at all.
	 */
	private Leg missingLeg(AtlasLegs legs, Location to, TravelMethod chosen, List<TravelMethod> candidates)
	{
		Leg fallback = null;
		for (TravelMethod method : candidates)
		{
			// Only suggest something the player could actually get: a spell they can't cast needs a tablet to buy, and
			// an ironman can't buy one (elzerma: Harmony Island planned by a tablet they can't get). A tablet already
			// held, group storage included, was planned as owned before this
			final Spell spell = method.getSpell();
			if (method.getKind() == TravelKind.SPELL && !access.canCast(spell, config.isSwitchSpellbooks())
				&& (!spell.hasTablet() || access.isIronman()))
			{
				continue;
			}
			// Spirit trees and fairy rings aren't something to bring; nor is anything the player turned off
			if (method.getKind() == TravelKind.SPIRIT_TREE || method.getKind() == TravelKind.FAIRY_RING
				|| !config.isOn(family(method)))
			{
				continue;
			}
			final AtlasOption walk = landingWalk(legs, method, to);
			if (walk == null)
			{
				continue;
			}
			final double seconds = baseSeconds(method) + walk.getSeconds() + TravelTimes.LEG_OVERHEAD;
			final Leg leg = new Leg(method, Departure.DIRECT, seconds, true, MISSING_PENALTY, "not owned yet: "
				+ method.getDisplayName() + " " + round(baseSeconds(method)) + " + " + legs.breakdown(walk) + " + overhead "
				+ round(TravelTimes.LEG_OVERHEAD));
			if (method == chosen)
			{
				return leg;
			}
			if (fallback == null || seconds < fallback.seconds)
			{
				fallback = leg;
			}
		}
		return fallback != null ? fallback : noWay();
	}

	/** No way there: last in the ordering, if it can be. */
	private static Leg noWay()
	{
		return new Leg(null, Departure.NONE, 0, false, NO_WAY_PENALTY, "no way there");
	}

	/** The atlas's way from where one of the plugin's own methods lands to the stop's patches; null if there's none. */
	private AtlasOption landingWalk(AtlasLegs legs, TravelMethod method, Location to)
	{
		final WorldPoint landing = AtlasLinks.landing(method);
		AtlasOption best = null;
		for (TravelAtlas.Place place : atlas.getAtlas().getPlaces())
		{
			if (!AtlasLinks.sameLanding(place.getPoint(), landing))
			{
				continue;
			}
			for (int target : places(to))
			{
				final AtlasOption walk = legs.walk(place.getId(), target);
				if (walk != null && (best == null || walk.getSeconds() < best.getSeconds()))
				{
					best = walk;
				}
			}
		}
		return best;
	}

	/** The Travel tab family one of the plugin's own methods would be suggested under, when nothing else gets there. */
	private TravelFamily family(TravelMethod method)
	{
		switch (method.getKind())
		{
			case SPELL:
				if (method.getSpell() == Spell.LUMBRIDGE_HOME_TELEPORT)
				{
					return TravelFamily.COOLDOWN;
				}
				// A spell the player can't cast is suggested as its tablet
				return access.canCast(method.getSpell(), config.isSwitchSpellbooks()) ? TravelFamily.SPELLS
					: TravelFamily.TABLETS;
			case TABLET:
				return TravelFamily.TABLETS;
			case HOUSE_PORTAL:
				return TravelFamily.HOUSE;
			case BALLOON:
				return TravelFamily.GLIDERS;
			default:
				// Jewellery, capes, diary items, the quetzal whistle and other teleport items
				return TravelFamily.TELEPORT_ITEMS;
		}
	}

	/**
	 * The plugin's own rules for its items and spells before anything is used, as the planner applies them: for the
	 * Travel tab, which says which ways are ready (TravelWays).
	 */
	static AtlasLegs.TeleportCheck startRules(RunConfig config, AccessSnapshot access, Holdings holdings, PohSetup poh,
		RouteAtlas atlas)
	{
		return new RoutePlanner(config, access, holdings, poh, atlas)::teleportExtra;
	}

	/** The plugin's timing for using one of its own methods, before the walk from where it lands. */
	private static double baseSeconds(TravelMethod method)
	{
		switch (method.getKind())
		{
			case SPELL:
				return method.getSpell() == Spell.LUMBRIDGE_HOME_TELEPORT ? TravelTimes.HOME_TELEPORT
					: TravelTimes.TELEPORT;
			case TABLET:
			case HOUSE_PORTAL:
				return TravelTimes.TELEPORT;
			case QUETZAL:
				// Arriving by quetzal directly means the whistle
				return TravelTimes.QUETZAL_WHISTLE;
			case FAIRY_RING:
				return TravelTimes.FAIRY_RING;
			case SPIRIT_TREE:
				return TravelTimes.SPIRIT_TREE;
			case BALLOON:
				return TravelTimes.BALLOON;
			default:
				return TravelTimes.ITEM_TELEPORT;
		}
	}

	private static String round(double seconds)
	{
		return String.valueOf(Math.round(seconds));
	}

	/** Teleport to House by a cape, a tablet, or the spell (switching spellbook first if it's another book). */
	private double houseTeleportSeconds()
	{
		if (capeHome())
		{
			return TravelTimes.ITEM_TELEPORT;
		}
		return TravelTimes.TELEPORT + spellbookSwap(Spell.TELEPORT_TO_HOUSE);
	}

	/** A construction cape or max cape gets the player home without a tablet or runes. */
	private boolean capeHome()
	{
		return owns(TravelItem.CONSTRUCTION_CAPE) || owns(TravelItem.MAX_CAPE);
	}

	/**
	 * Extra time when the spell is on another spellbook and has to be cast after switching at a house altar.
	 * A tablet the player owns needs no switch.
	 */
	private double spellbookSwap(Spell spell)
	{
		final boolean tablet = spell.hasTablet() && holdings.count(spell.getTabletItemId()) > 0;
		return tablet ? 0 : castSwap(spell);
	}

	private boolean canTeleport(Spell spell)
	{
		return access.canCast(spell, config.isSwitchSpellbooks())
			|| (spell.hasTablet() && holdings.count(spell.getTabletItemId()) > 0);
	}

	/** Teleport to House by spell, tablet, construction cape or max cape. */
	boolean canTeleportHome()
	{
		return canTeleport(Spell.TELEPORT_TO_HOUSE) || capeHome();
	}

	/** What a leg costs besides time, for the travel styles. */
	enum Cost
	{
		FREE,
		/** Through the house, paying for Teleport to House with a tablet or runes. */
		HOUSE,
		/** Uses a charge or a daily use. */
		CHARGES,
		TABLET,
		RUNES
	}

	/** Seconds a style adds to ways it doesn't favour, so those win only when clearly quicker. */
	static final double STYLE_PENALTY = 15;
	/**
	 * How much slower than the quickest an atlas option can be and still be picked: the difference in overhead between
	 * a walk and the rest, and every preference (the style, prefer walking, Renu) at once.
	 */
	static final double OPTION_SLACK = TravelTimes.LEG_OVERHEAD - TravelTimes.WALK_OVERHEAD + STYLE_PENALTY
		+ PREFER_WALKING_SECONDS + RENU_PREFERENCE + 1;

	static double stylePenalty(TravelStyle style, Cost cost)
	{
		switch (style)
		{
			case PREFER_FREE:
				return cost == Cost.FREE ? 0 : STYLE_PENALTY;
			case SAVE_CHARGES:
				return cost == Cost.CHARGES ? STYLE_PENALTY : 0;
			default:
				return 0;
		}
	}

	private Cost cost(Leg leg, Location to)
	{
		// Into the house by a spirit tree costs nothing (issue #16), nor does Renu from a pad (no whistle charge)
		if (leg.departure.isByTree() || leg.departure == Departure.RENU)
		{
			return Cost.FREE;
		}
		if (leg.departure.isViaHouse() || leg.method.getKind() == TravelKind.HOUSE_PORTAL)
		{
			return capeHome() ? Cost.FREE : Cost.HOUSE;
		}
		if (leg.departure.getFairyRingAccess() != null)
		{
			final TravelItem item = itemUsed(leg);
			return item != null && isLimited(item, leg.method) ? Cost.CHARGES : Cost.FREE;
		}
		final Spell spell = leg.method.getSpell();
		if (spell != null)
		{
			return SupplyCalculator.usesTablet(spell, to, config, access, holdings) ? Cost.TABLET : Cost.RUNES;
		}
		final TravelItem item = leg.method.getItem();
		return item != null && isLimited(item, leg.method) ? Cost.CHARGES : Cost.FREE;
	}

	/** Runs out: charged jewellery, self-charged items, single-use items or daily-limited teleports. */
	private boolean isLimited(TravelItem item, TravelMethod method)
	{
		return ChargeBudget.held(item, holdings) != null || ChargeBudget.chargesUnknown(item, holdings)
			|| method != null && ChargeBudget.leftToday(method, holdings) != null;
	}

	/** Held, with a charge to spare, and not already used up earlier in this run. */
	private boolean owns(TravelItem item)
	{
		return item != null && holdings.countAny(item.getItemIds()) > 0 && startCharges.hasCharge(item)
			&& !usedUp.contains(item);
	}

	/** The charged item a leg uses up a charge of, or null (spells, walking, the house, unlimited items). */
	private TravelItem itemUsed(Leg leg)
	{
		if (leg.atlas != null)
		{
			return leg.atlas.getItem();
		}
		if (leg.method == null)
		{
			return null;
		}
		if (leg.departure == Departure.DIRECT && leg.method.getItem() != null)
		{
			return leg.method.getItem();
		}
		final FairyRingAccess way = leg.departure.getFairyRingAccess();
		if (way != null)
		{
			for (TravelItem item : way.getItems())
			{
				if (owns(item))
				{
					return item;
				}
			}
		}
		return null;
	}

	/** The daily-limited teleport a leg uses one of today's uses of, or null. */
	private static TravelMethod dailyUsed(Leg leg)
	{
		if (leg.atlas != null)
		{
			return leg.atlas.getDaily();
		}
		return leg.departure == Departure.DIRECT ? leg.method : null;
	}

	/**
	 * Walk the planned order spending charges, pooled per item; a leg whose item has none left is planned again
	 * without that item (e.g. a slayer ring with one charge covers one stop, not two). A home or minigame teleport is
	 * used once per run at most, as its cooldown (30 or 20 minutes) outlasts most runs.
	 *
	 * @param firstFrom where the first leg sets off from, or null for anywhere (or where the player stands)
	 */
	private Leg[] spendCharges(List<Location> stops, int[] order, Leg first, Leg[][] legs, Location firstFrom)
	{
		final ChargeBudget budget = new ChargeBudget(holdings, config.isKeepLastCharge());
		final Set<String> cooldownsSpent = new HashSet<>();
		final Leg[] chosen = new Leg[order.length];
		for (int k = 0; k < order.length; k++)
		{
			final Location from = k == 0 ? firstFrom : stops.get(order[k - 1]);
			final Location to = stops.get(order[k]);
			Leg leg = k == 0 ? first : legs[order[k - 1]][order[k]];
			// The leg was worked out before anything ran out: planned again once if it uses what ran out earlier
			boolean replanned = false;
			while (true)
			{
				final TravelItem item = itemUsed(leg);
				final TravelMethod daily = dailyUsed(leg);
				final boolean itemOut = item != null && !budget.hasCharge(item);
				final boolean dailyOut = daily != null && !budget.hasUseToday(daily);
				final boolean cooldownOut = leg.cooldown != null && cooldownsSpent.contains(leg.cooldown);
				if (!itemOut && !dailyOut && !cooldownOut)
				{
					if (item != null)
					{
						budget.spend(item);
					}
					if (daily != null)
					{
						budget.spendUseToday(daily);
					}
					if (leg.cooldown != null)
					{
						cooldownsSpent.add(leg.cooldown);
					}
					break;
				}
				// Rule out what ran out and plan this stop again; each pass rules out something new, so this ends
				final boolean ruledOut = (itemOut && usedUp.add(item)) | (dailyOut && usedUpToday.add(daily))
					| (cooldownOut && cooldownsUsed.add(leg.cooldown));
				if (!ruledOut && replanned)
				{
					// Nothing else gets there: keep it, as something to recharge, come back to tomorrow or wait for
					leg = new Leg(leg.method, leg.departure, leg.seconds, true, MISSING_PENALTY, leg.breakdown
						+ (cooldownOut ? ", used once already this run (cooldown)" : ", no charges left"), leg.atlas,
						leg.ways, leg.cooldown);
					break;
				}
				leg = bestLeg(from, to);
				replanned = true;
			}
			chosen[k] = leg;
		}
		return chosen;
	}
}
