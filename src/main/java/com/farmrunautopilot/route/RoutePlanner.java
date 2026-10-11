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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orders the stops and picks how to reach each one (SPEC 12).
 *
 * <p>Leg times are rough starting estimates (UNVERIFIED): a teleport takes about 3 seconds, running about
 * 0.3 seconds a tile, and walk distances come from the travel catalogue. M8 replaces them with the player's
 * own timings.
 */
public final class RoutePlanner
{
	// Starting estimates, in seconds (UNVERIFIED; replaced by learned timings in M8)
	static final double SECONDS_PER_TILE = 0.3;
	/** With "prefer walking" on, a walk up to this much slower than a teleport is chosen instead. */
	static final double PREFER_WALKING_SECONDS = 20.0;
	static final double TELEPORT = 3.0;
	static final double ITEM_TELEPORT = 3.6;
	static final double HOME_TELEPORT = 10.5;
	static final double QUETZAL = 6.0;
	static final double FAIRY_RING = 4.0;
	static final double SPIRIT_TREE = 4.5;
	/** Walking out of the house to its portal, or into it from outside. */
	static final double HOUSE_DOOR = 5.0;
	/** Walking from the house's arrival point to the nexus, jewellery box, fairy ring or spirit tree. */
	static final double HOUSE_WALK = 2.0;
	/** Switching spellbook at a house altar and back again later (two trips to the altar). */
	static final double SPELLBOOK_SWAP = 2 * (TELEPORT + HOUSE_WALK + 3.0);
	/** Used when the player doesn't own what a method needs, so owned methods win when close. */
	/**
	 * Every leg but a walk between neighbouring stops: finishing the last patch, opening the menu or spellbook,
	 * the animation and the load. Recorded legs ran two to three times the old estimates without it.
	 */
	static final double LEG_OVERHEAD = 8;
	static final double MISSING_PENALTY = 60;
	/** Rough time at each patch (harvest, clear, plant, pay). */
	static final double SECONDS_PER_PATCH = 20;
	/** Walk to a bank from locations without one nearby. */
	static final int NO_BANK_TILES = 70;
	/** Above this many stops, use a fast near-optimal search instead of an exact one. */
	static final int EXACT_LIMIT = 14;

	/** Spirit trees close to these stops, and the walk to them (UNVERIFIED). */
	private static final Map<Location, Integer> SPIRIT_TREE_WALK = new EnumMap<>(Location.class);
	/** Unlocks needed for player-planted spirit trees near a stop. */
	private static final Map<Location, Unlock> PLANTED_SPIRIT_TREES = new EnumMap<>(Location.class);
	/** Stops close enough to walk between, and the distance in tiles (UNVERIFIED). */
	private static final Map<Location, Map<Location, Integer>> WALKS = new EnumMap<>(Location.class);

	static
	{
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
		walk(Location.FALADOR_PARK, Location.FALADOR_FARM, 75);
		// Out of Falador's west gate and up through Taverley's south gate (estimated), so one Falador Teleport covers both
		walk(Location.FALADOR_PARK, Location.TAVERLEY, 100);
	}

