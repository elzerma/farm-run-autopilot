package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Known-good chains a farmer would take, so the cost model can't drift away from them unnoticed. */
public class RouteChainTest
{
	/** A POH_HOUSE_LOCATION value the plugin doesn't know: a place the game added later. */
	private static final int NEW_PLACE = 99;

	/**
	 * Tree Gnome Village and The Grand Tree (the Stronghold's spirit tree needs both; the old tables only checked the
	 * first), Fairytale II and 99 everywhere; no house.
	 */
	private static AccessSnapshot access()
	{
		return access(0);
	}

	/** As {@link #access()}, with the house where the game's POH_HOUSE_LOCATION puts it (0: none). */
	private static AccessSnapshot access(int houseLocation)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		quests.put(Quest.THE_GRAND_TREE, QuestState.FINISHED);
		quests.put(Quest.FAIRYTALE_II__CURE_A_QUEEN, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.of(Unlock.SPIRIT_TREES, Unlock.FAIRY_RINGS), houseLocation, false, Spellbook.STANDARD,
			Collections.emptySet(), 0, 0, false);
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

	/** The route on the travel atlas for this player, near a stop or not (null). */
	private static List<RouteStop> plan(List<Patch> patches, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, Location near, int nearTiles)
	{
		return RoutePlanner.plan(patches, config, access, holdings, poh, AtlasPlayers.with(access, holdings), null, near,
			nearTiles).getStops();
	}

	@Test
	public void strongholdThenSpiritTreeToTreeGnomeVillage()
	{
		final List<Patch> patches = Arrays.asList(Patch.GNOME_STRONGHOLD_TREE, Patch.GNOME_STRONGHOLD_FRUIT_TREE,
			Patch.TREE_GNOME_VILLAGE_FRUIT_TREE, Patch.FALADOR_TREE, Patch.LUMBRIDGE_TREE, Patch.VARROCK_TREE);
		// Isolate the chain: ending near a bank would otherwise favour finishing at the Stronghold's bank
		final RunConfig config = new RunConfig().sanitise();
		config.setEndNearBank(false);
		final List<RouteStop> stops = plan(patches, config, access(), holdings(ItemID.SLAYER_RING_8, ItemID.DRAMEN_STAFF),
			new PohSetup(), null, 0);

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
		assertEquals(describe(stops), Location.TREE_GNOME_VILLAGE, next.getLocation());
		assertEquals(describe(stops), TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, next.getMethod());
	}

	@Test
	public void startsByWalkingToANearbyStop()
	{
		// By the Grand Tree, about 50 tiles from the Stronghold patches, with Start at: Anywhere
		final List<RouteStop> stops = plan(Arrays.asList(Patch.values()), new RunConfig().sanitise(), access(),
			holdings(ItemID.SLAYER_RING_8, ItemID.DRAMEN_STAFF), new PohSetup(), Location.GNOME_STRONGHOLD, 50);
		assertEquals(describe(stops), Location.GNOME_STRONGHOLD, stops.get(0).getLocation());
		assertEquals(describe(stops), Departure.WALK, stops.get(0).getDeparture());
		assertEquals(describe(stops), TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, stops.get(1).getMethod());

		// Even when ending the run at the Stronghold's bank would otherwise win by a second or two
		final List<RouteStop> few = plan(Arrays.asList(Patch.GNOME_STRONGHOLD_TREE, Patch.FALADOR_TREE,
			Patch.LUMBRIDGE_TREE, Patch.VARROCK_TREE), new RunConfig().sanitise(), access(), holdings(ItemID.SLAYER_RING_8),
			new PohSetup(), Location.GNOME_STRONGHOLD, 36);
		assertEquals(describe(few), Location.GNOME_STRONGHOLD, few.get(0).getLocation());

		// Near Falador Park (57 tiles), where the ring of wealth is quicker than the walk: still the first stop
		final List<RouteStop> park = plan(Arrays.asList(Patch.values()), new RunConfig().sanitise(), access(),
			holdings(ItemID.SLAYER_RING_8, ItemID.RING_OF_WEALTH_5), new PohSetup(), Location.FALADOR_PARK, 57);
		assertEquals(describe(park), Location.FALADOR_PARK, park.get(0).getLocation());
	}

