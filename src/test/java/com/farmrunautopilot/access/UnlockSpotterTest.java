package com.farmrunautopilot.access;

import com.farmrunautopilot.data.Unlock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class UnlockSpotterTest
{
	@Test
	public void eachSpiritTreeIsSpottedOnlyInItsOwnRegion()
	{
		// GitHub #19: at the Farming Guild (4922), the guided test said Port Sarim
		assertTrue(UnlockSpotter.regions(Unlock.SPIRIT_TREE_FARMING_GUILD).contains(4922));
		assertFalse(UnlockSpotter.regions(Unlock.SPIRIT_TREE_PORT_SARIM).contains(4922));
		assertFalse(UnlockSpotter.regions(Unlock.SPIRIT_TREE_BRIMHAVEN).contains(4922));
	}

	@Test
	public void unlocksNotSpottedHaveNoRegions()
	{
		assertEquals(0, UnlockSpotter.regions(Unlock.FAIRY_RINGS).size());
	}
}
