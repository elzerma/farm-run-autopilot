package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The player's own recorded leg times, blended into the route's estimates (SPEC 12.4): samples more than
 * three times the estimate are ignored, and the estimate keeps some weight until there are three samples.
 */
public final class LearnedTimes
{
	public static final LearnedTimes NONE = new LearnedTimes(Collections.emptyMap());
	/** Samples needed before the player's own times replace the estimate completely. */
	static final int FULL_WEIGHT_SAMPLES = 3;
	private static final double OUTLIER_FACTOR = 3.0;

	/** Seconds per leg, keyed by {@link #key}. */
	private final Map<String, List<Double>> samples;

	public LearnedTimes(Map<String, List<Double>> samples)
	{
		this.samples = samples;
	}

	/** e.g. "CATHERBY|CAMELOT_TELEPORT|DIRECT". */
	public static String key(String location, String method, String departure)
	{
		return location + "|" + method + "|" + departure;
	}

	/** The estimate adjusted by the player's recorded times for this way of reaching {@code to}. */
	double adjust(Location to, TravelMethod method, Departure departure, double estimate)
	{
		if (method == null)
		{
			return estimate;
		}
		final List<Double> times = samples.get(key(to.name(), method.name(), departure.name()));
		if (times == null)
		{
			return estimate;
		}
		double total = 0;
		int n = 0;
		for (double seconds : times)
		{
			if (seconds > 0 && seconds <= estimate * OUTLIER_FACTOR)
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
