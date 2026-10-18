package com.farmrunautopilot.data.travel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Daily teleport limits, and the Ardougne cloak's count split over two varbits (elzerma's run, 2026-10-18). */
public class DailyLimitsTest
{
	@Test
	public void aSplitCountIsPutBackTogether()
	{
		// One low bit: 3 uses are low 1, high 1, which the low bit alone read as 1
		assertEquals(3, DailyLimits.combine(1, 1, 1));
		assertEquals(2, DailyLimits.combine(0, 1, 1));
		assertEquals(1, DailyLimits.combine(1, 0, 1));
		assertEquals(5, DailyLimits.combine(1, 1, 2));
	}

	@Test
	public void usesPerDayFollowTheBestCloakHeld()
	{
		assertEquals(Integer.valueOf(3), DailyLimits.perDay(TravelMethod.ARDOUGNE_CLOAK_FARM,
			id -> id == ItemID.ARDY_CAPE_MEDIUM));
		assertEquals(Integer.valueOf(5), DailyLimits.perDay(TravelMethod.ARDOUGNE_CLOAK_FARM,
			id -> id == ItemID.ARDY_CAPE_MEDIUM || id == ItemID.ARDY_CAPE_HARD));
		assertNull(DailyLimits.perDay(TravelMethod.ARDOUGNE_CLOAK_FARM, id -> id == ItemID.ARDY_CAPE_ELITE));
	}
}
