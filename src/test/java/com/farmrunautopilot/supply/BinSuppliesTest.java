package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.BinState;
import com.farmrunautopilot.data.CompostBin;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** P3: the volcanic ash and empty buckets that emptying the compost bins on the route takes. */
public class BinSuppliesTest
{
	private static final Route ROUTE = new Route(Arrays.asList(
		new RouteStop(Location.FALADOR_FARM, null, Departure.WALK, 10, false),
		new RouteStop(Location.TROLL_STRONGHOLD, null, Departure.WALK, 10, false),
		new RouteStop(Location.CATHERBY, null, Departure.WALK, 10, false)), null, 0, 0);

	private static Holdings holdings(Holdings.Source source, int itemId, int count)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		final Map<Integer, Integer> in = new HashMap<>();
		in.put(itemId, count);
		items.put(source, in);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	private static RunConfig making()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setMakeCompost(true);
		return config;
	}

	private static SupplyLine named(List<SupplyLine> lines, String name)
	{
		return lines.stream().filter(line -> line.getName().equals(name)).findFirst().orElse(null);
	}

	@Test
	public void readyBinsOfSupercompostTakeAshAndBuckets()
	{
		// Falador's rotted, Catherby's open with 9 buckets left
		final Map<CompostBin, BinState> bins = new EnumMap<>(CompostBin.class);
		bins.put(CompostBin.FALADOR, BinState.decode(126, false));
		bins.put(CompostBin.CATHERBY, BinState.decode(56, false));
		final List<SupplyLine> lines = SupplyCalculator.binSupplies(making(), ROUTE, bins::get, Holdings.EMPTY, Holdings.EMPTY);
		assertEquals(50, named(lines, "Volcanic ash").getNeed());
		assertEquals(15 + 9, named(lines, "Empty bucket").getNeed());
	}

	@Test
	public void nothingWithoutMakeCompostOrAReadyBin()
	{
		final Function<CompostBin, BinState> rotted = bin -> BinState.decode(126, false);
		assertTrue(SupplyCalculator.binSupplies(new RunConfig().sanitise(), ROUTE, rotted, Holdings.EMPTY,
			Holdings.EMPTY).isEmpty());
		// Still rotting, or filling: nothing to take out
		assertTrue(SupplyCalculator.binSupplies(making(), ROUTE, bin -> BinState.decode(95, false), Holdings.EMPTY,
			Holdings.EMPTY).isEmpty());
		assertTrue(SupplyCalculator.binSupplies(making(), ROUTE, bin -> null, Holdings.EMPTY, Holdings.EMPTY).isEmpty());
	}

	@Test
	public void noAshWithoutUltracompostAndNoBucketsWithTheBottomlessBucket()
	{
		final RunConfig config = making();
		config.setMakeUltracompost(false);
		final Function<CompostBin, BinState> rotted = bin -> BinState.decode(126, false);
		final List<SupplyLine> plain = SupplyCalculator.binSupplies(config, ROUTE, rotted, Holdings.EMPTY, Holdings.EMPTY);
		assertEquals(null, named(plain, "Volcanic ash"));
		final Holdings bottomless = holdings(Holdings.Source.BANK, ItemID.BOTTOMLESS_COMPOST_BUCKET, 1);
		assertEquals(null, named(SupplyCalculator.binSupplies(making(), ROUTE, rotted, bottomless, Holdings.EMPTY),
			"Empty bucket"));
	}

	@Test
	public void theLeprechaunsBucketsAreCountedAsTheGameDoes()
	{
		// farming_tools_getstored: 2^8 times the second overflow, 2^5 times the first, plus the base
		assertEquals(0, LeprechaunItem.buckets(0, 0, 0));
		assertEquals(31, LeprechaunItem.buckets(31, 0, 0));
		assertEquals(32 + 3, LeprechaunItem.buckets(3, 1, 0));
		assertEquals(256 + 64 + 5, LeprechaunItem.buckets(5, 2, 1));
		// Buckets he holds take no inventory slots to bring
		final Map<CompostBin, BinState> bins = new EnumMap<>(CompostBin.class);
		bins.put(CompostBin.FALADOR, BinState.decode(126, false));
		final Holdings stored = holdings(Holdings.Source.LEPRECHAUN, ItemID.BUCKET_EMPTY, 20);
		assertEquals(0, named(SupplyCalculator.binSupplies(making(), ROUTE, bins::get, stored, Holdings.EMPTY),
			"Empty bucket").getSlots());
	}
}
