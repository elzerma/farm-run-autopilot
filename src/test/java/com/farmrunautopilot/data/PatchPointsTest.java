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
	public void patchesSharingAVarbitAreFarApart()
	{
		// The scene tracker matches patch objects by varbit within 30 tiles of a point, nearest first
		for (Patch a : Patch.values())
		{
			for (Patch b : Patch.values())
			{
				// Bounds keep some apart: the game sends the Catherby allotments' varbits only away from the fruit tree
				if (a.ordinal() < b.ordinal() && a.getVarbitId() == b.getVarbitId()
					&& a.isInBounds(PatchPoints.of(b)) && b.isInBounds(PatchPoints.of(a)))
				{
					final int distance = PatchPoints.of(a).distanceTo2D(PatchPoints.of(b));
					// In different regions (the Falador farm and Draynor Manor, 46 tiles; the Lumbridge hops and the Champions'
					// Guild, 48), beyond the tracker's 30 is enough
					final int least = sameRegion(a, b) ? 60 : 30;
					assertTrue(a + " and " + b + " are only " + distance + " tiles apart", distance > least);
				}
			}
		}
	}

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

	/** Whether one patch's region is the other's, or one of its extra regions. */
	private static boolean sameRegion(Patch a, Patch b)
	{
		if (a.getRegionId() == b.getRegionId())
		{
			return true;
		}
		for (int id : a.getExtraRegionIds())
		{
			if (id == b.getRegionId())
			{
				return true;
			}
		}
		for (int id : b.getExtraRegionIds())
		{
			if (id == a.getRegionId())
			{
				return true;
			}
		}
		return false;
	}
}
