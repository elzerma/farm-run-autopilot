package com.farmrunautopilot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

/**
 * Allotments, flowers and hops (docs/plans/crop-types.md, Sprint 1a): crops, seed counts and the varbit tables copied
 * from RuneLite core.
 */
public class AllotmentDataTest
{
	@Test
	public void seedsPerPatch()
	{
		for (Crop crop : Crop.values())
		{
			switch (crop.getType())
			{
				case ALLOTMENT:
					assertEquals(crop.name(), 3, crop.getSeedsPerPatch());
					break;
				case HOPS:
					// Fibres take 3, the rest 4 (wiki: Hops patch)
					final boolean fibre = crop == Crop.JUTE || crop == Crop.FLAX || crop == Crop.HEMP || crop == Crop.COTTON;
					assertEquals(crop.name(), fibre ? 3 : 4, crop.getSeedsPerPatch());
					break;
				default:
					assertEquals(crop.name(), 1, crop.getSeedsPerPatch());
			}
		}
	}

	@Test
	public void growthTimesMatchRuneLite()
	{
		// RuneLite Produce: tick minutes x (stages - 1)
		assertEquals(40, Crop.POTATO.getGrowthMinutes());
		assertEquals(60, Crop.SWEETCORN.getGrowthMinutes());
		assertEquals(80, Crop.WATERMELON.getGrowthMinutes());
		assertEquals(70, Crop.SNAPE_GRASS.getGrowthMinutes());
		assertEquals(20, Crop.WHITE_LILY.getGrowthMinutes());
		assertEquals(40, Crop.HAMMERSTONE.getGrowthMinutes());
		assertEquals(60, Crop.FLAX.getGrowthMinutes());
		assertEquals(100, Crop.COTTON.getGrowthMinutes());
	}

	@Test
	public void flowersThatProtectEachAllotmentCrop()
	{
		assertEquals(Arrays.asList(Crop.MARIGOLD, Crop.WHITE_LILY), Crop.POTATO.getProtectingFlowers());
		assertEquals(Arrays.asList(Crop.ROSEMARY, Crop.WHITE_LILY), Crop.CABBAGE.getProtectingFlowers());
		assertEquals(Arrays.asList(Crop.SCARECROW, Crop.WHITE_LILY), Crop.SWEETCORN.getProtectingFlowers());
		assertEquals(Arrays.asList(Crop.WHITE_LILY), Crop.SNAPE_GRASS.getProtectingFlowers());
		assertTrue(Crop.RANARR.getProtectingFlowers().isEmpty());
		for (Crop crop : Crop.values())
		{
			if (crop.getType() == PatchType.ALLOTMENT)
			{
				assertTrue(crop.name(), crop.getProtectingFlowers().contains(Crop.WHITE_LILY));
			}
		}
	}

	@Test
	public void scarecrowIsNeverAChoice()
	{
		assertTrue(!Crop.SCARECROW.isChoosable());
		assertTrue(Crop.MARIGOLD.isChoosable());
	}

	@Test
	public void newTablesDecodeEveryValue()
	{
		// RuneLite covers every value of these three
		for (PatchType type : Arrays.asList(PatchType.ALLOTMENT, PatchType.FLOWER, PatchType.HOPS))
		{
			for (int value = 0; value <= 255; value++)
			{
				assertNotEquals(type + " " + value, PatchState.UNKNOWN, PatchStateDecoder.decode(type, value).getState());
			}
		}
	}

	@Test
	public void allotmentValues()
	{
		assertEquals(PatchState.EMPTY, PatchStateDecoder.decode(PatchType.ALLOTMENT, 3).getState());
		assertDecodes(PatchType.ALLOTMENT, 6, Crop.POTATO, PatchState.GROWING, 0);
		assertDecodes(PatchType.ALLOTMENT, 10, Crop.POTATO, PatchState.HARVESTABLE, 0);
		// Watered: still growing
		assertDecodes(PatchType.ALLOTMENT, 70, Crop.POTATO, PatchState.GROWING, 0);
		assertDecodes(PatchType.ALLOTMENT, 128, Crop.SNAPE_GRASS, PatchState.GROWING, 0);
		assertDecodes(PatchType.ALLOTMENT, 138, Crop.SNAPE_GRASS, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.ALLOTMENT, 135, Crop.POTATO, PatchState.DISEASED, 1);
		assertDecodes(PatchType.ALLOTMENT, 202, Crop.SNAPE_GRASS, PatchState.DISEASED, 4);
		assertDecodes(PatchType.ALLOTMENT, 245, Crop.WATERMELON, PatchState.DEAD, 1);
	}

	@Test
	public void flowerValues()
	{
		assertDecodes(PatchType.FLOWER, 8, Crop.MARIGOLD, PatchState.GROWING, 0);
		assertDecodes(PatchType.FLOWER, 12, Crop.MARIGOLD, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.FLOWER, 41, Crop.WHITE_LILY, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.FLOWER, 101, Crop.WHITE_LILY, PatchState.GROWING, 0);
		assertDecodes(PatchType.FLOWER, 230, Crop.WHITE_LILY, PatchState.DEAD, 1);
		// A placed scarecrow counts as growing, so its patch never comes due
		assertEquals(PatchState.GROWING, PatchStateDecoder.decode(PatchType.FLOWER, 33).getState());
		assertEquals(Crop.SCARECROW, PatchStateDecoder.decode(PatchType.FLOWER, 36).getCrop());
	}

	@Test
	public void hopsValues()
	{
		assertDecodes(PatchType.HOPS, 4, Crop.HAMMERSTONE, PatchState.GROWING, 0);
		// Lives left count down
		assertDecodes(PatchType.HOPS, 8, Crop.HAMMERSTONE, PatchState.HARVESTABLE, 2);
		assertDecodes(PatchType.HOPS, 11, Crop.HAMMERSTONE, PatchState.DISEASED, 1);
		assertDecodes(PatchType.HOPS, 139, Crop.HAMMERSTONE, PatchState.DEAD, 1);
		assertDecodes(PatchType.HOPS, 96, Crop.FLAX, PatchState.GROWING, 0);
		assertDecodes(PatchType.HOPS, 241, Crop.HEMP, PatchState.DEAD, 3);
		assertDecodes(PatchType.HOPS, 250, Crop.COTTON, PatchState.DEAD, 1);
	}

	@Test
	public void newTypesAreNotHealthChecked()
	{
		assertTrue(PatchType.TREE.isHealthChecked());
		assertTrue(!PatchType.ALLOTMENT.isHealthChecked());
		assertTrue(PatchType.ALLOTMENT.isProtectable());
		assertTrue(!PatchType.FLOWER.isProtectable());
		assertEquals("Hops", PatchType.HOPS.getPluralName());
	}

	private static void assertDecodes(PatchType type, int value, Crop crop, PatchState state, int stage)
	{
		final DecodedPatch decoded = PatchStateDecoder.decode(type, value);
		assertEquals(type + " " + value, crop, decoded.getCrop());
		assertEquals(type + " " + value, state, decoded.getState());
		assertEquals(type + " " + value, stage, decoded.getStage());
	}
}
