package com.farmrunautopilot.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.MetaOrder;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.TravelStyle;
import com.farmrunautopilot.supply.Holdings;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class RoutePlannerTest
{
	private static RoutePlanner.Leg leg(double seconds)
	{
		return new RoutePlanner.Leg(null, Departure.DIRECT, seconds, false);
	}

	/** Asymmetric costs where the cheap tour is 0 -> 2 -> 1 -> 3. */
	private static RoutePlanner.Leg[][] legs()
	{
		final double[][] c = {
			{0, 50, 1, 50},
			{50, 0, 50, 1},
			{50, 1, 0, 50},
			{1, 50, 50, 0},
		};
		final RoutePlanner.Leg[][] legs = new RoutePlanner.Leg[4][4];
		for (int i = 0; i < 4; i++)
		{
			for (int j = 0; j < 4; j++)
			{
				legs[i][j] = leg(c[i][j]);
			}
		}
		return legs;
	}

	private static RoutePlanner.Leg[] first(double... seconds)
	{
		return Arrays.stream(seconds).mapToObj(RoutePlannerTest::leg).toArray(RoutePlanner.Leg[]::new);
	}

	@Test
	public void exactOrderFindsTheCheapestTour()
	{
		final int[] order = RoutePlanner.exactOrder(first(1, 9, 9, 9), legs(), new double[4], -1);
		assertArrayEquals(new int[]{0, 2, 1, 3}, order);
	}

	@Test
	public void exactOrderRespectsTheStart()
	{
		final int[] order = RoutePlanner.exactOrder(first(1, 1, 1, 1), legs(), new double[4], 1);
		assertEquals(1, order[0]);
		assertArrayEquals(new int[]{1, 3, 0, 2}, order);
	}

	@Test
	public void nearestNeighbourMatchesExactOnASmallCase()
	{
		final int[] order = RoutePlanner.nearestNeighbourThenImprove(first(1, 9, 9, 9), legs(), new double[4], -1);
		assertArrayEquals(new int[]{0, 2, 1, 3}, order);
	}

	@Test
	public void endNearBankPullsTheBankLastWhenItCostsNothingElse()
	{
		final RoutePlanner.Leg[][] flat = new RoutePlanner.Leg[3][3];
		for (int i = 0; i < 3; i++)
		{
			for (int j = 0; j < 3; j++)
			{
				flat[i][j] = leg(5);
			}
		}
		final int[] order = RoutePlanner.exactOrder(first(5, 5, 5), flat, new double[]{30, 0, 30}, 0);
		assertEquals(1, order[2]);
	}

	@Test
	public void fixedOrderFollowsPreferenceThenAppendsTheRest()
	{
		final List<Location> stops = Arrays.asList(Location.CATHERBY, Location.WEISS, Location.LUMBRIDGE);
		final int[] order = RoutePlanner.fixedOrder(stops, Arrays.asList(Location.LUMBRIDGE, Location.CATHERBY), -1);
		assertArrayEquals(new int[]{2, 0, 1}, order);
	}

	@Test
	public void combinedMetaOrderHasEveryLocationOnce()
	{
		final List<Location> combined = MetaOrder.combined();
		final Set<Location> unique = EnumSet.noneOf(Location.class);
		unique.addAll(combined);
		assertEquals(combined.size(), unique.size());
		assertEquals(EnumSet.allOf(Location.class), unique);
		// Shared locations sit where they first appear: the Gnome Stronghold is in the tree order.
		assertTrue(combined.indexOf(Location.GNOME_STRONGHOLD) < combined.indexOf(Location.TREE_GNOME_VILLAGE));
	}

	@Test
	public void walksWhenNothingFasterIsUsableAndTakesTheSpiritTreeOnToTreeGnomeVillage()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.FALADOR_PARK);
		// No teleports owned and too low Magic to cast any: walking is the only way to the farm.
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, 1);
		levels.put(Skill.FARMING, 99);
		final AccessSnapshot noTeleports = new AccessSnapshot(true, Collections.emptyMap(), levels,
			Collections.emptySet(), Collections.emptySet(), null);
		final List<Patch> patches = Arrays.asList(Patch.FALADOR_TREE, Patch.FALADOR_HERB);
		final Route route = RoutePlanner.plan(patches, config, noTeleports, Holdings.EMPTY, new PohSetup());
		assertEquals(Location.FALADOR_PARK, route.getStops().get(0).getLocation());
		assertEquals(Departure.WALK, route.getStops().get(1).getDeparture());

		config.setStartLocation(Location.GNOME_STRONGHOLD);
		final Route gnomes = RoutePlanner.plan(
			Arrays.asList(Patch.GNOME_STRONGHOLD_FRUIT_TREE, Patch.TREE_GNOME_VILLAGE_FRUIT_TREE), config,
			AccessSnapshot.UNKNOWN, Holdings.EMPTY, new PohSetup());
		final RouteStop village = gnomes.getStops().get(1);
		assertEquals(Location.TREE_GNOME_VILLAGE, village.getLocation());
		assertEquals(TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, village.getMethod());
	}

	@Test
	public void ringByAStopIsAsFarAsArrivingThereByRing()
	{
		assertEquals(Integer.valueOf(28), RoutePlanner.ringWalkTiles(Location.FARMING_GUILD));
		assertEquals(null, RoutePlanner.ringWalkTiles(Location.TAVERLEY));
	}

	@Test
	public void everyWayToAFairyRingHasADeparture()
	{
		for (FairyRingAccess way : FairyRingAccess.values())
		{
			assertEquals(way, Departure.of(way).getFairyRingAccess());
		}
		final RouteStop stop = new RouteStop(Location.FARMING_GUILD, TravelMethod.FAIRY_RING_CIR,
			Departure.FAIRY_RING_SLAYER_RING, 30, false);
		assertEquals("Fairy ring CIR (from AJR by slayer ring)", stop.describeTravel());
	}

	@Test
	public void preferWalkingPairsFaladorParkAndTaverley()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.VARROCK);
		final List<Patch> patches = Arrays.asList(Patch.VARROCK_TREE, Patch.FALADOR_TREE, Patch.TAVERLEY_TREE);
		// 66 Agility: over the rocks north-west of Falador
		final AccessSnapshot agile = new AccessSnapshot(true, Collections.emptyMap(),
			Collections.singletonMap(Skill.AGILITY, 66), Collections.emptySet(), Collections.emptySet(), null);
		final Route walking = RoutePlanner.plan(patches, config, agile, Holdings.EMPTY, new PohSetup());
		// One teleport into the pair, then a walk between them
		assertEquals(Departure.WALK, walking.getStops().get(2).getDeparture());

		config.setPreferWalking(false);
		final Route fastest = RoutePlanner.plan(patches, config, AccessSnapshot.UNKNOWN, Holdings.EMPTY, new PohSetup());
		assertEquals(Departure.DIRECT, fastest.getStops().get(2).getDeparture());
	}

	@Test
	public void metaModeUsesTheWikiOrderAndStartsAtTheStart()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setRouteMode(RouteMode.META);
		config.setStartLocation(Location.FARMING_GUILD);
		final List<Patch> patches = Arrays.asList(Patch.TAVERLEY_TREE, Patch.LUMBRIDGE_TREE, Patch.FARMING_GUILD_TREE);
		final Route route = RoutePlanner.plan(patches, config, AccessSnapshot.UNKNOWN, Holdings.EMPTY, new PohSetup());
		assertEquals(Location.FARMING_GUILD, route.getStops().get(0).getLocation());
		assertEquals(Location.LUMBRIDGE, route.getStops().get(1).getLocation());
		assertEquals(Location.TAVERLEY, route.getStops().get(2).getLocation());
	}

	@Test
	public void ownOrderFollowsTheSavedOrder()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setRouteMode(RouteMode.OFF);
		config.setCustomOrder(Arrays.asList(Location.TAVERLEY, Location.LUMBRIDGE));
		final List<Patch> patches = Arrays.asList(Patch.TAVERLEY_TREE, Patch.LUMBRIDGE_TREE, Patch.FARMING_GUILD_TREE);
		final Route route = RoutePlanner.plan(patches, config, AccessSnapshot.UNKNOWN, Holdings.EMPTY, new PohSetup());
		assertEquals(Location.TAVERLEY, route.getStops().get(0).getLocation());
		assertEquals(Location.LUMBRIDGE, route.getStops().get(1).getLocation());
		assertEquals(Location.FARMING_GUILD, route.getStops().get(2).getLocation());
	}

	private static long stopsUsing(Holdings holdings, boolean keepLast)
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.LUMBRIDGE);
		config.setKeepLastCharge(keepLast);
		// No spells to cast, so the skills necklace is the way to the Farming Guild, Falador Park and Varrock
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, 1);
		levels.put(Skill.FARMING, 99);
		final AccessSnapshot access = new AccessSnapshot(true, Collections.emptyMap(), levels,
			Collections.emptySet(), Collections.emptySet(), null);
		final Route route = RoutePlanner.plan(Arrays.asList(Patch.FARMING_GUILD_TREE, Patch.FALADOR_TREE,
			Patch.VARROCK_TREE), config, access, holdings, new PohSetup());
		return route.getStops().stream()
			.filter(s -> s.getMethod() != null && s.getMethod().getItem() == TravelItem.SKILLS_NECKLACE
				&& s.getDeparture() == Departure.DIRECT && !s.isNeedsSupplies())
			.count();
	}

	private static Holdings bank(int itemId, int count)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, Collections.singletonMap(itemId, count));
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	@Test
	public void chargesArePooledAcrossTheRun()
	{
		// One charge covers one stop, not every stop the necklace could reach
		assertEquals(1, stopsUsing(bank(ItemID.JEWL_NECKLACE_OF_SKILLS_1, 1), false));
		// Two necklaces (1) give two charges
		assertTrue(stopsUsing(bank(ItemID.JEWL_NECKLACE_OF_SKILLS_1, 2), false) <= 2);
		// Keeping the last charge of rechargeable jewellery: a single (1) isn't used at all
		assertEquals(0, stopsUsing(bank(ItemID.JEWL_NECKLACE_OF_SKILLS_1, 1), true));
	}

	@Test
	public void chargesHeldAddUpAcrossPieces()
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		final Map<Integer, Integer> bank = new java.util.HashMap<>();
		bank.put(ItemID.NECKLACE_OF_MINIGAMES_8, 1);
		bank.put(ItemID.NECKLACE_OF_MINIGAMES_3, 2);
		items.put(Holdings.Source.BANK, bank);
		final Holdings holdings = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
		assertEquals(Integer.valueOf(14), ChargeBudget.held(TravelItem.GAMES_NECKLACE, holdings));
		// An eternal version never runs out
		assertEquals(null, ChargeBudget.held(TravelItem.SLAYER_RING, bank(ItemID.SLAYER_RING_ETERNAL, 1)));
	}

	@Test
	public void dailyLimitsCountTodaysUses()
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.WORN, Collections.singletonMap(ItemID.ARDY_CAPE_MEDIUM, 1));
		// Cloak 2: three a day, two used
		final Holdings twoUsed = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false, -1,
			null, Collections.singletonMap(TravelMethod.ARDOUGNE_CLOAK_FARM, 2));
		assertEquals(Integer.valueOf(1), ChargeBudget.leftToday(TravelMethod.ARDOUGNE_CLOAK_FARM, twoUsed));
		final Holdings allUsed = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false, -1,
			null, Collections.singletonMap(TravelMethod.ARDOUGNE_CLOAK_FARM, 3));
		assertEquals(Integer.valueOf(0), ChargeBudget.leftToday(TravelMethod.ARDOUGNE_CLOAK_FARM, allUsed));
		// Cloak 4 is unlimited
		assertEquals(null, ChargeBudget.leftToday(TravelMethod.ARDOUGNE_CLOAK_FARM,
			bank(ItemID.ARDY_CAPE_ELITE, 1)));
	}

	@Test
	public void stylesPenaliseWhatTheyAvoid()
	{
		assertEquals(0, RoutePlanner.stylePenalty(TravelStyle.FASTEST, RoutePlanner.Cost.CHARGES), 1e-9);
		assertEquals(0, RoutePlanner.stylePenalty(TravelStyle.PREFER_FREE, RoutePlanner.Cost.FREE), 1e-9);
		assertTrue(RoutePlanner.stylePenalty(TravelStyle.PREFER_FREE, RoutePlanner.Cost.TABLET) > 0);
		assertTrue(RoutePlanner.stylePenalty(TravelStyle.SAVE_CHARGES, RoutePlanner.Cost.CHARGES) > 0);
		assertEquals(0, RoutePlanner.stylePenalty(TravelStyle.SAVE_CHARGES, RoutePlanner.Cost.RUNES), 1e-9);
		// Fewest items: a tablet per stop costs more than shared runes or one house tablet
		assertTrue(RoutePlanner.stylePenalty(TravelStyle.FEWEST_ITEMS, RoutePlanner.Cost.TABLET)
			> RoutePlanner.stylePenalty(TravelStyle.FEWEST_ITEMS, RoutePlanner.Cost.HOUSE));
	}

	@Test
	public void saveChargesWalksOrCastsInsteadOfANecklace()
	{
		// Skills necklace (6) and 99 Magic on the standard book: Fastest may take the necklace to Falador Park;
		// Save charges should cast Falador Teleport instead when it's only a little slower
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.LUMBRIDGE);
		config.setPreferWalking(false);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, 99);
		levels.put(Skill.FARMING, 99);
		final AccessSnapshot access = new AccessSnapshot(true, Collections.emptyMap(), levels,
			Collections.emptySet(), Collections.emptySet(), null);
		final Holdings necklace = bank(ItemID.JEWL_NECKLACE_OF_SKILLS_6, 1);
		config.setTravelStyle(TravelStyle.SAVE_CHARGES);
		final RouteStop stop = RoutePlanner.plan(Collections.singletonList(Patch.FALADOR_TREE), config, access,
			necklace, new PohSetup()).getStops().get(0);
		assertTrue(stop.getMethod() + "", stop.getMethod() == null || stop.getMethod().getItem() != TravelItem.SKILLS_NECKLACE);
	}
}