	private static void walk(Location a, Location b, int tiles)
	{
		WALKS.computeIfAbsent(a, k -> new EnumMap<>(Location.class)).put(b, tiles);
		WALKS.computeIfAbsent(b, k -> new EnumMap<>(Location.class)).put(a, tiles);
	}

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
		return new RouteStop(to, leg.method, leg.departure, leg.seconds, leg.needsSupplies);
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
		return new RoutePlanner(config, access, holdings, poh, learned).plan(stops, patches.size(), here,
			config.isEndNearBank(), true, null);
	}

	/**
	 * One half of a run with a bank stop between the halves.
	 *
	 * @param here first half: the stop the player stands at, or null; second half: the bank stop it starts from
	 * @param first the first half: starts at the start location and ends next to a bank; the second half
	 *              starts from the bank stop (so a nearby spirit tree or walk still counts) and ends as the
	 *              settings say
	 */
	static Route planHalf(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, LearnedTimes learned, Location here, boolean first)
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
		return new RoutePlanner(config, access, holdings, poh, learned).plan(stops, patches.size(),
			first ? here : null, first || config.isEndNearBank(), first, first ? null : here);
	}

	private Route plan(List<Location> stops, int patchCount, Location here, boolean endNearBank,
		boolean fromStartLocation, Location departFrom)
	{
		final int n = stops.size();
		final Leg[] firstLeg = new Leg[n];
		final Leg[][] legs = new Leg[n][n];
		for (int j = 0; j < n; j++)
		{
			// From anywhere, or onward from where the run already is (a bank stop) so nearby chains still count
			firstLeg[j] = bestLeg(departFrom, stops.get(j));
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
			endCost[j] = endNearBank ? bankTiles(stops.get(j)) * SECONDS_PER_TILE : 0;
		}
		// Already standing at a stop: start there, with nothing to travel or bring for it
		final int standingAt = here == null ? -1 : stops.indexOf(here);
		if (standingAt >= 0)
		{
			firstLeg[standingAt] = new Leg(null, Departure.WALK, 0, false);
		}
		final int start = standingAt >= 0 ? standingAt
			: fromStartLocation ? stops.indexOf(config.getStartLocation()) : -1;

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
			result.add(new RouteStop(stops.get(order[k]), leg.method, leg.departure, leg.seconds, leg.needsSupplies));
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

	/** Fast near-optimal order for many stops: nearest neighbour, then swap pairs while it helps. */
	static int[] nearestNeighbourThenImprove(Leg[] firstLeg, Leg[][] legs, double[] endCost, int start)
	{
		final int n = firstLeg.length;
		final int[] order = new int[n];
		final boolean[] used = new boolean[n];
		int current = start;
		if (current < 0)
		{
			current = 0;
			for (int j = 1; j < n; j++)
			{
				if (firstLeg[j].cost < firstLeg[current].cost)
				{
					current = j;
				}
			}
		}
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

		// 2-opt style improvement on the full (asymmetric) route cost; the start stays first if fixed
		boolean improved = true;
		while (improved)
		{
			improved = false;
			for (int i = start < 0 ? 0 : 1; i < n - 1; i++)
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
		}
		return order;
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
		return location.getBankWalk() != null ? location.getBankWalk().getEstimatedTiles() : NO_BANK_TILES;
	}

	// Legs

	/** How one leg is travelled and what it costs. */
	static final class Leg
	{
		final TravelMethod method;
		final Departure departure;
		/** Estimated time, as shown to the player. */
		final double seconds;
		final boolean needsSupplies;
		/** What the route ordering minimises: the time plus any preference against teleporting. */
		final double cost;

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies)
		{
			this(method, departure, seconds, needsSupplies, seconds);
		}

		Leg(TravelMethod method, Departure departure, double seconds, boolean needsSupplies, double cost)
		{
			this.method = method;
			this.departure = departure;
			this.seconds = seconds;
			this.needsSupplies = needsSupplies;
			this.cost = cost;
		}
	}

	/** Extra cost on each teleport when the player prefers walking: saves charges and clicks for little time. */
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
		if (from != null)
		{
			final Integer tiles = WALKS.getOrDefault(from, new EnumMap<>(Location.class)).get(to);
			if (tiles != null)
			{
				walk = new Leg(null, Departure.WALK, tiles * SECONDS_PER_TILE, false);
			}
		}
		final Leg best = quickest(candidates, from, to, walk);
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
			final double seconds = baseSeconds(method) + walkSeconds(method) + MISSING_PENALTY;
			final Leg leg = new Leg(method, Departure.DIRECT, seconds, true);
			if (method == chosen)
			{
				return leg;
			}
			if (fallback == null || seconds < fallback.seconds)
			{
				fallback = leg;
			}
		}
		return fallback != null ? fallback : new Leg(null, Departure.NONE, MISSING_PENALTY * 5, false);
	}

	/** The cheapest usable leg using these methods (or {@code best}, e.g. a walk), or null if there's none. */
	private Leg quickest(List<TravelMethod> methods, Location from, Location to, Leg best)
	{
		return quickest(methods, from, to, best, null);
	}

	/** @param how only legs that start this way (e.g. through the nexus), or null for any */
	private Leg quickest(List<TravelMethod> methods, Location from, Location to, Leg best, Departure how)
	{
		for (TravelMethod method : methods)
		{
			for (Leg leg : options(method, from))
			{
				if (how != null && leg.departure != how)
				{
					continue;
				}
				// Teleports carry the walking preference, so a walk that's only a little slower wins. The player's
				// own times include the overhead, so the estimate they're blended with does too
				final double seconds = learned.adjust(to, leg.method, leg.departure, leg.seconds + LEG_OVERHEAD);
				final Leg scored = new Leg(leg.method, leg.departure, seconds, leg.needsSupplies,
					seconds + teleportPreference() + stylePenalty(leg, to));
				if (best == null || scored.cost < best.cost)
				{
					best = scored;
				}
			}
		}
		return best;
	}

	/** Every usable way of travelling with this method from here. */
	private List<Leg> options(TravelMethod method, Location from)
	{
		final List<Leg> legs = new ArrayList<>();
		final double walk = walkSeconds(method);
		final boolean house = canTeleportHome();
		final double viaHouse = TELEPORT + (poh.isTeleportOutside() ? HOUSE_DOOR : 0) + HOUSE_WALK;

		switch (method.getKind())
		{
			case SPELL:
				if (canTeleport(method.getSpell()))
				{
					legs.add(direct(method, baseSeconds(method) + walk + spellbookSwap(method.getSpell())));
				}
				break;
			case HOUSE_PORTAL:
				if (house)
				{
					legs.add(direct(method, TELEPORT + (poh.isTeleportOutside() ? 0 : HOUSE_DOOR) + walk));
				}
				break;
			case FAIRY_RING:
				if (hasFairyRingStaff())
				{
					if (poh.isFairyRing() && house)
					{
						legs.add(new Leg(method, Departure.POH_FAIRY_RING, viaHouse + FAIRY_RING + walk, false));
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
							legs.add(new Leg(method, Departure.of(way), reach + FAIRY_RING + walk, false));
						}
					}
				}
				break;
			case SPIRIT_TREE:
				final Integer treeWalk = from == null ? null : spiritTreeWalk(from);
				if (treeWalk != null)
				{
					legs.add(direct(method, treeWalk * SECONDS_PER_TILE + SPIRIT_TREE + walk));
				}
				if (poh.isSpiritTree() && house)
				{
					legs.add(new Leg(method, Departure.POH_SPIRIT_TREE, viaHouse + SPIRIT_TREE + walk, false));
				}
				break;
			default:
				if (method.getItem() != null && owns(method.getItem()) && startCharges.hasUseToday(method)
					&& !usedUpToday.contains(method))
				{
					legs.add(direct(method, baseSeconds(method) + walk));
				}
				break;
		}

		if (house && method.getNexus() != null && poh.getNexusDestinations().contains(method.getNexus()))
		{
			legs.add(new Leg(method, Departure.POH_NEXUS, viaHouse + TELEPORT + walk, false));
		}
		if (house && method.getJewelleryBox() != null && poh.getJewelleryBox() != null
			&& poh.getJewelleryBox().includes(method.getJewelleryBox()))
		{
			legs.add(new Leg(method, Departure.POH_JEWELLERY_BOX, viaHouse + TELEPORT + walk, false));
		}
		return legs;
	}

	private static Leg direct(TravelMethod method, double seconds)
	{
		return new Leg(method, Departure.DIRECT, seconds, false);
	}

	static double baseSeconds(TravelMethod method)
	{
		switch (method.getKind())
		{
			case SPELL:
				return method.getSpell() == Spell.LUMBRIDGE_HOME_TELEPORT ? HOME_TELEPORT : TELEPORT;
			case TABLET:
			case HOUSE_PORTAL:
				return TELEPORT;
			case QUETZAL:
				return QUETZAL;
			case FAIRY_RING:
				return FAIRY_RING;
			case SPIRIT_TREE:
				return SPIRIT_TREE;
			default:
				return ITEM_TELEPORT;
		}
	}

	private static double walkSeconds(TravelMethod method)
	{
		// Measured where researched (TravelTimes), otherwise the rough Walk size; plus any ride or ladder
		final Integer tiles = TravelTimes.tiles(method);
		return (tiles != null ? tiles : method.getWalk().getEstimatedTiles()) * SECONDS_PER_TILE
			+ TravelTimes.extraSeconds(method);
	}

	/** Seconds to get to a fairy ring this way, or null if the player can't. */
	private Double reachRing(FairyRingAccess way, Location from)
	{
		final double walk = way.getTiles() * SECONDS_PER_TILE;
		if (way == FairyRingAccess.NEARBY)
		{
			final Integer tiles = from == null ? null : ringWalkTiles(from);
			return tiles == null ? null : tiles * SECONDS_PER_TILE;
		}
		for (TravelItem item : way.getItems())
		{
			// Owning isn't always enough: the quest point cape also needs every quest done
			if (owns(item) && access.missing(item.getRequirements()).isEmpty())
			{
				return ITEM_TELEPORT + walk;
			}
		}
		return null;
	}

	/**
	 * How far the fairy ring by a stop is from its patches: the walk the other way when arriving there by
	 * ring. Null if the stop has no fairy ring.
	 */
	static Integer ringWalkTiles(Location location)
	{
		Integer tiles = null;
		for (TravelMethod method : TravelMethod.values())
		{
			if (method.getKind() == TravelKind.FAIRY_RING && method.getDestination() == location)
			{
				final int walk = method.getWalk().getEstimatedTiles();
				tiles = tiles == null ? walk : Math.min(tiles, walk);
			}
		}
		return tiles;
	}

	private Integer spiritTreeWalk(Location from)
	{
		final Integer tiles = SPIRIT_TREE_WALK.get(from);
		final Unlock planted = PLANTED_SPIRIT_TREES.get(from);
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
		return access.needsLunarSwap(spell) ? TELEPORT : SPELLBOOK_SWAP;
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
					leg = new Leg(leg.method, leg.departure, leg.seconds, true);
					break;
				}
				leg = bestLeg(from, to);
			}
			chosen[k] = leg;
		}
		return chosen;
	}
}
