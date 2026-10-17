package com.farmrunautopilot.supply;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Issue #8: what can only come from the seed vault, and how much to take from it. */
public class SupplyPlanTest
{
	private static SupplyLine line(SupplyLine.Group group, int need, int carried, int bank, int vault)
	{
		final Map<Holdings.Source, Integer> where = new EnumMap<>(Holdings.Source.class);
		if (carried > 0)
		{
			where.put(Holdings.Source.INVENTORY, carried);
		}
		if (bank > 0)
		{
			where.put(Holdings.Source.BANK, bank);
		}
		if (vault > 0)
		{
			where.put(Holdings.Source.SEED_VAULT, vault);
		}
		return new SupplyLine(group, "Ranarr seed", need, carried + bank + vault, carried, where, null, 1, false, null,
			new int[]{1});
	}

	private static SupplyPlan plan(SupplyLine... lines)
	{
		return new SupplyPlan(Arrays.asList(lines), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), 0, "", 0, Collections.emptyMap());
	}

	private static Integer fromVault(SupplyLine line)
	{
		return plan(line).onlyInVault().get(line);
	}

	@Test
	public void seedsOnlyInTheVaultAreTakenFromIt()
	{
		assertEquals(Integer.valueOf(6), fromVault(line(SupplyLine.Group.SEEDS, 6, 0, 0, 10)));
		// It holds fewer than are needed: all of them
		assertEquals(Integer.valueOf(4), fromVault(line(SupplyLine.Group.SEEDS, 6, 0, 0, 4)));
	}

	@Test
	public void theBankCanCoverThemSoTheVaultIsntNeeded()
	{
		assertEquals(null, fromVault(line(SupplyLine.Group.SEEDS, 6, 0, 6, 10)));
		assertEquals(null, fromVault(line(SupplyLine.Group.SEEDS, 6, 2, 4, 10)));
	}

	@Test
	public void whenTheBankCanOnlyCoverSomeTheVaultGivesAllOfThemInOneTrip()
	{
		assertEquals(Integer.valueOf(6), fromVault(line(SupplyLine.Group.SEEDS, 6, 0, 2, 10)));
		// Some carried already: only what's still to get
		assertEquals(Integer.valueOf(4), fromVault(line(SupplyLine.Group.SEEDS, 6, 2, 1, 10)));
	}

	@Test
	public void carriedOrOptionalLinesArentListed()
	{
		assertEquals(null, fromVault(line(SupplyLine.Group.SEEDS, 6, 6, 0, 10)));
		assertEquals(null, fromVault(line(SupplyLine.Group.OPTIONAL, 6, 0, 0, 10)));
		assertTrue(plan(line(SupplyLine.Group.SEEDS, 6, 0, 0, 0)).onlyInVault().isEmpty());
	}
}
