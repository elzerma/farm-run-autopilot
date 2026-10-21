package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.route.AtlasPlayers;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RoutePlanner;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Supplies and run selection for allotments, flowers and hops (docs/plans/crop-types.md, Sprint 1a). */
public class AllotmentSuppliesTest
{
	private static final long NOW = 1_700_000_000L;
	private static final Function<Patch, PatchPrediction> NEVER_SEEN = p -> null;

	private static RunConfig types(PatchType... types)
	{
		final RunConfig config = new RunConfig();
		config.setEnabledTypes(EnumSet.copyOf(Arrays.asList(types)));
		return config.sanitise();
	}

	private static Holdings bank(Map<Integer, Integer> bank)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	private static RunSelection select(RunConfig config, Function<Patch, PatchPrediction> predictions, boolean fullRun)
	{
		return RunSelector.select(config, AccessSnapshot.UNKNOWN, predictions, NOW, fullRun, Collections.emptyMap());
	}

	private static SupplyPlan plan(RunConfig config, Holdings holdings, Function<Patch, PatchPrediction> predictions,
		boolean fullRun)
	{
		final RunSelection selection = select(config, predictions, fullRun);
		final Route route = RoutePlanner.plan(selection.getPatches(), config, AccessSnapshot.UNKNOWN, holdings,
			new PohSetup(), AtlasPlayers.with(AccessSnapshot.UNKNOWN, holdings), null);
		return SupplyCalculator.calculate(config, AccessSnapshot.UNKNOWN, holdings, selection, route, predictions,
			id -> "item " + id, id -> 0);
	}

	private static SupplyLine find(SupplyPlan plan, String prefix)
	{
		for (SupplyLine line : plan.getLines())
		{
			if (line.getName().startsWith(prefix))
			{
				return line;
			}
		}
		return null;
	}

	private static PatchPrediction holding(Patch patch, Crop crop, PatchState state)
	{
		return new PatchPrediction(patch, crop, state, 0, 5, 0, NOW, 0, PatchPrediction.Source.THIS_PLUGIN);
	}

	@Test
	public void newPlayersStartWithTheOriginalRunTypes()
	{
		assertEquals(EnumSet.of(PatchType.TREE, PatchType.FRUIT_TREE, PatchType.HERB), new RunConfig().getEnabledTypes());
		assertEquals(Protection.FLOWER, new RunConfig().sanitise().getProtection().get(PatchType.ALLOTMENT));
	}

	@Test
	public void flowerProtectedAllotmentRun()
	{
		final RunConfig config = types(PatchType.ALLOTMENT);
		config.getCrops().put(PatchType.ALLOTMENT, Crop.POTATO);
		final Map<Integer, Integer> held = new HashMap<>();
		held.put(ItemID.POTATO_SEED, 100);
		final SupplyPlan plan = plan(config, bank(held), NEVER_SEEN, false);

		// 8 sets of two, plus Harmony Island; each set's flower patch joins to protect them, though flowers are off
		assertEquals(17, (int) plan.getPatchCounts().get(PatchType.ALLOTMENT));
		assertEquals(8, (int) plan.getPatchCounts().get(PatchType.FLOWER));

		final SupplyLine seeds = find(plan, "item " + ItemID.POTATO_SEED);
		assertEquals(51, seeds.getNeed());
		assertEquals(1, seeds.getSlots());
		assertTrue(seeds.getNote().contains("Falador farm (south) x2"));

		final SupplyLine marigolds = find(plan, "item " + ItemID.MARIGOLD_SEED);
		assertEquals("item " + ItemID.MARIGOLD_SEED + " - protects allotments", marigolds.getName());
		assertEquals(8, marigolds.getNeed());

		// Protected by flowers, and Harmony Island has nobody to pay: no payments at all
		assertNull(find(plan, "item " + ItemID.BUCKET_COMPOST));
		assertNotNull(find(plan, "Seed dibber"));
		assertNull("allotments aren't chopped", find(plan, "Axe"));
	}

