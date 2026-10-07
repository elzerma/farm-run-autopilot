package com.farmrunautopilot.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.DecodedPatch;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import org.junit.Test;

public class PatchPredictorTest
{
	/** A time that sits exactly on a 5, 20, 40 and 160 minute boundary when there is no offset. */
	private static final long T0 = 160L * 60 * 10_000_000;
	private static final long MIN = 60;

	private static PatchPrediction predict(Patch patch, int value, long observedAt, long now)
	{
		return predict(patch, value, observedAt, now, false);
	}

	private static PatchPrediction predict(Patch patch, int value, long observedAt, long now, boolean leagues)
	{
		return PatchPredictor.predict(patch, new PatchRecord(value, observedAt), 0,
			PatchPrediction.Source.THIS_PLUGIN, now, FarmingTick.UNKNOWN, leagues);
	}

	@Test
	public void herbGrowsOneStagePerTwentyMinutes()
	{
		// Ranarr planted (value 32 = growing stage 0), seen a minute after a tick boundary.
		final long seen = T0 + MIN;
		PatchPrediction p = predict(Patch.CATHERBY_HERB, 32, seen, seen);
		assertEquals(Crop.RANARR, p.getCrop());
		assertEquals(PatchState.GROWING, p.getState());
		assertEquals(0, p.getStage());
		assertEquals(T0 + 80 * MIN, p.getDoneAt());

		p = predict(Patch.CATHERBY_HERB, 32, seen, T0 + 20 * MIN);
		assertEquals(1, p.getStage());

		p = predict(Patch.CATHERBY_HERB, 32, seen, T0 + 79 * MIN);
		assertEquals(PatchState.GROWING, p.getState());
		assertEquals(3, p.getStage());

		p = predict(Patch.CATHERBY_HERB, 32, seen, T0 + 80 * MIN);
		assertEquals(PatchState.HARVESTABLE, p.getState());
	}

	@Test
	public void grownTreeNeedsCheckHealth()
	{
		// Magic tree just planted (value 48): 12 ticks of 40 minutes.
		PatchPrediction p = predict(Patch.TAVERLEY_TREE, 48, T0, T0 + 479 * MIN);
		assertEquals(PatchState.GROWING, p.getState());
		assertEquals(11, p.getStage());

		p = predict(Patch.TAVERLEY_TREE, 48, T0, T0 + 480 * MIN);
		assertEquals(PatchState.CHECK_HEALTH, p.getState());
		assertEquals(12, p.getStage());

		// Long after: still capped at check-health.
		p = predict(Patch.TAVERLEY_TREE, 48, T0, T0 + 100_000 * MIN);
		assertEquals(PatchState.CHECK_HEALTH, p.getState());
		assertEquals(T0 + 480 * MIN, p.getDoneAt());
	}

	@Test
	public void fruitTreeTakesSixteenHours()
	{
		// Palm just planted (value 200).
		final PatchPrediction p = predict(Patch.BRIMHAVEN_FRUIT_TREE, 200, T0, T0 + 959 * MIN);
		assertEquals(PatchState.GROWING, p.getState());
		assertEquals(T0 + 960 * MIN, p.getDoneAt());
		assertEquals(PatchState.CHECK_HEALTH,
			predict(Patch.BRIMHAVEN_FRUIT_TREE, 200, T0, T0 + 960 * MIN).getState());
	}

	@Test
	public void fruitRegrowsUpToSix()
	{
		// Apple tree with 0 fruit (value 14).
		final long regrow = Crop.FRUIT_REGROW_MINUTES * MIN;
		assertEquals(0, predict(Patch.CATHERBY_FRUIT_TREE, 14, T0, T0).getStage());
		assertEquals(3, predict(Patch.CATHERBY_FRUIT_TREE, 14, T0, T0 + 3 * regrow).getStage());
		final PatchPrediction full = predict(Patch.CATHERBY_FRUIT_TREE, 14, T0, T0 + 50 * regrow);
		assertEquals(6, full.getStage());
		assertEquals(PatchState.HARVESTABLE, full.getState());
	}

