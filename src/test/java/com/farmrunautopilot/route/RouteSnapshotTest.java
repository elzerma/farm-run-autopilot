package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * The whole route planned for three test accounts, saved in src/test/resources (docs/plans/travel-rework.md, section 7:
 * route snapshots). A change to the planner, or a rebuild of the travel atlas, that moves any of them fails here: the
 * new routes are written to build/route-snapshots.txt to look over, and copied over the saved ones when that's meant.
 */
public class RouteSnapshotTest
{
	private static final String SAVED = "/com/farmrunautopilot/route/route-snapshots.txt";
	private static final Path NOW = Paths.get("build", "route-snapshots.txt");

	@Test
	public void routesAreAsSaved() throws IOException
	{
		final String now = snapshots();
		final String saved = saved();
		if (!now.equals(saved))
		{
			Files.createDirectories(NOW.getParent());
			Files.write(NOW, now.getBytes(StandardCharsets.UTF_8));
		}
		assertEquals("Routes changed: look over " + NOW + " and, if that's meant, copy it to src/test/resources" + SAVED,
			saved, now);
	}

	private static String snapshots()
	{
		final List<Patch> elzerma = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (ElzermaAccount.STOPS.contains(patch.getLocation()))
			{
				elzerma.add(patch);
			}
		}
		final AccessSnapshot mid = midLevel();
		final AccessSnapshot maxed = maxed();
		return "# Routes planned on the travel atlas (RouteSnapshotTest)\n"
			+ section("Like elzerma's: every patch at their 18 stops", ElzermaAccount.access(), ElzermaAccount.holdings(),
				elzerma)
			+ section("Mid-level: 50 in every skill, no quests, a skills necklace, a ring of wealth and a games necklace", mid,
				midLevelHoldings(), usable(mid))
			+ section("Maxed: 99 in every skill, every quest, diary and unlock, every teleport item, no house", maxed,
				maxedHoldings(), usable(maxed));
	}

	private static String section(String title, AccessSnapshot access, Holdings holdings, List<Patch> patches)
	{
		final Route route = RoutePlanner.plan(patches, new RunConfig().sanitise(), access, holdings, new PohSetup(),
			AtlasPlayers.with(access, holdings), null);
		final StringBuilder s = new StringBuilder("\n## ").append(title).append('\n')
			.append(String.format(Locale.ROOT, "%d patches, %d stops, travel %.0f s%n", patches.size(),
				route.getStops().size(), route.getTravelSeconds()).trim()).append('\n');
		int i = 1;
		for (RouteStop stop : route.getStops())
		{
			s.append(String.format(Locale.ROOT, "%2d. %s: %s, %.0f s", i++, stop.getLocation().getDisplayName(),
				stop.describeTravel(), stop.getLegSeconds())).append('\n');
		}
		return s.toString();
	}

	/** The saved routes, or nothing yet; the line ends as written (git may have made them \r\n). */
	private static String saved() throws IOException
	{
		try (InputStream in = RouteSnapshotTest.class.getResourceAsStream(SAVED))
		{
			if (in == null)
			{
				return "";
			}
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			in.transferTo(bytes);
			return bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
		}
	}

	/** The patches this account can farm. */
	private static List<Patch> usable(AccessSnapshot access)
	{
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (access.missingFor(patch).isEmpty())
			{
				patches.add(patch);
			}
		}
		return patches;
	}

	private static AccessSnapshot midLevel()
	{
		return new AccessSnapshot(true, Collections.emptyMap(), levels(50), Collections.emptySet(),
			Collections.emptySet(), null, Spellbook.STANDARD, Collections.emptySet());
	}

	private static Holdings midLevelHoldings()
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.JEWL_NECKLACE_OF_SKILLS_4, 1);
		bank.put(ItemID.RING_OF_WEALTH_5, 1);
		bank.put(ItemID.NECKLACE_OF_MINIGAMES_8, 1);
		return bank(bank);
	}

	private static AccessSnapshot maxed()
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		final Set<String> diaries = new HashSet<>();
		for (AchievementDiary diary : AchievementDiary.values())
		{
			for (AchievementDiary.Tier tier : AchievementDiary.Tier.values())
			{
				diaries.add(AccessSnapshot.diaryKey(diary, tier));
			}
		}
		return new AccessSnapshot(true, quests, levels(99), diaries, EnumSet.allOf(Unlock.class), null,
			Spellbook.STANDARD, Collections.emptySet());
	}

	/** One of every teleport item the plugin tracks (the first of its ids). */
	private static Holdings maxedHoldings()
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		for (TravelItem item : TravelItem.values())
		{
			bank.put(item.getItemIds()[0], 1);
		}
		bank.put(ItemID.DRAMEN_STAFF, 1);
		return bank(bank);
	}

	private static Map<Skill, Integer> levels(int level)
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, level);
		}
		return levels;
	}

	/** These items in the bank, with runes and coins as any account has. */
	private static Holdings bank(Map<Integer, Integer> bank)
	{
		for (Rune rune : Rune.values())
		{
			bank.put(rune.getItemId(), 1000);
		}
		bank.put(ItemID.COINS, 1_000_000);
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}
}
