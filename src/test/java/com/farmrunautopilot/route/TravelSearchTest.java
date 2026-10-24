package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

/**
 * Choosing ways with the inventory in mind (T4) on the test account like elzerma's. The two runs that save a bank visit
 * came from a sweep of 60 random runs (2026-10-23); a rebuilt atlas or new supply rules may move them over or under an
 * inventory, so a failure there means looking at what changed, not necessarily a broken search.
 */
public class TravelSearchTest
{
	private static final RouteAtlas ATLAS = ElzermaAccount.atlas();

	@Test
	public void leavingTheEctophialAtHomeSkipsTheBankVisit()
	{
		final TravelSearch.Outcome o = search(Arrays.asList(Patch.FARMING_GUILD_TREE, Patch.NEMUS_RETREAT_TREE,
			Patch.FARMING_GUILD_FRUIT_TREE, Patch.TROLL_STRONGHOLD_HERB, Patch.CIVITAS_HERB, Patch.FALADOR_ALLOTMENT_NW,
			Patch.FALADOR_ALLOTMENT_SE, Patch.CATHERBY_ALLOTMENT_N, Patch.CATHERBY_ALLOTMENT_S, Patch.ARDOUGNE_ALLOTMENT_N,
			Patch.HARMONY_ISLAND_ALLOTMENT, Patch.HOSIDIUS_FLOWER, Patch.FARMING_GUILD_FLOWER, Patch.CIVITAS_FLOWER,
			Patch.KASTORI_FLOWER, Patch.LUMBRIDGE_HOPS, Patch.ALDARIN_HOPS, Patch.CATHERBY_FLOWER), Long.MAX_VALUE);
		assertEquals(Collections.singleton("item:ECTOPHIAL"), o.getAvoid());
		assertEquals(1, o.getQuickest().getBankStops().size());
		assertTrue(o.getRun().getBankStops().isEmpty());
		assertTrue(TravelSearch.score(o.getRun()) + TravelSearch.MIN_GAIN <= TravelSearch.score(o.getQuickest()));
		// The way to Port Phasmatys changes; nothing on the supply list is the Ectophial
		assertFalse(o.getRun().getSupplies().getLines().stream().anyMatch(l -> l.getName().contains("Ectophial")));

		// The leg that changed says why on the Run tab's hover; the others say nothing
		final RunAssembly.Result explained = TravelSearch.explain(o.getQuickest(), o.getRun(), o.getAvoid(),
			id -> "item " + id);
		final List<String> whys = new java.util.ArrayList<>();
		for (RouteStop stop : explained.getRoute().getStops())
		{
			if (stop.getWhy() == null)
			{
				continue;
			}
			whys.add(stop.getLocation() + ": " + stop.getWhy());
			// It went by the Ectophial in the quickest run (Harmony Island, through Port Phasmatys)
			final RouteStop was = o.getQuickest().getRoute().getStops().stream()
				.filter(s -> s.getLocation() == stop.getLocation()).findFirst().orElse(null);
			assertTrue(stop.getLocation().name(), was != null && Footprint.of(was, new RunConfig().sanitise(), ElzermaAccount.access(),
				ElzermaAccount.holdings()).contains("item:ECTOPHIAL"));
		}
		assertEquals(whys.toString(), 1, whys.size());
		assertTrue(whys.get(0), whys.get(0).contains(" s slower than the quickest way, so your Ectophial can stay in "
			+ "the bank: one bank visit fewer, "));
		assertTrue(whys.get(0), whys.get(0).endsWith(" s quicker overall"));
	}

	@Test
	public void twoThingsLeftAtHomeSaveOneOfTwoVisits()
	{
		final TravelSearch.Outcome o = search(Arrays.asList(Patch.LUMBRIDGE_TREE, Patch.VARROCK_TREE,
			Patch.FALADOR_TREE, Patch.NEMUS_RETREAT_TREE, Patch.GNOME_STRONGHOLD_FRUIT_TREE, Patch.CATHERBY_FRUIT_TREE,
			Patch.BRIMHAVEN_FRUIT_TREE, Patch.KASTORI_FRUIT_TREE, Patch.CATHERBY_HERB, Patch.CIVITAS_HERB,
			Patch.FALADOR_ALLOTMENT_NW, Patch.FALADOR_ALLOTMENT_SE, Patch.CATHERBY_ALLOTMENT_N,
			Patch.PORT_PHASMATYS_ALLOTMENT_NW, Patch.PORT_PHASMATYS_ALLOTMENT_SE, Patch.HOSIDIUS_ALLOTMENT_NE,
			Patch.HOSIDIUS_ALLOTMENT_SW, Patch.FARMING_GUILD_ALLOTMENT_N, Patch.FARMING_GUILD_ALLOTMENT_S,
			Patch.CIVITAS_ALLOTMENT_S, Patch.ARDOUGNE_FLOWER, Patch.PRIFDDINAS_FLOWER, Patch.LUMBRIDGE_HOPS,
			Patch.SEERS_VILLAGE_HOPS, Patch.ALDARIN_HOPS, Patch.FALADOR_FLOWER, Patch.HOSIDIUS_FLOWER,
			Patch.FARMING_GUILD_FLOWER), Long.MAX_VALUE);
		assertEquals(2, o.getAvoid().size());
		assertEquals(2, o.getQuickest().getBankStops().size());
		assertEquals(1, o.getRun().getBankStops().size());
		assertTrue(TravelSearch.score(o.getRun()) + TravelSearch.MIN_GAIN <= TravelSearch.score(o.getQuickest()));
	}

