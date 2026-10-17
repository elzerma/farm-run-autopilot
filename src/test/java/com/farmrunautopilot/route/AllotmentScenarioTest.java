package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Allotments, flowers and hops in the one combined route (docs/plans/crop-types.md, Sprint 1a), on an account like
 * elzerma's.
 */
public class AllotmentScenarioTest
{
	private static List<RouteStop> plan(List<Patch> patches)
	{
		return RoutePlanner.plan(patches, new RunConfig().sanitise(), ElzermaAccount.access(), ElzermaAccount.holdings(),
			new PohSetup(), LearnedTimes.NONE, null, null, 0).getStops();
	}

	@Test
	public void allotmentsAndFlowersShareTheHerbStops()
	{
		final List<Patch> herbs = ElzermaAccount.patches(PatchType.HERB);
		final List<Patch> all = ElzermaAccount.patches(PatchType.HERB, PatchType.ALLOTMENT, PatchType.FLOWER);
		final List<RouteStop> herbRun = plan(herbs);
		final List<RouteStop> combined = plan(all);

		// Each stop once: the allotments and flower patch are done with the herb, not on a second visit
		final Set<Location> seen = EnumSet.noneOf(Location.class);
		for (RouteStop stop : combined)
		{
			assertTrue("visited twice: " + stop.getLocation(), seen.add(stop.getLocation()));
		}
		// Kastori's lone flower patch is the only stop they add
		final Set<Location> herbStops = EnumSet.noneOf(Location.class);
		herbRun.forEach(stop -> herbStops.add(stop.getLocation()));
		seen.removeAll(herbStops);
		assertEquals(EnumSet.of(Location.KASTORI), seen);
	}

	@Test
	public void hopsJoinTheRouteAsTheirOwnStops()
	{
		final List<Patch> patches = ElzermaAccount.patches(PatchType.HERB);
		patches.add(Patch.LUMBRIDGE_HOPS);
		patches.add(Patch.SEERS_VILLAGE_HOPS);
		final List<RouteStop> stops = plan(patches);
		final Set<Location> locations = EnumSet.noneOf(Location.class);
		stops.forEach(stop -> locations.add(stop.getLocation()));
		assertTrue(locations.contains(Location.LUMBRIDGE_HOPS));
		assertTrue(locations.contains(Location.SEERS_VILLAGE_HOPS));
		for (RouteStop stop : stops)
		{
			if (stop.getLocation() == Location.LUMBRIDGE_HOPS || stop.getLocation() == Location.SEERS_VILLAGE_HOPS)
			{
				// Reached by a real method or a walk, never an unmeasured fallback
				assertTrue(stop.getLegSeconds() > 0 && stop.getLegSeconds() < 120);
			}
		}
	}
}
