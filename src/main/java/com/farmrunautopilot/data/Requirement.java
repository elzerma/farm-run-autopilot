package com.farmrunautopilot.data;

import com.farmrunautopilot.data.poh.HousePortal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Something the account needs before a patch or travel method can be used. Only the fields that
 * belong to {@link #getKind()} are set; the rest are null or 0.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Requirement
{
	public enum Kind
	{
		QUEST_COMPLETE,
		QUEST_STARTED,
		SKILL,
		DIARY,
		UNLOCK,
		HOUSE_PORTAL,
		/** {@link #getLevel()} in every skill (max cape). */
		ALL_SKILLS,
		/** Every achievement diary at {@link #getDiaryTier()} (achievement diary cape). */
		ALL_DIARIES,
		/**
		 * Every quest point there is (quest point cape). The cape is taken off and can't be used again when a
		 * new quest comes out, until it's done.
		 */
		ALL_QUESTS
	}

	Kind kind;
	Quest quest;
	Skill skill;
	int level;
	AchievementDiary diary;
	AchievementDiary.Tier diaryTier;
	Unlock unlock;
	HousePortal housePortal;

	public static Requirement quest(Quest quest)
	{
		return new Requirement(Kind.QUEST_COMPLETE, quest, null, 0, null, null, null, null);
	}

	public static Requirement questStarted(Quest quest)
	{
		return new Requirement(Kind.QUEST_STARTED, quest, null, 0, null, null, null, null);
	}

	public static Requirement skill(Skill skill, int level)
	{
		return new Requirement(Kind.SKILL, null, skill, level, null, null, null, null);
	}

	public static Requirement diary(AchievementDiary diary, AchievementDiary.Tier tier)
	{
		return new Requirement(Kind.DIARY, null, null, 0, diary, tier, null, null);
	}

	public static Requirement unlock(Unlock unlock)
	{
		return new Requirement(Kind.UNLOCK, null, null, 0, null, null, unlock, null);
	}

	public static Requirement allSkills(int level)
	{
		return new Requirement(Kind.ALL_SKILLS, null, null, level, null, null, null, null);
	}

	public static Requirement allDiaries(AchievementDiary.Tier tier)
	{
		return new Requirement(Kind.ALL_DIARIES, null, null, 0, null, tier, null, null);
	}

	public static Requirement allQuests()
	{
		return new Requirement(Kind.ALL_QUESTS, null, null, 0, null, null, null, null);
	}

	public static Requirement housePortal(HousePortal portal)
	{
		return new Requirement(Kind.HOUSE_PORTAL, null, null, 0, null, null, null, portal);
	}

	/**
	 * Plain-language text for tooltips, e.g. "Needs Elite Morytania diary".
	 */
	public String describe()
	{
		switch (kind)
		{
			case QUEST_COMPLETE:
				return "Needs " + quest.getName();
			case QUEST_STARTED:
				return "Needs " + quest.getName() + " started";
			case SKILL:
				return "Needs " + level + " " + skill.getName();
			case DIARY:
				return "Needs " + capitalise(diaryTier.name()) + " " + diary.getDisplayName() + " diary";
			case UNLOCK:
				return "Needs " + unlock.getDescription();
			case HOUSE_PORTAL:
				return "Needs house portal in " + housePortal.getDisplayName();
			case ALL_SKILLS:
				return "Needs " + level + " in every skill";
			case ALL_DIARIES:
				return "Needs every " + capitalise(diaryTier.name()) + " diary";
			case ALL_QUESTS:
				return "Needs every quest done, including any new ones";
			default:
				throw new IllegalStateException("Unhandled requirement kind " + kind);
		}
	}

	private static String capitalise(String s)
	{
		return s.charAt(0) + s.substring(1).toLowerCase();
	}
}
