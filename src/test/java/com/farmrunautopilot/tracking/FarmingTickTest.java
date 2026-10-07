package com.farmrunautopilot.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class FarmingTickTest
{
	private static final long T0 = 160L * 60 * 10_000_000;

	@Test
	public void withoutOffsetTicksSitOnMinuteBoundaries()
	{
		assertEquals(T0, FarmingTick.UNKNOWN.tickTime(20, 0, T0 + 1199));
		assertEquals(T0 + 1200, FarmingTick.UNKNOWN.tickTime(20, 0, T0 + 1200));
		assertEquals(T0 + 3 * 2400, FarmingTick.UNKNOWN.tickTime(40, 3, T0 + 5));
	}

	@Test
	public void offsetShiftsTheBoundaries()
	{
		final FarmingTick tick = new FarmingTick(7, 40);
		// Boundaries for a 20 minute crop fall 7 minutes before each multiple of 20.
		assertEquals(T0 - 7 * 60, tick.tickTime(20, 0, T0));
		assertEquals(T0 + 13 * 60, tick.tickTime(20, 1, T0));
		assertEquals(T0 + 13 * 60, tick.tickTime(20, 0, T0 + 13 * 60));
	}

	@Test
	public void imprecisOffsetIsIgnoredForLongerTicks()
	{
		// Learned on a 20 minute crop: not precise enough for a 160 minute fruit tree.
		final FarmingTick tick = new FarmingTick(7, 20);
		assertEquals(T0, tick.tickTime(160, 0, T0 + 5));
	}

	@Test
	public void observedOffset()
	{
		// Seen growing 5 minutes past a 20 minute multiple.
		assertEquals(15, FarmingTick.observedOffsetMinutes(20, T0 + 5 * 60));
	}

	@Test
	public void recordRoundTrip()
	{
		final PatchRecord record = new PatchRecord(42, 1_700_000_000L);
		assertEquals(record, PatchRecord.parse(record.format()));
		assertNull(PatchRecord.parse(null));
		assertNull(PatchRecord.parse("garbage"));
		assertNull(PatchRecord.parse("1:x"));
		assertNull(PatchRecord.parse("1:0"));
	}
}
