package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.supply.Holdings;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class BankTeleportTest
{
	private static Holdings bank(int... itemIds)
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		for (int id : itemIds)
		{
			bank.put(id, 1);
		}
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	private static AccessSnapshot crafting(int level)
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 1);
		}
		levels.put(Skill.CRAFTING, level);
		return new AccessSnapshot(true, Collections.emptyMap(), levels, Collections.emptySet(),
			Collections.emptySet(), null, Spellbook.STANDARD, Collections.emptySet());
	}

	@Test
	public void quickestHeldTeleportWins()
	{
		assertEquals(BankTeleport.CRAFTING_CAPE, BankTeleport.best(crafting(99),
			bank(ItemID.SKILLCAPE_CRAFTING, ItemID.RING_OF_DUELING_8, ItemID.RING_OF_WEALTH_5)));
		// A crafting cape without 99 Crafting can't teleport
		assertEquals(BankTeleport.RING_OF_DUELING, BankTeleport.best(crafting(50),
			bank(ItemID.SKILLCAPE_CRAFTING, ItemID.RING_OF_DUELING_8, ItemID.RING_OF_WEALTH_5)));
		assertEquals(BankTeleport.RING_OF_WEALTH, BankTeleport.best(crafting(50), bank(ItemID.RING_OF_WEALTH_5)));
		assertNull(BankTeleport.best(crafting(50), bank()));
	}
}
