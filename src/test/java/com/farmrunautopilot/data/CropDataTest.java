package com.farmrunautopilot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class CropDataTest
{
	@Test
	public void cropCounts()
	{
		assertEquals(5, countOfType(PatchType.TREE));
		assertEquals(8, countOfType(PatchType.FRUIT_TREE));
		assertEquals(15, countOfType(PatchType.HERB));
	}

	@Test
	public void growthTimesMatchTheWiki()
	{
		assertEquals(160, Crop.OAK.getGrowthMinutes());
		assertEquals(240, Crop.WILLOW.getGrowthMinutes());
		assertEquals(320, Crop.MAPLE.getGrowthMinutes());
		assertEquals(400, Crop.YEW.getGrowthMinutes());
		assertEquals(480, Crop.MAGIC.getGrowthMinutes());
		for (Crop crop : Crop.values())
		{
			if (crop.getType() == PatchType.FRUIT_TREE)
			{
				assertEquals(crop.name(), 16 * 60, crop.getGrowthMinutes());
			}
			else if (crop.getType() == PatchType.HERB)
			{
				assertEquals(crop.name(), 80, crop.getGrowthMinutes());
			}
		}
	}

	@Test
	public void levelsRiseWithinEachType()
	{
		for (PatchType type : PatchType.values())
		{
			int previous = 0;
			for (Crop crop : Crop.values())
			{
				if (crop.getType() == type)
				{
					assertTrue(crop.name() + " out of level order", crop.getFarmingLevel() > previous);
					previous = crop.getFarmingLevel();
				}
			}
		}
	}

	@Test
	public void paymentsOnlyForProtectableTypes()
	{
		for (Crop crop : Crop.values())
		{
			if (crop.getType().isProtectable())
			{
				assertTrue(crop.name(), crop.hasPayment());
				assertTrue(crop.name(), crop.getPaymentQuantity() > 0);
			}
			else
			{
				assertFalse(crop.name(), crop.hasPayment());
				assertEquals(crop.name(), 0, crop.getPaymentQuantity());
			}
		}
	}

	@Test
	public void plantItemsAreUnique()
	{
		final Set<Integer> seen = new HashSet<>();
		for (Crop crop : Crop.values())
		{
			assertTrue("duplicate plant item for " + crop, seen.add(crop.getPlantItemId()));
		}
	}

	@Test
	public void huascaIsLevel65()
	{
		assertEquals(65, Crop.HUASCA.getFarmingLevel());
	}

	private static long countOfType(PatchType type)
	{
		return Arrays.stream(Crop.values()).filter(c -> c.getType() == type).count();
	}
}
