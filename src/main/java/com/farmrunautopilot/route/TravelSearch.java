package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyCalculator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.LongSupplier;
import lombok.Value;

/**
 * Choosing ways with the inventory in mind (docs/plans/travel-rework.md, section 4, last part; sprint T4). The fastest
 * way per leg isn't always the fastest run: each way needs things carried, and when they and the farming supplies
 * don't fit, the run banks partway. A slot that saves a bank visit is worth that visit's seconds, and no more
 * (elzerma: "it needs to balance between making another bank stop and being absolute fastest").
 *
 * <p>Things a way carries ({@link Footprint}) are left at home: the whole run is planned again without them (every leg
 * that used one takes its next best way, and the stop order is worked out again), its supplies counted and its bank
 * visits placed, and the quickest run kept (see {@link #search}). A run that fits in one inventory has nothing to gain
 * and is left as it is. Pure, so it can run off the client thread.
 *
 * <p>Measured on the test account like elzerma's (2026-10-23): their usual runs keep the quickest ways, as their bank
 * visits are cheap (25 s by ring of dueling) and seeds and compost fill the inventory, not teleports; of 52 random runs
 * that bank, 3 saved a visit (15-19 s) and a few banked somewhere quicker, at about a second each off the client thread.
 */
final class TravelSearch
{
	/**
	 * A run leaving things at home has to save at least this much to be taken: a second or two isn't worth ways other
	 * than the quickest (the sweep above found 1 s gains).
	 */
	static final double MIN_GAIN = 3;
	/** For comparing runs only: a leg needing something the player doesn't have, as the planner counts it. */
	private static final double MISSING = RoutePlanner.MISSING_PENALTY;

	@Value
	static class Outcome
	{
		/** What the run leaves at home ({@link Footprint} keys); empty when the quickest ways are best. */
		Set<String> avoid;
		/** The run planned with that. */
		RunAssembly.Result run;
		/** The run with the quickest ways, for explaining what changed. */
		RunAssembly.Result quickest;
		/** Whole runs planned and counted, for the dev log. */
		int tried;
	}

	private TravelSearch()
	{
	}

	/**
	 * Each thing carried is screened on its own, quickly: the route planned without it and the run's supplies counted
	 * as one inventory, no bank visits placed. Only those that free a slot for less time than the bank visits cost are
	 * kept, cheapest per slot first. Then they're left at home one more at a time (one alone rarely frees enough to
	 * save a visit), each set weighed as the whole run with its bank visits placed, until what they cost on the way
	 * reaches what the visits cost; the quickest run wins.
	 *
	 * @param assembly the run's assembly on an atlas view (with what it avoids)
	 * @param now for the time limit, in milliseconds
	 * @param deadline when to stop looking (as {@code now} counts); what's best so far is kept
	 */
	static Outcome search(RouteAtlas atlas, Function<RouteAtlas, RunAssembly> assembly, RunAssembly.Start start,
		RunConfig config, AccessSnapshot access, Holdings holdings, LongSupplier now, long deadline)
	{
		final RunAssembly.Result quickest = assembly.apply(atlas).assemble(start);
		if (quickest.getBankStops().isEmpty())
		{
			return new Outcome(Collections.emptySet(), quickest, quickest, 1);
		}
		final RunAssembly.Unbanked base = assembly.apply(atlas).unbanked(start);
		final double baseTravel = base.getRoute().getTravelSeconds();
		final int baseSlots = base.getSupplies().getSlots();
		// The most leaving things at home can save: every bank visit and what it adds to the legs after it
		final double visits = score(quickest) - baseTravel;
		int tried = 1;

		final List<Screened> screened = new ArrayList<>();
		for (String key : carried(base.getRoute(), config, access, holdings))
		{
			if (now.getAsLong() >= deadline)
			{
				break;
			}
			final RunAssembly.Unbanked without = assembly.apply(atlas.avoiding(Collections.singleton(key))).unbanked(start);
			final int freed = baseSlots - without.getSupplies().getSlots();
			final double slower = without.getRoute().getTravelSeconds() - baseTravel;
			if (freed > 0 && slower < visits)
			{
				screened.add(new Screened(key, slower / freed));
			}
		}
		screened.sort(Comparator.comparingDouble(s -> s.perSlot));

		Set<String> avoid = Collections.emptySet();
		RunAssembly.Result best = quickest;
		double bestScore = score(quickest);
		final Set<String> trial = new TreeSet<>();
		int lastSlots = baseSlots;
		for (Screened s : screened)
		{
			if (now.getAsLong() >= deadline)
			{
				break;
			}
			trial.add(s.key);
			final RouteAtlas view = atlas.avoiding(trial);
			final RunAssembly.Unbanked without = assembly.apply(view).unbanked(start);
			if (without.getRoute().getTravelSeconds() - baseTravel >= visits)
			{
				break;
			}
			if (without.getSupplies().getSlots() >= lastSlots)
			{
				// Freed nothing alongside the rest (its slot was shared): leave it out
				trial.remove(s.key);
				continue;
			}
			lastSlots = without.getSupplies().getSlots();
			final RunAssembly.Result run = assembly.apply(view).assemble(start);
			tried++;
			final double score = score(run);
			if (score < bestScore - MIN_GAIN)
			{
				avoid = Collections.unmodifiableSet(new TreeSet<>(trial));
				best = run;
				bestScore = score;
			}
		}
		return new Outcome(avoid, best, quickest, tried);
	}

	/** A thing carried, with how many seconds a slot it frees costs on the way. */
	@Value
	private static class Screened
	{
		String key;
		double perSlot;
	}

	/**
	 * What a run is weighed by: its travel, bank visits included, with a leg the player can't make yet counted as the
	 * planner counts it; a run that can't fit even with bank visits never wins.
	 */
	static double score(RunAssembly.Result run)
	{
		if (run.getBankStops().isEmpty() && run.getSupplies().getSlots() > SupplyCalculator.INVENTORY_SLOTS)
		{
			return Double.MAX_VALUE;
		}
		double total = run.getRoute().getTravelSeconds();
		for (RouteStop stop : run.getRoute().getStops())
		{
			total += stop.isNeedsSupplies() ? MISSING : 0;
			total += stop.getDeparture() == Departure.NONE ? RoutePlanner.NO_WAY_PENALTY : 0;
		}
		return total;
	}

	/** Everything the run's ways carry, as {@link Footprint} keys. */
	static Set<String> carried(Route route, RunConfig config, AccessSnapshot access, Holdings holdings)
	{
		final Set<String> keys = new TreeSet<>();
		for (RouteStop stop : route.getStops())
		{
			keys.addAll(Footprint.of(stop, config, access, holdings));
		}
		return keys;
	}
}
