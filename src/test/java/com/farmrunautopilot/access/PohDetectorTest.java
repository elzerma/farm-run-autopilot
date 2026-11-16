package com.farmrunautopilot.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.travel.atlas.AtlasHouse;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.SettingsStore;
import com.google.gson.Gson;
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
	public void theMountedXericsTalismanIsFoundInTheHouse()
	{
		// Its object and one for each place its left-click can be set to; not the digsite pendant's beside it
		for (int id : new int[]{ObjectID.POH_AMULET_XERIC, ObjectID.POH_AMULET_XERIC_LOOKOUT,
			ObjectID.POH_AMULET_XERIC_GLADE, ObjectID.POH_AMULET_XERIC_INFERNO, ObjectID.POH_AMULET_XERIC_HEART,
			ObjectID.POH_AMULET_XERIC_HONOUR})
		{
			assertTrue(String.valueOf(id), PohDetector.isXericsTalisman(id));
		}
		assertFalse(PohDetector.isXericsTalisman(ObjectID.POH_AMULET_DIGSITE));
		assertFalse(PohDetector.isXericsTalisman(ObjectID.POH_AMULET_DIG_FOSSIL));
		assertFalse(PohDetector.isXericsTalisman(ObjectID.POH_NEXUS_PORTAL_1));

		final SettingsStore settings = new SettingsStore(null, new Gson());
		settings.getAccount().setAutoDetectHouse(true);
		final PohDetector detector = new PohDetector(null, settings, null);
		detector.onHouseLoading();
		detector.onSceneLoading(true);
		detector.onObjectSpawned(ObjectID.POH_AMULET_XERIC_GLADE);
		assertTrue(detector.hasFoundXericsTalisman());

		// Rescan clears it with the rest of the detected furniture
		final PohSetup poh = new PohSetup();
		poh.setXericsTalisman(true);
		PohDetector.clearDetected(poh);
		assertFalse(poh.isXericsTalisman());
	}

	@Test
	public void eachWayOutOfTheHouseIsItsFurniture()
	{
		// What the guide outlines in the house for each way out (the talisman as elzerma's house shows it, 2026-11-15)
		assertTrue(PohDetector.isWayOut(AtlasHouse.ExitKind.XERICS_TALISMAN, ObjectID.POH_AMULET_XERIC_GLADE));
		assertTrue(PohDetector.isWayOut(AtlasHouse.ExitKind.NEXUS, ObjectID.POH_NEXUS_PORTAL_3));
		assertTrue(PohDetector.isWayOut(AtlasHouse.ExitKind.JEWELLERY_BOX, ObjectID.POH_JEWELLERY_BOX_2));
		// The spirit ring is both
		assertTrue(PohDetector.isWayOut(AtlasHouse.ExitKind.FAIRY_RING, ObjectID.POH_SPIRIT_RING));
		assertTrue(PohDetector.isWayOut(AtlasHouse.ExitKind.SPIRIT_TREE, ObjectID.POH_SPIRIT_RING));
		assertFalse(PohDetector.isWayOut(AtlasHouse.ExitKind.FAIRY_RING, ObjectID.POH_SPIRIT_TREE));
		assertFalse(PohDetector.isWayOut(AtlasHouse.ExitKind.NEXUS, ObjectID.POH_AMULET_XERIC_GLADE));
		// The front door is a journey step of its own, and the pool is no way out
		assertFalse(PohDetector.isWayOut(AtlasHouse.ExitKind.PORTAL, ObjectID.POH_EXIT_PORTAL));
		assertFalse(PohDetector.isAnyWayOut(ObjectID.POH_POOL_RESTORATION));
		assertTrue(PohDetector.isAnyWayOut(ObjectID.POH_AMULET_XERIC_HEART));
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
		final SettingsStore settings = new SettingsStore(null, new Gson());
		settings.getAccount().setAutoDetectHouse(true);
		final PohDetector detector = new PohDetector(null, settings, null);
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

		// Beta switched off: even the player's own house isn't scanned
		settings.getAccount().setAutoDetectHouse(false);
		detector.reset();
		detector.onHouseLoading();
		detector.onSceneLoading(true);
		detector.onObjectSpawned(ObjectID.POH_FAIRY_RING);
		assertFalse(detector.hasFound());
	}

	@Test
	public void aHouseWithNoneOfTheFurnitureStillCountsAsScanned()
	{
		final SettingsStore settings = new SettingsStore(null, new Gson());
		settings.getAccount().setAutoDetectHouse(true);
		final PohDetector detector = new PohDetector(null, settings, null);
		detector.onHouseLoading();
		assertFalse(detector.isScanDue());
		// The house's scene loads with nothing the detector looks for in it
		detector.onSceneLoading(true);
		assertTrue(detector.isScanDue());
		assertFalse(detector.hasFound());

		// With the beta off, the house isn't scanned at all
		detector.reset();
		settings.getAccount().setAutoDetectHouse(false);
		detector.onHouseLoading();
		detector.onSceneLoading(true);
		assertFalse(detector.isScanDue());

		// Leaving before the next tick drops the scan
		settings.getAccount().setAutoDetectHouse(true);
		detector.onHouseLoading();
		detector.onSceneLoading(true);
		detector.onSceneLoading(false);
		assertFalse(detector.isScanDue());
	}
}
