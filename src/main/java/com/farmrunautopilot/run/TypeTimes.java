package com.farmrunautopilot.run;

import com.farmrunautopilot.data.PatchType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Run times per patch type (docs/plans/travel-rework.md, section 5; elzerma: "like quantities of the same type are
 * compared to each other", with a median per type after each run). Each stop's leg and its time there are shared
 * between the stop's patch types by patch count; bank visits stay out. The player's own times only: nothing here is
 * shared, and nothing here changes the route.
 */
final class TypeTimes
{
	/** Runs the median per patch looks back over. */
	static final int MEDIAN_RUNS = 10;

	/** One type's patches in one run and the time spent on them. */
	@Value
	static class Part
	{
		int patches;
		double seconds;

		double perPatch()
		{
			return seconds / patches;
		}
	}

	private TypeTimes()
	{
	}

	/** Each finished, fully timed run's time per type, oldest first. */
	static List<Map<PatchType, Part>> history(List<RunTimings.Run> runs, List<RunTimings.Leg> legs)
	{
		final List<RunTimings.Run> sorted = new ArrayList<>(runs);
		sorted.sort(Comparator.comparingLong(RunTimings.Run::getStartedAt));
		final List<Map<PatchType, Part>> history = new ArrayList<>();
		for (RunTimings.Run run : sorted)
		{
			if (run.isFinished())
			{
				final Map<PatchType, Part> parts = of(run, legsOf(run, legs));
				if (!parts.isEmpty())
				{
					history.add(parts);
				}
			}
		}
		return history;
	}

	/** The legs saved with this run: those finished while it ran. */
	static List<RunTimings.Leg> legsOf(RunTimings.Run run, List<RunTimings.Leg> legs)
	{
		final long end = run.getStartedAt() + (long) (run.getSeconds() * 1000) + 1000;
		final List<RunTimings.Leg> found = new ArrayList<>();
		for (RunTimings.Leg leg : legs)
		{
			if (leg.getFinishedAt() >= run.getStartedAt() && leg.getFinishedAt() <= end)
			{
				found.add(leg);
			}
		}
		return found;
	}

	/** A run's time per type; empty if a stop wasn't timed (a skipped stop, or a save from before 2026-10-18). */
	static Map<PatchType, Part> of(RunTimings.Run run, List<RunTimings.Leg> legs)
	{
		final Map<PatchType, Integer> expected = RunTimings.parseMakeup(run.getMakeup());
		final Map<PatchType, Integer> patches = new EnumMap<>(PatchType.class);
		final Map<PatchType, Double> seconds = new EnumMap<>(PatchType.class);
		for (RunTimings.Leg leg : legs)
		{
			final Map<PatchType, Integer> counts = RunTimings.parseMakeup(leg.getCounts());
			final int all = counts.values().stream().mapToInt(Integer::intValue).sum();
			if (all == 0)
			{
				continue;
			}
			final double time = leg.getSeconds() + leg.getStopSeconds();
			for (Map.Entry<PatchType, Integer> e : counts.entrySet())
			{
				patches.merge(e.getKey(), e.getValue(), Integer::sum);
				seconds.merge(e.getKey(), time * e.getValue() / all, Double::sum);
			}
		}
		if (expected.isEmpty() || !expected.equals(patches))
		{
			return Collections.emptyMap();
		}
		final Map<PatchType, Part> parts = new EnumMap<>(PatchType.class);
		for (Map.Entry<PatchType, Integer> e : patches.entrySet())
		{
			parts.put(e.getKey(), new Part(e.getValue(), seconds.get(e.getKey())));
		}
		return parts;
	}

	/** The best time for exactly this many patches of a type, or null if no run had that many. */
	static Double best(List<Map<PatchType, Part>> history, PatchType type, int patches)
	{
		Double best = null;
		for (Map<PatchType, Part> run : history)
		{
			final Part part = run.get(type);
			if (part != null && part.getPatches() == patches && (best == null || part.getSeconds() < best))
			{
				best = part.getSeconds();
			}
		}
		return best;
	}

	/** The median time per patch of a type over the last {@link #MEDIAN_RUNS} runs with it, or null. */
	static Double medianPerPatch(List<Map<PatchType, Part>> history, PatchType type)
	{
		final List<Double> perPatch = new ArrayList<>();
		for (int i = history.size() - 1; i >= 0 && perPatch.size() < MEDIAN_RUNS; i--)
		{
			final Part part = history.get(i).get(type);
			if (part != null)
			{
				perPatch.add(part.perPatch());
			}
		}
		if (perPatch.isEmpty())
		{
			return null;
		}
		Collections.sort(perPatch);
		final int n = perPatch.size();
		return n % 2 == 1 ? perPatch.get(n / 2) : (perPatch.get(n / 2 - 1) + perPatch.get(n / 2)) / 2;
	}

	/** The quickest time per patch of a type in any run, or null: the pace the splits are against. */
	static Double bestPerPatch(List<Map<PatchType, Part>> history, PatchType type)
	{
		Double best = null;
		for (Map<PatchType, Part> run : history)
		{
			final Part part = run.get(type);
			if (part != null && (best == null || part.perPatch() < best))
			{
				best = part.perPatch();
			}
		}
		return best;
	}

	/** e.g. "9 herbs: 6:12, 41 s each; best 6:05; median 43 s each". */
	static String line(PatchType type, Part part, Double best, Double medianPerPatch)
	{
		final int n = part.getPatches();
		final StringBuilder s = new StringBuilder();
		s.append(n).append(' ').append((n == 1 ? type.getDisplayName() : type.getPluralName()).toLowerCase())
			.append(": ").append(GuidanceOverlay.clock(Math.round(part.getSeconds())))
			.append(", ").append(Math.round(part.perPatch())).append(" s each");
		if (best != null)
		{
			s.append("; best ").append(GuidanceOverlay.clock(Math.round(best)));
		}
		if (medianPerPatch != null)
		{
			s.append("; median ").append(Math.round(medianPerPatch)).append(" s each");
		}
		return s.toString();
	}
}
