package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.AtlasBanks;
import com.farmrunautopilot.bank.FarmBankTab;
import com.farmrunautopilot.bank.SeedVaultSection;
import com.farmrunautopilot.route.AtlasLeg;
import com.farmrunautopilot.route.BankStop;
import com.farmrunautopilot.route.ChargeBudget;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.VaultBanking;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.StaffSwaps;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Runs a farm run (SPEC 13.4): off until Build run, building while supplies are gathered, armed (plan frozen,
 * first step shown) once they are, and running from the first teleport or patch click. While running it moves
 * from stop to stop, banking where the plan says, records leg times, and finishes itself after the last stop.
 * Getting to each stop is the {@link Navigator}'s job and the work at its patches the {@link PatchGuide}'s;
 * banking and what to wear come from {@link BankGuide} (docs/REBUILD.md, Sprint 4).
 *
 * <p>All methods except {@link #getView()} run on the client thread.
 */
@Slf4j
@Singleton
public class RunSession
{
	/** How long "Run finished" stays under the player. */
	private static final long FINISHED_NOTICE_MILLIS = 15_000;
	private static final int BEST_TIMES = 3;
	/** Dived during this run, so run may have been turned off underwater. */
	private boolean dived;
	/** Back up from the dive with run still off: the reminder to turn it on is shown. */
	private boolean surfacedWithRunOff;
	/** Underwater by Fossil Island, where nothing can be wielded. */
	private boolean underwater;
	/** The fishbowl helmet and diving apparatus, worn only for the dive by Fossil Island. */
	private static final Set<Integer> DIVING_GEAR = Set.of(ItemID.HUNDRED_PIRATE_DIVING_HELMET,
		ItemID.HUNDRED_PIRATE_DIVING_BACKPACK);
	/** How long the step in hand must lack its item before the task becomes a bank visit. */
	private static final long MISSING_GRACE_MILLIS = 1800;
	/** Moving further than this in one tick can only be a teleport. */
	private static final int TELEPORT_TILES = 30;

	private final Client client;
	private final ItemManager itemManager;
	private final ChatOptions chatOptions;
	private final RunService runService;
	private final HoldingsTracker holdingsTracker;
	private final SettingsStore settings;
	private final RunTimings timings;
	private final SceneTracker scene;
	/** Draws the walk for "run to" steps, if Shortest Path is installed. */
	private final ShortestPathBridge shortestPath;
	private final Navigator navigator;
	private final PatchGuide patches;
	private final FarmBankTab bankTab;
	private final SeedVaultSection seedVault;

	private RunView.State stage = RunView.State.OFF;
	/** Frozen when the run arms; null while off or building. */
	private RunPlan plan;
	/** When the timer started (epoch millis), 0 until the player leaves. */
	private long startedAt;
	/** Where the player was last tick, to notice a teleport while armed. */
	private WorldPoint lastLocation;
	private int stopIndex;
	/** At a bank visit after the current stop, restocking for the next part of the run. */
	private boolean banking;
	/** The bank visit after the current stop is done (or skipped). */
	/** Bank visits done, by the stop they follow: seen done or ticked by hand. */
	private final Set<Integer> banksDone = new HashSet<>();
	/** Bank visits ticked by hand, so they can be unticked. */
	private final Set<Integer> manualBanks = new HashSet<>();
	/** Travel steps of the current stop ticked by hand (they can be unticked), by index in its steps. */
	private final Set<Integer> manualSteps = new HashSet<>();
	/**
	 * An unplanned bank visit, because an item the plan counted on has gone missing (elzerma): after the current stop,
	 * or before finishing it when {@link #extraNow}. Null when there's none.
	 */
	private BankStop extraBank;
	/** The unplanned bank visit comes before the current stop is finished: its step needs the missing item. */
	private boolean extraNow;
	/** The planned bank visit the unplanned one does early (its stop index), or -1: it's done with it (banking early). */
	private int movedVisit = -1;
	/** An unplanned bank visit was ticked away by hand: no more until the next planned one. */
	private boolean declinedExtra;
	/** A daily teleport the game just refused for having no uses left today, whose leg is to be planned again. */
	private TravelMethod usedUpDaily;
	/** The house's teleport setting the run's legs follow (outside or not), or null before it's frozen. */
	private Boolean houseOutside;
	/**
	 * Items the plan counts on have gone missing, and the Run tab names them and asks whether to bank for them or skip
	 * (elzerma, 2026-10-18: not straight to a bank visit): what the rest of this part needs, or null when nothing's
	 * asked.
	 */
	private SupplyPlan missingAsk;
	/** The seed vault was on the way when that was asked, so what's in it counts. */
	private boolean missingAskVault;
	/**
	 * Items the player chose to do without (Skip, or a planned bank visit ticked by hand), until the next planned bank
	 * visit: not asked about again, and a step that needs one of them is skipped.
	 */
	private final Set<Integer> skippedItems = new HashSet<>();
	/** What the player held when items were last checked for the rest of this part. */
	private Holdings checkedHoldings;
	/** Since when the current step's item has been missing (epoch millis), 0 if it isn't. */
	private long missingSince;
	/**
	 * The bank visit under way, step by step (elzerma: at Castle Wars it still said to use the ring): got to the bank,
	 * opened it, opened the Farm run tab. Each stays done once seen (or ticked by hand) until the visit ends.
	 */
	private final boolean[] bankProgress = new boolean[3];
	/** Bank visit steps ticked by hand, so they can be unticked. */
	private final boolean[] bankTicked = new boolean[3];
	/** Within this of the bank chest or booth counts as having got there. */
	private static final int AT_BANK_TILES = 20;
	/** At the Farming Guild, everything needed from the seed vault has been taken (or skipped). */
	private boolean vaultChecked;
	/**
	 * The Farming Guild's seed vault step for its row on the Run tab, kept once shown so it stays listed, ticked, when
	 * done (elzerma, 2026-10-18: say at the start what the guide is doing); null at other stops.
	 */
	private String vaultStepText;
	/** That step was ticked by hand, so it can be unticked. */
	private boolean vaultTicked;
	/** The items the seed vault's step in hand has asked for, so taking too many is still seen once none are left to take. */
	private final Set<Integer> vaultItems = new LinkedHashSet<>();
	/** What the open seed vault's step still wants, for the inventory beside it; empty while it's closed. Client thread. */
	private List<InventoryCount> vaultCounts = Collections.emptyList();
	/** The same for the open bank's step: seeds and saplings still to take, or to put back. Client thread. */
	private List<InventoryCount> bankCounts = Collections.emptyList();

	/**
	 * A line for the inventory beside the open seed vault or bank: how many more to take from it, or how many to put
	 * back.
	 */
	@lombok.Value
	public static class InventoryCount
	{
		String text;
		/** Too many carried: some go back. */
		boolean deposit;
	}
	/**
	 * While gathering: the seed vault's step and the bank's for the seeds for later, once they've come up, so the
	 * Getting ready list keeps them, ticked, when done.
	 */
	private String gatherVault;
	private String gatherToBank;
	/**
	 * While gathering, once the seed vault's done: everything has been deposited at the bank (the inventory seen empty
	 * with the bank open), so what the Farm run tab marks is taken from there (elzerma, 2026-10-19).
	 */
	private boolean depositedAll;
	/** The step in hand is to press the bank's Deposit inventory button, which is outlined. Client thread. */
	private boolean depositAllStep;
	/** The current stop's patches in the order its checklist last showed them, so a tick goes to the patch shown. */
	private List<Patch> shownPatches = Collections.emptyList();
	/** The stop those were shown for, or -1. */
	private int shownPatchesStop = -1;
	/** The bank visit's step is the seed vault's, so the bank's Farm run tab has nothing left to flash for. */
	private boolean vaultStep;
	/** The time for the leg to the current stop has been saved. */
	private boolean legRecorded;
	private long legStartedAt;
	private final List<RunTimings.Leg> legs = new ArrayList<>();
	private long finishedAt;
	private String lastRun;
	/** The last run's time per patch type, one line each, for hovering its line; null if it couldn't be split. */
	private String lastRunTypes;
	/** When the current stop's last patch was done (before any bank visit there), 0 until then. */
	private long patchesDoneAt;
	/** Seconds spent on bank visits so far this run: not counted in any patch type's time. */
	private double bankSeconds;
	/** How far ahead of or behind the best pace the run is after the last stop, or null without one. */
	private String split;
	private volatile RunView view = RunView.OFF_VIEW;
	/** A staff to wield before setting off, outlined to put on; empty when there is none (issue #7). */
	private Set<Integer> wieldIds = Collections.emptySet();

	@Inject
	RunSession(Client client, RunService runService, HoldingsTracker holdingsTracker, SettingsStore settings,
		RunTimings timings, SceneTracker scene, ShortestPathBridge shortestPath, Navigator navigator,
		PatchGuide patches, FarmBankTab bankTab, SeedVaultSection seedVault, ItemManager itemManager,
		ChatOptions chatOptions)
	{
		this.chatOptions = chatOptions;
		this.itemManager = itemManager;
		this.client = client;
		this.runService = runService;
		this.holdingsTracker = holdingsTracker;
		this.settings = settings;
		this.timings = timings;
		this.scene = scene;
		this.shortestPath = shortestPath;
		this.navigator = navigator;
		this.patches = patches;
		this.bankTab = bankTab;
		this.seedVault = seedVault;
	}

	public RunView getView()
	{
		return view;
	}

	/** Start gathering supplies: the bank tab and the "get N items" reminder appear. */
	public void build()
	{
		if (stage != RunView.State.OFF)
		{
			return;
		}
		stage = RunView.State.BUILDING;
		lastRun = null;
		gatherVault = null;
		gatherToBank = null;
		depositedAll = false;
		vaultItems.clear();
		log.debug("Building a run");
		evaluate();
	}

	/** Back to off from building or armed, without saving anything. A running run is stopped instead. */
	public void cancel()
	{
		if (stage == RunView.State.RUNNING)
		{
			stop(false);
			return;
		}
		if (stage == RunView.State.OFF)
		{
			return;
		}
		stage = RunView.State.OFF;
		shortestPath.clear();
		plan = null;
		houseOutside = null;
		patches.reset(null);
		log.debug("Run cancelled");
		evaluate();
	}

	/** Logged out: drop a run that is being built or waiting to leave. */
	public void cancelIfNotRunning()
	{
		if (stage != RunView.State.RUNNING)
		{
			cancel();
		}
	}

	/** Start the timer now rather than waiting for a teleport or patch click; arms first if still building. */
	public void startNow()
	{
		if (stage == RunView.State.BUILDING && !runService.getPlan().getRoute().getStops().isEmpty())
		{
			arm(runService.getPlan());
			// Started without everything: what's missing counts as skipped, not asked about straight away (elzerma's
			// second run, started at Gnome Stronghold without its saplings, seeds or payments)
			final List<RouteStop> first = plan.getRoute().getStops().subList(0, partEnd() + 1);
			final Set<SupplyLine> missing = plan.getSupplies().counted(holdingsTracker.getHoldings())
				.missingLines(passesGuild(first)).keySet();
			if (!missing.isEmpty())
			{
				log.debug("Started without: {}", missing.stream().map(SupplyLine::getName).collect(Collectors.joining(", ")));
				skip(missing);
			}
		}
		if (stage == RunView.State.ARMED)
		{
			startClock(System.currentTimeMillis());
		}
	}

	/** Freeze the plan and show the first step; the timer waits for the player to leave. */
	private void arm(RunPlan current)
	{
		plan = current;
		houseOutside = runService.houseTeleportsOutside();
		stage = RunView.State.ARMED;
		startedAt = 0;
		stopIndex = 0;
		navigator.reset();
		patches.reset(plan);
		legs.clear();
		lastRun = null;
		lastRunTypes = null;
		patchesDoneAt = 0;
		bankSeconds = 0;
		split = null;
		banking = false;
		banksDone.clear();
		manualBanks.clear();
		vaultChecked = false;
		vaultStepText = null;
		vaultTicked = false;
		vaultItems.clear();
		legRecorded = false;
		manualSteps.clear();
		clearExtraBank();
		declinedExtra = false;
		skippedItems.clear();
		usedUpDaily = null;
		final Player player = client.getLocalPlayer();
		lastLocation = player != null ? player.getWorldLocation() : null;
		log.debug("Run armed with {} stops", plan.getRoute().getStops().size());
	}

	private void startClock(long now)
	{
		if (stage != RunView.State.ARMED)
		{
			return;
		}
		stage = RunView.State.RUNNING;
		startedAt = now;
		legStartedAt = now;
		log.debug("Run timer started");
		evaluate();
	}

	/** A jump this far in one tick is a teleport: walking and running cover at most two tiles. */
	static boolean isTeleport(WorldPoint from, WorldPoint to)
	{
		return from != null && to != null && from.distanceTo2D(to) > TELEPORT_TILES;
	}

	/**
	 * Start the timer on the first click on a patch in this run, or on its gardener, for players who start
	 * right next to their first patch.
	 */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (stage != RunView.State.ARMED)
		{
			return;
		}
		final List<Patch> inRun = plan.getSelection().getPatches();
		switch (event.getMenuAction())
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GAME_OBJECT:
				final Patch patch = scene.patchAt(event.getId(), event.getParam0(), event.getParam1());
				if (patch != null && inRun.contains(patch))
				{
					startClock(System.currentTimeMillis());
				}
				break;
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case WIDGET_TARGET_ON_NPC:
				final NPC npc = event.getMenuEntry().getNpc();
				for (Patch p : inRun)
				{
					if (npc != null && scene.gardener(p) == npc)
					{
						startClock(System.currentTimeMillis());
						break;
					}
				}
				break;
			default:
				break;
		}
	}

	/**
	 * End the run and save its timings. Before the timer has started this just goes back to off.
	 *
	 * @param finished every stop was completed
	 */
	public void stop(boolean finished)
	{
		if (stage != RunView.State.RUNNING)
		{
			cancel();
			return;
		}
		stage = RunView.State.OFF;
		shortestPath.clear();
		final long now = System.currentTimeMillis();
		final double seconds = (now - startedAt) / 1000.0;
		final Map<PatchType, Integer> counts = patchCounts(plan);
		final String makeup = RunTimings.makeup(counts);
		final RunTimings.Run run = new RunTimings.Run(startedAt, seconds, stopIndex, finished, makeup);
		run.setBankSeconds(bankSeconds);
		run.setEstimate(plan.getRoute().getTravelSeconds() + plan.getRoute().getPatchSeconds());
		timings.save(new ArrayList<>(legs), run);
		finishedAt = now;
		lastRun = (finished ? "Run finished in " : "Run stopped after ") + GuidanceOverlay.clock((long) seconds);
		lastRunTypes = null;
		if (finished)
		{
			// Like with like per patch type (elzerma, 2026-10-17); the whole run's best when it can't be split
			final String[] types = typeResults(run);
			lastRun += types[0] != null ? types[0]
				: rank(timings.best(makeup, BEST_TIMES), seconds, RunTimings.makeupName(counts));
			lastRunTypes = types[1];
			if (types[0] != null && types[0].contains("new best"))
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Farm Run Autopilot: " + lastRun, null);
			}
		}
		log.debug("Run ended: {}{}", lastRun, lastRunTypes != null ? "\n" + lastRunTypes : "");
		plan = null;
		houseOutside = null;
		patches.reset(null);
		// Replan with the newly learned leg times
		runService.markDirty();
		evaluate();
	}

	/** The current stop's leg gets its time there and its patches by type, for the times per patch type. */
	private void timeStop(RouteStop stop, long now)
	{
		if (legs.isEmpty())
		{
			return;
		}
		final RunTimings.Leg leg = legs.get(legs.size() - 1);
		if (leg.getLocation().equals(stop.getLocation().name()) && leg.getCounts() == null)
		{
			leg.setStopSeconds((now - leg.getFinishedAt()) / 1000.0);
			leg.setCounts(RunTimings.makeup(countsAt(plan, stop.getLocation())));
		}
	}

	/**
	 * After stop {@code done}: the time so far (bank visits left out) against the best pace, the quickest time per patch
	 * of each type in the player's runs, e.g. "-0:12 against your best pace"; null until each type done has one.
	 */
	private String split(long now, int done)
	{
		final List<Map<PatchType, TypeTimes.Part>> history = timings.typeHistory();
		if (history.isEmpty())
		{
			return null;
		}
		final Map<PatchType, Integer> counts = new EnumMap<>(PatchType.class);
		final List<RouteStop> stops = plan.getRoute().getStops();
		for (int i = 0; i <= done && i < stops.size(); i++)
		{
			countsAt(plan, stops.get(i).getLocation()).forEach((type, n) -> counts.merge(type, n, Integer::sum));
		}
		double pace = 0;
		for (Map.Entry<PatchType, Integer> e : counts.entrySet())
		{
			final Double best = TypeTimes.bestPerPatch(history, e.getKey());
			if (best == null)
			{
				return null;
			}
			pace += best * e.getValue();
		}
		final long gap = Math.round((now - startedAt) / 1000.0 - bankSeconds - pace);
		return (gap <= 0 ? "-" : "+") + GuidanceOverlay.clock(Math.abs(gap)) + " against your best pace";
	}

	/** The run's patches at one stop, by type. */
	private static Map<PatchType, Integer> countsAt(RunPlan plan, Location location)
	{
		final Map<PatchType, Integer> counts = new EnumMap<>(PatchType.class);
		for (Patch patch : patchesAt(plan, location))
		{
			counts.merge(patch.getType(), 1, Integer::sum);
		}
		return counts;
	}

	/**
	 * A finished run's time per patch type: " - new best for 9 herbs!" (or "first time for ...") for the line, and one
	 * line per type with its best and median, plus the banking, for hovering it. Null parts when it couldn't be split
	 * (a stop skipped without reaching it).
	 */
	private String[] typeResults(RunTimings.Run run)
	{
		final Map<PatchType, TypeTimes.Part> parts = TypeTimes.of(run, legs);
		if (parts.isEmpty())
		{
			return new String[]{null, null};
		}
		final List<Map<PatchType, TypeTimes.Part>> history = timings.typeHistory();
		// This run is the newest in the history: the bests before it are what it beat
		final List<Map<PatchType, TypeTimes.Part>> before = history.isEmpty() ? history
			: history.subList(0, history.size() - 1);
		final List<String> news = new ArrayList<>();
		final List<String> lines = new ArrayList<>();
		for (Map.Entry<PatchType, TypeTimes.Part> e : parts.entrySet())
		{
			final TypeTimes.Part part = e.getValue();
			final Double previous = TypeTimes.best(before, e.getKey(), part.getPatches());
			final String name = RunTimings.makeupName(Map.of(e.getKey(), part.getPatches()));
			if (previous == null)
			{
				news.add("first time for " + name);
			}
			else if (part.getSeconds() < previous)
			{
				news.add("new best for " + name + "!");
			}
			lines.add(TypeTimes.line(e.getKey(), part, TypeTimes.best(history, e.getKey(), part.getPatches()),
				TypeTimes.medianPerPatch(history, e.getKey())));
		}
		if (run.getBankSeconds() >= 1)
		{
			lines.add("Banking: " + GuidanceOverlay.clock(Math.round(run.getBankSeconds())));
		}
		return new String[]{news.isEmpty() ? "" : " - " + String.join(", ", news), String.join("\n", lines)};
	}

	/** e.g. " - new best for 6 herbs!" or " - 2nd best for 6 herbs"; empty outside the top times. */
	static String rank(List<Double> best, double seconds, String makeupName)
	{
		final int place = best.indexOf(seconds);
		if (place < 0)
		{
			return "";
		}
		if (best.size() == 1)
		{
			return " - first time for " + makeupName;
		}
		if (place == 0)
		{
			return " - new best for " + makeupName + "!";
		}
		return " - " + (place == 1 ? "2nd" : "3rd") + " best for " + makeupName;
	}

	/** e.g. "Best for 6 herbs: 8:12, 8:40, 9:03", or null with none yet. */
	private String bestTimes(RunPlan current)
	{
		// Just after a run: its time per type (for hovering the "Run finished" line)
		if (lastRun != null && lastRunTypes != null)
		{
			return lastRunTypes;
		}
		Map<PatchType, Integer> counts = patchCounts(current);
		final List<Map<PatchType, TypeTimes.Part>> history = timings.typeHistory();
		// Nothing due yet: the best times for the last run's patches (elzerma: they only showed once a run was ready)
		if (counts.isEmpty() && !history.isEmpty())
		{
			counts = new EnumMap<>(PatchType.class);
			for (Map.Entry<PatchType, TypeTimes.Part> e : history.get(history.size() - 1).entrySet())
			{
				counts.put(e.getKey(), e.getValue().getPatches());
			}
		}
		final String makeup = RunTimings.makeup(counts);
		if (makeup.isEmpty())
		{
			return null;
		}
		// Per type, like with like: the best for this many patches of each type, and its median per patch
		final List<String> bests = new ArrayList<>();
		final List<String> lines = new ArrayList<>();
		for (Map.Entry<PatchType, Integer> e : counts.entrySet())
		{
			final String name = RunTimings.makeupName(Map.of(e.getKey(), e.getValue()));
			final Double best = TypeTimes.best(history, e.getKey(), e.getValue());
			final Double median = TypeTimes.medianPerPatch(history, e.getKey());
			if (best != null)
			{
				bests.add(name + " " + GuidanceOverlay.clock(Math.round(best)));
			}
			if (best != null || median != null)
			{
				lines.add(name + ": " + (best != null ? "best " + GuidanceOverlay.clock(Math.round(best)) : "no best yet")
					+ (median != null ? "; median " + Math.round(median) + " s each" : ""));
			}
		}
		if (!lines.isEmpty())
		{
			return "Best: " + (bests.isEmpty() ? "none for these numbers of patches yet" : String.join(", ", bests)) + "\n"
				+ String.join("\n", lines);
		}
		// Runs from before times per type were kept: the whole run's best
		final List<Double> best = timings.best(makeup, BEST_TIMES);
		if (best.isEmpty())
		{
			return null;
		}
		final List<String> clocks = new ArrayList<>();
		for (double seconds : best)
		{
			clocks.add(GuidanceOverlay.clock((long) seconds));
		}
		return "Best for " + RunTimings.makeupName(counts) + ": " + String.join(", ", clocks);
	}

	/**
	 * A step of the current stop ticked or unticked by hand, as in Quest Helper (elzerma): ticking says the guide
	 * missed that it's done, unticking goes back to a step ticked by hand. A step the plugin saw done stays
	 * ticked. The way there and its journey steps go in order, so ticking one ticks those before it and unticking
	 * one unticks those after it; a patch is ticked on its own (and the seed vault check with it, as the player
	 * has moved on).
	 *
	 * @param step its index in the current stop's {@link RunView.Stop#getSteps() steps}
	 */
	public void setStepTicked(int step, boolean ticked)
	{
		if (stage != RunView.State.RUNNING || stopIndex >= plan.getRoute().getStops().size())
		{
			return;
		}
		if (banking)
		{
			// The steps shown are the bank visit's
			setBankStepTicked(step, ticked);
			return;
		}
		final RouteStop stop = plan.getRoute().getStops().get(stopIndex);
		final int journey = Navigator.journeySteps(stop);
		final long now = System.currentTimeMillis();
		log.debug("Step {}: {} step {}", ticked ? "ticked" : "unticked", stop.getLocation().getDisplayName(), step);
		if (step < travelSteps(stop))
		{
			if (ticked)
			{
				final int first = firstTravelStepToDo(journey);
				// Each call moves one step on; the guard stops it at the end whatever the state
				for (int i = 0; i <= journey && !travelStepDone(step); i++)
				{
					navigator.markStepDone(stop, now);
				}
				for (int s = first; s <= step; s++)
				{
					manualSteps.add(s);
				}
				// Not seen arriving, so the leg isn't a sample
				legRecorded = true;
			}
			else if (manualSteps.contains(step))
			{
				navigator.rewindTo(step);
				manualSteps.removeIf(s -> s >= step);
				patches.clearCurrent();
			}
		}
		else if (step == travelSteps(stop) && vaultSteps(stop) > 0)
		{
			// The seed vault by hand: done (or to do again) whatever's carried
			vaultChecked = ticked;
			vaultTicked = ticked;
		}
		else
		{
			// The patches in the order the sidebar showed them
			final List<Patch> here = shownPatchesStop == stopIndex ? shownPatches : patchesAt(plan, stop.getLocation());
			final int index = step - travelSteps(stop) - vaultSteps(stop);
			if (index >= here.size())
			{
				return;
			}
			if (!ticked)
			{
				patches.unskip(here.get(index));
			}
			else
			{
				if (!navigator.isArrived())
				{
					navigator.markArrived(now);
					legRecorded = true;
				}
				vaultChecked = true;
				patches.skipAll(Collections.singletonList(here.get(index)));
			}
		}
		evaluate();
	}

	/**
	 * How many of the current stop's steps are travel: the way there and its journey steps. None for a walk from
	 * the stop before (the patch's own "Walk to the ... patch" covers it), nor when the player was already there
	 * without travelling (elzerma: Taverley and Kastori showed the way there ticked already).
	 */
	private int travelSteps(RouteStop stop)
	{
		if (stop.getDeparture() == Departure.WALK || (navigator.isArrived() && !navigator.isTeleported()))
		{
			return 0;
		}
		return Navigator.journeySteps(stop) + 1;
	}

	/** 1 when the stop's steps list the seed vault (at the Farming Guild, once there's been something to take), else 0. */
	private int vaultSteps(RouteStop stop)
	{
		return stop.getLocation() == Location.FARMING_GUILD && vaultStepText != null ? 1 : 0;
	}

	/** The way there (step 0) or a journey step after it is done, as {@link #steps} shows it. */
	private boolean travelStepDone(int step)
	{
		return navigator.isArrived() || (step == 0 ? navigator.isTeleported() : navigator.getJourneyStep() >= step);
	}

	private int firstTravelStepToDo(int journey)
	{
		for (int s = 0; s <= journey; s++)
		{
			if (!travelStepDone(s))
			{
				return s;
			}
		}
		return journey + 1;
	}

	/**
	 * A stop or bank row ticked or unticked by hand. Ticking the current stop skips everything left there
	 * (getting there, its patches, the seed vault); ticking the current bank visit carries on without everything
	 * from the bank. Unticking either goes back to it, until the stop after it is finished.
	 *
	 * @param row its index in the view's {@link RunView#getStops() stops}, bank visits included
	 */
	public void setRowTicked(int row, boolean ticked)
	{
		if (stage != RunView.State.RUNNING)
		{
			return;
		}
		final List<Object> rows = rows();
		if (row < 0 || row >= rows.size())
		{
			return;
		}
		final Object at = rows.get(row);
		if (at == extraBank)
		{
			if (ticked)
			{
				declineExtraBank();
			}
		}
		else if (at instanceof BankStop)
		{
			if (ticked)
			{
				skipBank(((BankStop) at).getAfterStop());
			}
			else
			{
				untickBank(((BankStop) at).getAfterStop());
			}
		}
		else if (ticked)
		{
			skipStop((Integer) at);
		}
		else
		{
			untickStop((Integer) at);
		}
	}

	/** The current stop ticked by hand: skip everything left there in one go. Not timed, as any skipped stop. */
	private void skipStop(int index)
	{
		if (index != stopIndex || banking)
		{
			return;
		}
		final RouteStop stop = plan.getRoute().getStops().get(stopIndex);
		log.debug("Stop ticked: {}", stop.getLocation().getDisplayName());
		if (!navigator.isArrived())
		{
			navigator.markArrived(System.currentTimeMillis());
			legRecorded = true;
		}
		if (stop.getLocation() == Location.FARMING_GUILD)
		{
			vaultChecked = true;
		}
		patches.skipAll(patchesAt(plan, stop.getLocation()));
		evaluate();
	}

	/**
	 * A stop ticked by hand is unticked: its patches skipped by hand are to do again, and if it's behind the player
	 * the guide goes back to it. Any number of stops can be unticked, all the way back to the start (elzerma); what
	 * the plugin saw done stays done, so the way forward again passes it.
	 */
	private void untickStop(int index)
	{
		final Location location = plan.getRoute().getStops().get(index).getLocation();
		boolean any = false;
		for (Patch patch : patchesAt(plan, location))
		{
			if (patches.isSkipped(patch))
			{
				patches.unskip(patch);
				any = true;
			}
		}
		if (!any)
		{
			return;
		}
		log.debug("Stop unticked: {}", location.getDisplayName());
		if (location == Location.FARMING_GUILD)
		{
			vaultChecked = false;
		}
		if (index <= stopIndex)
		{
			backTo(index);
		}
		evaluate();
	}

	/** The current bank visit ticked by hand: carry on without everything from the bank. */
	private void skipBank(int afterStop)
	{
		if (afterStop != stopIndex || !banking)
		{
			return;
		}
		log.debug("Bank visit ticked by hand");
		// Carrying on without what the visit was for: not asked about straight after (elzerma's first run)
		final BankStop bank = plan.bankAfter(afterStop);
		if (bank != null)
		{
			skip(bank.getSupplies().counted(holdingsTracker.getHoldings()).missingLines(false).keySet());
		}
		banksDone.add(afterStop);
		manualBanks.add(afterStop);
		banking = false;
		evaluate();
	}

	/** A bank visit ticked by hand is unticked: to do again, and if it's behind the player, back to it. */
	private void untickBank(int afterStop)
	{
		if (!manualBanks.remove(afterStop))
		{
			return;
		}
		log.debug("Bank visit unticked");
		banksDone.remove(afterStop);
		if (afterStop < stopIndex)
		{
			backTo(afterStop);
			// The stop itself was finished: straight to its bank visit
			navigator.markArrived(System.currentTimeMillis());
		}
		evaluate();
	}

	/** Every patch at this stop is done (seen done or ticked by hand), so there's nothing to go there for. */
	private boolean stopDone(int index)
	{
		final List<Patch> here = patchesAt(plan, plan.getRoute().getStops().get(index).getLocation());
		for (Patch patch : here)
		{
			if (!patches.isDone(patch))
			{
				return false;
			}
		}
		return !here.isEmpty();
	}

	/** Some patch at this stop was ticked by hand, so the stop can be unticked. */
	private boolean stopTickedByHand(int index)
	{
		for (Patch patch : patchesAt(plan, plan.getRoute().getStops().get(index).getLocation()))
		{
			if (patches.isSkipped(patch))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The Run tab's rows in order: each stop (its index) and each bank visit (its {@link BankStop}), the unplanned
	 * one before the current stop when it comes first, else after it.
	 */
	private List<Object> rows()
	{
		final List<Object> rows = new ArrayList<>();
		for (int i = 0; i < plan.getRoute().getStops().size(); i++)
		{
			if (extraBank != null && extraNow && i == stopIndex)
			{
				rows.add(extraBank);
			}
			rows.add(i);
			final BankStop bank = plan.bankAfter(i);
			if (bank != null)
			{
				rows.add(bank);
			}
			else if (extraBank != null && !extraNow && i == stopIndex)
			{
				rows.add(extraBank);
			}
		}
		return rows;
	}

	/** The bank visit after the current stop: the unplanned one if there is one, else the planned one, or null. */
	private BankStop bankAfterStop()
	{
		return extraBank != null && !extraNow ? extraBank : plan.bankAfter(stopIndex);
	}

	/** The bank visit under way, or null. */
	private BankStop activeBank()
	{
		return !banking ? null : extraBank != null ? extraBank : plan.bankAfter(stopIndex);
	}

	/** The unplanned bank visit ticked away by hand: carry on without the missing items, and don't ask again. */
	private void declineExtraBank()
	{
		log.debug("Unplanned bank visit ticked by hand");
		declinedExtra = true;
		clearExtraBank();
		banking = false;
		evaluate();
	}

	private void clearExtraBank()
	{
		extraBank = null;
		extraNow = false;
		movedVisit = -1;
		checkedHoldings = null;
		missingSince = 0;
		missingAsk = null;
	}

	/**
	 * The game refused a daily teleport for having no uses left today ("You have already used all of your available
	 * teleports for today", elzerma's run 2026-10-18): if the leg in hand counts on it, it's planned again without it.
	 * Client thread.
	 */
	public void dailyUsedUp(TravelMethod method)
	{
		usedUpDaily = method;
	}

	/**
	 * The leg to the current stop, planned again once the holdings say the refused teleport has nothing left today,
	 * so the new way doesn't use it either. A new way, so it isn't timed as a sample.
	 */
	private void replanUsedUpLeg()
	{
		final TravelMethod method = usedUpDaily;
		final List<RouteStop> stops = plan.getRoute().getStops();
		if (method == null || stopIndex >= stops.size() || navigator.isArrived())
		{
			usedUpDaily = null;
			return;
		}
		final RouteStop stop = stops.get(stopIndex);
		if (stop.getMethod() != method && (stop.getAtlas() == null || stop.getAtlas().getDaily() != method))
		{
			usedUpDaily = null;
			return;
		}
		final Integer left = ChargeBudget.leftToday(method, holdingsTracker.getHoldings());
		if (left != null && left > 0)
		{
			// The holdings haven't caught up with the message yet
			return;
		}
		usedUpDaily = null;
		final RouteStop leg = runService.leg(stopIndex == 0 ? null : stops.get(stopIndex - 1).getLocation(),
			stop.getLocation());
		if (leg == null || leg.getMethod() == method)
		{
			return;
		}
		log.debug("{} has no uses left today: {} now by {}", method, stop.getLocation().getDisplayName(),
			leg.describeTravel());
		replaceLeg(stop, leg);
	}

	/**
	 * The house's teleport setting changed during the run (House Options): the leg in hand, if it goes by Teleport to
	 * House and the player hasn't teleported yet, is planned again, so its Inside or Outside follows the setting
	 * (elzerma, 2026-10-23: switched back mid-run, the stop still said Inside).
	 */
	private void replanForHouseSetting()
	{
		final boolean outside = runService.houseTeleportsOutside();
		if (houseOutside == null || houseOutside == outside)
		{
			houseOutside = outside;
			return;
		}
		houseOutside = outside;
		final List<RouteStop> stops = plan.getRoute().getStops();
		if (stopIndex >= stops.size() || navigator.isArrived() || navigator.isTeleported())
		{
			return;
		}
		final RouteStop stop = stops.get(stopIndex);
		if (stop.spell() != Spell.TELEPORT_TO_HOUSE && !stop.getDeparture().isViaHouse()
			&& (stop.getAtlas() == null || !stop.getAtlas().isViaHouse()))
		{
			return;
		}
		final RouteStop leg = runService.leg(stopIndex == 0 ? null : stops.get(stopIndex - 1).getLocation(),
			stop.getLocation());
		if (leg == null)
		{
			return;
		}
		log.debug("House teleport setting changed: {} now by {}", stop.getLocation().getDisplayName(),
			leg.describeTravel());
		replaceLeg(stop, leg);
	}

	/** The leg in hand ({@code stop}) planned again as {@code leg}, the guide starting it afresh and not timing it. */
	private void replaceLeg(RouteStop stop, RouteStop leg)
	{
		final List<RouteStop> next = new ArrayList<>(plan.getRoute().getStops());
		next.set(stopIndex, leg);
		final Route route = plan.getRoute();
		plan = new RunPlan(plan.getSelection(), new Route(Collections.unmodifiableList(next), route.getMode(),
			route.getTravelSeconds() - stop.getLegSeconds() + leg.getLegSeconds(), route.getPatchSeconds(),
			route.getStartsFrom()), plan.getSupplies(), plan.getObjectives(), plan.getBankStops());
		navigator.nextStop();
		manualSteps.clear();
		legRecorded = true;
	}

	/**
	 * "Bank for them": an unplanned bank visit for the missing items, before finishing this stop if its step needs
	 * them, else after it. Client thread.
	 */
	public void bankForMissing()
	{
		if (stage != RunView.State.RUNNING || missingAsk == null || banking || extraBank != null)
		{
			return;
		}
		missingAsk = null;
		final List<RouteStop> stops = plan.getRoute().getStops();
		final int end = partEnd();
		final boolean now = patches.currentItemMissing(view.getHighlights()) || stopIndex >= end;
		SupplyPlan needed = now ? stillNeeded(end)
			: runService.suppliesFor(plan, stops.subList(stopIndex + 1, end + 1));
		// Banking early does the planned visit now, when the rest of this part and the next fit in the inventory
		// (elzerma, 2026-10-24: "it should just move the scheduled stop up")
		final BankStop planned = plan.bankAfter(end);
		if (planned != null && !banksDone.contains(end))
		{
			final int nextEnd = partEndAfter(end);
			final SupplyPlan both = now ? stillNeeded(nextEnd)
				: runService.suppliesFor(plan, stops.subList(stopIndex + 1, nextEnd + 1));
			if (both.getSlots() <= SupplyCalculator.INVENTORY_SLOTS)
			{
				needed = both;
				movedVisit = end;
			}
		}
		startExtraBank(needed, now);
		evaluate();
	}

	/** The last stop of the part after the bank visit following stop {@code end}: up to the next visit, or the end. */
	private int partEndAfter(int end)
	{
		for (BankStop bank : plan.getBankStops())
		{
			if (bank.getAfterStop() > end)
			{
				return bank.getAfterStop();
			}
		}
		return plan.getRoute().getStops().size() - 1;
	}

	/** An unplanned visit is done: if it took a planned visit's place (banking early), that one is done too. */
	private void extraBankDone()
	{
		if (movedVisit < 0)
		{
			return;
		}
		log.debug("Planned bank visit after {} done early", plan.getRoute().getStops().get(movedVisit).getLocation()
			.getDisplayName());
		banksDone.add(movedVisit);
		// As at a planned visit: a new part
		declinedExtra = false;
		skippedItems.clear();
		missingAsk = null;
		movedVisit = -1;
	}

	/**
	 * What's still to use from this stop to the end of the part: this stop's patches not finished or planted yet, and
	 * its way here only if not arrived yet. What was used here already isn't asked for again (elzerma, 2026-10-24: at
	 * Hosidius, the house tablet that got them there and the snapdragon and nasturtium just planted were asked for).
	 */
	private SupplyPlan stillNeeded(int end)
	{
		final List<RouteStop> part = new ArrayList<>(plan.getRoute().getStops().subList(stopIndex, end + 1));
		final RouteStop here = part.get(0);
		if (legRecorded)
		{
			part.set(0, new RouteStop(here.getLocation(), null, Departure.WALK, 0, false));
		}
		final Set<Patch> used = EnumSet.noneOf(Patch.class);
		for (Patch patch : plan.getSelection().getPatches())
		{
			if (patch.getLocation() == here.getLocation() && patches.suppliesUsed(patch))
			{
				used.add(patch);
			}
		}
		return runService.suppliesFor(plan, part, used);
	}

	/**
	 * "Skip": carry on without the missing items until the next planned bank visit. A step that needs one of them is
	 * skipped (composting or paying left out, or the patch left for this run). Client thread.
	 */
	public void skipMissing()
	{
		if (missingAsk == null)
		{
			return;
		}
		final Set<SupplyLine> lines = missingAsk.counted(holdingsTracker.getHoldings()).missingLines(missingAskVault)
			.keySet();
		log.debug("Missing items skipped: {}", lines.stream().map(SupplyLine::getName).collect(Collectors.joining(", ")));
		skip(lines);
		missingAsk = null;
		evaluate();
	}

	private void skip(Collection<SupplyLine> lines)
	{
		for (SupplyLine line : lines)
		{
			for (int id : line.getItemIds())
			{
				skippedItems.add(id);
			}
		}
	}

	/** The missing items asked about, e.g. "2 x Palm sapling", while the Run tab asks; empty otherwise. */
	private List<String> missingAsked()
	{
		if (missingAsk == null)
		{
			return Collections.emptyList();
		}
		final List<String> names = new ArrayList<>();
		missingAsk.counted(holdingsTracker.getHoldings()).missingLines(missingAskVault)
			.forEach((line, n) -> names.add(n + " x " + line.getName()));
		return names;
	}

	/** The last stop of the part of the run under way: up to the next planned bank visit, or the end. */
	private int partEnd()
	{
		for (BankStop bank : plan.getBankStops())
		{
			if (bank.getAfterStop() >= stopIndex)
			{
				return bank.getAfterStop();
			}
		}
		return plan.getRoute().getStops().size() - 1;
	}

	/** What's missing of these supplies, leaving out what the player chose to do without. */
	private Map<SupplyLine, Integer> unskipped(SupplyPlan needed, boolean vaultHere)
	{
		final Map<SupplyLine, Integer> missing = new LinkedHashMap<>(needed.missingLines(vaultHere));
		missing.keySet().removeIf(line -> java.util.Arrays.stream(line.getItemIds()).anyMatch(skippedItems::contains));
		return missing;
	}

	/**
	 * An item the plan counted on has gone missing: the Run tab names it and asks whether to bank for it or skip
	 * (elzerma, 2026-10-18; it used to go straight to a bank visit). Asked when the current step needs it, or a later
	 * stop in this part does. Not when a planned bank visit comes first, the tool leprechaun has it, or the player
	 * chose to do without it; a step needing an item done without is skipped. Checked when what the player holds
	 * changes. Client thread.
	 */
	private void checkMissing(long now)
	{
		if (missingAsk != null)
		{
			// Got them after all, or they were there: nothing to ask
			if (unskipped(missingAsk.counted(holdingsTracker.getHoldings()), missingAskVault).isEmpty())
			{
				log.debug("Missing items carried after all");
				missingAsk = null;
			}
			return;
		}
		if (banking || extraBank != null || declinedExtra)
		{
			return;
		}
		final List<RouteStop> stops = plan.getRoute().getStops();
		final int end = partEnd();

		// Needed now: the step in hand can't be done; a moment's grace, as the inventory changes mid-step
		if (patches.currentItemMissing(view.getHighlights()))
		{
			if (view.getHighlights().getStepItemIds().stream().anyMatch(skippedItems::contains))
			{
				log.debug("Step skipped: its item was skipped");
				patches.skipCurrentStep();
				missingSince = 0;
				return;
			}
			if (missingSince == 0)
			{
				missingSince = now;
			}
			else if (now - missingSince >= MISSING_GRACE_MILLIS)
			{
				final List<RouteStop> part = stops.subList(stopIndex, end + 1);
				if (ask(stillNeeded(end), passesGuild(part), "now"))
				{
					return;
				}
			}
		}
		else
		{
			missingSince = 0;
		}

		// Needed later in this part
		final Holdings holdings = holdingsTracker.getHoldings();
		if (holdings == checkedHoldings || stopIndex >= end)
		{
			return;
		}
		checkedHoldings = holdings;
		final List<RouteStop> part = stops.subList(stopIndex + 1, end + 1);
		// At the Farming Guild, what the seed vault holds counts until leaving it: its step takes them (elzerma's third
		// run was asked about the later stops' seeds while standing at the vault)
		ask(runService.suppliesFor(plan, part), passesGuild(stops.subList(stopIndex, end + 1)), "for later");
	}

	/** Ask about what's missing of these supplies, if anything not done without is. */
	private boolean ask(SupplyPlan needed, boolean vaultHere, String when)
	{
		final Map<SupplyLine, Integer> missing = unskipped(needed, vaultHere);
		if (missing.isEmpty())
		{
			return false;
		}
		missingAsk = needed;
		missingAskVault = vaultHere;
		log.debug("Item missing {} at {}, asking Bank or Skip: {}", when,
			plan.getRoute().getStops().get(stopIndex).getLocation().getDisplayName(),
			missing.keySet().stream().map(SupplyLine::getName).collect(Collectors.joining(", ")));
		return true;
	}

	/** The seed vault, in the Farming Guild, is on the way, so what's in it counts. */
	private static boolean passesGuild(List<RouteStop> stops)
	{
		for (RouteStop stop : stops)
		{
			if (stop.getLocation() == Location.FARMING_GUILD)
			{
				return true;
			}
		}
		return false;
	}

	private void startExtraBank(SupplyPlan needed, boolean now)
	{
		final Location here = plan.getRoute().getStops().get(stopIndex).getLocation();
		// Banking near the stop if there's a bank, else by a bank teleport carried, else any bank
		final BankTeleport teleport = AtlasBanks.near(here) != null ? null : runService.carriedBankTeleport();
		extraBank = new BankStop(stopIndex, here, needed, teleport);
		extraNow = now;
		log.debug("Item missing, unplanned bank visit {} {}: {}", now ? "now, at" : "after", here.getDisplayName(),
			missingNames(needed));
	}

	/** What a bank visit is for, by name, for the log. */
	private static String missingNames(SupplyPlan needed)
	{
		final List<String> names = new ArrayList<>();
		for (SupplyLine line : needed.getLines())
		{
			final int leprechaun = line.getWhere().getOrDefault(Holdings.Source.LEPRECHAUN, 0);
			if (line.getGroup() != SupplyLine.Group.OPTIONAL && !line.isCoveredOtherwise()
				&& line.getCarried() + leprechaun < line.getNeed())
			{
				names.add(line.getName());
			}
		}
		return String.join(", ", names);
	}

	/** Make an earlier (or the current) stop current again. Not timed. */
	private void backTo(int index)
	{
		stopIndex = index;
		patchesDoneAt = 0;
		banking = false;
		clearExtraBank();
		manualSteps.clear();
		vaultStepText = null;
		vaultTicked = false;
		vaultItems.clear();
		navigator.nextStop();
		patches.clearCurrent();
		legRecorded = true;
	}

	public void onChatMessage(String message)
	{
		if ((stage == RunView.State.ARMED || stage == RunView.State.RUNNING) && plan != null
			&& stopIndex < plan.getRoute().getStops().size())
		{
			patches.onChatMessage(message, patchesAt(plan, plan.getRoute().getStops().get(stopIndex).getLocation()));
		}
	}

	/** Re-evaluate the run, and start the timer if the player just teleported. Call every game tick. */
	public void onGameTick()
	{
		final Player player = client.getLocalPlayer();
		final WorldPoint location = player != null ? player.getWorldLocation() : null;
		if (stage == RunView.State.ARMED && isTeleport(lastLocation, location))
		{
			lastLocation = location;
			startClock(System.currentTimeMillis());
			return;
		}
		lastLocation = location;
		trackDiveRun(location);
		evaluate();
	}

	/**
	 * Underwater turns run off (elzerma, 2026-10-25: "after diving run is toggled off"): once back up with run still off,
	 * a reminder to turn it on, until it is. Client thread.
	 */
	private void trackDiveRun(WorldPoint location)
	{
		underwater = location != null && location.getRegionID() == Patch.FOSSIL_ISLAND_SEAWEED_NORTH.getRegionId();
		if (underwater && stage == RunView.State.RUNNING)
		{
			surfacedWithRunOff = false;
			dived = true;
			return;
		}
		final boolean runOn = client.getVarpValue(VarPlayerID.OPTION_RUN) != 0;
		surfacedWithRunOff = dived && !runOn && stage == RunView.State.RUNNING && location != null;
		if (runOn || stage != RunView.State.RUNNING)
		{
			dived = false;
		}
	}

	private void evaluate()
	{
		vaultCounts = Collections.emptyList();
		bankCounts = Collections.emptyList();
		depositAllStep = false;
		if (stage == RunView.State.OFF)
		{
			view = offView();
			return;
		}
		if (stage == RunView.State.BUILDING)
		{
			final RunPlan current = runService.getPlan();
			final Holdings holdings = holdingsTracker.getHoldings();
			// The seed vault first, for what the bank is short of (elzerma, 2026-10-19); with "Take all seeds at the
			// start", the later parts' too
			final Map<Integer, Integer> targets = vaultTargets(current, holdings,
				takesAllAtStart(current, settings.getRunConfig()));
			final Map<Integer, Integer> fromVault = vaultMore(targets, holdings);
			final Map<Integer, Integer> toBank = fromVault.isEmpty()
				? toBankForLater(current, holdings) : Collections.emptyMap();
			if (!isReady(current) || !fromVault.isEmpty() || !toBank.isEmpty())
			{
				view = buildingView(current, targets, fromVault, toBank);
				return;
			}
			arm(current);
		}

		replanUsedUpLeg();
		replanForHouseSetting();
		final List<RouteStop> stops = plan.getRoute().getStops();
		final long now = System.currentTimeMillis();
		// Armed shows the first step but doesn't time legs or move on until the timer starts
		final boolean timing = stage == RunView.State.RUNNING;
		String instruction = null;
		vaultStep = false;
		wieldIds = Collections.emptySet();
		while (stopIndex < stops.size())
		{
			final RouteStop stop = stops.get(stopIndex);
			final List<Patch> here = patchesAt(plan, stop.getLocation());
			// An item the step in hand needs has gone missing: to a bank first, then back to this stop
			if (extraBank != null && extraNow && timing)
			{
				if (missingCount(extraBank.getSupplies().counted(holdingsTracker.getHoldings()), false) > 0)
				{
					visitBank(extraBank);
					patches.clearCurrent();
					instruction = bankStep(extraBank);
					break;
				}
				log.debug("Unplanned bank visit done, back to {}", stop.getLocation().getDisplayName());
				extraBankDone();
				clearExtraBank();
				banking = false;
				// Back from the bank the way the stop is reached; that trip isn't a sample
				navigator.nextStop();
				manualSteps.clear();
				legRecorded = true;
			}
			// Already done, e.g. passed again after going back to an earlier stop: nothing to travel there for
			if (!navigator.isArrived() && stopDone(stopIndex))
			{
				navigator.markArrived(now);
				legRecorded = true;
			}
			instruction = navigator.travel(stop, here, now);
			if (instruction != null)
			{
				// Swapping staves (issue #7): the one this leg needs goes on before setting off. Ticking the way there
				// done skips it.
				// Nothing can be wielded underwater ("You won't be able to swim carrying that"), so the staff for the leg
				// after the dive goes on before it (elzerma, 2026-10-25: back up to wield it cost 10 seconds)
				StaffSwaps.Swap swap = partFor(plan, stopIndex, false).getStaffSwaps().getWield().get(stop.getLocation());
				if (swap == null && stop.getLocation() == Location.FOSSIL_ISLAND_UNDERWATER
					&& stopIndex + 1 < plan.getRoute().getStops().size())
				{
					swap = partFor(plan, stopIndex + 1, false).getStaffSwaps().getWield()
						.get(plan.getRoute().getStops().get(stopIndex + 1).getLocation());
				}
				final String wield = timing && !navigator.isTeleported() && !underwater ? wieldStep(swap) : null;
				if (wield != null)
				{
					instruction = wield;
				}
				patches.clearCurrent();
				break;
			}
			if (timing && !legRecorded && navigator.nearAny(here))
			{
				recordLeg(now);
			}

			// The patches are followed first, even while the seed vault reminder shows, so planting is still seen and the
			// run can finish (elzerma: the reminder stayed up after planting and the run never stopped)
			instruction = patches.nextStep(stop, here);
			final String vault = instruction != null && stop.getLocation() == Location.FARMING_GUILD && !vaultChecked
				? fromSeedVault() : null;
			if (vault != null)
			{
				instruction = vault;
				patches.clearCurrent();
				break;
			}
			if (instruction != null || !timing)
			{
				break;
			}
			// Every patch here is done
			if (!legRecorded)
			{
				// Skipped without ever reaching the patch: not a real leg, so not a sample
				legRecorded = true;
				log.debug("Leg not timed: {} skipped without reaching it", stop.getLocation().getDisplayName());
			}
			// The time here ends now, before any bank visit (times per patch type, docs/plans/travel-rework.md)
			if (timing && patchesDoneAt == 0)
			{
				patchesDoneAt = now;
				timeStop(stop, now);
			}
			// A bank visit after this stop: the rest of the run didn't fit in one inventory, or an item went missing
			final BankStop bank = bankAfterStop();
			// A planned visit already done (or ticked) stays done, e.g. when the guide goes back past it
			if (bank != null && (bank == extraBank || !banksDone.contains(stopIndex)))
			{
				// Ready when the next part of the run, as planned at the start, is carried
				if (missingCount(bank.getSupplies().counted(holdingsTracker.getHoldings()), false) > 0)
				{
					if (!banking)
					{
						// What it takes too, so a list asking for a staff and its runes shows in the log (elzerma, 2026-10-24)
						log.debug("Bank visit after {}: deposit {}; take {}", stop.getLocation().getDisplayName(),
							depositNames(), missingNames(bank.getSupplies().counted(holdingsTracker.getHoldings())));
					}
					visitBank(bank);
					patches.clearCurrent();
					instruction = bankStep(bank);
					break;
				}
				// Timed from the stop's last patch, for checking what a visit costs (BankPlanner, T4)
				log.debug("Bank visit done after {}{}", stop.getLocation().getDisplayName(), patchesDoneAt > 0
					? ": " + Math.round((now - patchesDoneAt) / 1000.0) + " s from the last patch, " + bank.how() : "");
				banking = false;
				if (extraBank == null)
				{
					banksDone.add(stopIndex);
					// A planned visit starts a new part: missing items count again, and an earlier ask is out of date
					declinedExtra = false;
					skippedItems.clear();
					missingAsk = null;
				}
				else
				{
					extraBankDone();
				}
				legStartedAt = now;
			}
			log.debug("Stop done: {}", stop.getLocation().getDisplayName());
			if (patchesDoneAt > 0)
			{
				// Any bank visit after this stop: banking counts in no patch type's time
				bankSeconds += (now - patchesDoneAt) / 1000.0;
				patchesDoneAt = 0;
			}
			split = split(now, stopIndex);
			stopIndex++;
			navigator.nextStop();
			manualSteps.clear();
			vaultStepText = null;
			vaultTicked = false;
			vaultItems.clear();
			// What's carried is checked against the rest of the part again from here (the seed vault no longer counts)
			checkedHoldings = null;
			if (extraBank != null)
			{
				// After an unplanned visit the next leg starts at a bank: not a sample
				clearExtraBank();
				legRecorded = true;
			}
			else
			{
				legRecorded = false;
			}
			legStartedAt = now;
		}

		if (stopIndex >= stops.size())
		{
			stop(true);
			return;
		}
		if (timing)
		{
			checkMissing(now);
		}
		shortestPath.setTarget(timing ? walkTarget(stops.get(stopIndex)) : null);
		bankTab.setAttention(banking && !vaultStep && !bankTab.isTabOpen());
		vaultCounts = seedVault.isOpen() ? openVaultCounts() : Collections.emptyList();
		// At a bank visit with the bank open: the seeds and saplings the next part plants, still to take or to put back
		bankCounts = banking && !vaultStep && bankTab.isBankOpen()
			? bankCounts(bankSupplies(), holdingsTracker.getHoldings(), runService::itemName) : Collections.emptyList();
		view = runningView(instruction);
	}

	/**
	 * The counts for the inventory beside the open seed vault during the run: at the Farming Guild's stop, what the rest
	 * of this part plants; at a bank visit there, what the part being banked for plants.
	 */
	private List<InventoryCount> openVaultCounts()
	{
		final BankStop bank = activeBank();
		final Map<Integer, Integer> take;
		final Map<Integer, Integer> plant;
		if (bank != null)
		{
			if (!bank.isAtGuild())
			{
				return Collections.emptyList();
			}
			take = byItem(vaultAtBank(bank));
			final SupplyPlan part = bankSupplies();
			plant = new LinkedHashMap<>();
			for (int id : take.keySet())
			{
				plant.put(id, planted(part, id));
			}
			for (int id : vaultItems)
			{
				plant.putIfAbsent(id, planted(part, id));
			}
		}
		else
		{
			final List<RouteStop> stops = plan.getRoute().getStops();
			if (banking || stopIndex >= stops.size() || stops.get(stopIndex).getLocation() != Location.FARMING_GUILD)
			{
				return Collections.emptyList();
			}
			take = byItem(toTakeFromVault());
			plant = stillToPlant();
		}
		vaultItems.addAll(take.keySet());
		final Map<Integer, Integer> wanted = new LinkedHashMap<>();
		for (int id : vaultItems)
		{
			wanted.put(id, plant.getOrDefault(id, 0));
		}
		return vaultCounts(take, wanted);
	}

	/**
	 * Lines for the inventory beside the open seed vault (elzerma, 2026-10-18: "less to guess about how many more to
	 * click"): how many more of each item to take, then how many to put back when more were taken than wanted.
	 *
	 * @param take how many more to take, by item
	 * @param wanted how many of each item the step means to carry
	 */
	private List<InventoryCount> vaultCounts(Map<Integer, Integer> take, Map<Integer, Integer> wanted)
	{
		final Holdings carried = holdingsTracker.getHoldings().carriedOnly();
		final List<InventoryCount> lines = new ArrayList<>();
		take.forEach((id, n) ->
		{
			if (n > 0)
			{
				lines.add(new InventoryCount("Take " + n + (carried.count(id) > 0 ? " more" : "") + " x "
					+ runService.itemName(id), false));
			}
		});
		wanted.forEach((id, n) ->
		{
			final int over = carried.count(id) - n;
			if (over > 0)
			{
				lines.add(new InventoryCount("Deposit " + over + " x " + runService.itemName(id), true));
			}
		});
		return lines;
	}

	/** What the open seed vault's step still wants, for the inventory beside it; empty while it's closed. Client thread. */
	public List<InventoryCount> getVaultCounts()
	{
		return vaultCounts;
	}

	/** What the open bank's step still wants of each seed and sapling, for the inventory beside it. Client thread. */
	public List<InventoryCount> getBankCounts()
	{
		return bankCounts;
	}

	/** The step in hand is to press the bank's Deposit inventory button. Client thread. */
	public boolean isDepositAllStep()
	{
		return depositAllStep;
	}

	/**
	 * Where the current step says to walk or run, for Shortest Path to draw: the bank at a bank visit, the way on
	 * while travelling, or the next patch at a stop. Null while teleporting, riding or working a patch.
	 */
	private WorldPoint walkTarget(RouteStop stop)
	{
		if (banking)
		{
			final BankStop bank = activeBank();
			// By teleport, the bank is right where it lands; by a trip to the Farming Guild, once the player's there
			if (bank == null || bank.getTeleport() != null || bank.getTrip() != null && !inGuild())
			{
				return null;
			}
			return bankPoint(bank.getBankLocation());
		}
		if (!navigator.isArrived())
		{
			return navigator.travelTarget(stop, patchesAt(plan, stop.getLocation()));
		}
		final Patch current = patches.getCurrentPatch();
		return current != null && navigator.farFrom(current) ? scene.locationOf(current) : null;
	}

	/**
	 * Save the time for the leg to the current stop. Recorded on reaching a patch rather than when its area
	 * loads, to match the estimates, which include the walk from the teleport spot.
	 */
	private void recordLeg(long now)
	{
		legRecorded = true;
		final RouteStop stop = plan.getRoute().getStops().get(stopIndex);
		// The trip to the first stop starts from wherever the player banked
		final boolean first = stopIndex == 0;
		// After a bank visit the leg starts at the bank, not the stop before
		final String from = first ? null : plan.bankAfter(stopIndex - 1) != null ? "BANK"
			: plan.getRoute().getStops().get(stopIndex - 1).getLocation().name();
		final double seconds = (now - legStartedAt) / 1000.0;
		legs.add(new RunTimings.Leg(stop.getLocation().name(),
			first || stop.getMethod() == null ? null : stop.getMethod().name(),
			first ? "START" : stop.getDeparture().name(), seconds, now, from));
		// One per stop, for comparing estimates with real times
		log.debug("Leg timed: {} from {} by {}: {} s (estimate {} s{})", stop.getLocation().getDisplayName(),
			first ? "the start" : from, stop.describeTravel(), Math.round(seconds), Math.round(stop.getLegSeconds()),
			first ? ", first leg" : "");
	}

	/**
	 * At the Farming Guild: what the run still needs that's only in the seed vault, as a step ("Take 3 maple
	 * saplings from the seed vault"), or null once it's all carried.
	 */
	private String fromSeedVault()
	{
		final Map<SupplyLine, Integer> take = toTakeFromVault();
		if (take.isEmpty())
		{
			vaultChecked = true;
			return null;
		}
		final String amounts = amounts(byItem(take));
		if (vaultStepText == null)
		{
			vaultStepText = "Take " + amounts + " from the seed vault";
		}
		return "Take " + amounts + " from the seed vault (next to the guild's bank)";
	}

	/**
	 * What the Farm run section at the top of the seed vault shows while a run is being built or done (elzerma: the
	 * whole time, not only at the guild's own step): each seed and sapling the rest of the run plants, how many, and
	 * whether they're carried yet, in the bank tab's order. The whole run even when it banks partway, as "Take all
	 * seeds at the start" takes them all from the vault; with "Bank at the Farming Guild", only the part being
	 * gathered or banked for, as the rest comes from the vault at the guild's bank. While gathering, only what's to
	 * come out of the vault, with how many to have on leaving it ({@link #vaultTargets}). Empty with no run.
	 */
	public List<SeedVaultSection.Seed> seedVaultSeeds()
	{
		final RunPlan shown = stage == RunView.State.BUILDING ? runService.getPlan() : plan;
		if (stage == RunView.State.OFF || shown == null)
		{
			return Collections.emptyList();
		}
		if (stage == RunView.State.BUILDING)
		{
			final Holdings holdings = holdingsTracker.getHoldings();
			final Holdings carried = holdings.carriedOnly();
			final Map<Integer, Integer> vault = holdings.in(Holdings.Source.SEED_VAULT);
			final List<SeedVaultSection.Seed> seeds = new ArrayList<>();
			vaultTargets(shown, holdings, takesAllAtStart(shown, settings.getRunConfig())).forEach((id, n) ->
				seeds.add(new SeedVaultSection.Seed(id, n, carried.count(id) >= n ? SupplyLine.Status.CARRIED
					: carried.count(id) + vault.getOrDefault(id, 0) >= n ? SupplyLine.Status.IN_STORAGE
					: SupplyLine.Status.MISSING)));
			return seeds;
		}
		final boolean running = stage == RunView.State.RUNNING;
		final boolean partOnly = !shown.getBankStops().isEmpty() && RunService.guildBanking(settings.getRunConfig());
		final int at = running ? stopIndex : 0;
		final boolean atBank = running && (banking || banksDone.contains(stopIndex));
		final int[] range = partOnly ? partStops(shown, at, atBank) : new int[]{0, shown.getRoute().getStops().size()};
		final Set<Location> inPart = EnumSet.noneOf(Location.class);
		for (RouteStop stop : shown.getRoute().getStops().subList(range[0], range[1]))
		{
			inPart.add(stop.getLocation());
		}
		// The bank tab's order: each part's seeds and saplings, then anything else planted
		final Map<Integer, Integer> need = new LinkedHashMap<>();
		final List<SupplyPlan> parts = new ArrayList<>();
		if (partOnly)
		{
			parts.add(partFor(shown, at, atBank));
		}
		else
		{
			parts.add(shown.getSupplies());
			for (BankStop bank : shown.getBankStops())
			{
				parts.add(bank.getSupplies());
			}
		}
		for (SupplyPlan part : parts)
		{
			for (SupplyLine line : part.getLines())
			{
				if (line.getGroup() == SupplyLine.Group.SEEDS && line.getItemIds() != null)
				{
					for (int id : line.getItemIds())
					{
						need.putIfAbsent(id, 0);
					}
				}
			}
		}
		for (Patch patch : shown.getSelection().getPatches())
		{
			final Crop crop = shown.getSupplies().getPlantings().get(patch);
			// What's planted already has been used
			if (crop != null && inPart.contains(patch.getLocation())
				&& (stage == RunView.State.BUILDING || !patches.isDone(patch)))
			{
				need.merge(crop.getPlantItemId(), crop.getSeedsPerPatch(), Integer::sum);
			}
		}
		final Holdings holdings = holdingsTracker.getHoldings();
		final Holdings carried = holdings.carriedOnly();
		// With "Take all seeds at the start", the later parts' seeds wait in the bank for the bank visit, so before the
		// run starts those count as done here
		final boolean takeAll = stage != RunView.State.RUNNING && takesAllAtStart(shown, settings.getRunConfig());
		final Map<Integer, Integer> bank = holdings.in(Holdings.Source.BANK);
		final List<SeedVaultSection.Seed> seeds = new ArrayList<>();
		need.forEach((id, n) ->
		{
			if (n > 0)
			{
				int ready = carried.count(id);
				if (takeAll)
				{
					int later = 0;
					for (BankStop stop : shown.getBankStops())
					{
						later += planted(stop.getSupplies(), id);
					}
					ready += Math.min(bank.getOrDefault(id, 0), later);
				}
				seeds.add(new SeedVaultSection.Seed(id, n, ready >= n ? SupplyLine.Status.CARRIED
					: holdings.count(id) >= n ? SupplyLine.Status.IN_STORAGE : SupplyLine.Status.MISSING));
			}
		});
		return seeds;
	}

	/** At the Farming Guild: each line this part of the run still needs from the seed vault, with how many to take. */
	private Map<SupplyLine, Integer> toTakeFromVault()
	{
		final Map<SupplyLine, Integer> take = new LinkedHashMap<>();
		final Holdings carried = holdingsTracker.getHoldings().carriedOnly();
		final Map<Integer, Integer> plant = stillToPlant();
		for (SupplyLine line : bankSupplies().getLines())
		{
			final int vault = line.getWhere().getOrDefault(Holdings.Source.SEED_VAULT, 0);
			if (vault <= 0 || line.getGroup() == SupplyLine.Group.OPTIONAL)
			{
				continue;
			}
			int needed = 0;
			for (int id : line.getItemIds())
			{
				needed += plant.getOrDefault(id, 0);
			}
			final int stillNeeded = needed - carried.countAny(line.getItemIds());
			if (stillNeeded > 0)
			{
				take.put(line, Math.min(vault, stillNeeded));
			}
		}
		return take;
	}

	/**
	 * The seeds and saplings still to plant in this part of the run, by item: from the current stop's patches not
	 * done yet to the next planned bank visit. So a run setting off from the Farming Guild takes the whole part's seeds
	 * from its vault (elzerma's third run was told 1 Guam seed of the 7 the part plants), and one passing it only what
	 * it still lacks (elzerma: "Take 2 x Avantoe seed" with one patch left and the seed in their inventory).
	 */
	private Map<Integer, Integer> stillToPlant()
	{
		final Map<Integer, Integer> plant = new LinkedHashMap<>();
		final List<RouteStop> stops = plan.getRoute().getStops();
		for (int i = stopIndex; i <= partEnd(); i++)
		{
			for (Patch patch : patchesAt(plan, stops.get(i).getLocation()))
			{
				final Crop crop = plan.getSupplies().getPlantings().get(patch);
				if (crop != null && !patches.isDone(patch))
				{
					plant.merge(crop.getPlantItemId(), crop.getSeedsPerPatch(), Integer::sum);
				}
			}
		}
		return plant;
	}

	private static boolean contains(int[] ids, int id)
	{
		if (ids == null)
		{
			return false;
		}
		for (int each : ids)
		{
			if (each == id)
			{
				return true;
			}
		}
		return false;
	}

	private static List<Patch> patchesAt(RunPlan plan, Location location)
	{
		final List<Patch> here = new ArrayList<>();
		for (Patch patch : plan.getSelection().getPatches())
		{
			if (patch.getLocation() == location)
			{
				here.add(patch);
			}
		}
		return here;
	}

	private RunView runningView(String instruction)
	{
		final List<RunView.Stop> stopViews = new ArrayList<>();
		final List<RouteStop> stops = plan.getRoute().getStops();
		for (Object row : rows())
		{
			if (row instanceof BankStop)
			{
				stopViews.add(bankRow((BankStop) row, instruction));
				continue;
			}
			final int i = (Integer) row;
			final RouteStop stop = stops.get(i);
			// At a bank visit before finishing the stop, the stop waits; after it, the stop is done
			// Stops ahead can be done already too, after going back to an earlier one
			final RunView.StopStatus status = i < stopIndex || (i == stopIndex && banking && !extraNow)
				|| (i > stopIndex && stopDone(i)) ? RunView.StopStatus.DONE
				: i == stopIndex && !banking ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			stopViews.add(new RunView.Stop(stop.getLocation().getDisplayName(), stop.describeTravel(), status,
				status == RunView.StopStatus.CURRENT ? instruction : null,
				plan.getObjectives().getOrDefault(stop.getLocation(), Collections.emptyList()),
				status == RunView.StopStatus.CURRENT ? steps(stop) : Collections.emptyList(), false,
				status == RunView.StopStatus.DONE && stopTickedByHand(i)));
		}
		if (instruction == null)
		{
			instruction = "Head to your next stop";
		}
		// Before leaving and at each bank: what to put on for this part of the run. On the way, the staves swapped
		// between are left to their own step (issue #7).
		final Set<Integer> swapped = new HashSet<>(stage == RunView.State.RUNNING && !banking
			? partFor(plan, stopIndex, false).getStaffSwaps().getItemIds() : Collections.emptySet());
		// The diving gear only for the dive, not on the way to the stops after it; on the way to the dive the outfit isn't
		// asked for, as the gear takes its hood's and cape's slots (elzerma, 2026-10-25: "Wear: Graceful outfit" in diving gear)
		final boolean dive = nextStopIs(Location.FOSSIL_ISLAND_UNDERWATER);
		if (!dive)
		{
			swapped.addAll(DIVING_GEAR);
		}
		final Outfit outfit = settings.getRunConfig().getOutfit();
		if (dive)
		{
			swapped.addAll(piecesUnderDivingGear(outfit));
		}
		final BankGuide.Wear wear = !wieldIds.isEmpty() ? new BankGuide.Wear(null, wieldIds)
			: stage == RunView.State.ARMED || banking || patches.getCurrentPatch() == null
			? BankGuide.wear(bankSupplies(), holdingsTracker.getHoldings(), outfit, swapped)
			: BankGuide.Wear.NONE;
		final Highlights highlights = highlights(wear);
		return new RunView(stage, startedAt, stopViews, instruction, false, null, null,
			reminders(highlights), wear.getText(), highlights, split, missingAsked());
	}

	/** A bank visit's row: planned, or unplanned because an item went missing. */
	private RunView.Stop bankRow(BankStop bank, String instruction)
	{
		final int i = bank.getAfterStop();
		final RunView.StopStatus status;
		if (bank == extraBank)
		{
			status = banking ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
		}
		else
		{
			status = banksDone.contains(i) ? RunView.StopStatus.DONE
				: i == stopIndex && banking && extraBank == null ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
		}
		final boolean current = status == RunView.StopStatus.CURRENT;
		return new RunView.Stop(bank.title(), bank == extraBank && movedVisit < 0 ? "Get what's missing" : "Restock for the rest of the run",
			status, current ? instruction : null, Collections.emptyList(),
			current ? bankSteps(bank) : Collections.emptyList(), true,
			bank != extraBank && manualBanks.contains(i));
	}

	/** What the bank visit says to deposit, by name, for the log. */
	private String depositNames()
	{
		final List<String> names = depositList();
		return names.isEmpty() ? "nothing" : String.join(", ", names);
	}

	private List<String> depositList()
	{
		final List<String> names = new ArrayList<>();
		final SupplyPlan part = extraBank != null ? extraBank.getSupplies() : partFor(plan, stopIndex, true);
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> extras = BankGuide.extras(part, holdings);
		for (int id : BankGuide.toDeposit(part, holdings, id -> id))
		{
			// Seeds and saplings beyond what the next part plants: how many, and how many to keep
			names.add(extras.containsKey(id) ? keeping(Collections.singletonMap(id, extras.get(id)), holdings)
				: runService.itemName(id));
		}
		return names;
	}

	/** At a bank visit partway through a run: what to deposit, by name, for the bank tab; empty otherwise. */
	public List<String> depositNow()
	{
		return stage == RunView.State.RUNNING && banking && plan != null ? depositList() : Collections.emptyList();
	}

	/**
	 * A bank visit, one step at a time (decision 8.7): get to the bank, open the Farm run tab (it flashes), then
	 * deposit what's outlined and take what the tab shows. At the Farming Guild's bank, what only its seed vault has
	 * comes from the vault last.
	 */
	private String bankStep(BankStop bank)
	{
		vaultStep = false;
		if (!bankProgress[0])
		{
			if (bank.getTrip() != null)
			{
				// By fairy ring, with the staff for it swapped out (issue #7)
				final String wield = SupplyCalculator.usesFairyRing(bank.getTrip())
					? wieldStep(partFor(plan, stopIndex, false).getStaffSwaps().getFairyRing()) : null;
				return (wield != null ? wield + ", then go" : "Go") + " to the Farming Guild's bank: "
					+ bank.getTrip().describeWay();
			}
			return bank.getTeleport() != null ? "Use your " + bank.getTeleport().getItemName() + " to teleport to "
				+ bank.getTeleport().getBankName() : bank.title() + ": walk to the bank";
		}
		// Issue #8: with the vault open and its view off, or once the bank has given everything else
		if (bank.isAtGuild() && seedVault.isCalling())
		{
			vaultStep = true;
			return "Click the flashing button at the top of the seed vault";
		}
		final Map<SupplyLine, Integer> vault = vaultAtBank(bank);
		if (!vault.isEmpty() && missingCount(bankSupplies(), false) <= vault.size())
		{
			vaultStep = true;
			return "Take " + amounts(byItem(vault)) + " from the seed vault (next to the bank)";
		}
		if (!bankTab.isBankOpen())
		{
			return "Open the bank";
		}
		if (!bankTab.isTabOpen())
		{
			return "Deposit the outlined items, then click the flashing Farm run tab at the top of the bank";
		}
		return "Deposit the outlined items, then take what the Farm run tab shows";
	}

	/** At the Farming Guild's bank: what the part being banked for still needs that only the seed vault has. */
	private Map<SupplyLine, Integer> vaultAtBank(BankStop bank)
	{
		return bank.isAtGuild() ? bankSupplies().onlyInVault() : Collections.emptyMap();
	}

	/**
	 * At a bank visit this tick: a new visit starts with nothing done; then what the game shows is added (at the
	 * bank, the bank open, the Farm run tab open) and stays done until the visit ends.
	 */
	private void visitBank(BankStop bank)
	{
		if (!banking)
		{
			java.util.Arrays.fill(bankProgress, false);
			java.util.Arrays.fill(bankTicked, false);
		}
		banking = true;
		final boolean open = bankTab.isBankOpen();
		bankProgress[0] |= open || atBank(bank);
		bankProgress[1] |= open;
		bankProgress[2] |= open && bankTab.isTabOpen();
	}

	/** In the Farming Guild's area, where its bank and seed vault are. */
	private boolean inGuild()
	{
		final Player player = client.getLocalPlayer();
		return player != null && player.getWorldLocation().getRegionID() == Patch.FARMING_GUILD_HERB.getRegionId();
	}

	/** Close to this visit's bank chest or booth: where its teleport lands, the bank by the stop, or the guild's. */
	private boolean atBank(BankStop bank)
	{
		final WorldPoint point = bank.getTeleport() != null ? bank.getTeleport().getBankPoint()
			: bankPoint(bank.getBankLocation());
		final Player player = client.getLocalPlayer();
		return point != null && player != null && player.getWorldLocation().getPlane() == point.getPlane()
			&& player.getWorldLocation().distanceTo2D(point) <= AT_BANK_TILES;
	}

	/** The bank booth or chest by a stop (the travel atlas's), or null if it has none. */
	private static WorldPoint bankPoint(Location location)
	{
		final AtlasBanks.Bank bank = AtlasBanks.near(location);
		return bank == null ? null : bank.getPoint();
	}

	/**
	 * A bank visit step ticked or unticked by hand: the first three move the guide on (or back), the last carries on
	 * without everything from the bank.
	 */
	private void setBankStepTicked(int step, boolean ticked)
	{
		if (step >= bankProgress.length)
		{
			if (ticked && extraBank != null)
			{
				declineExtraBank();
			}
			else if (ticked)
			{
				skipBank(stopIndex);
			}
			return;
		}
		if (ticked)
		{
			// Ticking a step ticks those before it too, as the visit goes in order
			for (int i = 0; i <= step; i++)
			{
				bankTicked[i] |= !bankProgress[i];
				bankProgress[i] = true;
			}
		}
		else if (bankTicked[step])
		{
			for (int i = step; i < bankProgress.length; i++)
			{
				if (bankTicked[i])
				{
					bankProgress[i] = false;
					bankTicked[i] = false;
				}
			}
		}
		evaluate();
	}

	/** A bank visit's steps, as {@link #bankStep} goes through them, for the bank's row on the Run tab. */
	private List<RunView.Step> bankSteps(BankStop bank)
	{
		final String[] texts = {bank.getTeleport() != null || bank.getTrip() != null ? capitalised(bank.how())
			: "Walk to the bank", "Open the bank", "Open the Farm run tab"};
		final List<RunView.Step> steps = new ArrayList<>();
		boolean current = false;
		for (int i = 0; i < texts.length; i++)
		{
			final RunView.StopStatus status = bankProgress[i] ? RunView.StopStatus.DONE
				: !current ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			current |= !bankProgress[i];
			steps.add(new RunView.Step(texts[i], status, false, bankProgress[i] && bankTicked[i]));
		}
		// At the Farming Guild's bank, the seed vault comes last (issue #8)
		final Map<SupplyLine, Integer> vault = vaultAtBank(bank);
		final boolean vaultNow = !vault.isEmpty() && missingCount(bankSupplies(), false) <= vault.size();
		steps.add(new RunView.Step("Deposit and take the marked items", current ? RunView.StopStatus.PENDING
			: vaultNow ? RunView.StopStatus.DONE : RunView.StopStatus.CURRENT, false, false));
		if (!vault.isEmpty())
		{
			steps.add(new RunView.Step("Take the seeds from the seed vault", vaultNow ? RunView.StopStatus.CURRENT
				: RunView.StopStatus.PENDING, false, false));
		}
		return steps;
	}

	/**
	 * Gathering supplies, one step at a time, in the same order every time (elzerma, 2026-10-19: "rigid process"):
	 * what the bank is short of from the seed vault; then at the bank everything deposited, the Farm run tab opened and
	 * what it marks taken; then any seeds and saplings for the run's later parts taken too many put back.
	 *
	 * @param fromBank lines still to get from the bank (or other storage) while it has some
	 * @param fromVault how many more of each seed and sapling to take from the seed vault
	 * @param toBank seeds and saplings for the later parts that are carried, by item, with how many to put in the bank
	 * @param nowhere what's still short with none left anywhere the player keeps things, e.g. "6 x White lily seed"
	 */
	private String gatheringStep(int fromBank, Map<Integer, Integer> fromVault, Map<Integer, Integer> toBank,
		List<String> nowhere)
	{
		// At the seed vault with its Farm Run Autopilot view off, while it holds something the run needs
		if (seedVault.isCalling())
		{
			return "Farm run: click the flashing button at the top of the seed vault";
		}
		if (!fromVault.isEmpty())
		{
			return "Farm run: first take " + amounts(fromVault) + " from the seed vault in the Farming Guild, beside its "
				+ "bank";
		}
		if (bankToDo(fromBank, toBank))
		{
			if (!depositedAll)
			{
				return bankTab.isBankOpen() ? "Farm run: deposit everything with the outlined Deposit inventory button"
					: "Farm run: open a bank";
			}
			if (fromBank > 0)
			{
				final String items = fromBank + (fromBank == 1 ? " item" : " items");
				if (!bankTab.isBankOpen())
				{
					return "Farm run: open a bank to get " + items;
				}
				return bankTab.isTabOpen() ? "Farm run: take the " + items + " marked in red or yellow"
					: "Farm run: click the flashing Farm run tab at the top of the bank";
			}
			return "Farm run: deposit " + keeping(toBank, holdingsTracker.getHoldings()) + ", for later in the run";
		}
		return nowhere.isEmpty() ? null
			: "Farm run: you don't have " + String.join(", ", nowhere) + ": get them, or press Start now to go without";
	}

	/** Something's still to do at the bank: items to take from it, or seeds and saplings for later to put back. */
	private static boolean bankToDo(int fromBank, Map<Integer, Integer> toBank)
	{
		return fromBank > 0 || !toBank.isEmpty();
	}

	/**
	 * The current stop's steps in order, each done, current or still to do (elzerma: show that a stop has several
	 * steps): the way there and any journey steps after it (a boat, Elkoy), then each patch's plan.
	 */
	private List<RunView.Step> steps(RouteStop stop)
	{
		final List<RunView.Step> steps = new ArrayList<>();
		final boolean arrived = navigator.isArrived();
		// The way there, then each journey step after it (a boat, Elkoy) as its sub-steps
		for (int i = 0; i < travelSteps(stop); i++)
		{
			final RunView.StopStatus status;
			if (arrived || (i == 0 && navigator.isTeleported()) || (i > 0 && i <= navigator.getJourneyStep()))
			{
				status = RunView.StopStatus.DONE;
			}
			else if (i == 0 ? !navigator.isTeleported() : navigator.isTeleported() && i == navigator.getJourneyStep() + 1)
			{
				status = RunView.StopStatus.CURRENT;
			}
			else
			{
				status = RunView.StopStatus.PENDING;
			}
			final String text = i == 0 ? stop.describeWay()
				: capitalised(stop.rides().get(i - 1).getBrief());
			steps.add(new RunView.Step(text, status, i > 0, status == RunView.StopStatus.DONE && manualSteps.contains(i)));
		}
		// At the Farming Guild, the seed vault comes first (elzerma: say at the start what the guide is doing)
		if (vaultSteps(stop) > 0)
		{
			final RunView.StopStatus status = vaultChecked ? RunView.StopStatus.DONE
				: arrived ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			steps.add(new RunView.Step(vaultStepText, status, false, vaultChecked && vaultTicked));
		}
		// Each patch's plan, in the order the guide takes them; the objectives were made in the plan's order
		final List<Patch> inPlan = patchesAt(plan, stop.getLocation());
		final List<String> objectives = plan.getObjectives().getOrDefault(stop.getLocation(), Collections.emptyList());
		final List<Patch> here = patches.order(inPlan);
		shownPatches = here;
		shownPatchesStop = stopIndex;
		for (Patch patch : here)
		{
			final int i = inPlan.indexOf(patch);
			final RunView.StopStatus status = patches.isDone(patch) ? RunView.StopStatus.DONE
				: arrived && patch == patches.getCurrentPatch() ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			steps.add(new RunView.Step(i < objectives.size() ? objectives.get(i) : patch.getDisplayName(), status, false,
				status == RunView.StopStatus.DONE && patches.isSkipped(patch)));
		}
		return steps;
	}

	private static String capitalised(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/** What the current step points at: the patch and items to use there, or the teleport item while travelling. */
	private Highlights highlights(BankGuide.Wear wear)
	{
		if (!wieldIds.isEmpty())
		{
			return new Highlights(null, false, null, Collections.emptySet(), null, wieldIds);
		}
		if (banking)
		{
			// Teleporting to the bank: the bank teleport is the item to use, as for any other teleport (elzerma), with
			// its option to pick, e.g. the ring of dueling's Castle Wars (elzerma, 2026-10-21: it wasn't coloured)
			final BankStop bank = activeBank();
			if (bank != null && bank.getTeleport() != null && !bankProgress[0])
			{
				final Set<Integer> teleport = new LinkedHashSet<>();
				for (int id : bank.getTeleport().getItemIds())
				{
					teleport.add(id);
				}
				return new Highlights(null, false, null, teleport, null, wear.getItemIds(), Collections.emptyList(),
					bank.getTeleport().getOption());
			}
			// On the way to the Farming Guild's bank: what gets the player there, as for a stop (issue #8)
			if (bank != null && bank.getTrip() != null && !bankProgress[0])
			{
				return travelHighlights(bank.getTrip(), wear, null, false);
			}
			return new Highlights(null, false, null, BankGuide.toDeposit(bankSupplies(), holdingsTracker.getHoldings(),
				runService::notedId), null, wear.getItemIds());
		}
		final Highlights atPatch = patches.highlights();
		if (atPatch != null)
		{
			// Armed at the first stop's patch, what to put on is still marked (elzerma, 2026-10-19: the Wear labels went
			// once the run armed itself there)
			return wear.getItemIds().isEmpty() ? atPatch : atPatch.withWear(wear.getItemIds());
		}
		final RouteStop stop = plan.getRoute().getStops().get(stopIndex);
		return travelHighlights(stop, wear, navigator.travelNpc(stop), navigator.isTeleported());
	}

	/** The fairy ring code on the way to the stop in hand (e.g. "CIP"), for the travel log; null if none. Client thread. */
	String fairyRingCode()
	{
		if (stage != RunView.State.RUNNING || banking || plan == null || patches.getCurrentPatch() != null
			|| stopIndex >= plan.getRoute().getStops().size())
		{
			return null;
		}
		return plan.getRoute().getStops().get(stopIndex).fairyRingCode();
	}

	/**
	 * The outfit's pieces worn in the fishbowl helmet's or diving apparatus's slot, left off for the dive; the rest are
	 * still asked for (elzerma, 2026-10-25: "equip what you can, with the apparatus and helmet"). Client thread.
	 */
	private Set<Integer> piecesUnderDivingGear(Outfit outfit)
	{
		final Set<Integer> slots = new HashSet<>();
		for (int id : DIVING_GEAR)
		{
			if (slot(id) >= 0)
			{
				slots.add(slot(id));
			}
		}
		final Set<Integer> ids = new HashSet<>();
		for (int[] piece : outfit.getPieces())
		{
			if (piece.length == 0 || !slots.contains(slot(piece[0])))
			{
				continue;
			}
			for (int id : piece)
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/**
	 * The option the step needs in a chatbox list: the ride's line (a rowboat's), or the place in the teleport item's list.
	 * Null if none. Client thread.
	 */
	String chatOption()
	{
		final JourneyStep ride = currentRide();
		if (ride != null && ride.getDialog() != null)
		{
			return ride.getDialog();
		}
		return view != null && view.getHighlights() != null ? view.getHighlights().getTeleportOption() : null;
	}

	/** Back up from the dive with run still off, for the run orb's vine. Client thread. */
	boolean isSurfacedWithRunOff()
	{
		return surfacedWithRunOff;
	}

	/** An item's equipment slot, or -1 if it isn't worn or its stats aren't known. Client thread. */
	private int slot(int itemId)
	{
		final ItemStats stats = itemManager.getItemStats(itemId);
		return stats != null && stats.getEquipment() != null ? stats.getEquipment().getSlot() : -1;
	}

	/** Whether the stop in hand, or the one after a bank visit, is at this place. */
	private boolean nextStopIs(Location location)
	{
		if (plan == null)
		{
			return false;
		}
		final List<RouteStop> stops = plan.getRoute().getStops();
		for (int i = stopIndex; i < stops.size() && i <= stopIndex + (banking ? 1 : 0); i++)
		{
			if (stops.get(i).getLocation() == location)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The ride the guide is on (a boat, an NPC, a portal), for colouring its right-click option (elzerma, 2026-10-24:
	 * "Dive" on the rowboat, Captain Barnaby's, the barge guard's); null when not travelling. Client thread.
	 */
	JourneyStep currentRide()
	{
		if (stage != RunView.State.RUNNING || banking || plan == null || stopIndex >= plan.getRoute().getStops().size())
		{
			return null;
		}
		return navigator.currentRide(plan.getRoute().getStops().get(stopIndex));
	}

	/**
	 * Travelling: the item, spell and NPC that get the player there. Main item first: reminders name it. Once landed,
	 * the teleport's item and spell are done with (elzerma, 2026-10-18): only the NPC and a travel map's place to pick.
	 */
	private Highlights travelHighlights(RouteStop stop, BankGuide.Wear wear, String npc, boolean landed)
	{
		if (landed)
		{
			return new Highlights(null, false, npc, Collections.emptySet(), null, wear.getItemIds(), stop.menuPicks(),
				null);
		}
		final Set<Integer> items = new LinkedHashSet<>();
		if (stop.getDeparture() == Departure.DIRECT && stop.getMethod() != null)
		{
			travelItems(stop.getMethod(), items);
		}
		final AtlasLeg atlas = stop.getAtlas();
		if (atlas != null)
		{
			// A way from the travel atlas: its teleport item, the plugin's or one it doesn't track (a tablet...)
			if (atlas.getItem() != null)
			{
				for (int id : atlas.getItem().getItemIds())
				{
					items.add(id);
				}
			}
			for (AtlasLeg.ItemNeed need : atlas.getItems())
			{
				if (need.isTeleport())
				{
					for (int id : need.getIds())
					{
						items.add(id);
					}
				}
			}
			if (atlas.getSpell() != null && atlas.getSpell().hasTablet())
			{
				items.add(atlas.getSpell().getTabletItemId());
			}
		}
		// The item that gets the player to the fairy ring
		final FairyRingAccess way = stop.getDeparture().getFairyRingAccess();
		if (way != null)
		{
			for (TravelItem item : way.getItems())
			{
				for (int id : item.getItemIds())
				{
					items.add(id);
				}
			}
		}
		// The spell to cast: the teleport itself, or Teleport to House for routes through the house
		Spell spell = null;
		if (stop.getDeparture() == Departure.DIRECT && stop.getMethod() != null)
		{
			spell = stop.getMethod().getSpell();
		}
		else if (atlas != null && atlas.getSpell() != null)
		{
			spell = atlas.getSpell();
		}
		else if (stop.getDeparture().isViaHouse() || atlas != null && atlas.isViaHouse())
		{
			spell = Spell.TELEPORT_TO_HOUSE;
			// Whatever gets the player home: construction cape, max cape or a house tablet
			for (TravelItem cape : new TravelItem[]{TravelItem.CONSTRUCTION_CAPE, TravelItem.MAX_CAPE})
			{
				for (int id : cape.getItemIds())
				{
					items.add(id);
				}
			}
			items.add(Spell.TELEPORT_TO_HOUSE.getTabletItemId());
		}
		// The spell or its tablet, as the supply list takes it, not both (elzerma, 2026-10-24: with a tablet carried, the
		// spell was still outlined in the spellbook): the tablet when the player has one and doesn't cast from runes
		if (spell != null && spell.hasTablet())
		{
			if (SupplyCalculator.usesTablet(spell, stop.getLocation(), settings.getRunConfig(), runService.access(),
				holdingsTracker.getHoldings()))
			{
				spell = null;
			}
			else
			{
				items.remove(spell.getTabletItemId());
			}
		}
		// An item to put on isn't also outlined as the teleport to use, so the two can't be confused
		items.removeAll(wear.getItemIds());
		return new Highlights(null, false, npc, items, patches.castableNow(spell), wear.getItemIds(), stop.menuPicks(),
			stop.teleportOption());
	}

	/**
	 * "Wield your mist battlestaff" while this staff is in the inventory and not wielded, with it outlined to put on;
	 * null when it's on already, or isn't carried (the missing items reminder covers that).
	 */
	private String wieldStep(StaffSwaps.Swap swap)
	{
		if (swap == null)
		{
			return null;
		}
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Set<Integer> ids = new LinkedHashSet<>();
		for (int id : swap.getItemIds())
		{
			if (holdings.in(Holdings.Source.WORN).getOrDefault(id, 0) > 0)
			{
				return null;
			}
			if (inventory.getOrDefault(id, 0) > 0)
			{
				ids.add(id);
			}
		}
		if (ids.isEmpty())
		{
			return null;
		}
		wieldIds = ids;
		return "Wield your " + swap.getName();
	}

	private static void travelItems(TravelMethod method, Set<Integer> items)
	{
		if (method.getItem() != null)
		{
			for (int id : method.getItem().getItemIds())
			{
				items.add(id);
			}
		}
		final Spell spell = method.getSpell();
		if (spell != null && spell.hasTablet())
		{
			items.add(spell.getTabletItemId());
		}
	}

	/** e.g. "Drop 4 weeds and 2 empty plant pots", or null when there's nothing to drop or it's turned off. */
	private String dropReminder()
	{
		if (!settings.getRunConfig().isRemindToDrop())
		{
			return null;
		}
		final Map<Integer, Integer> inventory = holdingsTracker.getHoldings().in(Holdings.Source.INVENTORY);
		final int weeds = inventory.getOrDefault(ItemID.WEEDS, 0);
		final int pots = inventory.getOrDefault(ItemID.PLANTPOT_EMPTY, 0);
		final int buckets = inventory.getOrDefault(ItemID.BUCKET_EMPTY, 0);
		final List<String> parts = new ArrayList<>();
		if (weeds > 0)
		{
			parts.add(weeds + (weeds == 1 ? " weed" : " weeds"));
		}
		if (pots > 0)
		{
			parts.add(pots + (pots == 1 ? " empty plant pot" : " empty plant pots"));
		}
		if (buckets > 0)
		{
			parts.add(buckets + (buckets == 1 ? " empty bucket" : " empty buckets"));
		}
		if (parts.isEmpty())
		{
			return null;
		}
		String text = parts.get(0);
		for (int i = 1; i < parts.size(); i++)
		{
			text += (i == parts.size() - 1 ? " and " : ", ") + parts.get(i);
		}
		return "Drop " + text;
	}

	/** Reminders shown under the step, one per line, or null when there are none. */
	private String reminders(Highlights highlights)
	{
		final List<String> lines = new ArrayList<>();
		final String missing = patches.missingItemReminder(patches.getCurrentPatch() != null ? highlights : null);
		if (missing != null)
		{
			lines.add(missing);
		}
		if (missingAsk != null)
		{
			// The items are named on the Run tab, by its buttons
			lines.add("Items missing: Bank for them or Skip, on the Run tab");
		}
		final String tool = patches.plantingToolReminder();
		if (tool != null)
		{
			lines.add(tool);
		}
		final String drop = dropReminder();
		if (drop != null)
		{
			lines.add(drop);
		}
		final String press = chatOptions.reminder();
		if (press != null)
		{
			lines.add(press);
		}
		if (surfacedWithRunOff)
		{
			lines.add("Turn your run back on: the dive turned it off");
		}
		final String space = patches.spaceReminder();
		if (space != null)
		{
			lines.add(space);
		}
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	/**
	 * What the bank tab and deposit outlines work from. During a run, the part of the plan fixed when the run
	 * started that the player is carrying or banking for, counted against what they hold now (so finished
	 * patches can't change it). Otherwise the live plan. Client thread.
	 */
	public SupplyPlan bankSupplies()
	{
		if ((stage == RunView.State.RUNNING || stage == RunView.State.ARMED) && plan != null)
		{
			return (extraBank != null && banking ? extraBank.getSupplies() : partFor(plan, stopIndex, banking || banksDone.contains(stopIndex)))
				.counted(holdingsTracker.getHoldings());
		}
		return runService.getSupplies();
	}

	/**
	 * The part of the run being carried at this stop, or banked for at a bank visit right after it.
	 *
	 * @param atBank at (or past) the bank visit after this stop, if there is one
	 */
	static SupplyPlan partFor(RunPlan plan, int stopIndex, boolean atBank)
	{
		SupplyPlan part = plan.getSupplies();
		for (BankStop bank : plan.getBankStops())
		{
			if (bank.getAfterStop() < stopIndex || (bank.getAfterStop() == stopIndex && atBank))
			{
				part = bank.getSupplies();
			}
		}
		return part;
	}

	/**
	 * The stops of the part of the run carried at this stop, or banked for at a bank visit right after it, as
	 * {from, to}: the same part as {@link #partFor}.
	 */
	static int[] partStops(RunPlan plan, int stopIndex, boolean atBank)
	{
		int from = 0;
		int to = plan.getRoute().getStops().size();
		for (BankStop bank : plan.getBankStops())
		{
			if (bank.getAfterStop() < stopIndex || (bank.getAfterStop() == stopIndex && atBank))
			{
				from = bank.getAfterStop() + 1;
			}
			else
			{
				to = Math.min(to, bank.getAfterStop() + 1);
			}
		}
		return new int[]{from, to};
	}

	/** Patches in the whole run per type (not only the part before the first bank). */
	static Map<PatchType, Integer> patchCounts(RunPlan plan)
	{
		final Map<PatchType, Integer> counts = new EnumMap<>(PatchType.class);
		for (Patch patch : plan.getSelection().getPatches())
		{
			counts.merge(patch.getType(), 1, Integer::sum);
		}
		return counts;
	}

	/** Nothing in game, apart from how the last run went for a few seconds. */
	private RunView offView()
	{
		bankTab.setAttention(false);
		final RunPlan current = runService.getPlan();
		final String notice = lastRun != null && System.currentTimeMillis() - finishedAt < FINISHED_NOTICE_MILLIS
			? lastRun : null;
		return new RunView(RunView.State.OFF, 0, new ArrayList<>(), notice, false, lastRun, bestTimes(current),
			null, null, Highlights.NONE, null, Collections.emptyList());
	}

	/**
	 * Gathering supplies: what's still to get and where, in order (elzerma, 2026-10-19: the seed vault, then the bank),
	 * and what to put on once it's in the inventory.
	 *
	 * @param targets how many of each seed and sapling to have on leaving the seed vault
	 * @param fromVault how many more of each to take from it
	 * @param toBank seeds for the later parts to put in the bank before the run starts, by item
	 */
	private RunView buildingView(RunPlan current, Map<Integer, Integer> targets, Map<Integer, Integer> fromVault,
		Map<Integer, Integer> toBank)
	{
		String notice = null;
		Set<Integer> outlined = Collections.emptySet();
		List<RunView.Stop> ready = Collections.emptyList();
		if (!current.getRoute().getStops().isEmpty())
		{
			final Holdings holdings = holdingsTracker.getHoldings();
			// Deposit inventory pressed once the seed vault's done: the inventory seen empty with the bank open
			if (!fromVault.isEmpty())
			{
				depositedAll = false;
			}
			else if (bankTab.isBankOpen() && holdings.in(Holdings.Source.INVENTORY).values().stream().noneMatch(n -> n > 0))
			{
				depositedAll = true;
			}
			// Still to get: from the bank (or other storage) while it has some, or from the seed vault first; else
			// nowhere the player keeps them, which is named rather than waited for (elzerma: stuck on "Take the 1 item it
			// marks" for a seed they don't have)
			int fromBank = 0;
			final List<String> nowhere = new ArrayList<>();
			for (Map.Entry<SupplyLine, Integer> e : current.getSupplies().missingLines(false).entrySet())
			{
				final SupplyLine line = e.getKey();
				final Map<Holdings.Source, Integer> where = line.getWhere();
				final int stored = line.getHave() - line.getCarried() - where.getOrDefault(Holdings.Source.LEPRECHAUN, 0)
					- where.getOrDefault(Holdings.Source.SEED_VAULT, 0);
				if (stored > 0)
				{
					fromBank++;
				}
				else if (line.getItemIds() == null
					|| java.util.Arrays.stream(line.getItemIds()).noneMatch(fromVault::containsKey))
				{
					nowhere.add(e.getValue() + " x " + line.getName());
				}
			}
			notice = gatheringStep(fromBank, fromVault, toBank, nowhere);
			if (seedVault.isOpen())
			{
				vaultCounts = vaultCounts(fromVault, targets);
			}
			final boolean deposited = fromVault.isEmpty() && depositedAll;
			if (deposited && bankTab.isBankOpen())
			{
				bankCounts = bankCounts(current.getSupplies(), holdings, runService::itemName);
			}
			depositAllStep = fromVault.isEmpty() && !depositedAll && bankToDo(fromBank, toBank) && bankTab.isBankOpen();
			ready = Collections.singletonList(new RunView.Stop("Getting ready", null, RunView.StopStatus.CURRENT,
				null, Collections.emptyList(), gatheringSteps(fromBank, fromVault, toBank, nowhere), false, false));
			// Flash the Farm run tab once everything's deposited, until it's open, while there's something to take
			bankTab.setAttention(deposited && fromBank > 0 && !bankTab.isTabOpen());
			// The seeds to put back are outlined while the bank's open, as at a bank visit
			if (deposited && fromBank <= 0 && bankTab.isBankOpen())
			{
				outlined = new LinkedHashSet<>(toBank.keySet());
			}
		}
		final List<RouteStop> stops = current.getRoute().getStops();
		final boolean atFirstStop = !stops.isEmpty() && navigator.inRange(patchesAt(current, stops.get(0).getLocation()));
		// The diving gear is put on for the dive, unless that's where the run starts (elzerma, 2026-10-25)
		final boolean diveFirst = !stops.isEmpty() && stops.get(0).getLocation() == Location.FOSSIL_ISLAND_UNDERWATER;
		final BankGuide.Wear wear = BankGuide.wear(current.getSupplies(), holdingsTracker.getHoldings(),
			settings.getRunConfig().getOutfit(), diveFirst ? piecesUnderDivingGear(settings.getRunConfig().getOutfit()) : DIVING_GEAR);
		return new RunView(RunView.State.BUILDING, 0, ready, notice, atFirstStop, null,
			bestTimes(current), null, wear.getText(), new Highlights(null, false, null, outlined, null,
			wear.getItemIds()), null, Collections.emptyList());
	}

	/**
	 * Getting ready as a checklist for the Run tab (elzerma, 2026-10-18: make clear at the start what the guide takes
	 * the player through), in the same order every time (2026-10-19): the seed vault for what the bank is short of; the
	 * bank, everything deposited and what the Farm run tab marks taken; any seeds for later taken too many put back;
	 * then what's to get elsewhere. Ticked as the plugin sees them done; what to do now is under the player, as for a
	 * stop.
	 *
	 * @param fromBank lines still to get from the bank
	 * @param fromVault how many more of each seed and sapling to take from the seed vault
	 * @param toBank seeds and saplings for the later parts that are carried, by item, with how many to put in the bank
	 * @param nowhere what's still short with none left anywhere the player keeps things: last, to get or go without
	 */
	private List<RunView.Step> gatheringSteps(int fromBank, Map<Integer, Integer> fromVault,
		Map<Integer, Integer> toBank, List<String> nowhere)
	{
		// What's still to take, kept up to date (elzerma, 2026-10-22: it named only the palm sapling, worked out before the
		// bank's maple saplings and irit seeds were known, so it never ticked); once it's all taken, a plain line, ticked
		if (!fromVault.isEmpty())
		{
			gatherVault = "Take " + amounts(fromVault) + " from the seed vault";
		}
		else if (gatherVault != null)
		{
			gatherVault = "Take the seeds and saplings from the seed vault";
		}
		// After everything's been deposited, carrying seeds for later means too many were taken
		if (!toBank.isEmpty() && depositedAll)
		{
			gatherToBank = "Deposit " + keeping(toBank, holdingsTracker.getHoldings()) + ", for later in the run";
		}

		final boolean vaultDone = fromVault.isEmpty();
		final boolean open = bankTab.isBankOpen();
		final boolean taken = vaultDone && fromBank <= 0 && (depositedAll || toBank.isEmpty());
		final List<String> texts = new ArrayList<>();
		final List<Boolean> done = new ArrayList<>();
		if (gatherVault != null)
		{
			texts.add("Go to the seed vault in the Farming Guild, beside its bank");
			done.add(vaultDone || seedVault.isOpen());
			texts.add("Click the flashing button at the top of the seed vault");
			done.add(vaultDone || seedVault.isOpen() && !seedVault.isCalling());
			texts.add(gatherVault);
			done.add(vaultDone);
		}
		// The bank, unless there's nothing to do there
		if (!vaultDone || depositedAll || bankToDo(fromBank, toBank))
		{
			texts.add("Open a bank");
			done.add(vaultDone && (open || depositedAll));
			texts.add("Deposit everything with the Deposit inventory button");
			done.add(vaultDone && depositedAll);
			texts.add("Open the Farm run tab at the top of the bank");
			done.add(taken || vaultDone && depositedAll && open && bankTab.isTabOpen());
			texts.add(taken || fromBank <= 0 ? "Take the items it marks" : "Take the " + fromBank
				+ (fromBank == 1 ? " item" : " items") + " it marks in red or yellow");
			done.add(taken);
			if (gatherToBank != null)
			{
				texts.add(gatherToBank);
				done.add(taken && toBank.isEmpty());
			}
		}
		if (!nowhere.isEmpty())
		{
			texts.add("Get " + String.join(", ", nowhere) + ", or press Start now to go without");
			done.add(false);
		}

		final List<RunView.Step> steps = new ArrayList<>();
		boolean current = false;
		for (int i = 0; i < texts.size(); i++)
		{
			final RunView.StopStatus status = done.get(i) ? RunView.StopStatus.DONE
				: !current ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			current |= !done.get(i);
			steps.add(new RunView.Step(texts.get(i), status, false, false));
		}
		return steps;
	}

	/**
	 * Everything required is carried, or can be taken from the tool leprechaun when the run starts (every
	 * location has one).
	 */
	static boolean isReady(RunPlan plan)
	{
		return !plan.getRoute().getStops().isEmpty() && missingCount(plan) == 0;
	}

	/**
	 * Required lines that are neither carried nor stored at the tool leprechaun. What's in the seed vault is taken
	 * before the run, even one starting at the Farming Guild (elzerma, 2026-10-19: the vault first, then the bank).
	 */
	static int missingCount(RunPlan plan)
	{
		return missingCount(plan.getSupplies(), false);
	}

	/** e.g. "6 x Ranarr seed, 1 x Maple sapling", by the items' own names. */
	private String amounts(Map<Integer, Integer> take)
	{
		final List<String> parts = new ArrayList<>();
		take.forEach((id, count) -> parts.add(count + " x " + runService.itemName(id)));
		return String.join(", ", parts);
	}

	/** Some of what's carried to put in the bank, with what stays, e.g. "3 x Magic sapling (keep 2)". */
	private String keeping(Map<Integer, Integer> deposit, Holdings holdings)
	{
		final List<String> parts = new ArrayList<>();
		final Holdings carried = holdings.carriedOnly();
		deposit.forEach((id, count) ->
		{
			final int keep = carried.count(id) - count;
			parts.add(count + " x " + runService.itemName(id) + (keep > 0 ? " (keep " + keep + ")" : ""));
		});
		return String.join(", ", parts);
	}

	/** How many to take of each line, by the line's item, adding up lines for the same item. */
	static Map<Integer, Integer> byItem(Map<SupplyLine, Integer> take)
	{
		final Map<Integer, Integer> items = new LinkedHashMap<>();
		take.forEach((line, count) -> items.merge(line.getItemIds()[0], count, Integer::sum));
		return items;
	}

	/** The run banks partway, and the seed vault's seeds for later are taken at the start (issue #8). */
	static boolean takesAllAtStart(RunPlan plan, RunConfig config)
	{
		return !plan.getBankStops().isEmpty() && config.isUseSeedVault()
			&& config.getVaultBanking() == VaultBanking.TAKE_ALL_AT_START;
	}

	/**
	 * What the later parts of a run that banks partway need that only the seed vault has, by item, adding up the
	 * parts: with "Take all seeds at the start" it's taken with the first part's and put in the bank for later.
	 */
	public static Map<Integer, Integer> laterFromVault(RunPlan plan)
	{
		final Map<Integer, Integer> take = new LinkedHashMap<>();
		for (BankStop bank : plan.getBankStops())
		{
			byItem(bank.getSupplies().onlyInVault()).forEach((id, n) -> take.merge(id, n, Integer::sum));
		}
		return take;
	}

	/**
	 * Of the seeds and saplings the run's later parts plant, how many are carried beyond what the first part plants:
	 * the ones for later, to put in the bank before the run starts (elzerma, 2026-10-18: take only what each part
	 * needs, whether they came from the seed vault or were carried already).
	 */
	static Map<Integer, Integer> toBankForLater(RunPlan plan, Holdings holdings)
	{
		final Set<Integer> later = new LinkedHashSet<>();
		for (BankStop bank : plan.getBankStops())
		{
			for (SupplyLine line : bank.getSupplies().getLines())
			{
				if (line.getGroup() == SupplyLine.Group.SEEDS)
				{
					for (int id : line.getItemIds())
					{
						later.add(id);
					}
				}
			}
		}
		final Map<Integer, Integer> deposit = new LinkedHashMap<>();
		final Holdings carried = holdings.carriedOnly();
		for (int id : later)
		{
			final int extra = carried.count(id) - planted(plan.getSupplies(), id);
			if (extra > 0)
			{
				deposit.put(id, extra);
			}
		}
		return deposit;
	}

	/**
	 * Seeds and saplings to bring out of the seed vault before the run (elzerma, 2026-10-19: the vault first, then the
	 * bank, so the bank holds what the run needs): of each one the run plants from the start, and with {@code all} its
	 * later parts' too, what the bank doesn't hold, when the vault holds some. Counting what's carried, it's how many to
	 * have on leaving the vault, as its section shows (it said 7 maple saplings when the bank had 1 of them).
	 */
	static Map<Integer, Integer> vaultTargets(RunPlan plan, Holdings holdings, boolean all)
	{
		final Map<Integer, Integer> plant = new LinkedHashMap<>();
		addPlanted(plan.getSupplies(), plant);
		if (all)
		{
			for (BankStop bank : plan.getBankStops())
			{
				addPlanted(bank.getSupplies(), plant);
			}
		}
		final Map<Integer, Integer> bank = holdings.in(Holdings.Source.BANK);
		final Map<Integer, Integer> vault = holdings.in(Holdings.Source.SEED_VAULT);
		final Map<Integer, Integer> targets = new LinkedHashMap<>();
		plant.forEach((id, n) ->
		{
			final int target = n - bank.getOrDefault(id, 0);
			if (target > 0 && vault.getOrDefault(id, 0) > 0)
			{
				targets.put(id, target);
			}
		});
		return targets;
	}

	/** Of those, how many more of each to take from the vault: what isn't carried yet, as far as the vault holds. */
	static Map<Integer, Integer> vaultMore(Map<Integer, Integer> targets, Holdings holdings)
	{
		final Holdings carried = holdings.carriedOnly();
		final Map<Integer, Integer> vault = holdings.in(Holdings.Source.SEED_VAULT);
		final Map<Integer, Integer> more = new LinkedHashMap<>();
		targets.forEach((id, target) ->
		{
			final int n = Math.min(target - carried.count(id), vault.getOrDefault(id, 0));
			if (n > 0)
			{
				more.put(id, n);
			}
		});
		return more;
	}

	/**
	 * Lines for the inventory beside the open bank (elzerma, 2026-10-19: the seed vault said how many, the bank
	 * didn't): of each seed and sapling this part of the run plants, how many more to take, as far as the bank holds
	 * them, then how many to put back when more are carried.
	 */
	static List<InventoryCount> bankCounts(SupplyPlan part, Holdings holdings, IntFunction<String> itemName)
	{
		final Map<Integer, Integer> plant = new LinkedHashMap<>();
		addPlanted(part, plant);
		final Holdings carried = holdings.carriedOnly();
		final Map<Integer, Integer> bank = holdings.in(Holdings.Source.BANK);
		final List<InventoryCount> lines = new ArrayList<>();
		plant.forEach((id, n) ->
		{
			final int more = Math.min(n - carried.count(id), bank.getOrDefault(id, 0));
			if (more > 0)
			{
				lines.add(new InventoryCount("Take " + more + (carried.count(id) > 0 ? " more" : "") + " x "
					+ itemName.apply(id), false));
			}
		});
		plant.forEach((id, n) ->
		{
			final int over = carried.count(id) - n;
			if (over > 0)
			{
				lines.add(new InventoryCount("Deposit " + over + " x " + itemName.apply(id), true));
			}
		});
		return lines;
	}

	/** Each seed and sapling a part of the run plants, with how many, added to {@code plant}. */
	private static void addPlanted(SupplyPlan part, Map<Integer, Integer> plant)
	{
		for (SupplyLine line : part.getLines())
		{
			if (line.getGroup() == SupplyLine.Group.SEEDS && line.getItemIds() != null && line.getItemIds().length > 0)
			{
				plant.merge(line.getItemIds()[0], line.getNeed(), Integer::sum);
			}
		}
	}

	/** How many of this seed or sapling a part of the run plants. */
	private static int planted(SupplyPlan part, int id)
	{
		int n = 0;
		for (SupplyLine line : part.getLines())
		{
			if (line.getGroup() == SupplyLine.Group.SEEDS && contains(line.getItemIds(), id))
			{
				n += line.getNeed();
			}
		}
		return n;
	}

	/** @param vaultHere the seed vault can be used here (the Farming Guild) */
	static int missingCount(SupplyPlan supplies, boolean vaultHere)
	{
		return supplies.missingCount(vaultHere);
	}
}
