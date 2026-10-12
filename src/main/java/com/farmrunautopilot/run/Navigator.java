package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.tracking.PatchTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Getting to the current stop (docs/REBUILD.md, Sprint 4): spots the teleport landing, steps through the
 * journey after it (a boat, a quetzal, Elkoy), notices arriving at the stop's patches, and says where the
 * walk goes for Shortest Path. One stop at a time; {@link #nextStop()} starts over. Client thread.
 */
@Singleton
class Navigator
{
	/** Further than this from a patch and the step starts with "Run to the ... patch". */
	static final int NEAR_PATCH_TILES = 7;
	/** Running covers 2 tiles a tick, so a bigger move in one tick is a teleport. */
	private static final int JUMP_TILES = 10;
	/** A teleport landing this close to the stop's patch counts as having arrived in the area. */
	private static final int LANDED_NEAR_TILES = 150;
	/** A journey step's walk is done this close to where it goes. */
	private static final int JOURNEY_NEAR_TILES = 4;
	/** A teleport landing this close to a journey's first step counts as having arrived in the area. */
	private static final int FIRST_STEP_LANDING_TILES = 60;
	/** The quetzal that flies between Varlamore landing pads, matched by name. */
	static final String RENU = "Renu";

	private final Client client;
	private final PatchTracker patchTracker;
	private final SceneTracker scene;

	private boolean arrived;
	/** When the player reached the current stop (epoch seconds); patches must be seen after this to count. */
	private long arrivedAtSeconds;
	/** Teleported close to the current stop but its patch hasn't loaded yet. */
	private boolean teleported;
	/** How many of the travel method's journey steps (boat, quetzal, Elkoy...) are done since landing. */
	private int journeyStep;
	/** The player's position last tick, to spot teleports. */
	private WorldPoint lastPosition;

	@Inject
	Navigator(Client client, PatchTracker patchTracker, SceneTracker scene)
	{
		this.client = client;
		this.patchTracker = patchTracker;
		this.scene = scene;
	}

	/** A new run: nothing travelled yet. */
	void reset()
	{
		nextStop();
		lastPosition = null;
	}

	/** On to the next stop. */
	void nextStop()
	{
		arrived = false;
		teleported = false;
		journeyStep = 0;
	}

	boolean isArrived()
	{
		return arrived;
	}

	/** Landed from the teleport (or set off on foot); the journey steps come next. */
	boolean isTeleported()
	{
		return teleported;
	}

	/** How many of the travel method's journey steps are done since landing. */
	int getJourneyStep()
	{
		return journeyStep;
	}

	long getArrivedAtSeconds()
	{
		return arrivedAtSeconds;
	}

	void markArrived(long nowMillis)
	{
		arrived = true;
		arrivedAtSeconds = nowMillis / 1000;
	}

	/**
	 * "Mark done" while travelling: on one step at a time, the teleport, then each journey step (Sean: it skipped
	 * them all). After the last one the stop counts as reached, so the patch's own "Walk to the ... patch" is next.
	 */
	void markStepDone(RouteStop stop, long nowMillis)
	{
		if (arrived)
		{
			return;
		}
		final int journey = journeySteps(stop);
		if (!teleported)
		{
			teleported = true;
			if (journey == 0)
			{
				markArrived(nowMillis);
			}
		}
		else if (journeyStep < journey - 1)
		{
			journeyStep++;
		}
		else
		{
			journeyStep = journey;
			markArrived(nowMillis);
		}
	}

	/** How many journey steps follow the way there (a boat, Elkoy...); none for a walk. */
	static int journeySteps(RouteStop stop)
	{
		return stop.getMethod() == null || stop.getDeparture() == Departure.WALK ? 0 : stop.getMethod().getThen().size();
	}

	/**
	 * Follow the travel to this stop for one tick.
	 *
	 * @param here the stop's patches in the run
	 * @return the travel step to show, or null once arrived
	 */
	String travel(RouteStop stop, List<Patch> here, long nowMillis)
	{
		final Player player = client.getLocalPlayer();
		final WorldPoint position = player != null ? player.getWorldLocation() : null;
		final WorldPoint previous = lastPosition;
		lastPosition = position;
		if (arrived)
		{
			return null;
		}
		if (inRange(here))
		{
			markArrived(nowMillis);
			return null;
		}
		// A walk from the last stop (Falador Park to Taverley, or to a stop near the start) has nothing to travel to
		// first: arrived straight away, so the patch's own "Walk to the ... patch" is the one step, with its walking
		// line (Sean: it took two Mark done presses)
		if (stop.getDeparture() == Departure.WALK)
		{
			markArrived(nowMillis);
			return null;
		}
		if (!teleported && (jumpedTowards(previous, position, here.get(0)) || jumpedToFirstStep(stop, previous, position)))
		{
			teleported = true;
		}
		else if (teleported && journeyStepDone(journeyStep(stop), previous, position))
		{
			journeyStep++;
		}
		final JourneyStep step = journeyStep(stop);
		return !teleported ? stop.describeWay() + " to " + stop.getLocation().getDisplayName()
			: step != null ? step.getText()
			: walkInstruction(here.get(0));
	}

	/**
	 * Where the travel says to walk, for Shortest Path: back to Renu's landing pad, to the boat or NPC of a
	 * journey step, or to the stop's nearest patch after landing. Null while teleporting or riding.
	 */
	WorldPoint travelTarget(RouteStop stop, List<Patch> here)
	{
		if (!teleported)
		{
			// A Renu leg starts with the walk back to the landing pad
			final NPC renu = renuFirst(stop) ? scene.transferNpc(RENU) : null;
			return renu != null ? renu.getWorldLocation() : null;
		}
		final JourneyStep step = journeyStep(stop);
		final WorldPoint stepAt = step != null ? journeyTarget(step) : null;
		if (stepAt != null)
		{
			// To the boat, quetzal or NPC: where it stands once loaded, else where it's expected
			return stepAt;
		}
		final List<Patch> ordered = nearestFirst(here, null);
		return ordered.isEmpty() ? null : scene.locationOf(ordered.get(0));
	}

	/** The NPC to outline while travelling (Renu before a Renu flight, a journey step's NPC), or null. */
	String travelNpc(RouteStop stop)
	{
		if (renuFirst(stop))
		{
			return RENU;
		}
		final JourneyStep step = journeyStep(stop);
		return step != null ? step.getNpc() : null;
	}

	/**
	 * A stop's patches, closest to the player first, so nobody runs past one patch to reach another (Catherby's
	 * herb patch is on the way from the Camelot teleport to the fruit tree). The patch already being worked on
	 * stays first until it's done, so the step doesn't flip between patches while walking.
	 */
	List<Patch> nearestFirst(List<Patch> here, Patch current)
	{
		final Player player = client.getLocalPlayer();
		final WorldPoint at = player != null ? player.getWorldLocation() : null;
		final List<Patch> ordered = new ArrayList<>(here);
		if (at != null)
		{
			ordered.sort(Comparator.comparingInt(p ->
			{
				final WorldPoint point = scene.locationOf(p);
				return point == null || point.getPlane() != at.getPlane() ? Integer.MAX_VALUE : point.distanceTo2D(at);
			}));
		}
		if (current != null && ordered.remove(current))
		{
			ordered.add(0, current);
		}
		return ordered;
	}

	boolean inRange(List<Patch> here)
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

	boolean nearAny(List<Patch> here)
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

	boolean farFrom(Patch patch)
	{
		final Player player = client.getLocalPlayer();
		final WorldPoint point = scene.locationOf(patch);
		return player != null && point != null && player.getWorldLocation().distanceTo(point) > NEAR_PATCH_TILES;
	}

	/**
	 * A teleport (or other jump) that landed near the stop: the position changed by more than running could in
	 * one tick, and the patch is now close. Some teleports land too far out for the patch to load yet
	 * (Trollheim), so without this the step would keep saying to teleport.
	 */
	private boolean jumpedTowards(WorldPoint from, WorldPoint to, Patch patch)
	{
		final WorldPoint target = scene.locationOf(patch);
		return jumped(from, to) && target != null && target.getPlane() == to.getPlane()
			&& target.distanceTo2D(to) <= LANDED_NEAR_TILES;
	}

	/** The position changed by more than running could in one tick: a teleport, ride or plane change. */
	private static boolean jumped(WorldPoint from, WorldPoint to)
	{
		return from != null && to != null && (from.getPlane() != to.getPlane() || from.distanceTo2D(to) > JUMP_TILES);
	}

	/** This leg is a Renu flight not taken yet: the way starts at the quetzal on the landing pad. */
	private boolean renuFirst(RouteStop stop)
	{
		return stop.getDeparture() == Departure.RENU && !teleported && !arrived;
	}

	/** The journey step to do now, after the teleport and before the walk to the patch, or null. */
	private JourneyStep journeyStep(RouteStop stop)
	{
		final TravelMethod method = stop.getMethod();
		return teleported && !arrived && method != null && journeyStep < method.getThen().size()
			? method.getThen().get(journeyStep) : null;
	}

	/**
	 * A teleport that landed by the journey's first step rather than near the patch, e.g. the Mining Guild,
	 * which is underground with coordinates far from Falador Park.
	 */
	private boolean jumpedToFirstStep(RouteStop stop, WorldPoint from, WorldPoint to)
	{
		final TravelMethod method = stop.getMethod();
		if (method == null || method.getThen().isEmpty() || !jumped(from, to))
		{
			return false;
		}
		final WorldPoint at = method.getThen().get(0).getAt();
		return at != null && at.getPlane() == to.getPlane() && at.distanceTo2D(to) <= FIRST_STEP_LANDING_TILES;
	}

	/** A ride is done when the player is moved; a walk when they get close to where it goes. */
	private boolean journeyStepDone(JourneyStep step, WorldPoint from, WorldPoint to)
	{
		if (step == null || to == null)
		{
			return false;
		}
		if (step.getKind() == JourneyStep.Kind.RIDE)
		{
			return jumped(from, to);
		}
		final WorldPoint target = journeyTarget(step);
		return target != null && target.getPlane() == to.getPlane() && target.distanceTo2D(to) <= JOURNEY_NEAR_TILES;
	}

	/** Where a journey step happens: its NPC's spot once loaded, otherwise the expected spot. */
	private WorldPoint journeyTarget(JourneyStep step)
	{
		final NPC npc = step.getNpc() != null ? scene.transferNpc(step.getNpc()) : null;
		return npc != null ? npc.getWorldLocation() : step.getAt();
	}

	/** After teleporting: the way on from the landing spot, e.g. "Walk to the Troll Stronghold herb patch". */
	private static String walkInstruction(Patch patch)
	{
		return "Walk to the " + patch.getDisplayName() + " patch";
	}
}
