package com.farmrunautopilot.data.travel.atlas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.supply.Holdings;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * The shipped travel atlas (tools/atlas, docs/plans/travel-rework.md sprint T2) and legs built from it for pretend
 * players: the cases sprint T0 found the plugin's own tables get wrong.
 */
public class TravelAtlasTest
{
	private static final TravelAtlas ATLAS = TravelAtlas.get();

	@Test
	public void readsTheShippedAtlas()
	{
		assertFalse(ATLAS.isEmpty());
		assertTrue(ATLAS.getTeleports().size() > 500);
		assertTrue(ATLAS.getHops().size() > 1000);
		assertFalse(ATLAS.getNeeds().stream().anyMatch(n -> n.getKind() == AtlasNeed.Kind.UNKNOWN));
	}

	@Test
	public void everyPatchThePluginRunsCanBeReachedFromALanding()
	{
		for (Patch patch : PatchPoints.all().keySet())
		{
			final TravelAtlas.Place place = ATLAS.patchPlace(patch.name());
			assertNotNull(patch + " has no place in the atlas", place);
			assertTrue(patch + " has no way from a teleport or hop landing", ATLAS.into(place.getId()).stream()
				.anyMatch(from -> ATLAS.place(from).getKind() == TravelAtlas.Kind.LANDING));
		}
	}

	@Test
	public void anItemNeedIsAnyOneOfItsBranches()
	{
		final AtlasNeed necklace = AtlasNeed.parse("ITEMS", "11105=1;11107=1", "any skills necklace");
		assertEquals(2, necklace.getBranches().size());
		final AtlasNeed air = AtlasNeed.parse("ITEMS", "556|4695=3/1381|1397", "air runes");
		assertEquals(3, air.getBranches().get(0).getQuantity());
		assertEquals(2, air.getBranches().get(0).getCovers().length);
		final AtlasNeed unlock = AtlasNeed.parse("ITEMS", "unlock:KARAMJA_ELITE", "Karamja elite");
		assertEquals("KARAMJA_ELITE", unlock.getBranches().get(0).getUnlock());

		final AtlasAccount withSkillsNecklace3 = account(99, items(ItemID.JEWL_NECKLACE_OF_SKILLS_3, 1));
		assertTrue(withSkillsNecklace3.meets(necklace));
		assertFalse(withSkillsNecklace3.meets(air));
		assertTrue(account(99, items(1381, 1)).meets(air));
	}

	@Test
	public void hosidiusBySkillsNecklaceNeeds45AgilityForTheSteppingStone()
	{
		final Map<Integer, Integer> necklace = items(ItemID.JEWL_NECKLACE_OF_SKILLS_4, 1);
		final AtlasLegs.Leg with = new AtlasLegs(ATLAS, account(99, necklace)).quickest(-1, places("HOSIDIUS_HERB"));
		final AtlasLegs.Leg without = new AtlasLegs(ATLAS, account(44, necklace)).quickest(-1, places("HOSIDIUS_HERB"));
		assertNotNull(with);
		assertNotNull(without);
		assertTrue(with.getHow(), with.getHow().startsWith("Skills necklace"));
		// 122 tiles round instead of 79 and the stone (T0)
		assertTrue(with.getSeconds() + " against " + without.getSeconds(), without.getSeconds() > with.getSeconds() + 8);
	}

	@Test
	public void faladorParkToTaverleyGoesOverTheRocksWith66Agility()
	{
		final int park = ATLAS.patchPlace("FALADOR_TREE").getId();
		final AtlasLegs.Leg rocks = new AtlasLegs(ATLAS, account(99, Map.of())).quickest(park, places("TAVERLEY_TREE"));
		final AtlasLegs.Leg gate = new AtlasLegs(ATLAS, account(65, Map.of())).quickest(park, places("TAVERLEY_TREE"));
		assertNotNull(rocks);
		assertNotNull(gate);
		assertTrue(rocks.isWalking());
		assertTrue(rocks.getSeconds() + " against " + gate.getSeconds(), gate.getSeconds() > rocks.getSeconds() + 5);
	}

	@Test
	public void harmonyIslandWithoutItsTeleportGoesByBoatAndBrotherTranquility()
	{
		final Map<Integer, Integer> held = new HashMap<>(items(ItemID.ECTOPHIAL, 1));
		held.put(ItemID.ECTOTOKEN, 100);
		held.put(ItemID.COINS, 100_000);
		final AtlasLegs.Leg leg = new AtlasLegs(ATLAS, account(99, held)).quickest(-1, places("HARMONY_ISLAND_HERB"));
		assertNotNull("No way to Harmony Island without its teleport", leg);
		assertTrue(leg.getHow(), leg.getHow().contains("Brother Tranquility"));
	}

	private static List<Integer> places(String patch)
	{
		return List.of(ATLAS.patchPlace(patch).getId());
	}

	private static Map<Integer, Integer> items(int id, int quantity)
	{
		final Map<Integer, Integer> items = new HashMap<>();
		items.put(id, quantity);
		return items;
	}

	/** Every quest done, every skill at {@code level}, no diaries or unlocks, holding these items in the bank. */
	private static AtlasAccount account(int level, Map<Integer, Integer> bank)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			levels.put(skill, level);
		}
		final AccessSnapshot access = new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.noneOf(Unlock.class), null);
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		final Holdings holdings = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
		return new AtlasAccount(access, holdings, Map.of(), Map.of(), Set.of());
	}
}
