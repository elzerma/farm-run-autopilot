package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The player's own recorded leg times, blended into the route's estimates (SPEC 12.4): samples more than
 * three times the estimate or under a third of it are ignored (a trip to the bank mid-leg, a leg skipped
 * through), and the estimate keeps some weight until there are three samples.
 *
 * <p>A teleport takes the same time wherever it's cast, but a walk, the spirit tree or fairy ring by the stop
 * just finished, and Renu from its landing pad depend on where the leg starts; those are kept per starting
 * stop, so a 14-tile walk to the Stronghold's spirit tree isn't averaged with a 60-tile one.
 */
public final class LearnedTimes
{
	public static final LearnedTimes NONE = new LearnedTimes(Collections.emptyMap());
	/** Samples needed before the player's own times replace the estimate completely. */
	static final int FULL_WEIGHT_SAMPLES = 3;
	private static final double OUTLIER_FACTOR = 3.0;
	/** The method name recorded for a walk between stops. */
	private static final String WALK = "WALK";

	/** Seconds per leg, keyed by {@link #key}. */
	private final Map<String, List<Double>> samples;

	public LearnedTimes(Map<String, List<Double>> samples)
	{
		this.samples = samples;
	}

	/** No legs learned yet. */
	public boolean isEmpty()
	{
		return samples.isEmpty();
	}

	/**
	 * One line per learned leg for "Share your run times" (Sprint 9): its key, the median seconds and how many
	 * samples, e.g. "CATHERBY|CAMELOT_TELEPORT|DIRECT: 34.1 s (3)". Nothing else about the player.
	 */
	public List<String> summary()
	{
		final List<String> lines = new java.util.ArrayList<>();
		for (Map.Entry<String, List<Double>> e : new java.util.TreeMap<>(samples).entrySet())
		{
			final List<Double> sorted = new java.util.ArrayList<>(e.getValue());
			if (sorted.isEmpty())
			{
				continue;
			}
			Collections.sort(sorted);
			final int n = sorted.size();
			final double median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
			lines.add(e.getKey() + ": " + Math.round(median * 10) / 10.0 + " s (" + n + ")");
		}
		return lines;
	}

	/**
	 * e.g. "CATHERBY|CAMELOT_TELEPORT|DIRECT", or "GNOME_STRONGHOLD>TREE_GNOME_VILLAGE|SPIRIT_TREE_..|DIRECT"
	 * for a leg that depends on where it starts. Null if it depends on that and the start isn't known (old
	 * samples, the first leg of a run, the leg after a bank).
	 *
	 * @param from the stop the leg started at, or null
	 * @param method the travel method, or null for a walk
	 */
	public static String key(String from, String to, String method, String departure)
	{
		final String way = (method == null ? WALK : method) + "|" + departure;
		if (!dependsOnStart(method, departure))
		{
			return to + "|" + way;
		}
		return from == null ? null : from + ">" + to + "|" + way;
	}

	/** Walks, Renu, the fairy ring by the last stop, the spirit tree by the last stop, and that tree into the house. */
	static boolean dependsOnStart(String method, String departure)
	{
		if (method == null || Departure.WALK.name().equals(departure) || Departure.RENU.name().equals(departure)
			|| Departure.FAIRY_RING_NEARBY.name().equals(departure) || departure.startsWith("TREE_HOUSE_"))
		{
			return true;
		}
		if (!Departure.DIRECT.name().equals(departure))
		{
			return false;
		}
		try
		{
			return TravelMethod.valueOf(method).getKind() == TravelKind.SPIRIT_TREE;
		}
		catch (IllegalArgumentException e)
		{
			// A method from an older version of the plugin
			return false;
		}
	}

	/** The estimate adjusted by the player's recorded times for this way from {@code from} to {@code to}. */
	double adjust(Location from, Location to, TravelMethod method, Departure departure, double estimate)
	{
		final String key = key(from == null ? null : from.name(), to.name(), method == null ? null : method.name(),
			departure.name());
		final List<Double> times = key == null ? null : samples.get(key);
		if (times == null)
		{
			return estimate;
		}
		double total = 0;
		int n = 0;
		for (double seconds : times)
		{
			if (seconds >= estimate / OUTLIER_FACTOR && seconds <= estimate * OUTLIER_FACTOR)
			{
				total += seconds;
				n++;
			}
		}
		if (n == 0)
		{
			return estimate;
		}
		final double weight = Math.min(n, FULL_WEIGHT_SAMPLES) / (double) FULL_WEIGHT_SAMPLES;
		return weight * (total / n) + (1 - weight) * estimate;
	}
}
