package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.atlas.AtlasBanks;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.PatchCare;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * The next run put together from the player as they are: the route (SPEC 12), what to bring (SPEC 11) and, for a run too
 * big for one inventory, where it banks partway. Pure: {@link RunService} reads the game on the client thread and passes
 * everything in, with the item lookups, so the tests can drive it and the travel search (T4) can run it off the client
 * thread.
 */
final class RunAssembly
{
	/** The route, the first part's supplies, and each bank visit with what it takes. */
	@Value
	static class Result
	{
		Route route;
		SupplyPlan supplies;
		List<BankStop> bankStops;
	}

	/** Where the player is for the route's start (GitHub #1, issue #8). */
	@Value
	static class Start
	{
		static final Start ANYWHERE = new Start(null, null, 0, false, null);

		/** The stop the player stands at, or null. */
		Location here;
		/** The stop the player is close to but not at, or null. */
		Location near;
		int nearTiles;
		/** In the Farming Guild, where it isn't a stop. */
		boolean inGuild;
		/** Where the player stands, or null. */
		WorldPoint tile;
	}

	private final RunConfig config;
	private final AccessSnapshot access;
	private final Holdings holdings;
	private final PohSetup poh;
	private final RouteAtlas atlas;
	private final RunSelection selection;
	private final Function<Patch, PatchPrediction> predict;
	private final IntFunction<String> itemName;
	private final IntToLongFunction price;
	/** The equipment slot an item is worn in, or null if none the supply list plans. */
	private final IntFunction<TravelItem.WearSlot> wearSlot;

	RunAssembly(RunConfig config, AccessSnapshot access, Holdings holdings, PohSetup poh, RouteAtlas atlas,
		RunSelection selection, Function<Patch, PatchPrediction> predict, IntFunction<String> itemName,
		IntToLongFunction price, IntFunction<TravelItem.WearSlot> wearSlot)
	{
		this.config = config;
		this.access = access;
		this.holdings = holdings;
		this.poh = poh;
		this.atlas = atlas;
		this.selection = selection;
		this.predict = predict;
		this.itemName = itemName;
		this.price = price;
		this.wearSlot = wearSlot;
	}

	/** The route and the whole run's supplies as one inventory, before any bank visit is placed. */
	@Value
	static class Unbanked
	{
		Route route;
		SupplyPlan supplies;
		/** "Bank at the Farming Guild" decides where the run sets off once its bank visits are placed (issue #8). */
		boolean guildBanks;
	}

	/** The run from where the player is. */
	Result assemble(Start start)
	{
		final Unbanked unbanked = unbanked(start);
		Route route = unbanked.route;
		SupplyPlan supplies = unbanked.supplies;
		final boolean guildBanks = unbanked.guildBanks;
		// Too much for one inventory: bank partway, as often as needed, where it costs least
		final List<BankStop> bankStops = new ArrayList<>();
		if (supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS)
		{
			Banked banked = bankStops(route);
			if (banked != null && guildBanks && RunService.plansFromGuild(config, banked.route, banked.first))
			{
				route = fromGuild();
				supplies = supplies(route);
				banked = supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS ? bankStops(route) : null;
			}
			if (banked != null)
			{
				route = banked.route;
				supplies = banked.first;
				bankStops.addAll(banked.stops);
			}
		}
		return new Result(route, supplies, Collections.unmodifiableList(bankStops));
	}

