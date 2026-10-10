package com.farmrunautopilot.route;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.MetaOrder;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
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
		assertEquals("Slayer ring (Fremennik Slayer Dungeon), ring AJR: Fairy ring CIR", stop.describeTravel());
	}

	@Test
	public void preferWalkingPairsFaladorParkAndTaverley()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.VARROCK);
		final List<Patch> patches = Arrays.asList(Patch.VARROCK_TREE, Patch.FALADOR_TREE, Patch.TAVERLEY_TREE);
		final Route walking = RoutePlanner.plan(patches, config, AccessSnapshot.UNKNOWN, Holdings.EMPTY, new PohSetup());
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
}
