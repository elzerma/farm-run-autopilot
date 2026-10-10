package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.regex.Matcher;
import org.junit.Test;

/** Message wording from the wiki's bottomless compost bucket transcript. */
public class BottomlessBucketTrackerTest
{
	@Test
	public void readsCheckAndFillMessages()
	{
		final Matcher check = BottomlessBucketTracker.CHECK.matcher(
			"Your bottomless compost bucket is currently holding 1,234 uses of ultracompost.");
		assertTrue(check.matches());
		assertEquals(1234, BottomlessBucketTracker.number(check.group(1)));
		assertEquals("ultracompost", check.group(2));

		assertTrue(BottomlessBucketTracker.EMPTY.matcher("Your compost bucket is currently empty.").matches());

		final Matcher one = BottomlessBucketTracker.FILL_TYPE.matcher(
			"You fill your bottomless compost bucket with a single bucket of supercompost.");
		assertTrue(one.matches());
		assertEquals("supercompost", one.group(1));
		assertTrue(BottomlessBucketTracker.FILL_TYPE.matcher(
			"You fill your bottomless compost bucket with 12 buckets of compost.").matches());

		final Matcher total = BottomlessBucketTracker.FILL_TOTAL.matcher(
			"Your bottomless compost bucket now contains a total of 26 uses.");
		assertTrue(total.matches());
		assertEquals(26, BottomlessBucketTracker.number(total.group(1)));

		assertTrue(BottomlessBucketTracker.TREATED.matcher("You treat the herb patch with ultracompost.").matches());
	}
}