	/** The route from where the player is, and its supplies as one inventory: quick, as no bank visit is placed. */
	Unbanked unbanked(Start start)
	{
		// In the Farming Guild, where it isn't a stop: the run sets off from there (issue #8)
		Route route = RoutePlanner.plan(selection.getPatches(), config, access, holdings, poh, atlas, start.here,
			start.near, start.nearTiles, start.inGuild ? Location.FARMING_GUILD : null, start.tile);
		SupplyPlan supplies = supplies(route);
		// Starting at the stop the player stands at or near only makes sense if they can start from there: carrying
		// everything already, or with a bank close by. Otherwise (Kastori, still needing supplies) they'll bank
		// somewhere first, so plan from anywhere (elzerma). The same for setting off on foot from where they stand.
		final Location startStop = start.here != null ? start.here : start.near;
		final boolean missing = supplies.missingCount(startStop == Location.FARMING_GUILD) > 0;
		if (missing && (startStop != null ? AtlasBanks.near(startStop) == null
			: start.tile != null && !RoutePlanner.bankNear(atlas, start.tile)))
		{
			route = RoutePlanner.plan(selection.getPatches(), config, access, holdings, poh, atlas, null, null, 0);
			supplies = supplies(route);
		}
		// Seeds the run plants that only the seed vault has: the player fetches them from the Farming Guild, so the run
		// sets off from there (issue #8). With "Bank at the Farming Guild" on a run that banks partway, only the first
		// part's come out before the run, so that's checked once the bank visits are placed.
		final boolean guildBanks = RunService.guildBanking(config)
			&& supplies.getSlots() > SupplyCalculator.INVENTORY_SLOTS;
		if (!guildBanks && RunService.plansFromGuild(config, route, supplies))
		{
			route = fromGuild();
			supplies = supplies(route);
		}
		return new Unbanked(route, supplies, guildBanks);
	}

	private Route fromGuild()
	{
		return RoutePlanner.plan(selection.getPatches(), config, access, holdings, poh, atlas, null, null, 0,
			Location.FARMING_GUILD);
	}

	/** The whole run's supplies for a route. */
	private SupplyPlan supplies(Route route)
	{
		return SupplyCalculator.calculate(config, access, holdings, selection, route, predict, itemName, price,
			wearSlot);
	}

	/** A run with its bank visits: the route, what to bring at the start, and each visit with what it takes. */
	private static final class Banked
	{
		final Route route;
		final SupplyPlan first;
		final List<BankStop> stops;

		Banked(Route route, SupplyPlan first, List<BankStop> stops)
		{
			this.route = route;
			this.first = first;
			this.stops = stops;
		}
	}

	/**
	 * Bank visits for a run too big for one inventory (see {@link BankPlanner}), with each part's supplies.
	 * Null if a part can't fit even on its own; the plan then warns about slots as before.
	 */
	private Banked bankStops(Route route)
	{
		final List<RouteStop> stops = route.getStops();
		final Map<Long, Integer> slots = new HashMap<>();
		final BankPlanner.Slots slotsOf = (a, b) -> slots.computeIfAbsent((long) a * 1000 + b,
			k -> part(holdings, stops.subList(a, b), route).getSlots());
		final BankTeleport teleport = BankTeleport.best(access, holdings, t -> RunService.allowed(config, t));
		final BiFunction<Location, Location, Double> chainSaving = (from, to) -> RoutePlanner.chainSaving(from, to,
			config, access, holdings, poh, atlas);
		final Function<Location, RouteStop> fromAnywhere = to -> RoutePlanner.fromAnywhere(to, config, access,
			holdings, poh, atlas);
		BankPlanner.Result result = BankPlanner.place(route, slotsOf, SupplyCalculator.INVENTORY_SLOTS, teleport,
			chainSaving, fromAnywhere, RunService.guildBanking(config) ? guild(route) : null);
		if (result == null && RunService.guildBanking(config))
		{
			// No way to bank at the guild before every part that needs its vault (an item that doesn't fit): bank
			// wherever is quickest, and the vault's seeds are fetched by hand as before
			result = BankPlanner.place(route, slotsOf, SupplyCalculator.INVENTORY_SLOTS, teleport, chainSaving,
				fromAnywhere);
		}
		if (result == null || result.getVisits().isEmpty())
		{
			return null;
		}

		// Each part's supplies, counted against what the parts before it leave (seeds planted, runes cast), with
		// the teleport to bank with carried in the part before the bank
		final List<RouteStop> banked = result.getRoute().getStops();
		final List<BankPlanner.Visit> visits = result.getVisits();
		final List<SupplyPlan> parts = new ArrayList<>();
		int start = 0;
		Holdings left = holdings;
		for (int i = 0; i <= visits.size(); i++)
		{
			final int end = i < visits.size() ? visits.get(i).getAfterStop() + 1 : banked.size();
			SupplyPlan part = part(left, banked.subList(start, end), result.getRoute());
			if (i < visits.size() && visits.get(i).getTeleport() != null)
			{
				part = withTeleport(part, visits.get(i).getTeleport(), left, config.getOutfit());
			}
			if (i < visits.size() && visits.get(i).getTrip() != null)
			{
				part = withTrip(part, visits.get(i).getTrip(), left);
			}
			parts.add(part);
			left = part.leftAfter(left);
			start = end;
		}
		final List<BankStop> bankStops = new ArrayList<>();
		for (int i = 0; i < visits.size(); i++)
		{
			final BankPlanner.Visit v = visits.get(i);
			bankStops.add(new BankStop(v.getAfterStop(), v.getLocation(), parts.get(i + 1), v.getTeleport(), v.getTrip()));
		}

		// The guide needs every patch's crop, not only the first part's
		final Map<Patch, Crop> plantings = new EnumMap<>(Patch.class);
		final Map<Patch, PatchCare> care = new EnumMap<>(Patch.class);
		for (SupplyPlan part : parts)
		{
			plantings.putAll(part.getPlantings());
			care.putAll(part.getCare());
		}
		final SupplyPlan first = parts.get(0);
		return new Banked(result.getRoute(), new SupplyPlan(first.getLines(), first.getPatchCounts(),
			first.getNotDue(), first.getTravelPlan(), first.getWarnings(), first.getCoins(), first.getRuneSummary(),
			first.getSlots(), Collections.unmodifiableMap(plantings), first.getStaffSwaps(),
			Collections.unmodifiableMap(care)), bankStops);
	}

