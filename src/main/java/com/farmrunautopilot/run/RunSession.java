package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.bank.FarmBankTab;
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
import java.util.HashSet;
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
	/** How long the step in hand must lack its item before the task becomes a bank visit. */
	private static final long MISSING_GRACE_MILLIS = 1800;
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
	private final FarmBankTab bankTab;

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
	 * An unplanned bank visit, because an item the plan counted on has gone missing (Sean): after the current stop,
	 * or before finishing it when {@link #extraNow}. Null when there's none.
	 */
	private BankStop extraBank;
	/** The unplanned bank visit comes before the current stop is finished: its step needs the missing item. */
	private boolean extraNow;
	/** An unplanned bank visit was ticked away by hand: no more until the next planned one. */
	private boolean declinedExtra;
	/** What the player held when items were last checked for the rest of this part. */
	private Holdings checkedHoldings;
	/** Since when the current step's item has been missing (epoch millis), 0 if it isn't. */
	private long missingSince;
	/**
	 * The bank visit under way, step by step (Sean: at Castle Wars it still said to use the ring): got to the bank,
	 * opened it, opened the Farm run tab. Each stays done once seen (or ticked by hand) until the visit ends.
	 */
	private final boolean[] bankProgress = new boolean[3];
	/** Bank visit steps ticked by hand, so they can be unticked. */
	private final boolean[] bankTicked = new boolean[3];
	/** Within this of the bank chest or booth counts as having got there. */
	private static final int AT_BANK_TILES = 20;
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
		PatchGuide patches, FarmBankTab bankTab)
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
		this.bankTab = bankTab;
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
		banksDone.clear();
		manualBanks.clear();
		vaultChecked = false;
		legRecorded = false;
		manualSteps.clear();
		clearExtraBank();
		declinedExtra = false;
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

	/**
	 * A step of the current stop ticked or unticked by hand, as in Quest Helper (Sean): ticking says the guide
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
		else
		{
			final List<Patch> here = patchesAt(plan, stop.getLocation());
			final int index = step - travelSteps(stop);
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
	 * without travelling (Sean: Taverley and Kastori showed the way there ticked already).
	 */
	private int travelSteps(RouteStop stop)
	{
		if (stop.getDeparture() == Departure.WALK || (navigator.isArrived() && !navigator.isTeleported()))
		{
			return 0;
		}
		return Navigator.journeySteps(stop) + 1;
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
	 * the guide goes back to it. Any number of stops can be unticked, all the way back to the start (Sean); what
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
		checkedHoldings = null;
		missingSince = 0;
	}

	/**
	 * An item the plan counted on has gone missing (Sean): the current task becomes a bank visit. When the current
	 * step needs it, straight away; when a later stop does, after this stop. Not when a planned bank visit comes
	 * first, or the tool leprechaun has it. Checked when what the player holds changes. Client thread.
	 */
	private void checkMissing(long now)
	{
		if (banking || extraBank != null || declinedExtra)
		{
			return;
		}
		final List<RouteStop> stops = plan.getRoute().getStops();
		// This part of the run: up to the next planned bank visit, or the end
		int end = stops.size() - 1;
		for (BankStop bank : plan.getBankStops())
		{
			if (bank.getAfterStop() >= stopIndex)
			{
				end = bank.getAfterStop();
				break;
			}
		}

		// Needed now: the step in hand can't be done; a moment's grace, as the inventory changes mid-step
		if (patches.currentItemMissing(view.getHighlights()))
		{
			if (missingSince == 0)
			{
				missingSince = now;
			}
			else if (now - missingSince >= MISSING_GRACE_MILLIS)
			{
				final List<RouteStop> part = stops.subList(stopIndex, end + 1);
				final SupplyPlan needed = runService.suppliesFor(plan, part);
				if (needed.missingCount(passesGuild(part)) > 0)
				{
					startExtraBank(needed, true);
					return;
				}
			}
		}
		else
		{
			missingSince = 0;
		}

		// Needed later in this part: bank after this stop
		final Holdings holdings = holdingsTracker.getHoldings();
		if (holdings == checkedHoldings || stopIndex >= end)
		{
			return;
		}
		checkedHoldings = holdings;
		final List<RouteStop> part = stops.subList(stopIndex + 1, end + 1);
		final SupplyPlan needed = runService.suppliesFor(plan, part);
		if (needed.missingCount(passesGuild(part)) > 0)
		{
			startExtraBank(needed, false);
		}
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
		final BankTeleport teleport = TravelTimes.bankTiles(here) != null ? null : runService.carriedBankTeleport();
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
		banking = false;
		clearExtraBank();
		manualSteps.clear();
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
			instruction = patches.nextStep(stop, here);
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
						log.debug("Bank visit after {}: deposit {}", stop.getLocation().getDisplayName(),
							depositNames());
					}
					visitBank(bank);
					patches.clearCurrent();
					instruction = bankStep(bank);
					break;
				}
				log.debug("Bank visit done after {}", stop.getLocation().getDisplayName());
				banking = false;
				if (extraBank == null)
				{
					banksDone.add(stopIndex);
					// A planned visit starts a new part: missing items count again
					declinedExtra = false;
				}
				legStartedAt = now;
			}
			log.debug("Stop done: {}", stop.getLocation().getDisplayName());
			stopIndex++;
			navigator.nextStop();
			manualSteps.clear();
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
		bankTab.setAttention(banking && !bankTab.isTabOpen());
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
			final BankStop bank = activeBank();
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
		final Holdings carried = holdingsTracker.getHoldings().carriedOnly();
		final List<Patch> here = patchesAt(plan, Location.FARMING_GUILD);
		for (SupplyLine line : bankSupplies().getLines())
		{
			final int vault = line.getWhere().getOrDefault(Holdings.Source.SEED_VAULT, 0);
			// Only what the guild's own patches still need, less what's carried now: the line's need is the whole
			// part's (Sean: "Take 2 x Avantoe seed" with one patch left and the seed in his inventory)
			int neededHere = 0;
			for (Patch patch : here)
			{
				final Crop crop = plan.getSupplies().getPlantings().get(patch);
				if (crop != null && !patches.isDone(patch) && contains(line.getItemIds(), crop.getPlantItemId()))
				{
					neededHere++;
				}
			}
			final int stillNeeded = neededHere - carried.countAny(line.getItemIds());
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
		// Before leaving and at each bank: what to put on for this part of the run
		final BankGuide.Wear wear = stage == RunView.State.ARMED || banking || patches.getCurrentPatch() == null
			? BankGuide.wear(bankSupplies(), holdingsTracker.getHoldings(), settings.getRunConfig().getOutfit())
			: BankGuide.Wear.NONE;
		final Highlights highlights = highlights(wear);
		return new RunView(stage, startedAt, stopViews, instruction, false, null, null,
			reminders(highlights), wear.getText(), highlights);
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
		return new RunView.Stop(bank.title(), bank == extraBank ? "Get what's missing" : "Restock for the rest of the run",
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
		for (int id : BankGuide.toDeposit(part, holdingsTracker.getHoldings(), id -> id))
		{
			names.add(runService.itemName(id));
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
	 * deposit what's outlined and take what the tab shows.
	 */
	private String bankStep(BankStop bank)
	{
		if (!bankProgress[0])
		{
			return bank.getTeleport() != null ? "Use your " + bank.getTeleport().getItemName() + " to teleport to "
				+ bank.getTeleport().getBankName() : bank.title() + ": walk to the bank";
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

	/** Close to this visit's bank chest or booth: where its teleport lands, or the bank by the stop. */
	private boolean atBank(BankStop bank)
	{
		final WorldPoint point = bank.getTeleport() != null ? bank.getTeleport().getBankPoint()
			: TravelTimes.bankPoint(bank.getLocation());
		final Player player = client.getLocalPlayer();
		return point != null && player != null && player.getWorldLocation().getPlane() == point.getPlane()
			&& player.getWorldLocation().distanceTo2D(point) <= AT_BANK_TILES;
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
		final String[] texts = {bank.getTeleport() != null ? capitalised(bank.how()) : "Walk to the bank", "Open the bank",
			"Open the Farm run tab"};
		final List<RunView.Step> steps = new ArrayList<>();
		boolean current = false;
		for (int i = 0; i < texts.length; i++)
		{
			final RunView.StopStatus status = bankProgress[i] ? RunView.StopStatus.DONE
				: !current ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			current |= !bankProgress[i];
			steps.add(new RunView.Step(texts[i], status, false, bankProgress[i] && bankTicked[i]));
		}
		steps.add(new RunView.Step("Deposit and take the marked items", current ? RunView.StopStatus.PENDING
			: RunView.StopStatus.CURRENT, false, false));
		return steps;
	}

	/** Gathering supplies, one step at a time: open a bank, open the Farm run tab, take what it shows. */
	private String gatheringStep(int missing)
	{
		final String items = missing + (missing == 1 ? " item" : " items");
		if (!bankTab.isBankOpen())
		{
			return "Farm run: open a bank to get " + items;
		}
		if (!bankTab.isTabOpen())
		{
			return "Farm run: click the flashing Farm run tab at the top of the bank";
		}
		return "Farm run: take the " + items + " marked in red or yellow";
	}

	/**
	 * The current stop's steps in order, each done, current or still to do (Sean: show that a stop has several
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
				: capitalised(stop.getMethod().getRides().get(i - 1).getBrief());
			steps.add(new RunView.Step(text, status, i > 0, status == RunView.StopStatus.DONE && manualSteps.contains(i)));
		}
		// Each patch's plan, in the same order the objectives were made
		final List<Patch> here = patchesAt(plan, stop.getLocation());
		final List<String> objectives = plan.getObjectives().getOrDefault(stop.getLocation(), Collections.emptyList());
		for (int i = 0; i < here.size(); i++)
		{
			final Patch patch = here.get(i);
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
		if (banking)
		{
			// Teleporting to the bank: the bank teleport is the item to use, as for any other teleport (Sean)
			final BankStop bank = activeBank();
			if (bank != null && bank.getTeleport() != null && !bankProgress[0])
			{
				final Set<Integer> teleport = new LinkedHashSet<>();
				for (int id : bank.getTeleport().getItemIds())
				{
					teleport.add(id);
				}
				return new Highlights(null, false, null, teleport, null, wear.getItemIds());
			}
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
			null, null, Highlights.NONE);
	}

	/** Gathering supplies: how many items are still to get, and what to put on once it's in the inventory. */
	private RunView buildingView(RunPlan current)
	{
		String notice = null;
		if (!current.getRoute().getStops().isEmpty())
		{
			final int missing = missingCount(current);
			notice = gatheringStep(missing);
			// Flash the Farm run tab until it's open, while there's something to take
			bankTab.setAttention(missing > 0 && !bankTab.isTabOpen());
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
		return supplies.missingCount(vaultHere);
	}
}
