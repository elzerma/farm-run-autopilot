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

	@Test
	public void aClickedSlotLosesItsOutlineAtOnce()
	{
		// elzerma, 2026-10-30: "as soon as the click registers, unhighlight vs waiting until it switches to a clean herb"
		assertTrue(ItemHighlightOverlay.takesTheItem("Clean"));
		assertTrue(ItemHighlightOverlay.takesTheItem("Wear"));
		assertTrue(ItemHighlightOverlay.takesTheItem("Wield"));
		// Picking an item up to use on something doesn't take it
		assertFalse(ItemHighlightOverlay.takesTheItem("Use"));
		assertFalse(ItemHighlightOverlay.takesTheItem("Examine"));
		final int grimyIrit = net.runelite.api.gameval.ItemID.UNIDENTIFIED_IRIT;
		assertTrue(ItemHighlightOverlay.hidden(grimyIrit, NOW - 100, grimyIrit, NOW));
		// Cleaned: the clean herb in the slot isn't outlined anyway; still grimy four ticks on, as after a misclick, it is again
		assertFalse(ItemHighlightOverlay.hidden(grimyIrit, NOW - ItemHighlightOverlay.CLICKED_MILLIS, grimyIrit, NOW));
		assertFalse(ItemHighlightOverlay.hidden(grimyIrit, NOW - 100, net.runelite.api.gameval.ItemID.UNIDENTIFIED_KWUARM,
			NOW));
	}

	@Test
	public void herbsToCleanAreOutlinedInEverySlot()
	{
		final java.util.Set<Integer> herbs = java.util.Collections.singleton(net.runelite.api.gameval.ItemID.UNIDENTIFIED_IRIT);
		final Highlights clean = new Highlights(null, false, null, herbs, null, java.util.Collections.emptySet(),
			java.util.Collections.emptyList(), null, java.util.Collections.emptySet(), herbs);
		assertTrue(clean.getEverySlotIds().containsAll(herbs));
		assertTrue(clean.withWear(java.util.Collections.emptySet()).getEverySlotIds().containsAll(herbs));
		assertTrue(Highlights.NONE.getEverySlotIds().isEmpty());
	}
}
