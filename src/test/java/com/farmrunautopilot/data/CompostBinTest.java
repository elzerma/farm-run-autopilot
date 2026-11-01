package com.farmrunautopilot.data;

import com.farmrunautopilot.settings.Compost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** P3: the compost bins and their states, as RuneLite's Time Tracking reads them. */
public class CompostBinTest
{
	private static void is(BinState state, BinState.Stage stage, Compost compost, int count)
	{
		assertEquals(stage, state.getStage());
		assertEquals(compost, state.getCompost());
		assertEquals(count, state.getCount());
	}

	@Test
	public void aRegularBinFillsRotsAndEmpties()
	{
		is(BinState.decode(0, false), BinState.Stage.EMPTY, Compost.NONE, 0);
		// Watermelons going in: supercompost, counted to 15
		is(BinState.decode(33, false), BinState.Stage.FILLING, Compost.SUPERCOMPOST, 1);
		is(BinState.decode(47, false), BinState.Stage.FILLING, Compost.SUPERCOMPOST, 15);
		is(BinState.decode(95, false), BinState.Stage.ROTTING, Compost.SUPERCOMPOST, 0);
		is(BinState.decode(126, false), BinState.Stage.ROTTED, Compost.SUPERCOMPOST, 0);
		// Opened: the buckets left to take
		is(BinState.decode(62, false), BinState.Stage.READY, Compost.SUPERCOMPOST, 15);
		is(BinState.decode(48, false), BinState.Stage.READY, Compost.SUPERCOMPOST, 1);
		// With the volcanic ash added
		is(BinState.decode(190, false), BinState.Stage.READY, Compost.ULTRACOMPOST, 15);
		is(BinState.decode(176, false), BinState.Stage.READY, Compost.ULTRACOMPOST, 1);
		// Regular compost
		is(BinState.decode(15, false), BinState.Stage.FILLING, Compost.COMPOST, 15);
		is(BinState.decode(31, false), BinState.Stage.ROTTING, Compost.COMPOST, 0);
		is(BinState.decode(94, false), BinState.Stage.ROTTED, Compost.COMPOST, 0);
		is(BinState.decode(30, false), BinState.Stage.READY, Compost.COMPOST, 15);
		// Only the big bin goes past 15 ultracompost
		assertNull(BinState.decode(200, false));
	}

	@Test
	public void theBigBinHoldsThirty()
	{
		is(BinState.decode(175, true), BinState.Stage.FILLING, Compost.SUPERCOMPOST, 30);
		is(BinState.decode(161, true), BinState.Stage.FILLING, Compost.SUPERCOMPOST, 16);
		is(BinState.decode(114, true), BinState.Stage.READY, Compost.SUPERCOMPOST, 30);
		is(BinState.decode(205, true), BinState.Stage.READY, Compost.ULTRACOMPOST, 30);
		is(BinState.decode(77, true), BinState.Stage.FILLING, Compost.COMPOST, 30);
		is(BinState.decode(92, true), BinState.Stage.READY, Compost.COMPOST, 30);
		is(BinState.decode(97, true), BinState.Stage.ROTTING, Compost.SUPERCOMPOST, 0);
		assertEquals(30, CompostBin.FARMING_GUILD.capacity());
		assertEquals(50, CompostBin.FARMING_GUILD.ashForUltracompost());
		assertEquals(15, CompostBin.FALADOR.capacity());
		assertEquals(25, CompostBin.FALADOR.ashForUltracompost());
	}

	@Test
	public void tomatoesAreLeftToThePlayer()
	{
		is(BinState.decode(130, false), BinState.Stage.FILLING, Compost.NONE, 2);
		assertFalse(BinState.decode(150, false).hasCompost());
		assertTrue(BinState.decode(126, false).hasCompost());
		assertTrue(BinState.decode(48, false).hasCompost());
		assertFalse(BinState.decode(40, false).hasCompost());
	}

	@Test
	public void everyBinIsAtAFarmingAreaWithPatches()
	{
		for (CompostBin bin : CompostBin.values())
		{
			assertEquals(bin, CompostBin.at(bin.getLocation()));
			boolean patchThere = false;
			for (Patch patch : Patch.values())
			{
				patchThere |= patch.getLocation() == bin.getLocation() && patch.getRegionId() == bin.getRegionId();
			}
			assertTrue(bin.name(), patchThere);
		}
		assertNull(CompostBin.at(Location.TROLL_STRONGHOLD));
	}
}
