package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.data.travel.WalkGrid;
import com.farmrunautopilot.run.RunTimings;
import com.farmrunautopilot.run.StepAdvisor;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.VaultBanking;
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
import java.util.function.BiFunction;
import java.util.function.Function;
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
	/** The player was in the Farming Guild on the last tick. */
	private boolean guild;

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

	/**
	 * The current plan for a "Report a problem" issue (issue #17): what's in the run, each stop and its way, the
	 * bank visits, and each supply line's need and whether it's carried. Nothing that says who or where the player
	 * is, and no bank, vault or storage counts. Any thread.
	 */
	public List<String> problemSummary()
	{
		final RunConfig config = settings.getRunConfig();
		final RunPlan plan = getPlan();
		final List<String> lines = new ArrayList<>();
		lines.add("Run types: " + config.getEnabledTypes());
		lines.add("Start: " + (config.getStartLocation() != null ? config.getStartLocation().getDisplayName() : "nearest"));
		final List<RouteStop> stops = plan.getRoute().getStops();
		if (stops.isEmpty())
		{
			lines.add("No run built");
			return lines;
		}
		for (int i = 0; i < stops.size(); i++)
		{
			final RouteStop stop = stops.get(i);
			lines.add((i + 1) + ". " + stop.getLocation().getDisplayName() + ": " + stop.describeTravel() + ", "
				+ Math.round(stop.getLegSeconds()) + " s");
			final BankStop bank = plan.bankAfter(i);
			if (bank != null)
			{
				lines.add("   Bank: " + bank.how());
			}
		}
		lines.add("Supplies (first part):");
		for (SupplyLine line : plan.getSupplies().getLines())
		{
			lines.add("- " + line.getName() + ": need " + line.getNeed() + ", carried " + line.getCarried() + ", "
				+ line.getStatus() + (line.isWear() ? ", wear" : ""));
		}
		for (String warning : plan.getSupplies().getWarnings())
		{
			lines.add("Warning: " + warning);
		}
		return lines;
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
		final boolean guildNow = inGuild();
		final boolean moved = nowAt != here || nearNow != near || guildNow != guild;
		here = nowAt;
		near = nearNow;
		guild = guildNow;
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
		// In the Farming Guild, where it isn't a stop: the run sets off from there (issue #8)
		Route route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
			settings.getAccount().getPoh(), timings.learned(), here, near, nearTiles,
			guild ? Location.FARMING_GUILD : null);
		SupplyPlan supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
			patchTracker::predict, this::itemName, itemManager::getItemPrice);
		// Starting at the stop the player stands at or near only makes sense if they can start from there: carrying
		// everything already, or with a bank close by. Otherwise (Kastori, still needing supplies) they'll bank
		// somewhere first, so plan from anywhere (elzerma)
		final Location startStop = here != null ? here : near;
		if (startStop != null && TravelTimes.bankTiles(startStop) == null
			&& supplies.missingCount(startStop == Location.FARMING_GUILD) > 0)
		{
			route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
				settings.getAccount().getPoh(), timings.learned(), null, null, 0);
			supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
				patchTracker::predict, this::itemName, itemManager::getItemPrice);
		}
		// Seeds the run plants that only the seed vault has: the player fetches them from the Farming Guild, so the run
		// sets off from there (issue #8). With "Bank at the Farming Guild" on a run that banks partway, only the first
		// part's come out before the run, so that's checked once the bank visits are placed.
		final boolean guildBanks = guildBanking(config) && supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS;
		if (!guildBanks && plansFromGuild(config, route, supplies))
		{
			route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
				settings.getAccount().getPoh(), timings.learned(), null, null, 0, Location.FARMING_GUILD);
			supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
				patchTracker::predict, this::itemName, itemManager::getItemPrice);
		}
		// Too much for one inventory: bank partway, as often as needed, where it costs least
		final List<BankStop> bankStops = new ArrayList<>();
		if (supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
		{
			Banked banked = bankStops(config, access, holdings, selection, route);
			if (banked != null && guildBanks && plansFromGuild(config, banked.route, banked.first))
			{
				route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
					settings.getAccount().getPoh(), timings.learned(), null, null, 0, Location.FARMING_GUILD);
				supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
					patchTracker::predict, this::itemName, itemManager::getItemPrice);
				banked = supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS
					? bankStops(config, access, holdings, selection, route) : null;
			}
			if (banked != null)
			{
				route = banked.route;
				supplies = banked.first;
				bankStops.addAll(banked.stops);
			}
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

		final RunPlan next = new RunPlan(selection, route, supplies, objectives,
			Collections.unmodifiableList(bankStops));
		if (next.equals(plan) && !picksChanged)
		{
			return false;
		}
		if ((plan == null || !next.getRoute().equals(plan.getRoute())
			|| !next.getBankStops().equals(plan.getBankStops())) && log.isDebugEnabled())
		{
			log.debug("Route planned:\n{}", explain(next));
		}
		plan = next;
		return true;
	}

	/**
	 * The run should set off from the Farming Guild, as seeds it plants are only in the guild's seed vault (issue #8):
	 * unless it does already, or the player picked where runs start.
	 */
	static boolean plansFromGuild(RunConfig config, Route route, SupplyPlan supplies)
	{
		return config.getStartLocation() == null && !route.getStops().isEmpty()
			&& !route.setsOffFrom(Location.FARMING_GUILD) && !supplies.onlyInVault().isEmpty();
	}

	/** "Bank at the Farming Guild": a run that banks partway gets the seed vault's seeds at the guild then (issue #8). */
	public static boolean guildBanking(RunConfig config)
	{
		return config.isUseSeedVault() && config.getVaultBanking() == VaultBanking.BANK_AT_GUILD;
	}

	/** In the Farming Guild's area, where its bank and seed vault are. Client thread. */
	private boolean inGuild()
	{
		final Player player = client.getLocalPlayer();
		return player != null && player.getWorldLocation().getRegionID() == Patch.FARMING_GUILD_HERB.getRegionId();
	}

	/**
	 * The route leg by leg with how each time was worked out, and where it banks, so a strange route can be
	 * explained from the log alone (docs/REBUILD.md).
	 */
	private static String explain(RunPlan plan)
	{
		final StringBuilder b = new StringBuilder();
		if (plan.getRoute().getStartsFrom() != null)
		{
			b.append("From ").append(plan.getRoute().getStartsFrom().getDisplayName()).append('\n');
		}
		final List<RouteStop> stops = plan.getRoute().getStops();
		for (int i = 0; i < stops.size(); i++)
		{
			final RouteStop stop = stops.get(i);
			b.append(i + 1).append(". ").append(stop.getLocation().getDisplayName()).append(": ")
				.append(stop.describeTravel()).append(", ").append(Math.round(stop.getLegSeconds())).append(" s = ")
				.append(stop.getBreakdown()).append('\n');
			final BankStop bank = plan.bankAfter(i);
			if (bank != null)
			{
				b.append("   Bank: ").append(bank.how()).append(", ").append(bank.getSupplies().getSlots())
					.append(" slots for the next part\n");
			}
		}
		return b.append("Travel ").append(Math.round(plan.getRoute().getTravelSeconds())).append(" s, first part ")
			.append(plan.getSupplies().getSlots()).append(" slots").toString();
	}

	/** A run with its bank visits: the route, what to bring at the start, and each visit with what it takes. */
	private static final class Banked
	{
		final Route route;
		final SupplyPlan first;
		final List<BankStop> stops;

		Banked(Route route, SupplyPlan first, List<BankStop> stops)
		{
			this.route = route;
			this.first = first;
			this.stops = stops;
		}
	}

	/**
	 * Bank visits for a run too big for one inventory (see {@link BankPlanner}), with each part's supplies.
	 * Null if a part can't fit even on its own; the plan then warns about slots as before.
	 */
	private Banked bankStops(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Route route)
	{
		final PohSetup poh = settings.getAccount().getPoh();
		final List<RouteStop> stops = route.getStops();
		final Map<Long, Integer> slots = new HashMap<>();
		final BankPlanner.Slots slotsOf = (a, b) -> slots.computeIfAbsent((long) a * 1000 + b,
			k -> part(config, access, holdings, selection, stops.subList(a, b), route).getSlots());
		final BankTeleport teleport = BankTeleport.best(access, holdings);
		final BiFunction<Location, Location, Double> chainSaving = (from, to) -> RoutePlanner.chainSaving(from, to,
			config, access, holdings, poh, timings.learned());
		final Function<Location, RouteStop> fromAnywhere = to -> RoutePlanner.fromAnywhere(to, config, access,
			holdings, poh, timings.learned());
		BankPlanner.Result result = BankPlanner.place(route, slotsOf, SupplyCalculator.INVENTORY_SLOTS, teleport,
			chainSaving, fromAnywhere, guildBanking(config) ? guild(config, access, holdings, selection, route, poh) : null);
		if (result == null && guildBanking(config))
		{
			// No way to bank at the guild before every part that needs its vault (an item that doesn't fit): bank
			// wherever is quickest, and the vault's seeds are fetched by hand as before
			result = BankPlanner.place(route, slotsOf, SupplyCalculator.INVENTORY_SLOTS, teleport, chainSaving,
				fromAnywhere);
		}
		if (result == null || result.getVisits().isEmpty())
		{
			return null;
		}

		// Each part's supplies, counted against what the parts before it leave (seeds planted, runes cast), with
		// the teleport to bank with carried in the part before the bank
		final List<RouteStop> banked = result.getRoute().getStops();
		final List<BankPlanner.Visit> visits = result.getVisits();
		final List<SupplyPlan> parts = new ArrayList<>();
		int start = 0;
		Holdings left = holdings;
		for (int i = 0; i <= visits.size(); i++)
		{
			final int end = i < visits.size() ? visits.get(i).getAfterStop() + 1 : banked.size();
			SupplyPlan part = part(config, access, left, selection, banked.subList(start, end), result.getRoute());
			if (i < visits.size() && visits.get(i).getTeleport() != null)
			{
				part = withTeleport(part, visits.get(i).getTeleport(), left);
			}
			if (i < visits.size() && visits.get(i).getTrip() != null)
			{
				part = withTrip(part, visits.get(i).getTrip(), left);
			}
			parts.add(part);
			left = part.leftAfter(left);
			start = end;
		}
		final List<BankStop> bankStops = new ArrayList<>();
		for (int i = 0; i < visits.size(); i++)
		{
			final BankPlanner.Visit v = visits.get(i);
			bankStops.add(new BankStop(v.getAfterStop(), v.getLocation(), parts.get(i + 1), v.getTeleport(), v.getTrip()));
		}

		// The guide needs every patch's crop, not only the first part's
		final Map<Patch, Crop> plantings = new EnumMap<>(Patch.class);
		for (SupplyPlan part : parts)
		{
			plantings.putAll(part.getPlantings());
		}
		final SupplyPlan first = parts.get(0);
		return new Banked(result.getRoute(), new SupplyPlan(first.getLines(), first.getPatchCounts(),
			first.getNotDue(), first.getTravelPlan(), first.getWarnings(), first.getCoins(), first.getRuneSummary(),
			first.getSlots(), Collections.unmodifiableMap(plantings), first.getStaffSwaps()), bankStops);
	}

	/** The supplies for some of the route's stops on their own. */
	private SupplyPlan part(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		List<RouteStop> stops, Route route)
	{
		return supplies(config, access, holdings, selection, patchesAt(selection, stops),
			new Route(new ArrayList<>(stops), route.getMode(), 0, 0));
	}

	/**
	 * "Bank at the Farming Guild" for the bank planner (issue #8): which parts of the route need the seed vault, and
	 * the ways to and from the guild.
	 */
	private BankPlanner.Guild guild(RunConfig config, AccessSnapshot access, Holdings holdings, RunSelection selection,
		Route route, PohSetup poh)
	{
		final List<RouteStop> stops = route.getStops();
		final Map<Long, Boolean> needs = new HashMap<>();
		final Map<Integer, Holdings> leftBefore = new HashMap<>();
		return new BankPlanner.Guild()
		{
			@Override
			public boolean needsVault(int from, int to)
			{
				return needs.computeIfAbsent((long) from * 1000 + to, k -> !part(config, access, left(from), selection,
					stops.subList(from, to), route).onlyInVault().isEmpty());
			}

			/** What the player has left after the stops before this one, with each part counted on its own. */
			private Holdings left(int stop)
			{
				return leftBefore.computeIfAbsent(stop, k -> k == 0 ? holdings
					: part(config, access, holdings, selection, stops.subList(0, k), route).leftAfter(holdings));
			}

			@Override
			public RouteStop to(Location from)
			{
				return RoutePlanner.between(from, Location.FARMING_GUILD, config, access, holdings, poh, timings.learned());
			}

			@Override
			public RouteStop from(Location to)
			{
				if (to == Location.FARMING_GUILD)
				{
					// From the guild's bank out to its patches
					final int tiles = TravelTimes.bankTiles(Location.FARMING_GUILD);
					return new RouteStop(to, null, Departure.WALK, tiles * TravelTimes.SECONDS_PER_TILE, false,
						"from the bank " + tiles + " tiles");
				}
				return RoutePlanner.between(Location.FARMING_GUILD, to, config, access, holdings, poh, timings.learned());
			}
		};
	}

	/**
	 * A part's supplies with the item for the trip to the Farming Guild's bank, used at the end of it (issue #8). Ways
	 * with nothing to bring (a spirit tree, a fairy ring with a staff kept on) add nothing.
	 */
	private static SupplyPlan withTrip(SupplyPlan before, RouteStop trip, Holdings holdings)
	{
		if (trip.getMethod() == null || trip.getMethod().getItem() == null)
		{
			return before;
		}
		final int[] ids = trip.getMethod().getItem().getItemIds();
		final List<SupplyLine> lines = new ArrayList<>(before.getLines());
		lines.add(new SupplyLine(SupplyLine.Group.TRAVEL, trip.getMethod().getItem().getDisplayName()
			+ " (bank at the Farming Guild)", 1, holdings.countAny(ids), holdings.carriedOnly().countAny(ids),
			holdings.where(ids), "To bank partway at the Farming Guild, by its seed vault", 1, false, null, ids));
		return new SupplyPlan(lines, before.getPatchCounts(), before.getNotDue(), before.getTravelPlan(),
			before.getWarnings(), before.getCoins(), before.getRuneSummary(), before.getSlots() + 1,
			before.getPlantings(), before.getStaffSwaps());
	}

	/** A part's supplies with the bank teleport to bring, used at the bank that ends it. */
	private static SupplyPlan withTeleport(SupplyPlan before, BankTeleport teleport, Holdings holdings)
	{
		final int[] ids = teleport.getItemIds();
		final List<SupplyLine> lines = new ArrayList<>(before.getLines());
		lines.add(new SupplyLine(SupplyLine.Group.TRAVEL, teleport.getItemName() + " (bank partway)", 1,
			holdings.countAny(ids), holdings.carriedOnly().countAny(ids), holdings.where(ids),
			"To bank partway through the run: teleport to " + teleport.getBankName(), 1, false, null, ids));
		return new SupplyPlan(lines, before.getPatchCounts(), before.getNotDue(), before.getTravelPlan(),
			before.getWarnings(), before.getCoins(), before.getRuneSummary(), before.getSlots() + 1,
			before.getPlantings(), before.getStaffSwaps());
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

	/**
	 * What some stops of a run in progress need, worked out afresh against what the player holds now: for spotting
	 * an item gone missing mid-run (elzerma). Client thread.
	 */
	public SupplyPlan suppliesFor(RunPlan plan, List<RouteStop> stops)
	{
		return part(settings.getRunConfig(), accessChecker.getSnapshot(), holdingsTracker.getHoldings(),
			plan.getSelection(), stops, plan.getRoute());
	}

	/** The quickest bank teleport the player is carrying now, or null. Client thread. */
	public BankTeleport carriedBankTeleport()
	{
		return BankTeleport.best(accessChecker.getSnapshot(), holdingsTracker.getHoldings().carriedOnly());
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
	 * The stop whose patch is closest to walk to, if within {@link #NEARBY_TILES} walking tiles, so a run can start
	 * by walking there (e.g. from the Grand Tree to the Stronghold patches); sets {@link #nearTiles}. Another floor
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
		// Only stops in the run: a nearby patch that isn't due shouldn't hide one a little further away
		final RunPlan current = plan;
		for (Map.Entry<Patch, WorldPoint> e : PatchPoints.all().entrySet())
		{
			if (current != null && !current.getSelection().getPatches().isEmpty()
				&& !current.getSelection().getPatches().contains(e.getKey()))
			{
				continue;
			}
			final WorldPoint point = e.getValue();
			// Walking distance from the measured grid (elzerma: Castle Wars isn't near Lletya); straight-line only if
			// the grid didn't load
			final Integer walked = WalkGrid.loaded() ? WalkGrid.tiles(e.getKey().getLocation(), location) : null;
			if (WalkGrid.loaded() && walked == null)
			{
				continue;
			}
			final int tiles = walked != null ? walked
				: point.distanceTo2D(location) + (point.getPlane() != location.getPlane() ? 10 : 0);
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
