package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;

public class CropAllocatorTest
{
	private static final List<Patch> HERBS = Arrays.asList(Patch.FALADOR_HERB, Patch.CATHERBY_HERB, Patch.ARDOUGNE_HERB,
		Patch.TROLL_STRONGHOLD_HERB, Patch.WEISS_HERB, Patch.HARMONY_ISLAND_HERB);
	private static final Set<Patch> DISEASE_FREE = EnumSet.of(Patch.TROLL_STRONGHOLD_HERB, Patch.WEISS_HERB,
		Patch.HARMONY_ISLAND_HERB);
	private static final Predicate<Patch> SAFE = DISEASE_FREE::contains;
	private static final List<Crop> TOADFLAX_THEN_GUAM = Arrays.asList(Crop.TOADFLAX, Crop.GUAM);
	private static final List<Crop> NONE_RESERVED = Collections.emptyList();

	private static Map<Crop, Integer> stock(Object... cropThenCount)
	{
		final Map<Crop, Integer> stock = new HashMap<>();
		for (int i = 0; i < cropThenCount.length; i += 2)
		{
			stock.put((Crop) cropThenCount[i], (Integer) cropThenCount[i + 1]);
		}
		return stock;
	}

	private static Map<Patch, Crop> allocate(List<Crop> choices, Map<Crop, Integer> stock, List<Crop> reserved)
	{
		return CropAllocator.allocate(HERBS, choices, c -> stock.getOrDefault(c, 0), SAFE, reserved);
	}

	private static int count(Map<Patch, Crop> plan, Crop crop)
	{
		return Collections.frequency(plan.values(), crop);
	}

	@Test
	public void singleChoiceFillsEveryPatch()
	{
		final Map<Patch, Crop> plan = allocate(Collections.singletonList(Crop.RANARR), stock(Crop.RANARR, 1), NONE_RESERVED);
		assertEquals(6, count(plan, Crop.RANARR));
	}

	@Test
	public void shortStockFallsBackInOrderAndSafePatchesGetTheBest()
	{
		final List<Crop> choices = Arrays.asList(Crop.RANARR, Crop.TOADFLAX, Crop.GUAM);
		final Map<Patch, Crop> plan = allocate(choices, stock(Crop.RANARR, 3, Crop.TOADFLAX, 2, Crop.GUAM, 10),
			NONE_RESERVED);
		// Three ranarr go to the three disease-free patches.
		for (Patch patch : DISEASE_FREE)
		{
			assertEquals(Crop.RANARR, plan.get(patch));
		}
		assertEquals(2, count(plan, Crop.TOADFLAX));
		assertEquals(1, count(plan, Crop.GUAM));
	}

	@Test
	public void whenEverythingRunsOutTheFirstChoiceIsShort()
	{
		final List<Crop> choices = Arrays.asList(Crop.RANARR, Crop.TOADFLAX, Crop.GUAM);
		final Map<Patch, Crop> plan = allocate(choices, stock(Crop.RANARR, 1, Crop.TOADFLAX, 1, Crop.GUAM, 1),
			NONE_RESERVED);
		assertEquals(1, count(plan, Crop.TOADFLAX));
		assertEquals(1, count(plan, Crop.GUAM));
		assertEquals(4, count(plan, Crop.RANARR));
	}

	@Test
	public void reservedHerbsGoOnlyToDiseaseFreePatchesBestFirst()
	{
		// Snapdragon ranked above ranarr. Plenty of both, but other patches never get them.
		final Map<Patch, Crop> plan = allocate(TOADFLAX_THEN_GUAM,
			stock(Crop.SNAPDRAGON, 2, Crop.RANARR, 10, Crop.TOADFLAX, 10),
			Arrays.asList(Crop.SNAPDRAGON, Crop.RANARR));
		assertEquals(2, count(plan, Crop.SNAPDRAGON));
		assertEquals(1, count(plan, Crop.RANARR));
		assertEquals(3, count(plan, Crop.TOADFLAX));
		assertEquals(Crop.TOADFLAX, plan.get(Patch.CATHERBY_HERB));
	}

	@Test
	public void diseaseFreePatchesFallBackToNormalChoicesWhenReservedRunOut()
	{
		final Map<Patch, Crop> plan = allocate(TOADFLAX_THEN_GUAM, stock(Crop.RANARR, 1, Crop.TOADFLAX, 10),
			Collections.singletonList(Crop.RANARR));
		assertEquals(1, count(plan, Crop.RANARR));
		assertEquals(5, count(plan, Crop.TOADFLAX));
	}

	@Test
	public void reservedFirstChoiceIsKeptOutOfRiskyPatches()
	{
		// Ranarr is both the 1st choice and reserved: risky patches get the 2nd choice.
		final Map<Patch, Crop> plan = allocate(Arrays.asList(Crop.RANARR, Crop.TOADFLAX),
			stock(Crop.RANARR, 10, Crop.TOADFLAX, 10), Collections.singletonList(Crop.RANARR));
		assertEquals(3, count(plan, Crop.RANARR));
		assertEquals(Crop.TOADFLAX, plan.get(Patch.FALADOR_HERB));
	}

	@Test
	public void resultKeepsThePatchOrder()
	{
		final Map<Patch, Crop> plan = allocate(TOADFLAX_THEN_GUAM, stock(Crop.TOADFLAX, 5), NONE_RESERVED);
		assertEquals(HERBS, Arrays.asList(plan.keySet().toArray(new Patch[0])));
	}
}
