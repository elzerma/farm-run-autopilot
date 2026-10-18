package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.TravelStyle;
import com.farmrunautopilot.supply.Holdings;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Routes planned on the travel atlas (docs/plans/travel-rework.md, sprint T3): the plan's scenarios (section 7), the
 * charges, daily uses and the whistle's wait kept, the first leg from where the player stands, and elzerma's full run
 * beside the old tables' for the log.
 */
public class AtlasRouteTest
{
	private static final RunConfig CONFIG = new RunConfig().sanitise();

	@Test
	public void harmonyIslandWithoutItsTeleportGoesByBoatAndBrotherTranquility()
	{
		final AccessSnapshot access = access(99, Collections.emptySet(), Quest.THE_GREAT_BRAIN_ROBBERY);
		final Holdings holdings = bank(ItemID.ECTOPHIAL, 1, ItemID.COINS, 100_000);
		final RouteStop stop = between(null, Location.HARMONY_ISLAND, access, holdings);
		assertEquals(stop.describeTravel(), Departure.ATLAS, stop.getDeparture());
		assertTrue(stop.describeTravel(), stop.describeTravel().contains("Brother Tranquility"));
		assertTrue(stop.getBreakdown(), stop.getLegSeconds() > 20 && stop.getLegSeconds() < 120);
	}

	@Test
	public void harmonyIslandWithItsTabletTakesTheTablet()
	{
		// On the standard spellbook with no altar: the spell can't be cast, the tablet can
		final AccessSnapshot access = access(99);
		final RouteStop stop = between(null, Location.HARMONY_ISLAND, access, bank(ItemID.TELETAB_HARMONY, 5));
		assertEquals(stop.describeTravel(), TravelMethod.HARMONY_ISLAND_TELEPORT, stop.getMethod());
		assertEquals(Departure.DIRECT, stop.getDeparture());
	}

	@Test
	public void hosidiusBySkillsNecklaceNeedsTheSteppingStone()
	{
		final Holdings holdings = bank(ItemID.JEWL_NECKLACE_OF_SKILLS_4, 1);
		final RouteStop agile = between(null, Location.HOSIDIUS, accessWith(Skill.AGILITY, 50), holdings);
		final RouteStop not = between(null, Location.HOSIDIUS, accessWith(Skill.AGILITY, 30), holdings);
		assertEquals(agile.getBreakdown(), TravelMethod.SKILLS_NECKLACE_WOODCUTTING_GUILD, agile.getMethod());
		assertTrue(agile.getBreakdown(), agile.getBreakdown().contains("Stepping stone"));
		// 122 tiles round without the stone (T0)
		assertTrue(not.getBreakdown(), not.getLegSeconds() > agile.getLegSeconds() + 8);
	}

	@Test
	public void faladorParkToTaverleyGoesOverTheRocksWith66Agility()
	{
		final RouteStop rocks = between(Location.FALADOR_PARK, Location.TAVERLEY, accessWith(Skill.AGILITY, 66), bank());
		final RouteStop gate = between(Location.FALADOR_PARK, Location.TAVERLEY, accessWith(Skill.AGILITY, 60), bank());
		assertEquals(rocks.getBreakdown(), Departure.WALK, rocks.getDeparture());
		assertTrue(rocks.getBreakdown() + " / " + gate.getBreakdown(), gate.getLegSeconds() > rocks.getLegSeconds() + 5);
	}

	@Test
	public void gnomeStrongholdToTreeGnomeVillageBySpiritTreeAndElkoy()
	{
		final RouteStop stop = between(Location.GNOME_STRONGHOLD, Location.TREE_GNOME_VILLAGE, access(99), bank());
		assertEquals(stop.getBreakdown(), TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, stop.getMethod());
		assertTrue(stop.describeTravel(), stop.describeTravel().contains("Elkoy"));
	}

