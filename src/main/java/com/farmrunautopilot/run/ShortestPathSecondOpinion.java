package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Sprint T8 of docs/plans/travel-rework.md, for the dev client's log only: when a route is planned, asks the Shortest
 * Path plugin (if installed) for its own fastest way for each leg, with the player's own Shortest Path settings
 * ("shortestpath" / "query"), and logs each answer beside the plugin's estimate. Never while one of our walking lines
 * is drawn: those switch Shortest Path to walking only, and a drawn path cancels pending questions. Client thread.
 */
@Slf4j
@Singleton
public class ShortestPathSecondOpinion
{
	private static final String NAMESPACE = "shortestpath";
	private static final double SECONDS_PER_TILE = 0.3;
	private static final double SECONDS_PER_TICK = 0.6;

	private final EventBus eventBus;
	/** Questions still waiting for an answer: id to what the leg is. */
	private final Map<String, String> pending = new HashMap<>();
	private int round;

	@Inject
	ShortestPathSecondOpinion(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	/** Ask about every leg of this plan: from the previous stop's first patch (the first from where the player is). */
	public void ask(RunPlan plan)
	{
		round++;
		pending.clear();
		final Map<Location, Set<WorldPoint>> stopPoints = new LinkedHashMap<>();
		for (Patch patch : plan.getSelection().getPatches())
		{
			final WorldPoint point = PatchPoints.of(patch);
			if (point != null)
			{
				stopPoints.computeIfAbsent(patch.getLocation(), k -> new LinkedHashSet<>()).add(point);
			}
		}
		WorldPoint from = null;
		final List<RouteStop> stops = plan.getRoute().getStops();
		for (int i = 0; i < stops.size(); i++)
		{
			final RouteStop stop = stops.get(i);
			final Set<WorldPoint> targets = stopPoints.get(stop.getLocation());
			if (targets == null)
			{
				continue;
			}
			final String id = "farmrun-" + round + "-" + i;
			final Map<String, Object> data = new HashMap<>();
			data.put("id", id);
			if (from != null)
			{
				data.put("start", from);
			}
			data.put("target", new LinkedHashSet<>(targets));
			pending.put(id, String.format(Locale.ROOT, "%d. %s (plugin %.0f s: %s)", i + 1,
				stop.getLocation().getDisplayName(), stop.getLegSeconds(), stop.describeTravel()));
			eventBus.post(new PluginMessage(NAMESPACE, "query", data));
			from = targets.iterator().next();
		}
	}

	/** An answer from Shortest Path; logged against the leg it's for. */
	public void onPluginMessage(PluginMessage event)
	{
		if (!NAMESPACE.equals(event.getNamespace()) || !"result".equals(event.getName()))
		{
			return;
		}
		final Map<String, Object> data = event.getData();
		final String leg = pending.remove(String.valueOf(data.get("id")));
		if (leg == null)
		{
			return;
		}
		if (!Boolean.TRUE.equals(data.get("reached")))
		{
			log.debug("Shortest Path (live): {}: no way ({})", leg, data.get("reason"));
			return;
		}
		// Its cost counts a walked tile as one tick; split the walk from the transports to time them as the plugin does
		final int cost = data.get("cost") instanceof Integer ? (Integer) data.get("cost") : -1;
		final int walked = walkedTiles(data.get("path"), data.get("transports"));
		final double seconds = walked * SECONDS_PER_TILE + Math.max(0, cost - walked) * SECONDS_PER_TICK;
		log.debug("Shortest Path (live): {}: {} s, walking {} tiles, via {}", leg, Math.round(seconds), walked,
			describe(data.get("transports")));
	}

	/** Tiles walked along the path, not counting the jumps a transport makes. */
	static int walkedTiles(Object path, Object transports)
	{
		if (!(path instanceof List))
		{
			return 0;
		}
		final Set<String> jumps = new LinkedHashSet<>();
		if (transports instanceof List)
		{
			for (Object t : (List<?>) transports)
			{
				if (t instanceof Map)
				{
					jumps.add(((Map<?, ?>) t).get("origin") + ">" + ((Map<?, ?>) t).get("destination"));
				}
			}
		}
		int tiles = 0;
		WorldPoint last = null;
		for (Object o : (List<?>) path)
		{
			if (!(o instanceof WorldPoint))
			{
				continue;
			}
			final WorldPoint p = (WorldPoint) o;
			if (last != null && last.getPlane() == p.getPlane() && !jumps.contains(last + ">" + p))
			{
				tiles += Math.max(Math.abs(p.getX() - last.getX()), Math.abs(p.getY() - last.getY()));
			}
			last = p;
		}
		return tiles;
	}

	private static String describe(Object transports)
	{
		if (!(transports instanceof List) || ((List<?>) transports).isEmpty())
		{
			return "walking only";
		}
		final List<String> names = new ArrayList<>();
		for (Object t : (List<?>) transports)
		{
			if (t instanceof Map)
			{
				final Object info = ((Map<?, ?>) t).get("displayInfo");
				final Object object = ((Map<?, ?>) t).get("objectInfo");
				names.add(info != null && !String.valueOf(info).isEmpty() ? String.valueOf(info) : String.valueOf(object));
			}
		}
		return String.join(" > ", names);
	}
}
