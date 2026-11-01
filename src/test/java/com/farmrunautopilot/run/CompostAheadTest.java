package com.farmrunautopilot.run;

import com.farmrunautopilot.settings.Compost;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Compost for later stops on a visit to the tool leprechaun made anyway, while there's room (elzerma, 2026-10-31: "Yes,
 * when there's room").
 */
public class CompostAheadTest
{
	private static Map<Compost, Integer> ultra(int count)
	{
		final Map<Compost, Integer> map = new EnumMap<>(Compost.class);
		if (count > 0)
		{
			map.put(Compost.ULTRACOMPOST, count);
		}
		return map;
	}

	private static final Map<Compost, Integer> NONE = ultra(0);
	private static final Map<Compost, Integer> PLENTY = ultra(400);

	@Test
	public void thisStopsCompostWhateverTheRoom()
	{
		final PatchGuide.CompostTrade trade = PatchGuide.compostTrade(NONE, NONE, PLENTY, ultra(2),
			Collections.emptyList(), Collections.emptyList(), 0, 8);
		assertEquals(ultra(2), trade.take);
		assertEquals(NONE, trade.store);
	}

	@Test
	public void laterStopsWhileTheirHarvestsStillFit()
	{
		// 20 free once the visit's done, 8 still to pick here: the next stop's 2 fit with its 8, the one after's 1 doesn't
		final List<Map<Compost, Integer>> later = Arrays.asList(ultra(2), ultra(1));
		final PatchGuide.CompostTrade trade = PatchGuide.compostTrade(NONE, NONE, PLENTY, ultra(2), later,
			Arrays.asList(8, 8), 20, 8);
		assertEquals(ultra(4), trade.take);
		// With room to spare, all of it
		assertEquals(ultra(5), PatchGuide.compostTrade(NONE, NONE, PLENTY, ultra(2), later, Arrays.asList(8, 8), 40, 8)
			.take);
	}

	@Test
	public void notPastAStopWhoseCompostBinSuppliesIt()
	{
		final List<Map<Compost, Integer>> later = Arrays.asList(ultra(2), null, ultra(2));
		assertEquals(ultra(4), PatchGuide.compostTrade(NONE, NONE, PLENTY, ultra(2), later, Arrays.asList(0, 0, 0), 40, 0)
			.take);
	}

	@Test
	public void laterStopsOnlyWhenHeHasEnough()
	{
		// This stop's when he has it, the stops ahead only when he has all of theirs
		assertEquals(ultra(2), PatchGuide.compostTrade(NONE, NONE, ultra(3), ultra(2), Arrays.asList(ultra(2)),
			Arrays.asList(0), 40, 0).take);
		assertEquals(NONE, PatchGuide.compostTrade(NONE, NONE, ultra(1), ultra(2), Collections.emptyList(),
			Collections.emptyList(), 40, 0).take);
	}

	@Test
	public void theBinsSpareIsKeptForLaterStopsAndTheRestStored()
	{
		// 15 out of the bin, 2 for here: 2 more kept for the next stop while its harvest fits, the other 11 stored
		final PatchGuide.CompostTrade trade = PatchGuide.compostTrade(ultra(15), ultra(15), PLENTY, ultra(2),
			Arrays.asList(ultra(2), ultra(2)), Arrays.asList(8, 8), 5, 0);
		assertEquals(NONE, trade.take);
		assertEquals(ultra(11), trade.store);
	}

	@Test
	public void whatWasBroughtIsNeverStored()
	{
		// 10 brought from the bank for the run: kept, though only 2 are needed here and the stops ahead aren't in reach
		final PatchGuide.CompostTrade trade = PatchGuide.compostTrade(ultra(10), NONE, PLENTY, ultra(2),
			Collections.emptyList(), Collections.emptyList(), 0, 0);
		assertEquals(NONE, trade.take);
		assertEquals(NONE, trade.store);
	}
}
