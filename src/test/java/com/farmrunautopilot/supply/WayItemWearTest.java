package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.route.AtlasLeg;
import com.farmrunautopilot.settings.Outfit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * The travel atlas's teleport jewellery the plugin doesn't track is worn when its slot is free (elzerma, 2026-10-19:
 * the ring slot was empty, yet the ring of dueling wasn't asked to be worn).
 */
public class WayItemWearTest
{
	private static final AtlasLeg.ItemNeed DUELING = new AtlasLeg.ItemNeed(
		new int[]{ItemID.RING_OF_DUELING_8, ItemID.RING_OF_DUELING_7}, 1, true, true);
	private static final IntFunction<TravelItem.WearSlot> SLOTS = id ->
		id == ItemID.RING_OF_DUELING_8 || id == ItemID.RING_OF_DUELING_7 || id == ItemID.RING_OF_WEALTH_5
			? TravelItem.WearSlot.RING : null;

	@Test
	public void aRingOfDuelingIsWornWhenTheRingSlotIsFree()
	{
		final List<SupplyCalculator.Wearable> wearables = SupplyCalculator.wayWearables(ways(DUELING),
			counts(DUELING, 1), holdings(Collections.emptyMap()), SLOTS);
		assertEquals(1, wearables.size());
		assertEquals(TravelItem.WearSlot.RING, wearables.get(0).slot);
		final Map<TravelItem.WearSlot, Object> wear = SupplyCalculator.toWear(Collections.emptySet(), wearables,
			Outfit.NONE, holdings(Collections.emptyMap()));
		assertEquals(DUELING, wear.get(TravelItem.WearSlot.RING));
	}

	@Test
	public void notOverARingAlreadyWorn()
	{
		assertTrue(SupplyCalculator.wayWearables(ways(DUELING), counts(DUELING, 1),
			holdings(Map.of(ItemID.RING_OF_WEALTH_5, 1)), SLOTS).isEmpty());
	}

	@Test
	public void notWhenMoreThanOneIsCarried()
	{
		assertTrue(SupplyCalculator.wayWearables(ways(DUELING), counts(DUELING, 2),
			holdings(Collections.emptyMap()), SLOTS).isEmpty());
	}

	@Test
	public void notWhatTheWayOnlyNeeds()
	{
		final AtlasLeg.ItemNeed logs = new AtlasLeg.ItemNeed(new int[]{ItemID.LOGS}, 1, true, false);
		assertTrue(SupplyCalculator.wayWearables(ways(logs), counts(logs, 1), holdings(Collections.emptyMap()),
			id -> TravelItem.WearSlot.RING).isEmpty());
	}

	@Test
	public void aTeleportItemThePluginPlansKeepsTheSlot()
	{
		final List<SupplyCalculator.Wearable> wearables = SupplyCalculator.wayWearables(ways(DUELING),
			counts(DUELING, 1), holdings(Collections.emptyMap()), SLOTS);
		final Map<TravelItem.WearSlot, Object> wear = SupplyCalculator.toWear(
			EnumSet.of(TravelItem.RING_OF_WEALTH), wearables, Outfit.NONE, holdings(Collections.emptyMap()));
		assertEquals(TravelItem.RING_OF_WEALTH, wear.get(TravelItem.WearSlot.RING));
	}

	private static Map<Integer, AtlasLeg.ItemNeed> ways(AtlasLeg.ItemNeed need)
	{
		final Map<Integer, AtlasLeg.ItemNeed> ways = new LinkedHashMap<>();
		ways.put(need.getIds()[0], need);
		return ways;
	}

	private static Map<Integer, Integer> counts(AtlasLeg.ItemNeed need, int count)
	{
		return Map.of(need.getIds()[0], count);
	}

	private static Holdings holdings(Map<Integer, Integer> worn)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.WORN, worn);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}
}
