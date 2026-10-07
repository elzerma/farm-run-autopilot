package com.farmrunautopilot.tracking;

import static org.junit.Assert.assertEquals;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import org.junit.Test;

public class PatchStatusTextTest
{
	private static final long NOW = 1_700_000_000L;

	private static PatchPrediction prediction(Patch patch, Crop crop, PatchState state, int stage, int stages,
		long doneAt)
	{
		return new PatchPrediction(patch, crop, state, stage, stages, doneAt, NOW, 0,
			PatchPrediction.Source.THIS_PLUGIN);
	}

	@Test
	public void durations()
	{
		assertEquals("<1m", PatchStatusText.duration(0));
		assertEquals("<1m", PatchStatusText.duration(59));
		assertEquals("1m", PatchStatusText.duration(60));
		assertEquals("1h 00m", PatchStatusText.duration(3600));
		assertEquals("2h 05m", PatchStatusText.duration(7500));
	}

	@Test
	public void descriptions()
	{
		assertEquals("Not seen yet", PatchStatusText.describe(null, NOW));
		assertEquals("Ranarr - ready in 34m", PatchStatusText.describe(
			prediction(Patch.CATHERBY_HERB, Crop.RANARR, PatchState.GROWING, 2, 5, NOW + 34 * 60), NOW));
		assertEquals("Ranarr - ready to pick", PatchStatusText.describe(
			prediction(Patch.CATHERBY_HERB, Crop.RANARR, PatchState.HARVESTABLE, 2, 5, 0), NOW));
		assertEquals("Magic - check health", PatchStatusText.describe(
			prediction(Patch.TAVERLEY_TREE, Crop.MAGIC, PatchState.CHECK_HEALTH, 12, 13, 0), NOW));
		assertEquals("Magic - grown, ready to clear", PatchStatusText.describe(
			prediction(Patch.TAVERLEY_TREE, Crop.MAGIC, PatchState.HARVESTABLE, 0, 1, 0), NOW));
		assertEquals("Palm - 3 fruit (full in 2h 15m)", PatchStatusText.describe(
			prediction(Patch.BRIMHAVEN_FRUIT_TREE, Crop.PALM, PatchState.HARVESTABLE, 3, 7, NOW + 135 * 60), NOW));
		assertEquals("Palm - 6 fruit", PatchStatusText.describe(
			prediction(Patch.BRIMHAVEN_FRUIT_TREE, Crop.PALM, PatchState.HARVESTABLE, 6, 7, NOW - 60), NOW));
		assertEquals("Dead herb", PatchStatusText.describe(
			prediction(Patch.CATHERBY_HERB, null, PatchState.DEAD, 1, 1, 0), NOW));
		assertEquals("Empty", PatchStatusText.describe(
			prediction(Patch.CATHERBY_HERB, null, PatchState.EMPTY, 0, 4, 0), NOW));
	}
}
