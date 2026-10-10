package com.farmrunautopilot.access;

import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.SettingsStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads quests, diaries and skill levels on the client thread and publishes an {@link AccessSnapshot}
 * (SPEC 9). Results are cached and refreshed after login, on request, and every
 * {@link #REFRESH_TICKS} game ticks, never every tick.
 */
@Slf4j
@Singleton
public class AccessChecker
{
	/** Quests and diaries change rarely; re-read them about every 30 seconds. */
	private static final int REFRESH_TICKS = 50;

	private static final Set<Quest> QUESTS = EnumSet.of(Quest.FAIRYTALE_II__CURE_A_QUEEN, Quest.TREE_GNOME_VILLAGE);
	/** Every skill, since the max cape needs 99 in all of them. */
	private static final Set<Skill> SKILLS = AccessSnapshot.REAL_SKILLS;

	static
	{
		final List<Requirement> all = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			all.addAll(patch.getRequirements());
		}
		for (Spell spell : Spell.values())
		{
			all.addAll(spell.getRequirements());
		}
		for (TravelMethod method : TravelMethod.values())
		{
			all.addAll(method.getRequirements());
		}
		for (TravelItem item : TravelItem.values())
		{
			all.addAll(item.getRequirements());
		}
		for (Requirement requirement : all)
		{
			if (requirement.getQuest() != null)
			{
				QUESTS.add(requirement.getQuest());
			}
		}
	}

	private final Client client;
	private final SettingsStore settings;

	private volatile AccessSnapshot snapshot = AccessSnapshot.UNKNOWN;
	private volatile boolean refreshRequested = true;
	private int ticksSinceRefresh;

	@Inject
	AccessChecker(Client client, SettingsStore settings)
	{
		this.client = client;
		this.settings = settings;
	}

	public AccessSnapshot getSnapshot()
	{
		return snapshot;
	}

	/** Forget the previous account, e.g. after switching accounts. */
	public void reset()
	{
		snapshot = AccessSnapshot.UNKNOWN;
		refreshRequested = true;
	}

	/** Re-read on the next game tick if a level that matters changed. Ignores ordinary XP drops. */
	public void onStatChanged(Skill skill, int realLevel)
	{
		final AccessSnapshot current = snapshot;
		if (SKILLS.contains(skill) && current.isKnown() && current.level(skill) != realLevel)
		{
			refreshRequested = true;
		}
	}

	/** Re-read on the next game tick (after login or a Setup change). Any thread. */
	public void requestRefresh()
	{
		refreshRequested = true;
	}

	/**
	 * Call on the client thread every game tick.
	 *
	 * @return whether the snapshot changed
	 */
	public boolean onGameTick()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		if (!refreshRequested && ++ticksSinceRefresh < REFRESH_TICKS)
		{
			return false;
		}
		refreshRequested = false;
		ticksSinceRefresh = 0;

		final AccessSnapshot next = read();
		if (next.equals(snapshot))
		{
			return false;
		}
		log.debug("Account access changed");
		snapshot = next;
		return true;
	}

	private AccessSnapshot read()
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : QUESTS)
		{
			quests.put(quest, quest.getState(client));
		}

		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : SKILLS)
		{
			levels.put(skill, client.getRealSkillLevel(skill));
		}

		final Set<String> diaries = new HashSet<>();
		for (AchievementDiary diary : AchievementDiary.values())
		{
			for (AchievementDiary.Tier tier : AchievementDiary.Tier.values())
			{
				if (client.getVarbitValue(diary.getCompleteVarbit(tier)) > 0)
				{
					diaries.add(AccessSnapshot.diaryKey(diary, tier));
				}
			}
		}

		final AccountSettings account = settings.getAccount();
		final Set<Unlock> unlocks = EnumSet.noneOf(Unlock.class);
		unlocks.addAll(account.getManualUnlocks());
		// A dramen or lunar staff is also needed unless the Elite Lumbridge diary is done; the supply
		// calculator (M4) checks for the staff.
		if (quests.get(Quest.FAIRYTALE_II__CURE_A_QUEEN) != QuestState.NOT_STARTED)
		{
			unlocks.add(Unlock.FAIRY_RINGS);
		}
		if (quests.get(Quest.TREE_GNOME_VILLAGE) == QuestState.FINISHED)
		{
			unlocks.add(Unlock.SPIRIT_TREES);
		}

		final PohAltar altar = account.getPoh().getAltar();
		return new AccessSnapshot(true, Collections.unmodifiableMap(quests), Collections.unmodifiableMap(levels),
			Collections.unmodifiableSet(diaries), Collections.unmodifiableSet(unlocks), account.getPoh().getPortal(),
			spellbook(client.getVarbitValue(VarbitID.SPELLBOOK)),
			altar != null ? altar.getSpellbooks() : Collections.emptySet(),
			// UNVERIFIED: QP_MAX is assumed to be the quest points available in the game (shown in Rules > Unlocks)
			client.getVarpValue(VarPlayerID.QP), client.getVarbitValue(VarbitID.QP_MAX));
	}

	/** The SPELLBOOK varbit: 0 standard, 1 ancient, 2 lunar, 3 arceuus; null for anything else. */
	static Spellbook spellbook(int value)
	{
		switch (value)
		{
			case 0:
				return Spellbook.STANDARD;
			case 1:
				return Spellbook.ANCIENT;
			case 2:
				return Spellbook.LUNAR;
			case 3:
				return Spellbook.ARCEUUS;
			default:
				return null;
		}
	}

	/** Re-read soon if the varbit is the spellbook. Client thread. */
	public void onVarbitChanged(int varbitId)
	{
		if (varbitId == VarbitID.SPELLBOOK)
		{
			refreshRequested = true;
		}
	}

	/** Unlocks that are worked out automatically, so Setup shows them as detected rather than a toggle. */
	public static boolean isDetected(Unlock unlock)
	{
		return unlock == Unlock.FAIRY_RINGS || unlock == Unlock.SPIRIT_TREES;
	}
}
