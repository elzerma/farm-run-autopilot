package com.farmrunautopilot.data;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PatchPointsTest
{
	@Test
	public void everyPatchHasAPointInsideItsOwnRegion()
	{
		for (Patch patch : Patch.values())
		{
			final WorldPoint point = PatchPoints.of(patch);
			assertNotNull(patch.name(), point);

			final Set<Integer> regions = new HashSet<>();
			regions.add(patch.getRegionId());
			for (int region : patch.getExtraRegionIds())
			{
				regions.add(region);
			}
			assertTrue(patch + " point " + point + " is in region " + point.getRegionID(),
				regions.contains(point.getRegionID()));
			assertTrue(patch + " bounds", patch.isInBounds(point));
		}
	}
}
