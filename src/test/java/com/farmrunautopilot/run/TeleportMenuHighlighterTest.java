package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.RouteStop;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The option a leg needs on its teleport item's right-click menu (elzerma, 2026-10-18). */
public class TeleportMenuHighlighterTest
{
	private static final int NECKLACE = ItemID.JEWL_NECKLACE_OF_SKILLS_4;

	@Test
	public void theOptionNamingThePlace()
	{
		assertEquals(1, TeleportMenuHighlighter.pick(new String[]{"Remove", "<col=ffffff>Farming Guild"},
			new int[]{NECKLACE, NECKLACE}, Set.of(NECKLACE), "Farming Guild"));
		// The Ardougne cloak's "Ardougne Farm" for the plugin's "Ardougne cloak (farm)"
		assertEquals(0, TeleportMenuHighlighter.pick(new String[]{"Ardougne Farm"}, new int[]{ItemID.ARDY_CAPE_MEDIUM},
			Set.of(ItemID.ARDY_CAPE_MEDIUM), "farm"));
	}

	@Test
	public void rubWhenThePlacesAreInAList()
	{
		assertEquals(1, TeleportMenuHighlighter.pick(new String[]{"Drop", "Rub"}, new int[]{NECKLACE, NECKLACE},
			Set.of(NECKLACE), "Farming Guild"));
	}

	@Test
	public void notAnotherItemsMenu()
	{
		assertEquals(-1, TeleportMenuHighlighter.pick(new String[]{"Rub"}, new int[]{ItemID.AMULET_OF_GLORY_4},
			Set.of(NECKLACE), "Farming Guild"));
	}

	@Test
	public void thePluginsOwnMethodsNameTheirOption()
	{
		assertEquals("Farming Guild", new RouteStop(Location.FARMING_GUILD, TravelMethod.SKILLS_NECKLACE_FARMING_GUILD,
			Departure.DIRECT, 20, false).teleportOption());
		assertEquals("Fremennik Slayer Dungeon", new RouteStop(Location.FARMING_GUILD, TravelMethod.FAIRY_RING_CIR,
			Departure.FAIRY_RING_SLAYER_RING, 40, false).teleportOption());
		assertNull(new RouteStop(Location.CATHERBY, TravelMethod.CAMELOT_TELEPORT, Departure.DIRECT, 20, false)
			.teleportOption());
	}
}
