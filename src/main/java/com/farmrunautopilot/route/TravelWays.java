package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.travel.ItemCharges;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasLegs;
import com.farmrunautopilot.data.travel.atlas.AtlasLinks;
import com.farmrunautopilot.data.travel.atlas.AtlasNeed;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * What the Travel tab shows of each family of ways to travel (docs/plans/travel-rework.md, sprint T7, agreed with
 * elzerma on 2026-10-20): each way the player has or could get, whether they can use it now (ticked) or not (greyed
 * out, with why on hover: "needs 45 Agility", no charges, none left today...), and how many are ready. Every spell,
 * ring, tree, pad, ship and cart is listed; teleport items and tablets only if the player owns them, as the atlas knows
 * over 300. Worked out on the client thread with each plan, from the atlas, rules and choices the planner uses;
 * immutable, read on the Swing thread.
 */
@Value
public class TravelWays
{
	/** Before the travel atlas is read. */
	public static final TravelWays EMPTY = new TravelWays(Collections.emptyList());

	/** How a way stands for the player, closest to usable first. */
	public enum State
	{
		READY,
		COOLING_DOWN,
		NONE_TODAY,
		LAST_CHARGE_KEPT,
		NO_CHARGES,
		NEEDS,
		WILDERNESS
	}

	/** One way in a family's list. */
	@Value
	public static class Way
	{
		/** What it's turned off by (AtlasWays.key). */
		String key;
		/** As its family's list names it, e.g. "Skills necklace: Farming Guild", "CKR (South of Tai Bwo Wannai)". */
		String name;
		/** How it stands for the player, whether or not they turned it off. */
		State state;
		/** What it lacks, for {@link State#NEEDS}, e.g. "45 Agility"; otherwise null. */
		String needs;
		/** The player turned it off ("don't use this way"). */
		boolean off;
		/** For the hover: what it needs, its charges or uses left, and why it can't be used. */
		String detail;
	}

	/** One family: its ways and how many are ready. */
	@Value
	public static class Family
	{
		TravelFamily family;
		/** Its ways by name; none for a family without a list (House, Agility shortcuts). */
		List<Way> ways;
		/** Ways ready to use and not turned off, counted as if the family were on. */
		int usable;
		/** What every way in it lacks when none is ready, e.g. "Twilight's Promise"; or null. */
		String needs;

		/** The words on the right of the family's row: "38 usable" or "needs Twilight's Promise". */
		public String summary()
		{
			return needs != null ? "needs " + needs : usable + " usable";
		}
	}

	/** Every family, in the tab's order; empty before the atlas is read. */
	List<Family> families;

	/** The family's ways, or null before the atlas is read. */
	public Family get(TravelFamily family)
	{
		for (Family f : families)
		{
			if (f.getFamily() == family)
			{
				return f;
			}
		}
		return null;
	}

	/**
	 * Every family's ways for this player, before anything is used up.
	 *
	 * @param itemName an item's name (client thread), for an item a way needs
	 */
	static TravelWays of(RouteAtlas routeAtlas, RunConfig config, AccessSnapshot access, Holdings holdings,
		PohSetup poh, IntFunction<String> itemName)
	{
		if (routeAtlas == null)
		{
			return EMPTY;
		}
		return new Builder(routeAtlas, config, access, holdings, poh, itemName).build();
	}

	/** Works one player's ways out once. */
	private static final class Builder
	{
		private final TravelAtlas atlas;
		/** The player with every family and way on, for what a way would need if it were. */
		private final AtlasAccount everything;
		private final AtlasWays choices;
		private final AtlasLegs.TeleportCheck rules;
		private final ChargeBudget budget;
		private final RunConfig config;
		private final AccessSnapshot access;
		private final Holdings holdings;
		private final IntFunction<String> itemName;
		/** Whether each need (by id) is met, worked out once. */
		private final Boolean[] met;

