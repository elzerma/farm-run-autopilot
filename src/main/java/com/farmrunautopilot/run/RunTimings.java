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
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Saves how long each leg and run took, per account (SPEC 12.4). LearnedTimes uses these to replace the
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
	/** The pace looks at the last this many finished runs, and needs at least the second number. */
	static final int PACE_RUNS = 5;
	static final int PACE_MIN_RUNS = 3;

	/** No-argument constructor so Gson can build these normally. */
	@Data
	@NoArgsConstructor
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
		/**
		 * Location enum name of the stop the leg started at, "BANK" after a bank visit, or null (the first leg,
		 * and saves from before 2026-10-11).
		 */
		String from;
		/** Seconds at the stop, from reaching its patch to its last patch done; 0 in saves from before 2026-10-18. */
		double stopSeconds;
		/** The stop's patches in this run by type, e.g. "HERB:1,FLOWER:1"; null in saves from before 2026-10-18. */
		String counts;

		public Leg(String location, String method, String departure, double seconds, long finishedAt, String from)
		{
			this.location = location;
			this.method = method;
			this.departure = departure;
			this.seconds = seconds;
			this.finishedAt = finishedAt;
			this.from = from;
		}
	}

	@Data
	@NoArgsConstructor
	public static class Run
	{
		long startedAt;
		double seconds;
		int stops;
		boolean finished;
		/** Patches by type, e.g. "HERB:6,TREE:6", so runs are only compared with like runs. Null in old saves. */
		String makeup;
		/** Seconds spent on bank visits partway; 0 in saves from before 2026-10-18. */
		double bankSeconds;
		/** The plan's estimate for the run when it started, in seconds; 0 in saves from before 2026-10-18. */
		double estimate;

		public Run(long startedAt, double seconds, int stops, boolean finished, String makeup)
		{
			this.startedAt = startedAt;
			this.seconds = seconds;
			this.stops = stops;
			this.finished = finished;
			this.makeup = makeup;
		}
	}

	private final ConfigManager configManager;
	private final Gson gson;
	/** Saved runs and learned leg times for {@link #cachedProfile}, so they aren't re-read every tick. */
	private List<Run> cachedRuns;
	private LearnedTimes cachedLearned;
	/** Each finished run's time per patch type, oldest first. */
	private List<Map<PatchType, TypeTimes.Part>> cachedTypes;
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
		cachedTypes = null;
	}

	/**
	 * How the player's runs compare with the plan's estimates: the median of "actual over estimate" over their last
	 * {@link #PACE_RUNS} finished runs, or null with fewer than {@link #PACE_MIN_RUNS}. Only for showing a truer
	 * estimate; it never changes the route (elzerma, 2026-10-17).
	 */
	public Double pace()
	{
		checkProfile();
		if (cachedRuns == null)
		{
			cachedRuns = read(RUNS_KEY, Run[].class);
		}
		return pace(cachedRuns);
	}

	static Double pace(List<Run> runs)
	{
		final List<Double> ratios = new ArrayList<>();
		for (int i = runs.size() - 1; i >= 0 && ratios.size() < PACE_RUNS; i--)
		{
			final Run run = runs.get(i);
			if (run.isFinished() && run.getEstimate() > 0)
			{
				ratios.add(run.getSeconds() / run.getEstimate());
			}
		}
		if (ratios.size() < PACE_MIN_RUNS)
		{
			return null;
		}
		Collections.sort(ratios);
		final int n = ratios.size();
		return n % 2 == 1 ? ratios.get(n / 2) : (ratios.get(n / 2 - 1) + ratios.get(n / 2)) / 2;
	}

	/**
	 * Each finished run's time per patch type, oldest first; runs saved before stops were timed (2026-10-18), or with
	 * a stop not timed, are left out.
	 */
	public List<Map<PatchType, TypeTimes.Part>> typeHistory()
	{
		checkProfile();
		if (cachedTypes == null)
		{
			cachedTypes = TypeTimes.history(read(RUNS_KEY, Run[].class), read(LEGS_KEY, Leg[].class));
		}
		return cachedTypes;
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
			// The trip to the first stop starts wherever the player banked, so it isn't a sample
			if (leg.getDeparture() == null || leg.getLocation() == null || "START".equals(leg.getDeparture()))
			{
				continue;
			}
			final String key = LearnedTimes.key(leg.getFrom(), leg.getLocation(), leg.getMethod(), leg.getDeparture());
			if (key != null)
			{
				samples.computeIfAbsent(key, k -> new ArrayList<>()).add(leg.getSeconds());
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
			cachedTypes = null;
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

	/** "HERB:6,TREE:6" back to counts; unknown types (from a later version) are skipped. */
	public static Map<PatchType, Integer> parseMakeup(String makeup)
	{
		final Map<PatchType, Integer> counts = new java.util.EnumMap<>(PatchType.class);
		if (makeup == null || makeup.isEmpty())
		{
			return counts;
		}
		for (String part : makeup.split(","))
		{
			final String[] kv = part.split(":");
			try
			{
				counts.put(PatchType.valueOf(kv[0]), Integer.parseInt(kv[1]));
			}
			catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e)
			{
				// A type or format this version doesn't know
			}
		}
		return counts;
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
				parts.add(n + " " + (n == 1 ? type.getDisplayName() : type.getPluralName()).toLowerCase());
			}
		}
		return String.join(", ", parts);
	}

	/** Forget every recorded leg and run (learned times and best times) for this account. */
	public void clear()
	{
		configManager.unsetRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, LEGS_KEY);
		configManager.unsetRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, RUNS_KEY);
		cachedRuns = null;
		cachedLearned = null;
		cachedTypes = null;
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
