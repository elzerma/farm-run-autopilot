package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.regex.Matcher;
import org.junit.Test;

/** Message wording from the wiki's item pages and transcripts. */
public class ItemChargeTrackerTest
{
	private static int read(java.util.regex.Pattern pattern, String message)
	{
		final Matcher m = pattern.matcher(message);
		assertTrue(message, m.matches());
		return ItemChargeTracker.count(m.group(1));
	}

	@Test
	public void readsCheckAndChargeMessages()
	{
		assertEquals(4, read(ItemChargeTracker.WHISTLE_CHECK, "Your quetzal whistle has 4 charges remaining."));
		assertEquals(0, read(ItemChargeTracker.TALISMAN_CHECK, "The talisman has no charges."));
		assertEquals(1, read(ItemChargeTracker.TALISMAN_CHECK, "The talisman has one charge."));
		assertEquals(1000, read(ItemChargeTracker.TALISMAN_CHECK, "The talisman has 1,000 charges."));
		assertEquals(250, read(ItemChargeTracker.TALISMAN_CHARGED, "Your talisman now has 250 charges."));
		assertEquals(1, read(ItemChargeTracker.PENDANT_CHECK, "The pendant has 1 charge."));
		assertEquals(37, read(ItemChargeTracker.PENDANT_CHARGED,
			"Your add 12 frozen tears to your pendant. It now has 37 charges."));
		assertFalse(ItemChargeTracker.PENDANT_CHECK.matcher("The talisman has 3 charges.").matches());
	}
}
