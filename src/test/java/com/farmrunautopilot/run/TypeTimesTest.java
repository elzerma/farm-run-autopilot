package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.PatchType;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Times per patch type (docs/plans/travel-rework.md, section 5): each stop's leg and time there shared by patch
 * count, like quantities compared, a median per patch, bank visits left out.
 */
public class TypeTimesTest
{
	private static final long MINUTE = 60_000;

	@Test
	public void aMixedStopIsSharedByPatchCount()
	{
		final RunTimings.Run run = run(0, 300, "HERB:2,FLOWER:1");
		// One stop with 2 herbs and a flower: 60 s there and 30 s at the patches, so 90 s shared 2:1
		final RunTimings.Leg leg = leg(1000, 60, 30, "HERB:2,FLOWER:1");
		final Map<PatchType, TypeTimes.Part> parts = TypeTimes.of(run, List.of(leg));
		assertEquals(60, parts.get(PatchType.HERB).getSeconds(), 0.001);
		assertEquals(30, parts.get(PatchType.FLOWER).getSeconds(), 0.001);
		assertEquals(30, parts.get(PatchType.HERB).perPatch(), 0.001);
	}

	@Test
	public void aRunWithAStopNotTimedIsLeftOut()
	{
		// Two herbs in the run, but only one stop timed (the other skipped without reaching it)
		final RunTimings.Run run = run(0, 300, "HERB:2");
		assertTrue(TypeTimes.of(run, List.of(leg(1000, 40, 20, "HERB:1"))).isEmpty());
		// An old save without stop times
		assertTrue(TypeTimes.of(run, List.of(leg(1000, 40, 0, null))).isEmpty());
	}

	@Test
	public void likeQuantitiesAreCompared()
	{
		final List<Map<PatchType, TypeTimes.Part>> history = List.of(
			Map.of(PatchType.HERB, new TypeTimes.Part(9, 400)),
			Map.of(PatchType.HERB, new TypeTimes.Part(6, 200)),
			Map.of(PatchType.HERB, new TypeTimes.Part(9, 380), PatchType.TREE, new TypeTimes.Part(4, 170)));
		assertEquals(380, TypeTimes.best(history, PatchType.HERB, 9), 0.001);
		assertEquals(200, TypeTimes.best(history, PatchType.HERB, 6), 0.001);
		assertNull(TypeTimes.best(history, PatchType.HERB, 8));
		// Per patch: 44.4, 33.3, 42.2: the median is 42.2, the best pace 33.3
		assertEquals(380.0 / 9, TypeTimes.medianPerPatch(history, PatchType.HERB), 0.001);
		assertEquals(200.0 / 6, TypeTimes.bestPerPatch(history, PatchType.HERB), 0.001);
		assertEquals(42.5, TypeTimes.medianPerPatch(history, PatchType.TREE), 0.001);
	}

	@Test
	public void theHistoryHasOnlyFinishedFullyTimedRunsOldestFirst()
	{
		final RunTimings.Run first = run(0, 120, "HERB:1");
		final RunTimings.Run unfinished = run(10 * MINUTE, 60, "HERB:1");
		unfinished.setFinished(false);
		final RunTimings.Run second = run(20 * MINUTE, 100, "HERB:1");
		final List<RunTimings.Leg> legs = List.of(leg(1000, 50, 20, "HERB:1"),
			leg(10 * MINUTE + 1000, 40, 10, "HERB:1"), leg(20 * MINUTE + 1000, 30, 15, "HERB:1"));
		final List<Map<PatchType, TypeTimes.Part>> history = TypeTimes.history(List.of(second, unfinished, first), legs);
		assertEquals(2, history.size());
		assertEquals(70, history.get(0).get(PatchType.HERB).getSeconds(), 0.001);
		assertEquals(45, history.get(1).get(PatchType.HERB).getSeconds(), 0.001);
	}

	@Test
	public void thePaceIsTheMedianOfRecentRunsAgainstTheirEstimates()
	{
		final RunTimings.Run a = paced(110, 100);
		final RunTimings.Run b = paced(90, 100);
		final RunTimings.Run c = paced(100, 100);
		assertNull("Needs three runs", RunTimings.pace(List.of(a, b)));
		assertEquals(1.0, RunTimings.pace(List.of(a, b, c)), 0.001);
		// Old saves without an estimate, and unfinished runs, don't count
		final RunTimings.Run old = paced(500, 0);
		final RunTimings.Run stopped = paced(10, 100);
		stopped.setFinished(false);
		assertEquals(1.0, RunTimings.pace(List.of(a, b, c, old, stopped)), 0.001);
	}

	@Test
	public void aLineSaysTheTimeTheBestAndTheMedian()
	{
		assertEquals("9 herbs: 6:12, 41 s each; best 6:05; median 43 s each",
			TypeTimes.line(PatchType.HERB, new TypeTimes.Part(9, 372), 365.0, 43.0));
	}

	private static RunTimings.Run paced(double seconds, double estimate)
	{
		final RunTimings.Run run = run(0, seconds, "HERB:1");
		run.setEstimate(estimate);
		return run;
	}

	private static RunTimings.Run run(long startedAt, double seconds, String makeup)
	{
		return new RunTimings.Run(startedAt, seconds, 1, true, makeup);
	}

	private static RunTimings.Leg leg(long finishedAt, double seconds, double stopSeconds, String counts)
	{
		final RunTimings.Leg leg = new RunTimings.Leg("CATHERBY", null, "DIRECT", seconds, finishedAt, null);
		leg.setStopSeconds(stopSeconds);
		leg.setCounts(counts);
		return leg;
	}
}
