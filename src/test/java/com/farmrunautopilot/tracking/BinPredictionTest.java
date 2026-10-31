package com.farmrunautopilot.tracking;

import com.farmrunautopilot.data.BinState;
import com.farmrunautopilot.settings.Compost;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** P3: a bin seen closed counts as rotted 70 minutes later (OSRS Wiki: 35 to 70). */
public class BinPredictionTest
{
	private static final long SEEN = 1_000_000;

	@Test
	public void aClosedBinRotsWithinSeventyMinutes()
	{
		final BinState rotting = BinState.decode(95, false);
		assertEquals(BinState.Stage.ROTTING, PatchTracker.rotted(rotting, SEEN, SEEN + PatchTracker.ROT_SECONDS - 1).getStage());
		final BinState later = PatchTracker.rotted(rotting, SEEN, SEEN + PatchTracker.ROT_SECONDS);
		assertEquals(BinState.Stage.ROTTED, later.getStage());
		assertEquals(Compost.SUPERCOMPOST, later.getCompost());
		// Filling or open stays as it was seen
		final BinState filling = BinState.decode(40, false);
		assertEquals(filling, PatchTracker.rotted(filling, SEEN, SEEN + 100 * PatchTracker.ROT_SECONDS));
	}
}
