package com.farmrunautopilot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class PatchDataTest
{
	@Test
	public void catalogueHas24Patches()
	{
		assertEquals(24, Patch.values().length);
		assertEquals(7, countOfType(PatchType.TREE));
		assertEquals(7, countOfType(PatchType.FRUIT_TREE));
		assertEquals(10, countOfType(PatchType.HERB));
	}

	@Test
	public void regionAndVarbitPairsAreUnique()
	{
		final Set<String> seen = new HashSet<>();
		for (Patch patch : Patch.values())
		{
			assertTrue("duplicate region/varbit for " + patch, seen.add(patch.getRegionId() + "." + patch.getVarbitId()));
		}
	}

	@Test
	public void varbitsAreFarmingTransmitVarbits()
	{
		final List<Integer> transmit = Arrays.asList(VarbitID.FARMING_TRANSMIT_A, VarbitID.FARMING_TRANSMIT_B,
			VarbitID.FARMING_TRANSMIT_D, VarbitID.FARMING_TRANSMIT_E, VarbitID.FARMING_TRANSMIT_G,
			VarbitID.FARMING_TRANSMIT_K);
		for (Patch patch : Patch.values())
		{
			assertTrue(patch.name(), transmit.contains(patch.getVarbitId()));
		}
	}

	@Test
	public void extraRegionsNeverRepeatTheMainRegion()
	{
		for (Patch patch : Patch.values())
		{
			final Set<Integer> seen = new HashSet<>();
			seen.add(patch.getRegionId());
			for (int region : patch.getExtraRegionIds())
			{
				assertTrue(patch + " repeats region " + region, seen.add(region));
			}
		}
	}

	@Test
	public void gardenersOnlyOnProtectablePatches()
	{
		for (Patch patch : Patch.values())
		{
			if (patch.getType().isProtectable())
			{
				assertTrue(patch.name(), patch.hasGardener());
				assertNotNull(patch.name(), patch.getGardenerName());
			}
			else
			{
				assertFalse(patch.name(), patch.hasGardener());
				assertNull(patch.name(), patch.getGardenerName());
			}
		}
	}

	@Test
	public void gardenerNpcsAreUnique()
	{
		final Set<Integer> seen = new HashSet<>();
		for (Patch patch : Patch.values())
		{
			if (patch.hasGardener())
			{
				assertTrue("duplicate gardener for " + patch, seen.add(patch.getGardenerNpcId()));
			}
		}
	}

	@Test
	public void everyLocationHasAtMostOnePatchPerType()
	{
		for (Location location : Location.values())
		{
			final List<Patch> patches = location.getPatches();
			assertFalse(location + " has no patches", patches.isEmpty());

			final Set<PatchType> types = EnumSet.noneOf(PatchType.class);
			for (Patch patch : patches)
			{
				assertTrue(location + " has two " + patch.getType() + " patches", types.add(patch.getType()));
			}
		}
	}

	@Test
	public void everyLocationHasAToolLeprechaun()
	{
		for (Location location : Location.values())
		{
			assertTrue(location.name(), location.isHasToolLeprechaun());
		}
	}

	@Test
	public void multiPatchLocationsHaveAnInternalWalk()
	{
		for (Location location : Location.values())
		{
			assertEquals(location.name(), location.getPatches().size() > 1, location.getInternalWalk() != null);
		}
	}

	@Test
	public void farmingGuildPatches()
	{
		assertEquals(3, Location.FARMING_GUILD.getPatches().size());
		for (Patch patch : Location.FARMING_GUILD.getPatches())
		{
			assertEquals(4922, patch.getRegionId());
		}
		assertEquals(85, Patch.FARMING_GUILD_FRUIT_TREE.getRequirements().get(0).getLevel());
	}

	@Test
	public void catherbyBoundsSplitHerbAndFruitTree()
	{
		// East of x 2840 in the shared region: fruit tree varbits, not herb.
		final WorldPoint beach = new WorldPoint(2850, 3445, 0);
		assertFalse(Patch.CATHERBY_HERB.isInBounds(beach));
		assertTrue(Patch.CATHERBY_FRUIT_TREE.isInBounds(beach));

		// At the herb patch itself.
		final WorldPoint farm = new WorldPoint(2813, 3463, 0);
		assertTrue(Patch.CATHERBY_HERB.isInBounds(farm));
	}

	@Test
	public void faladorHerbBoundsExcludePortSarim()
	{
		assertTrue(Patch.FALADOR_HERB.isInBounds(new WorldPoint(3058, 3310, 0)));
		assertFalse(Patch.FALADOR_HERB.isInBounds(new WorldPoint(3058, 3260, 0)));
	}

	@Test
	public void diseaseFreeRules()
	{
		assertTrue(Patch.TROLL_STRONGHOLD_HERB.isAlwaysDiseaseFree());
		assertTrue(Patch.WEISS_HERB.isAlwaysDiseaseFree());
		assertFalse(Patch.CATHERBY_HERB.isAlwaysDiseaseFree());
		assertNull(Patch.CATHERBY_HERB.getDiseaseFreeRequirement());
		assertEquals(Requirement.Kind.DIARY, Patch.FALADOR_TREE.getDiseaseFreeRequirement().getKind());
		assertEquals("Needs Elite Falador diary", Patch.FALADOR_TREE.getDiseaseFreeRequirement().describe());
	}

	@Test
	public void sharedRegionPatchesUseDifferentVarbits()
	{
		assertEquals(Patch.GNOME_STRONGHOLD_TREE.getRegionId(), Patch.GNOME_STRONGHOLD_FRUIT_TREE.getRegionId());
		assertNotEquals(Patch.GNOME_STRONGHOLD_TREE.getVarbitId(), Patch.GNOME_STRONGHOLD_FRUIT_TREE.getVarbitId());
	}

	private static long countOfType(PatchType type)
	{
		return Arrays.stream(Patch.values()).filter(p -> p.getType() == type).count();
	}
}
