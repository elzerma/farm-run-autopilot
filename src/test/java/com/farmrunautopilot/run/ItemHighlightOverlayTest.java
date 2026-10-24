package com.farmrunautopilot.run;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ItemHighlightOverlayTest
{
	private static final long NOW = 1_000_000;

	@Test
	public void aNewVineGoesOnTheFirstSlotHoldingTheItem()
	{
		assertTrue(ItemHighlightOverlay.goesOn(4, -1, 0, NOW));
	}

	@Test
	public void theVineStaysOnItsSlot()
	{
		assertTrue(ItemHighlightOverlay.goesOn(5, 5, NOW - 20, NOW));
		// Another bucket earlier in the inventory doesn't take it while its own still holds the item
		assertFalse(ItemHighlightOverlay.goesOn(2, 5, NOW - 20, NOW));
	}

	@Test
	public void itWaitsWhenItsItemIsUsedUpBeforeMovingOn()
	{
		// Its bucket of compost was just used: the step usually moves on within a tick
		assertFalse(ItemHighlightOverlay.goesOn(6, 5, NOW - 600, NOW));
		// It didn't, so the vine moves to the next bucket
		assertTrue(ItemHighlightOverlay.goesOn(6, 5, NOW - ItemHighlightOverlay.MOVE_AFTER_MILLIS, NOW));
	}
}
