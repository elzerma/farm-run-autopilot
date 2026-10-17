package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.travel.Rune;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carrying more than one staff and swapping between them during the run, when that takes fewer inventory slots than
 * carrying the runes (issue #7, elzerma's decisions of 2026-10-17). Pure.
 *
 * <p>How slots are counted: the wielded staff takes none and each other staff carried takes one. Runes take
 * {@link #runeSlots}: with a rune pouch, the pouch is one slot shared by the rune types in it (3 types: 1/3 each, 2:
 * 1/2 each, 1: the whole slot; a divine rune pouch holds 4, so 1/4 each), and each type that doesn't fit takes a slot
 * of its own; without one, each type takes a slot. A plan with swaps is only used when it frees at least one whole
 * slot over keeping one staff on.
 *
 * <p>One staff is wielded per cast, so a cast needing two rune types is only fully covered by a combination staff
 * (or a staff and a tome); carrying a second staff only pays when different casts need different runes.
 *
 * <p>Staves are only swapped just before setting off for a stop, so the staff wielded for the way there is also the
 * one at its patches (for Cure plant).
 */
final class StaffPlanner
{
	/** Swapping between more than this many staves never saves a slot: there are only four elemental runes. */
	private static final int MOST_STAVES = 3;
	static final int POUCH_TYPES = 3;
	static final int DIVINE_POUCH_TYPES = 4;

	/** Setting off for a stop: what has to be wielded for it. */
	static final class Leg
	{
		final Location location;
		/** Runes cast on the way there (the teleport, Teleport to House, Spellbook Swap). */
		final Map<Rune, Integer> runes;
		/** Runes that may be cast at its patches (Cure plant): carried once for the run, not once a stop. */
		final Map<Rune, Integer> backup;
		/** Needs the fairy ring staff wielded. */
		final boolean fairyRing;

		Leg(Location location, Map<Rune, Integer> runes, Map<Rune, Integer> backup, boolean fairyRing)
		{
			this.location = location;
			this.runes = runes;
			this.backup = backup;
			this.fairyRing = fairyRing;
		}

		Set<Rune> needs()
		{
			final Set<Rune> needs = EnumSet.noneOf(Rune.class);
			needs.addAll(runes.keySet());
			needs.addAll(backup.keySet());
			return needs;
		}
	}

	/** The staves to bring and when to wield each. */
	static final class Plan
	{
		/** A staff item ID, or {@link SupplyCalculator.Wearable#FAIRY_RING_STAFF}, in the order first wielded. */
		final List<Object> staves;
		/** Before setting off for each of these stops, the staff to wield (the first is the one put on at the bank). */
		final Map<Location, Object> wield;
		/** The runes still to carry, and how many. */
		final Map<Rune, Integer> runes;
		/** Rune types needed that no longer have to be carried at all. */
		final Set<Rune> free;
		final int slots;
		final int swaps;

		Plan(List<Object> staves, Map<Location, Object> wield, Map<Rune, Integer> runes, Set<Rune> free, int slots,
			int swaps)
		{
			this.staves = staves;
			this.wield = wield;
			this.runes = runes;
			this.free = free;
			this.slots = slots;
			this.swaps = swaps;
		}

		Object start()
		{
			return staves.isEmpty() ? null : staves.get(0);
		}
	}

	private StaffPlanner()
	{
	}

	/** Rune types the pouch holds: 4 for a divine rune pouch, 3 for a rune pouch, 0 with neither. */
	static int pouchTypes(Holdings holdings)
	{
		if (holdings.countAny(SupplyItems.DIVINE_RUNE_POUCHES) > 0)
		{
			return DIVINE_POUCH_TYPES;
		}
		return holdings.countAny(SupplyItems.RUNE_POUCHES) > 0 ? POUCH_TYPES : 0;
	}

	/** Inventory slots for this many rune types: the pouch's one slot for those it holds, one each for the rest. */
	static int runeSlots(int types, int pouchTypes)
	{
		if (pouchTypes == 0 || types == 0)
		{
			return types;
		}
		return 1 + Math.max(0, types - pouchTypes);
	}

	/**
	 * The best plan with swaps, or null when none frees a whole slot over {@code current}, the staff the plan keeps on
	 * otherwise (a staff ID, the fairy ring staff, or null for none).
	 *
	 * @param staves the staves the player owns that cover a rune this part needs, one per set of runes covered, plus
	 *               the fairy ring staff when fairy rings need it
	 * @param alwaysFree runes covered all the way by something else (a tome)
	 */
	static Plan plan(List<Leg> legs, Map<Rune, Integer> need, List<Object> staves, Object current, Set<Rune> alwaysFree,
		int pouchTypes)
	{
		final Plan kept = evaluate(legs, need, current == null || current == SupplyCalculator.Wearable.MAGIC_SECATEURS
			? Collections.emptyList() : Collections.singletonList(current), alwaysFree, pouchTypes);
		Plan best = null;
		final int n = staves.size();
		for (int mask = 1; mask < 1 << n; mask++)
		{
			final int size = Integer.bitCount(mask);
			if (size < 2 || size > MOST_STAVES)
			{
				continue;
			}
			final List<Object> chosen = new ArrayList<>();
			for (int i = 0; i < n; i++)
			{
				if ((mask & 1 << i) != 0)
				{
					chosen.add(staves.get(i));
				}
			}
			final Plan plan = evaluate(legs, need, chosen, alwaysFree, pouchTypes);
			if (plan != null && plan.staves.size() == size && plan.swaps > 0 && (best == null || better(plan, best)))
			{
				best = plan;
			}
		}
		if (best == null || (kept != null && best.slots > kept.slots - 1))
		{
			return null;
		}
		return best;
	}

	private static boolean better(Plan a, Plan b)
	{
		if (a.slots != b.slots)
		{
			return a.slots < b.slots;
		}
		if (a.swaps != b.swaps)
		{
			return a.swaps < b.swaps;
		}
		return a.staves.size() < b.staves.size();
	}

	/**
	 * The fewest slots these staves can do it in, with the fewest swaps for that; null if some fairy ring can't be
	 * used. Every staff given is carried, but {@link Plan#staves} lists only those wielded somewhere.
	 */
	static Plan evaluate(List<Leg> legs, Map<Rune, Integer> need, List<Object> staves, Set<Rune> alwaysFree,
		int pouchTypes)
	{
		// The elemental runes a staff could cover; each subset of them is tried as the set left uncarried
		final List<Rune> coverable = new ArrayList<>();
		for (Rune rune : need.keySet())
		{
			if (!alwaysFree.contains(rune) && anyCovers(staves, rune))
			{
				coverable.add(rune);
			}
		}
		Plan best = null;
		for (int mask = 0; mask < 1 << coverable.size(); mask++)
		{
			final Set<Rune> free = EnumSet.noneOf(Rune.class);
			for (int i = 0; i < coverable.size(); i++)
			{
				if ((mask & 1 << i) != 0)
				{
					free.add(coverable.get(i));
				}
			}
			final List<Object> assigned = assign(legs, staves, free);
			if (assigned == null)
			{
				continue;
			}
			final Plan plan = toPlan(legs, need, staves.size(), assigned, alwaysFree, pouchTypes);
			if (best == null || better(plan, best))
			{
				best = plan;
			}
		}
		return best;
	}

	/**
	 * The staff wielded for each leg, so that {@code free} (runes a staff covers) needn't be carried, with the fewest swaps: keep the staff on while it
	 * will do, and otherwise wield the one that will do for longest. Null when some leg has no staff that will do; a
	 * null entry is a leg that needs none.
	 */
	private static List<Object> assign(List<Leg> legs, List<Object> staves, Set<Rune> free)
	{
		final List<List<Object>> allowed = new ArrayList<>();
		for (Leg leg : legs)
		{
			final Set<Rune> mustCover = leg.needs();
			mustCover.retainAll(free);
			if (mustCover.isEmpty() && !leg.fairyRing)
			{
				allowed.add(null);
				continue;
			}
			final List<Object> ok = new ArrayList<>();
			for (Object staff : staves)
			{
				if (covers(staff).containsAll(mustCover) && (!leg.fairyRing || staff == SupplyCalculator.Wearable.FAIRY_RING_STAFF))
				{
					ok.add(staff);
				}
			}
			if (ok.isEmpty())
			{
				return null;
			}
			allowed.add(ok);
		}
		final List<Object> assigned = new ArrayList<>();
		Object wielded = null;
		for (int i = 0; i < legs.size(); i++)
		{
			final List<Object> ok = allowed.get(i);
			if (ok == null)
			{
				assigned.add(null);
				continue;
			}
			if (wielded == null || !ok.contains(wielded))
			{
				int furthest = -1;
				for (Object staff : ok)
				{
					int until = i + 1;
					while (until < legs.size() && (allowed.get(until) == null || allowed.get(until).contains(staff)))
					{
						until++;
					}
					if (until > furthest)
					{
						furthest = until;
						wielded = staff;
					}
				}
			}
			assigned.add(wielded);
		}
		return assigned;
	}

	private static Plan toPlan(List<Leg> legs, Map<Rune, Integer> need, int carried, List<Object> assigned,
		Set<Rune> alwaysFree, int pouchTypes)
	{
		final Map<Rune, Integer> runes = new EnumMap<>(Rune.class);
		final Map<Rune, Integer> backup = new EnumMap<>(Rune.class);
		final Map<Rune, Integer> fromLegs = new EnumMap<>(Rune.class);
		final List<Object> staves = new ArrayList<>();
		final Map<Location, Object> wield = new LinkedHashMap<>();
		Object wielded = null;
		for (int i = 0; i < legs.size(); i++)
		{
			final Leg leg = legs.get(i);
			final Object staff = assigned.get(i);
			if (staff != null && !staff.equals(wielded))
			{
				wielded = staff;
				wield.put(leg.location, staff);
				if (!staves.contains(staff))
				{
					staves.add(staff);
				}
			}
			final Set<Rune> covered = wielded == null ? EnumSet.noneOf(Rune.class) : covers(wielded);
			leg.runes.forEach((rune, n) ->
			{
				fromLegs.merge(rune, n, Integer::sum);
				if (!covered.contains(rune) && !alwaysFree.contains(rune))
				{
					runes.merge(rune, n, Integer::sum);
				}
			});
			leg.backup.forEach((rune, n) ->
			{
				if (!covered.contains(rune) && !alwaysFree.contains(rune))
				{
					backup.merge(rune, n, Math::max);
				}
			});
		}
		backup.forEach((rune, n) -> runes.merge(rune, n, Integer::sum));
		// Anything not cast on a leg (there shouldn't be any) is carried in full
		final Set<Rune> backupRunes = EnumSet.noneOf(Rune.class);
		for (Leg leg : legs)
		{
			backupRunes.addAll(leg.backup.keySet());
		}
		need.forEach((rune, n) ->
		{
			final int rest = n - fromLegs.getOrDefault(rune, 0);
			if (rest > 0 && !backupRunes.contains(rune) && !alwaysFree.contains(rune))
			{
				runes.merge(rune, rest, Integer::sum);
			}
		});
		final Set<Rune> free = EnumSet.noneOf(Rune.class);
		for (Rune rune : need.keySet())
		{
			if (!runes.containsKey(rune))
			{
				free.add(rune);
			}
		}
		final int slots = Math.max(0, carried - 1) + runeSlots(runes.size(), pouchTypes);
		return new Plan(staves, wield, runes, free, slots, Math.max(0, wield.size() - 1));
	}

	static Set<Rune> covers(Object staff)
	{
		final Set<Rune> runes = staff instanceof Integer ? SupplyItems.INFINITE_RUNE_ITEMS.get(staff) : null;
		return runes != null ? runes : Collections.emptySet();
	}

	private static boolean anyCovers(List<Object> staves, Rune rune)
	{
		for (Object staff : staves)
		{
			if (covers(staff).contains(rune))
			{
				return true;
			}
		}
		return false;
	}
}
