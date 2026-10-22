package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.travel.Spell;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SideTabTest
{
	private static final IntPredicate NOTHING = id -> false;

	private static IntPredicate holding(int... ids)
	{
		final Set<Integer> held = new HashSet<>();
		for (int id : ids)
		{
			held.add(id);
		}
		return held::contains;
	}

	private static Highlights items(int... ids)
	{
		final Set<Integer> set = new HashSet<>();
		for (int id : ids)
		{
			set.add(id);
		}
		return new Highlights(null, false, null, set, null);
	}

	@Test
	public void aSpellNeedsTheSpellbook()
	{
		final Highlights spell = new Highlights(null, false, null, Collections.emptySet(), Spell.FALADOR_TELEPORT);
		assertEquals(SideTab.SPELLBOOK, SideTab.needed(spell, NOTHING, NOTHING, true, true));
	}

	@Test
	public void noSpellTabWhenSpellsAreNotHighlighted()
	{
		final Highlights spell = new Highlights(null, false, null, Collections.emptySet(), Spell.FALADOR_TELEPORT);
		assertNull(SideTab.needed(spell, NOTHING, NOTHING, true, false));
	}

	@Test
	public void aTabletInTheInventoryNeedsTheInventory()
	{
		assertEquals(SideTab.INVENTORY, SideTab.needed(items(ItemID.POH_TABLET_FALADORTELEPORT),
			holding(ItemID.POH_TABLET_FALADORTELEPORT), NOTHING, true, true));
	}

	@Test
	public void wornJewelleryNeedsWornEquipment()
	{
		assertEquals(SideTab.EQUIPMENT, SideTab.needed(items(ItemID.RING_OF_DUELING_8), NOTHING,
			holding(ItemID.RING_OF_DUELING_8), true, true));
	}

	@Test
	public void theInventoryWinsWhenTheItemIsInBoth()
	{
		assertEquals(SideTab.INVENTORY, SideTab.needed(items(ItemID.RING_OF_DUELING_8),
			holding(ItemID.RING_OF_DUELING_8), holding(ItemID.RING_OF_DUELING_8), true, true));
	}

	@Test
	public void anItemToPutOnNeedsTheInventory()
	{
		final Highlights wear = items().withWear(Collections.singleton(ItemID.RING_OF_DUELING_8));
		assertEquals(SideTab.INVENTORY, SideTab.needed(wear, holding(ItemID.RING_OF_DUELING_8), NOTHING, true,
			true));
	}

	@Test
	public void aWornItemToUseComesBeforeOneToPutOn()
	{
		// The Ardougne cloak worn for the teleport, the Graceful cape in the inventory to wear
		final Highlights both = items(ItemID.ARDY_CAPE_EASY).withWear(Collections.singleton(ItemID.GRACEFUL_CAPE));
		assertEquals(SideTab.EQUIPMENT, SideTab.needed(both, holding(ItemID.GRACEFUL_CAPE),
			holding(ItemID.ARDY_CAPE_EASY), true, true));
	}

	@Test
	public void anItemToUseInTheInventoryStillNeedsTheInventoryWithOneToPutOn()
	{
		final Highlights both = items(ItemID.POH_TABLET_FALADORTELEPORT).withWear(
			Collections.singleton(ItemID.GRACEFUL_CAPE));
		assertEquals(SideTab.INVENTORY, SideTab.needed(both,
			holding(ItemID.POH_TABLET_FALADORTELEPORT, ItemID.GRACEFUL_CAPE), NOTHING, true, true));
	}

	@Test
	public void anItemToPutOnNeedsTheInventoryWhenTheItemToUseIsNotCarried()
	{
		final Highlights both = items(ItemID.ARDY_CAPE_EASY).withWear(Collections.singleton(ItemID.GRACEFUL_CAPE));
		assertEquals(SideTab.INVENTORY, SideTab.needed(both, holding(ItemID.GRACEFUL_CAPE), NOTHING, true, true));
	}

	@Test
	public void noTabForAnItemNotCarriedOrWhenItemsAreNotHighlighted()
	{
		assertNull(SideTab.needed(items(ItemID.RING_OF_DUELING_8), NOTHING, NOTHING, true, true));
		assertNull(SideTab.needed(items(ItemID.RING_OF_DUELING_8), holding(ItemID.RING_OF_DUELING_8), NOTHING,
			false, true));
		assertNull(SideTab.needed(Highlights.NONE, NOTHING, NOTHING, true, true));
	}

	@Test
	public void openOnlyWhenThePanelIsThatTab()
	{
		assertTrue(SideTab.INVENTORY.isOpen(3));
		assertTrue(SideTab.EQUIPMENT.isOpen(4));
		assertTrue(SideTab.SPELLBOOK.isOpen(6));
		assertFalse(SideTab.INVENTORY.isOpen(5));
		// The side panel closed (resizable layouts)
		assertFalse(SideTab.SPELLBOOK.isOpen(-1));
	}

	@Test
	public void everyTabHasAnIconInEachLayout()
	{
		for (SideTab tab : SideTab.values())
		{
			assertEquals(tab.name(), 3, tab.getIcons().length);
		}
	}
}
