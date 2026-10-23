package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.settings.RouteMode;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BankPlannerTest
{
	/** The banks by the stops are the atlas's (AtlasBanks), read once. */
	private static final TravelAtlas ATLAS = TravelAtlas.get();

	private static RouteStop stop(Location location)
	{
		return new RouteStop(location, TravelMethod.LUMBRIDGE_TELEPORT, Departure.DIRECT, 20, false);
	}

	/** Four stops: Farming Guild and Catherby have banks by them, Tree Gnome Village and Troll Stronghold don't. */
	private static final Route ROUTE = new Route(Arrays.asList(stop(Location.TREE_GNOME_VILLAGE),
		stop(Location.FARMING_GUILD), stop(Location.TROLL_STRONGHOLD), stop(Location.CATHERBY)), RouteMode.AUTOPILOT, 80,
		0);

	/** Each stop needs this many slots. */
	private static BankPlanner.Slots each(int perStop)
	{
		return (a, b) -> (b - a) * perStop;
	}

	private static BankPlanner.Result place(BankPlanner.Slots slots, BankTeleport teleport)
	{
		return BankPlanner.place(ROUTE, slots, 28, teleport, (from, to) -> 0.0,
			to -> new RouteStop(to, TravelMethod.LUMBRIDGE_TELEPORT, Departure.DIRECT, 25, false));
	}

	@Test
	public void noVisitWhenItAllFits()
	{
		assertTrue(place(each(7), null).getVisits().isEmpty());
	}

	@Test
	public void banksAtTheStopWithABankNearby()
	{
		// 4 x 10 slots: two parts of two stops; only the Farming Guild (stop 2) has a bank to split at
		final BankPlanner.Result result = place(each(10), null);
		assertEquals(1, result.getVisits().size());
		assertEquals(Location.FARMING_GUILD, result.getVisits().get(0).getLocation());
		assertNull(result.getVisits().get(0).getTeleport());
	}

	@Test
	public void asManyVisitsAsNeeded()
	{
		// 4 x 20 slots: every stop is its own inventory, so three visits; Tree Gnome Village and the Troll Stronghold
		// have no bank
		final BankPlanner.Result result = place(each(20), BankTeleport.RING_OF_DUELING);
		assertEquals(3, result.getVisits().size());
		assertEquals(BankTeleport.RING_OF_DUELING, result.getVisits().get(0).getTeleport());
	}

	@Test
	public void nothingFitsWithoutAWay()
	{
		// A stop that can't fit even alone
		assertNull(place(each(30), BankTeleport.RING_OF_DUELING));
		// Every stretch needs a bank where none is near and there's no teleport
		assertNull(BankPlanner.place(new Route(Arrays.asList(stop(Location.TREE_GNOME_VILLAGE),
			stop(Location.TROLL_STRONGHOLD)), RouteMode.AUTOPILOT, 40, 0), each(20), 28, null, (from, to) -> 0.0,
			to -> stop(to)));
	}

	@Test
	public void bankingByTeleportSetsOffFromTheBank()
	{
		final List<RouteStop> stops = place(each(20), BankTeleport.RING_OF_DUELING).getRoute().getStops();
		// The leg after a teleport to the bank is the leg from anywhere (25 s), not from the stop before
		assertEquals(25, stops.get(1).getLegSeconds(), 1e-9);
	}

	// "Bank at the Farming Guild" (issue #8)

	/**
	 * The guild for parts with any of these stops, which plant seeds only the seed vault has: there by a skills
	 * necklace in {@code toSeconds}, and on from it in 20 s.
	 */
	private static BankPlanner.Guild guild(double toSeconds, int... needVault)
	{
		return new BankPlanner.Guild()
		{
			@Override
			public boolean needsVault(int from, int to)
			{
				for (int stop : needVault)
				{
					if (stop >= from && stop < to)
					{
						return true;
					}
				}
				return false;
			}

			@Override
			public RouteStop to(Location from)
			{
				return new RouteStop(Location.FARMING_GUILD, TravelMethod.SKILLS_NECKLACE_FARMING_GUILD, Departure.DIRECT,
					toSeconds, false);
			}

			@Override
			public RouteStop from(Location to)
			{
				return new RouteStop(to, TravelMethod.LUMBRIDGE_TELEPORT, Departure.DIRECT, 20, false);
			}
		};
	}

	private static BankPlanner.Result place(BankPlanner.Slots slots, BankTeleport teleport, BankPlanner.Guild guild)
	{
		return BankPlanner.place(ROUTE, slots, 28, teleport, (from, to) -> 0.0,
			to -> new RouteStop(to, TravelMethod.LUMBRIDGE_TELEPORT, Departure.DIRECT, 25, false), guild);
	}

	@Test
	public void withoutVaultSeedsTheGuildChangesNothing()
	{
		// A slow way to the guild and no part needing the vault: the same visits as banking anywhere
		assertEquals(place(each(10), null).getVisits(), place(each(10), null, guild(500)).getVisits());
		assertEquals(place(each(20), BankTeleport.RING_OF_DUELING).getVisits(),
			place(each(20), BankTeleport.RING_OF_DUELING, guild(500)).getVisits());
	}

	@Test
	public void aPartNeedingTheVaultBanksAtTheGuildsOwnStop()
	{
		// The Troll Stronghold and Catherby (stops 3 and 4) need the vault: the visit after the guild's stop is there
		// anyway
		final BankPlanner.Result result = place(each(10), null, guild(500, 2, 3));
		assertEquals(1, result.getVisits().size());
		final BankPlanner.Visit visit = result.getVisits().get(0);
		assertEquals(Location.FARMING_GUILD, visit.getLocation());
		assertNull(visit.getTrip());
		assertTrue(visit.isAtGuild());
	}

	@Test
	public void otherwiseItsATripToTheGuildAndOnFromThere()
	{
		// Every stop is its own inventory; Catherby (stop 4) needs the vault, so the visit after the Troll Stronghold is
		// at the guild even though the trip is slower than the ring of dueling
		final BankPlanner.Result result = place(each(20), BankTeleport.RING_OF_DUELING, guild(500, 3));
		assertEquals(3, result.getVisits().size());
		final BankPlanner.Visit visit = result.getVisits().get(2);
		assertEquals(2, visit.getAfterStop());
		assertEquals(TravelMethod.SKILLS_NECKLACE_FARMING_GUILD, visit.getTrip().getMethod());
		assertTrue(visit.isAtGuild());
		// The leg to Catherby sets off from the guild (20 s), not from anywhere (25 s)
		assertEquals(20, result.getRoute().getStops().get(3).getLegSeconds(), 1e-9);
		// The earlier visits are the ring of dueling as before
		assertEquals(BankTeleport.RING_OF_DUELING, result.getVisits().get(0).getTeleport());
	}

	@Test
	public void theFirstPartsVaultSeedsComeBeforeTheRun()
	{
		// Tree Gnome Village (stop 1) needs the vault: that's gathered before the run starts, so it doesn't change where
		// to bank
		assertEquals(place(each(10), null).getVisits(), place(each(10), null, guild(500, 0)).getVisits());
	}
}
