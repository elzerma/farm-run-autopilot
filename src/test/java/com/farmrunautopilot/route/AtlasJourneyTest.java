package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/** Journeys written from the travel atlas's steps for every leg, the plugin's own methods included (sprint T5). */
public class AtlasJourneyTest
{
	@Test
	public void aPlaceInTheMenuIsTheOption()
	{
		final TravelAtlas.Menu barnaby = TravelAtlas.menu("Brimhaven Captain Barnaby 8763", "Brimhaven");
		assertEquals("Brimhaven", barnaby.getOption());
		assertEquals("Captain Barnaby", barnaby.getTarget());
		assertEquals(8763, barnaby.getId());
		assertTrue(barnaby.isPlace());

		// A place of two words isn't split at the first
		final TravelAtlas.Menu veos = TravelAtlas.menu("Land's End Veos 8630", "Land's End");
		assertEquals("Land's End", veos.getOption());
		assertEquals("Veos", veos.getTarget());
	}

	@Test
	public void anActionInTheMenuIsTheFirstWord()
	{
		final TravelAtlas.Menu primio = TravelAtlas.menu("Travel Primio 12888", "Civitas illa Fortis");
		assertEquals("Travel", primio.getOption());
		assertEquals("Primio", primio.getTarget());
		assertFalse(primio.isPlace());

		// A name that's the whole right-click, or starts with an action, isn't taken as a place
		final TravelAtlas.Menu stile = TravelAtlas.menu("Climb-over Stile 7527", "Climb-over Stile");
		assertEquals("Climb-over", stile.getOption());
		assertEquals("Stile", stile.getTarget());
		final TravelAtlas.Menu rowboat = TravelAtlas.menu("Travel Rowboat 57272", "Travel Rowboat");
		assertEquals("Travel", rowboat.getOption());
		assertEquals("Rowboat", rowboat.getTarget());
		assertEquals(57272, rowboat.getId());
	}

	@Test
	public void theArdougneBoatComesFromTheAtlas()
	{
		// The plugin's own method, with no hand-written journey left: the boat and its fare are the atlas's
		final RouteStop brimhaven = stop(Location.BRIMHAVEN, Patch.BRIMHAVEN_FRUIT_TREE);
		if (brimhaven.getMethod() != TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT)
		{
			return;
		}
		final List<JourneyStep> rides = brimhaven.rides();
		assertEquals(rides.toString(), 1, rides.size());
		assertEquals("Captain Barnaby", rides.get(0).getNpc());
		assertTrue(rides.get(0).getText(), rides.get(0).getText().contains("(30 coins)"));
		assertTrue(brimhaven.describeTravel(), brimhaven.describeTravel().startsWith("Ardougne Teleport, then "));
	}

	@Test
	public void everyRideOnTheRouteSaysWhatToDo()
	{
		final RunConfig config = new RunConfig().sanitise();
		final Route route = RoutePlanner.plan(ElzermaAccount.patches(PatchType.HERB, PatchType.TREE, PatchType.FRUIT_TREE,
			PatchType.ALLOTMENT, PatchType.FLOWER, PatchType.HOPS), config, ElzermaAccount.access(),
			ElzermaAccount.holdings(), new PohSetup(), ElzermaAccount.atlas(), null);
		for (RouteStop stop : route.getStops())
		{
			for (JourneyStep step : stop.rides())
			{
				assertFalse(stop.getLocation().name(), step.getText() == null || step.getText().isEmpty());
				assertFalse(stop.getLocation().name(), step.getBrief() == null || step.getBrief().isEmpty());
			}
			for (JourneyStep step : stop.directions())
			{
				assertFalse(stop.getLocation().name(), step.getText().isEmpty());
			}
		}
	}

	@Test
	public void intoTheHouseByATreeThenOutOfItsPortal()
	{
		assertEquals(Collections.singletonList(JourneyStep.LEAVE_HOUSE), new RouteStop(Location.HOSIDIUS,
			TravelMethod.HOUSE_PORTAL_HOSIDIUS, Departure.TREE_HOUSE_PORTAL, 30, false).rides());
	}

	/** elzerma's leg to a stop with only this patch in the run, from anywhere. */
	private static RouteStop stop(Location location, Patch patch)
	{
		final RunConfig config = new RunConfig().sanitise();
		final Route route = RoutePlanner.plan(Collections.singletonList(patch), config, ElzermaAccount.access(),
			ElzermaAccount.holdings(), new PohSetup(), ElzermaAccount.atlas(), null);
		return route.getStops().stream().filter(s -> s.getLocation() == location).collect(Collectors.toList()).get(0);
	}
}
