package com.farmrunautopilot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.tracking.PatchPrediction;
import org.junit.Test;

/**
 * Sprint 1b: bushes, cacti, mushrooms, belladonna and seaweed, against RuneLite's PatchImplementation and Produce
 * (master, 2026-10-24).
 */
public class SpecialsDataTest
{
	@Test
	public void bushValues()
	{
		assertDecodes(PatchType.BUSH, 5, Crop.REDBERRY, PatchState.GROWING, 0);
		assertDecodes(PatchType.BUSH, 12, Crop.REDBERRY, PatchState.HARVESTABLE, 2);
		assertDecodes(PatchType.BUSH, 59, Crop.WHITEBERRY, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.BUSH, 103, Crop.JANGERBERRY, PatchState.DISEASED, 1);
		assertDecodes(PatchType.BUSH, 180, Crop.WHITEBERRY, PatchState.DEAD, 1);
		assertDecodes(PatchType.BUSH, 225, Crop.POISON_IVY, PatchState.DISEASED, 8);
		// RuneLite's last growing stage, which waits for "Check-health" as a tree's does
		assertDecodes(PatchType.BUSH, 250, Crop.REDBERRY, PatchState.CHECK_HEALTH, 5);
		assertDecodes(PatchType.BUSH, 255, Crop.POISON_IVY, PatchState.CHECK_HEALTH, 8);
		assertEquals(PatchState.WEEDS, PatchStateDecoder.decode(PatchType.BUSH, 64).getState());
	}

	@Test
	public void cactusValues()
	{
		assertDecodes(PatchType.CACTUS, 15, Crop.CACTUS, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.CACTUS, 31, Crop.CACTUS, PatchState.CHECK_HEALTH, 7);
		assertDecodes(PatchType.CACTUS, 45, Crop.POTATO_CACTUS, PatchState.HARVESTABLE, 6);
		assertDecodes(PatchType.CACTUS, 58, Crop.POTATO_CACTUS, PatchState.CHECK_HEALTH, 7);
	}

	@Test
	public void mushroomBelladonnaAndSeaweedValues()
	{
		assertDecodes(PatchType.MUSHROOM, 10, Crop.BITTERCAP, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.MUSHROOM, 21, Crop.BITTERCAP, PatchState.DEAD, 1);
		assertDecodes(PatchType.BELLADONNA, 8, Crop.BELLADONNA, PatchState.HARVESTABLE, 0);
		assertDecodes(PatchType.BELLADONNA, 12, Crop.BELLADONNA, PatchState.DEAD, 1);
		assertDecodes(PatchType.SEAWEED, 9, Crop.GIANT_SEAWEED, PatchState.HARVESTABLE, 1);
		assertDecodes(PatchType.SEAWEED, 11, Crop.GIANT_SEAWEED, PatchState.DISEASED, 1);
	}

	@Test
	public void growthMatchesRuneLite()
	{
		assertEquals(100, Crop.REDBERRY.getGrowthMinutes());
		assertEquals(160, Crop.POISON_IVY.getGrowthMinutes());
		assertEquals(560, Crop.CACTUS.getGrowthMinutes());
		assertEquals(70, Crop.POTATO_CACTUS.getGrowthMinutes());
		assertEquals(240, Crop.BITTERCAP.getGrowthMinutes());
		assertEquals(320, Crop.BELLADONNA.getGrowthMinutes());
		assertEquals(40, Crop.GIANT_SEAWEED.getGrowthMinutes());
		assertEquals(20, Crop.REDBERRY.getRegrowMinutes());
		assertEquals(5, Crop.POTATO_CACTUS.getRegrowMinutes());
		assertEquals(0, Crop.BITTERCAP.getRegrowMinutes());
	}

	@Test
	public void unprotectedTypesHaveNoGardener()
	{
		assertFalse(Patch.CANIFIS_MUSHROOM.hasGardener());
		assertFalse(Patch.DRAYNOR_MANOR_BELLADONNA.hasGardener());
		assertNull(Patch.AUBURNVALE_BELLADONNA.getGardenerName());
		assertEquals("Mernia", Patch.FOSSIL_ISLAND_SEAWEED_NORTH.getGardenerName());
	}

	@Test
	public void aCropPickedAndLeftGrowingIsDueOnceRegrown()
	{
		final PatchPrediction picked = live(Crop.REDBERRY, PatchState.HARVESTABLE, 1);
		final PatchPrediction full = live(Crop.REDBERRY, PatchState.HARVESTABLE, 4);
		// Picked and left growing: due again only with all four berries back
		assertFalse(RunSelector.isDue(picked, true));
		assertTrue(RunSelector.isDue(full, true));
		// Replanted each run: due as soon as it's grown
		assertTrue(RunSelector.isDue(picked, false));
		assertFalse(RunSelector.isDue(live(Crop.REDBERRY, PatchState.GROWING, 2), true));
		assertTrue(RunSelector.isDue(live(Crop.REDBERRY, PatchState.CHECK_HEALTH, 5), true));
	}

	private static PatchPrediction live(Crop crop, PatchState state, int stage)
	{
		return new PatchPrediction(Patch.CHAMPIONS_GUILD_BUSH, crop, state, stage, crop.getStages(), 0, 0, 0,
			PatchPrediction.Source.THIS_PLUGIN);
	}

	private static void assertDecodes(PatchType type, int value, Crop crop, PatchState state, int stage)
	{
		final DecodedPatch decoded = PatchStateDecoder.decode(type, value);
		assertEquals(type + " " + value, crop, decoded.getCrop());
		assertEquals(type + " " + value, state, decoded.getState());
		assertEquals(type + " " + value, stage, decoded.getStage());
	}
}