	@Test
	public void elzermasUsualRunKeepsTheQuickestWays()
	{
		// Herbs, trees and fruit trees: 39 slots and one 25 s bank visit, and the teleports free at most 8 slots
		final RunConfig config = new RunConfig().sanitise();
		final TravelSearch.Outcome o = search(config, Long.MAX_VALUE);
		assertEquals(1, o.getQuickest().getBankStops().size());
		assertTrue(o.getAvoid().isEmpty());
		assertSame(o.getQuickest(), o.getRun());
	}

	@Test
	public void aRunThatFitsIsLeftAlone()
	{
		final TravelSearch.Outcome o = search(Arrays.asList(Patch.CATHERBY_HERB, Patch.FALADOR_HERB,
			Patch.ARDOUGNE_HERB), Long.MAX_VALUE);
		assertTrue(o.getQuickest().getBankStops().isEmpty());
		assertTrue(o.getAvoid().isEmpty());
		assertEquals(1, o.getTried());
	}

	@Test
	public void outOfTimeKeepsTheQuickestWays()
	{
		final RunConfig config = config(Arrays.asList(Patch.FARMING_GUILD_TREE, Patch.NEMUS_RETREAT_TREE,
			Patch.FARMING_GUILD_FRUIT_TREE, Patch.TROLL_STRONGHOLD_HERB, Patch.CIVITAS_HERB, Patch.FALADOR_ALLOTMENT_NW,
			Patch.FALADOR_ALLOTMENT_SE, Patch.CATHERBY_ALLOTMENT_N, Patch.CATHERBY_ALLOTMENT_S, Patch.ARDOUGNE_ALLOTMENT_N,
			Patch.HARMONY_ISLAND_ALLOTMENT, Patch.HOSIDIUS_FLOWER, Patch.FARMING_GUILD_FLOWER, Patch.CIVITAS_FLOWER,
			Patch.KASTORI_FLOWER, Patch.LUMBRIDGE_HOPS, Patch.ALDARIN_HOPS, Patch.CATHERBY_FLOWER));
		final TravelSearch.Outcome o = search(config, 0);
		assertTrue(o.getAvoid().isEmpty());
		assertSame(o.getQuickest(), o.getRun());
	}

	@Test
	public void avoidingAnItemPlansWithoutItWhereThereIsAnotherWay()
	{
		final RunConfig config = new RunConfig().sanitise();
		final RunSelection selection = RunSelector.select(config, ElzermaAccount.access(), p -> null, 0, true,
			Collections.emptyMap());
		final RunAssembly.Unbanked without = assembly(config, selection, ATLAS.avoiding(
			Collections.singleton("item:QUETZAL_WHISTLE"))).unbanked(RunAssembly.Start.ANYWHERE);
		assertFalse(TravelSearch.carried(without.getRoute(), config, ElzermaAccount.access(), ElzermaAccount.holdings())
			.contains("item:QUETZAL_WHISTLE"));
		// Every stop still has a way
		assertTrue(without.getRoute().getStops().stream().noneMatch(s -> s.getDeparture() == Departure.NONE));
	}

	private static TravelSearch.Outcome search(List<Patch> patches, long deadline)
	{
		return search(config(patches), deadline);
	}

	private static TravelSearch.Outcome search(RunConfig config, long deadline)
	{
		final RunSelection selection = RunSelector.select(config, ElzermaAccount.access(), p -> null, 0, true,
			Collections.emptyMap());
		return TravelSearch.search(ATLAS, a -> assembly(config, selection, a), RunAssembly.Start.ANYWHERE, config,
			ElzermaAccount.access(), ElzermaAccount.holdings(), () -> 0L, deadline);
	}

	/** Every crop type, with only these patches in the run. */
	private static RunConfig config(List<Patch> patches)
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setEnabledTypes(EnumSet.allOf(PatchType.class));
		for (Patch patch : Patch.values())
		{
			if (!patches.contains(patch))
			{
				config.getDisabledPatches().add(patch);
			}
		}
		return config;
	}

	private static RunAssembly assembly(RunConfig config, RunSelection selection, RouteAtlas atlas)
	{
		return new RunAssembly(config, ElzermaAccount.access(), ElzermaAccount.holdings(), new PohSetup(), atlas,
			selection, p -> null, id -> "item " + id, id -> 0, id -> null);
	}
}
