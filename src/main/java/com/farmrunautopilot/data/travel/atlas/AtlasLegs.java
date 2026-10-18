package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.data.travel.TravelTimes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * Legs from the atlas for one player (docs/plans/travel-rework.md, section 4): the atlas's way from where they are, a
 * teleport and the way from where it lands, the house (teleport in, or a spirit tree in), or a hop (a fairy ring, spirit
 * tree, quetzal...) taken on foot or after a teleport, then the way from where it lands. Only ways, teleports and hops
 * whose needs the player meets, none of them into the Wilderness, and no teleports with a cooldown (elzerma,
 * 2026-10-17). Seconds use the plugin's own timings for each kind of teleport and hop (the quetzal whistle's real wait,
 * not Shortest Path's animation ticks), plus walking tiles and transport ticks; the leg's set-off overhead is added by
 * the planner. Built for one plan: what it works out (needs met, teleports usable, reaching each hub) is kept.
 */
public final class AtlasLegs
{
	private static final double SECONDS_PER_TILE = 0.3;
	private static final double SECONDS_PER_TICK = 0.6;
	/** Teleports with a cooldown (minigame and home teleports) aren't planned (elzerma, 2026-10-17). */
	private static final Set<String> COOLDOWN = Set.of("TELEPORTATION_MINIGAME", "TELEPORTATION_SPELL_HOME");
	/** The house's exits: planned with the player's own house set-up (AtlasHouse), not from the atlas alone. */
	private static final Set<String> HOUSE = Set.of("TELEPORTATION_PORTAL_POH", "TELEPORTATION_BOX");
	/** Teleporting inside the house (Teleport to House with the house's teleport setting "inside"): out of the door. */
	private static final int HOUSE_INSIDE_VARBIT = VarbitID.POH_TELE_TOGGLE;

	private final TravelAtlas atlas;
	private final AtlasAccount account;
	/** The player's house as a way out, or null. */
	private final AtlasHouse house;
	/** The plugin's say on its own items and spells, or null to go by the atlas's needs alone. */
	private final TeleportCheck teleportCheck;
	/** Teleports and hops timed by Shortest Path's ticks (for the referee), not the plugin's timings. */
	private final boolean shortestPathTiming;
	private final List<TravelAtlas.Jump> teleports = new ArrayList<>();
	/** Seconds to use each usable teleport, by its id. */
	private final Map<Integer, Double> teleportTime = new HashMap<>();
	/** Whether each need (by id) is met, worked out once. */
	private final Boolean[] met;
	/** Whether each place (by id) is in the Wilderness, worked out once. */
	private final Boolean[] wild;
	/** Ways through the Wilderness (a transport on the way starts or ends there). */
	private final Map<TravelAtlas.Way, Boolean> wildWays = new IdentityHashMap<>();
	/** The quickest way to each hub from anywhere (a teleport and a walk), worked out once. */
	private final Map<Integer, Reach> hubFromAnywhere = new HashMap<>();
	/** Each place's options that don't depend on where the player is, quickest first, worked out once. */
	private final Map<Integer, List<AtlasOption>> anywhere = new HashMap<>();
	/** Each place's hops in, quickest after the hop first, worked out once. */
	private final Map<Integer, List<HopIn>> hopsIn = new HashMap<>();
	/** The spirit trees the player can use (their hubs), for going into the house by one. */
	private List<Integer> spiritTrees;

	/**
	 * Whether a teleport can be used and how long it takes beyond its kind's timing, where the plugin knows better than
	 * the atlas: its own items (charges, daily uses, used up earlier in the run) and spells (a spellbook swap).
	 */
	public interface TeleportCheck
	{
		/** Seconds to add to this teleport, or null if the player can't use it. */
		Double extraSeconds(TravelAtlas.Jump teleport);
	}

	/** One leg: its seconds (before overhead), what it is, and whether it's only walking (no teleport or hop). */
	@Value
	public static class Leg
	{
		double seconds;
		String how;
		boolean walking;
	}

	/** How to reach a hub: the teleport (or null, on foot), the way, and its seconds. */
	@Value
	private static class Reach
	{
		TravelAtlas.Jump teleport;
		TravelAtlas.Way way;
		double seconds;
	}

