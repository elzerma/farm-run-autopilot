package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The check screenshots wait for the vines to be in flower, as they share one cycle (elzerma's clean step, 2026-10-30). */
public class VineFrameTest
{
	@Test
	public void screenshotsWaitForTheVinesInFlower()
	{
		// Grown at 900 ms into the 2600 ms cycle, in flower from 1300 to 1900: the middle of that is 1600
		assertEquals(1600, VineFrame.millisUntilInFlower(0));
		assertEquals(0, VineFrame.millisUntilInFlower(1600));
		assertEquals(2500, VineFrame.millisUntilInFlower(1700));
		for (long now = 1_700_000_000_000L; now < 1_700_000_005_200L; now += 37)
		{
			final long wait = VineFrame.millisUntilInFlower(now);
			assertTrue(wait >= 0 && wait < 2600);
			assertEquals(1600, (now + wait) % 2600);
		}
	}
}
