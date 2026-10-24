package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The place to pick on Renu's, the whistle's or a balloon's map on the way to a stop (elzerma, 2026-10-18). */
public class MenuPickTest
{
	@Test
	public void thePluginsOwnQuetzalAndBalloonWays()
	{
		assertEquals(List.of(new MenuPick(MenuPick.Menu.WHISTLE, "Kastori")),
			stop(Location.KASTORI, TravelMethod.QUETZAL_KASTORI, Departure.DIRECT).menuPicks());
		assertEquals(List.of(new MenuPick(MenuPick.Menu.RENU, "Hunter Guild")),
			stop(Location.CIVITAS_ILLA_FORTIS, TravelMethod.QUETZAL_HUNTER_GUILD, Departure.RENU).menuPicks());
		assertEquals(List.of(new MenuPick(MenuPick.Menu.RENU, "Hunter Guild")),
			stop(Location.CIVITAS_ILLA_FORTIS, TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT, Departure.DIRECT).menuPicks());
		assertEquals(List.of(new MenuPick(MenuPick.Menu.BALLOON, "Taverley")),
			stop(Location.TAVERLEY, TravelMethod.HOT_AIR_BALLOON_TAVERLEY, Departure.DIRECT).menuPicks());
		assertTrue(stop(Location.CATHERBY, TravelMethod.CAMELOT_TELEPORT, Departure.DIRECT).menuPicks().isEmpty());
		assertTrue(new RouteStop(Location.TAVERLEY, null, Departure.WALK, 20, false).menuPicks().isEmpty());
	}

	@Test
	public void aWayFromTheAtlas()
	{
		// Kastori by the Civitas illa Fortis Teleport, then Renu from the city's pad (with their whistle, its flight is
		// under a second quicker)
		final AccessSnapshot access = ElzermaAccount.access();
		final Holdings holdings = ElzermaAccount.holdingsWithout(ItemID.HG_QUETZALWHISTLE_ENHANCED);
		final RouteStop stop = RoutePlanner.between(null, Location.KASTORI, new RunConfig().sanitise(), access, holdings,
			new PohSetup(), AtlasPlayers.with(access, holdings));
		assertEquals(stop.getBreakdown(), Departure.ATLAS, stop.getDeparture());
		assertEquals(List.of(new MenuPick(MenuPick.Menu.RENU, "Kastori")), stop.menuPicks());
	}

	private static RouteStop stop(Location location, TravelMethod method, Departure departure)
	{
		return new RouteStop(location, method, departure, 30, false, "", null);
	}
}