	@Test
	public void spiritTreeIntoTheHouseThenOutOfItsPortal()
	{
		// issue #16: Farming Guild, its spirit tree to the house, then out of the house's Hosidius portal. Without the
		// Magic for Teleport to House (which is quicker when it can be cast) or a tablet
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		levels.put(Skill.MAGIC, 1);
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		final AccessSnapshot access = new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.of(Unlock.SPIRIT_TREES, Unlock.SPIRIT_TREE_FARMING_GUILD), HousePortal.HOSIDIUS, Spellbook.STANDARD,
			Collections.emptySet());
		final PohSetup poh = new PohSetup();
		poh.setSpiritTree(true);
		final RunConfig config = new RunConfig().sanitise();
		config.setEndNearBank(false);
		final List<RouteStop> stops = plan(Arrays.asList(Patch.FARMING_GUILD_HERB, Patch.HOSIDIUS_HERB), config, access,
			holdings(), poh, Location.FARMING_GUILD, 0);
		assertEquals(describe(stops), Location.HOSIDIUS, stops.get(1).getLocation());
		assertEquals(describe(stops), Departure.TREE_HOUSE_PORTAL, stops.get(1).getDeparture());
		assertEquals(describe(stops), TravelMethod.HOUSE_PORTAL_HOSIDIUS, stops.get(1).getMethod());
	}

	@Test
	public void theNexusWhereverTheHouseIs()
	{
		// The nexus's Catherby portal and a spirit tree set up in My house. The route goes through the house while the
		// game says there is one, even in a place the plugin doesn't know yet (only out of the front door needs to know
		// where), and never once it says there's none
		final PohSetup poh = new PohSetup();
		poh.setNexusDestinations(EnumSet.of(PortalNexus.Destination.CATHERBY));
		poh.setSpiritTree(true);
		final RunConfig config = new RunConfig().sanitise();
		config.setEndNearBank(false);
		final List<Patch> catherby = Collections.singletonList(Patch.CATHERBY_HERB);

		// In by Teleport to House (99 Magic on the standard spellbook), quicker than Camelot Teleport and the walk
		for (int house : new int[]{HousePortal.TAVERLEY.getGameValue(), NEW_PLACE})
		{
			final List<RouteStop> teleport = plan(catherby, config, access(house), holdings(), poh, null, 0);
			assertEquals(describe(teleport), Departure.POH_NEXUS, teleport.get(0).getDeparture());
			assertEquals(describe(teleport), TravelMethod.CATHERBY_TELEPORT, teleport.get(0).getMethod());
		}
		final List<RouteStop> none = plan(catherby, config, access(), holdings(), poh, null, 0);
		assertFalse(describe(none), none.get(0).getWays().contains(AtlasWays.familyKey(TravelFamily.HOUSE)));
		assertFalse(describe(none), none.get(0).getDeparture().isViaHouse());

		// In by the Farming Guild's spirit tree, without the Magic for Teleport to House, to a house somewhere new
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		levels.put(Skill.MAGIC, 1);
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		final AccessSnapshot noMagic = new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.of(Unlock.SPIRIT_TREES, Unlock.SPIRIT_TREE_FARMING_GUILD), NEW_PLACE, false, Spellbook.STANDARD,
			Collections.emptySet(), 0, 0, false);
		final List<RouteStop> tree = plan(Arrays.asList(Patch.FARMING_GUILD_HERB, Patch.CATHERBY_HERB), config, noMagic,
			holdings(), poh, Location.FARMING_GUILD, 0);
		assertEquals(describe(tree), Location.CATHERBY, tree.get(1).getLocation());
		assertEquals(describe(tree), Departure.TREE_HOUSE_NEXUS, tree.get(1).getDeparture());
	}

	@Test
	public void treeGnomeVillageChainsFromTheStronghold()
	{
		// The CIQ fairy ring walk is long; the Stronghold's spirit tree is the way in
		final AccessSnapshot access = access();
		final Holdings holdings = holdings(ItemID.SLAYER_RING_8, ItemID.DRAMEN_STAFF);
		assertEquals(true, RoutePlanner.chainsFrom(Location.GNOME_STRONGHOLD, Location.TREE_GNOME_VILLAGE,
			new RunConfig().sanitise(), access, holdings, new PohSetup(), AtlasPlayers.with(access, holdings)));
	}

	@Test
	public void bigRunsKeepTheStrongholdChain()
	{
		// Above the exact-search limit the fast search must still find the chain
		final List<Patch> patches = Arrays.asList(Patch.values());
		final RunConfig config = new RunConfig().sanitise();
		config.setEndNearBank(false);
		final List<RouteStop> stops = plan(patches, config, access(), holdings(ItemID.SLAYER_RING_8, ItemID.DRAMEN_STAFF),
			new PohSetup(), null, 0);
		assertEquals(true, stops.size() > RoutePlanner.EXACT_LIMIT);
		int stronghold = -1;
		for (int i = 0; i < stops.size(); i++)
		{
			if (stops.get(i).getLocation() == Location.GNOME_STRONGHOLD)
			{
				stronghold = i;
			}
		}
		assertEquals(describe(stops), true, stronghold + 1 < stops.size()
			&& stops.get(stronghold + 1).getMethod() == TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE);
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
		final List<RouteStop> whistle = plan(patches, config, access,
			holdings(ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE), new PohSetup(), null, 0);
		assertEquals(describe(whistle), true, whistle.get(whistle.size() - 1).getLocation() != Location.KASTORI);

		// Without a whistle: Renu on every leg. On the atlas each is quickest from the city's pad, by the Civitas illa
		// Fortis Teleport (16 s from Civitas illa Fortis to Kastori against 22 s walking back to the Hunter Guild's
		// pad); the old tables only knew walking back to a pad
		final List<RouteStop> noWhistle = plan(patches, config, access, holdings(), new PohSetup(), null, 0);
		for (RouteStop stop : noWhistle)
		{
			assertTrue(describe(noWhistle), stop.describeTravel().contains("Renu")
				&& stop.travelItem() != TravelItem.QUETZAL_WHISTLE);
		}
		assertEquals(describe(noWhistle), true,
			noWhistle.get(noWhistle.size() - 1).getLocation() != Location.KASTORI);
	}

	@Test
	public void faladorParkWalkToTaverleyThenCabbageToFaladorFarm()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setEndNearBank(false);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		// The Explorer's ring 4 and its Lumbridge & Draynor diaries (the unlimited cabbage teleport needs the hard one)
		final AccessSnapshot access = new AccessSnapshot(true, Collections.emptyMap(), levels, new HashSet<>(Arrays.asList(
			AccessSnapshot.diaryKey(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.MEDIUM),
			AccessSnapshot.diaryKey(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.HARD),
			AccessSnapshot.diaryKey(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.ELITE))),
			Collections.emptySet(), null, Spellbook.STANDARD, Collections.emptySet());
		final List<RouteStop> stops = plan(Arrays.asList(Patch.FALADOR_TREE, Patch.TAVERLEY_TREE, Patch.FALADOR_HERB),
			config, access, holdings(ItemID.RING_OF_WEALTH_5, ItemID.LUMBRIDGE_RING_ELITE), new PohSetup(), null, 0);

		// Falador Park and Taverley back to back with a walk between (either way round), and the farm by the
		// cabbage teleport rather than a walk; the same legs in any order cost the same
		boolean walkedBetween = false;
		for (int i = 1; i < stops.size(); i++)
		{
			final boolean pair = (stops.get(i - 1).getLocation() == Location.FALADOR_PARK
				&& stops.get(i).getLocation() == Location.TAVERLEY)
				|| (stops.get(i - 1).getLocation() == Location.TAVERLEY && stops.get(i).getLocation() == Location.FALADOR_PARK);
			walkedBetween |= pair && stops.get(i).getDeparture() == Departure.WALK;
		}
		assertEquals(describe(stops), true, walkedBetween);
		for (RouteStop stop : stops)
		{
			if (stop.getLocation() == Location.FALADOR_FARM)
			{
				assertEquals(describe(stops), TravelMethod.EXPLORERS_RING_CABBAGE_PATCH, stop.getMethod());
			}
		}
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
