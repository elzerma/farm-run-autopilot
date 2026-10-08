package com.farmrunautopilot.run;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RunSessionTest
{
	private static SupplyLine line(SupplyLine.Group group, int need, int carried, int leprechaun)
	{
		final Map<Holdings.Source, Integer> where = new EnumMap<>(Holdings.Source.class);
		if (carried > 0)
		{
			where.put(Holdings.Source.INVENTORY, carried);
		}
		if (leprechaun > 0)
		{
			where.put(Holdings.Source.LEPRECHAUN, leprechaun);
		}
		return new SupplyLine(group, "x", need, carried + leprechaun, carried, where, null, 1, false, null,
			new int[]{1});
	}

	private static RunPlan plan(List<SupplyLine> lines)
	{
		final Route route = new Route(Collections.singletonList(
			new RouteStop(Location.CATHERBY, null, Departure.WALK, 10, false)), RouteMode.AUTOPILOT, 10, 20);
		final SupplyPlan supplies = new SupplyPlan(lines, Collections.emptyMap(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyList(), 0, "", 0, Collections.emptyMap());
		return new RunPlan(RunSelection.EMPTY, route, supplies, Collections.emptyMap());
	}

	@Test
	public void readyWhenCarriedOrAtTheLeprechaun()
	{
		assertTrue(RunSession.isReady(plan(java.util.Arrays.asList(
			line(SupplyLine.Group.SEEDS, 8, 8, 0),
			line(SupplyLine.Group.TOOLS, 8, 0, 128)))));
	}

	@Test
	public void notReadyWhenSomethingIsStillInTheBank()
	{
		assertFalse(RunSession.isReady(plan(Collections.singletonList(line(SupplyLine.Group.SEEDS, 8, 3, 0)))));
	}

	@Test
	public void optionalItemsDontBlock()
	{
		assertTrue(RunSession.isReady(plan(java.util.Arrays.asList(
			line(SupplyLine.Group.SEEDS, 1, 1, 0),
			line(SupplyLine.Group.OPTIONAL, 4, 0, 0)))));
	}

	@Test
	public void walkTilesComeFromTheMethodOrTheWalkLeg()
	{
		assertEquals(55, RunSession.walkTiles(
			new RouteStop(Location.CATHERBY, TravelMethod.CAMELOT_TELEPORT, Departure.DIRECT, 20, false)));
		assertEquals(100, RunSession.walkTiles(new RouteStop(Location.TAVERLEY, null, Departure.WALK, 30, false)));
	}

	@Test
	public void harvestSpaceForHerbsAndFruit()
	{
		final PatchPrediction fruit = new PatchPrediction(Patch.CATHERBY_FRUIT_TREE, Crop.PAPAYA,
			PatchState.HARVESTABLE, 4, 7, 0, 0, 0, PatchPrediction.Source.THIS_PLUGIN);
		assertEquals(4, RunSession.expectedHarvest(Patch.CATHERBY_FRUIT_TREE, StepAdvisor.Action.PICK, fruit));
		assertEquals(6, RunSession.expectedHarvest(Patch.CATHERBY_FRUIT_TREE, StepAdvisor.Action.CHECK_HEALTH, null));
		assertEquals(8, RunSession.expectedHarvest(Patch.CATHERBY_HERB, StepAdvisor.Action.PICK, null));
		assertEquals(0, RunSession.expectedHarvest(Patch.CATHERBY_HERB, StepAdvisor.Action.PLANT, null));
		assertEquals(0, RunSession.expectedHarvest(Patch.TAVERLEY_TREE, StepAdvisor.Action.CHECK_HEALTH, null));
	}

	@Test
	public void notReadyWithNoRoute()
	{
		assertFalse(RunSession.isReady(RunPlan.EMPTY));
	}
}
