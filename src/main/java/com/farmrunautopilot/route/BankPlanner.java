package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.TravelTimes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.Value;

/**
 * Where a run too big for one inventory banks (docs/REBUILD.md, Sprint 2): the cheapest set of bank visits,
 * as many as needed, so that every stretch between them fits in the inventory. A visit goes between two stops
 * and is either:
 * <ul>
 * <li>the bank near the stop just finished: walk there, and walk back only if the next leg sets off from that
 * stop (a walk, its spirit tree, Renu) and that's quicker than a teleport from the bank; or</li>
 * <li>a bank teleport the player carries (crafting cape, ring of dueling...), then the next stop from there.</li>
 * </ul>
 * Pure: the slot counts, chain savings and onward legs are passed in, so this can be tested without a client.
 */
final class BankPlanner
{
	/** Depositing and withdrawing, the same at any bank. */
	static final double BANK_SECONDS = 15;

	/** Inventory slots for the stops in [from, to) of the route, as one inventory. */
	interface Slots
	{
		int of(int from, int to);
	}

	@Value
	static class Visit
	{
		/** Index of the stop it follows. */
		int afterStop;
		/** The stop just finished. */
		Location location;
		/** Null when walking to the bank near {@link #location}. */
		BankTeleport teleport;
		/** Getting there (and back, if the next leg sets off from the stop) plus banking. */
		double seconds;
	}

	@Value
	static class Result
	{
		/** The route with the leg after each visit set off from the bank where that changed. */
		Route route;
		List<Visit> visits;
	}

	private BankPlanner()
	{
	}

	/**
	 * @param slotLimit slots in one inventory
	 * @param teleport the quickest bank teleport the player has, or null
	 * @param chainSaving how much quicker the second stop is from the first than from anywhere (0 if no quicker)
	 * @param fromAnywhere the best leg to a stop from anywhere, e.g. from a bank
	 * @return the route with its visits (none if it all fits), or null if some stretch can't fit even alone
	 */
	static Result place(Route route, Slots slots, int slotLimit, BankTeleport teleport,
		BiFunction<Location, Location, Double> chainSaving, Function<Location, RouteStop> fromAnywhere)
	{
		final List<RouteStop> stops = route.getStops();
		final int n = stops.size();
		if (n == 0 || slots.of(0, n) <= slotLimit)
		{
			return new Result(route, Collections.emptyList());
		}

		// best[b]: cheapest way to do stops [0, b) and bank right after stop b - 1 (or finish, at b = n)
		final double[] best = new double[n + 1];
		final int[] from = new int[n + 1];
		final Visit[] visit = new Visit[n + 1];
		java.util.Arrays.fill(best, Double.MAX_VALUE);
		best[0] = 0;
		for (int a = 0; a < n; a++)
		{
			if (best[a] == Double.MAX_VALUE)
			{
				continue;
			}
			for (int b = a + 1; b <= n; b++)
			{
				final int used = slots.of(a, b);
				if (used > slotLimit)
				{
					// A longer stretch only needs more
					break;
				}
				if (b == n)
				{
					if (best[a] < best[n])
					{
						best[n] = best[a];
						from[n] = a;
					}
					continue;
				}
				final Visit v = cheapestVisit(stops, b - 1, used, slotLimit, teleport, chainSaving);
				if (v != null && best[a] + v.seconds < best[b])
				{
					best[b] = best[a] + v.seconds;
					from[b] = a;
					visit[b] = v;
				}
			}
		}
		if (best[n] == Double.MAX_VALUE)
		{
			return null;
		}

		final List<Visit> visits = new ArrayList<>();
		for (int b = from[n]; b > 0; b = from[b])
		{
			visits.add(0, visit[b]);
		}
		final List<RouteStop> onward = new ArrayList<>(stops);
		double travel = route.getTravelSeconds();
		for (Visit v : visits)
		{
			final int next = v.afterStop + 1;
			final RouteStop leg = stops.get(next);
			if (!walksBack(v, stops, chainSaving))
			{
				final RouteStop fresh = fromAnywhere.apply(leg.getLocation());
				onward.set(next, fresh);
				travel += fresh.getLegSeconds() - leg.getLegSeconds();
			}
			travel += visitOnly(v, stops, chainSaving);
		}
		return new Result(new Route(onward, route.getMode(), travel, route.getPatchSeconds()), visits);
	}

	/** The cheaper of banking near the stop or by teleport, or null if neither fits. */
	private static Visit cheapestVisit(List<RouteStop> stops, int after, int used, int slotLimit,
		BankTeleport teleport, BiFunction<Location, Location, Double> chainSaving)
	{
		final Location here = stops.get(after).getLocation();
		final double chain = chainSaving.apply(here, stops.get(after + 1).getLocation());
		Visit best = null;
		final Integer tiles = TravelTimes.bankTiles(here);
		if (tiles != null)
		{
			final double walk = tiles * TravelTimes.SECONDS_PER_TILE;
			// Walk back to keep the chain, or set off from the bank and lose it, whichever is quicker
			best = new Visit(after, here, null, walk + Math.min(walk, chain) + BANK_SECONDS);
		}
		// The teleport item takes a slot until it's used
		if (teleport != null && used + 1 <= slotLimit)
		{
			final Visit byTeleport = new Visit(after, here, teleport, teleport.getSeconds() + chain + BANK_SECONDS);
			if (best == null || byTeleport.seconds < best.seconds)
			{
				best = byTeleport;
			}
		}
		return best;
	}

	/** Whether the player walks back from the bank to the stop to keep the next leg's chain. */
	private static boolean walksBack(Visit v, List<RouteStop> stops, BiFunction<Location, Location, Double> chainSaving)
	{
		if (v.teleport != null)
		{
			return false;
		}
		final double walk = TravelTimes.bankTiles(v.location) * TravelTimes.SECONDS_PER_TILE;
		final double chain = chainSaving.apply(v.location, stops.get(v.afterStop + 1).getLocation());
		return chain > walk;
	}

	/** A visit's own seconds, without the chain it loses (that's in the replaced leg). */
	private static double visitOnly(Visit v, List<RouteStop> stops, BiFunction<Location, Location, Double> chainSaving)
	{
		if (v.teleport != null)
		{
			return v.teleport.getSeconds() + BANK_SECONDS;
		}
		final double walk = TravelTimes.bankTiles(v.location) * TravelTimes.SECONDS_PER_TILE;
		return walk + (walksBack(v, stops, chainSaving) ? walk : 0) + BANK_SECONDS;
	}
}
