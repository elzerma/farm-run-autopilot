package com.farmrunautopilot.data.travel.atlas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Location;
import java.util.EnumSet;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Each stop's bank from the travel atlas (docs/plans/travel-rework.md, T3's last step: the old bank tables went). */
public class AtlasBanksTest
{
	/** Read first: AtlasBanks only uses an atlas that has been read. */
	private static final TravelAtlas ATLAS = TravelAtlas.get();

	@Test
	public void everyStopTheOldTablesBankedAtStillHasABank()
	{
		for (Location location : EnumSet.of(Location.VARROCK, Location.FALADOR_PARK, Location.GNOME_STRONGHOLD,
			Location.FARMING_GUILD, Location.NEMUS_RETREAT, Location.CATHERBY, Location.LLETYA, Location.PORT_PHASMATYS,
			Location.CIVITAS_ILLA_FORTIS, Location.SEERS_VILLAGE_HOPS, Location.YANILLE_HOPS, Location.ALDARIN_HOPS,
			Location.PRIFDDINAS))
		{
			assertNotNull(location.name(), AtlasBanks.near(location));
		}
	}

	@Test
	public void stopsWithNoBankByThem()
	{
		// Brimhaven's, Kastori's and Harmony Island's nearest banks are across the water by boat; the north Lumbridge
		// hops patch's is further than Port Phasmatys's
		for (Location location : EnumSet.of(Location.TREE_GNOME_VILLAGE, Location.BRIMHAVEN, Location.KASTORI,
			Location.TROLL_STRONGHOLD, Location.HARMONY_ISLAND, Location.WEISS, Location.LUMBRIDGE_HOPS))
		{
			assertNull(location.name(), AtlasBanks.near(location));
		}
	}

	@Test
	public void theStrongholdsUpstairsBankIsTheOneEntryByHand()
	{
		// Shortest Path's map has no way up to it, so the atlas has no bank by the Stronghold
		final AtlasBanks.Bank bank = AtlasBanks.near(Location.GNOME_STRONGHOLD);
		assertEquals(new WorldPoint(2445, 3424, 1), bank.getPoint());
		assertEquals(10, bank.getTiles());
	}

	@Test
	public void banksAreTheAtlasesOwn()
	{
		// Civitas illa Fortis banks in the city, nearer than the Hunter Guild's bank the old table had (89 tiles)
		final AtlasBanks.Bank civitas = AtlasBanks.near(Location.CIVITAS_ILLA_FORTIS);
		assertEquals(new WorldPoint(1648, 3118, 0), civitas.getPoint());
		assertTrue(civitas.getTiles() + " tiles", civitas.getTiles() < 89);
		// The Farming Guild's chest is by its patches, timed from the furthest (the cactus, since sprint 1b)
		assertTrue(AtlasBanks.near(Location.FARMING_GUILD).getSeconds() + " s", AtlasBanks.near(Location.FARMING_GUILD).getSeconds() < 20);
		// Stops the old table had none for: Hosidius, the Falador farm and Lumbridge (Draynor's), the Ardougne farm
		// (north Ardougne's) and Taverley (Falador's west bank)
		for (Location location : EnumSet.of(Location.HOSIDIUS, Location.FALADOR_FARM, Location.LUMBRIDGE,
			Location.ARDOUGNE_FARM, Location.TAVERLEY))
		{
			assertNotNull(location.name(), AtlasBanks.near(location));
		}
		// No further than Port Phasmatys's, the longest walk the old table had
		for (Location location : Location.values())
		{
			final AtlasBanks.Bank bank = AtlasBanks.near(location);
			assertTrue(location.name(), bank == null || bank.getTiles() <= AtlasBanks.NEAR_TILES);
		}
		assertEquals(116, AtlasBanks.near(Location.PORT_PHASMATYS).getTiles());
	}
}
