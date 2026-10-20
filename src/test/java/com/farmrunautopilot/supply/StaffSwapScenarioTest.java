package com.farmrunautopilot.supply;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Swapping staves on the way (issue #7), through the whole supply calculation: Falador Teleport (air, water, law), the
 * Civitas Teleport (earth, fire, law) and a fairy ring to Ardougne, every spell cast from runes.
 */
public class StaffSwapScenarioTest
{
	private static AccessSnapshot access(boolean eliteLumbridge)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 90);
		}
		final Set<String> diaries = new HashSet<>();
		if (eliteLumbridge)
		{
			diaries.add(AccessSnapshot.diaryKey(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.ELITE));
		}
		return new AccessSnapshot(true, quests, levels, diaries, EnumSet.of(Unlock.FAIRY_RINGS), null,
			Spellbook.STANDARD, Collections.emptySet());
	}

	private static Holdings holdings(int... bankItems)
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		for (int id : bankItems)
		{
			bank.put(id, 1);
		}
		for (Rune rune : Rune.values())
		{
			bank.put(rune.getItemId(), 1000);
		}
		bank.put(ItemID.SPADE, 1);
		bank.put(ItemID.RAKE, 1);
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	private static SupplyPlan plan(AccessSnapshot access, Holdings holdings)
	{
		final RunConfig config = new RunConfig();
		config.setEnabledTypes(EnumSet.of(PatchType.HERB, PatchType.TREE));
		config.sanitise();
		config.setUseRunesNotTabs(true);
		config.setUseCurePlant(false);
		final Route route = new Route(Arrays.asList(
			new RouteStop(Location.FALADOR_PARK, TravelMethod.FALADOR_TELEPORT_TO_PARK, Departure.DIRECT, 10, false),
			new RouteStop(Location.CIVITAS_ILLA_FORTIS, TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT, Departure.DIRECT, 10,
				false),
			new RouteStop(Location.ARDOUGNE_FARM, TravelMethod.FAIRY_RING_BLR, Departure.DIRECT, 10, false)),
			RouteMode.values()[0], 30, 0);
		final RunSelection selection = new RunSelection(Arrays.asList(Patch.FALADOR_TREE, Patch.CIVITAS_HERB,
			Patch.ARDOUGNE_HERB), EnumSet.of(PatchType.HERB, PatchType.TREE), Collections.emptyMap(),
			Collections.emptyList(), Collections.emptyMap());
		return SupplyCalculator.calculate(config, access, holdings, selection, route, p -> null, id -> "item " + id,
			id -> 0);
	}

	private static SupplyLine find(SupplyPlan plan, String name)
	{
		for (SupplyLine line : plan.getLines())
		{
			if (line.getName().equals(name))
			{
				return line;
			}
		}
		return null;
	}

	private static String rune(Rune rune)
	{
		return "item " + rune.getItemId();
	}

	@Test
	public void dramenLavaAndMistAreSwappedBetween()
	{
		final SupplyPlan plan = plan(access(false), holdings(ItemID.DRAMEN_STAFF, ItemID.LAVA_BATTLESTAFF,
			ItemID.MIST_BATTLESTAFF));
		final StaffSwaps swaps = plan.getStaffSwaps();
		// Dramen on all the way: air, water, earth, fire and law (5 slots). Mist on, lava and dramen carried, law: 3.
		assertArrayEquals(new int[]{ItemID.MIST_BATTLESTAFF}, swaps.getWield().get(Location.FALADOR_PARK).getItemIds());
		assertArrayEquals(new int[]{ItemID.LAVA_BATTLESTAFF},
			swaps.getWield().get(Location.CIVITAS_ILLA_FORTIS).getItemIds());
		assertArrayEquals(new int[]{ItemID.DRAMEN_STAFF}, swaps.getWield().get(Location.ARDOUGNE_FARM).getItemIds());
		assertEquals("item " + ItemID.DRAMEN_STAFF, "item " + swaps.getFairyRing().getItemIds()[0]);

		final SupplyLine mist = find(plan, "item " + ItemID.MIST_BATTLESTAFF);
		assertTrue(mist.isWear());
		assertEquals(0, mist.getSlots());
		final SupplyLine lava = find(plan, "item " + ItemID.LAVA_BATTLESTAFF);
		assertFalse(lava.isWear());
		assertEquals(1, lava.getSlots());
		assertTrue(lava.getNote().contains("Civitas"));
		final SupplyLine dramen = find(plan, "Dramen or lunar staff");
		assertFalse(dramen.isWear());
		assertEquals(1, dramen.getSlots());

		// Only law is carried
		assertNotNull(find(plan, rune(Rune.LAW)));
		for (Rune covered : new Rune[]{Rune.AIR, Rune.WATER, Rune.EARTH, Rune.FIRE})
		{
			assertNull(covered.name(), find(plan, rune(covered)));
		}
	}

	@Test
	public void withTheEliteDiaryOnlyLavaAndMistAreSwapped()
	{
		final SupplyPlan plan = plan(access(true), holdings(ItemID.DRAMEN_STAFF, ItemID.LAVA_BATTLESTAFF,
			ItemID.MIST_BATTLESTAFF));
		final StaffSwaps swaps = plan.getStaffSwaps();
		assertNull(swaps.getFairyRing());
		assertNull(swaps.getWield().get(Location.ARDOUGNE_FARM));
		assertNull(find(plan, "Dramen or lunar staff"));
		assertEquals(2, swaps.getWield().size());
	}

	@Test
	public void aRunePouchWithRoomKeepsOneStaffOn()
	{
		// Divine pouch: with the dramen staff on, air, water, earth, fire and law are the pouch and a slot (2). Carrying
		// a staff can't free a whole slot, so nothing is swapped and the dramen staff is worn as before.
		final SupplyPlan plan = plan(access(false), holdings(ItemID.DRAMEN_STAFF, ItemID.LAVA_BATTLESTAFF,
			ItemID.MIST_BATTLESTAFF, ItemID.DIVINE_RUNE_POUCH));
		assertTrue(plan.getStaffSwaps().isEmpty());
		assertTrue(find(plan, "Dramen or lunar staff").isWear());
		// The pouch: one slot for the first four rune types, the fifth loose
		int runeSlots = 0;
		int pouched = 0;
		for (SupplyLine line : plan.getLines())
		{
			if (line.getGroup() == SupplyLine.Group.RUNES && line.getName().startsWith("item ")
				&& Arrays.stream(Rune.values()).anyMatch(r -> line.getItemIds()[0] == r.getItemId()))
			{
				runeSlots += line.getSlots();
				pouched += line.getNote() != null && line.getNote().startsWith("In your rune pouch") ? 1 : 0;
			}
		}
		assertEquals(2, runeSlots);
		assertEquals(4, pouched);
	}
}
