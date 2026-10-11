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
			LearnedTimes.key(null, "CATHERBY", "CAMELOT_TELEPORT", "DIRECT"), Arrays.asList(seconds)));
	}

	private static double adjust(LearnedTimes learned, double estimate)
	{
		return learned.adjust(null, Location.CATHERBY, TravelMethod.CAMELOT_TELEPORT, Departure.DIRECT, estimate);
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
		// So is 4s, under a third of it (a stop skipped through without going there)
		assertEquals(45, adjust(with(40.0, 45.0, 50.0, 4.0), 30), 1e-9);
	}

	@Test
	public void savedLegsBecomeSamplesExceptTheFirstLeg()
	{
		final LearnedTimes learned = RunTimings.learned(Arrays.asList(
			new RunTimings.Leg("CATHERBY", null, "START", 300, 1, null),
			new RunTimings.Leg("CATHERBY", "CAMELOT_TELEPORT", "DIRECT", 45, 2, null),
			new RunTimings.Leg("CATHERBY", "CAMELOT_TELEPORT", "DIRECT", 45, 3, null),
			new RunTimings.Leg("CATHERBY", "CAMELOT_TELEPORT", "DIRECT", 45, 4, null)));
		assertEquals(45, adjust(learned, 30), 1e-9);
	}

	@Test
	public void otherMethodsAreUnaffected()
	{
		final List<Double> samples = Arrays.asList(60.0, 60.0, 60.0);
		final LearnedTimes learned = new LearnedTimes(Collections.singletonMap(
			LearnedTimes.key(null, "CATHERBY", "CAMELOT_TELEPORT", "POH_NEXUS"), samples));
		assertEquals(30, adjust(learned, 30), 1e-9);
	}

	@Test
	public void legsThatDependOnWhereTheyStartAreKeptPerStart()
	{
		// The Stronghold's spirit tree is 14 tiles from its patch; Tree Gnome Village's is 60
		final LearnedTimes learned = RunTimings.learned(Arrays.asList(
			new RunTimings.Leg("TREE_GNOME_VILLAGE", "SPIRIT_TREE_TREE_GNOME_VILLAGE", "DIRECT", 30, 1, "GNOME_STRONGHOLD"),
			new RunTimings.Leg("TREE_GNOME_VILLAGE", "SPIRIT_TREE_TREE_GNOME_VILLAGE", "DIRECT", 30, 2, "GNOME_STRONGHOLD"),
			new RunTimings.Leg("TREE_GNOME_VILLAGE", "SPIRIT_TREE_TREE_GNOME_VILLAGE", "DIRECT", 30, 3, "GNOME_STRONGHOLD"),
			// Saved before the start was recorded: can't tell where it started, so not a sample
			new RunTimings.Leg("TREE_GNOME_VILLAGE", "SPIRIT_TREE_TREE_GNOME_VILLAGE", "DIRECT", 90, 4, null),
			new RunTimings.Leg("TAVERLEY", null, "WALK", 20, 5, "FALADOR_PARK"),
			new RunTimings.Leg("TAVERLEY", null, "WALK", 20, 6, "FALADOR_PARK"),
			new RunTimings.Leg("TAVERLEY", null, "WALK", 20, 7, "FALADOR_PARK")));
		assertEquals(30, learned.adjust(Location.GNOME_STRONGHOLD, Location.TREE_GNOME_VILLAGE,
			TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, Departure.DIRECT, 40), 1e-9);
		// From somewhere else: no samples
		assertEquals(40, learned.adjust(Location.FARMING_GUILD, Location.TREE_GNOME_VILLAGE,
			TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, Departure.DIRECT, 40), 1e-9);
		// Walks are learned too
		assertEquals(20, learned.adjust(Location.FALADOR_PARK, Location.TAVERLEY, null, Departure.WALK, 33), 1e-9);
	}
}
