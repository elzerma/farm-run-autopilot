package com.farmrunautopilot.data.travel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class TravelDataTest
{
	@Test
	public void methodsCarryWhatTheirKindNeeds()
	{
		for (TravelMethod method : TravelMethod.values())
		{
			final String name = method.name();
			switch (method.getKind())
			{
				case SPELL:
				case HOUSE_PORTAL:
					assertNotNull(name, method.getSpell());
					assertNull(name, method.getItem());
					break;
				case FAIRY_RING:
					assertTrue(name, method.getDisplayName().matches("Fairy ring [A-D][I-L][P-S]"));
					break;
				case SPIRIT_TREE:
				case BALLOON:
					assertNull(name, method.getSpell());
					assertNull(name, method.getItem());
					break;
				default:
					assertNotNull(name, method.getItem());
					assertNull(name, method.getSpell());
			}
			if (method.getJewelleryBox() != null)
			{
				assertEquals(name, TravelKind.JEWELLERY, method.getKind());
			}
			if (method.getKind() == TravelKind.HOUSE_PORTAL)
			{
				assertEquals(name, Spell.TELEPORT_TO_HOUSE, method.getSpell());
			}
		}
	}

	@Test
	public void spellRunesArePositiveAndNotRepeated()
	{
		for (Spell spell : Spell.values())
		{
			final Set<Rune> seen = EnumSet.noneOf(Rune.class);
			for (RuneAmount amount : spell.getRunes())
			{
				assertTrue(spell.name(), amount.getQuantity() > 0);
				assertTrue(spell + " lists " + amount.getRune() + " twice", seen.add(amount.getRune()));
			}
			assertTrue(spell.name(), spell.getMagicLevel() >= 1 && spell.getMagicLevel() <= 99);
		}
		assertTrue(Spell.LUMBRIDGE_HOME_TELEPORT.getRunes().isEmpty());
		assertFalse(Spell.CATHERBY_TELEPORT.getRunes().isEmpty());
	}

	@Test
	public void tabletsAreUnique()
	{
		final Set<Integer> seen = new HashSet<>();
		for (Spell spell : Spell.values())
		{
			if (spell.hasTablet())
			{
				assertTrue("duplicate tablet for " + spell, seen.add(spell.getTabletItemId()));
			}
		}
	}

	@Test
	public void travelItemIdsAreUniqueAcrossItems()
	{
		final Set<Integer> seen = new HashSet<>();
		for (TravelItem item : TravelItem.values())
		{
			assertTrue(item.name(), item.getItemIds().length > 0);
			for (int id : item.getItemIds())
			{
				assertTrue(item + " repeats item " + id, seen.add(id));
			}
		}
	}

	@Test
	public void catherbyRuneCostMatchesSpec()
	{
		assertEquals(3, Spell.CATHERBY_TELEPORT.getRunes().size());
		assertEquals(RuneAmount.of(10, Rune.WATER), Spell.CATHERBY_TELEPORT.getRunes().get(0));
	}
}