	public AtlasLegs(TravelAtlas atlas, AtlasAccount account)
	{
		this(atlas, account, null, null);
	}

	public AtlasLegs(TravelAtlas atlas, AtlasAccount account, AtlasHouse house)
	{
		this(atlas, account, house, null);
	}

	/**
	 * @param house         the player's house as a way out (AtlasHouse.of), or null
	 * @param teleportCheck the plugin's own say on its items and spells, or null to go by the atlas's needs alone. With
	 *                      it, a spell the plugin knows is planned by the plugin's rules for runes and spellbooks (the
	 *                      supply list brings the runes; a spellbook swap costs its time), not the atlas's needs for
	 *                      runes owned and the spellbook in use now.
	 */
	public AtlasLegs(TravelAtlas atlas, AtlasAccount account, AtlasHouse house, TeleportCheck teleportCheck)
	{
		this(atlas, account, house, teleportCheck, false);
	}

	/**
	 * @param shortestPathTiming time teleports and hops by Shortest Path's ticks instead of the plugin's timings, to
	 *                           compare with its own answers (the referee)
	 */
	public AtlasLegs(TravelAtlas atlas, AtlasAccount account, AtlasHouse house, TeleportCheck teleportCheck,
		boolean shortestPathTiming)
	{
		this.atlas = atlas;
		this.account = account;
		this.house = house;
		this.teleportCheck = teleportCheck;
		this.shortestPathTiming = shortestPathTiming;
		this.met = new Boolean[atlas.getNeeds().size()];
		this.wild = new Boolean[atlas.getPlaces().size()];
		for (TravelAtlas.Jump t : atlas.getTeleports())
		{
			if (COOLDOWN.contains(t.getType()) || HOUSE.contains(t.getType()) || wild(t.getTo()))
			{
				continue;
			}
			final Double seconds = teleportSeconds(t);
			if (seconds != null)
			{
				teleports.add(t);
				teleportTime.put(t.getId(), seconds);
			}
		}
	}

	/** How many teleports this player can use, for the log. */
	public int usableTeleports()
	{
		return teleports.size();
	}

	/** Seconds to use this teleport (its kind's timing and anything the plugin adds), or null if it can't be used. */
	private Double teleportSeconds(TravelAtlas.Jump t)
	{
		final boolean pluginSpell = teleportCheck != null && t.getType().equals("TELEPORTATION_SPELL")
			&& AtlasLinks.spell(atlas, t) != null;
		for (int id : t.getNeeds())
		{
			final AtlasNeed need = atlas.need(id);
			// A spell the plugin knows: its runes and spellbook are the plugin's to plan
			if (pluginSpell && (need.getKind() == AtlasNeed.Kind.ITEMS
				|| need.getKind() == AtlasNeed.Kind.VARBIT && need.getVar() == VarbitID.SPELLBOOK))
			{
				continue;
			}
			if (!meets(id))
			{
				return null;
			}
		}
		final Double extra = teleportCheck == null ? Double.valueOf(0) : teleportCheck.extraSeconds(t);
		if (extra == null)
		{
			return null;
		}
		return shortestPathTiming ? t.getTicks() * SECONDS_PER_TICK
			: kindSeconds(t) + extra + (insideHouse(t) ? TravelTimes.HOUSE_DOOR : 0);
	}

	/** Teleport to House set to land inside: out of the door to the portal, where the atlas puts it. */
	private boolean insideHouse(TravelAtlas.Jump t)
	{
		for (int id : t.getNeeds())
		{
			final AtlasNeed need = atlas.need(id);
			if (need.getKind() == AtlasNeed.Kind.VARBIT && need.getVar() == HOUSE_INSIDE_VARBIT && need.passes(1)
				&& !need.passes(0))
			{
				return true;
			}
		}
		return false;
	}

