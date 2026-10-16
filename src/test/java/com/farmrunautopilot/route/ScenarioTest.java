package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Sean's expectations for his own runs (docs/REBUILD.md, Sprint 0), on an account like his. The rebuilt
 * planner must pass all of these; ignored ones wait for the sprint named in their reason.
 */
public class ScenarioTest
{
	private static final PatchType[] ALL = {PatchType.HERB, PatchType.TREE, PatchType.FRUIT_TREE};

	private static List<RouteStop> plan(List<Patch> patches, Location near, int nearTiles)
	{
		return RoutePlanner.plan(patches, new RunConfig().sanitise(), SeanAccount.access(), SeanAccount.holdings(),
			new PohSetup(), LearnedTimes.NONE, null, near, nearTiles).getStops();
	}

	private static int indexOf(List<RouteStop> stops, Location location)
	{
		for (int i = 0; i < stops.size(); i++)
		{
			if (stops.get(i).getLocation() == location)
			{
				return i;
			}
		}
		return -1;
	}

	private static String describe(List<RouteStop> stops)
	{
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < stops.size(); i++)
		{
			final RouteStop stop = stops.get(i);
			b.append(i + 1).append(". ").append(stop.getLocation()).append(" via ").append(stop.describeTravel())
				.append(" (").append(Math.round(stop.getLegSeconds())).append(" s)\n");
		}
		return b.toString();
	}

	/** Gnome Stronghold, then straight to Tree Gnome Village by the Stronghold's spirit tree. */
	private static void assertStrongholdChain(List<RouteStop> stops)
	{
		final int stronghold = indexOf(stops, Location.GNOME_STRONGHOLD);
		assertTrue(describe(stops), stronghold >= 0 && stronghold + 1 < stops.size());
		assertEquals(describe(stops), Location.TREE_GNOME_VILLAGE, stops.get(stronghold + 1).getLocation());
		assertEquals(describe(stops), TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, stops.get(stronghold + 1).getMethod());
	}

	// Starting

	@Test
	public void startsAtTheStopYouAreNear()
	{
		// Falador west bank, 57 tiles from the Falador Park patch
		final List<RouteStop> stops = plan(SeanAccount.patches(ALL), Location.FALADOR_PARK, 57);
		assertEquals(describe(stops), Location.FALADOR_PARK, stops.get(0).getLocation());
	}

	@Test
	public void startsAtTheStrongholdFromTheGrandTree()
	{
		final List<RouteStop> stops = plan(SeanAccount.patches(ALL), Location.GNOME_STRONGHOLD, 50);
		assertEquals(describe(stops), Location.GNOME_STRONGHOLD, stops.get(0).getLocation());
		assertStrongholdChain(stops);
	}

	// Chains a player wouldn't think of

	@Test
	public void fullRunChainsStrongholdToTreeGnomeVillage()
	{
		assertStrongholdChain(plan(SeanAccount.patches(ALL), null, 0));
	}

	@Test
	public void fullRunNearFaladorStillChainsStrongholdToTreeGnomeVillage()
	{
		assertStrongholdChain(plan(SeanAccount.patches(ALL), Location.FALADOR_PARK, 57));
	}

	@Test
	public void faladorParkWalksToTaverleyAndTheFarmIsByExplorersRing()
	{
		final List<RouteStop> stops = plan(SeanAccount.patches(ALL), null, 0);
		final int park = indexOf(stops, Location.FALADOR_PARK);
		final int taverley = indexOf(stops, Location.TAVERLEY);
		assertEquals(describe(stops), 1, Math.abs(park - taverley));
		assertEquals(describe(stops), Departure.WALK, stops.get(Math.max(park, taverley)).getDeparture());
		assertEquals(describe(stops), TravelMethod.EXPLORERS_RING_CABBAGE_PATCH,
			stops.get(indexOf(stops, Location.FALADOR_FARM)).getMethod());
	}

	@Test
	public void theWhistleBeatsTheCivitasTeleportToTheHunterGuild()
	{
		// Sean, 2026-10-15: on a herb run the Civitas illa Fortis Teleport (then Renu) was picked over his whistle,
		// which is faster: the charge penalty is only a tie-breaker
		final List<Patch> herbs = Arrays.asList(Patch.CIVITAS_HERB, Patch.CATHERBY_HERB, Patch.ARDOUGNE_HERB);
		final List<RouteStop> stops = RoutePlanner.plan(herbs, new RunConfig().sanitise(), SeanAccount.access(),
			SeanAccount.holdings(), new PohSetup(), LearnedTimes.NONE, null, null, 0).getStops();
		assertEquals(describe(stops), TravelMethod.QUETZAL_HUNTER_GUILD,
			stops.get(indexOf(stops, Location.CIVITAS_ILLA_FORTIS)).getMethod());
	}

	@Test
	public void withoutAWhistleKastoriIsFirstOrSecondOfTheVarlamoreStops()
	{
		// A Varlamore run chained by Renu: the walk back to the Kastori pad is slow, so Kastori isn't left until
		// last. (With an unlimited whistle every Varlamore stop is its own teleport and their order doesn't matter;
		// in a full run, Nemus Retreat is reached by fairy ring and isn't part of the chain.)
		final List<Patch> varlamore = Arrays.asList(Patch.KASTORI_FRUIT_TREE, Patch.NEMUS_RETREAT_TREE, Patch.CIVITAS_HERB);
		final List<RouteStop> stops = RoutePlanner.plan(varlamore, new RunConfig().sanitise(),
			SeanAccount.access(), SeanAccount.holdingsWithout(ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE),
			new PohSetup(), LearnedTimes.NONE, null, null, 0).getStops();
		int seen = 0;
		for (RouteStop stop : stops)
		{
			final Location at = stop.getLocation();
			if (at == Location.KASTORI)
			{
				assertTrue(describe(stops), seen <= 1);
				return;
			}
			if (at == Location.CIVITAS_ILLA_FORTIS || at == Location.NEMUS_RETREAT)
			{
				seen++;
			}
		}
	}

	// Each run can be a different mix

	@Test
	public void herbsOnlyVisitsOnlyHerbStopsAndStartsNearby()
	{
		final List<RouteStop> stops = plan(SeanAccount.patches(PatchType.HERB), Location.CATHERBY, 40);
		assertEquals(describe(stops), Location.CATHERBY, stops.get(0).getLocation());
		for (RouteStop stop : stops)
		{
			assertTrue(describe(stops), stop.getLocation() != Location.GNOME_STRONGHOLD
				&& stop.getLocation() != Location.TREE_GNOME_VILLAGE && stop.getLocation() != Location.LLETYA);
		}
	}

	@Test
	public void herbsAndFruitTreesChainStrongholdToTreeGnomeVillage()
	{
		assertStrongholdChain(plan(SeanAccount.patches(PatchType.HERB, PatchType.FRUIT_TREE), null, 0));
	}

	@Test
	public void herbsAndTreesWalkFaladorParkToTaverley()
	{
		final List<RouteStop> stops = plan(SeanAccount.patches(PatchType.HERB, PatchType.TREE), null, 0);
		assertEquals(describe(stops), 1,
			Math.abs(indexOf(stops, Location.FALADOR_PARK) - indexOf(stops, Location.TAVERLEY)));
	}

	// Travel times Sean has corrected

	@Test
	public void treeGnomeVillageIsQuickerByTheStrongholdSpiritTreeThanByFairyRing()
	{
		assertTrue(RoutePlanner.chainsFrom(Location.GNOME_STRONGHOLD, Location.TREE_GNOME_VILLAGE,
			new RunConfig().sanitise(), SeanAccount.access(), SeanAccount.holdings(), new PohSetup(), LearnedTimes.NONE));
	}

	// Banking partway

	/**
	 * Sean's full run from near Falador Park with bank visits placed, using a simple slot count (5 tools, one
	 * travel item per stop, two per patch: seed or sapling, and payment or compost), so it needs banking.
	 */
	private static BankPlanner.Result bankedFullRun()
	{
		final RunConfig config = new RunConfig().sanitise();
		final Route route = RoutePlanner.plan(SeanAccount.patches(ALL), config, SeanAccount.access(),
			SeanAccount.holdings(), new PohSetup(), LearnedTimes.NONE, null, Location.FALADOR_PARK, 57);
		final List<RouteStop> stops = route.getStops();
		final BankPlanner.Slots slots = (a, b) ->
		{
			int used = 5;
			for (RouteStop stop : stops.subList(a, b))
			{
				used += 1 + 2 * (int) SeanAccount.patches(ALL).stream()
					.filter(p -> p.getLocation() == stop.getLocation()).count();
			}
			return used;
		};
		return BankPlanner.place(route, slots, 28,
			com.farmrunautopilot.data.travel.BankTeleport.best(SeanAccount.access(), SeanAccount.holdings()),
			(from, to) -> RoutePlanner.chainSaving(from, to, config, SeanAccount.access(), SeanAccount.holdings(),
				new PohSetup(), LearnedTimes.NONE),
			to -> RoutePlanner.fromAnywhere(to, config, SeanAccount.access(), SeanAccount.holdings(), new PohSetup(),
				LearnedTimes.NONE));
	}

	private static String describe(BankPlanner.Result result)
	{
		final StringBuilder b = new StringBuilder(describe(result.getRoute().getStops()));
		for (BankPlanner.Visit v : result.getVisits())
		{
			b.append("bank after ").append(v.getAfterStop() + 1).append(": ")
				.append(v.getTeleport() != null ? v.getTeleport().name() : "near " + v.getLocation()).append('\n');
		}
		return b.toString();
	}

	@Test
	public void neverBanksAtPortPhasmatys()
	{
		final BankPlanner.Result result = bankedFullRun();
		for (BankPlanner.Visit v : result.getVisits())
		{
			assertTrue(describe(result), v.getTeleport() != null || v.getLocation() != Location.PORT_PHASMATYS);
		}
	}

	@Test
	public void bigRunBanksAtABankAlongTheWayOrByABankTeleport()
	{
		final BankPlanner.Result result = bankedFullRun();
		assertTrue(describe(result), !result.getVisits().isEmpty());
		for (BankPlanner.Visit v : result.getVisits())
		{
			assertTrue(describe(result), v.getTeleport() != null
				|| com.farmrunautopilot.data.travel.TravelTimes.bankTiles(v.getLocation()) != null);
		}
	}

	@Test
	public void bankBreakKeepsTheStrongholdChainAndTheStart()
	{
		final BankPlanner.Result result = bankedFullRun();
		final List<RouteStop> stops = result.getRoute().getStops();
		assertEquals(describe(result), Location.FALADOR_PARK, stops.get(0).getLocation());
		assertStrongholdChain(stops);
	}

	@Test
	@Ignore("Sprint 3: per-inventory supplies")
	public void midRunBankNeverSaysToDepositWhatTheRestOfTheRunNeeds()
	{
	}
}
