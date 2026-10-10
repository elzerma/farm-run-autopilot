package com.farmrunautopilot.run;

import static org.junit.Assert.assertNotNull;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelMethod;
import org.junit.Test;

public class SpellHighlightOverlayTest
{
	@Test
	public void everySpellCastOnARunHasASpellbookButton()
	{
		for (TravelMethod method : TravelMethod.values())
		{
			if (method.getSpell() != null)
			{
				assertNotNull(method + " uses " + method.getSpell(), SpellHighlightOverlay.buttonFor(method.getSpell()));
			}
		}
		assertNotNull(SpellHighlightOverlay.buttonFor(Spell.TELEPORT_TO_HOUSE));
		assertNotNull(SpellHighlightOverlay.buttonFor(Spell.CURE_PLANT));
	}
}
