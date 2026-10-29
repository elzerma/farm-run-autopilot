package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.WalkGrid;
import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasNeed;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.run.StepAdvisor;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.VaultBanking;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchTracker;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
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
	/** The Run tab when the travel atlas couldn't be read (it ships in the plugin): there's no route without it. */
	static final String NO_ATLAS = "Travel data couldn't be read, so there's no route. Restart RuneLite; if it happens "
		+ "again, please use Report a problem in Settings.";

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
	/** Each item's wear slot, looked up once (client thread), so the travel search can use them off it. */
	private final Map<Integer, Optional<TravelItem.WearSlot>> wearSlots = new HashMap<>();
	/** How long the travel search may take before keeping what it has (T4; about a second for most runs). */
	private static final long SEARCH_MILLIS = 8000;
	/** The travel search's own thread, from startUp to shutDown. */
	private ExecutorService searcher;
	/** The travel search running in the background, and the quickest run it's for ({@link #searchKey}); null if none. */
	private Future<?> search;
	private String searchingFor;
	/** The last travel search's answer, set from its thread. */
	private volatile Searched searched;

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
		searched = null;
		searchingFor = null;
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
		if (TravelAtlas.ifLoaded() == null)
		{
			// The travel atlas is read in the background at start-up (about half a second): planned once it's read
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
		if (atlas == null)
		{
			// It couldn't be read: no route at all rather than one without travel, and the Run tab says why
			final RunPlan none = new RunPlan(selection, new Route(new ArrayList<>(), config.getRouteMode(), 0, 0),
				new SupplyPlan(Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
					Collections.emptyList(), Collections.singletonList(NO_ATLAS), 0, "", 0, Collections.emptyMap()),
				Collections.emptyMap());
			if (none.equals(plan))
			{
				return false;
			}
			plan = none;
			return true;
		}
		final Player player = client.getLocalPlayer();
		final WorldPoint tile = player != null ? player.getWorldLocation() : null;
		final RunAssembly.Start start = new RunAssembly.Start(here, near, nearTiles, guild, tile);
		final RunAssembly.Result run = searched(assembly(config, access, holdings, atlas, selection).assemble(start),
			config, access, holdings, atlas, selection, start);
		final Route route = run.getRoute();
		final SupplyPlan supplies = run.getSupplies();
		final List<BankStop> bankStops = run.getBankStops();

		final Map<Location, List<String>> objectives = new EnumMap<>(Location.class);
		for (Patch patch : selection.getPatches())
		{
			objectives.computeIfAbsent(patch.getLocation(), k -> new ArrayList<>()).add(StepAdvisor.objectives(
				patch, patchTracker.predict(patch), supplies.getPlantings().get(patch),
				config.keepsGrowing(patch.getType())));
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
		}
		plan = next;
		return true;
	}

	/**
	 * Read the travel atlas in the background (about half a second), never on the client thread; no route is planned
	 * until it's read.
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
		// A thread of its own: the search can take seconds, and RuneLite's executor is shared with other plugins
		searcher = Executors.newSingleThreadExecutor(r ->
		{
			final Thread thread = new Thread(r, "Farm Run Autopilot travel search");
			thread.setDaemon(true);
			return thread;
		});
	}

	public void shutDown()
	{
		if (atlasLoad != null)
		{
			atlasLoad.cancel(false);
			atlasLoad = null;
		}
		if (search != null)
		{
			search.cancel(true);
			search = null;
		}
		if (searcher != null)
		{
			searcher.shutdownNow();
			searcher = null;
		}
		searchingFor = null;
		searched = null;
	}

	/** What the travel search found for a run (T4): what to leave at home, for the run planned with the quickest ways. */
	private static final class Searched
	{
		/** {@link #searchKey} of the quickest run it searched from. */
		final String key;
		final Set<String> avoid;

		Searched(String key, Set<String> avoid)
		{
			this.key = key;
			this.avoid = avoid;
		}
	}

	/**
	 * The run with the travel search's answer (docs/plans/travel-rework.md, T4): a run that banks partway may be quicker
	 * leaving a teleport at home. The search takes about a second, so it runs in the background from the quickest
	 * run, which shows meanwhile; its answer is used once it's back, for as long as the quickest run stays the same
	 * (withdrawing items for it doesn't change it). Counted again here with the game's item lookups, and kept only if
	 * it's still quicker. Client thread.
	 */
	private RunAssembly.Result searched(RunAssembly.Result quickest, RunConfig config, AccessSnapshot access,
		Holdings holdings, RouteAtlas atlas, RunSelection selection, RunAssembly.Start start)
	{
		if (quickest.getBankStops().isEmpty())
		{
			return quickest;
		}
		final String key = searchKey(quickest);
		final Searched done = searched;
		if (done != null && done.key.equals(key))
		{
			if (done.avoid.isEmpty())
			{
				return quickest;
			}
			final RunAssembly.Result run = assembly(config, access, holdings, atlas.avoiding(done.avoid), selection)
				.assemble(start);
			if (TravelSearch.score(run) + TravelSearch.MIN_GAIN > TravelSearch.score(quickest))
			{
				return quickest;
			}
			return TravelSearch.explain(quickest, run, done.avoid, this::itemName);
		}
		if (!key.equals(searchingFor))
		{
			startSearch(key, config, access, holdings, atlas, selection, start);
		}
		return quickest;
	}

	/**
	 * The search on RuneLite's executor, with what it needs read now: the patches' predictions, the crops' prices, and
	 * the item names and wear slots looked up so far (an item not looked up yet counts as carried, which can only make
	 * a slot look dearer; the answer is counted again on the client thread). Client thread.
	 */
	private void startSearch(String key, RunConfig config, AccessSnapshot access, Holdings holdings, RouteAtlas atlas,
		RunSelection selection, RunAssembly.Start start)
	{
		if (search != null)
		{
			search.cancel(true);
		}
		searchingFor = key;
		final PohSetup poh = settings.getAccount().getPoh();
		final Map<Patch, PatchPrediction> predictions = new EnumMap<>(Patch.class);
		for (Patch patch : selection.getPatches())
		{
			final PatchPrediction prediction = patchTracker.predict(patch);
			if (prediction != null)
			{
				predictions.put(patch, prediction);
			}
		}
		final Map<Integer, Long> prices = new HashMap<>();
		for (Crop crop : Crop.values())
		{
			prices.computeIfAbsent(crop.getPlantItemId(), id -> (long) itemManager.getItemPrice(id));
		}
		final Map<Integer, String> namesNow = new HashMap<>(names);
		final Map<Integer, Optional<TravelItem.WearSlot>> slotsNow = new HashMap<>(wearSlots);
		final RouteAtlas own = atlas.copy();
		if (searcher == null)
		{
			return;
		}
		search = searcher.submit(() ->
		{
			final long began = System.currentTimeMillis();
			final TravelSearch.Outcome outcome = TravelSearch.search(own, a -> new RunAssembly(config, access, holdings,
					poh, a, selection, predictions::get, id -> namesNow.getOrDefault(id, ""),
					id -> prices.getOrDefault(id, 0L), id -> slotsNow.getOrDefault(id, Optional.empty()).orElse(null)),
				start, config, access, holdings, System::currentTimeMillis, began + SEARCH_MILLIS);
			if (Thread.currentThread().isInterrupted())
			{
				return;
			}
			log.debug("Travel search: {} whole runs weighed in {} ms; leave at home: {}", outcome.getTried(),
				System.currentTimeMillis() - began, outcome.getAvoid().isEmpty() ? "nothing" : outcome.getAvoid());
			searched = new Searched(key, outcome.getAvoid());
			markDirty();
		});
	}

	/**
	 * What the travel search depends on in a run: each stop and its way, and where it banks with how full each part is.
	 * Not what's carried yet, so withdrawing the items doesn't start it again.
	 */
	private static String searchKey(RunAssembly.Result run)
	{
		final StringBuilder b = new StringBuilder();
		for (RouteStop stop : run.getRoute().getStops())
		{
			b.append(stop.getLocation()).append('|').append(stop.describeTravel()).append('|')
				.append(Math.round(stop.getLegSeconds())).append(';');
		}
		b.append(run.getSupplies().getSlots());
		for (BankStop bank : run.getBankStops())
		{
			b.append(';').append(bank.getAfterStop()).append('|').append(bank.how()).append('|')
				.append(bank.getSupplies().getSlots());
		}
		return b.toString();
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
	 * Travel tab lets the route use), or null if it hasn't been read or couldn't be: there's no route then. Client
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
			if (stop.getWhy() != null)
			{
				b.append("   Not the quickest: ").append(stop.getWhy()).append('\n');
			}
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

	/** The run's assembly for the player as they are now, with the game's item lookups. Client thread. */
	private RunAssembly assembly(RunConfig config, AccessSnapshot access, Holdings holdings, RouteAtlas atlas,
		RunSelection selection)
	{
		return new RunAssembly(config, access, holdings, settings.getAccount().getPoh(), atlas, selection,
			patchTracker::predict, this::itemName, itemManager::getItemPrice, this::wearSlot);
	}

	/**
	 * The best way to a stop with what the player has now, for a run in progress whose leg can't be taken as planned
	 * (a daily teleport with no uses left); null without the travel atlas. Client thread.
	 *
	 * @param from the stop it sets off from, or null for wherever the player is
	 */
	/** The account's levels, quests, unlocks and house, as last read. */
	public AccessSnapshot access()
	{
		return accessChecker.getSnapshot();
	}

	/** Whether the house is set to teleport outside, as last read from the game (House Options). */
	public boolean houseTeleportsOutside()
	{
		return access().isHouseTeleportOutside();
	}

	public RouteStop leg(Location from, Location to)
	{
		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final Holdings holdings = holdingsTracker.getHoldings();
		final RouteAtlas atlas = routeAtlas(config, access, holdings);
		return atlas == null ? null : RoutePlanner.between(from, to, config, access, holdings,
			settings.getAccount().getPoh(), atlas);
	}

	/**
	 * What some stops of a run in progress need, worked out afresh against what the player holds now: for spotting
	 * an item gone missing mid-run (elzerma). Client thread.
	 */
	public SupplyPlan suppliesFor(RunPlan plan, List<RouteStop> stops)
	{
		return suppliesFor(plan, stops, Collections.emptySet());
	}

	/** As above, leaving out patches whose supplies are used already (done or planted this run). Client thread. */
	public SupplyPlan suppliesFor(RunPlan plan, List<RouteStop> stops, Set<Patch> leaveOut)
	{
		// A staff or tome left in the bank isn't offered for runes mid-run: the runes carried count instead (elzerma,
		// 2026-10-24: asked at Kastori for the mystic air staff they'd banked, carrying the air runes it would replace)
		final Holdings holdings = holdingsTracker.getHoldings().carriedOnly(SupplyItems.INFINITE_RUNE_ITEMS.keySet());
		// No route is planned, so no travel atlas is needed
		return assembly(settings.getRunConfig(), accessChecker.getSnapshot(), holdings, null, plan.getSelection())
			.part(holdings, stops, plan.getRoute(), leaveOut);
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

	/**
	 * Whether the tool leprechaun can note this item: it has a noted form and isn't a note already (a note links back to
	 * its item, so {@link #notedId} alone can't tell). Client thread.
	 */
	public boolean notable(int itemId)
	{
		final net.runelite.api.ItemComposition item = itemManager.getItemComposition(itemId);
		return item.getNote() == -1 && item.getLinkedNoteId() > 0;
	}

	/** An item's name, looked up once. Client thread. */
	public String itemName(int itemId)
	{
		return names.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).getName());
	}

	/** The equipment slot an item is worn in, from RuneLite's item stats; null if none the supply list plans. */
	TravelItem.WearSlot wearSlot(int itemId)
	{
		return wearSlots.computeIfAbsent(itemId, id ->
		{
			final ItemStats stats = itemManager.getItemStats(id);
			return Optional.ofNullable(stats == null || !stats.isEquipable() || stats.getEquipment() == null ? null
				: TravelItem.WearSlot.of(stats.getEquipment().getSlot()));
		}).orElse(null);
	}
}
