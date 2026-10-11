package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.MetaOrder;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.TravelStyle;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyCalculator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Orders the stops and picks how to reach each one (SPEC 12).
 *
 * <p>Every leg is in seconds: the estimate from {@link TravelTimes} (cast, walk, rides, overhead), replaced by
 * the player's own recorded times once there are enough. Preferences (prefer walking, the travel style) only
 * choose between ways of making a leg; they never change its seconds. A leg the player can't make yet carries a
 * penalty for the ordering so it goes where it hurts least, but its seconds stay honest.
 */
public final class RoutePlanner
{
	static final double SECONDS_PER_TILE = TravelTimes.SECONDS_PER_TILE;
	/** With "prefer walking" on, a walk up to this much slower than a teleport is chosen instead. */
	static final double PREFER_WALKING_SECONDS = 8.0;
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

	private final RunConfig config;
	private final AccessSnapshot access;
	private final Holdings holdings;
	private final PohSetup poh;
	private final LearnedTimes learned;
	/** Work out Auto's own pick, ignoring any teleport the player chose (for showing "Auto (best)"). */
	private boolean ignoreChoices;
	/** Charges held before the run, for ruling out items with none to spare. */
	private final ChargeBudget startCharges;
	/** Items whose charges ran out earlier in the run being planned. */
	private final Set<TravelItem> usedUp = EnumSet.noneOf(TravelItem.class);
	/** Daily-limited teleports with no uses left today, found while planning. */
	private final Set<TravelMethod> usedUpToday = EnumSet.noneOf(TravelMethod.class);
	/** A stop the player is close to but not at, so the run can start by walking there; null if none. */
	private Location near;
	private int nearTiles;

	private RoutePlanner(RunConfig config, AccessSnapshot access, Holdings holdings, PohSetup poh,
		LearnedTimes learned)
	{
		this.config = config;
		this.access = access;
		this.holdings = holdings;
		this.poh = poh;
		this.learned = learned;
		this.startCharges = new ChargeBudget(holdings, config.isKeepLastCharge());
	}

	/**
	 * How Auto (best) would reach a location from anywhere (e.g. straight from the bank), ignoring what the
	 * player chose for it. Shown on the location's row in the Travel tab.
	 */
	public static RouteStop autoPick(Location to, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, LearnedTimes learned)
	{
		final RoutePlanner planner = new RoutePlanner(config, access, holdings, poh, learned);
		planner.ignoreChoices = true;
		final Leg leg = planner.bestLeg(null, to);
		return new RouteStop(to, leg.method, leg.departure, leg.seconds, leg.needsSupplies, leg.breakdown);
	}

	/**
	 * Whether a stop is quicker to reach onward from another stop (a spirit tree, a walk) than from anywhere,
	 * e.g. Tree Gnome Village from the Gnome Stronghold.
	 */
	static boolean chainsFrom(Location from, Location to, RunConfig config, AccessSnapshot access,
		Holdings holdings, PohSetup poh, LearnedTimes learned)
	{
		return chainSaving(from, to, config, access, holdings, poh, learned) > 0;
	}

	/** How much quicker a stop is to reach onward from another stop than from anywhere; 0 if it isn't. */
	static double chainSaving(Location from, Location to, RunConfig config, AccessSnapshot access,
		Holdings holdings, PohSetup poh, LearnedTimes learned)
	{
		final RoutePlanner planner = new RoutePlanner(config, access, holdings, poh, learned);
		return Math.max(0, planner.bestLeg(null, to).seconds - planner.bestLeg(from, to).seconds);
	}

