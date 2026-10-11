package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.settings.Outfit;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Sprint 3: what one part of a run uses up, worn teleports, and counting a frozen list again. */
public class RunPartsTest
{
	private static Holdings holdings(Map<Holdings.Source, Map<Integer, Integer>> items)
	{
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	private static Map<Integer, Integer> of(int id, int n)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		m.put(id, n);
		return m;
	}

	private static SupplyLine line(String name, int need, boolean consumed, int... ids)
	{
		return new SupplyLine(SupplyLine.Group.SEEDS, name, need, 0, 0, Collections.emptyMap(), null, 1, false, null,
			ids, consumed);
	}

	@Test
	public void useTakesFromCarriedFirstThenTheBank()
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.INVENTORY, of(ItemID.RANARR_SEED, 3));
		items.put(Holdings.Source.BANK, of(ItemID.RANARR_SEED, 10));
		final Holdings left = holdings(items).use(new int[]{ItemID.RANARR_SEED}, 5);
		assertEquals(0, (int) left.in(Holdings.Source.INVENTORY).get(ItemID.RANARR_SEED));
		assertEquals(8, (int) left.in(Holdings.Source.BANK).get(ItemID.RANARR_SEED));
	}

	@Test
	public void aPartUsesUpSeedsButNotTools()
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		final Map<Integer, Integer> bank = of(ItemID.RANARR_SEED, 10);
		bank.put(ItemID.SPADE, 1);
		items.put(Holdings.Source.BANK, bank);
		final SupplyPlan part = new SupplyPlan(Arrays.asList(line("Ranarr seed", 4, true, ItemID.RANARR_SEED),
			line("Spade", 1, false, ItemID.SPADE)), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), 0, "", 2, Collections.emptyMap());
		final Holdings left = part.leftAfter(holdings(items));
		// The second part of the run can only count on the 6 seeds the first part didn't plant
		assertEquals(6, left.count(ItemID.RANARR_SEED));
		assertEquals(1, left.count(ItemID.SPADE));
	}

	@Test
	public void aFrozenListIsCountedAgainstWhatsHeldNow()
	{
		final SupplyLine planned = line("Ranarr seed", 4, true, ItemID.RANARR_SEED);
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.INVENTORY, of(ItemID.RANARR_SEED, 4));
		final SupplyLine now = planned.counted(holdings(items));
		assertEquals(4, now.getCarried());
		assertEquals(4, now.getNeed());
		assertTrue(now.isConsumed());
	}

	@Test
	public void oneTeleportPerEquipmentSlotIsWorn()
	{
		final Set<TravelItem> used = EnumSet.of(TravelItem.RING_OF_WEALTH, TravelItem.SLAYER_RING,
			TravelItem.ARDOUGNE_CLOAK, TravelItem.ECTOPHIAL);
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		// Already wearing the slayer ring: that's the ring to keep on
		items.put(Holdings.Source.WORN, of(ItemID.SLAYER_RING_8, 1));
		final Set<TravelItem> worn = SupplyCalculator.toWear(used, Outfit.NONE, holdings(items));
		assertEquals(EnumSet.of(TravelItem.SLAYER_RING, TravelItem.ARDOUGNE_CLOAK), worn);
		// Graceful takes the cape slot, so the cloak goes in the inventory
		assertFalse(SupplyCalculator.toWear(used, Outfit.GRACEFUL, holdings(items)).contains(TravelItem.ARDOUGNE_CLOAK));
	}
}
