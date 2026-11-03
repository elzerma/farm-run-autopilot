package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.FarmingContract;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.ContractTracker.Contract;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class RunSelectorTest
{
	private static final long NOW = 1_700_000_000L;
	private static final long HOUR = 3600;

	private static PatchPrediction growing(Patch patch, long doneAt)
	{
		return new PatchPrediction(patch, null, PatchState.GROWING, 0, 7, doneAt, NOW, 0,
			PatchPrediction.Source.THIS_PLUGIN);
	}

	private static PatchPrediction ready(Patch patch)
	{
		return new PatchPrediction(patch, null, PatchState.HARVESTABLE, 0, 7, 0, NOW, 0,
			PatchPrediction.Source.THIS_PLUGIN);
	}

	private static RunConfig config(PatchType... types)
	{
		final RunConfig config = new RunConfig();
		config.setEnabledTypes(EnumSet.noneOf(PatchType.class));
		for (PatchType type : types)
		{
			config.getEnabledTypes().add(type);
		}
		return config.sanitise();
	}

	/** Fruit trees: one ready, the rest growing for another 2-8 hours. Herbs: all ready. */
	private static final Function<Patch, PatchPrediction> MIXED = p ->
	{
		if (p.getType() == PatchType.HERB || p == Patch.CATHERBY_FRUIT_TREE)
		{
			return ready(p);
		}
		return growing(p, NOW + (2 + p.ordinal() % 7) * HOUR);
	};

	private static RunSelection select(RunConfig config, Map<PatchType, TypeOverride> overrides)
	{
		return RunSelector.select(config, AccessSnapshot.UNKNOWN, MIXED, NOW, false, overrides);
	}

	@Test
	public void typesWithTooFewDuePatchesAreSkippedWithACountdown()
	{
		final RunSelection selection = select(config(PatchType.FRUIT_TREE, PatchType.HERB), Collections.emptyMap());
		assertTrue(selection.getIncludedTypes().contains(PatchType.HERB));
		assertFalse(selection.getIncludedTypes().contains(PatchType.FRUIT_TREE));
		assertEquals(10, selection.getPatches().size());

		// 100% of 7 fruit trees: ready when the slowest one is.
		final long readyAt = selection.getSkippedTypes().get(PatchType.FRUIT_TREE);
		long latest = 0;
		for (Patch patch : Patch.values())
		{
			if (patch.getType() == PatchType.FRUIT_TREE && patch != Patch.CATHERBY_FRUIT_TREE)
			{
				latest = Math.max(latest, MIXED.apply(patch).getDoneAt());
			}
		}
		assertEquals(latest, readyAt);
		assertEquals(1, (int) selection.getDueCounts().get(PatchType.FRUIT_TREE));
	}

	@Test
	public void lowerThresholdIncludesTheTypeAndListsWhatIsNotDue()
	{
		final RunConfig config = config(PatchType.FRUIT_TREE);
		config.setDueThresholdPercent(10);
		final RunSelection selection = select(config, Collections.emptyMap());
		assertTrue(selection.getIncludedTypes().contains(PatchType.FRUIT_TREE));
		assertEquals(1, selection.getPatches().size());
		assertEquals(6, selection.getNotDue().size());
	}

	@Test
	public void overridesWin()
	{
		final Map<PatchType, TypeOverride> overrides = new EnumMap<>(PatchType.class);
		overrides.put(PatchType.FRUIT_TREE, TypeOverride.INCLUDE);
		overrides.put(PatchType.HERB, TypeOverride.SKIP);
		final RunSelection selection = select(config(PatchType.FRUIT_TREE, PatchType.HERB), overrides);
		assertTrue(selection.getIncludedTypes().contains(PatchType.FRUIT_TREE));
		assertFalse(selection.getIncludedTypes().contains(PatchType.HERB));
		assertTrue(selection.getSkippedTypes().containsKey(PatchType.HERB));
		// Included anyway: only the due fruit tree is visited.
		assertEquals(1, selection.getPatches().size());
	}

	@Test
	public void untickedTypesAreIgnored()
	{
		final RunSelection selection = select(config(PatchType.HERB), Collections.emptyMap());
		assertFalse(selection.getSkippedTypes().containsKey(PatchType.TREE));
		assertFalse(selection.getIncludedTypes().contains(PatchType.TREE));
	}

	@Test
	public void neededDueRoundsUp()
	{
		assertEquals(7, RunSelector.neededDue(7, 100));
		assertEquals(4, RunSelector.neededDue(7, 50));
		assertEquals(1, RunSelector.neededDue(7, 1));
	}

	/** Every guild contract patch holds a pre-plant: grown and waiting (herbs and allotments picked, the rest checked). */
	private static PatchPrediction prePlant(Patch patch)
	{
		final Crop crop = FarmingContract.prePlants(patch, 99).get(0);
		return new PatchPrediction(patch, crop, patch.getType().isHealthChecked() ? PatchState.CHECK_HEALTH
			: PatchState.HARVESTABLE, 0, crop.getStages(), 0, NOW, 0, PatchPrediction.Source.THIS_PLUGIN);
	}

	@Test
	public void contractsRunTheGuildsPatchesForThem()
	{
		final RunConfig config = config(PatchType.HERB);
		config.setFarmingContracts(true);
		final Function<Patch, PatchPrediction> guild = p -> FarmingContract.PATCHES.contains(p) ? prePlant(p) : MIXED.apply(p);
		// A maple contract: the guild's grown maple (the Hard table's first tree) is taken, though trees aren't ticked;
		// its herb, a pre-plant waiting, isn't, though herbs are; the other herb patches as usual
		final RunSelection maple = RunSelector.select(config, AccessSnapshot.UNKNOWN, guild, NOW, false,
			Collections.emptyMap(), new Contract(9, Crop.MAPLE, false, 3, 0));
		assertTrue(maple.getPatches().contains(Patch.FARMING_GUILD_TREE));
		assertFalse(maple.getPatches().contains(Patch.FARMING_GUILD_HERB));
		assertTrue(maple.getNotDue().contains(Patch.FARMING_GUILD_HERB.getDisplayName() + ": kept for a farming contract"));
		assertTrue(maple.getPatches().contains(Patch.CATHERBY_HERB));
		assertEquals(ContractPlanner.Jane.NONE, maple.getContract().getJane());
		// Handed in, everything else waiting: the guild's first patch anchors the stop for Guildmaster Jane
		final RunSelection handIn = RunSelector.select(config, AccessSnapshot.UNKNOWN, guild, NOW, false,
			Collections.emptyMap(), new Contract(9, Crop.MAPLE, true, 3, 1));
		assertEquals(ContractPlanner.Jane.HAND_IN, handIn.getContract().getJane());
		assertTrue(handIn.getPatches().contains(FarmingContract.PATCHES.get(0)));
		// Off: the guild's herb is an ordinary due herb patch
		config.setFarmingContracts(false);
		final RunSelection off = RunSelector.select(config, AccessSnapshot.UNKNOWN, guild, NOW, false,
			Collections.emptyMap(), new Contract(9, Crop.MAPLE, false, 3, 0));
		assertTrue(off.getPatches().contains(Patch.FARMING_GUILD_HERB));
		assertFalse(off.getContract().isOn());
	}
}
