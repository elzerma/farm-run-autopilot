package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.route.LearnedTimes;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Saves how long each leg and run took, per account (SPEC 12.4). Milestone M8 uses these to replace the
 * starting time estimates with the player's own.
 */
@Slf4j
@Singleton
public class RunTimings
{
	private static final String LEGS_KEY = "timings.legs";
	private static final String RUNS_KEY = "timings.runs";
	/** Keep the most recent samples only. */
	private static final int MAX_LEGS = 500;
	private static final int MAX_RUNS = 100;

	/** No-argument constructor so Gson can build these normally. */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Leg
	{
		/** Location enum name. */
		String location;
		/** TravelMethod enum name, or null for walking and for the trip to the first stop. */
		String method;
		/** Departure enum name. */
		String departure;
		double seconds;
		long finishedAt;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Run
	{
		long startedAt;
		double seconds;
		int stops;
		boolean finished;
		/** Patches by type, e.g. "HERB:6,TREE:6", so runs are only compared with like runs. Null in old saves. */
		String makeup;
	}

	private final ConfigManager configManager;
	private final Gson gson;
	/** Saved runs and learned leg times for {@link #cachedProfile}, so they aren't re-read every tick. */
	private List<Run> cachedRuns;
	private LearnedTimes cachedLearned;
	private String cachedProfile;

	@Inject
	RunTimings(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void save(List<Leg> legs, Run run)
	{
		final List<Leg> savedLegs = read(LEGS_KEY, Leg[].class);
		savedLegs.addAll(legs);
		write(LEGS_KEY, trim(savedLegs, MAX_LEGS));

		final List<Run> savedRuns = read(RUNS_KEY, Run[].class);
		savedRuns.add(run);
		write(RUNS_KEY, trim(savedRuns, MAX_RUNS));
		cachedRuns = null;
		cachedLearned = null;
	}

	/** The fastest finished runs with this makeup, quickest first, at most {@code limit}. */
	public List<Double> best(String makeup, int limit)
	{
		checkProfile();
		if (cachedRuns == null)
		{
			cachedRuns = read(RUNS_KEY, Run[].class);
		}
		return best(cachedRuns, makeup, limit);
	}

	/** The player's recorded times for each way of reaching each stop. */
	public LearnedTimes learned()
	{
		checkProfile();
		if (cachedLearned == null)
		{
			cachedLearned = learned(read(LEGS_KEY, Leg[].class));
		}
		return cachedLearned;
	}

	public static LearnedTimes learned(List<Leg> legs)
	{
		final Map<String, List<Double>> samples = new HashMap<>();
		for (Leg leg : legs)
		{
			// The trip to the first stop and walks between stops aren't samples of a travel method
			if (leg.getMethod() != null && leg.getDeparture() != null && leg.getLocation() != null)
			{
				samples.computeIfAbsent(LearnedTimes.key(leg.getLocation(), leg.getMethod(), leg.getDeparture()),
					k -> new ArrayList<>()).add(leg.getSeconds());
			}
		}
		return new LearnedTimes(samples);
	}

	private void checkProfile()
	{
		final String profile = configManager.getRSProfileKey();
		if (!Objects.equals(profile, cachedProfile))
		{
			cachedRuns = null;
			cachedLearned = null;
			cachedProfile = profile;
		}
	}

	static List<Double> best(List<Run> runs, String makeup, int limit)
	{
		final List<Double> times = new ArrayList<>();
		for (Run run : runs)
		{
			if (run.isFinished() && makeup.equals(run.getMakeup()))
			{
				times.add(run.getSeconds());
			}
		}
		Collections.sort(times);
		return times.size() <= limit ? times : new ArrayList<>(times.subList(0, limit));
	}

	/** e.g. "HERB:6,TREE:6", in patch type order. */
	public static String makeup(Map<PatchType, Integer> counts)
	{
		final List<String> parts = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			final int n = counts.getOrDefault(type, 0);
			if (n > 0)
			{
				parts.add(type.name() + ":" + n);
			}
		}
		return String.join(",", parts);
	}

	/** e.g. "6 herbs, 6 trees". */
	public static String makeupName(Map<PatchType, Integer> counts)
	{
		final List<String> parts = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			final int n = counts.getOrDefault(type, 0);
			if (n > 0)
			{
				parts.add(n + " " + type.getDisplayName().toLowerCase() + (n == 1 ? "" : "s"));
			}
		}
		return String.join(", ", parts);
	}

	public List<Leg> legs()
	{
		return read(LEGS_KEY, Leg[].class);
	}

	/** Stored as JSON arrays, so plain array classes are enough for Gson (no type tokens needed). */
	private <T> List<T> read(String key, Class<T[]> type)
	{
		final String json = configManager.getRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key);
		if (json == null)
		{
			return new ArrayList<>();
		}
		try
		{
			final T[] items = gson.fromJson(json, type);
			return items != null ? new ArrayList<>(Arrays.asList(items)) : new ArrayList<>();
		}
		catch (JsonParseException e)
		{
			log.warn("Couldn't read saved {}", key, e);
			return new ArrayList<>();
		}
	}

	private void write(String key, Object value)
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key, gson.toJson(value));
		}
	}

	private static <T> List<T> trim(List<T> list, int max)
	{
		return list.size() <= max ? list : new ArrayList<>(list.subList(list.size() - max, list.size()));
	}
}
