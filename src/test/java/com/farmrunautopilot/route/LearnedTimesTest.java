package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.run.RunTimings;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LearnedTimesTest
{
	private static LearnedTimes with(Double... seconds)
	{
		return new LearnedTimes(Collections.singletonMap(
			LearnedTimes.key("CATHERBY", "CAMELOT_TELEPORT", "DIRECT"), Arrays.asList(seconds)));
	}

	private static double adjust(LearnedTimes learned, double estimate)
	{
		return learned.adjust(Location.CATHERBY, TravelMethod.CAMELOT_TELEPORT, Departure.DIRECT, estimate);
	}

	@Test
	public void noSamplesKeepsTheEstimate()
	{
		assertEquals(30, adjust(LearnedTimes.NONE, 30), 1e-9);
	}

	@Test
	public void blendsUntilThreeSamples()
	{
		// One sample of 60s against a 30s estimate: one third of the way
		assertEquals(40, adjust(with(60.0), 30), 1e-9);
		// Three samples averaging 45s replace the estimate
		assertEquals(45, adjust(with(40.0, 45.0, 50.0), 30), 1e-9);
	}

	@Test
	public void ignoresOutliers()
	{
		// 200s is more than three times the 30s estimate (e.g. the player went to the bank)
		assertEquals(45, adjust(with(40.0, 45.0, 50.0, 200.0), 30), 1e-9);
	}

	@Test
	public void savedLegsBecomeSamplesExceptTheFirstLegAndWalks()
	{
		final LearnedTimes learned = RunTimings.learned(Arrays.asList(
			new RunTimings.Leg("CATHERBY", null, "START", 300, 1),
			new RunTimings.Leg("CATHERBY", "CAMELOT_TELEPORT", "DIRECT", 45, 2),
			new RunTimings.Leg("CATHERBY", "CAMELOT_TELEPORT", "DIRECT", 45, 3),
			new RunTimings.Leg("CATHERBY", "CAMELOT_TELEPORT", "DIRECT", 45, 4)));
		assertEquals(45, adjust(learned, 30), 1e-9);
	}

	@Test
	public void otherMethodsAreUnaffected()
	{
		final List<Double> samples = Arrays.asList(60.0, 60.0, 60.0);
		final LearnedTimes learned = new LearnedTimes(Collections.singletonMap(
			LearnedTimes.key("CATHERBY", "CAMELOT_TELEPORT", "POH_NEXUS"), samples));
		assertEquals(30, adjust(learned, 30), 1e-9);
	}
}