	/** The plugin's timing for a teleport of this kind (TravelTimes), not Shortest Path's animation ticks. */
	static double kindSeconds(TravelAtlas.Jump t)
	{
		switch (t.getType())
		{
			case "TELEPORTATION_SPELL":
				return TravelTimes.TELEPORT;
			case "TELEPORTATION_SPELL_HOME":
				return TravelTimes.HOME_TELEPORT;
			case "QUETZAL_WHISTLE":
				return TravelTimes.QUETZAL_WHISTLE;
			default:
				return TravelTimes.ITEM_TELEPORT;
		}
	}

	/** The plugin's timing for a hop: rings, trees, Renu and balloons; others at their ticks, at least 3 s. */
	double hopSeconds(TravelAtlas.Jump hop)
	{
		if (shortestPathTiming)
		{
			return hop.getTicks() * SECONDS_PER_TICK;
		}
		switch (hop.getType())
		{
			case "FAIRY_RING":
				return TravelTimes.FAIRY_RING;
			case "SPIRIT_TREE":
				return TravelTimes.SPIRIT_TREE;
			case "QUETZAL":
				return TravelTimes.QUETZAL;
			case "HOT_AIR_BALLOON":
				return TravelTimes.BALLOON;
			default:
				return Math.max(3, hop.getTicks() * SECONDS_PER_TICK);
		}
	}

	/** Seconds for this usable teleport, as worked out for this player. */
	public double teleportTime(TravelAtlas.Jump teleport)
	{
		return teleportTime.get(teleport.getId());
	}

	/**
	 * Every way of making a leg to any of {@code to} (a stop's patch places), quickest first.
	 *
	 * @param from where the player is: places with the seconds to walk to each first (0 at a stop's own patches);
	 *             empty for "from anywhere" (the start of a run, after banking)
	 */
	public List<AtlasOption> options(Map<Integer, Double> from, Collection<Integer> to)
	{
		return options(from, to, Double.MAX_VALUE);
	}

	/**
	 * The ways of making a leg that are no more than {@code slack} seconds slower than the quickest, quickest first.
	 * What doesn't depend on where the player is (teleports, the house, a teleport then a hop) is worked out once per
	 * place; walking, a hop on foot and a spirit tree into the house, for each call.
	 */
	public List<AtlasOption> options(Map<Integer, Double> from, Collection<Integer> to, double slack)
	{
		final List<AtlasOption> here = new ArrayList<>();
		final Reach tree = house != null && house.isTreeIn() && !from.isEmpty() ? treeReach(from) : null;
		double quickest = Double.MAX_VALUE;
		for (int target : to)
		{
			final List<AtlasOption> anywhere = anywhere(target);
			if (!anywhere.isEmpty())
			{
				quickest = Math.min(quickest, anywhere.get(0).getSeconds());
			}
			for (Map.Entry<Integer, Double> start : from.entrySet())
			{
				final TravelAtlas.Way way = usable(atlas.ways(start.getKey(), target));
				if (way != null)
				{
					here.add(new AtlasOption(AtlasOption.Kind.WALK, null, null, null, null, way, start.getValue() + seconds(way)));
				}
			}
			if (tree != null)
			{
				treeHouseOptions(target, tree, here);
			}
		}
		for (AtlasOption option : here)
		{
			quickest = Math.min(quickest, option.getSeconds());
		}
		// A hop on foot: to its hub, the hop, then the way from where it lands; the quickest after the hop first
		if (!from.isEmpty())
		{
			final Map<Integer, Reach> onFoot = new HashMap<>();
			for (int target : to)
			{
				for (HopIn in : hopsIn(target))
				{
					if (in.after > limit(quickest, slack))
					{
						break;
					}
					final Reach walk = onFoot.computeIfAbsent(in.hop.getFrom(), hub -> reachOnFoot(from, hub));
					if (walk != null && walk.seconds + in.after <= limit(quickest, slack))
					{
						here.add(new AtlasOption(AtlasOption.Kind.HOP, null, null, in.hop, walk.way, in.last,
							walk.seconds + in.after));
						quickest = Math.min(quickest, walk.seconds + in.after);
					}
				}
			}
		}
		final double limit = limit(quickest, slack);
		final List<AtlasOption> all = new ArrayList<>();
		for (AtlasOption option : here)
		{
			if (option.getSeconds() <= limit)
			{
				all.add(option);
			}
		}
		for (int target : to)
		{
			// Quickest first: the rest are slower still
			for (AtlasOption option : anywhere(target))
			{
				if (option.getSeconds() > limit)
				{
					break;
				}
				all.add(option);
			}
		}
		all.sort(Comparator.comparingDouble(AtlasOption::getSeconds));
		return all;
	}

