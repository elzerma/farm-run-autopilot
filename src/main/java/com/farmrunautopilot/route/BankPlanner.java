package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.atlas.AtlasBanks;
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
 * <li>the bank by the stop just finished, from the travel atlas (AtlasBanks): walk there, and walk back only if the
 * next leg sets off from that stop (a walk, its spirit tree, Renu) and that's quicker than a teleport from the bank;
 * or</li>
 * <li>a bank teleport the player carries (crafting cape, ring of dueling...), then the next stop from there; or</li>
 * <li>with "Bank at the Farming Guild" (issue #8), a trip to the guild's bank and seed vault, then the next stop
 * from the guild. The visit before a part that plants seeds only the vault has is always at the guild: after the
 * guild's own stop, or by that trip.</li>
 * </ul>
 * Pure: the slot counts, chain savings and onward legs are passed in, and the banks come from the atlas's data, so this
 * can be tested without a client.
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

	/** The Farming Guild's seed vault, for "Bank at the Farming Guild" (issue #8). */
	interface Guild
	{
		/** The stops in [from, to) plant seeds only the seed vault has, counted against what the stops before leave. */
		boolean needsVault(int from, int to);

		/** The way from a stop to the Farming Guild. */
		RouteStop to(Location from);

		/** The leg from the Farming Guild's bank to a stop. */
		RouteStop from(Location to);
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
		/** The way to the Farming Guild, for a visit to its bank from another stop; null otherwise. */
		RouteStop trip;

		/** At the Farming Guild's bank, where the seed vault is. */
		boolean isAtGuild()
		{
			return trip != null || teleport == null && location == Location.FARMING_GUILD;
		}
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
		return place(route, slots, slotLimit, teleport, chainSaving, fromAnywhere, null);
	}

	/**
	 * @param guild with "Bank at the Farming Guild": a part that plants seeds only the seed vault has comes after a
	 *     visit to the guild's bank; null to bank wherever is quickest
	 */
	static Result place(Route route, Slots slots, int slotLimit, BankTeleport teleport,
		BiFunction<Location, Location, Double> chainSaving, Function<Location, RouteStop> fromAnywhere, Guild guild)
	{
		final List<RouteStop> stops = route.getStops();
		final int n = stops.size();
		if (n == 0 || slots.of(0, n) <= slotLimit)
		{
			return new Result(route, Collections.emptyList());
		}

		// best[b][g]: cheapest way to do stops [0, b) and bank right after stop b - 1 (or finish, at b = n), where
		// g = 1 when that visit is at the Farming Guild's bank, so the part after it can take from the seed vault
		final double[][] best = new double[n + 1][2];
		final int[][] from = new int[n + 1][2];
		final int[][] fromGuild = new int[n + 1][2];
		final Visit[][] visit = new Visit[n + 1][2];
		for (double[] row : best)
		{
			java.util.Arrays.fill(row, Double.MAX_VALUE);
		}
		best[0][0] = 0;
		for (int a = 0; a < n; a++)
		{
			for (int g = 0; g < 2; g++)
			{
				if (best[a][g] == Double.MAX_VALUE)
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
					// The seeds for the first part are taken before the run; later parts get theirs at the guild
					if (a > 0 && g == 0 && guild != null && guild.needsVault(a, b))
					{
						continue;
					}
					if (b == n)
					{
						if (best[a][g] < best[n][0])
						{
							best[n][0] = best[a][g];
							from[n][0] = a;
							fromGuild[n][0] = g;
						}
						continue;
					}
					final Visit[] ways = {cheapestVisit(stops, b - 1, used, slotLimit, teleport, chainSaving, guild != null),
						guild == null ? null : guildVisit(stops, b - 1, used, slotLimit, chainSaving, guild)};
					for (int h = 0; h < 2; h++)
					{
						final Visit v = ways[h];
						if (v != null && best[a][g] + v.seconds < best[b][h])
						{
							best[b][h] = best[a][g] + v.seconds;
							from[b][h] = a;
							fromGuild[b][h] = g;
							visit[b][h] = v;
						}
					}
				}
			}
		}
		if (best[n][0] == Double.MAX_VALUE)
		{
			return null;
		}

		final List<Visit> visits = new ArrayList<>();
		int g = fromGuild[n][0];
		for (int b = from[n][0]; b > 0; )
		{
			visits.add(0, visit[b][g]);
			final int a = from[b][g];
			g = fromGuild[b][g];
			b = a;
		}
		final List<RouteStop> onward = new ArrayList<>(stops);
		double travel = route.getTravelSeconds();
		for (Visit v : visits)
		{
			final int next = v.afterStop + 1;
			final RouteStop leg = stops.get(next);
			if (v.trip != null)
			{
				// On from the guild
				final RouteStop fresh = guild.from(leg.getLocation());
				onward.set(next, fresh);
				travel += fresh.getLegSeconds() - leg.getLegSeconds();
			}
			else if (!walksBack(v, stops, chainSaving))
			{
				final RouteStop fresh = fromAnywhere.apply(leg.getLocation());
				onward.set(next, fresh);
				travel += fresh.getLegSeconds() - leg.getLegSeconds();
			}
			travel += visitOnly(v, stops, chainSaving);
		}
		return new Result(new Route(onward, route.getMode(), travel, route.getPatchSeconds(), route.getStartsFrom()),
			visits);
	}

	/**
	 * The cheaper of banking near the stop or by teleport, or null if neither fits.
	 *
	 * @param notGuild leave out banking near the Farming Guild's stop, which counts as a visit to the guild
	 */
	private static Visit cheapestVisit(List<RouteStop> stops, int after, int used, int slotLimit,
		BankTeleport teleport, BiFunction<Location, Location, Double> chainSaving, boolean notGuild)
	{
		final Location here = stops.get(after).getLocation();
		Visit best = notGuild && here == Location.FARMING_GUILD ? null : nearVisit(stops, after, chainSaving);
		// The teleport item takes a slot until it's used
		if (teleport != null && used + 1 <= slotLimit)
		{
			final double chain = chainSaving.apply(here, stops.get(after + 1).getLocation());
			final Visit byTeleport = new Visit(after, here, teleport, teleport.getSeconds() + chain + BANK_SECONDS, null);
			if (best == null || byTeleport.seconds < best.seconds)
			{
				best = byTeleport;
			}
		}
		return best;
	}

	/** Banking near the stop just finished, or null if there's no bank near it. */
	private static Visit nearVisit(List<RouteStop> stops, int after, BiFunction<Location, Location, Double> chainSaving)
	{
		final Location here = stops.get(after).getLocation();
		if (AtlasBanks.near(here) == null)
		{
			return null;
		}
		final double chain = chainSaving.apply(here, stops.get(after + 1).getLocation());
		final double walk = bankWalk(here);
		// Walk back to keep the chain, or set off from the bank and lose it, whichever is quicker
		return new Visit(after, here, null, walk + Math.min(walk, chain) + BANK_SECONDS, null);
	}

	/**
	 * Banking at the Farming Guild, by its seed vault: near its stop, or by a trip there and on from the guild. Null
	 * if the trip's item doesn't fit.
	 */
	private static Visit guildVisit(List<RouteStop> stops, int after, int used, int slotLimit,
		BiFunction<Location, Location, Double> chainSaving, Guild guild)
	{
		final Location here = stops.get(after).getLocation();
		if (here == Location.FARMING_GUILD)
		{
			return nearVisit(stops, after, chainSaving);
		}
		final RouteStop trip = guild.to(here);
		// Only a way the player can make now; an item to get there takes a slot until it's used, like a bank teleport
		if (trip.isNeedsSupplies() || trip.getDeparture() == Departure.NONE
			|| trip.travelItem() != null && used + 1 > slotLimit)
		{
			return null;
		}
		final RouteStop next = stops.get(after + 1);
		return new Visit(after, here, null, trip.getLegSeconds() + guildBankWalk() + BANK_SECONDS
			+ guild.from(next.getLocation()).getLegSeconds() - next.getLegSeconds(), trip);
	}

	/** From the guild's patches, where the ways there arrive, to its bank. */
	private static double guildBankWalk()
	{
		return bankWalk(Location.FARMING_GUILD);
	}

	/** From a stop's patches to the bank by it (AtlasBanks); 0 if it has none. */
	private static double bankWalk(Location location)
	{
		final AtlasBanks.Bank bank = AtlasBanks.near(location);
		return bank == null ? 0 : bank.getSeconds();
	}

	/** Whether the player walks back from the bank to the stop to keep the next leg's chain. */
	private static boolean walksBack(Visit v, List<RouteStop> stops, BiFunction<Location, Location, Double> chainSaving)
	{
		if (v.teleport != null)
		{
			return false;
		}
		final double walk = bankWalk(v.location);
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
		if (v.trip != null)
		{
			return v.trip.getLegSeconds() + guildBankWalk() + BANK_SECONDS;
		}
		final double walk = bankWalk(v.location);
		return walk + (walksBack(v, stops, chainSaving) ? walk : 0) + BANK_SECONDS;
	}
}
