package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.MetaOrder;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
	/** Getting to an ordinary fairy ring when the player has none at home. */
	static final int FAIRY_RING_WALK_TILES = 40;
	/** Used when the player doesn't own what a method needs, so owned methods win when close. */
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
		SPIRIT_TREE_WALK.put(Location.GNOME_STRONGHOLD, 20);
		SPIRIT_TREE_WALK.put(Location.TREE_GNOME_VILLAGE, 15);
		SPIRIT_TREE_WALK.put(Location.FARMING_GUILD, 25);
		SPIRIT_TREE_WALK.put(Location.BRIMHAVEN, 10);
		SPIRIT_TREE_WALK.put(Location.FALADOR_FARM, 40);
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

	private RoutePlanner(RunConfig config, AccessSnapshot access, Holdings holdings, PohSetup poh,
		LearnedTimes learned)
	{
		this.config = config;
		this.access = access;
		this.holdings = holdings;
		this.poh = poh;
		this.learned = learned;
	}

	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh)
	{
		return plan(patches, config, access, holdings, poh, LearnedTimes.NONE);
	}

	/**
	 * @param patches the patches in this run
	 * @param learned the player's recorded leg times
	 */
	public static Route plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, LearnedTimes learned)
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
		return new RoutePlanner(config, access, holdings, poh, learned).plan(stops, patches.size());
	}

	private Route plan(List<Location> stops, int patchCount)
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
		final int start = stops.indexOf(config.getStartLocation());

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

		final List<RouteStop> result = new ArrayList<>();
		double travel = 0;
		for (int k = 0; k < order.length; k++)
		{
			final Leg leg = k == 0 ? firstLeg[order[0]] : legs[order[k - 1]][order[k]];
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

	private static int bankTiles(Location location)
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
	 * The quickest way from {@code from} (null at the start of the run) to {@code to}: the player's chosen
	 * method if set, otherwise every unlocked method, directly or through the house. Methods the player
	 * lacks the item or tablet for only win if nothing else is possible.
	 */
	Leg bestLeg(Location from, Location to)
	{
		final TravelMethod chosen = config.getTravel().get(to);
		final List<TravelMethod> candidates = new ArrayList<>();
		if (chosen != null && access.missingFor(chosen).isEmpty())
		{
			candidates.add(chosen);
		}
		else
		{
			for (TravelMethod method : TravelMethod.values())
			{
				if (method.getDestination() == to && access.missingFor(method).isEmpty())
				{
					candidates.add(method);
				}
			}
		}

		Leg best = null;
		if (chosen == null && from != null)
		{
			final Integer tiles = WALKS.getOrDefault(from, new EnumMap<>(Location.class)).get(to);
			if (tiles != null)
			{
				best = new Leg(null, Departure.WALK, tiles * SECONDS_PER_TILE, false);
			}
		}
		for (TravelMethod method : candidates)
		{
			for (Leg leg : options(method, from))
			{
				// Teleports carry the walking preference, so a walk that's only a little slower wins
				final double seconds = learned.adjust(to, leg.method, leg.departure, leg.seconds);
				final Leg scored = new Leg(leg.method, leg.departure, seconds, leg.needsSupplies,
					seconds + teleportPreference());
				if (best == null || scored.cost < best.cost)
				{
					best = scored;
				}
			}
		}
		if (best != null)
		{
			return best;
		}

		// Nothing usable: take the quickest unlocked method anyway and let the supply list ask for it.
		for (TravelMethod method : candidates)
		{
			final double seconds = baseSeconds(method) + walkSeconds(method) + MISSING_PENALTY;
			if (best == null || seconds < best.seconds)
			{
				best = new Leg(method, Departure.DIRECT, seconds, true);
			}
		}
		return best != null ? best : new Leg(null, Departure.NONE, MISSING_PENALTY * 5, false);
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
					legs.add(direct(method, FAIRY_RING_WALK_TILES * SECONDS_PER_TILE + FAIRY_RING + walk));
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
				if (method.getItem() != null && owns(method.getItem()))
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
		return method.getWalk().getEstimatedTiles() * SECONDS_PER_TILE;
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
		return tablet || access.isOnSpellbook(spell) ? 0 : SPELLBOOK_SWAP;
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

	private boolean owns(TravelItem item)
	{
		return holdings.countAny(item.getItemIds()) > 0;
	}
}