	/** The slowest an option can be and still be within {@code slack} of the quickest. */
	private static double limit(double quickest, double slack)
	{
		return quickest == Double.MAX_VALUE || slack == Double.MAX_VALUE ? Double.MAX_VALUE : quickest + slack;
	}

	/** A hop into a place by a stop: the hop, the way from where it lands, and the seconds for both. */
	@Value
	private static class HopIn
	{
		TravelAtlas.Jump hop;
		TravelAtlas.Way last;
		double after;
	}

	/** Every teleport, way through the house and teleport-then-hop to this place, quickest first; worked out once. */
	private List<AtlasOption> anywhere(int target)
	{
		return anywhere.computeIfAbsent(target, t ->
		{
			final List<AtlasOption> options = new ArrayList<>();
			for (TravelAtlas.Jump teleport : teleports)
			{
				final TravelAtlas.Way way = usable(atlas.ways(teleport.getTo(), t));
				if (way != null)
				{
					options.add(new AtlasOption(AtlasOption.Kind.TELEPORT, teleport, null, null, null, way,
						teleportTime(teleport) + seconds(way)));
				}
			}
			final Double in = house != null ? house.getTeleportIn() : null;
			if (in != null)
			{
				for (AtlasHouse.Exit exit : house.getExits())
				{
					final TravelAtlas.Way way = usable(atlas.ways(exit.getLanding(), t));
					if (way != null)
					{
						options.add(new AtlasOption(AtlasOption.Kind.HOUSE, null, exit, null, null, way,
							in + exit.getSeconds() + seconds(way)));
					}
				}
			}
			for (HopIn hopIn : hopsIn(t))
			{
				final Reach reach = hubFromAnywhere.computeIfAbsent(hopIn.hop.getFrom(), this::reachFromAnywhere);
				if (reach != null)
				{
					options.add(new AtlasOption(AtlasOption.Kind.TELEPORT_HOP, reach.teleport, null, hopIn.hop, reach.way,
						hopIn.last, reach.seconds + hopIn.after));
				}
			}
			options.sort(Comparator.comparingDouble(AtlasOption::getSeconds));
			return options;
		});
	}

	/** Every hop the player can take into a place with a way on to this one, quickest after the hop first; once. */
	private List<HopIn> hopsIn(int target)
	{
		return hopsIn.computeIfAbsent(target, t ->
		{
			final List<HopIn> found = new ArrayList<>();
			for (int landing : atlas.into(t))
			{
				final TravelAtlas.Place place = atlas.place(landing);
				final TravelAtlas.Way last = place == null || place.getKind() != TravelAtlas.Kind.LANDING ? null
					: usable(atlas.ways(landing, t));
				if (last == null)
				{
					continue;
				}
				for (TravelAtlas.Jump hop : atlas.hopsInto(landing))
				{
					if (!wild(hop.getFrom()) && !wild(hop.getTo()) && meetsAll(hop.getNeeds()))
					{
						found.add(new HopIn(hop, last, hopSeconds(hop) + seconds(last)));
					}
				}
			}
			found.sort(Comparator.comparingDouble(HopIn::getAfter));
			return found;
		});
	}

