package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.PatchType;
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
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

/**
 * Works out the next run on the client thread, at most once per game tick, when something it depends on
 * changes: which patches (SPEC 10), the route (SPEC 12) and the supplies (SPEC 11).
 */
@Singleton
public class RunService
{
	/** Patches become due as they grow, so recalculate about once a minute regardless. */
	private static final int REFRESH_TICKS = 100;
	/** Within this many tiles of a patch counts as standing at its stop. */
	private static final int STANDING_TILES = 25;

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
		final boolean moved = nowAt != here;
		here = nowAt;
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
			settings.getAccount().getPoh(), timings.learned(), here);
		SupplyPlan supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
			patchTracker::predict, this::itemName, itemManager::getItemPrice);
		// Too much for one inventory: bank partway
		final Split split = supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS
			? split(config, access, holdings, selection, route) : null;
		final BankStop bankStop = split != null ? split.bankStop : null;
		if (split != null)
		{
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

		Split(Route route, SupplyPlan before, BankStop bankStop, double seconds)
		{
			this.route = route;
			this.before = before;
			this.bankStop = bankStop;
			this.seconds = seconds;
		}
	}

	/**
	 * How to bank partway through a run too big for one inventory. With the fastest route, the run is planned
	 * as two halves by patch type (e.g. trees and fruit trees, bank, herbs), so each half carries fewer kinds
	 * of item, and the first half ends next to a bank; the quickest split where both halves fit wins. Otherwise
	 * (or if none fits) the planned order is cut at the stop whose bank adds the least walking. Null if no
	 * split fits: the plan then warns as before.
	 */
	private Split split(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Route route)
	{
		Split best = null;
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
		return best != null ? best : splitInOrder(config, access, holdings, selection, route);
	}

	/** First the stops with any of these patch types (a stop is only visited once), then a bank, then the rest. */
	private Split splitByType(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Set<PatchType> firstTypes)
	{
		final Set<Location> firstAt = EnumSet.noneOf(Location.class);
		for (Patch patch : selection.getPatches())
		{
			if (firstTypes.contains(patch.getType()))
			{
				firstAt.add(patch.getLocation());
			}
		}
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
		final Route route1 = RoutePlanner.planHalf(first, config, access, holdings, poh, timings.learned(), here, true);
		final SupplyPlan before = supplies(config, access, holdings, selection, first, route1);
		if (before.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
		{
			return null;
		}
		final Route route2 = RoutePlanner.planHalf(second, config, access, holdings, poh, timings.learned(),
			// Onward from the bank stop, so e.g. the Stronghold bank then its spirit tree to Tree Gnome Village counts
			route1.getStops().get(route1.getStops().size() - 1).getLocation(),
			false);
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
		return new Split(whole, before, new BankStop(route1.getStops().size() - 1, bankAt, after),
			whole.getTravelSeconds());
	}

	/** Cut the planned order at the stop whose bank adds the least walking, among cuts where both halves fit. */
	private Split splitInOrder(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Route route)
	{
		final List<RouteStop> stops = route.getStops();
		Split best = null;
		int bestTiles = Integer.MAX_VALUE;
		for (int k = 1; k < stops.size(); k++)
		{
			final Location bankAt = stops.get(k - 1).getLocation();
			final int tiles = RoutePlanner.bankTiles(bankAt);
			if (tiles >= bestTiles)
			{
				continue;
			}
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
			if (after.getSlots() <= SupplyCalculator.INVENTORY_SLOTS)
			{
				best = new Split(route, before, new BankStop(k - 1, bankAt, after), route.getTravelSeconds());
				bestTiles = tiles;
			}
		}
		return best;
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
