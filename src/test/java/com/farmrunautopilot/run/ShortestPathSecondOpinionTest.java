package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Shortest Path's answer split into walking and transports, to time it as the plugin does (sprint T8). */
public class ShortestPathSecondOpinionTest
{
	@Test
	public void aTransportsJumpIsntWalking()
	{
		final WorldPoint a = new WorldPoint(3200, 3200, 0);
		final WorldPoint b = new WorldPoint(3203, 3201, 0);
		final WorldPoint landing = new WorldPoint(2800, 3400, 0);
		final WorldPoint c = new WorldPoint(2805, 3400, 0);
		final List<Map<String, Object>> transports = List.of(Map.of("origin", b, "destination", landing));
		// 3 tiles to the transport, the jump, then 5 more
		assertEquals(8, ShortestPathSecondOpinion.walkedTiles(List.of(a, b, landing, c), transports));
	}

	@Test
	public void stairsDontCountAsWalking()
	{
		final WorldPoint down = new WorldPoint(3200, 3200, 0);
		final WorldPoint up = new WorldPoint(3200, 3200, 1);
		final WorldPoint on = new WorldPoint(3204, 3200, 1);
		assertEquals(4, ShortestPathSecondOpinion.walkedTiles(List.of(down, up, on), List.of()));
	}
}
