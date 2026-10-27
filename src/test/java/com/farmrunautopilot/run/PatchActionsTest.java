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
	/** Every item has a noted form here but the dibber and coins. */
	private static int noted(int id)
	{
		return id == ItemID.DIBBER || id == ItemID.COINS ? id : id + 1;
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
	}

	@Test
	public void treatingAndPlantingWait()
	{
		assertTrue(PatchGuide.treatsOrPlants(StepAdvisor.Action.COMPOST));
		assertTrue(PatchGuide.treatsOrPlants(StepAdvisor.Action.PLANT));
		assertTrue(PatchGuide.treatsOrPlants(StepAdvisor.Action.PAY));
		assertFalse(PatchGuide.treatsOrPlants(StepAdvisor.Action.PICK));
		assertFalse(PatchGuide.treatsOrPlants(StepAdvisor.Action.RAKE));
		assertFalse(PatchGuide.treatsOrPlants(StepAdvisor.Action.CHECK_HEALTH));
		assertFalse(PatchGuide.treatsOrPlants(StepAdvisor.Action.PAY_TO_CLEAR));
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
	public void nearbyPatchesGroupByWing()
	{
		final java.util.List<com.farmrunautopilot.data.Patch> guild = com.farmrunautopilot.data.Location.FARMING_GUILD.getPatches();
		final Set<com.farmrunautopilot.data.Patch> group = PatchGuide.nearby(com.farmrunautopilot.data.Patch.FARMING_GUILD_ALLOTMENT_N, guild);
		// The allotment set and its flower work together; the herb, across the guild, waits
		assertTrue(group.contains(com.farmrunautopilot.data.Patch.FARMING_GUILD_ALLOTMENT_S));
		assertTrue(group.contains(com.farmrunautopilot.data.Patch.FARMING_GUILD_FLOWER));
		assertFalse(group.contains(com.farmrunautopilot.data.Patch.FARMING_GUILD_HERB));
	}

	@Test
	public void settingsDefaults()
	{
		final RunConfig config = new RunConfig().sanitise();
		assertTrue(config.isNoteProduce());
		assertEquals(EmptyBuckets.DROP, config.getEmptyBuckets());
		assertEquals(com.farmrunautopilot.settings.StopOrder.PATCH_BY_PATCH, config.getStopOrder());
		config.setEmptyBuckets(null);
		assertEquals(EmptyBuckets.DROP, config.sanitise().getEmptyBuckets());
	}
}