	@Test
	public void civitasToKastoriTakesRenuNotAWhistleCharge()
	{
		// Quickest: the Civitas illa Fortis Teleport lands by the city's pad, then Renu; never the whistle
		final Holdings whistle = bank(ItemID.HG_QUETZALWHISTLE_BASIC, 1);
		final RouteStop fastest = between(Location.CIVITAS_ILLA_FORTIS, Location.KASTORI, access(99), whistle);
		assertNotEquals(fastest.getBreakdown(), TravelItem.QUETZAL_WHISTLE, fastest.travelItem());
		assertTrue(fastest.getBreakdown(), fastest.describeTravel().contains("Renu"));

		// Preferring free ways: back to the Hunter Guild's pad on foot, then Renu (no runes, no charge)
		final RunConfig free = new RunConfig().sanitise();
		free.setTravelStyle(TravelStyle.PREFER_FREE);
		final RouteStop pad = RoutePlanner.between(Location.CIVITAS_ILLA_FORTIS, Location.KASTORI, free, access(99),
			whistle, new PohSetup(), AtlasPlayers.with(access(99), whistle));
		assertEquals(pad.getBreakdown(), Departure.RENU, pad.getDeparture());
	}

	@Test
	public void theQuetzalWhistleTakesItsRealWait()
	{
		// Only the whistle: no spells, so no Civitas illa Fortis Teleport and Renu
		final RouteStop stop = between(null, Location.KASTORI, access(1), bank(ItemID.HG_QUETZALWHISTLE_BASIC, 1));
		assertEquals(stop.getBreakdown(), TravelMethod.QUETZAL_KASTORI, stop.getMethod());
		assertEquals(Departure.DIRECT, stop.getDeparture());
		// 13 s for the whistle, not Shortest Path's 4 ticks
		assertTrue(stop.getBreakdown(), stop.getBreakdown().contains(" 13 + "));
		assertTrue(stop.getBreakdown(), stop.getLegSeconds() >= 13 + 8);
	}

	@Test
	public void trollStrongholdRoofWithTheDiaryAnd73Agility()
	{
		final Holdings holdings = bank(ItemID.STRONGHOLD_TELEPORT_BASALT, 5);
		final Set<String> diary = Collections.singleton(
			AccessSnapshot.diaryKey(AchievementDiary.FREMENNIK, AchievementDiary.Tier.HARD));
		final AccessSnapshot both = access(99, diary);
		final RouteStop roof = RoutePlanner.between(null, Location.TROLL_STRONGHOLD, CONFIG, both, holdings, new PohSetup(),
			AtlasPlayers.with(both, holdings, Map.of(FREMENNIK_HARD_VARBIT, 1, MY_ARM_VARBIT, MY_ARM_DONE)));
		assertEquals(roof.getBreakdown(), TravelMethod.STONY_BASALT_ROOF, roof.getMethod());

		// Neither: the basalt lands at the entrance, and the way up is through the stronghold
		final AccessSnapshot neither = accessWith(Skill.AGILITY, 60);
		final RouteStop inside = RoutePlanner.between(null, Location.TROLL_STRONGHOLD, CONFIG, neither, holdings,
			new PohSetup(), AtlasPlayers.with(neither, holdings,
				Map.of(FREMENNIK_HARD_VARBIT, 0, MY_ARM_VARBIT, MY_ARM_DONE)));
		assertNotEquals(inside.getBreakdown(), TravelMethod.STONY_BASALT_ROOF, inside.getMethod());
		assertTrue(inside.getBreakdown(), inside.getLegSeconds() > roof.getLegSeconds() + 5);
	}

	@Test
	public void anItemWithOneChargeIsUsedOnce()
	{
		// A slayer ring with one charge: the Stronghold's slayer cave or the fairy ring by it, not both
		final List<Patch> patches = Arrays.asList(Patch.GNOME_STRONGHOLD_TREE, Patch.FARMING_GUILD_HERB);
		final AccessSnapshot access = access(99);
		final Holdings holdings = bank(ItemID.SLAYER_RING_1, 1, ItemID.DRAMEN_STAFF, 1);
		final Route route = RoutePlanner.plan(patches, CONFIG, access, holdings, new PohSetup(),
			AtlasPlayers.with(access, holdings), null);
		long rings = route.getStops().stream().filter(s -> slayerRing(s)).count();
		assertTrue(describe(route), rings <= 1);
	}

