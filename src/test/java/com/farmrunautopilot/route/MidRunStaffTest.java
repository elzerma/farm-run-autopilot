package com.farmrunautopilot.route;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Mid-run, a staff left in the bank isn't asked for when the runes it would replace are carried (elzerma, 2026-10-24:
 * asked at Kastori for the mystic air staff they'd banked, with Catherby's air runes in their inventory).
 */
public class MidRunStaffTest
{
	@Test
	public void aBankedStaffIsNotAskedForWhenItsRunesAreCarried()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setEnabledTypes(EnumSet.of(PatchType.HERB));
		for (Patch patch : Patch.values())
		{
			if (patch != Patch.CATHERBY_HERB)
			{
				config.getDisabledPatches().add(patch);
			}
		}
		final Map<Integer, Integer> bank = new HashMap<>(ElzermaAccount.holdings().in(Holdings.Source.BANK));
		bank.put(ItemID.MYSTIC_AIR_STAFF, 1);
		final Map<Integer, Integer> inventory = new HashMap<>();
		inventory.put(ItemID.AIRRUNE, 5);
		inventory.put(ItemID.LAWRUNE, 1);
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		items.put(Holdings.Source.INVENTORY, inventory);
		final Holdings holdings = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false, -1,
			null, Collections.emptyMap(), Collections.emptyMap());
		final RunSelection selection = RunSelector.select(config, ElzermaAccount.access(), p -> null, 0, true,
			Collections.emptyMap());
		final Route route = RoutePlanner.plan(selection.getPatches(), config, ElzermaAccount.access(), holdings,
			new PohSetup(), ElzermaAccount.atlas(), null);

		// Planning before the run, the staff in the bank is worth taking and wielding
		assertTrue(lines(config, holdings, selection, route).stream().anyMatch(l -> hasId(l, ItemID.MYSTIC_AIR_STAFF)));

		// Mid-run, as RunService.suppliesFor counts it: the carried air runes do, and nothing is missing for them
		final Holdings midRun = holdings.carriedOnly(SupplyItems.INFINITE_RUNE_ITEMS.keySet());
		final SupplyPlan part = new RunAssembly(config, ElzermaAccount.access(), midRun, new PohSetup(), null, selection,
			p -> null, id -> "item " + id, id -> 0, id -> null).part(midRun, route.getStops(), route);
		assertFalse(part.getLines().stream().anyMatch(l -> hasId(l, ItemID.MYSTIC_AIR_STAFF)));
		assertFalse(part.counted(holdings).missingLines(false).keySet().stream()
			.anyMatch(l -> l.getGroup() == SupplyLine.Group.RUNES));
	}

	private static java.util.List<SupplyLine> lines(RunConfig config, Holdings holdings, RunSelection selection,
		Route route)
	{
		return new RunAssembly(config, ElzermaAccount.access(), holdings, new PohSetup(), null, selection, p -> null,
			id -> "item " + id, id -> 0, id -> null).part(holdings, route.getStops(), route).getLines();
	}

	private static boolean hasId(SupplyLine line, int id)
	{
		return line.getItemIds() != null && java.util.Arrays.stream(line.getItemIds()).anyMatch(i -> i == id);
	}
}
