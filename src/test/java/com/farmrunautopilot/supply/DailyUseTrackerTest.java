package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.regex.Matcher;
import org.junit.Test;

/** The daily teleports' messages, as they came in elzerma's runs of 2026-10-18. */
public class DailyUseTrackerTest
{
	@Test
	public void readsTodaysUsesFromTheGamesMessage()
	{
		Matcher m = DailyUseTracker.USED.matcher("You have used 3 of your 3 Ardougne Farm teleports for today.");
		assertTrue(m.matches());
		assertEquals("3", m.group(1));
		assertEquals("Ardougne Farm", m.group(3));
		m = DailyUseTracker.USED.matcher("You have used 1 of your 3 Cabbage teleports for today.");
		assertTrue(m.matches());
		assertEquals("1", m.group(1));
		assertEquals("Cabbage", m.group(3));
	}

	@Test
	public void aRefusedCloakTeleportCountsAsAllUsed()
	{
		assertEquals(TravelMethod.ARDOUGNE_CLOAK_FARM, DailyUseTracker.refusedBy("You have already used all of your "
			+ "available teleports for today. Try again tomorrow when the cape has recharged."));
		assertNull(DailyUseTracker.refusedBy("You have used 3 of your 3 Ardougne Farm teleports for today."));
		assertFalse(DailyUseTracker.USED.matcher("You have already used all of your available teleports for today.")
			.matches());
	}
}