		Builder(RouteAtlas routeAtlas, RunConfig config, AccessSnapshot access, Holdings holdings, PohSetup poh,
			IntFunction<String> itemName)
		{
			this.atlas = routeAtlas.getAtlas();
			this.everything = routeAtlas.getAccount().withWays(AtlasWays.ALL);
			this.choices = routeAtlas.getAccount().getWays();
			this.rules = RoutePlanner.startRules(config, access, holdings, poh, routeAtlas);
			this.budget = new ChargeBudget(holdings, config.isKeepLastCharge());
			this.config = config;
			this.access = access;
			this.holdings = holdings;
			this.itemName = itemName;
			this.met = new Boolean[atlas.getNeeds().size()];
		}

		TravelWays build()
		{
			// Each family's teleports and hops, grouped by way (one name can be several of the atlas's rows)
			final Map<TravelFamily, Map<String, List<TravelAtlas.Jump>>> grouped = new EnumMap<>(TravelFamily.class);
			for (TravelAtlas.Jump t : atlas.getTeleports())
			{
				group(grouped, t);
			}
			for (TravelAtlas.Jump hop : atlas.getHops())
			{
				group(grouped, hop);
			}
			final List<Family> families = new ArrayList<>();
			for (TravelFamily family : TravelFamily.values())
			{
				final List<Way> ways = new ArrayList<>();
				for (Map.Entry<String, List<TravelAtlas.Jump>> e : grouped.getOrDefault(family, Collections.emptyMap())
					.entrySet())
				{
					final Way way = way(family, e.getKey(), e.getValue());
					if (way != null)
					{
						ways.add(way);
					}
				}
				ways.sort(Comparator.comparing(w -> w.getName().toLowerCase(Locale.ROOT)));
				families.add(family(family, ways));
			}
			return new TravelWays(Collections.unmodifiableList(families));
		}

		private static void group(Map<TravelFamily, Map<String, List<TravelAtlas.Jump>>> grouped, TravelAtlas.Jump jump)
		{
			final TravelFamily family = AtlasWays.family(jump);
			if (family != null && family.isListed())
			{
				grouped.computeIfAbsent(family, k -> new LinkedHashMap<>())
					.computeIfAbsent(AtlasWays.key(jump), k -> new ArrayList<>()).add(jump);
			}
		}

		/** A family with its count, and what all its ways lack when none is ready. */
		private static Family family(TravelFamily family, List<Way> ways)
		{
			int usable = 0;
			String needs = null;
			boolean same = true;
			for (Way way : ways)
			{
				if (way.getState() == State.READY && !way.isOff())
				{
					usable++;
				}
				if (way.getState() != State.NEEDS || needs != null && !needs.equals(way.getNeeds()))
				{
					same = false;
				}
				needs = way.getNeeds();
			}
			return new Family(family, Collections.unmodifiableList(ways), usable,
				usable == 0 && same && !ways.isEmpty() ? needs : null);
		}

		/** A way from its rows: the row closest to usable says how it stands; null if it isn't listed. */
		private Way way(TravelFamily family, String key, List<TravelAtlas.Jump> rows)
		{
			// Teleport items and tablets only if the player owns one (the atlas knows over 300)
			if ((family == TravelFamily.TELEPORT_ITEMS || family == TravelFamily.TABLETS) && rows.stream().noneMatch(this::owned))
			{
				return null;
			}
			Stand best = null;
			for (TravelAtlas.Jump row : rows)
			{
				final Stand stand = row.getFrom() >= 0 ? hop(row) : teleport(row);
				if (best == null || stand.state.ordinal() < best.state.ordinal())
				{
					best = stand;
				}
			}
			final boolean off = choices.isOff(key);
			return new Way(key, AtlasWays.name(atlas, rows.get(0)), best.state, best.needs, off,
				detail(key, rows.get(0), best, off));
		}

