package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.data.Location;
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
}
