package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import com.farmrunautopilot.data.PatchType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RunTimingsTest
{
	@Test
	public void runsAreComparedWithLikeRuns()
	{
		final Map<PatchType, Integer> counts = new EnumMap<>(PatchType.class);
		counts.put(PatchType.TREE, 6);
		counts.put(PatchType.HERB, 6);
		final String makeup = RunTimings.makeup(counts);
		assertEquals("6 trees, 6 herbs",RunTimings.makeupName(counts));

		final List<RunTimings.Run> runs = Arrays.asList(
			new RunTimings.Run(1, 600, 12, true, makeup),
			new RunTimings.Run(2, 500, 12, true, makeup),
			new RunTimings.Run(3, 300, 6, true, "HERB:6"),
			new RunTimings.Run(4, 400, 5, false, makeup),
			new RunTimings.Run(5, 550, 12, true, makeup),
			new RunTimings.Run(6, 700, 12, true, makeup));
		assertEquals(Arrays.asList(500.0, 550.0, 600.0), RunTimings.best(runs, makeup, 3));
	}

	@Test
	public void rankText()
	{
		assertEquals(" - new best for 6 herbs!", RunSession.rank(Arrays.asList(300.0, 320.0), 300, "6 herbs"));
		assertEquals(" - 2nd best for 6 herbs", RunSession.rank(Arrays.asList(300.0, 320.0), 320, "6 herbs"));
		assertEquals(" - first time for 6 herbs", RunSession.rank(Arrays.asList(300.0), 300, "6 herbs"));
		assertEquals("", RunSession.rank(Arrays.asList(1.0, 2.0, 3.0), 400, "6 herbs"));
	}
}
