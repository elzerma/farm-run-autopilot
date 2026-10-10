package com.farmrunautopilot.run;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/**
 * Runs a farm run (SPEC 13.4): off until Build run, building while supplies are gathered, armed (plan frozen,
 * first step shown) once they are, and running from the first teleport or patch click. While running it works
 * out the next step from live patch states, notices when steps are done, records leg times, and finishes
 * itself after the last step.
 *
 * <p>All methods except {@link #getView()} run on the client thread.
 */
@Slf4j
@Singleton
public class RunSession
{
	/** Compost messages, as in RuneLite core CompostTracker (BSD-2). */
	private static final Pattern COMPOST_USED = Pattern.compile(
		"You treat the .+ with (ultra|super|)compost\\.|^The .+ has been treated with (ultra|super|)compost.*");
	private static final Pattern ALREADY_COMPOSTED = Pattern.compile(
		"This .+ has already been (treated|fertilised) with (ultra|super|)compost.*");
	/** How long "Run finished" stays under the player. */
	private static final long FINISHED_NOTICE_MILLIS = 15_000;
	/** Further than this from a patch and the step starts with "Run to the ... patch". */
	private static final int NEAR_PATCH_TILES = 7;
	private static final int BEST_TIMES = 3;
	private static final int INVENTORY_SLOTS = 28;
	/** Moving further than this in one tick can only be a teleport. */
	private static final int TELEPORT_TILES = 30;

	private final Client client;
	private final RunService runService;
	private final PatchTracker patchTracker;
	private final HoldingsTracker holdingsTracker;
	private final AccessChecker accessChecker;
	private final SettingsStore settings;
	private final RunTimings timings;
	private final SceneTracker scene;

	private RunView.State stage = RunView.State.OFF;
	/** Frozen when the run arms; null while off or building. */
	private RunPlan plan;
	/** When the timer started (epoch millis), 0 until the player leaves. */
	private long startedAt;
	/** Where the player was last tick, to notice a teleport while armed. */
	private WorldPoint lastLocation;
	private int stopIndex;
	private boolean arrived;
	/** When the player reached the current stop (epoch seconds); patches must be seen after this to count. */
	private long arrivedAtSeconds;
	/** The time for the leg to the current stop has been saved. */
	private boolean legRecorded;
	private long legStartedAt;
	private Patch currentPatch;
	/** What to do at {@link #currentPatch}, or null while travelling. */
	private StepAdvisor.Action currentAction;
	private final Map<Patch, Progress> progress = new EnumMap<>(Patch.class);
	private final List<RunTimings.Leg> legs = new ArrayList<>();
	private long finishedAt;
	private String lastRun;
	private volatile RunView view = RunView.OFF_VIEW;

	/** What has happened at a patch during this run. */
	private static final class Progress
	{
		boolean composted;
		boolean paid;
		boolean skipped;
		/** Seen in range in a state other than growing, so a crop growing later was planted this run. */
		boolean sawUnplanted;
		/** Payment items in the inventory when paying became the next step; -1 until then. */
		int paymentBaseline = -1;
	}

	@Inject
	RunSession(Client client, RunService runService, PatchTracker patchTracker, HoldingsTracker holdingsTracker,
		AccessChecker accessChecker, SettingsStore settings, RunTimings timings,
		SceneTracker scene)
	{
		this.scene = scene;
		this.client = client;
		this.runService = runService;
		this.patchTracker = patchTracker;
		this.holdingsTracker = holdingsTracker;
		this.accessChecker = accessChecker;
		this.settings = settings;
		this.timings = timings;
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
		plan = null;
		currentPatch = null;
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
		arrived = false;
		currentPatch = null;
		progress.clear();
		legs.clear();
		lastRun = null;
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
		final List<Patch> patches = plan.getSelection().getPatches();
		switch (event.getMenuAction())
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GAME_OBJECT:
				final Patch patch = scene.patchAt(event.getId(), event.getParam0(), event.getParam1());
				if (patch != null && patches.contains(patch))
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
				for (Patch p : patches)
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
		final long now = System.currentTimeMillis();
		final double seconds = (now - startedAt) / 1000.0;
		final Map<PatchType, Integer> counts = plan.getSupplies().getPatchCounts();
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
		final Map<PatchType, Integer> counts = current.getSupplies().getPatchCounts();
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
		if (!arrived)
		{
			markArrived(System.currentTimeMillis());
		}
		else if (currentPatch != null)
		{
			progress(currentPatch).skipped = true;
		}
		evaluate();
	}

