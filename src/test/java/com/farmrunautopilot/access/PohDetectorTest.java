package com.farmrunautopilot.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PoolTier;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

public class PohDetectorTest
{
	@Test
	public void furnitureMapsToTiers()
	{
		assertEquals(JewelleryBoxTier.BASIC, PohDetector.jewelleryBoxFor(ObjectID.POH_JEWELLERY_BOX_1));
		assertEquals(JewelleryBoxTier.ORNATE, PohDetector.jewelleryBoxFor(ObjectID.POH_JEWELLERY_BOX_3));
		assertEquals(PoolTier.RESTORATION, PohDetector.poolFor(ObjectID.POH_POOL_RESTORATION));
		assertEquals(PoolTier.ORNATE_REJUVENATION, PohDetector.poolFor(ObjectID.POH_POOL_REGENERATION));
		assertNull(PohDetector.jewelleryBoxFor(ObjectID.POH_FAIRY_RING));
		assertNull(PohDetector.poolFor(ObjectID.POH_JEWELLERY_BOX_2));
	}

	@Test
	public void tellsSomeoneElsesHouseFromHowThePlayerGotIn()
	{
		// Someone else's
		assertEquals(Boolean.TRUE, PohDetector.someoneElsesHouse("Friend's house", "Portal", null));
		assertEquals(Boolean.TRUE, PohDetector.someoneElsesHouse("View", "House Advertisement", null));
		assertEquals(Boolean.TRUE, PohDetector.someoneElsesHouse("Continue", "", "Go to a friend's house."));
		// The player's own
		assertEquals(Boolean.FALSE, PohDetector.someoneElsesHouse("Home", "Portal", null));
		assertEquals(Boolean.FALSE, PohDetector.someoneElsesHouse("Build mode", "Portal", null));
		assertEquals(Boolean.FALSE, PohDetector.someoneElsesHouse("Break", "Teleport to house", null));
		assertEquals(Boolean.FALSE, PohDetector.someoneElsesHouse("Cast", "Teleport to House", null));
		assertEquals(Boolean.FALSE, PohDetector.someoneElsesHouse("Tele to POH", "Construct. cape", null));
		assertEquals(Boolean.FALSE, PohDetector.someoneElsesHouse("Continue", "", "Go to your house."));
		// Anything else says nothing
		assertNull(PohDetector.someoneElsesHouse("Walk here", "", null));
	}

	@Test
	public void onlyLooksInsideAHouse()
	{
		final PohDetector detector = new PohDetector(null, null, null);
		// Outside a house, even a matching object is ignored
		detector.onObjectSpawned(ObjectID.POH_FAIRY_RING);
		assertFalse(detector.hasFound());

		// The house loading screen comes first, then the house's scene (an instance) and its objects
		detector.onHouseLoading();
		detector.onSceneLoading(true);
		detector.onObjectSpawned(ObjectID.POH_FAIRY_RING);
		assertTrue(detector.hasFound());

		// Leaving: the next scene isn't an instance
		detector.reset();
		detector.onHouseLoading();
		detector.onSceneLoading(false);
		detector.onObjectSpawned(ObjectID.POH_FAIRY_RING);
		assertFalse(detector.hasFound());
	}
}
