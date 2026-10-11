package com.farmrunautopilot.data;

import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.RouteStop;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class JourneyStepTest
{
	@Test
	public void everyStepSaysWhatToDo()
	{
		for (TravelMethod method : TravelMethod.values())
		{
			for (JourneyStep step : method.getThen())
			{
				assertNotNull(method.name(), step.getKind());
				assertFalse(method.name(), step.getText().isEmpty());
				assertFalse(method.name(), step.getBrief().isEmpty());
			}
		}
	}

	@Test
	public void routeListSpellsOutTheBoat()
	{
		final RouteStop stop = new RouteStop(Location.BRIMHAVEN, TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT,
			Departure.DIRECT, 40, false);
		assertEquals("Ardougne Teleport, then boat from the Ardougne docks", stop.describeTravel());
	}
}
