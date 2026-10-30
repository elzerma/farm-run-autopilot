package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.settings.EmptyBuckets;
import com.farmrunautopilot.settings.RunConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Patch actions P1 (docs/plans/patch-actions.md): the stop's produce, its order of work and the new settings. */
public class PatchActionsTest
{
	/** Every item has a noted form here but the dibber and coins, and the notes themselves. */
	private static boolean noted(int id)
	{
		return id != ItemID.DIBBER && id != ItemID.COINS && id != ItemID.Cert.UNIDENTIFIED_IRIT && id != ItemID.Cert.WATERMELON;
	}

	@Test
	public void produceIsWhatWasGainedAndCanBeNoted()
	{
		final Map<Integer, Integer> before = new HashMap<>();
		before.put(ItemID.UNIDENTIFIED_RANARR, 2);
		before.put(ItemID.DIBBER, 1);
		final Map<Integer, Integer> now = new HashMap<>(before);
		now.put(ItemID.UNIDENTIFIED_RANARR, 9);
		now.put(ItemID.WATERMELON, 12);
		now.put(ItemID.WEEDS, 3);
		now.put(ItemID.COINS, 50);
		now.put(ItemID.BUCKET_EMPTY, 2);
		now.put(ItemID.BUCKET_ULTRACOMPOST, 3);
		now.put(ItemID.RAKE, 1);
		final Set<Integer> produce = PatchGuide.produceGained(before, now, PatchActionsTest::noted);
		assertTrue(produce.contains(ItemID.UNIDENTIFIED_RANARR));
		assertTrue(produce.contains(ItemID.WATERMELON));
		// Weeds are dropped, coins and the dibber can't be noted, and nothing gained is no produce
		assertFalse(produce.contains(ItemID.WEEDS));
		assertFalse(produce.contains(ItemID.COINS));
		assertFalse(produce.contains(ItemID.DIBBER));
		// Nor what composting leaves or the leprechaun gives back
		assertFalse(produce.contains(ItemID.BUCKET_EMPTY));
		assertFalse(produce.contains(ItemID.BUCKET_ULTRACOMPOST));
		assertFalse(produce.contains(ItemID.RAKE));
		assertTrue(PatchGuide.produceGained(before, before, PatchActionsTest::noted).isEmpty());

		// Once noted on the leprechaun, the notes gained aren't produce, so the Note step clears (elzerma, 2026-10-28)
		final Map<Integer, Integer> noted = new HashMap<>(before);
		noted.put(ItemID.Cert.UNIDENTIFIED_IRIT, 11);
		noted.put(ItemID.Cert.WATERMELON, 20);
		assertTrue(PatchGuide.produceGained(before, noted, PatchActionsTest::noted).isEmpty());
	}

	@Test
	public void onlyPickingGivesProduce()
	{
		assertTrue(PatchGuide.harvests(StepAdvisor.Action.PICK));
		assertTrue(PatchGuide.harvests(StepAdvisor.Action.CHECK_HEALTH));
		// A staff taken off or compost taken out between patches isn't produce (elzerma, 2026-10-28)
		assertFalse(PatchGuide.harvests(StepAdvisor.Action.NOTE));
		assertFalse(PatchGuide.harvests(StepAdvisor.Action.TAKE_COMPOST));
		assertFalse(PatchGuide.harvests(StepAdvisor.Action.COMPOST));
		assertFalse(PatchGuide.harvests(null));
	}

	@Test
	public void checkScreenshotNamesAreSafe()
	{
		assertEquals("Civitas illa Fortis (Ortus Farm) - note step",
			com.farmrunautopilot.testing.CheckShots.safe("Civitas illa Fortis (Ortus Farm) - note step"));
		assertEquals("Port Phasmatys - ab", com.farmrunautopilot.testing.CheckShots.safe("Port Phasmatys - a/b? ..."));
	}

	@Test
	public void compostNotNeededHereIsSpare()
	{
		// elzerma, 2026-10-28: 7 supercompost from the bin and ultracompost for the two allotments still to treat
		final Map<Integer, Integer> inventory = new HashMap<>();
		inventory.put(ItemID.BUCKET_SUPERCOMPOST, 7);
		inventory.put(ItemID.BUCKET_ULTRACOMPOST, 2);
		inventory.put(ItemID.BUCKET_EMPTY, 16);
		final Map<com.farmrunautopilot.settings.Compost, Integer> need = new java.util.EnumMap<>(com.farmrunautopilot.settings.Compost.class);
		need.put(com.farmrunautopilot.settings.Compost.ULTRACOMPOST, 2);
		final Map<com.farmrunautopilot.settings.Compost, Integer> spare = PatchGuide.spareCompost(inventory, need);
		assertEquals(Integer.valueOf(7), spare.get(com.farmrunautopilot.settings.Compost.SUPERCOMPOST));
		assertFalse(spare.containsKey(com.farmrunautopilot.settings.Compost.ULTRACOMPOST));
		assertEquals(1, spare.size());
	}

	@Test
	public void inspectingATreatedPatchCountsItsCompost()
	{
		assertTrue(PatchGuide.INSPECTED_COMPOSTED.matcher("This is an allotment. The soil has been treated with ultracompost. The "
			+ "patch has watermelons growing in it and is at state 1/9.").matches());
		assertFalse(PatchGuide.INSPECTED_COMPOSTED.matcher("This is a hardwood tree patch. The soil has not been treated. The patch "
			+ "is empty and weeded.").matches());
		assertEquals(com.farmrunautopilot.data.PatchType.ALLOTMENT, PatchGuide.typeNamed("You treat the allotment with ultracompost."));
	}

	@Test
	public void settingsDefaults()
	{
		final RunConfig config = new RunConfig().sanitise();
		assertTrue(config.isNoteProduce());
		assertEquals(EmptyBuckets.DROP, config.getEmptyBuckets());
		config.setEmptyBuckets(null);
		assertEquals(EmptyBuckets.DROP, config.sanitise().getEmptyBuckets());
	}
}