	/**
	 * Into the house by the spirit tree near the player ({@code tree}), which lands in the garden: indoors to an exit
	 * (not the house's own tree), or out of the front door to the portal.
	 */
	private void treeHouseOptions(int target, Reach tree, List<AtlasOption> all)
	{
		final double in = tree.seconds + TravelTimes.SPIRIT_TREE;
		for (AtlasHouse.Exit exit : house.getExits())
		{
			final TravelAtlas.Way way = exit.getKind() == AtlasHouse.ExitKind.SPIRIT_TREE ? null
				: usable(atlas.ways(exit.getLanding(), target));
			if (way != null)
			{
				all.add(new AtlasOption(AtlasOption.Kind.TREE_HOUSE, null, exit, null, tree.way, way,
					in + TravelTimes.HOUSE_DOOR + TravelTimes.HOUSE_WALK + exit.getSeconds() + seconds(way)));
			}
		}
		final AtlasHouse.Exit portal = house.getPortal();
		final TravelAtlas.Way out = portal == null ? null : usable(atlas.ways(portal.getLanding(), target));
		if (out != null)
		{
			all.add(new AtlasOption(AtlasOption.Kind.TREE_HOUSE, null, portal, null, tree.way, out,
				in + TravelTimes.HOUSE_WALK + seconds(out)));
		}
	}

	/** On foot from one place to another (the atlas's way the player can use), or null if there's none. */
	public AtlasOption walk(int from, int to)
	{
		final TravelAtlas.Way way = usable(atlas.ways(from, to));
		return way == null ? null : new AtlasOption(AtlasOption.Kind.WALK, null, null, null, null, way, seconds(way));
	}

	/**
	 * The quickest leg from a place (or from anywhere, {@code from} &lt; 0) to any of these places; null if there's
	 * none. For the referee and the tests.
	 */
	public Leg quickest(int from, Collection<Integer> to)
	{
		final List<AtlasOption> options = options(from >= 0 ? Map.of(from, 0.0) : Collections.emptyMap(), to, 0);
		if (options.isEmpty())
		{
			return null;
		}
		final AtlasOption best = options.get(0);
		return new Leg(best.getSeconds(), how(best), best.isWalking());
	}

	/** e.g. "Skills necklace: Crafting Guild > walk 8 tiles > HOT_AIR_BALLOON Taverley > walk 18 tiles". */
	public String how(AtlasOption option)
	{
		final StringBuilder s = new StringBuilder();
		if (option.getTeleport() != null)
		{
			s.append(option.getTeleport().getName()).append(" > ");
		}
		if (option.getKind() == AtlasOption.Kind.TREE_HOUSE)
		{
			s.append(describe(option.getToHub())).append(" > Spirit tree: Your house > ");
		}
		if (option.getHouseExit() != null)
		{
			s.append("House > ").append(option.getHouseExit().getName()).append(" > ");
		}
		if (option.getHop() != null)
		{
			s.append(describe(option.getToHub())).append(" > ").append(option.getHop().getType()).append(' ')
				.append(option.getHop().getName()).append(" > ");
		}
		return s.append(describe(option.getWay())).toString();
	}

	/** How the seconds add up, e.g. "Ectophial 4 + walk 70 tiles, Travel Bill Teach 25". */
	public String breakdown(AtlasOption option)
	{
		final StringBuilder s = new StringBuilder();
		if (option.getTeleport() != null)
		{
			s.append(option.getTeleport().getName()).append(' ').append(round(teleportTime(option.getTeleport()))).append(" + ");
		}
		if (option.getKind() == AtlasOption.Kind.HOUSE)
		{
			s.append("house ").append(round(house.getTeleportIn())).append(" + ");
		}
		if (option.getKind() == AtlasOption.Kind.TREE_HOUSE)
		{
			s.append("to a spirit tree, ").append(describe(option.getToHub())).append(' ').append(round(seconds(option.getToHub())))
				.append(" + tree to the house ").append(round(TravelTimes.SPIRIT_TREE)).append(" + ");
		}
		if (option.getHouseExit() != null)
		{
			s.append(option.getHouseExit().getName()).append(' ').append(round(option.getHouseExit().getSeconds())).append(" + ");
		}
		if (option.getHop() != null)
		{
			s.append("to ").append(option.getHop().getName()).append(", ").append(describe(option.getToHub())).append(' ')
				.append(round(seconds(option.getToHub()))).append(" + hop ").append(round(hopSeconds(option.getHop())))
				.append(" + ");
		}
		return s.append(describe(option.getWay())).append(' ').append(round(seconds(option.getWay()))).toString();
	}