	/** The supplies for some of the route's stops on their own, against what the player holds then. */
	SupplyPlan part(Holdings holdings, List<RouteStop> stops, Route route)
	{
		final RunSelection part = new RunSelection(patchesAt(stops), selection.getIncludedTypes(),
			selection.getSkippedTypes(), Collections.emptyList(), selection.getDueCounts());
		return SupplyCalculator.calculate(config, access, holdings, part,
			new Route(new ArrayList<>(stops), route.getMode(), 0, 0), predict, itemName, price, wearSlot);
	}

	/**
	 * "Bank at the Farming Guild" for the bank planner (issue #8): which parts of the route need the seed vault, and
	 * the ways to and from the guild.
	 */
	private BankPlanner.Guild guild(Route route)
	{
		final List<RouteStop> stops = route.getStops();
		final Map<Long, Boolean> needs = new HashMap<>();
		final Map<Integer, Holdings> leftBefore = new HashMap<>();
		return new BankPlanner.Guild()
		{
			@Override
			public boolean needsVault(int from, int to)
			{
				return needs.computeIfAbsent((long) from * 1000 + to, k -> !part(left(from), stops.subList(from, to), route)
					.onlyInVault().isEmpty());
			}

			/** What the player has left after the stops before this one, with each part counted on its own. */
			private Holdings left(int stop)
			{
				return leftBefore.computeIfAbsent(stop, k -> k == 0 ? holdings
					: part(holdings, stops.subList(0, k), route).leftAfter(holdings));
			}

			@Override
			public RouteStop to(Location from)
			{
				return RoutePlanner.between(from, Location.FARMING_GUILD, config, access, holdings, poh, atlas);
			}

			@Override
			public RouteStop from(Location to)
			{
				if (to == Location.FARMING_GUILD)
				{
					// From the guild's bank out to its patches
					final AtlasBanks.Bank bank = AtlasBanks.near(Location.FARMING_GUILD);
					return new RouteStop(to, null, Departure.WALK, bank == null ? 0 : bank.getSeconds(), false,
						"from the bank " + (bank == null ? 0 : bank.getTiles()) + " tiles");
				}
				return RoutePlanner.between(Location.FARMING_GUILD, to, config, access, holdings, poh, atlas);
			}
		};
	}

