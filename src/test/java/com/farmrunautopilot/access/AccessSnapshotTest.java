package com.farmrunautopilot.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

public class AccessSnapshotTest
{
	private static AccessSnapshot snapshot(int farming, int magic, HousePortal portal)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.PRIEST_IN_PERIL, QuestState.FINISHED);
		quests.put(Quest.MOURNINGS_END_PART_I, QuestState.IN_PROGRESS);
		quests.put(Quest.MY_ARMS_BIG_ADVENTURE, QuestState.NOT_STARTED);
		quests.put(Quest.THE_GREAT_BRAIN_ROBBERY, QuestState.FINISHED);

		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.FARMING, farming);
		levels.put(Skill.MAGIC, magic);

		final Set<String> diaries = new HashSet<>();
		diaries.add(AccessSnapshot.diaryKey(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE));

		final Set<Unlock> unlocks = EnumSet.of(Unlock.FAIRY_RINGS);
		return new AccessSnapshot(true, quests, levels, diaries, unlocks, portal);
	}

	@Test
	public void questPointCapeNeedsEveryQuestPoint()
	{
		assertTrue(AccessSnapshot.hasAllQuests(343, 343));
		// A new quest came out: the cape can't be used until it's done
		assertFalse(AccessSnapshot.hasAllQuests(343, 345));
		// Maximum not read: don't trust the cape
		assertFalse(AccessSnapshot.hasAllQuests(343, 0));
		final AccessSnapshot newQuest = new AccessSnapshot(true, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptySet(), Collections.emptySet(), null, null, Collections.emptySet(), 343, 345);
		assertFalse(newQuest.isMet(Requirement.allQuests()));
	}

	@Test
	public void unknownSnapshotLocksNothing()
	{
		for (Patch patch : Patch.values())
		{
			assertTrue(patch.name(), AccessSnapshot.UNKNOWN.missingFor(patch).isEmpty());
		}
	}

	@Test
	public void questsSkillsDiariesAndUnlocks()
	{
		final AccessSnapshot s = snapshot(70, 50, null);
		assertTrue(s.isMet(Requirement.quest(Quest.PRIEST_IN_PERIL)));
		assertFalse(s.isMet(Requirement.quest(Quest.MOURNINGS_END_PART_I)));
		assertTrue(s.isMet(Requirement.questStarted(Quest.MOURNINGS_END_PART_I)));
		assertFalse(s.isMet(Requirement.questStarted(Quest.MY_ARMS_BIG_ADVENTURE)));
		// A quest that was never read counts as not done.
		assertFalse(s.isMet(Requirement.questStarted(Quest.LUNAR_DIPLOMACY)));

		assertTrue(s.isMet(Requirement.skill(Skill.FARMING, 65)));
		assertFalse(s.isMet(Requirement.skill(Skill.FARMING, 85)));
		assertTrue(s.isMet(Requirement.diary(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE)));
		assertFalse(s.isMet(Requirement.diary(AchievementDiary.MORYTANIA, AchievementDiary.Tier.ELITE)));
		assertTrue(s.isMet(Requirement.unlock(Unlock.FAIRY_RINGS)));
		assertFalse(s.isMet(Requirement.unlock(Unlock.SPIRIT_TREES)));
	}

	@Test
	public void patchLocks()
	{
		final AccessSnapshot s = snapshot(70, 50, null);
		assertTrue(s.missingFor(Patch.FARMING_GUILD_TREE).isEmpty());
		assertEquals("Needs 85 Farming", AccessSnapshot.describe(s.missingFor(Patch.FARMING_GUILD_FRUIT_TREE)));
		assertTrue(s.missingFor(Patch.PORT_PHASMATYS_HERB).isEmpty());
		assertTrue(s.missingFor(Patch.LLETYA_FRUIT_TREE).isEmpty());
		assertEquals("Needs My Arm's Big Adventure", AccessSnapshot.describe(s.missingFor(Patch.TROLL_STRONGHOLD_HERB)));
	}

	@Test
	public void tabletsIgnoreMagicLevelButNotQuests()
	{
		final AccessSnapshot lowMagic = snapshot(70, 1, null);
		// Harmony Island Teleport needs 65 Magic, but its tablet works for anyone with the quest.
		assertTrue(lowMagic.missingFor(TravelMethod.HARMONY_ISLAND_TELEPORT).isEmpty());
		assertFalse(lowMagic.canCast(Spell.HARMONY_ISLAND_TELEPORT));
		// Catherby Teleport still needs Lunar Diplomacy.
		assertEquals("Needs Lunar Diplomacy", AccessSnapshot.describe(lowMagic.missingFor(TravelMethod.CATHERBY_TELEPORT)));
	}

	@Test
	public void housePortalMethodsNeedTheRightPortal()
	{
		assertFalse(snapshot(70, 50, null).missingFor(TravelMethod.HOUSE_PORTAL_TAVERLEY).isEmpty());
		assertFalse(snapshot(70, 50, HousePortal.RIMMINGTON).missingFor(TravelMethod.HOUSE_PORTAL_TAVERLEY).isEmpty());
		assertTrue(snapshot(70, 50, HousePortal.TAVERLEY).missingFor(TravelMethod.HOUSE_PORTAL_TAVERLEY).isEmpty());
	}

	@Test
	public void fairyRingMethodsNeedTheUnlock()
	{
		final AccessSnapshot without = new AccessSnapshot(true, Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptySet(), Collections.emptySet(), null);
		assertFalse(without.missingFor(TravelMethod.FAIRY_RING_CIR).isEmpty());
		assertTrue(snapshot(70, 50, null).missingFor(TravelMethod.FAIRY_RING_CIR).isEmpty());
	}

	@Test
	public void capesNeedTheirSkills()
	{
		final AccessSnapshot s = snapshot(70, 50, null);
		assertEquals("Needs 99 Farming", AccessSnapshot.describe(s.missingFor(TravelMethod.FARMING_CAPE)));
		assertEquals("Needs 99 in every skill", AccessSnapshot.describe(s.missingFor(TravelMethod.MAX_CAPE_FARMING_GUILD)));
		assertEquals("Needs every Elite diary", AccessSnapshot.describe(s.missingFor(TravelMethod.DIARY_CAPE_LUMBRIDGE)));
		assertEquals("Needs 99 Construction",
			AccessSnapshot.describe(s.missingFor(TravelMethod.CONSTRUCTION_CAPE_TAVERLEY)));
		// The Explorer's ring needs the Medium Lumbridge & Draynor diary.
		assertFalse(s.missingFor(TravelMethod.EXPLORERS_RING_CABBAGE_PATCH).isEmpty());
	}

	@Test
	public void maxedAccountMeetsAllSkillsAndAllDiaries()
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		final Set<String> diaries = new HashSet<>();
		for (AchievementDiary diary : AchievementDiary.values())
		{
			for (AchievementDiary.Tier tier : AchievementDiary.Tier.values())
			{
				diaries.add(AccessSnapshot.diaryKey(diary, tier));
			}
		}
		final AccessSnapshot maxed = new AccessSnapshot(true, Collections.emptyMap(), levels, diaries,
			Collections.emptySet(), null);
		assertTrue(maxed.missingFor(TravelMethod.MAX_CAPE_FARMING_GUILD).isEmpty());
		assertTrue(maxed.missingFor(TravelMethod.DIARY_CAPE_LUMBRIDGE).isEmpty());
		assertTrue(maxed.missingFor(TravelMethod.FARMING_CAPE).isEmpty());

		// One skill short of maxed.
		levels.put(Skill.SAILING, 98);
		final AccessSnapshot almost = new AccessSnapshot(true, Collections.emptyMap(), levels, diaries,
			Collections.emptySet(), null);
		assertFalse(almost.missingFor(TravelMethod.MAX_CAPE_FARMING_GUILD).isEmpty());
	}

	@Test
	public void everyDiaryHasFourDistinctVarbits()
	{
		final Set<Integer> seen = new HashSet<>();
		for (AchievementDiary diary : AchievementDiary.values())
		{
			assertEquals(4, diary.getCompleteVarbits().length);
			for (int varbit : diary.getCompleteVarbits())
			{
				assertTrue(diary + " repeats varbit " + varbit, seen.add(varbit));
			}
		}
	}
}