		/** How one teleport stands: the Wilderness, what the atlas says it needs, then the plugin's own rules. */
		private Stand teleport(TravelAtlas.Jump t)
		{
			if (choices.avoidsWilderness() && AtlasLegs.inWilderness(atlas.place(t.getTo()).getPoint()))
			{
				return new Stand(State.WILDERNESS, null, t);
			}
			// A spell the plugin knows: its runes and spellbook are the plugin's to plan, as the planner has it
			final boolean pluginSpell = t.getType().equals("TELEPORTATION_SPELL") && AtlasLinks.spell(atlas, t) != null;
			boolean cooling = false;
			for (int id : t.getNeeds())
			{
				final AtlasNeed need = atlas.need(id);
				if (pluginSpell && (need.getKind() == AtlasNeed.Kind.ITEMS
					|| need.getKind() == AtlasNeed.Kind.VARBIT && need.getVar() == VarbitID.SPELLBOOK) || meets(id))
				{
					continue;
				}
				if (need.isCooldown())
				{
					cooling = true;
					continue;
				}
				return new Stand(State.NEEDS, missing(need), t);
			}
			if (cooling)
			{
				return new Stand(State.COOLING_DOWN, null, t);
			}
			return rules.extraSeconds(t) != null ? new Stand(State.READY, null, t) : why(t);
		}

		/** Why the plugin's own rules rule a teleport out: casting, charges, today's uses. */
		private Stand why(TravelAtlas.Jump t)
		{
			final Spell spell = AtlasLinks.spell(atlas, t);
			if (spell != null && t.getType().equals("TELEPORTATION_SPELL"))
			{
				if (access.level(Skill.MAGIC) < spell.getMagicLevel())
				{
					return new Stand(State.NEEDS, spell.getMagicLevel() + " Magic", t);
				}
				for (Requirement requirement : spell.getRequirements())
				{
					if (!access.isMet(requirement))
					{
						return new Stand(State.NEEDS, requirement.describe().replaceFirst("^Needs ", ""), t);
					}
				}
				// A spellbook it can't get to (no altar for it, no Spellbook Swap)
				final String book = spell.getSpellbook().name().toLowerCase(Locale.ROOT);
				return new Stand(State.NEEDS, "the " + (spell.getSpellbook() == Spellbook.STANDARD ? book
					: Character.toUpperCase(book.charAt(0)) + book.substring(1)) + " spellbook", t);
			}
			for (TravelItem item : AtlasLinks.items(atlas, t))
			{
				if (holdings.countAny(item.getItemIds()) == 0)
				{
					continue;
				}
				if (!budget.hasCharge(item))
				{
					final Integer charges = ChargeBudget.held(item, holdings);
					return new Stand(charges != null && charges > 0 && config.isKeepLastCharge()
						&& ItemCharges.isRechargeable(item) ? State.LAST_CHARGE_KEPT : State.NO_CHARGES, null, t);
				}
				final TravelMethod daily = AtlasTravel.daily(atlas, t);
				if (daily != null && !budget.hasUseToday(daily))
				{
					return new Stand(State.NONE_TODAY, null, t);
				}
			}
			final List<TravelItem> items = AtlasLinks.items(atlas, t);
			return new Stand(State.NEEDS, items.isEmpty() ? "an unlock" : items.get(0).getDisplayName(), t);
		}

		/** How one hop stands: the Wilderness, then what it needs. */
		private Stand hop(TravelAtlas.Jump hop)
		{
			if (choices.avoidsWilderness() && (AtlasLegs.inWilderness(atlas.place(hop.getFrom()).getPoint())
				|| AtlasLegs.inWilderness(atlas.place(hop.getTo()).getPoint())))
			{
				return new Stand(State.WILDERNESS, null, hop);
			}
			for (int id : hop.getNeeds())
			{
				if (!meets(id))
				{
					return new Stand(State.NEEDS, missing(atlas.need(id)), hop);
				}
			}
			return new Stand(State.READY, null, hop);
		}

		/** The player owns this teleport's item (its first item need is met), or it needs none. */
		private boolean owned(TravelAtlas.Jump t)
		{
			for (int id : t.getNeeds())
			{
				if (atlas.need(id).getKind() == AtlasNeed.Kind.ITEMS)
				{
					return meets(id);
				}
			}
			return true;
		}

