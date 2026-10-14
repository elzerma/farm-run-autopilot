package com.farmrunautopilot.data.travel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

/** Sprint 10: a journey step says only the way that fits the player's Agility level. */
public class JourneyStepTest
{
	@Test
	public void theShortcutWhenTheyHaveTheLevel()
	{
		assertEquals("Climb the Rocks beside the entrance up to the roof (73 Agility)",
			JourneyStep.STRONGHOLD_ROOF.textFor(73));
	}

	@Test
	public void theWayRoundWhenTheyDont()
	{
		final String text = JourneyStep.STRONGHOLD_ROOF.textFor(72);
		assertEquals("Enter the stronghold, go through the door in the west wall and Climb-up the ladder to the roof",
			text);
		assertFalse(text.contains("Agility"));
	}

	@Test
	public void noShortcutIsTheSameForEveryone()
	{
		assertEquals(JourneyStep.LEAVE_HOUSE.getText(), JourneyStep.LEAVE_HOUSE.textFor(1));
	}

	@Test
	public void noStepSaysBothWays()
	{
		for (TravelMethod method : TravelMethod.values())
		{
			for (JourneyStep step : method.getThen())
			{
				assertFalse(method.name(), step.getText().contains("without it"));
			}
		}
	}
}
