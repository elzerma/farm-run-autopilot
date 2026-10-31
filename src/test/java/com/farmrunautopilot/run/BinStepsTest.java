package com.farmrunautopilot.run;

import com.farmrunautopilot.data.BinState;
import com.farmrunautopilot.data.CompostBin;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * P3, the compost bin at a stop (elzerma, 2026-10-30): the empty buckets from the tool leprechaun first, then open, the ash
 * and take out; and a bin being filled kept at until it's full.
 */
public class BinStepsTest
{
	private static final BinState ROTTED = BinState.decode(126, false);
	private static final BinState OPEN_FULL = BinState.decode(62, false);
	private static final BinState ULTRA = BinState.decode(190, false);
	private static final CompostBin BIN = CompostBin.CIVITAS_ILLA_FORTIS;

	private static PatchGuide.BinNext next(BinState state, int buckets, int stored, int freeSlots, int ash)
	{
		return PatchGuide.binNext(state, BIN, buckets, false, stored, freeSlots, ash);
	}

	@Test
	public void bucketsFromTheLeprechaunBeforeOpening()
	{
		// "Highlight should go to leprechaun to remove buckets and then open"
		assertEquals(PatchGuide.BinNext.TAKE_BUCKETS, next(ROTTED, 0, 20, 18, 25));
		assertEquals(PatchGuide.BinNext.OPEN, next(ROTTED, 15, 20, 3, 25));
		// "and either a) withdraw or b) ... add it to the bin before removing it into buckets"
		assertEquals(PatchGuide.BinNext.ADD_ASH, next(OPEN_FULL, 15, 5, 3, 25));
		assertEquals(PatchGuide.BinNext.TAKE_OUT, next(OPEN_FULL, 15, 5, 3, 0));
		assertEquals(PatchGuide.BinNext.TAKE_OUT, next(ULTRA, 15, 5, 3, 25));
	}

	@Test
	public void ashWhileItsNoMoreThanForTheBucketsLeft()
	{
		// The bin takes all 25 however many are left (elzerma's run of 2026-10-31: 25 for the 13 left after taking 2
		// out), against 2 a bucket: worth it with 13 left, not 12 or 9
		assertEquals(PatchGuide.BinNext.ADD_ASH, next(BinState.decode(60, false), 13, 0, 3, 25));
		assertEquals(PatchGuide.BinNext.TAKE_OUT, next(BinState.decode(59, false), 12, 0, 3, 25));
		assertEquals(PatchGuide.BinNext.TAKE_OUT, next(BinState.decode(56, false), 9, 0, 3, 25));
		assertEquals(PatchGuide.BinNext.TAKE_OUT, next(OPEN_FULL, 15, 0, 3, 24));
		// The Farming Guild's big bin takes 50: worth it with 25 left
		final BinState bigOpen = BinState.decode(114, true);
		assertEquals(30, PatchGuide.compostIn(bigOpen, CompostBin.FARMING_GUILD));
		assertEquals(PatchGuide.BinNext.TAKE_OUT,
			PatchGuide.binNext(bigOpen, CompostBin.FARMING_GUILD, 30, false, 0, 3, 25));
		assertEquals(PatchGuide.BinNext.ADD_ASH,
			PatchGuide.binNext(bigOpen, CompostBin.FARMING_GUILD, 30, false, 0, 3, 50));
		assertEquals(PatchGuide.BinNext.ADD_ASH,
			PatchGuide.binNext(BinState.decode(109, true), CompostBin.FARMING_GUILD, 25, false, 0, 3, 50));
		assertEquals(PatchGuide.BinNext.TAKE_OUT,
			PatchGuide.binNext(BinState.decode(108, true), CompostBin.FARMING_GUILD, 24, false, 0, 3, 50));
	}

	@Test
	public void asManyBucketsAsTheLeprechaunHasAndThereIsRoomFor()
	{
		// Some carried, room for more: the rest from him
		assertEquals(PatchGuide.BinNext.TAKE_BUCKETS, next(ROTTED, 5, 20, 10, 0));
		// No room for more: on with the ones carried, and back to him once they're full
		assertEquals(PatchGuide.BinNext.OPEN, next(ROTTED, 5, 20, 0, 0));
		// He has none: on with what's carried, and left once those are full
		assertEquals(PatchGuide.BinNext.OPEN, next(ROTTED, 5, 0, 10, 0));
		assertEquals(PatchGuide.BinNext.NONE, next(BinState.decode(56, false), 0, 0, 10, 0));
		// The bottomless bucket needs none
		assertEquals(PatchGuide.BinNext.OPEN, PatchGuide.binNext(ROTTED, BIN, 0, true, 0, 10, 0));
	}

	@Test
	public void cropsGoInAnEmptyOrFillingBin()
	{
		assertEquals(0, PatchGuide.fillCount(BinState.decode(0, false)));
		assertEquals(4, PatchGuide.fillCount(BinState.decode(36, false)));
		assertEquals(15, PatchGuide.fillCount(BinState.decode(47, false)));
		// Rotting, holding compost, the player's own tomatoes, or not known: none
		assertEquals(-1, PatchGuide.fillCount(BinState.decode(95, false)));
		assertEquals(-1, PatchGuide.fillCount(OPEN_FULL));
		assertEquals(-1, PatchGuide.fillCount(BinState.decode(130, false)));
		assertEquals(-1, PatchGuide.fillCount(null));
	}

	@Test
	public void ashGoesInABinOfSupercompost()
	{
		assertTrue(ROTTED.takesAsh(25));
		assertTrue(OPEN_FULL.takesAsh(25));
		assertTrue(BinState.decode(60, false).takesAsh(25));
		assertFalse(BinState.decode(56, false).takesAsh(25));
		assertFalse(ULTRA.takesAsh(25));
		// Regular compost doesn't turn into ultracompost
		assertFalse(BinState.decode(94, false).takesAsh(25));
	}

	@Test
	public void theBinStepIsScreenshotOnceNotAtEachCount()
	{
		assertEquals("Put your watermelon in the compost bin",
			RunSession.uncounted("Put your watermelon in the compost bin (4/15)"));
		assertEquals("Close the compost bin", RunSession.uncounted("Close the compost bin"));
		assertEquals("", RunSession.uncounted(null));
	}
}
