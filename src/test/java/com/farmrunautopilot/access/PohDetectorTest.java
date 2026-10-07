package com.farmrunautopilot.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
}