	@Test
	public void weedLevelIsNotPredicted()
	{
		// Value 3 = raked clear; stays clear (in-game, weeds did not regrow on a 5 minute tick).
		final PatchPrediction empty = predict(Patch.ARDOUGNE_HERB, 3, T0, T0 + 600 * MIN);
		assertEquals(PatchState.EMPTY, empty.getState());
		assertEquals(0, empty.getDoneAt());

		// Value 1 = weed level 2; stays at level 2.
		final PatchPrediction weeds = predict(Patch.ARDOUGNE_HERB, 1, T0, T0 + 600 * MIN);
		assertEquals(PatchState.WEEDS, weeds.getState());
		assertEquals(2, weeds.getStage());
	}

	@Test
	public void diseasedAndDeadDoNotChange()
	{
		// Diseased ranarr (140), dead herb (170).
		final PatchPrediction diseased = predict(Patch.ARDOUGNE_HERB, 140, T0, T0 + 600 * MIN);
		assertEquals(PatchState.DISEASED, diseased.getState());
		assertEquals(0, diseased.getDoneAt());
		final PatchPrediction dead = predict(Patch.ARDOUGNE_HERB, 170, T0, T0 + 600 * MIN);
		assertEquals(PatchState.DEAD, dead.getState());
		assertNull(dead.getCrop());
	}

	@Test
	public void leaguesTicksAreFiveTimesFaster()
	{
		final PatchPrediction p = predict(Patch.CATHERBY_HERB, 32, T0, T0 + 16 * MIN, true);
		assertEquals(PatchState.HARVESTABLE, p.getState());
	}

	@Test
	public void growthTickDetection()
	{
		final DecodedPatch ranarr0 = new DecodedPatch(Crop.RANARR, PatchState.GROWING, 0);
		final DecodedPatch ranarr1 = new DecodedPatch(Crop.RANARR, PatchState.GROWING, 1);
		final DecodedPatch ranarrReady = new DecodedPatch(Crop.RANARR, PatchState.HARVESTABLE, 2);
		assertTrue(PatchPredictor.isObservedGrowthTick(PatchType.HERB, ranarr0, ranarr1));
		assertTrue(PatchPredictor.isObservedGrowthTick(PatchType.HERB, ranarr1, ranarrReady));
		assertFalse(PatchPredictor.isObservedGrowthTick(PatchType.HERB, ranarr1, ranarr0));

		final DecodedPatch magic11 = new DecodedPatch(Crop.MAGIC, PatchState.GROWING, 11);
		final DecodedPatch magicCheck = new DecodedPatch(Crop.MAGIC, PatchState.CHECK_HEALTH, 12);
		final DecodedPatch magicGrown = new DecodedPatch(Crop.MAGIC, PatchState.HARVESTABLE, 0);
		assertTrue(PatchPredictor.isObservedGrowthTick(PatchType.TREE, magic11, magicCheck));
		assertFalse(PatchPredictor.isObservedGrowthTick(PatchType.TREE, magicCheck, magicGrown));
		assertFalse(PatchPredictor.isObservedGrowthTick(PatchType.TREE, magic11, magicGrown));

		final DecodedPatch diseased = new DecodedPatch(Crop.RANARR, PatchState.DISEASED, 1);
		final DecodedPatch dead = new DecodedPatch(Crop.RANARR, PatchState.DEAD, 1);
		assertTrue(PatchPredictor.isObservedGrowthTick(PatchType.HERB, ranarr1, diseased));
		assertTrue(PatchPredictor.isObservedGrowthTick(PatchType.HERB, diseased, dead));

		final DecodedPatch weeds = new DecodedPatch(null, PatchState.WEEDS, 2);
		assertFalse(PatchPredictor.isObservedGrowthTick(PatchType.HERB, weeds, ranarr0));
	}

	@Test
	public void plantingDetection()
	{
		final DecodedPatch empty = new DecodedPatch(null, PatchState.EMPTY, 0);
		final DecodedPatch ranarr0 = new DecodedPatch(Crop.RANARR, PatchState.GROWING, 0);
		final DecodedPatch ranarr1 = new DecodedPatch(Crop.RANARR, PatchState.GROWING, 1);
		assertTrue(PatchPredictor.isPlanting(empty, ranarr0));
		assertFalse(PatchPredictor.isPlanting(ranarr0, ranarr1));
		assertFalse(PatchPredictor.isPlanting(empty, empty));
	}
}
