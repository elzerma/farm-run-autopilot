package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Sprint 4: what to deposit at a bank visit, what to put on, and which patch a compost message means. */
public class BankGuideTest
{
	private static Holdings holdings(Map<Integer, Integer> inventory, Map<Integer, Integer> worn)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.INVENTORY, inventory);
		items.put(Holdings.Source.WORN, worn);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	private static Map<Integer, Integer> items(int... idThenCount)
	{
		final Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idThenCount.length; i += 2)
		{
			m.put(idThenCount[i], idThenCount[i + 1]);
		}
		return m;
	}

	private static SupplyPlan part(SupplyLine... lines)
	{
		return new SupplyPlan(Arrays.asList(lines), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), 0, "", lines.length, Collections.emptyMap());
	}

	private static SupplyLine travel(String name, boolean wear, int... ids)
	{
		return new SupplyLine(SupplyLine.Group.TRAVEL, name, 1, 0, 0, Collections.emptyMap(), null, wear ? 0 : 1,
			false, null, ids, false, wear);
	}

	@Test
	public void depositsWhatTheNextPartDoesntNeedNotedToo()
	{
		final SupplyPlan next = part(new SupplyLine(SupplyLine.Group.SEEDS, "Ranarr seed", 4, 0, 0,
			Collections.emptyMap(), null, 1, false, null, new int[]{ItemID.RANARR_SEED}, true));
		// Holdings count noted herbs as unnoted grimy ranarr; the outline has to match the noted one too
		final Holdings now = holdings(items(ItemID.RANARR_SEED, 4, ItemID.UNIDENTIFIED_RANARR, 8), items());
		final java.util.Set<Integer> deposit = BankGuide.toDeposit(next, now, id -> id + 1);
		assertFalse(deposit.contains(ItemID.RANARR_SEED));
		assertTrue(deposit.contains(ItemID.UNIDENTIFIED_RANARR));
		assertTrue(deposit.contains(ItemID.UNIDENTIFIED_RANARR + 1));
	}

	@Test
	public void wearsTeleportItemsAndTheOutfitOnlyOnceCarried()
	{
		final SupplyPlan part = part(travel("Ring of wealth", true, ItemID.RING_OF_WEALTH_5),
			travel("Ectophial", false, ItemID.ECTOPHIAL));
		// Ring in the inventory, not worn: wear it; the ectophial is used from the inventory
		BankGuide.Wear wear = BankGuide.wear(part, holdings(items(ItemID.RING_OF_WEALTH_5, 1, ItemID.ECTOPHIAL, 1),
			items()), Outfit.NONE);
		assertEquals("Wear: Ring of wealth", wear.getText());
		assertEquals(Collections.singleton(ItemID.RING_OF_WEALTH_5), wear.getItemIds());
		// Already worn: nothing to say
		assertNull(BankGuide.wear(part, holdings(items(), items(ItemID.RING_OF_WEALTH_5, 1)), Outfit.NONE).getText());
		// Still in the bank: the bank tab asks for it, not the wear line
		assertNull(BankGuide.wear(part, holdings(items(), items()), Outfit.NONE).getText());
	}

	@Test
	public void compostMessageNamesThePatch()
	{
		final List<Patch> catherby = Location.CATHERBY.getPatches();
		assertEquals(Patch.CATHERBY_HERB, PatchGuide.patchNamed("You treat the herb patch with ultracompost.", catherby));
		assertEquals(Patch.CATHERBY_FRUIT_TREE,
			PatchGuide.patchNamed("You treat the fruit tree patch with ultracompost.", catherby));
		assertNull(PatchGuide.patchNamed("You treat the allotment with ultracompost.", catherby));
	}
}
