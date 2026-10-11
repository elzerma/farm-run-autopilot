package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.RouteMode;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BankPlannerTest
{
	private static RouteStop stop(Location location)
	{
		return new RouteStop(location, TravelMethod.LUMBRIDGE_TELEPORT, Departure.DIRECT, 20, false);
	}

	/** Four stops: Farming Guild and Catherby have banks nearby, Taverley and Lumbridge don't. */
	private static final Route ROUTE = new Route(Arrays.asList(stop(Location.TAVERLEY), stop(Location.FARMING_GUILD),
		stop(Location.LUMBRIDGE), stop(Location.CATHERBY)), RouteMode.AUTOPILOT, 80, 0);

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
		// 4 x 20 slots: every stop is its own inventory, so three visits; Taverley and Lumbridge have no bank
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
		assertNull(BankPlanner.place(new Route(Arrays.asList(stop(Location.TAVERLEY), stop(Location.LUMBRIDGE)),
			RouteMode.AUTOPILOT, 40, 0), each(20), 28, null, (from, to) -> 0.0, to -> stop(to)));
	}

	@Test
	public void bankingByTeleportSetsOffFromTheBank()
	{
		final List<RouteStop> stops = place(each(20), BankTeleport.RING_OF_DUELING).getRoute().getStops();
		// The leg after a teleport to the bank is the leg from anywhere (25 s), not from the stop before
		assertEquals(25, stops.get(1).getLegSeconds(), 1e-9);
	}
}
