package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.junit.Test;

public class FlowerProtectionTest
{
	private static PatchPrediction holding(Patch patch, Crop crop, PatchState state)
	{
		return new PatchPrediction(patch, crop, state, 0, 5, 0, 0, 0, PatchPrediction.Source.THIS_PLUGIN);
	}

	private static RunConfig allotments()
	{
		final RunConfig config = new RunConfig();
		config.setEnabledTypes(EnumSet.of(PatchType.ALLOTMENT));
		return config.sanitise();
	}

	@Test
	public void cheapestFlowerThatProtectsEveryCrop()
	{
		assertEquals(Crop.MARIGOLD, FlowerProtection.flowerFor(Arrays.asList(Crop.POTATO, Crop.ONION), 99, c -> 0));
		// Marigold and rosemary don't share a crop: only white lily covers both
		assertEquals(Crop.WHITE_LILY, FlowerProtection.flowerFor(Arrays.asList(Crop.POTATO, Crop.CABBAGE), 99, c -> 0));
		assertNull(FlowerProtection.flowerFor(Arrays.asList(Crop.POTATO, Crop.CABBAGE), 57, c -> 0));
		assertEquals(Crop.SCARECROW, FlowerProtection.flowerFor(Collections.singletonList(Crop.SWEETCORN), 99, c -> 0));
		assertNull(FlowerProtection.flowerFor(Collections.singletonList(Crop.STRAWBERRY), 40, c -> 0));
	}

	@Test
	public void aFlowerTheyHoldBeatsACheaperOneTheyDont()
	{
		assertEquals(Crop.WHITE_LILY, FlowerProtection.flowerFor(Collections.singletonList(Crop.POTATO), 99,
			c -> c == Crop.WHITE_LILY ? 1 : 0));
	}

	@Test
	public void aGrownProtectingFlowerIsLeftAlone()
	{
		final Patch flower = Patch.FALADOR_FLOWER;
		assertTrue(FlowerProtection.isDue(null, Crop.POTATO));
		assertTrue(FlowerProtection.isDue(holding(flower, null, PatchState.WEEDS), Crop.POTATO));
		assertTrue(FlowerProtection.isDue(holding(flower, Crop.MARIGOLD, PatchState.DEAD), Crop.POTATO));
		assertFalse(FlowerProtection.isDue(holding(flower, Crop.MARIGOLD, PatchState.HARVESTABLE), Crop.POTATO));
		assertFalse(FlowerProtection.isDue(holding(flower, Crop.MARIGOLD, PatchState.GROWING), Crop.POTATO));
		// The wrong flower for the crop is replaced
		assertTrue(FlowerProtection.isDue(holding(flower, Crop.ROSEMARY, PatchState.HARVESTABLE), Crop.POTATO));
	}

	@Test
	public void flowerPatchIsKeptOnlyForAFlowerProtectedSet()
	{
		final RunConfig config = allotments();
		assertTrue(FlowerProtection.isProtecting(Patch.FALADOR_FLOWER, config, AccessSnapshot.UNKNOWN));
		// Kastori's flower patch has no allotments
		assertFalse(FlowerProtection.isProtecting(Patch.KASTORI_FLOWER, config, AccessSnapshot.UNKNOWN));

		config.setUseProtectionOverrides(true);
		config.getProtectionOverrides().put(Patch.FALADOR_ALLOTMENT_NW, Protection.PAY_GARDENER);
		config.getProtectionOverrides().put(Patch.FALADOR_ALLOTMENT_SE, Protection.PAY_GARDENER);
		assertFalse(FlowerProtection.isProtecting(Patch.FALADOR_FLOWER, config, AccessSnapshot.UNKNOWN));

		final RunConfig unticked = allotments();
		unticked.getDisabledPatches().add(Patch.FALADOR_FLOWER);
		assertFalse(FlowerProtection.isProtecting(Patch.FALADOR_FLOWER, unticked, AccessSnapshot.UNKNOWN));
	}

	@Test
	public void effectiveProtection()
	{
		final RunConfig config = allotments();
		final Map<Patch, Crop> plantings = new EnumMap<>(Patch.class);
		plantings.put(Patch.FALADOR_ALLOTMENT_NW, Crop.POTATO);
		plantings.put(Patch.FALADOR_FLOWER, Crop.MARIGOLD);
		assertEquals(Protection.FLOWER, FlowerProtection.effective(Patch.FALADOR_ALLOTMENT_NW, config,
			AccessSnapshot.UNKNOWN, plantings, p -> null));

		// A flower that doesn't suit the crop: pay instead
		plantings.put(Patch.FALADOR_FLOWER, Crop.ROSEMARY);
		assertEquals(Protection.PAY_GARDENER, FlowerProtection.effective(Patch.FALADOR_ALLOTMENT_NW, config,
			AccessSnapshot.UNKNOWN, plantings, p -> null));

		// Not planted this run: what's there now counts
		plantings.remove(Patch.FALADOR_FLOWER);
		assertEquals(Protection.FLOWER, FlowerProtection.effective(Patch.FALADOR_ALLOTMENT_NW, config,
			AccessSnapshot.UNKNOWN, plantings, p -> p == Patch.FALADOR_FLOWER
				? holding(p, Crop.WHITE_LILY, PatchState.HARVESTABLE) : null));

		// Harmony Island: no flower patch and nobody to pay
		assertEquals(Protection.COMPOST_ONLY, FlowerProtection.effective(Patch.HARMONY_ISLAND_ALLOTMENT, config,
			AccessSnapshot.UNKNOWN, plantings, p -> null));
		assertNull(FlowerProtection.flowerPatchOf(Patch.HARMONY_ISLAND_ALLOTMENT));
	}
}
