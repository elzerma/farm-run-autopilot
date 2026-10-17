package com.farmrunautopilot.route;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

/** Issue #8: when the run is planned from the Farming Guild for seeds only in its seed vault. */
public class RunServiceTest
{
	private static SupplyPlan seeds(int carried, int bank, int vault)
	{
		final Map<Holdings.Source, Integer> where = new EnumMap<>(Holdings.Source.class);
		if (carried > 0)
		{
			where.put(Holdings.Source.INVENTORY, carried);
		}
		if (bank > 0)
		{
			where.put(Holdings.Source.BANK, bank);
		}
		if (vault > 0)
		{
			where.put(Holdings.Source.SEED_VAULT, vault);
		}
		final SupplyLine line = new SupplyLine(SupplyLine.Group.SEEDS, "Ranarr seed", 6, carried + bank + vault, carried,
			where, null, 1, false, null, new int[]{1}, true);
		return new SupplyPlan(Collections.singletonList(line), Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), 0, "", 0, Collections.emptyMap());
	}

	private static Route startingAt(Location first)
	{
		return new Route(Collections.singletonList(new RouteStop(first, null, Departure.WALK, 10, false)),
			RouteMode.AUTOPILOT, 10, 20);
	}

	@Test
	public void seedsOnlyInTheVaultPlanTheRunFromTheGuild()
	{
		assertTrue(RunService.plansFromGuild(new RunConfig(), startingAt(Location.CATHERBY), seeds(0, 0, 10)));
	}

	@Test
	public void notWhenTheBankOrInventoryHasThem()
	{
		assertFalse(RunService.plansFromGuild(new RunConfig(), startingAt(Location.CATHERBY), seeds(0, 6, 10)));
		assertFalse(RunService.plansFromGuild(new RunConfig(), startingAt(Location.CATHERBY), seeds(6, 0, 10)));
	}

	@Test
	public void notWhenItStartsThereOrThePlayerPickedTheStart()
	{
		assertFalse(RunService.plansFromGuild(new RunConfig(), startingAt(Location.FARMING_GUILD), seeds(0, 0, 10)));
		final RunConfig picked = new RunConfig();
		picked.setStartLocation(Location.CATHERBY);
		assertFalse(RunService.plansFromGuild(picked, startingAt(Location.CATHERBY), seeds(0, 0, 10)));
	}
}