	public void onChatMessage(String message)
	{
		if ((stage == RunView.State.ARMED || stage == RunView.State.RUNNING) && currentPatch != null
			&& (COMPOST_USED.matcher(message).matches() || ALREADY_COMPOSTED.matcher(message).matches()))
		{
			progress(currentPatch).composted = true;
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
			final List<Patch> here = patchesAt(stop.getLocation());
			if (!arrived)
			{
				if (inRange(here))
				{
					markArrived(now);
				}
				else
				{
					instruction = stop.describeTravel() + " to " + stop.getLocation().getDisplayName();
					currentPatch = null;
					break;
				}
			}
			if (timing && !legRecorded && nearAny(here))
			{
				recordLeg(now);
			}

			instruction = nextPatchStep(here);
			if (instruction != null || !timing)
			{
				break;
			}
			// Every patch here is done
			if (!legRecorded)
			{
				recordLeg(now);
			}
			stopIndex++;
			arrived = false;
			legStartedAt = now;
		}

		if (stopIndex >= stops.size())
		{
			stop(true);
			return;
		}
		view = runningView(instruction);
	}


	/** The first unfinished step at this stop, or null when every patch here is done. */
	private String nextPatchStep(List<Patch> here)
	{
		for (Patch patch : here)
		{
			final Progress p = progress(patch);
			if (p.skipped)
			{
				continue;
			}
			final PatchPrediction live = patchTracker.predict(patch);
			// Only trust a reading taken since arriving: an estimate from the last visit can say a tree is
			// still growing when it's ready, which would skip the patch
			if (live == null || live.getObservedAt() < arrivedAtSeconds)
			{
				currentPatch = patch;
				currentAction = StepAdvisor.Action.INSPECT;
				return "Walk to the " + patch.getDisplayName() + " patch";
			}
			if (live.getState() != PatchState.GROWING && patchTracker.getPatchesInRange().contains(patch))
			{
				p.sawUnplanted = true;
			}
			final StepAdvisor.PatchGoal goal = goal(patch, p);
			final StepAdvisor.Advice advice = StepAdvisor.advise(goal, live);
			if (advice.getAction() == StepAdvisor.Action.PAY)
			{
				watchPayment(patch, p);
				if (p.paid)
				{
					continue;
				}
			}
			if (advice.getAction() != StepAdvisor.Action.DONE)
			{
				currentPatch = patch;
				currentAction = advice.getAction();
				// The area loads well before the patch is reached, and some places have several patches
				if (advice.getAction() != StepAdvisor.Action.INSPECT && farFrom(patch))
				{
					final String text = advice.getText();
					final TravelMethod method = plan.getRoute().getStops().get(stopIndex).getMethod();
					final String directions = method != null ? method.getDirections() : null;
					return (directions != null ? directions + " to the " : "Run to the ") + patch.getDisplayName()
						+ " patch, then "
						+ Character.toLowerCase(text.charAt(0)) + text.substring(1);
				}
				return advice.getText();
			}
		}
		currentPatch = null;
		return null;
	}

	/** Paid once the payment items leave the inventory after paying became the next step. */
	private void watchPayment(Patch patch, Progress p)
	{
		final Crop crop = plan.getSupplies().getPlantings().get(patch);
		if (crop == null || !crop.hasPayment())
		{
			return;
		}
		final int held = holdingsTracker.getHoldings().in(Holdings.Source.INVENTORY)
			.getOrDefault(crop.getPaymentItemId(), 0);
		if (p.paymentBaseline < 0)
		{
			p.paymentBaseline = held;
		}
		else if (held < p.paymentBaseline)
		{
			p.paid = true;
		}
	}