	/**
	 * A part's supplies with the item for the trip to the Farming Guild's bank, used at the end of it (issue #8). Ways
	 * with nothing to bring (a spirit tree, a fairy ring with a staff kept on) add nothing.
	 */
	private static SupplyPlan withTrip(SupplyPlan before, RouteStop trip, Holdings holdings)
	{
		final TravelItem item = trip.travelItem();
		if (item == null)
		{
			return before;
		}
		final int[] ids = item.getItemIds();
		final List<SupplyLine> lines = new ArrayList<>(before.getLines());
		lines.add(new SupplyLine(SupplyLine.Group.TRAVEL, item.getDisplayName()
			+ " (bank at the Farming Guild)", 1, holdings.countAny(ids), holdings.carriedOnly().countAny(ids),
			holdings.where(ids), "To bank partway at the Farming Guild, by its seed vault", 1, false, null, ids));
		return new SupplyPlan(lines, before.getPatchCounts(), before.getNotDue(), before.getTravelPlan(),
			before.getWarnings(), before.getCoins(), before.getRuneSummary(), before.getSlots() + 1,
			before.getPlantings(), before.getStaffSwaps(), before.getCare());
	}

	/**
	 * A part's supplies with the bank teleport to bring, used at the bank that ends it: worn when its slot is free
	 * (elzerma, 2026-10-19: the ring slot was empty, yet the ring of dueling for the Castle Wars bank wasn't asked to be
	 * worn).
	 */
	private SupplyPlan withTeleport(SupplyPlan before, BankTeleport teleport, Holdings holdings, Outfit outfit)
	{
		final int[] ids = teleport.getItemIds();
		final boolean worn = slotFree(wearSlot.apply(ids[0]), before, ids, holdings, outfit);
		final List<SupplyLine> lines = new ArrayList<>(before.getLines());
		final SupplyLine line = new SupplyLine(SupplyLine.Group.TRAVEL, teleport.getItemName() + " (bank partway)", 1,
			holdings.countAny(ids), holdings.carriedOnly().countAny(ids), holdings.where(ids),
			"To bank partway through the run: teleport to " + teleport.getBankName()
				+ (worn ? ". Wear it: no inventory slot" : ""), worn ? 0 : 1, false, null, ids);
		lines.add(worn ? line.worn() : line);
		return new SupplyPlan(lines, before.getPatchCounts(), before.getNotDue(), before.getTravelPlan(),
			before.getWarnings(), before.getCoins(), before.getRuneSummary(), before.getSlots() + (worn ? 0 : 1),
			before.getPlantings(), before.getStaffSwaps(), before.getCare());
	}

	/**
	 * Whether something can be worn in this slot for the part: nothing the part wears goes there (Graceful's cape and
	 * gloves included), and nothing but one of {@code ids} is worn in it now.
	 */
	private boolean slotFree(TravelItem.WearSlot slot, SupplyPlan part, int[] ids, Holdings holdings, Outfit outfit)
	{
		if (slot == null || slot == TravelItem.WearSlot.WEAPON || outfit == Outfit.GRACEFUL
			&& (slot == TravelItem.WearSlot.CAPE || slot == TravelItem.WearSlot.HANDS))
		{
			return false;
		}
		for (SupplyLine line : part.getLines())
		{
			if (line.isWear() && line.getItemIds() != null && line.getItemIds().length > 0
				&& wearSlot.apply(line.getItemIds()[0]) == slot)
			{
				return false;
			}
		}
		for (int id : holdings.in(Holdings.Source.WORN).keySet())
		{
			if (Arrays.stream(ids).noneMatch(i -> i == id) && wearSlot.apply(id) == slot)
			{
				return false;
			}
		}
		return true;
	}

	private List<Patch> patchesAt(List<RouteStop> stops)
	{
		final Set<Location> at = EnumSet.noneOf(Location.class);
		for (RouteStop stop : stops)
		{
			at.add(stop.getLocation());
		}
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : selection.getPatches())
		{
			if (at.contains(patch.getLocation()))
			{
				patches.add(patch);
			}
		}
		return patches;
	}
}
