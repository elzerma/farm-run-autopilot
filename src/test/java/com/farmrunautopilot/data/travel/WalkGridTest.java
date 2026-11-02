package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** elzerma's start-point scenarios (docs/plans/shortest-path-research.md, "Start points by walking distance"). */
public class WalkGridTest
{
	@Test
	public void theGridShipsWithThePlugin()
	{
		assertTrue(WalkGrid.loaded());
	}

	@Test
	public void faladorWestBankIsAWalkFromFaladorPark()
	{
		final Integer tiles = WalkGrid.tiles(Location.FALADOR_PARK, new WorldPoint(2945, 3368, 0));
		assertNotNull(tiles);
		assertTrue("walk " + tiles, tiles < 100);
	}

	@Test
	public void castleWarsIsNotNearLletya()
	{
		// Straight-line it's about 140 tiles; there's no walk through the forest
		assertNull(WalkGrid.tiles(Location.LLETYA, new WorldPoint(2443, 3083, 0)));
	}

	@Test
	public void theGrandTreeIsAWalkFromTheStrongholdPatches()
	{
		assertNotNull(WalkGrid.tiles(Location.GNOME_STRONGHOLD, new WorldPoint(2465, 3495, 0)));
	}

	@Test
	public void theGrandExchangeIsAWalkFromVarrock()
	{
		final Integer tiles = WalkGrid.tiles(Location.VARROCK, new WorldPoint(3164, 3489, 0));
		assertNotNull(tiles);
		assertTrue("walk " + tiles, tiles < 120);
	}

	@Test
	public void upstairsCountsAsAFewTilesFurther()
	{
		final Integer ground = WalkGrid.tiles(Location.CATHERBY, new WorldPoint(2807, 3441, 0));
		final Integer upstairs = WalkGrid.tiles(Location.CATHERBY, new WorldPoint(2807, 3441, 1));
		assertNotNull(ground);
		assertTrue(upstairs > ground);
	}

	@Test
	public void everyStopOnTheGroundIsInTheGrid()
	{
		// The grid is measured from the patches in tools/walk/patches.tsv: a stop missing there can't be started on
		// foot (the bush, cactus, mushroom, belladonna and special tree stops until 2026-11-01)
		for (Patch patch : PatchPoints.all().keySet())
		{
			if (PatchPoints.of(patch).getPlane() == 0)
			{
				assertTrue(patch + " is a walk from its stop", walkable(patch));
			}
		}
	}

	@Test
	public void theUnderwaterSeaweedIsNotAWalk()
	{
		// Reached by a dive, so it's left out of the grid (tools/walk/patches.tsv)
		assertNull(WalkGrid.tiles(Location.FOSSIL_ISLAND_UNDERWATER, PatchPoints.of(Patch.FOSSIL_ISLAND_SEAWEED_NORTH)));
	}

	/** Whether the patch's stop is a walk from its 4x4 block or one around it (the patch itself can't be stood on). */
	private static boolean walkable(Patch patch)
	{
		for (int dx = -4; dx <= 4; dx += 4)
		{
			for (int dy = -4; dy <= 4; dy += 4)
			{
				if (WalkGrid.tiles(patch.getLocation(), PatchPoints.of(patch).dx(dx).dy(dy)) != null)
				{
					return true;
				}
			}
		}
		return false;
	}
}
