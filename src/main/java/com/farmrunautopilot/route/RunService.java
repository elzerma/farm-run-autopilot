package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.run.RunTimings;
import com.farmrunautopilot.run.StepAdvisor;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.tracking.PatchTracker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

/**
 * Works out the next run on the client thread, at most once per game tick, when something it depends on
 * changes: which patches (SPEC 10), the route (SPEC 12) and the supplies (SPEC 11).
 */
@Slf4j
@Singleton
public class RunService
{
	/** Patches become due as they grow, so recalculate about once a minute regardless. */
	private static final int REFRESH_TICKS = 100;
	/** Within this many tiles of a patch counts as standing at its stop. */
	private static final int STANDING_TILES = 25;
	/** A stop whose patch is within this many tiles can be the first stop by walking there. */
	private static final int NEARBY_TILES = 120;

	private final Client client;
	private final ItemManager itemManager;
	private final SettingsStore settings;
	private final AccessChecker accessChecker;
	private final PatchTracker patchTracker;
	private final HoldingsTracker holdingsTracker;
	private final RunOverrides overrides;
	private final RunTimings timings;
	/** Item names are fixed, so look each up once. */
	private final Map<Integer, String> names = new HashMap<>();

	private volatile boolean dirty = true;
	/** Null until the first plan after login, so that one is always published. */
	private volatile RunPlan plan;
	private volatile Map<Location, TravelPick> autoPicks = new EnumMap<>(Location.class);
	private int ticksSinceRefresh;
	/** The stop the player was standing at on the last tick, or null. */
	private Location here;
	/** The stop the player is close to but not at, and how far (tiles); null if none. */
	private Location near;
	private int nearTiles;

	@Inject
	RunService(Client client, ItemManager itemManager, SettingsStore settings, AccessChecker accessChecker,
		PatchTracker patchTracker, HoldingsTracker holdingsTracker, RunOverrides overrides, RunTimings timings)
	{
		this.timings = timings;
		this.client = client;
		this.itemManager = itemManager;
		this.settings = settings;
		this.accessChecker = accessChecker;
		this.patchTracker = patchTracker;
		this.holdingsTracker = holdingsTracker;
		this.overrides = overrides;
	}

	public RunPlan getPlan()
	{
		final RunPlan current = plan;
		return current != null ? current : RunPlan.EMPTY;
	}

	public SupplyPlan getSupplies()
	{
		return getPlan().getSupplies();
	}

	/** Forget the last plan (login, account switch) so the next one is always published. Any thread. */
	public void reset()
	{
		plan = null;
		dirty = true;
	}

	/** Recalculate on the next game tick. Any thread. */
	public void markDirty()
	{
		dirty = true;
	}

