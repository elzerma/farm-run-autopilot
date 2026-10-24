package com.farmrunautopilot.access;

import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * An immutable picture of what the account has unlocked (SPEC 9), safe to read from any thread.
 */
@Value
public class AccessSnapshot
{
	/** Before the first login: nothing is known, so nothing shows as locked. */
	public static final AccessSnapshot UNKNOWN = new AccessSnapshot(false, Collections.emptyMap(),
		Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), null);

	/**
	 * Every skill. In this RuneLite version {@code Skill.OVERALL} is a deprecated field left as null, not an
	 * enum value, so {@code Skill.values()} already has only real skills.
	 */
	public static final Set<Skill> REAL_SKILLS = Collections.unmodifiableSet(EnumSet.allOf(Skill.class));

	/** False until real account data has been read. */
	boolean known;
	Map<Quest, QuestState> quests;
	Map<Skill, Integer> realLevels;
	/** Completed diaries as "DIARY:TIER", e.g. "FALADOR:ELITE". */
	Set<String> completedDiaries;
	/** Detected unlocks plus the ones the player ticked by hand. */
	Set<Unlock> unlocks;
	/** Where the house is, as the game keeps it (POH_HOUSE_LOCATION): 0 without one; {@link #getHousePortal()}. */
	int houseLocation;
	/** Teleport to House lands outside the house, by its portal (the house's own setting, read from the game). */
	boolean houseTeleportOutside;
	/** The spellbook the player is on, or null if unknown. */
	Spellbook spellbook;
	/** Spellbooks a POH altar can switch to (empty without one). */
	Set<Spellbook> altarSpellbooks;
	/** The player's quest points. */
	int questPoints;
	/** Quest points available in the game right now; 0 if unknown. */
	int maxQuestPoints;
	/** Any ironman type, group ironmen included: no Grand Exchange, so a tablet they don't have can't be bought. */
	boolean ironman;

	public AccessSnapshot(boolean known, Map<Quest, QuestState> quests, Map<Skill, Integer> realLevels,
		Set<String> completedDiaries, Set<Unlock> unlocks, int houseLocation, boolean houseTeleportOutside,
		Spellbook spellbook, Set<Spellbook> altarSpellbooks, int questPoints, int maxQuestPoints, boolean ironman)
	{
		this.known = known;
		this.quests = quests;
		this.realLevels = realLevels;
		this.completedDiaries = completedDiaries;
		this.unlocks = unlocks;
		this.houseLocation = houseLocation;
		this.houseTeleportOutside = houseTeleportOutside;
		this.spellbook = spellbook;
		this.altarSpellbooks = altarSpellbooks;
		this.questPoints = questPoints;
		this.maxQuestPoints = maxQuestPoints;
		this.ironman = ironman;
	}

	/** With a house in this place (null: none), Teleport to House landing inside it. */
	public AccessSnapshot(boolean known, Map<Quest, QuestState> quests, Map<Skill, Integer> realLevels,
		Set<String> completedDiaries, Set<Unlock> unlocks, HousePortal housePortal, Spellbook spellbook,
		Set<Spellbook> altarSpellbooks, int questPoints, int maxQuestPoints, boolean ironman)
	{
		this(known, quests, realLevels, completedDiaries, unlocks, housePortal != null ? housePortal.getGameValue() : 0,
			false, spellbook, altarSpellbooks, questPoints, maxQuestPoints, ironman);
	}

	/** Without the account type: a normal account, not an ironman. */
	public AccessSnapshot(boolean known, Map<Quest, QuestState> quests, Map<Skill, Integer> realLevels,
		Set<String> completedDiaries, Set<Unlock> unlocks, HousePortal housePortal, Spellbook spellbook,
		Set<Spellbook> altarSpellbooks, int questPoints, int maxQuestPoints)
	{
		this(known, quests, realLevels, completedDiaries, unlocks, housePortal, spellbook, altarSpellbooks, questPoints,
			maxQuestPoints, false);
	}

	/** Without quest point information: the quest point cape counts as unusable. */
	public AccessSnapshot(boolean known, Map<Quest, QuestState> quests, Map<Skill, Integer> realLevels,
		Set<String> completedDiaries, Set<Unlock> unlocks, HousePortal housePortal, Spellbook spellbook,
		Set<Spellbook> altarSpellbooks)
	{
		this(known, quests, realLevels, completedDiaries, unlocks, housePortal, spellbook, altarSpellbooks, 0, 0);
	}

	/** Without spellbook information: any book counts as usable. */
	public AccessSnapshot(boolean known, Map<Quest, QuestState> quests, Map<Skill, Integer> realLevels,
		Set<String> completedDiaries, Set<Unlock> unlocks, HousePortal housePortal)
	{
		this(known, quests, realLevels, completedDiaries, unlocks, housePortal, null, Collections.emptySet());
	}

	public static String diaryKey(AchievementDiary diary, AchievementDiary.Tier tier)
	{
		return diary.name() + ":" + tier.name();
	}

	/** The player has a house, as the game says (before the first login, none: the route plans no house then). */
	public boolean hasHouse()
	{
		return houseLocation != 0;
	}

	/** Where the house portal is, or null without a house or in a place the plugin doesn't know yet. */
	public HousePortal getHousePortal()
	{
		return HousePortal.of(houseLocation);
	}

	public boolean isMet(Requirement requirement)
	{
		if (!known)
		{
			return true;
		}
		switch (requirement.getKind())
		{
			case QUEST_COMPLETE:
				return quests.get(requirement.getQuest()) == QuestState.FINISHED;
			case QUEST_STARTED:
			{
				final QuestState state = quests.get(requirement.getQuest());
				return state != null && state != QuestState.NOT_STARTED;
			}
			case SKILL:
				return level(requirement.getSkill()) >= requirement.getLevel();
			case DIARY:
				return completedDiaries.contains(diaryKey(requirement.getDiary(), requirement.getDiaryTier()));
			case UNLOCK:
				return unlocks.contains(requirement.getUnlock());
			case HOUSE_PORTAL:
				return requirement.getHousePortal() == getHousePortal();
			case ALL_SKILLS:
				for (Skill skill : REAL_SKILLS)
				{
					if (level(skill) < requirement.getLevel())
					{
						return false;
					}
				}
				return true;
			case ALL_DIARIES:
				for (AchievementDiary diary : AchievementDiary.values())
				{
					if (!completedDiaries.contains(diaryKey(diary, requirement.getDiaryTier())))
					{
						return false;
					}
				}
				return true;
			case ALL_QUESTS:
				return hasAllQuests(questPoints, maxQuestPoints);
			default:
				return false;
		}
	}

	/** Every quest point in the game; an unknown maximum (0) counts as not. */
	static boolean hasAllQuests(int questPoints, int maxQuestPoints)
	{
		return maxQuestPoints > 0 && questPoints >= maxQuestPoints;
	}

	public int level(Skill skill)
	{
		final Integer level = realLevels.get(skill);
		return level != null ? level : 1;
	}

	/**
	 * @return the requirements that aren't met, empty if everything is
	 */
	public List<Requirement> missing(List<Requirement> requirements)
	{
		final List<Requirement> missing = new ArrayList<>();
		for (Requirement requirement : requirements)
		{
			if (!isMet(requirement))
			{
				missing.add(requirement);
			}
		}
		return missing;
	}

	public List<Requirement> missingFor(Patch patch)
	{
		return missing(patch.getRequirements());
	}

	/**
	 * Requirements for a travel method: its own, the item's, the spell's quest, and the spell's Magic level
	 * when there is no tablet (tablets work at any Magic level). Owning the item, tablet or runes is checked
	 * by the supply calculator, not here.
	 */
	public List<Requirement> missingFor(TravelMethod method)
	{
		final List<Requirement> all = new ArrayList<>(method.getRequirements());
		if (method.getItem() != null)
		{
			all.addAll(method.getItem().getRequirements());
		}
		final Spell spell = method.getSpell();
		if (spell != null)
		{
			all.addAll(spell.getRequirements());
			if (!spell.hasTablet())
			{
				all.add(Requirement.skill(Skill.MAGIC, spell.getMagicLevel()));
			}
		}
		return missing(all);
	}

	/**
	 * Whether the player can cast this spell rather than needing its tablet: Magic level, quest, and being on
	 * its spellbook (or able to switch with a house altar). elzerma asked not to hop spellbooks otherwise.
	 */
	public boolean canCast(Spell spell)
	{
		return !known || (level(Skill.MAGIC) >= spell.getMagicLevel() && missing(spell.getRequirements()).isEmpty()
			&& (isOnSpellbook(spell) || altarSpellbooks.contains(spell.getSpellbook()) || canSwapFor(spell)));
	}

	/**
	 * As {@link #canCast(Spell)}, with switching spellbook (Spellbook Swap, the house altar) allowed only if
	 * {@code switching} (Travel > Ways to travel > Switch spellbook to cast).
	 */
	public boolean canCast(Spell spell, boolean switching)
	{
		return canCast(spell) && (switching || isOnSpellbook(spell));
	}

	/**
	 * The spell is on another book that the house altar can't switch to, but the player is on Lunar and can cast
	 * Spellbook Swap for it (one cast). Never true off Lunar.
	 */
	public boolean needsLunarSwap(Spell spell)
	{
		return known && !isOnSpellbook(spell) && !altarSpellbooks.contains(spell.getSpellbook()) && canSwapFor(spell);
	}

	/** Spellbook Swap only exists on Lunar: 96 Magic and Dream Mentor. */
	private boolean canSwapFor(Spell spell)
	{
		return spellbook == Spellbook.LUNAR && spell.getSpellbook() != Spellbook.LUNAR
			&& level(Skill.MAGIC) >= Spell.SPELLBOOK_SWAP.getMagicLevel()
			&& missing(Spell.SPELLBOOK_SWAP.getRequirements()).isEmpty();
	}

	/** On the spell's spellbook right now (or the spellbook isn't known). */
	public boolean isOnSpellbook(Spell spell)
	{
		return spellbook == null || spellbook == spell.getSpellbook();
	}

	/** Tooltip text listing what's missing, e.g. "Needs 65 Farming, Needs Priest in Peril". */
	public static String describe(List<Requirement> missing)
	{
		final List<String> parts = new ArrayList<>();
		for (Requirement requirement : missing)
		{
			parts.add(requirement.describe());
		}
		return String.join(", ", parts);
	}
}