	@Test
	public void paidAllotmentsPayPerPatch()
	{
		final RunConfig config = types(PatchType.ALLOTMENT);
		config.getCrops().put(PatchType.ALLOTMENT, Crop.POTATO);
		config.getProtection().put(PatchType.ALLOTMENT, Protection.PAY_GARDENER);
		final SupplyPlan plan = plan(config, Holdings.EMPTY, NEVER_SEEN, false);

		assertNull(plan.getPatchCounts().get(PatchType.FLOWER));
		// 2 compost for each of the 16 allotments that have a gardener
		assertEquals(32, find(plan, "item " + ItemID.BUCKET_COMPOST).getNeed());
	}

	@Test
	public void aGrownMarigoldStaysAndIsntInTheRun()
	{
		final RunConfig config = types(PatchType.ALLOTMENT, PatchType.FLOWER);
		config.getCrops().put(PatchType.ALLOTMENT, Crop.POTATO);
		final Function<Patch, PatchPrediction> predictions = p -> p == Patch.FALADOR_FLOWER
			? holding(p, Crop.MARIGOLD, PatchState.HARVESTABLE) : null;
		final RunSelection selection = select(config, predictions, false);
		assertFalse(selection.getPatches().contains(Patch.FALADOR_FLOWER));
		assertTrue(selection.getPatches().contains(Patch.FALADOR_ALLOTMENT_NW));
		// The other sets still need theirs, and Kastori's flower patch is an ordinary one
		assertTrue(selection.getPatches().contains(Patch.CATHERBY_FLOWER));
		assertTrue(selection.getPatches().contains(Patch.KASTORI_FLOWER));

		// Still protected by the marigold already there: no payment for Falador
		final SupplyPlan plan = plan(config, Holdings.EMPTY, predictions, false);
		assertNull(find(plan, "item " + ItemID.BUCKET_COMPOST));
	}

	@Test
	public void mixedCropsGetAWhiteLily()
	{
		final RunConfig config = types(PatchType.ALLOTMENT);
		config.setUseBackupCrops(true);
		config.getCrops().put(PatchType.ALLOTMENT, Crop.POTATO);
		config.getBackupCrops().put(PatchType.ALLOTMENT, Collections.singletonList(Crop.CABBAGE));
		for (Patch patch : Patch.values())
		{
			if (patch.getType() == PatchType.ALLOTMENT && patch.getLocation() != Patch.FALADOR_FLOWER.getLocation())
			{
				config.getDisabledPatches().add(patch);
			}
		}
		final Map<Integer, Integer> held = new HashMap<>();
		// Enough potato seeds for one allotment: the other gets cabbages
		held.put(ItemID.POTATO_SEED, 3);
		held.put(ItemID.CABBAGE_SEED, 3);
		final SupplyPlan plan = plan(config, bank(held), NEVER_SEEN, false);
		assertEquals(Crop.WHITE_LILY, plan.getPlantings().get(Patch.FALADOR_FLOWER));
	}

	@Test
	public void hopsTakeFourSeedsAndNoPaymentByDefault()
	{
		final RunConfig config = types(PatchType.HOPS);
		config.getCrops().put(PatchType.HOPS, Crop.HAMMERSTONE);
		final SupplyPlan plan = plan(config, Holdings.EMPTY, NEVER_SEEN, false);
		assertEquals(4, (int) plan.getPatchCounts().get(PatchType.HOPS));
		assertEquals(16, find(plan, "item " + ItemID.HAMMERSTONE_HOP_SEED).getNeed());
		assertNull(find(plan, "item " + ItemID.MARIGOLD));
	}

	@Test
	public void allocatorCountsSeedsPerPatch()
	{
		final Map<Patch, Crop> crops = CropAllocator.allocate(
			Arrays.asList(Patch.FALADOR_ALLOTMENT_NW, Patch.FALADOR_ALLOTMENT_SE, Patch.CATHERBY_ALLOTMENT_N),
			Arrays.asList(Crop.POTATO, Crop.ONION), crop -> crop == Crop.POTATO ? 7 : 3, patch -> false,
			Collections.emptyList());
		// 7 potato seeds plant two allotments (6), not three
		assertEquals(Crop.POTATO, crops.get(Patch.FALADOR_ALLOTMENT_NW));
		assertEquals(Crop.POTATO, crops.get(Patch.FALADOR_ALLOTMENT_SE));
		assertEquals(Crop.ONION, crops.get(Patch.CATHERBY_ALLOTMENT_N));
	}
}