	/**
	 * @return whether a new plan was made
	 */
	public boolean onGameTick()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		final boolean holdingsChanged = holdingsTracker.rebuildIfDirty();
		final Location nowAt = standingAt();
		final Location nearNow = nearby();
		final boolean moved = nowAt != here || nearNow != near;
		here = nowAt;
		near = nearNow;
		if (moved)
		{
			log.debug("Route start: at {}, near {} ({} tiles)", here, near, nearTiles);
		}
		if (!holdingsChanged && !moved && !dirty && ++ticksSinceRefresh < REFRESH_TICKS)
		{
			return false;
		}
		dirty = false;
		ticksSinceRefresh = 0;

		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final Holdings holdings = holdingsTracker.getHoldings();
		final RunSelection selection = RunSelector.select(config, access, patchTracker::predict,
			Instant.now().getEpochSecond(), config.isSupplyFullRun(), overrides.get());
		Route route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
			settings.getAccount().getPoh(), timings.learned(), here, near, nearTiles);
		SupplyPlan supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
			patchTracker::predict, this::itemName, itemManager::getItemPrice);
		if (moved && !route.getStops().isEmpty())
		{
			log.debug("Route planned, starting at {}", route.getStops().get(0).getLocation());
		}
		// Too much for one inventory: bank partway
		final Split split = supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS
			? split(config, access, holdings, selection, route) : null;
		final BankStop bankStop = split != null ? split.bankStop : null;
		if (split != null)
		{
			log.debug("Route split: {} after {}, starting at {}", split.bankStop.how(), split.bankStop.getLocation(),
				split.route.getStops().get(0).getLocation());
			route = split.route;
			// The guide needs every patch's crop, not only the first half's
			final Map<Patch, Crop> plantings = new EnumMap<>(Patch.class);
			plantings.putAll(split.before.getPlantings());
			plantings.putAll(split.bankStop.getSupplies().getPlantings());
			final SupplyPlan before = split.before;
			supplies = new SupplyPlan(before.getLines(), before.getPatchCounts(), before.getNotDue(),
				before.getTravelPlan(), before.getWarnings(), before.getCoins(), before.getRuneSummary(),
				before.getSlots(), Collections.unmodifiableMap(plantings));
		}

		final Map<Location, List<String>> objectives = new EnumMap<>(Location.class);
		for (Patch patch : selection.getPatches())
		{
			objectives.computeIfAbsent(patch.getLocation(), k -> new ArrayList<>()).add(StepAdvisor.objectives(
				patch, patchTracker.predict(patch), supplies.getPlantings().get(patch)));
		}

		// What Auto (best) picks for every location, from anywhere, for the Travel tab
		final Map<Location, TravelPick> picks = new EnumMap<>(Location.class);
		for (Location location : Location.values())
		{
			final RouteStop pick = RoutePlanner.autoPick(location, config, access, holdings,
				settings.getAccount().getPoh(), timings.learned());
			final boolean tablet = pick.getMethod() != null && pick.getMethod().getSpell() != null
				&& SupplyCalculator.usesTablet(pick.getMethod().getSpell(), location, config, access, holdings);
			picks.put(location, new TravelPick(pick, tablet));
		}
		final boolean picksChanged = !picks.equals(autoPicks);
		autoPicks = picks;

		final RunPlan next = new RunPlan(selection, route, supplies, objectives, bankStop);
		if (next.equals(plan) && !picksChanged)
		{
			return false;
		}
		plan = next;
		return true;
	}

	/** A run split by a bank stop: the whole route, what to bring before the bank, and the stop itself. */
	private static final class Split
	{
		final Route route;
		final SupplyPlan before;
		final BankStop bankStop;
		final double seconds;

		Split(Route route, SupplyPlan before, BankStop bankStop, double travelSeconds, double bankSeconds)
		{
			this.route = route;
			this.before = before;
			this.bankStop = bankStop;
			// Travel plus getting to the bank, so a far-off bank (Port Phasmatys) counts against it
			this.seconds = travelSeconds + bankSeconds;
		}
	}

	/**
	 * How to bank partway through a run too big for one inventory. Either the planned order is cut at the stop
	 * whose bank adds the least walking, among cuts where both halves fit, so patch types stay mixed (e.g. the
	 * Stronghold, its bank, then the spirit tree to Tree Gnome Village); or, with the fastest route, the run is
	 * planned as two halves by patch type (e.g. trees and fruit trees, bank, herbs), each ending near a bank.
	 * The quickest of these, counting the walk to the bank, wins. Null if no split fits: the plan then warns as
	 * before.
	 */
	private Split split(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Route route)
	{
		Split best = splitInOrder(config, access, holdings, selection, route);
		if (config.getRouteMode() == RouteMode.AUTOPILOT)
		{
			final PatchType[] types = PatchType.values();
			// Every way to put some patch types first: each non-empty, proper subset
			for (int mask = 1; mask < (1 << types.length) - 1; mask++)
			{
				final Set<PatchType> firstTypes = EnumSet.noneOf(PatchType.class);
				for (int t = 0; t < types.length; t++)
				{
					if ((mask & (1 << t)) != 0)
					{
						firstTypes.add(types[t]);
					}
				}
				final Split split = splitByType(config, access, holdings, selection, firstTypes);
				if (split != null && (best == null || split.seconds < best.seconds))
				{
					best = split;
				}
			}
		}
		return best;
	}

	/**
	 * The stop a run must start at: the one the player stands at, else the chosen Start at, else one they're
	 * near; null when it may start anywhere.
	 */
	static Location fixedStart(Location here, Location chosen, Location near)
	{
		return here != null ? here : chosen != null ? chosen : near;
	}

	/**
	 * The stops in the first half of a split by type: those with any of these patch types (a stop is only
	 * visited once). Null if the run's fixed start would land in the second half, so a split never moves it.
	 */
	static Set<Location> firstHalfStops(List<Patch> patches, Set<PatchType> firstTypes, Location start)
	{
		final Set<Location> firstAt = EnumSet.noneOf(Location.class);
		final Set<Location> all = EnumSet.noneOf(Location.class);
		for (Patch patch : patches)
		{
			all.add(patch.getLocation());
			if (firstTypes.contains(patch.getType()))
			{
				firstAt.add(patch.getLocation());
			}
		}
		return start != null && all.contains(start) && !firstAt.contains(start) ? null : firstAt;
	}

	/**
	 * First the stops with any of these patch types, then a bank, then the rest. A first-half stop that the bank
	 * stop chains to (e.g. the Stronghold bank, then its spirit tree to Tree Gnome Village) may move to just after
	 * the bank, carried in the second inventory, when that's quicker and both still fit.
	 */
	private Split splitByType(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Set<PatchType> firstTypes)
	{
		final Location start = fixedStart(here, config.getStartLocation(), near);
		final Set<Location> firstAt = firstHalfStops(selection.getPatches(), firstTypes, start);
		if (firstAt == null)
		{
			return null;
		}
		Split best = splitAt(config, access, holdings, selection, firstAt);
		if (best == null)
		{
			return null;
		}
		final Location bankAt = best.bankStop.getLocation();
		final PohSetup poh = settings.getAccount().getPoh();
		for (Location chained : firstAt)
		{
			if (chained == start || chained == bankAt
				|| !RoutePlanner.chainsFrom(bankAt, chained, config, access, holdings, poh, timings.learned()))
			{
				continue;
			}
			final Set<Location> fewer = EnumSet.copyOf(firstAt);
			fewer.remove(chained);
			final Split moved = splitAt(config, access, holdings, selection, fewer);
			if (moved != null && moved.seconds < best.seconds)
			{
				best = moved;
			}
		}
		return best;
	}

	/** The stops in firstAt, then a bank, then the rest; null if either half doesn't fit one inventory. */
	private Split splitAt(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Set<Location> firstAt)
	{
		final List<Patch> first = new ArrayList<>();
		final List<Patch> second = new ArrayList<>();
		for (Patch patch : selection.getPatches())
		{
			(firstAt.contains(patch.getLocation()) ? first : second).add(patch);
		}
		if (first.isEmpty() || second.isEmpty())
		{
			return null;
		}
		final PohSetup poh = settings.getAccount().getPoh();
		final Route route1 = RoutePlanner.planHalf(first, config, access, holdings, poh, timings.learned(), here, true,
			near, nearTiles);
		final SupplyPlan before = supplies(config, access, holdings, selection, first, route1);
		if (before.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
		{
			return null;
		}
		final Route route2 = RoutePlanner.planHalf(second, config, access, holdings, poh, timings.learned(),
			// Onward from the bank stop, so e.g. the Stronghold bank then its spirit tree to Tree Gnome Village counts
			route1.getStops().get(route1.getStops().size() - 1).getLocation(),
			false, null, 0);
		final SupplyPlan after = supplies(config, access, holdings, selection, second, route2);
		if (after.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
		{
			return null;
		}
		final List<RouteStop> stops = new ArrayList<>(route1.getStops());
		stops.addAll(route2.getStops());
		final Location bankAt = route1.getStops().get(route1.getStops().size() - 1).getLocation();
		final Route whole = new Route(stops, route1.getMode(), route1.getTravelSeconds() + route2.getTravelSeconds(),
			route1.getPatchSeconds() + route2.getPatchSeconds());
		return new Split(whole, before, new BankStop(route1.getStops().size() - 1, bankAt, after, null),
			whole.getTravelSeconds(), RoutePlanner.bankTiles(bankAt) * RoutePlanner.SECONDS_PER_TILE);
	}

	/**
	 * Cut the planned order where banking costs the least, among cuts where both halves fit: the walk to the bank
	 * near the stop just finished, or the player's quickest bank teleport (carried from the start) plus whatever
	 * the next leg loses by setting off from the bank instead of that stop (e.g. the Stronghold's spirit tree).
	 */
	private Split splitInOrder(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Route route)
	{
		final PohSetup poh = settings.getAccount().getPoh();
		final BankTeleport teleport = BankTeleport.best(access, holdings);
		final List<RouteStop> stops = route.getStops();
		Split best = null;
		for (int k = 1; k < stops.size(); k++)
		{
			final Location bankAt = stops.get(k - 1).getLocation();
			final List<RouteStop> head = stops.subList(0, k);
			final SupplyPlan before = supplies(config, access, holdings, selection, patchesAt(selection, head),
				new Route(new ArrayList<>(head), route.getMode(), 0, 0));
			if (before.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
			{
				// Banking later only makes the first half bigger
				break;
			}
			final List<RouteStop> tail = stops.subList(k, stops.size());
			final SupplyPlan after = supplies(config, access, holdings, selection, patchesAt(selection, tail),
				new Route(new ArrayList<>(tail), route.getMode(), 0, 0));
			if (after.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
			{
				continue;
			}
			final double walk = RoutePlanner.bankTiles(bankAt) * RoutePlanner.SECONDS_PER_TILE;
			final Location next = stops.get(k).getLocation();
			final double byTeleport = teleport == null || before.getSlots() + 1 > SupplyCalculator.INVENTORY_SLOTS
				? Double.MAX_VALUE
				: teleport.getSeconds() + RoutePlanner.chainSaving(bankAt, next, config, access, holdings, poh,
					timings.learned());
			final Split split;
			if (byTeleport < walk)
			{
				// Set off for the next stop from the bank, not from the stop just finished
				final List<RouteStop> onward = new ArrayList<>(stops);
				onward.set(k, RoutePlanner.fromAnywhere(next, config, access, holdings, poh, timings.learned()));
				split = new Split(new Route(onward, route.getMode(), route.getTravelSeconds(), route.getPatchSeconds()),
					withTeleport(before, teleport, holdings), new BankStop(k - 1, bankAt, after, teleport),
					route.getTravelSeconds(), byTeleport);
			}
			else
			{
				split = new Split(route, before, new BankStop(k - 1, bankAt, after, null), route.getTravelSeconds(),
					walk);
			}
			if (best == null || split.seconds < best.seconds)
			{
				best = split;
			}
		}
		return best;
	}

	/** The first half's supplies with the bank teleport to bring. */
	private static SupplyPlan withTeleport(SupplyPlan before, BankTeleport teleport, Holdings holdings)
	{
		final int[] ids = teleport.getItemIds();
		final List<SupplyLine> lines = new ArrayList<>(before.getLines());
		lines.add(new SupplyLine(SupplyLine.Group.TRAVEL, teleport.getItemName() + " (bank partway)", 1,
			holdings.countAny(ids), holdings.carriedOnly().countAny(ids), holdings.where(ids),
			"To bank partway through the run: teleport to " + teleport.getBankName(), 1, false, null, ids));
		return new SupplyPlan(lines, before.getPatchCounts(), before.getNotDue(), before.getTravelPlan(),
			before.getWarnings(), before.getCoins(), before.getRuneSummary(), before.getSlots() + 1,
			before.getPlantings());
	}

	private static List<Patch> patchesAt(RunSelection selection, List<RouteStop> stops)
	{
		final Set<Location> at = EnumSet.noneOf(Location.class);
		for (RouteStop stop : stops)
		{
			at.add(stop.getLocation());
		}
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : selection.getPatches())
		{
			if (at.contains(patch.getLocation()))
			{
				patches.add(patch);
			}
		}
		return patches;
	}

	/** The supplies for some of the run's patches on their own. */
	private SupplyPlan supplies(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		List<Patch> patches, Route route)
	{
		final RunSelection part = new RunSelection(patches, selection.getIncludedTypes(),
			selection.getSkippedTypes(), Collections.emptyList(), selection.getDueCounts());
		return SupplyCalculator.calculate(config, access, holdings, part, route, patchTracker::predict,
			this::itemName, itemManager::getItemPrice);
	}

	/** Auto (best)'s pick for each location, from anywhere. Any thread. */
	public Map<Location, TravelPick> getAutoPicks()
	{
		return autoPicks;
	}

	/**
	 * Whether a spell is taken from its tablet at this location, by the same rule as the supply list. For
	 * labelling a teleport the player picked. Any thread.
	 */
	public boolean usesTablet(Spell spell, Location location)
	{
		return SupplyCalculator.usesTablet(spell, location, settings.getRunConfig(), accessChecker.getSnapshot(),
			holdingsTracker.getHoldings());
	}

	/** The stop the player is standing at (near one of its patches), or null. Client thread. */
	/**
	 * The stop whose patch is closest to the player, if within {@link #NEARBY_TILES}, so a run can start by
	 * walking there (e.g. from the Grand Tree to the Stronghold patches); sets {@link #nearTiles}. Another floor
	 * (the Grand Tree bank is upstairs) counts as a few tiles further.
	 */
	private Location nearby()
	{
		final Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		final WorldPoint location = player.getWorldLocation();
		Location best = null;
		int bestTiles = Integer.MAX_VALUE;
		for (Map.Entry<Patch, WorldPoint> e : PatchPoints.all().entrySet())
		{
			final WorldPoint point = e.getValue();
			final int tiles = point.distanceTo2D(location) + (point.getPlane() != location.getPlane() ? 10 : 0);
			if (tiles <= NEARBY_TILES && tiles < bestTiles)
			{
				best = e.getKey().getLocation();
				bestTiles = tiles;
			}
		}
		nearTiles = best != null ? bestTiles : 0;
		return best;
	}

	private Location standingAt()
	{
		final Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		final WorldPoint location = player.getWorldLocation();
		for (Map.Entry<Patch, WorldPoint> e : PatchPoints.all().entrySet())
		{
			final WorldPoint point = e.getValue();
			if (point.getPlane() == location.getPlane() && point.distanceTo2D(location) <= STANDING_TILES)
			{
				return e.getKey().getLocation();
			}
		}
		return null;
	}

	/** The noted version of an item (or the item itself if it has none). Client thread. */
	public int notedId(int itemId)
	{
		final int noted = itemManager.getItemComposition(itemId).getLinkedNoteId();
		return noted > 0 ? noted : itemId;
	}

	/** An item's name, looked up once. Client thread. */
	public String itemName(int itemId)
	{
		return names.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).getName());
	}
}
