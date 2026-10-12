package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.route.BankStop;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;

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
	/** Moving further than this in one tick can only be a teleport. */
	private static final int TELEPORT_TILES = 30;

	private final Client client;
	private final RunService runService;
	private final HoldingsTracker holdingsTracker;
	private final SettingsStore settings;
	private final RunTimings timings;
	private final SceneTracker scene;
	/** Draws the walk for "run to" steps, if Shortest Path is installed. */
	private final ShortestPathBridge shortestPath;
	private final Navigator navigator;
	private final PatchGuide patches;

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
	private boolean banked;
	/** At the Farming Guild, everything needed from the seed vault has been taken (or skipped). */
	private boolean vaultChecked;
	/** The time for the leg to the current stop has been saved. */
	private boolean legRecorded;
	private long legStartedAt;
	private final List<RunTimings.Leg> legs = new ArrayList<>();
	private long finishedAt;
	private String lastRun;
	private volatile RunView view = RunView.OFF_VIEW;

	@Inject
	RunSession(Client client, RunService runService, HoldingsTracker holdingsTracker, SettingsStore settings,
		RunTimings timings, SceneTracker scene, ShortestPathBridge shortestPath, Navigator navigator,
		PatchGuide patches)
	{
		this.client = client;
		this.runService = runService;
		this.holdingsTracker = holdingsTracker;
		this.settings = settings;
		this.timings = timings;
		this.scene = scene;
		this.shortestPath = shortestPath;
		this.navigator = navigator;
		this.patches = patches;
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
		stage = RunView.State.ARMED;
		startedAt = 0;
		stopIndex = 0;
		navigator.reset();
		patches.reset(plan);
		legs.clear();
		lastRun = null;
		banking = false;
		banked = false;
		vaultChecked = false;
		legRecorded = false;
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
		timings.save(new ArrayList<>(legs), new RunTimings.Run(startedAt, seconds, stopIndex, finished, makeup));
		finishedAt = now;
		lastRun = (finished ? "Run finished in " : "Run stopped after ") + GuidanceOverlay.clock((long) seconds);
		if (finished)
		{
			lastRun += rank(timings.best(makeup, BEST_TIMES), seconds, RunTimings.makeupName(counts));
		}
		log.debug("Run ended: {}", lastRun);
		plan = null;
		patches.reset(null);
		// Replan with the newly learned leg times
		runService.markDirty();
		evaluate();
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
		final Map<PatchType, Integer> counts = patchCounts(current);
		final String makeup = RunTimings.makeup(counts);
		if (makeup.isEmpty())
		{
			return null;
		}
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

	/** Skip whatever the next step is (arriving, or the current patch). */
	public void skip()
	{
		if (stage != RunView.State.RUNNING)
		{
			return;
		}
		final Patch current = patches.getCurrentPatch();
		log.debug("Skip step: {}", banking ? "the bank visit" : !navigator.isArrived() ? "arriving"
			: current != null ? current.name() : "the seed vault");
		if (banking)
		{
			// Carry on without everything from the bank
			banked = true;
			banking = false;
		}
		else if (current == null && navigator.isArrived() && !vaultChecked
			&& plan.getRoute().getStops().get(stopIndex).getLocation() == Location.FARMING_GUILD)
		{
			// Carry on without what's in the seed vault
			vaultChecked = true;
		}
		else if (!navigator.isArrived())
		{
			navigator.markArrived(System.currentTimeMillis());
		}
		else
		{
			patches.skipCurrent();
		}
		evaluate();
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
		evaluate();
	}

	private void evaluate()
	{
		if (stage == RunView.State.OFF)
		{
			view = offView();
			return;
		}
		if (stage == RunView.State.BUILDING)
		{
			final RunPlan current = runService.getPlan();
			if (!isReady(current))
			{
				view = buildingView(current);
				return;
			}
			arm(current);
		}

		final List<RouteStop> stops = plan.getRoute().getStops();
		final long now = System.currentTimeMillis();
		// Armed shows the first step but doesn't time legs or move on until the timer starts
		final boolean timing = stage == RunView.State.RUNNING;
		String instruction = null;
		while (stopIndex < stops.size())
		{
			final RouteStop stop = stops.get(stopIndex);
			final List<Patch> here = patchesAt(plan, stop.getLocation());
			instruction = navigator.travel(stop, here, now);
			if (instruction != null)
			{
				patches.clearCurrent();
				break;
			}
			if (timing && !legRecorded && navigator.nearAny(here))
			{
				recordLeg(now);
			}

			instruction = stop.getLocation() == Location.FARMING_GUILD && !vaultChecked ? fromSeedVault() : null;
			if (instruction != null)
			{
				patches.clearCurrent();
				break;
			}
			instruction = patches.nextStep(here);
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
			// A bank visit after this stop: the rest of the run didn't fit in one inventory
			final BankStop bank = plan.bankAfter(stopIndex);
			if (bank != null && !banked)
			{
				// Ready when the next part of the run, as planned at the start, is carried
				if (missingCount(bank.getSupplies().counted(holdingsTracker.getHoldings()), false) > 0)
				{
					banking = true;
					patches.clearCurrent();
					final String go = bank.getTeleport() != null ? "Use your " + bank.getTeleport().getItemName()
						+ " to teleport to " + bank.getTeleport().getBankName() : bank.title();
					instruction = go + ": deposit the outlined items, then take what the Farm run bank tab shows";
					break;
				}
				log.debug("Bank visit done after {}", stop.getLocation().getDisplayName());
				banked = true;
				banking = false;
				legStartedAt = now;
			}
			log.debug("Stop done: {}", stop.getLocation().getDisplayName());
			stopIndex++;
			navigator.nextStop();
			banked = false;
			legRecorded = false;
			legStartedAt = now;
		}

		if (stopIndex >= stops.size())
		{
			stop(true);
			return;
		}
		shortestPath.setTarget(timing ? walkTarget(stops.get(stopIndex)) : null);
		view = runningView(instruction);
	}

	/**
	 * Where the current step says to walk or run, for Shortest Path to draw: the bank at a bank visit, the way on
	 * while travelling, or the next patch at a stop. Null while teleporting, riding or working a patch.
	 */
	private WorldPoint walkTarget(RouteStop stop)
	{
		if (banking)
		{
			final BankStop bank = plan.bankAfter(stopIndex);
			// By teleport, the bank is right where it lands
			return bank == null || bank.getTeleport() != null ? null : TravelTimes.bankPoint(bank.getLocation());
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
		// The trip to the first stop starts from wherever the player banked, so it isn't a sample for
		// learning our methods' times.
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
			first ? ", first leg, not learned" : "");
	}

	/**
	 * At the Farming Guild: what the run still needs that's only in the seed vault, as a step ("Take 3 maple
	 * saplings from the seed vault"), or null once it's all carried.
	 */
	private String fromSeedVault()
	{
		final List<String> take = new ArrayList<>();
		for (SupplyLine line : bankSupplies().getLines())
		{
			final int vault = line.getWhere().getOrDefault(Holdings.Source.SEED_VAULT, 0);
			final int stillNeeded = line.getNeed() - line.getCarried();
			if (vault > 0 && stillNeeded > 0 && line.getGroup() != SupplyLine.Group.OPTIONAL)
			{
				take.add(Math.min(vault, stillNeeded) + " x " + line.getName());
			}
		}
		if (take.isEmpty())
		{
			vaultChecked = true;
			return null;
		}
		return "Take " + String.join(", ", take) + " from the seed vault (next to the guild's bank)";
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
		for (int i = 0; i < stops.size(); i++)
		{
			final RouteStop stop = stops.get(i);
			final RunView.StopStatus status = i < stopIndex || (i == stopIndex && banking) ? RunView.StopStatus.DONE
				: i == stopIndex ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			stopViews.add(new RunView.Stop(stop.getLocation().getDisplayName(), stop.describeTravel(), status,
				status == RunView.StopStatus.CURRENT ? instruction : null,
				plan.getObjectives().getOrDefault(stop.getLocation(), Collections.emptyList())));
			final BankStop bank = plan.bankAfter(i);
			if (bank != null)
			{
				final RunView.StopStatus bankStatus = banked || i < stopIndex ? RunView.StopStatus.DONE
					: banking ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
				stopViews.add(new RunView.Stop(bank.title(),
					"Restock for the rest of the run", bankStatus,
					bankStatus == RunView.StopStatus.CURRENT ? instruction : null, Collections.emptyList()));
			}
		}
		if (instruction == null)
		{
			instruction = "Head to your next stop";
		}
		// Before leaving and at each bank: what to put on for this part of the run
		final BankGuide.Wear wear = stage == RunView.State.ARMED || banking || patches.getCurrentPatch() == null
			? BankGuide.wear(bankSupplies(), holdingsTracker.getHoldings(), settings.getRunConfig().getOutfit())
			: BankGuide.Wear.NONE;
		final Highlights highlights = highlights(wear);
		return new RunView(stage, startedAt, stopViews, instruction, false, null, null,
			reminders(highlights), wear.getText(), highlights);
	}

	/** What the current step points at: the patch and items to use there, or the teleport item while travelling. */
	private Highlights highlights(BankGuide.Wear wear)
	{
		if (banking)
		{
			return new Highlights(null, false, null, BankGuide.toDeposit(bankSupplies(), holdingsTracker.getHoldings(),
				runService::notedId), null, wear.getItemIds());
		}
		final Highlights atPatch = patches.highlights();
		if (atPatch != null)
		{
			return atPatch;
		}
		// Travelling. Main item first: reminders name it
		final RouteStop stop = plan.getRoute().getStops().get(stopIndex);
		final Set<Integer> items = new LinkedHashSet<>();
		if (stop.getDeparture() == Departure.DIRECT && stop.getMethod() != null)
		{
			travelItems(stop.getMethod(), items);
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
		else if (stop.getDeparture().isViaHouse())
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
		// An item to put on isn't also outlined as the teleport to use, so the two can't be confused
		items.removeAll(wear.getItemIds());
		return new Highlights(null, false, navigator.travelNpc(stop), items, patches.castableNow(spell),
			wear.getItemIds());
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
			return partFor(plan, stopIndex, banking || banked).counted(holdingsTracker.getHoldings());
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
		final RunPlan current = runService.getPlan();
		final String notice = lastRun != null && System.currentTimeMillis() - finishedAt < FINISHED_NOTICE_MILLIS
			? lastRun : null;
		return new RunView(RunView.State.OFF, 0, new ArrayList<>(), notice, false, lastRun, bestTimes(current),
			null, null, Highlights.NONE);
	}

	/** Gathering supplies: how many items are still to get, and what to put on once it's in the inventory. */
	private RunView buildingView(RunPlan current)
	{
		String notice = null;
		if (!current.getRoute().getStops().isEmpty())
		{
			final int missing = missingCount(current);
			notice = "Farm run: get " + missing + (missing == 1 ? " item" : " items") + " from the bank";
		}
		final List<RouteStop> stops = current.getRoute().getStops();
		final boolean atFirstStop = !stops.isEmpty() && navigator.inRange(patchesAt(current, stops.get(0).getLocation()));
		final BankGuide.Wear wear = BankGuide.wear(current.getSupplies(), holdingsTracker.getHoldings(),
			settings.getRunConfig().getOutfit());
		return new RunView(RunView.State.BUILDING, 0, new ArrayList<>(), notice, atFirstStop, null,
			bestTimes(current), null, wear.getText(), new Highlights(null, false, null, Collections.emptySet(), null,
			wear.getItemIds()));
	}

	/**
	 * Everything required is carried, or can be taken from the tool leprechaun when the run starts (every
	 * location has one).
	 */
	static boolean isReady(RunPlan plan)
	{
		return !plan.getRoute().getStops().isEmpty() && missingCount(plan) == 0;
	}

	/** Required lines that are neither carried nor stored at the tool leprechaun. */
	static int missingCount(RunPlan plan)
	{
		// The seed vault is in the Farming Guild: a run starting there can take what's in it on arrival
		final List<RouteStop> stops = plan.getRoute().getStops();
		final boolean vaultFirst = !stops.isEmpty() && stops.get(0).getLocation() == Location.FARMING_GUILD;
		return missingCount(plan.getSupplies(), vaultFirst);
	}

	/** @param vaultHere the seed vault can be used here (the Farming Guild) */
	static int missingCount(SupplyPlan supplies, boolean vaultHere)
	{
		int missing = 0;
		for (SupplyLine line : supplies.getLines())
		{
			if (line.getGroup() == SupplyLine.Group.OPTIONAL || line.isCoveredOtherwise())
			{
				continue;
			}
			final int leprechaun = line.getWhere().getOrDefault(Holdings.Source.LEPRECHAUN, 0);
			final int vault = vaultHere ? line.getWhere().getOrDefault(Holdings.Source.SEED_VAULT, 0) : 0;
			if (line.getCarried() + leprechaun + vault < line.getNeed())
			{
				missing++;
			}
		}
		return missing;
	}
}
