package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** Known-good chains a farmer would take, so the cost model can't drift away from them unnoticed. */
public class RouteChainTest
{
	private static AccessSnapshot access()
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		quests.put(Quest.FAIRYTALE_II__CURE_A_QUEEN, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.of(Unlock.SPIRIT_TREES, Unlock.FAIRY_RINGS), null, Spellbook.STANDARD, Collections.emptySet());
	}

	private static Holdings holdings(int... itemIds)
	{
		final Map<Integer, Integer> bank = new java.util.HashMap<>();
		for (int id : itemIds)
		{
			bank.put(id, 1);
		}
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	@Test
	public void strongholdThenSpiritTreeToTreeGnomeVillage()
	{
		final List<Patch> patches = Arrays.asList(Patch.GNOME_STRONGHOLD_TREE, Patch.GNOME_STRONGHOLD_FRUIT_TREE,
			Patch.TREE_GNOME_VILLAGE_FRUIT_TREE, Patch.FALADOR_TREE, Patch.LUMBRIDGE_TREE, Patch.VARROCK_TREE);
		// Isolate the chain: ending near a bank would otherwise favour finishing at the Stronghold's bank
		final RunConfig config = new RunConfig().sanitise();
		config.setEndNearBank(false);
		final Route route = RoutePlanner.plan(patches, config, access(),
			holdings(ItemID.SLAYER_RING_8, ItemID.DRAMEN_STAFF), new PohSetup());

		final List<RouteStop> stops = route.getStops();
		int stronghold = -1;
		for (int i = 0; i < stops.size(); i++)
		{
			if (stops.get(i).getLocation() == Location.GNOME_STRONGHOLD)
			{
				stronghold = i;
			}
		}
		if (stronghold + 1 >= stops.size())
		{
			throw new AssertionError(describe(stops));
		}
		final RouteStop next = stops.get(stronghold + 1);
		assertEquals(stops.toString(), Location.TREE_GNOME_VILLAGE, next.getLocation());
		assertEquals(stops.toString(), TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, next.getMethod());
	}

	@Test
	public void varlamoreChainsWithRenuAndKastoriIsNotLast()
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.CHILDREN_OF_THE_SUN, QuestState.FINISHED);
		quests.put(Quest.TWILIGHTS_PROMISE, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		final AccessSnapshot access = new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.of(Unlock.QUETZAL_KASTORI), null, Spellbook.STANDARD, Collections.emptySet());
		final RunConfig config = new RunConfig().sanitise();
		config.setEndNearBank(false);
		final List<Patch> patches = Arrays.asList(Patch.KASTORI_FRUIT_TREE, Patch.NEMUS_RETREAT_TREE,
			Patch.CIVITAS_HERB);

		// With an unlimited whistle, whistling beats walking back to the pad; Kastori still isn't last
		final List<RouteStop> whistle = RoutePlanner.plan(patches, config, access,
			holdings(ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE), new PohSetup()).getStops();
		assertEquals(describe(whistle), true, whistle.get(whistle.size() - 1).getLocation() != Location.KASTORI);

		// Without a whistle: in by teleport, then Renu between the pads
		final List<RouteStop> noWhistle = RoutePlanner.plan(patches, config, access, holdings(), new PohSetup())
			.getStops();
		int renu = 0;
		for (RouteStop stop : noWhistle)
		{
			renu += stop.getDeparture() == Departure.RENU ? 1 : 0;
		}
		assertEquals(describe(noWhistle), 2, renu);
		assertEquals(describe(noWhistle), true,
			noWhistle.get(noWhistle.size() - 1).getLocation() != Location.KASTORI);
	}

	private static String describe(List<RouteStop> stops)
	{
		final StringBuilder b = new StringBuilder();
		for (RouteStop stop : stops)
		{
			b.append(stop.getLocation()).append(" via ").append(stop.describeTravel()).append(" (")
				.append(Math.round(stop.getLegSeconds())).append(" s); ");
		}
		return b.toString();
	}
}