	@Test
	public void aDailyTeleportWithNoUsesLeftIsntPlanned()
	{
		// Ardougne cloak 2: 3 farm teleports a day, all used
		final Map<TravelMethod, Integer> used = new EnumMap<>(TravelMethod.class);
		used.put(TravelMethod.ARDOUGNE_CLOAK_FARM, 3);
		final Holdings holdings = new Holdings(items(ItemID.ARDY_CAPE_MEDIUM, 1), Collections.emptyMap(),
			Collections.emptySet(), true, false, -1, null, used);
		final RouteStop stop = between(null, Location.ARDOUGNE_FARM, access(99), holdings);
		assertNotEquals(stop.getBreakdown(), TravelMethod.ARDOUGNE_CLOAK_FARM, stop.getMethod());
		assertTrue(stop.getBreakdown(), stop.getAtlas() == null || stop.getAtlas().getDaily() == null);

		final RouteStop fresh = between(null, Location.ARDOUGNE_FARM, access(99), bank(ItemID.ARDY_CAPE_MEDIUM, 1));
		assertEquals(fresh.getBreakdown(), TravelMethod.ARDOUGNE_CLOAK_FARM, fresh.getMethod());
	}

	@Test
	public void theFirstLegCanStartBesideTheSpiritTreeThePlayerStandsAt()
	{
		// At the Grand Exchange's spirit tree, with nothing to teleport with
		final AccessSnapshot access = access(1);
		final Holdings holdings = bank();
		final WorldPoint tree = new WorldPoint(3185, 3508, 0);
		final RouteStop here = first(Patch.TREE_GNOME_VILLAGE_FRUIT_TREE, access, holdings, tree);
		assertEquals(here.getBreakdown(), TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, here.getMethod());
		final RouteStop anywhere = first(Patch.TREE_GNOME_VILLAGE_FRUIT_TREE, access, holdings, null);
		assertNotEquals(anywhere.getBreakdown(), TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, anywhere.getMethod());
	}

	@Test
	public void theFirstLegCanBeAWalkFromWhereThePlayerStands()
	{
		// In Falador's east bank, a short walk from Falador Park
		final RouteStop stop = first(Patch.FALADOR_TREE, access(99), bank(ItemID.RING_OF_WEALTH_5, 1),
			new WorldPoint(3013, 3355, 0));
		assertEquals(stop.getBreakdown(), Departure.WALK, stop.getDeparture());
	}

	@Test
	public void aTeleportThePluginNeverListedIsPlanned()
	{
		// Yanille's hops by the Watchtower Teleport, which isn't one of the plugin's own travel methods
		final RouteStop stop = between(null, Location.YANILLE_HOPS, ElzermaAccount.access(), ElzermaAccount.holdings());
		System.out.println("Yanille hops: " + stop.describeTravel() + ", " + stop.getBreakdown());
		assertEquals(stop.getBreakdown(), Departure.ATLAS, stop.getDeparture());
		assertNotNull(stop.getAtlas().getLanding());
	}

	@Test
	public void everyStopHasAWayOrSaysThereIsNone()
	{
		// A fresh mid-level account with a few teleports
		final AccessSnapshot access = access(50, Collections.emptySet(), Quest.values());
		final Holdings holdings = bank(ItemID.JEWL_NECKLACE_OF_SKILLS_4, 1, ItemID.RING_OF_WEALTH_5, 1, ItemID.COINS, 10_000);
		for (Location location : Location.values())
		{
			final RouteStop stop = between(null, location, access, holdings);
			assertNotNull(location.name(), stop.getDeparture());
			assertTrue(location + ": " + stop.getBreakdown(), stop.getDeparture() == Departure.NONE
				|| stop.getLegSeconds() > 0);
		}
	}