	/** The best way to a stop from anywhere, e.g. after a bank teleport. */
	static RouteStop fromAnywhere(Location to, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, LearnedTimes learned)
	{
		final Leg leg = new RoutePlanner(config, access, holdings, poh, learned).bestLeg(null, to);
		return new RouteStop(to, leg.method, leg.departure, leg.seconds, leg.needsSupplies, leg.breakdown);
	}

	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh)
	{
		return plan(patches, config, access, holdings, poh, LearnedTimes.NONE, null);
	}

	/**
	 * @param patches the patches in this run
	 * @param learned the player's recorded leg times
	 * @param here the stop the player is standing at, or null: the run starts there with no travel (GitHub #1)
	 */
	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, LearnedTimes learned, Location here)
	{
		return plan(patches, config, access, holdings, poh, learned, here, null, 0);
	}

	/**
	 * @param near a stop the player is close to but not at (e.g. by the Grand Tree for the Stronghold), or null
	 * @param nearTiles how far the player is from that stop's nearest patch
	 */
	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, LearnedTimes learned, Location here, Location near, int nearTiles)
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
		final RoutePlanner planner = new RoutePlanner(config, access, holdings, poh, learned);
		planner.near = near;
		planner.nearTiles = nearTiles;
		return planner.plan(stops, patches.size(), here);
	}

	/**
	 * Order the stops, then spend charges along that order. If an item or daily teleport ran out partway, the
	 * order was chosen as if it hadn't, so order again knowing it's gone and keep whichever is quicker.
	 */
	private Route plan(List<Location> stops, int patchCount, Location here)
	{
		final Route first = order(stops, patchCount, here);
		if (usedUp.isEmpty() && usedUpToday.isEmpty())
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
			endCost[j] = config.isEndNearBank() ? bankTiles(stops.get(j)) * SECONDS_PER_TILE : 0;
		}
		// Already standing at a stop: start there, with nothing to travel or bring for it
		final int standingAt = here == null ? -1 : stops.indexOf(here);
		int walkFirst = -1;
		if (standingAt >= 0)
		{
			firstLeg[standingAt] = new Leg(null, Departure.WALK, 0, false, "standing here");
		}
		else if (near != null && stops.indexOf(near) >= 0)
		{
			// Close to a stop already: start there, walking if that beats its teleport. Fixed rather than left to
			// the search, which would trade it for a second or two (e.g. ending the run at the Stronghold's bank);
			// a player near a patch expects to do it first.
			final int j = stops.indexOf(near);
			final double walk = learned.adjust(null, near, null, Departure.WALK,
				nearTiles * SECONDS_PER_TILE + TravelTimes.WALK_OVERHEAD);
			// Teleports carry the walking preference, as everywhere else
			if (walk < firstLeg[j].seconds + (firstLeg[j].method != null ? teleportPreference() : 0))
			{
				firstLeg[j] = new Leg(null, Departure.WALK, walk, false, nearTiles + " tiles from here");
			}
			walkFirst = j;
		}
		final int chosenStart = stops.indexOf(config.getStartLocation());
		final int start = standingAt >= 0 ? standingAt : chosenStart >= 0 ? chosenStart : walkFirst;

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

		final Leg[] chosen = spendCharges(stops, order, firstLeg[order[0]], legs);
		final List<RouteStop> result = new ArrayList<>();
		double travel = 0;
		for (int k = 0; k < order.length; k++)
		{
			final Leg leg = chosen[k];
			result.add(new RouteStop(stops.get(order[k]), leg.method, leg.departure, leg.seconds, leg.needsSupplies,
				leg.breakdown));
			travel += leg.seconds;
		}
		travel += endCost[order[order.length - 1]];
		return new Route(result, config.getRouteMode(), travel, patchCount * SECONDS_PER_PATCH);
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

	static int bankTiles(Location location)
	{
		final Integer tiles = TravelTimes.bankTiles(location);
		return tiles != null ? tiles : TravelTimes.NO_BANK_TILES;
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

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies, String breakdown)
		{
			this(method, departure, seconds, needsSupplies, 0, breakdown);
		}

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies, double penalty,
			String breakdown)
		{
			this.method = method;
			this.departure = departure;
			this.seconds = seconds;
			this.needsSupplies = needsSupplies;
			this.penalty = penalty;
			this.cost = seconds + penalty;
			this.breakdown = breakdown;
		}
	}

	/** A way of making a leg before overhead and learned times, with how its seconds add up. */
	private static final class Option
	{
		final TravelMethod method;
		final Departure departure;
		final double seconds;
		final String breakdown;

		Option(TravelMethod method, Departure departure, double seconds, String breakdown)
		{
			this.method = method;
			this.departure = departure;
			this.seconds = seconds;
			this.breakdown = breakdown;
		}
	}

	/** Preference against teleporting when the player prefers walking: saves charges and clicks for little time. */
	private double teleportPreference()
	{
		return config.isPreferWalking() ? PREFER_WALKING_SECONDS : 0;
	}

	/**
	 * The quickest way from {@code from} (null at the start of the run) to {@code to}. The player's chosen
	 * method is a preference: it's used whenever it can be (cast, or its item or tablet owned), otherwise every
	 * unlocked method is considered, directly or through the house (GitHub #2: a Lunar spell chosen on the
	 * standard book). Methods the player lacks the item or tablet for only win if nothing else is possible.
	 */
	Leg bestLeg(Location from, Location to)
	{
		final TravelMethod chosen = ignoreChoices ? null : config.getTravel().get(to);
		final boolean chosenUnlocked = chosen != null && access.missingFor(chosen).isEmpty();
		if (chosenUnlocked)
		{
			// The player may also have picked how (e.g. through the house portal nexus)
			final Leg leg = quickest(Collections.singletonList(chosen), from, to, null,
				config.getTravelHow().get(to));
			if (leg != null)
			{
				return leg;
			}
		}

		final List<TravelMethod> candidates = new ArrayList<>();
		for (TravelMethod method : TravelMethod.values())
		{
			if (method.getDestination() == to && access.missingFor(method).isEmpty())
			{
				candidates.add(method);
			}
		}
		Leg walk = null;
		final Integer tiles = from == null ? null
			: TravelTimes.walkTiles(from, to, access.isKnown() ? access.level(Skill.AGILITY) : 0);
		if (tiles != null)
		{
			final double estimate = tiles * SECONDS_PER_TILE + TravelTimes.WALK_OVERHEAD;
			final double seconds = learned.adjust(from, to, null, Departure.WALK, estimate);
			walk = new Leg(null, Departure.WALK, seconds, false, "walk " + tiles + " tiles " + round(tiles
				* SECONDS_PER_TILE) + " + overhead " + round(TravelTimes.WALK_OVERHEAD) + learnedNote(seconds, estimate));
		}
		final Leg best = quickest(candidates, from, to, walk, null);
		if (best != null)
		{
			return best;
		}

		// Nothing usable: take the chosen method, or the quickest unlocked one, and let the supply list ask for it
		Leg fallback = null;
		for (TravelMethod method : candidates)
		{
			// Only suggest something the player could actually get: a spell they can't cast needs a tablet to buy
			final Spell spell = method.getSpell();
			if (method.getKind() == TravelKind.SPELL && !access.canCast(spell) && !spell.hasTablet())
			{
				continue;
			}
			// Spirit trees and fairy rings aren't something to bring
			if (method.getKind() == TravelKind.SPIRIT_TREE || method.getKind() == TravelKind.FAIRY_RING)
			{
				continue;
			}
			final double seconds = baseSeconds(method) + walkSeconds(method) + TravelTimes.LEG_OVERHEAD;
			final Leg leg = new Leg(method, Departure.DIRECT, seconds, true, MISSING_PENALTY,
				"not owned yet: " + describe(method) + " + overhead " + round(TravelTimes.LEG_OVERHEAD));
			if (method == chosen)
			{
				return leg;
			}
			if (fallback == null || seconds < fallback.seconds)
			{
				fallback = leg;
			}
		}
		return fallback != null ? fallback : new Leg(null, Departure.NONE, 0, false, NO_WAY_PENALTY, "no way there");
	}

	/**
	 * The leg using these methods (or {@code best}, e.g. a walk) that the player would pick: the quickest, with
	 * the walking preference and the travel style as tie-breaks. Null if there's none.
	 *
	 * @param how only legs that start this way (e.g. through the nexus), or null for any
	 */
	private Leg quickest(List<TravelMethod> methods, Location from, Location to, Leg best, Departure how)
	{
		double bestRank = best == null ? Double.MAX_VALUE : best.seconds;
		for (TravelMethod method : methods)
		{
			for (Option option : options(method, from))
			{
				if (how != null && option.departure != how)
				{
					continue;
				}
				// The player's own times include the overhead, so the estimate they're blended with does too
				final double estimate = option.seconds + TravelTimes.LEG_OVERHEAD;
				final double seconds = learned.adjust(from, to, option.method, option.departure, estimate);
				final Leg leg = new Leg(option.method, option.departure, seconds, false, option.breakdown
					+ " + overhead " + round(TravelTimes.LEG_OVERHEAD) + learnedNote(seconds, estimate));
				final double rank = seconds + teleportPreference() + stylePenalty(leg, to);
				if (rank < bestRank)
				{
					best = leg;
					bestRank = rank;
				}
			}
		}
		return best;
	}

	/** Every usable way of travelling with this method from here, before overhead. */
	private List<Option> options(TravelMethod method, Location from)
	{
		final List<Option> options = new ArrayList<>();
		final double walk = walkSeconds(method);
		final String landing = describe(method);
		final boolean house = canTeleportHome();
		final double toHouse = houseTeleportSeconds();
		final double viaHouse = toHouse + (poh.isTeleportOutside() ? TravelTimes.HOUSE_DOOR : 0)
			+ TravelTimes.HOUSE_WALK;
		final String viaHouseText = "house " + round(viaHouse);

		switch (method.getKind())
		{
			case SPELL:
				if (canTeleport(method.getSpell()))
				{
					final double swap = spellbookSwap(method.getSpell());
					options.add(new Option(method, Departure.DIRECT, baseSeconds(method) + walk + swap,
						"cast " + round(baseSeconds(method)) + (swap > 0 ? " + spellbook swap " + round(swap) : "")
							+ " + " + landing));
				}
				break;
			case HOUSE_PORTAL:
				if (house)
				{
					final double door = poh.isTeleportOutside() ? 0 : TravelTimes.HOUSE_DOOR;
					options.add(new Option(method, Departure.DIRECT, toHouse + door + walk,
						"house " + round(toHouse + door) + " + " + landing));
				}
				break;
			case FAIRY_RING:
				if (hasFairyRingStaff())
				{
					if (poh.isFairyRing() && house)
					{
						options.add(new Option(method, Departure.POH_FAIRY_RING, viaHouse + TravelTimes.FAIRY_RING + walk,
							viaHouseText + " + ring " + round(TravelTimes.FAIRY_RING) + " + " + landing));
					}
					// No unnamed "some ring nearby": only rings the player has a known way to reach. The chosen way
					// is a preference: if it can't be used, Auto picks from the rest. The ring by the last stop is
					// always considered, since it needs nothing.
					final FairyRingAccess chosen = config.getFairyRingWay();
					final boolean chosenUsable = chosen != null && reachRing(chosen, from) != null;
					for (FairyRingAccess way : FairyRingAccess.values())
					{
						final boolean allowed = way == FairyRingAccess.NEARBY || !chosenUsable || way == chosen;
						final Double reach = allowed ? reachRing(way, from) : null;
						if (reach != null)
						{
							options.add(new Option(method, Departure.of(way), reach + TravelTimes.FAIRY_RING + walk,
								"to the ring " + round(reach) + " + ring " + round(TravelTimes.FAIRY_RING) + " + " + landing));
						}
					}
				}
				break;
			case SPIRIT_TREE:
				final Integer treeWalk = from == null ? null : spiritTreeWalk(from);
				if (treeWalk != null)
				{
					options.add(new Option(method, Departure.DIRECT,
						treeWalk * SECONDS_PER_TILE + TravelTimes.SPIRIT_TREE + walk, "to the tree " + treeWalk
						+ " tiles " + round(treeWalk * SECONDS_PER_TILE) + " + tree " + round(TravelTimes.SPIRIT_TREE)
						+ " + " + landing));
				}
				if (poh.isSpiritTree() && house)
				{
					options.add(new Option(method, Departure.POH_SPIRIT_TREE, viaHouse + TravelTimes.SPIRIT_TREE + walk,
						viaHouseText + " + tree " + round(TravelTimes.SPIRIT_TREE) + " + " + landing));
				}
				break;
			default:
				if (method.getItem() != null && owns(method.getItem()) && startCharges.hasUseToday(method)
					&& !usedUpToday.contains(method))
				{
					options.add(new Option(method, Departure.DIRECT, baseSeconds(method) + walk,
						"teleport " + round(baseSeconds(method)) + " + " + landing));
				}
				break;
		}

		// From one Varlamore stop to another: back to that stop's landing pad, then Renu (no whistle charge)
		final Integer padWalk = from == null || from == method.getDestination() ? null : TravelTimes.renuPadTiles(from);
		if (method.getKind() == TravelKind.QUETZAL && padWalk != null && renuReady(from))
		{
			options.add(new Option(method, Departure.RENU, padWalk * SECONDS_PER_TILE + TravelTimes.QUETZAL + walk,
				"back to the pad " + padWalk + " tiles " + round(padWalk * SECONDS_PER_TILE) + " + Renu "
					+ round(TravelTimes.QUETZAL) + " + " + landing));
		}

		if (house && method.getNexus() != null && poh.getNexusDestinations().contains(method.getNexus()))
		{
			options.add(new Option(method, Departure.POH_NEXUS, viaHouse + TravelTimes.TELEPORT + walk,
				viaHouseText + " + nexus " + round(TravelTimes.TELEPORT) + " + " + landing));
		}
		if (house && method.getJewelleryBox() != null && poh.getJewelleryBox() != null
			&& poh.getJewelleryBox().includes(method.getJewelleryBox()))
		{
			options.add(new Option(method, Departure.POH_JEWELLERY_BOX, viaHouse + TravelTimes.TELEPORT + walk,
				viaHouseText + " + jewellery box " + round(TravelTimes.TELEPORT) + " + " + landing));
		}
		return options;
	}

	static double baseSeconds(TravelMethod method)
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
				return TravelTimes.QUETZAL;
			case FAIRY_RING:
				return TravelTimes.FAIRY_RING;
			case SPIRIT_TREE:
				return TravelTimes.SPIRIT_TREE;
			default:
				return TravelTimes.ITEM_TELEPORT;
		}
	}

	/** From where the method lands to the patch: the measured walk plus any ride or ladder. */
	private static double walkSeconds(TravelMethod method)
	{
		return TravelTimes.tiles(method) * SECONDS_PER_TILE + TravelTimes.extraSeconds(method);
	}

	/** e.g. "56 tiles 17" or "100 tiles 30 + 40 more": the part of a leg after landing. */
	private static String describe(TravelMethod method)
	{
		final int tiles = TravelTimes.tiles(method);
		final double extra = TravelTimes.extraSeconds(method);
		return tiles + " tiles " + round(tiles * SECONDS_PER_TILE) + (extra > 0 ? " + " + round(extra) + " more" : "");
	}

	private static String round(double seconds)
	{
		return String.valueOf(Math.round(seconds));
	}

	private static String learnedNote(double seconds, double estimate)
	{
		return Math.abs(seconds - estimate) < 0.5 ? "" : " (your times: " + round(seconds) + ")";
	}

	/** Teleport to House by a cape, a tablet, or the spell (switching spellbook first if it's another book). */
	private double houseTeleportSeconds()
	{
		if (owns(TravelItem.CONSTRUCTION_CAPE) || owns(TravelItem.MAX_CAPE))
		{
			return TravelTimes.ITEM_TELEPORT;
		}
		return TravelTimes.TELEPORT + spellbookSwap(Spell.TELEPORT_TO_HOUSE);
	}

	/** Seconds to get to a fairy ring this way, or null if the player can't. */
	private Double reachRing(FairyRingAccess way, Location from)
	{
		final double walk = way.getTiles() * SECONDS_PER_TILE;
		if (way == FairyRingAccess.NEARBY)
		{
			final Integer tiles = from == null ? null : TravelTimes.fairyRingTiles(from);
			return tiles == null ? null : tiles * SECONDS_PER_TILE;
		}
		for (TravelItem item : way.getItems())
		{
			// Owning isn't always enough: the quest point cape also needs every quest done
			if (owns(item) && access.missing(item.getRequirements()).isEmpty())
			{
				return TravelTimes.ITEM_TELEPORT + walk;
			}
		}
		return null;
	}

	/** Renu (Twilight's Promise) can fly from the pad by this stop; Kastori's pad has to be built first. */
	private boolean renuReady(Location from)
	{
		return access.isMet(Requirement.quest(Quest.TWILIGHTS_PROMISE))
			&& (from != Location.KASTORI || access.isMet(Requirement.unlock(Unlock.QUETZAL_KASTORI)));
	}

	private Integer spiritTreeWalk(Location from)
	{
		final Integer tiles = TravelTimes.spiritTreeTiles(from);
		final Unlock planted = TravelTimes.plantedSpiritTree(from);
		if (tiles == null || (planted != null && !access.isMet(Requirement.unlock(planted))))
		{
			return null;
		}
		return tiles;
	}

	/**
	 * Extra time when the spell is on another spellbook and has to be cast after switching at a house altar.
	 * A tablet the player owns needs no switch.
	 */
	private double spellbookSwap(Spell spell)
	{
		final boolean tablet = spell.hasTablet() && holdings.count(spell.getTabletItemId()) > 0;
		if (tablet || access.isOnSpellbook(spell))
		{
			return 0;
		}
		// On Lunar, Spellbook Swap is one more cast; otherwise it's two trips to the house altar
		return access.needsLunarSwap(spell) ? TravelTimes.TELEPORT : SPELLBOOK_SWAP;
	}

	private boolean canTeleport(Spell spell)
	{
		return access.canCast(spell) || (spell.hasTablet() && holdings.count(spell.getTabletItemId()) > 0);
	}

	/** Teleport to House by spell, tablet, construction cape or max cape. */
	boolean canTeleportHome()
	{
		return canTeleport(Spell.TELEPORT_TO_HOUSE) || owns(TravelItem.CONSTRUCTION_CAPE) || owns(TravelItem.MAX_CAPE);
	}

	private boolean hasFairyRingStaff()
	{
		return (access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.ELITE)))
			|| holdings.countAny(SupplyItems.FAIRY_RING_STAFFS) > 0;
	}

	/** What a leg costs besides time, for the Auto (best) styles. */
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

	/** Extra cost of a leg under the chosen Auto (best) style; 0 for Fastest. */
	private double stylePenalty(Leg leg, Location to)
	{
		if (leg.method == null || config.getTravelStyle() == TravelStyle.FASTEST)
		{
			return 0;
		}
		return stylePenalty(config.getTravelStyle(), cost(leg, to));
	}

	static double stylePenalty(TravelStyle style, Cost cost)
	{
		switch (style)
		{
			case PREFER_FREE:
				return cost == Cost.FREE ? 0 : STYLE_PENALTY;
			case SAVE_CHARGES:
				return cost == Cost.CHARGES ? STYLE_PENALTY : 0;
			case FEWEST_ITEMS:
				// A tablet or a charged item is one more thing per stop; runes and the house tab are shared
				return cost == Cost.TABLET || cost == Cost.CHARGES ? STYLE_PENALTY
					: cost == Cost.RUNES || cost == Cost.HOUSE ? STYLE_PENALTY / 3 : 0;
			default:
				return 0;
		}
	}

	private Cost cost(Leg leg, Location to)
	{
		final boolean capeHome = owns(TravelItem.CONSTRUCTION_CAPE) || owns(TravelItem.MAX_CAPE);
		if (leg.departure.isViaHouse() || leg.method.getKind() == TravelKind.HOUSE_PORTAL)
		{
			return capeHome ? Cost.FREE : Cost.HOUSE;
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
			|| ChargeBudget.leftToday(method, holdings) != null;
	}

	/** Held, with a charge to spare, and not already used up earlier in this run. */
	private boolean owns(TravelItem item)
	{
		return holdings.countAny(item.getItemIds()) > 0 && startCharges.hasCharge(item) && !usedUp.contains(item);
	}

	/** The charged item a leg uses up a charge of, or null (spells, walking, the house, unlimited items). */
	private TravelItem itemUsed(Leg leg)
	{
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

	/**
	 * Walk the planned order spending charges, pooled per item; a leg whose item has none left is planned again
	 * without that item (e.g. a slayer ring with one charge covers one stop, not two).
	 */
	private Leg[] spendCharges(List<Location> stops, int[] order, Leg first, Leg[][] legs)
	{
		final ChargeBudget budget = new ChargeBudget(holdings, config.isKeepLastCharge());
		final Leg[] chosen = new Leg[order.length];
		for (int k = 0; k < order.length; k++)
		{
			final Location from = k == 0 ? null : stops.get(order[k - 1]);
			final Location to = stops.get(order[k]);
			Leg leg = k == 0 ? first : legs[order[k - 1]][order[k]];
			while (true)
			{
				final TravelItem item = itemUsed(leg);
				final TravelMethod daily = leg.departure == Departure.DIRECT ? leg.method : null;
				final boolean itemOut = item != null && !budget.hasCharge(item);
				final boolean dailyOut = daily != null && !budget.hasUseToday(daily);
				if (!itemOut && !dailyOut)
				{
					if (item != null)
					{
						budget.spend(item);
					}
					if (daily != null)
					{
						budget.spendUseToday(daily);
					}
					break;
				}
				// Rule out what ran out and plan this stop again; each pass rules out something new, so this ends
				final boolean ruledOut = (itemOut && usedUp.add(item)) | (dailyOut && usedUpToday.add(daily));
				if (!ruledOut)
				{
					// Nothing else gets there: keep it, as something to recharge or come back to tomorrow
					leg = new Leg(leg.method, leg.departure, leg.seconds, true, MISSING_PENALTY,
						leg.breakdown + ", no charges left");
					break;
				}
				leg = bestLeg(from, to);
			}
			chosen[k] = leg;
		}
		return chosen;
	}
}
