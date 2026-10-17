package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.travel.Rune;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class StaffPlannerTest
{
	private static final Object DRAMEN = SupplyCalculator.Wearable.FAIRY_RING_STAFF;
	private static final Object LAVA = ItemID.LAVA_BATTLESTAFF;
	private static final Object MIST = ItemID.MIST_BATTLESTAFF;

	private static Map<Rune, Integer> runes(Object... pairs)
	{
		final Map<Rune, Integer> runes = new EnumMap<>(Rune.class);
		for (int i = 0; i < pairs.length; i += 2)
		{
			runes.put((Rune) pairs[i], (Integer) pairs[i + 1]);
		}
		return runes;
	}

	private static StaffPlanner.Leg cast(Location location, Map<Rune, Integer> runes)
	{
		return new StaffPlanner.Leg(location, runes, new EnumMap<>(Rune.class), false);
	}

	private static StaffPlanner.Leg ring(Location location)
	{
		return new StaffPlanner.Leg(location, new EnumMap<>(Rune.class), new EnumMap<>(Rune.class), true);
	}

	private static Map<Rune, Integer> total(List<StaffPlanner.Leg> legs)
	{
		final Map<Rune, Integer> need = new EnumMap<>(Rune.class);
		for (StaffPlanner.Leg leg : legs)
		{
			leg.runes.forEach((rune, n) -> need.merge(rune, n, Integer::sum));
		}
		return need;
	}

	/** Lava for a cast needing earth and fire, mist for one needing air and water; law either way. */
	private static List<StaffPlanner.Leg> lavaAndMistLegs(Rune... alsoLaterOn)
	{
		final Map<Rune, Integer> later = runes(Rune.AIR, 3, Rune.WATER, 1, Rune.LAW, 1);
		for (Rune rune : alsoLaterOn)
		{
			later.put(rune, 1);
		}
		return Arrays.asList(
			cast(Location.CIVITAS_ILLA_FORTIS, runes(Rune.EARTH, 1, Rune.FIRE, 1, Rune.LAW, 2)),
			cast(Location.FALADOR_PARK, later));
	}

	@Test
	public void thePouchIsOneSlotSharedByItsRuneTypes()
	{
		// elzerma: 1/3 slot each when full, half each with two, the whole slot with one; a divine pouch holds four
		assertEquals(0, StaffPlanner.runeSlots(0, StaffPlanner.POUCH_TYPES));
		assertEquals(1, StaffPlanner.runeSlots(1, StaffPlanner.POUCH_TYPES));
		assertEquals(1, StaffPlanner.runeSlots(2, StaffPlanner.POUCH_TYPES));
		assertEquals(1, StaffPlanner.runeSlots(3, StaffPlanner.POUCH_TYPES));
		assertEquals(2, StaffPlanner.runeSlots(4, StaffPlanner.POUCH_TYPES));
		assertEquals(1, StaffPlanner.runeSlots(4, StaffPlanner.DIVINE_POUCH_TYPES));
		assertEquals(2, StaffPlanner.runeSlots(5, StaffPlanner.DIVINE_POUCH_TYPES));
		// No pouch: a slot a type
		assertEquals(3, StaffPlanner.runeSlots(3, 0));
	}

	@Test
	public void pouchTypesComeFromThePouchHeld()
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		final Map<Integer, Integer> bank = new HashMap<>();
		items.put(Holdings.Source.BANK, bank);
		final Holdings holdings = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
		assertEquals(0, StaffPlanner.pouchTypes(holdings));
		bank.put(ItemID.BH_RUNE_POUCH, 1);
		assertEquals(StaffPlanner.POUCH_TYPES, StaffPlanner.pouchTypes(holdings));
		bank.put(SupplyItems.DIVINE_RUNE_POUCHES[0], 1);
		assertEquals(StaffPlanner.DIVINE_POUCH_TYPES, StaffPlanner.pouchTypes(holdings));
	}

	@Test
	public void lavaAndMistSwapWhenThatSavesASlot()
	{
		final List<StaffPlanner.Leg> legs = lavaAndMistLegs();
		// Lava on all the way: air, water and law to carry, 3 slots. Mist carried and swapped to: mist + law, 2.
		final StaffPlanner.Plan plan = StaffPlanner.plan(legs, total(legs), Arrays.asList(LAVA, MIST), LAVA,
			Collections.emptySet(), 0);
		assertNotNull(plan);
		assertEquals(Arrays.asList(LAVA, MIST), plan.staves);
		assertEquals(LAVA, plan.wield.get(Location.CIVITAS_ILLA_FORTIS));
		assertEquals(MIST, plan.wield.get(Location.FALADOR_PARK));
		assertEquals(1, plan.swaps);
		assertEquals(2, plan.slots);
		assertEquals(runes(Rune.LAW, 3), plan.runes);
		assertEquals(EnumSet.of(Rune.AIR, Rune.WATER, Rune.EARTH, Rune.FIRE), plan.free);
	}

	@Test
	public void aRunePouchWithRoomMeansNoSwap()
	{
		// Air, water and law fit the pouch's one slot: the mist staff would take a slot to save none
		final List<StaffPlanner.Leg> legs = lavaAndMistLegs();
		assertNull(StaffPlanner.plan(legs, total(legs), Arrays.asList(LAVA, MIST), LAVA, Collections.emptySet(),
			StaffPlanner.POUCH_TYPES));
	}

	@Test
	public void aFullPouchStillSwapsWhenTheRunesSpillOver()
	{
		// Air, water, law, nature and astral: the pouch and two loose slots (3). With the mist staff: the staff, and
		// law, nature and astral filling the pouch (2).
		final List<StaffPlanner.Leg> legs = lavaAndMistLegs(Rune.NATURE, Rune.ASTRAL);
		final StaffPlanner.Plan plan = StaffPlanner.plan(legs, total(legs), Arrays.asList(LAVA, MIST), LAVA,
			Collections.emptySet(), StaffPlanner.POUCH_TYPES);
		assertNotNull(plan);
		assertEquals(2, plan.slots);
		assertEquals(EnumSet.of(Rune.LAW, Rune.NATURE, Rune.ASTRAL), plan.runes.keySet());
	}

	@Test
	public void theDivinePouchHoldsFourSoTheSameRunNeedsNoSwap()
	{
		// The same five types: a divine pouch and one loose slot (2), which the mist staff can't beat (2)
		final List<StaffPlanner.Leg> legs = lavaAndMistLegs(Rune.NATURE, Rune.ASTRAL);
		assertNull(StaffPlanner.plan(legs, total(legs), Arrays.asList(LAVA, MIST), LAVA, Collections.emptySet(),
			StaffPlanner.DIVINE_POUCH_TYPES));
	}

	@Test
	public void theDramenStaffSwapsInForFairyRingsAndBack()
	{
		final List<StaffPlanner.Leg> legs = Arrays.asList(
			ring(Location.ARDOUGNE_FARM),
			cast(Location.CIVITAS_ILLA_FORTIS, runes(Rune.EARTH, 1, Rune.FIRE, 1, Rune.LAW, 2)),
			ring(Location.PORT_PHASMATYS),
			cast(Location.TROLL_STRONGHOLD, runes(Rune.FIRE, 2, Rune.LAW, 2)));
		// The dramen staff on all the way: earth, fire and law (3). Swapping: the lava staff and law (2).
		final StaffPlanner.Plan plan = StaffPlanner.plan(legs, total(legs), Arrays.asList(DRAMEN, LAVA), DRAMEN,
			Collections.emptySet(), 0);
		assertNotNull(plan);
		assertEquals(DRAMEN, plan.start());
		assertEquals(DRAMEN, plan.wield.get(Location.ARDOUGNE_FARM));
		assertEquals(LAVA, plan.wield.get(Location.CIVITAS_ILLA_FORTIS));
		assertEquals(DRAMEN, plan.wield.get(Location.PORT_PHASMATYS));
		assertEquals(LAVA, plan.wield.get(Location.TROLL_STRONGHOLD));
		assertEquals(3, plan.swaps);
		assertEquals(2, plan.slots);
		assertEquals(runes(Rune.LAW, 4), plan.runes);
	}

	@Test
	public void cureRunesAreCarriedOnceWhenSomeHerbStopLacksTheStaff()
	{
		// Cure plant's earth at a stop reached by fairy ring: with the dramen staff on there, one cast's earth is carried
		final Map<Rune, Integer> cure = runes(Rune.ASTRAL, 1, Rune.EARTH, 8);
		final List<StaffPlanner.Leg> legs = Arrays.asList(
			new StaffPlanner.Leg(Location.ARDOUGNE_FARM, new EnumMap<>(Rune.class), cure, true),
			new StaffPlanner.Leg(Location.CIVITAS_ILLA_FORTIS, runes(Rune.EARTH, 1, Rune.FIRE, 1, Rune.LAW, 2), cure,
				false),
			new StaffPlanner.Leg(Location.FALADOR_FARM, runes(Rune.EARTH, 1, Rune.FIRE, 1, Rune.LAW, 2), cure, false));
		final Map<Rune, Integer> need = total(legs);
		need.merge(Rune.ASTRAL, 1, Integer::sum);
		need.merge(Rune.EARTH, 8, Integer::sum);
		final StaffPlanner.Plan plan = StaffPlanner.evaluate(legs, need, Arrays.asList(DRAMEN, LAVA),
			Collections.emptySet(), 0);
		assertEquals(Integer.valueOf(8), plan.runes.get(Rune.EARTH));
		assertNull(plan.runes.get(Rune.FIRE));
		assertEquals(Integer.valueOf(1), plan.runes.get(Rune.ASTRAL));
	}

	@Test
	public void aTomeCoversItsRunesWhateverIsWielded()
	{
		// The tome of fire covers fire even with the dramen staff wielded for the ring
		final List<StaffPlanner.Leg> legs = Arrays.asList(
			cast(Location.TROLL_STRONGHOLD, runes(Rune.FIRE, 2, Rune.LAW, 2)),
			ring(Location.ARDOUGNE_FARM));
		final StaffPlanner.Plan plan = StaffPlanner.evaluate(legs, total(legs), Collections.singletonList(DRAMEN),
			EnumSet.of(Rune.FIRE), 0);
		assertEquals(runes(Rune.LAW, 2), plan.runes);
		assertEquals(1, plan.slots);
	}
}
