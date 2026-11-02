package com.farmrunautopilot.run;

import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The patch's tile the walking line, arrow and tile mark go to: its nearest to the player, not its middle (elzerma,
 * 2026-11-01: "Can we have the path/arrow show at the closest tile to where we are of allotments?").
 */
public class NearestPatchTileTest
{
	/** A long allotment, 13 tiles by 2, like Catherby's. */
	private static final WorldPoint LOW = new WorldPoint(100, 200, 0);
	private static final WorldPoint HIGH = new WorldPoint(112, 201, 0);

	@Test
	public void theEndNearestThePlayer()
	{
		assertEquals(new WorldPoint(100, 200, 0), SceneTracker.nearestTile(LOW, HIGH, new WorldPoint(98, 199, 0)));
		assertEquals(new WorldPoint(112, 201, 0), SceneTracker.nearestTile(LOW, HIGH, new WorldPoint(115, 203, 0)));
	}

	@Test
	public void besideItsMiddle()
	{
		assertEquals(new WorldPoint(106, 201, 0), SceneTracker.nearestTile(LOW, HIGH, new WorldPoint(106, 204, 0)));
		// Standing in it (as a 1x1 corner of the rectangle can be), its own tile
		assertEquals(new WorldPoint(105, 200, 0), SceneTracker.nearestTile(LOW, HIGH, new WorldPoint(105, 200, 0)));
	}

	@Test
	public void atTheFarEndIsAtThePatch()
	{
		// Two tiles past its east end: well within reach of the patch, though 8 from its middle
		final WorldPoint player = new WorldPoint(114, 201, 0);
		assertTrue(SceneTracker.nearestTile(LOW, HIGH, player).distanceTo2D(player) <= Navigator.NEAR_PATCH_TILES);
		assertTrue(new WorldPoint(106, 200, 0).distanceTo2D(player) > Navigator.NEAR_PATCH_TILES);
	}
}