	private StepAdvisor.PatchGoal goal(Patch patch, Progress p)
	{
		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final Crop crop = plan.getSupplies().getPlantings().get(patch);
		final boolean faladorElite = patch == Patch.FALADOR_TREE && access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE));
		final boolean protectionNeeded = patch.getType().isProtectable() && crop != null && crop.hasPayment()
			&& config.protectionFor(patch) == Protection.PAY_GARDENER && !faladorElite;
		final String plantName = crop != null ? runService.itemName(crop.getPlantItemId()) : "your seed";
		final String payment = protectionNeeded
			? crop.getPaymentQuantity() + " x " + runService.itemName(crop.getPaymentItemId())
			+ (config.isPayWithNotes() ? " (noted is fine)" : "")
			: null;
		return new StepAdvisor.PatchGoal(patch, crop, plantName, config.getCompost().get(patch.getType()),
			protectionNeeded, payment, config.getPayToClear().contains(patch.getType()), p.composted, p.paid, p.sawUnplanted);
	}

	private void markArrived(long now)
	{
		arrived = true;
		arrivedAtSeconds = now / 1000;
		legRecorded = false;
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
		legs.add(new RunTimings.Leg(stop.getLocation().name(),
			first || stop.getMethod() == null ? null : stop.getMethod().name(),
			first ? "START" : stop.getDeparture().name(), (now - legStartedAt) / 1000.0, now));
	}

	private List<Patch> patchesAt(Location location)
	{
		return patchesAt(plan, location);
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

	private boolean inRange(List<Patch> here)
	{
		final Set<Patch> inRange = patchTracker.getPatchesInRange();
		for (Patch patch : here)
		{
			if (inRange.contains(patch))
			{
				return true;
			}
		}
		return false;
	}

	private boolean nearAny(List<Patch> here)
	{
		for (Patch patch : here)
		{
			if (!farFrom(patch))
			{
				return true;
			}
		}
		return false;
	}

	private boolean farFrom(Patch patch)
	{
		final Player player = client.getLocalPlayer();
		final WorldPoint point = scene.locationOf(patch);
		return player != null && point != null && player.getWorldLocation().distanceTo(point) > NEAR_PATCH_TILES;
	}

	private Progress progress(Patch patch)
	{
		return progress.computeIfAbsent(patch, k -> new Progress());
	}

	private RunView runningView(String instruction)
	{
		final List<RunView.Stop> stopViews = new ArrayList<>();
		final List<RouteStop> stops = plan.getRoute().getStops();
		for (int i = 0; i < stops.size(); i++)
		{
			final RouteStop stop = stops.get(i);
			final RunView.StopStatus status = i < stopIndex ? RunView.StopStatus.DONE
				: i == stopIndex ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
			final String travel = stop.describeTravel();
			stopViews.add(new RunView.Stop(stop.getLocation().getDisplayName(), travel, status,
				status == RunView.StopStatus.CURRENT ? instruction : null,
				plan.getObjectives().getOrDefault(stop.getLocation(), Collections.emptyList())));
		}
		if (instruction == null)
		{
			instruction = "Head to your next stop";
		}
		final Highlights highlights = highlights();
		return new RunView(stage, startedAt, stopViews, instruction, false, null, null,
			reminders(highlights), highlights);
	}

	/** What the current step points at: the patch and items to use there, or the teleport item while travelling. */
	private Highlights highlights()
	{
		// Main item first: reminders name it
		final Set<Integer> items = new LinkedHashSet<>();
		if (currentPatch == null || currentAction == null)
		{
			final RouteStop stop = plan.getRoute().getStops().get(stopIndex);
			if (stopIndex > 0 && stop.getDeparture() == Departure.DIRECT && stop.getMethod() != null)
			{
				travelItems(stop.getMethod(), items);
			}
			// The item that gets the player to the fairy ring
			final FairyRingAccess way = stop.getDeparture().getFairyRingAccess();
			if (stopIndex > 0 && way != null)
			{
				for (TravelItem item : way.getItems())
				{
					for (int id : item.getItemIds())
					{
						items.add(id);
					}
				}
			}
			return new Highlights(null, false, items);
		}

		boolean gardener = false;
		final Crop crop = plan.getSupplies().getPlantings().get(currentPatch);
		switch (currentAction)
		{
			case PLANT:
				if (crop != null)
				{
					items.add(crop.getPlantItemId());
				}
				break;
			case COMPOST:
				final Compost compost = settings.getRunConfig().getCompost().get(currentPatch.getType());
				if (compost != null && compost != Compost.NONE)
				{
					items.add(compost.getItemId());
				}
				items.add(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
				break;
			case RAKE:
				items.add(ItemID.RAKE);
				break;
			case CLEAR_DEAD:
			case DIG_STUMP:
				items.add(ItemID.SPADE);
				break;
			case CHOP:
				for (int axe : SupplyItems.AXES)
				{
					items.add(axe);
				}
				break;
			case CURE:
				items.add(ItemID.PLANT_CURE);
				break;
			case PAY:
				gardener = true;
				if (crop != null && crop.hasPayment())
				{
					items.add(crop.getPaymentItemId());
					items.add(runService.notedId(crop.getPaymentItemId()));
				}
				break;
			case PAY_TO_CLEAR:
				gardener = true;
				items.add(ItemID.COINS);
				break;
			default:
				break;
		}
		return new Highlights(currentPatch, gardener && currentPatch.hasGardener(), items);
	}

	private void travelItems(TravelMethod method, Set<Integer> items)
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
		final String missing = missingItemReminder(highlights);
		if (missing != null)
		{
			lines.add(missing);
		}
		final String drop = dropReminder();
		if (drop != null)
		{
			lines.add(drop);
		}
		final String space = spaceReminder();
		if (space != null)
		{
			lines.add(space);
		}
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	/**
	 * The item the current patch step needs isn't carried: take it from the tool leprechaun if it's stored
	 * there, otherwise say it's missing.
	 */
	private String missingItemReminder(Highlights highlights)
	{
		if (currentPatch == null || currentAction == null)
		{
			return null;
		}
		final Set<Integer> needed = highlights.getItemIds();
		if (needed.isEmpty())
		{
			return null;
		}
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> worn = holdings.in(Holdings.Source.WORN);
		final Map<Integer, Integer> leprechaun = holdings.in(Holdings.Source.LEPRECHAUN);
		Integer stored = null;
		for (int id : needed)
		{
			if (inventory.getOrDefault(id, 0) > 0 || worn.getOrDefault(id, 0) > 0)
			{
				return null;
			}
			if (stored == null && leprechaun.getOrDefault(id, 0) > 0)
			{
				stored = id;
			}
		}
		if (stored != null)
		{
			final String name = runService.itemName(stored).toLowerCase();
			final String article = currentAction == StepAdvisor.Action.COMPOST ? ""
				: "aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ";
			return "Take " + article + name + " from the tool leprechaun";
		}
		if (currentAction == StepAdvisor.Action.CHOP)
		{
			return "You aren't carrying an axe";
		}
		// The step's main item is listed first
		return "You aren't carrying: " + runService.itemName(needed.iterator().next());
	}

	/**
	 * Not enough free inventory space for what's about to be picked here (SPEC 11.5). Herbs and fruit can be
	 * noted by the tool leprechaun at every patch.
	 */
	private String spaceReminder()
	{
		if (currentPatch == null || currentAction == null)
		{
			return null;
		}
		final PatchPrediction live = patchTracker.predict(currentPatch);
		final int expected = expectedHarvest(currentPatch, currentAction, live);
		if (expected == 0)
		{
			return null;
		}
		final int free = freeInventorySlots();
		if (free >= expected)
		{
			return null;
		}
		return "Only " + free + " free slot" + (free == 1 ? "" : "s") + ": note produce on the tool leprechaun";
	}

	/** Inventory slots the next harvest here will take (herbs and fruit), 0 if none. */
	static int expectedHarvest(Patch patch, StepAdvisor.Action action, PatchPrediction live)
	{
		if (patch.getType() == PatchType.HERB)
		{
			return action == StepAdvisor.Action.PICK ? SupplyCalculator.HERBS_PER_PATCH : 0;
		}
		if (patch.getType() == PatchType.FRUIT_TREE)
		{
			if (action == StepAdvisor.Action.PICK && live != null)
			{
				return live.getStage();
			}
			return action == StepAdvisor.Action.CHECK_HEALTH ? SupplyCalculator.FRUIT_PER_TREE : 0;
		}
		return 0;
	}

	private int freeInventorySlots()
	{
		final ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return INVENTORY_SLOTS;
		}
		int used = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				used++;
			}
		}
		return INVENTORY_SLOTS - used;
	}

	/** Nothing in game, apart from how the last run went for a few seconds. */
	private RunView offView()
	{
		final RunPlan current = runService.getPlan();
		final String notice = lastRun != null && System.currentTimeMillis() - finishedAt < FINISHED_NOTICE_MILLIS
			? lastRun : null;
		return new RunView(RunView.State.OFF, 0, new ArrayList<>(), notice, false, lastRun, bestTimes(current),
			null, Highlights.NONE);
	}

	/** Gathering supplies: how many items are still to get. */
	private RunView buildingView(RunPlan current)
	{
		String notice = null;
		if (!current.getRoute().getStops().isEmpty())
		{
			final int missing = missingCount(current);
			notice = "Farm run: get " + missing + (missing == 1 ? " item" : " items") + " from the bank";
		}
		final List<RouteStop> stops = current.getRoute().getStops();
		final boolean atFirstStop = !stops.isEmpty()
			&& inRange(patchesAt(current, stops.get(0).getLocation()));
		return new RunView(RunView.State.BUILDING, 0, new ArrayList<>(), notice, atFirstStop, null,
			bestTimes(current), null, Highlights.NONE);
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
		int missing = 0;
		for (SupplyLine line : plan.getSupplies().getLines())
		{
			if (line.getGroup() == SupplyLine.Group.OPTIONAL || line.isCoveredOtherwise())
			{
				continue;
			}
			final int leprechaun = line.getWhere().getOrDefault(Holdings.Source.LEPRECHAUN, 0);
			if (line.getCarried() + leprechaun < line.getNeed())
			{
				missing++;
			}
		}
		return missing;
	}
}
