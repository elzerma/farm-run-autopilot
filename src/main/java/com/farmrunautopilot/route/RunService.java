package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.data.travel.WalkGrid;
import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasNeed;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.run.StepAdvisor;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.VaultBanking;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.PatchCare;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.tracking.PatchTracker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
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
import net.runelite.client.game.ItemStats;

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
	/** RuneLite's shared background executor, for reading the travel atlas. */
	private final ScheduledExecutorService executor;
	/** Reading the travel atlas, until it's done. */
	private Future<?> atlasLoad;
	/** Item names are fixed, so look each up once. */
	private final Map<Integer, String> names = new HashMap<>();

	private volatile boolean dirty = true;
	/** Null until the first plan after login, so that one is always published. */
	private volatile RunPlan plan;
	/** The Travel tab's families and ways as of the last plan; replaced only when something in them changes. */
	private volatile TravelWays ways = TravelWays.EMPTY;
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
		PatchTracker patchTracker, HoldingsTracker holdingsTracker, RunOverrides overrides,
		ScheduledExecutorService executor)
	{
		this.executor = executor;
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

	/** The Travel tab's families and ways, each with how it stands for the player. Any thread. */
	public TravelWays getTravelWays()
	{
		return ways;
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
		ways = TravelWays.EMPTY;
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
		final RouteAtlas atlas = routeAtlas(config, access, holdings);
		final TravelWays nowWays = TravelWays.of(atlas, config, access, holdings, settings.getAccount().getPoh(),
			this::itemName);
		if (!nowWays.equals(ways))
		{
			ways = nowWays;
		}
		final Player player = client.getLocalPlayer();
		final WorldPoint tile = player != null ? player.getWorldLocation() : null;
		// In the Farming Guild, where it isn't a stop: the run sets off from there (issue #8)
		Route route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
			settings.getAccount().getPoh(), atlas, here, near, nearTiles, guild ? Location.FARMING_GUILD : null, tile);
		SupplyPlan supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
			patchTracker::predict, this::itemName, itemManager::getItemPrice, this::wearSlot);
		// Starting at the stop the player stands at or near only makes sense if they can start from there: carrying
		// everything already, or with a bank close by. Otherwise (Kastori, still needing supplies) they'll bank
		// somewhere first, so plan from anywhere (elzerma). The same for setting off on foot from where they stand.
		final Location startStop = here != null ? here : near;
		final boolean missing = supplies.missingCount(startStop == Location.FARMING_GUILD) > 0;
		if (missing && (startStop != null ? TravelTimes.bankTiles(startStop) == null
			: atlas != null && tile != null && !RoutePlanner.bankNear(atlas, tile)))
		{
			route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
				settings.getAccount().getPoh(), atlas, null, null, 0);
			supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
				patchTracker::predict, this::itemName, itemManager::getItemPrice, this::wearSlot);
		}
		// Seeds the run plants that only the seed vault has: the player fetches them from the Farming Guild, so the run
		// sets off from there (issue #8). With "Bank at the Farming Guild" on a run that banks partway, only the first
		// part's come out before the run, so that's checked once the bank visits are placed.
		final boolean guildBanks = guildBanking(config) && supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS;
		if (!guildBanks && plansFromGuild(config, route, supplies))
		{
			route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
				settings.getAccount().getPoh(), atlas, null, null, 0, Location.FARMING_GUILD);
			supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
				patchTracker::predict, this::itemName, itemManager::getItemPrice, this::wearSlot);
		}
		// Too much for one inventory: bank partway, as often as needed, where it costs least
		final List<BankStop> bankStops = new ArrayList<>();
		if (supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
		{
			Banked banked = bankStops(atlas, config, access, holdings, selection, route);
			if (banked != null && guildBanks && plansFromGuild(config, banked.route, banked.first))
			{
				route = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
					settings.getAccount().getPoh(), atlas, null, null, 0, Location.FARMING_GUILD);
				supplies = SupplyCalculator.calculate(config, access, holdings, selection, route,
					patchTracker::predict, this::itemName, itemManager::getItemPrice, this::wearSlot);
				banked = supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS
					? bankStops(atlas, config, access, holdings, selection, route) : null;
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

		final RunPlan next = new RunPlan(selection, route, supplies, objectives,
			Collections.unmodifiableList(bankStops));
		if (next.equals(plan))
		{
			return false;
		}
		if ((plan == null || !next.getRoute().equals(plan.getRoute())
			|| !next.getBankStops().equals(plan.getBankStops())) && log.isDebugEnabled())
		{
			log.debug("Route planned:\n{}", explain(next));
			if (atlas != null)
			{
				// Sprint T3 of docs/plans/travel-rework.md: the same run on the old tables, for comparing in the dev client
				final Route old = RoutePlanner.plan(selection.getPatches(), config, access, holdings,
					settings.getAccount().getPoh(), null, here, near, nearTiles, guild ? Location.FARMING_GUILD : null);
				log.debug("The old tables' route (not used): {}", summary(old));
			}
		}
		plan = next;
		return true;
	}

	/**
	 * Read the travel atlas in the background (about half a second), never on the client thread; routes are planned on
	 * the old tables until it's read, then again on the atlas.
	 */
	public void startUp()
	{
		atlasLoad = executor.submit(() ->
		{
			final long start = System.nanoTime();
			final TravelAtlas atlas = TravelAtlas.get();
			log.debug("Travel atlas read in {} ms ({} places)", (System.nanoTime() - start) / 1_000_000,
				atlas.getPlaces().size());
			markDirty();
		});
	}

	public void shutDown()
	{
		if (atlasLoad != null)
		{
			atlasLoad.cancel(false);
			atlasLoad = null;
		}
	}

	/** What the player lets the route use: the Travel tab's families, single ways and the Wilderness (sprint T7). */
	static AtlasWays ways(RunConfig config)
	{
		return new AtlasWays(config.familiesOff(), config.getWaysOff(), config.isAvoidWilderness());
	}

	/** A bank teleport the player lets the route use: teleport items on, and this one not turned off. */
	static boolean allowed(RunConfig config, BankTeleport teleport)
	{
		return config.isOn(TravelFamily.TELEPORT_ITEMS) && !config.getWaysOff().contains(teleport.getWayKey());
	}

	/**
	 * The travel atlas with the player as it needs them (the varbits and varplayers it names, read now, and what the
	 * Travel tab lets the route use), or null if it didn't load: routes are then planned on the old tables. Client
	 * thread.
	 */
	private RouteAtlas routeAtlas(RunConfig config, AccessSnapshot access, Holdings holdings)
	{
		final TravelAtlas atlas = TravelAtlas.ifLoaded();
		if (atlas == null || atlas.isEmpty())
		{
			return null;
		}
		final Map<Integer, Integer> varbits = new HashMap<>();
		final Map<Integer, Integer> varps = new HashMap<>();
		for (AtlasNeed need : atlas.getNeeds())
		{
			if (need.getKind() == AtlasNeed.Kind.VARBIT)
			{
				varbits.computeIfAbsent(need.getVar(), client::getVarbitValue);
			}
			else if (need.getKind() == AtlasNeed.Kind.VARP)
			{
				varps.computeIfAbsent(need.getVar(), client::getVarpValue);
			}
		}
		// Minutes since 1970, as the game keeps when a home or minigame teleport was last used
		return new RouteAtlas(atlas, new AtlasAccount(access, holdings, varbits, varps, ways(config),
			Instant.now().getEpochSecond() / 60));
	}

	/** e.g. "Catherby (Camelot Teleport, 31 s), Ardougne farm (Ardougne cloak (farm), 18 s); travel 49 s". */
	private static String summary(Route route)
	{
		final List<String> stops = new ArrayList<>();
		for (RouteStop stop : route.getStops())
		{
			stops.add(stop.getLocation().getDisplayName() + " (" + stop.describeWay() + ", "
				+ Math.round(stop.getLegSeconds()) + " s)");
		}
		return String.join(", ", stops) + "; travel " + Math.round(route.getTravelSeconds()) + " s";
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
	private Banked bankStops(RouteAtlas atlas, RunConfig config, AccessSnapshot access, Holdings holdings,
		RunSelection selection, Route route)
	{
		final PohSetup poh = settings.getAccount().getPoh();
		final List<RouteStop> stops = route.getStops();
		final Map<Long, Integer> slots = new HashMap<>();
		final BankPlanner.Slots slotsOf = (a, b) -> slots.computeIfAbsent((long) a * 1000 + b,
			k -> part(config, access, holdings, selection, stops.subList(a, b), route).getSlots());
		final BankTeleport teleport = BankTeleport.best(access, holdings, t -> allowed(config, t));
		final BiFunction<Location, Location, Double> chainSaving = (from, to) -> RoutePlanner.chainSaving(from, to,
			config, access, holdings, poh, atlas);
		final Function<Location, RouteStop> fromAnywhere = to -> RoutePlanner.fromAnywhere(to, config, access,
			holdings, poh, atlas);
		BankPlanner.Result result = BankPlanner.place(route, slotsOf, SupplyCalculator.INVENTORY_SLOTS, teleport,
			chainSaving, fromAnywhere, guildBanking(config) ? guild(atlas, config, access, holdings, selection, route, poh) : null);
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
				part = withTeleport(part, visits.get(i).getTeleport(), left, config.getOutfit());
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
		final Map<Patch, PatchCare> care = new EnumMap<>(Patch.class);
		for (SupplyPlan part : parts)
		{
			plantings.putAll(part.getPlantings());
			care.putAll(part.getCare());
		}
		final SupplyPlan first = parts.get(0);
		return new Banked(result.getRoute(), new SupplyPlan(first.getLines(), first.getPatchCounts(),
			first.getNotDue(), first.getTravelPlan(), first.getWarnings(), first.getCoins(), first.getRuneSummary(),
			first.getSlots(), Collections.unmodifiableMap(plantings), first.getStaffSwaps(),
			Collections.unmodifiableMap(care)), bankStops);
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
	private BankPlanner.Guild guild(RouteAtlas atlas, RunConfig config, AccessSnapshot access, Holdings holdings,
		RunSelection selection, Route route, PohSetup poh)
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
				return RoutePlanner.between(from, Location.FARMING_GUILD, config, access, holdings, poh, atlas);
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
				return RoutePlanner.between(Location.FARMING_GUILD, to, config, access, holdings, poh, atlas);
			}
		};
	}

	/**
	 * A part's supplies with the item for the trip to the Farming Guild's bank, used at the end of it (issue #8). Ways
	 * with nothing to bring (a spirit tree, a fairy ring with a staff kept on) add nothing.
	 */
	private static SupplyPlan withTrip(SupplyPlan before, RouteStop trip, Holdings holdings)
	{
		final TravelItem item = trip.travelItem();
		if (item == null)
		{
			return before;
		}
		final int[] ids = item.getItemIds();
		final List<SupplyLine> lines = new ArrayList<>(before.getLines());
		lines.add(new SupplyLine(SupplyLine.Group.TRAVEL, item.getDisplayName()
			+ " (bank at the Farming Guild)", 1, holdings.countAny(ids), holdings.carriedOnly().countAny(ids),
			holdings.where(ids), "To bank partway at the Farming Guild, by its seed vault", 1, false, null, ids));
		return new SupplyPlan(lines, before.getPatchCounts(), before.getNotDue(), before.getTravelPlan(),
			before.getWarnings(), before.getCoins(), before.getRuneSummary(), before.getSlots() + 1,
			before.getPlantings(), before.getStaffSwaps(), before.getCare());
	}

	/**
	 * A part's supplies with the bank teleport to bring, used at the bank that ends it: worn when its slot is free
	 * (elzerma, 2026-10-19: the ring slot was empty, yet the ring of dueling for the Castle Wars bank wasn't asked to be
	 * worn).
	 */
	private SupplyPlan withTeleport(SupplyPlan before, BankTeleport teleport, Holdings holdings, Outfit outfit)
	{
		final int[] ids = teleport.getItemIds();
		final boolean worn = slotFree(wearSlot(ids[0]), before, ids, holdings, outfit);
		final List<SupplyLine> lines = new ArrayList<>(before.getLines());
		final SupplyLine line = new SupplyLine(SupplyLine.Group.TRAVEL, teleport.getItemName() + " (bank partway)", 1,
			holdings.countAny(ids), holdings.carriedOnly().countAny(ids), holdings.where(ids),
			"To bank partway through the run: teleport to " + teleport.getBankName()
				+ (worn ? ". Wear it: no inventory slot" : ""), worn ? 0 : 1, false, null, ids);
		lines.add(worn ? line.worn() : line);
		return new SupplyPlan(lines, before.getPatchCounts(), before.getNotDue(), before.getTravelPlan(),
			before.getWarnings(), before.getCoins(), before.getRuneSummary(), before.getSlots() + (worn ? 0 : 1),
			before.getPlantings(), before.getStaffSwaps(), before.getCare());
	}

	/**
	 * Whether something can be worn in this slot for the part: nothing the part wears goes there (Graceful's cape and
	 * gloves included), and nothing but one of {@code ids} is worn in it now.
	 */
	private boolean slotFree(TravelItem.WearSlot slot, SupplyPlan part, int[] ids, Holdings holdings, Outfit outfit)
	{
		if (slot == null || slot == TravelItem.WearSlot.WEAPON || outfit == Outfit.GRACEFUL
			&& (slot == TravelItem.WearSlot.CAPE || slot == TravelItem.WearSlot.HANDS))
		{
			return false;
		}
		for (SupplyLine line : part.getLines())
		{
			if (line.isWear() && line.getItemIds() != null && line.getItemIds().length > 0
				&& wearSlot(line.getItemIds()[0]) == slot)
			{
				return false;
			}
		}
		for (int id : holdings.in(Holdings.Source.WORN).keySet())
		{
			if (Arrays.stream(ids).noneMatch(i -> i == id) && wearSlot(id) == slot)
			{
				return false;
			}
		}
		return true;
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
			this::itemName, itemManager::getItemPrice, this::wearSlot);
	}

	/**
	 * The best way to a stop with what the player has now, for a run in progress whose leg can't be taken as planned
	 * (a daily teleport with no uses left). Client thread.
	 *
	 * @param from the stop it sets off from, or null for wherever the player is
	 */
	public RouteStop leg(Location from, Location to)
	{
		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final Holdings holdings = holdingsTracker.getHoldings();
		return RoutePlanner.between(from, to, config, access, holdings, settings.getAccount().getPoh(),
			routeAtlas(config, access, holdings));
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

	/** The quickest bank teleport the player is carrying now and lets the route use, or null. Client thread. */
	public BankTeleport carriedBankTeleport()
	{
		final RunConfig config = settings.getRunConfig();
		return BankTeleport.best(accessChecker.getSnapshot(), holdingsTracker.getHoldings().carriedOnly(),
			t -> allowed(config, t));
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

	/** The equipment slot an item is worn in, from RuneLite's item stats; null if none the supply list plans. */
	TravelItem.WearSlot wearSlot(int itemId)
	{
		final ItemStats stats = itemManager.getItemStats(itemId);
		return stats == null || !stats.isEquipable() || stats.getEquipment() == null ? null
			: TravelItem.WearSlot.of(stats.getEquipment().getSlot());
	}
}
