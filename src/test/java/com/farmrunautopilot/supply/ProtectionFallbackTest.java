package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RoutePlanner;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Protection falling back in order (elzerma, 2026-10-18): the type's choice first; a gardener whose payment can't be had
 * is replaced by the best compost held (ultra, super, then compost), and so is compost that's run out; paid trees and
 * fruit trees get no compost, paid patches whose harvest compost raises still do.
 */
public class ProtectionFallbackTest
{
	private static final long NOW = 1_700_000_000L;

	@Test
	public void aTreeWithoutItsPaymentGetsTheBestCompostHeld()
	{
		final RunConfig config = trees(Compost.ULTRACOMPOST);
		final SupplyPlan plan = plan(config, bank(ItemID.BUCKET_SUPERCOMPOST, 20));
		assertFalse(plan.getCare().isEmpty());
		for (PatchCare care : plan.getCare().values())
		{
			assertFalse(care.isPaid());
			// No ultracompost held: the next one down
			assertEquals(Compost.SUPERCOMPOST, care.getCompost());
		}
	}

	@Test
	public void aPaidTreeGetsNoCompost()
	{
		final RunConfig config = trees(Compost.ULTRACOMPOST);
		final SupplyPlan paid = plan(config, holdings(withPayments(config, ItemID.BUCKET_ULTRACOMPOST)));
		assertFalse(paid.getCare().isEmpty());
		for (Map.Entry<Patch, PatchCare> e : paid.getCare().entrySet())
		{
			assertTrue(e.getKey().name(), e.getValue().isPaid());
			assertEquals(e.getKey().name(), Compost.NONE, e.getValue().getCompost());
		}
	}

	@Test
	public void aPaidAllotmentStillGetsItsCompost()
	{
		final RunConfig config = only(PatchType.ALLOTMENT, Compost.ULTRACOMPOST);
		final SupplyPlan paid = plan(config, holdings(withPayments(config, ItemID.BUCKET_ULTRACOMPOST)));
		assertTrue(paid.getCare().toString(), paid.getCare().values().stream().anyMatch(PatchCare::isPaid));
		for (Map.Entry<Patch, PatchCare> e : paid.getCare().entrySet())
		{
			assertEquals(e.getKey().name(), Compost.ULTRACOMPOST, e.getValue().getCompost());
		}
	}

	@Test
	public void withNoCompostEitherThePaymentIsAskedFor()
	{
		final SupplyPlan plan = plan(trees(Compost.ULTRACOMPOST), bank(ItemID.SPADE, 1));
		assertFalse(plan.getCare().isEmpty());
		for (PatchCare care : plan.getCare().values())
		{
			assertTrue(care.isPaid());
			assertEquals(Compost.NONE, care.getCompost());
		}
		assertTrue(plan.getLines().stream().anyMatch(l -> l.getGroup() == SupplyLine.Group.PAYMENTS));
	}

	@Test
	public void compostFallsBackOnlyDownwards()
	{
		final Map<Compost, Integer> superOnly = new EnumMap<>(Compost.class);
		superOnly.put(Compost.SUPERCOMPOST, 3);
		assertEquals(Compost.SUPERCOMPOST, SupplyCalculator.fallBack(superOnly, Compost.ULTRACOMPOST, false));
		assertEquals(Compost.SUPERCOMPOST, SupplyCalculator.fallBack(superOnly, Compost.SUPERCOMPOST, false));
		// Nothing at or below the choice: the choice itself, for the supply list to ask for
		assertEquals(Compost.COMPOST, SupplyCalculator.fallBack(superOnly, Compost.COMPOST, false));
		// A bottomless bucket whose compost isn't known yet might be it
		assertEquals(Compost.ULTRACOMPOST, SupplyCalculator.fallBack(superOnly, Compost.ULTRACOMPOST, true));
	}

	private static RunConfig trees(Compost compost)
	{
		return only(PatchType.TREE, compost);
	}

	private static RunConfig only(PatchType type, Compost compost)
	{
		final RunConfig config = new RunConfig();
		config.setEnabledTypes(EnumSet.of(type));
		config.sanitise();
		config.getProtection().put(type, Protection.PAY_GARDENER);
		config.getCompost().put(type, compost);
		return config;
	}

	/** A bank with 20 of this compost and every payment the run's crops need. */
	private static Map<Integer, Integer> withPayments(RunConfig config, int compostId)
	{
		final Map<Integer, Integer> bank = new HashMap<>(Map.of(compostId, 20));
		plan(config, holdings(new HashMap<>(bank))).getPlantings().forEach((patch, crop) ->
		{
			if (crop.hasPayment())
			{
				bank.merge(crop.getPaymentItemId(), crop.getPaymentQuantity(), Integer::sum);
			}
		});
		return bank;
	}

	private static SupplyPlan plan(RunConfig config, Holdings holdings)
	{
		final RunSelection selection = RunSelector.select(config, AccessSnapshot.UNKNOWN, p -> null, NOW, true,
			Collections.emptyMap());
		final Route route = RoutePlanner.plan(selection.getPatches(), config, AccessSnapshot.UNKNOWN, holdings,
			new PohSetup());
		return SupplyCalculator.calculate(config, AccessSnapshot.UNKNOWN, holdings, selection, route, p -> null,
			id -> "item " + id, id -> 0);
	}

	private static Holdings bank(int id, int count)
	{
		return holdings(new HashMap<>(Map.of(id, count)));
	}

	private static Holdings holdings(Map<Integer, Integer> bank)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}
}