	private Reach reachOnFoot(Map<Integer, Double> from, int hub)
	{
		Reach best = null;
		for (Map.Entry<Integer, Double> start : from.entrySet())
		{
			final TravelAtlas.Way way = usable(atlas.ways(start.getKey(), hub));
			if (way != null && (best == null || start.getValue() + seconds(way) < best.seconds))
			{
				best = new Reach(null, way, start.getValue() + seconds(way));
			}
		}
		return best;
	}

	private Reach reachFromAnywhere(int hub)
	{
		Reach best = null;
		for (TravelAtlas.Jump t : teleports)
		{
			final TravelAtlas.Way way = usable(atlas.ways(t.getTo(), hub));
			if (way != null)
			{
				final double total = teleportTime(t) + seconds(way);
				if (best == null || total < best.seconds)
				{
					best = new Reach(t, way, total);
				}
			}
		}
		return best;
	}

	/** The nearest spirit tree the player can use, on foot from where they are. */
	private Reach treeReach(Map<Integer, Double> from)
	{
		if (spiritTrees == null)
		{
			spiritTrees = new ArrayList<>();
			for (TravelAtlas.Jump hop : atlas.getHops())
			{
				if (hop.getType().equals("SPIRIT_TREE") && !spiritTrees.contains(hop.getFrom()) && !wild(hop.getFrom())
					&& meetsAll(hop.getNeeds()))
				{
					spiritTrees.add(hop.getFrom());
				}
			}
		}
		Reach best = null;
		for (int hub : spiritTrees)
		{
			final Reach reach = reachOnFoot(from, hub);
			if (reach != null && (best == null || reach.seconds < best.seconds))
			{
				best = reach;
			}
		}
		return best;
	}

	/**
	 * The ways are quickest first, and each slower one needs less: the first the player can use (and that keeps out of
	 * the Wilderness) is the best.
	 */
	private TravelAtlas.Way usable(List<TravelAtlas.Way> ways)
	{
		for (TravelAtlas.Way way : ways)
		{
			if (meetsAll(way.getNeeds()) && !wild(way))
			{
				return way;
			}
		}
		return null;
	}

	private boolean meetsAll(int[] needs)
	{
		for (int id : needs)
		{
			if (!meets(id))
			{
				return false;
			}
		}
		return true;
	}

	/** A need (by id) is met; each is worked out once. */
	private boolean meets(int id)
	{
		Boolean ok = met[id];
		if (ok == null)
		{
			ok = account.meets(atlas.need(id));
			met[id] = ok;
		}
		return ok;
	}

	private boolean wild(int place)
	{
		Boolean w = wild[place];
		if (w == null)
		{
			w = inWilderness(atlas.place(place).getPoint());
			wild[place] = w;
		}
		return w;
	}

	private boolean wild(TravelAtlas.Way way)
	{
		return wildWays.computeIfAbsent(way, w ->
		{
			for (int step : w.getSteps())
			{
				if (inWilderness(atlas.step(step).getOrigin()) || inWilderness(atlas.step(step).getDestination()))
				{
					return true;
				}
			}
			return false;
		});
	}

	/** Shortest Path's Wilderness areas (BSD-2, WildernessChecker at 37abff3): above ground and underground. */
	static boolean inWilderness(WorldPoint p)
	{
		final int x = p.getX();
		final int y = p.getY();
		return x >= 2944 && x < 2944 + 448 && y >= 3525 && y < 3525 + 448
			|| x >= 2944 && x < 2944 + 518 && y >= 9918 && y < 9918 + 458;
	}

	static double seconds(TravelAtlas.Way way)
	{
		return way.getTiles() * SECONDS_PER_TILE + way.getTicks() * SECONDS_PER_TICK;
	}

	/** e.g. "walk 81 tiles, Cross Stepping stone". */
	public String describe(TravelAtlas.Way way)
	{
		final StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "walk %d tiles", way.getTiles()));
		for (int step : way.getSteps())
		{
			s.append(", ").append(atlas.step(step).getName());
		}
		return s.toString();
	}

	private static String round(double seconds)
	{
		return String.valueOf(Math.round(seconds));
	}
}
