package com.farmrunautopilot.run;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Runs a farm run from Start to Stop (SPEC 13.4): freezes the plan, works out the next step from live patch
 * states, notices when steps are done, records leg times, and finishes itself after the last step.
 *
 * <p>Shortest Path is only asked for directions to the first stop, so a run can start from any bank; our
 * route takes over after that. All methods except {@link #getView()} run on the client thread.
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

	private final Client client;
	private final RunService runService;
	private final PatchTracker patchTracker;
	private final HoldingsTracker holdingsTracker;
	private final AccessChecker accessChecker;
	private final SettingsStore settings;
	private final ShortestPathBridge shortestPath;
	private final RunTimings timings;

	private boolean running;
	private RunPlan plan;
	private long startedAt;
	private int stopIndex;
	private boolean arrived;
	private long legStartedAt;
	private Patch currentPatch;
	private final Map<Patch, Progress> progress = new EnumMap<>(Patch.class);
	private final List<RunTimings.Leg> legs = new ArrayList<>();
	private long finishedAt;
	private String lastRun;
	private volatile RunView view = RunView.IDLE_VIEW;

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
		AccessChecker accessChecker, SettingsStore settings, ShortestPathBridge shortestPath, RunTimings timings)
	{
		this.client = client;
		this.runService = runService;
		this.patchTracker = patchTracker;
		this.holdingsTracker = holdingsTracker;
		this.accessChecker = accessChecker;
		this.settings = settings;
		this.shortestPath = shortestPath;
		this.timings = timings;
	}

	public RunView getView()
	{
		return view;
	}

	public boolean isRunning()
	{
		return running;
	}

	/** Freeze the current plan and start guiding. */
	public void start()
	{
		final RunPlan current = runService.getPlan();
		if (running || current.getRoute().getStops().isEmpty())
		{
			return;
		}
		plan = current;
		running = true;
		startedAt = System.currentTimeMillis();
		legStartedAt = startedAt;
		stopIndex = 0;
		arrived = false;
		currentPatch = null;
		progress.clear();
		legs.clear();
		lastRun = null;
		log.debug("Run started with {} stops", plan.getRoute().getStops().size());
		onGameTick();
	}

	/**
	 * End the run and save its timings.
	 *
	 * @param finished every stop was completed
	 */
	public void stop(boolean finished)
	{
		if (!running)
		{
			return;
		}
		running = false;
		shortestPath.clear();
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
		onGameTick();
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
		if (!running)
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
		onGameTick();
	}

	public void onChatMessage(String message)
	{
		if (running && currentPatch != null
			&& (COMPOST_USED.matcher(message).matches() || ALREADY_COMPOSTED.matcher(message).matches()))
		{
			progress(currentPatch).composted = true;
		}
	}

	/** Re-evaluate the run. Call every game tick. */
	public void onGameTick()
	{
		if (!running)
		{
			view = idleView();
			return;
		}

		final List<RouteStop> stops = plan.getRoute().getStops();
		final long now = System.currentTimeMillis();
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
					instruction = travelInstruction(stop);
					// Shortest Path only for the first stop; our route covers the rest.
					if (stopIndex == 0 && settings.getRunConfig().isUseShortestPath())
					{
						shortestPath.setTarget(PatchPoints.of(here.get(0)));
					}
					currentPatch = null;
					break;
				}
			}
			if (stopIndex == 0)
			{
				shortestPath.clear();
			}

			instruction = nextPatchStep(here);
			if (instruction != null)
			{
				break;
			}
			// Every patch here is done
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

	private String travelInstruction(RouteStop stop)
	{
		final String place = stop.getLocation().getDisplayName();
		if (stopIndex == 0)
		{
			return "Get to " + place + (settings.getRunConfig().isUseShortestPath() && shortestPath.isAvailable()
				? " (Shortest Path shows the way)" : "");
		}
		return stop.describeTravel() + " to " + place;
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
			if (live != null && live.getState() != PatchState.GROWING
				&& patchTracker.getPatchesInRange().contains(patch))
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
		final RouteStop stop = plan.getRoute().getStops().get(stopIndex);
		// The trip to the first stop is the player's own (any bank, Shortest Path), so it isn't a sample for
		// learning our methods' times.
		final boolean first = stopIndex == 0;
		legs.add(new RunTimings.Leg(stop.getLocation().name(),
			first || stop.getMethod() == null ? null : stop.getMethod().name(),
			first ? "START" : stop.getDeparture().name(), (now - legStartedAt) / 1000.0, now));
	}

	private List<Patch> patchesAt(Location location)
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

	private boolean farFrom(Patch patch)
	{
		final Player player = client.getLocalPlayer();
		final WorldPoint point = PatchPoints.of(patch);
		return player != null && point != null && player.getWorldLocation().distanceTo(point) > NEAR_PATCH_TILES;
	}

	private Progress progress(Patch patch)
	{
		return progress.computeIfAbsent(patch, k -> new Progress());
	}

	/** The patch the current step is about, for highlights (M7b). */
	public WorldPoint currentTarget()
	{
		if (!running || plan == null)
		{
			return null;
		}
		if (currentPatch != null)
		{
			return PatchPoints.of(currentPatch);
		}
		final List<RouteStop> stops = plan.getRoute().getStops();
		if (stopIndex < stops.size())
		{
			final List<Patch> here = patchesAt(stops.get(stopIndex).getLocation());
			return here.isEmpty() ? null : PatchPoints.of(here.get(0));
		}
		return null;
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
			final String travel = i == 0 ? "From anywhere" : stop.describeTravel();
			stopViews.add(new RunView.Stop(stop.getLocation().getDisplayName(), travel, status,
				status == RunView.StopStatus.CURRENT ? instruction : null,
				plan.getObjectives().getOrDefault(stop.getLocation(), Collections.emptyList())));
		}
		return new RunView(RunView.State.RUNNING, startedAt, stopViews, instruction, false, null, null,
			dropReminder());
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
		final List<String> parts = new ArrayList<>();
		if (weeds > 0)
		{
			parts.add(weeds + (weeds == 1 ? " weed" : " weeds"));
		}
		if (pots > 0)
		{
			parts.add(pots + (pots == 1 ? " empty plant pot" : " empty plant pots"));
		}
		return parts.isEmpty() ? null : "Drop " + String.join(" and ", parts);
	}

	private RunView idleView()
	{
		final RunPlan current = runService.getPlan();
		final boolean ready = isReady(current);
		String notice = null;
		if (lastRun != null && System.currentTimeMillis() - finishedAt < FINISHED_NOTICE_MILLIS)
		{
			notice = lastRun;
		}
		else if (ready)
		{
			notice = "Ready - press Start run";
		}
		return new RunView(RunView.State.IDLE, 0, new ArrayList<>(), notice, ready, lastRun, bestTimes(current),
			null);
	}

	/**
	 * Everything required is carried, or can be taken from the tool leprechaun when the run starts (every
	 * location has one).
	 */
	static boolean isReady(RunPlan plan)
	{
		if (plan.getRoute().getStops().isEmpty())
		{
			return false;
		}
		for (SupplyLine line : plan.getSupplies().getLines())
		{
			if (line.getGroup() == SupplyLine.Group.OPTIONAL || line.isCoveredOtherwise())
			{
				continue;
			}
			final int leprechaun = line.getWhere().getOrDefault(Holdings.Source.LEPRECHAUN, 0);
			if (line.getCarried() + leprechaun < line.getNeed())
			{
				return false;
			}
		}
		return true;
	}
}
