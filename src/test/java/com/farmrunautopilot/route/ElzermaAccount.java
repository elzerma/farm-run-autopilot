package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.supply.Holdings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * An account like elzerma's, for scenario tests: the patches their full run visits and the teleports their routes use
 * (from their in-game screenshots, 2026-10-10). Not exact; close enough that the planner faces their choices.
 */
final class ElzermaAccount
{
	/** The stops of their full run (no Harmony Island or Weiss). */
	static final Set<Location> STOPS = Collections.unmodifiableSet(EnumSet.of(Location.FALADOR_PARK,
		Location.TAVERLEY, Location.KASTORI, Location.TREE_GNOME_VILLAGE, Location.LLETYA, Location.CATHERBY,
		Location.BRIMHAVEN, Location.NEMUS_RETREAT, Location.VARROCK, Location.LUMBRIDGE, Location.GNOME_STRONGHOLD,
		Location.CIVITAS_ILLA_FORTIS, Location.PORT_PHASMATYS, Location.TROLL_STRONGHOLD, Location.HOSIDIUS,
		Location.ARDOUGNE_FARM, Location.FALADOR_FARM, Location.FARMING_GUILD));

	private ElzermaAccount()
	{
	}

	static AccessSnapshot access()
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
		final Set<String> diaries = new HashSet<>(Arrays.asList(
			AccessSnapshot.diaryKey(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.MEDIUM),
			AccessSnapshot.diaryKey(AchievementDiary.ARDOUGNE, AchievementDiary.Tier.MEDIUM)));
		return new AccessSnapshot(true, quests, levels, diaries,
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES, Unlock.QUETZAL_KASTORI), null, Spellbook.STANDARD,
			Collections.emptySet());
	}

	static Holdings holdings()
	{
		return holdingsWithout();
	}

	/** The travel atlas for this account: its diaries and unlocks, and its items, coins for fares included. */
	static RouteAtlas atlas()
	{
		return AtlasPlayers.with(access(), holdings());
	}

	/** Their holdings minus some items, e.g. without the quetzal whistle so Varlamore is chained by Renu. */
	static Holdings holdingsWithout(int... missing)
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		for (int id : new int[]{ItemID.SLAYER_RING_8, ItemID.DRAMEN_STAFF, ItemID.RING_OF_WEALTH_5,
			ItemID.LUMBRIDGE_RING_MEDIUM, ItemID.ARDY_CAPE_MEDIUM, ItemID.ECTOPHIAL, ItemID.VEOS_KHAREDSTS_MEMOIRS,
			ItemID.MOURNING_TELEPORT_CRYSTAL_5, ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE, ItemID.RING_OF_DUELING_8})
		{
			bank.put(id, 1);
		}
		for (Rune rune : Rune.values())
		{
			bank.put(rune.getItemId(), 1000);
		}
		// Fares for boats and charter ships, as any real player has
		bank.put(ItemID.COINS, 1_000_000);
		for (int id : missing)
		{
			bank.remove(id);
		}
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	/** Their patches of these types. */
	static List<Patch> patches(PatchType... types)
	{
		final Set<PatchType> wanted = EnumSet.copyOf(Arrays.asList(types));
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (STOPS.contains(patch.getLocation()) && wanted.contains(patch.getType()))
			{
				patches.add(patch);
			}
		}
		return patches;
	}
}