	@Test
	public void elzermasFullRunOnTheAtlasAndTheOldTables()
	{
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (ElzermaAccount.STOPS.contains(patch.getLocation()))
			{
				patches.add(patch);
			}
		}
		final AccessSnapshot access = ElzermaAccount.access();
		final Holdings holdings = withCoins(ElzermaAccount.holdings());
		final RouteAtlas atlas = AtlasPlayers.with(access, holdings);
		final Route onAtlas = RoutePlanner.plan(patches, CONFIG, access, holdings, new PohSetup(), atlas, null);
		final Route tables = RoutePlanner.plan(patches, CONFIG, access, holdings, new PohSetup());
		System.out.printf(Locale.ROOT, "Atlas route, travel %.0f s:%n%s", onAtlas.getTravelSeconds(), describe(onAtlas));
		System.out.printf(Locale.ROOT, "Old tables' route, travel %.0f s:%n%s", tables.getTravelSeconds(), describe(tables));
		assertEquals(ElzermaAccount.STOPS.size(), onAtlas.getStops().size());
		for (RouteStop stop : onAtlas.getStops())
		{
			assertTrue(stop.getLocation() + ": " + stop.getBreakdown(), stop.getDeparture() != Departure.NONE
				&& !stop.isNeedsSupplies());
		}
	}

	/** The Fremennik hard diary (the stony basalt's roof teleport and the nexus portal's). */
	private static final int FREMENNIK_HARD_VARBIT = 4493;
	/** My Arm's Big Adventure, done (the basalt's own check; the pretend player's values can't pass all of its checks). */
	private static final int MY_ARM_VARBIT = 6528;
	private static final int MY_ARM_DONE = 207;

	private static boolean slayerRing(RouteStop stop)
	{
		return stop.travelItem() == TravelItem.SLAYER_RING
			|| stop.getDeparture() == Departure.FAIRY_RING_SLAYER_RING
			|| stop.getMethod() == TravelMethod.SLAYER_RING_STRONGHOLD_CAVE;
	}

	private static String describe(Route route)
	{
		final StringBuilder s = new StringBuilder();
		for (RouteStop stop : route.getStops())
		{
			s.append(String.format(Locale.ROOT, "  %s: %s, %.0f s = %s%n", stop.getLocation().getDisplayName(),
				stop.describeTravel(), stop.getLegSeconds(), stop.getBreakdown()));
		}
		return s.toString();
	}

	private static RouteStop between(Location from, Location to, AccessSnapshot access, Holdings holdings)
	{
		return RoutePlanner.between(from, to, CONFIG, access, holdings, new PohSetup(), AtlasPlayers.with(access, holdings));
	}

	/** The first stop of a run of one patch, for a player standing at {@code tile} (or anywhere if null). */
	private static RouteStop first(Patch patch, AccessSnapshot access, Holdings holdings, WorldPoint tile)
	{
		return RoutePlanner.plan(Collections.singletonList(patch), CONFIG, access, holdings, new PohSetup(),
			AtlasPlayers.with(access, holdings), null, null, 0, null, tile).getStops().get(0);
	}

	/** Every quest done but these, every skill at {@code level}, these diaries; fairy rings and spirit trees open. */
	private static AccessSnapshot access(int level, Set<String> diaries, Quest... notDone)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		for (Quest quest : notDone)
		{
			quests.put(quest, QuestState.NOT_STARTED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, level);
		}
		return new AccessSnapshot(true, quests, levels, new HashSet<>(diaries),
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES, Unlock.QUETZAL_KASTORI), null, Spellbook.STANDARD,
			Collections.emptySet());
	}

	private static AccessSnapshot access(int level)
	{
		return access(level, Collections.emptySet());
	}

	/** 99 everywhere but one skill. */
	private static AccessSnapshot accessWith(Skill skill, int level)
	{
		final AccessSnapshot all = access(99);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill s : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(s, s == skill ? level : 99);
		}
		return new AccessSnapshot(true, all.getQuests(), levels, Collections.emptySet(),
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES, Unlock.QUETZAL_KASTORI), null, Spellbook.STANDARD,
			Collections.emptySet());
	}

	/** In the bank: item id, count, item id, count... */
	private static Holdings bank(int... idsAndCounts)
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		for (int i = 0; i < idsAndCounts.length; i += 2)
		{
			bank.put(idsAndCounts[i], idsAndCounts[i + 1]);
		}
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	private static Map<Holdings.Source, Map<Integer, Integer>> items(int id, int count)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, new HashMap<>(Map.of(id, count)));
		return items;
	}

	/** The same holdings with coins in the bank, as any real player has for fares. */
	private static Holdings withCoins(Holdings holdings)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		final Map<Integer, Integer> bank = new HashMap<>(holdings.in(Holdings.Source.BANK));
		bank.put(ItemID.COINS, 1_000_000);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}
}