		private boolean meets(int id)
		{
			Boolean ok = met[id];
			if (ok == null)
			{
				ok = everything.meets(atlas.need(id));
				met[id] = ok;
			}
			return ok;
		}

		/** A need the player lacks, in the tab's words: "45 Agility", "Cabin Fever", "30 coins", "Dramen staff". */
		private String missing(AtlasNeed need)
		{
			if (need.getKind() != AtlasNeed.Kind.ITEMS)
			{
				return everything.missing(need);
			}
			final AtlasNeed.Branch branch = need.getBranches().isEmpty() ? null : need.getBranches().get(0);
			if (branch == null || branch.getItems().length == 0)
			{
				return "an unlock";
			}
			final int id = branch.getItems()[0];
			if (id == ItemID.COINS)
			{
				return String.format(Locale.ROOT, "%,d coins", branch.getQuantity());
			}
			final String name = itemName.apply(id);
			return branch.getQuantity() > 1 ? branch.getQuantity() + " x " + name : name;
		}

		/** The hover: the way's full name, what it needs, its charges or uses left, and why it can't be used. */
		private String detail(String key, TravelAtlas.Jump first, Stand stand, boolean off)
		{
			final StringBuilder s = new StringBuilder(AtlasWays.label(key)).append('.');
			final List<String> needs = new ArrayList<>();
			for (int id : stand.row.getNeeds())
			{
				final AtlasNeed need = atlas.need(id);
				if (need.getKind() == AtlasNeed.Kind.SKILL || need.getKind() == AtlasNeed.Kind.QUEST)
				{
					needs.add(need.getName());
				}
			}
			if (!needs.isEmpty())
			{
				s.append(" Needs ").append(join(needs)).append('.');
			}
			switch (stand.state)
			{
				case READY:
					s.append(left(first));
					break;
				case NEEDS:
					// Said even when it's among the needs above: it's what greys the way out
					s.append(" Missing: ").append(stand.needs).append('.');
					break;
				case COOLING_DOWN:
					s.append(" Used in the last ").append(first.getType().equals("TELEPORTATION_MINIGAME") ? 20 : 30)
						.append(" minutes: ready once its cooldown is over.");
					break;
				case NONE_TODAY:
					s.append(" No uses left today.");
					break;
				case LAST_CHARGE_KEPT:
					s.append(" Its last charge is kept back (Keep the last charge of my jewellery).");
					break;
				case NO_CHARGES:
					s.append(" No charges left.");
					break;
				case WILDERNESS:
					s.append(" It's in the Wilderness, which the route keeps out of (Avoid the Wilderness).");
					break;
				default:
					break;
			}
			if (off)
			{
				s.append(" Turned off: the route doesn't use it.");
			}
			return s.toString();
		}

		/** " 5 charges left." or " 2 uses left today." for one of the plugin's limited items; empty otherwise. */
		private String left(TravelAtlas.Jump t)
		{
			if (t.getFrom() >= 0)
			{
				return "";
			}
			final TravelMethod daily = AtlasTravel.daily(atlas, t);
			final Integer today = daily == null ? null : ChargeBudget.leftToday(daily, holdings);
			if (today != null)
			{
				return " " + today + (today == 1 ? " use" : " uses") + " left today.";
			}
			for (TravelItem item : AtlasLinks.items(atlas, t))
			{
				final Integer charges = holdings.countAny(item.getItemIds()) > 0 ? ChargeBudget.held(item, holdings) : null;
				if (charges != null)
				{
					return " " + charges + (charges == 1 ? " charge" : " charges") + " left.";
				}
			}
			return "";
		}

		private static String join(List<String> parts)
		{
			if (parts.size() == 1)
			{
				return parts.get(0);
			}
			return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
		}
	}

	/** How one of the atlas's rows stands, and the row. */
	private static final class Stand
	{
		final State state;
		final String needs;
		final TravelAtlas.Jump row;

		Stand(State state, String needs, TravelAtlas.Jump row)
		{
			this.state = state;
			this.needs = needs;
			this.row = row;
		}
	}
}
