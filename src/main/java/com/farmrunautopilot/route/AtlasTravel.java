package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.DailyLimits;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.AtlasHouse;
import com.farmrunautopilot.data.travel.atlas.AtlasLinks;
import com.farmrunautopilot.data.travel.atlas.AtlasNeed;
import com.farmrunautopilot.data.travel.atlas.AtlasOption;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.supply.Holdings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Turns the travel atlas's options into the planner's legs (docs/plans/travel-rework.md, sprint T3): one of the
 * plugin's own travel methods where one lands at the same place the same way (so its journey steps, charges, supplies
 * and highlights carry on), otherwise an {@link AtlasLeg} described from the atlas.
 */
final class AtlasTravel
{
	/** Transports on a way that move the player somewhere else (a boat, a portal): a journey step each. */
	private static final Set<String> RIDES = Set.of("SHIP", "BOAT", "TELEPORTATION_PORTAL", "TELEPORTATION_LEVER");
	/** The plugin's travel methods by the stop they go to. */
	private static final Map<Location, List<TravelMethod>> TO = new EnumMap<>(Location.class);
	/** A transport that moves the player further than this is a ride (the guide's "moved" is over 10 tiles a tick). */
	private static final int CARRIED_TILES = 10;
	/** A ring's hub (where its hops leave from) this close to where its own code lands is that ring. */
	private static final int RING_TILES = 8;
	/** Where each fairy ring code lands, by code ("AJR"), found once. */
	private static final Map<String, WorldPoint> RINGS = new ConcurrentHashMap<>();

	static
	{
		for (TravelMethod m : TravelMethod.values())
		{
			TO.computeIfAbsent(m.getDestination(), k -> new ArrayList<>()).add(m);
		}
	}

	private AtlasTravel()
	{
	}

	/** One of the plugin's travel methods and how it starts; no method for a walk or for {@link Departure#ATLAS}. */
	@Value
	static class Match
	{
		TravelMethod method;
		Departure departure;
	}

	/** The plugin's own travel method and departure this option is, or an atlas leg's. */
	static Match match(TravelAtlas atlas, AtlasOption option, Location to)
	{
		switch (option.getKind())
		{
			case WALK:
				return new Match(null, Departure.WALK);
			case TELEPORT:
			{
				final TravelAtlas.Jump t = option.getTeleport();
				final List<TravelItem> items = AtlasLinks.items(atlas, t);
				final Spell spell = AtlasLinks.spell(atlas, t);
				for (TravelMethod m : landingAt(atlas, t.getTo(), to))
				{
					final boolean same;
					switch (m.getKind())
					{
						case SPELL:
						case HOUSE_PORTAL:
							same = spell != null && spell == m.getSpell();
							break;
						case QUETZAL:
							same = t.getType().equals("QUETZAL_WHISTLE");
							break;
						case FAIRY_RING:
						case SPIRIT_TREE:
						case BALLOON:
							same = false;
							break;
						default:
							same = m.getItem() != null && items.contains(m.getItem());
							break;
					}
					if (same)
					{
						return new Match(m, Departure.DIRECT);
					}
				}
				break;
			}
			case HOUSE:
			case TREE_HOUSE:
			{
				final boolean tree = option.getKind() == AtlasOption.Kind.TREE_HOUSE;
				final AtlasHouse.ExitKind exit = option.getHouseExit().getKind();
				for (TravelMethod m : landingAt(atlas, option.getHouseExit().getLanding(), to))
				{
					if (exit == AtlasHouse.ExitKind.PORTAL && m.getKind() == TravelKind.HOUSE_PORTAL)
					{
						return new Match(m, Departure.TREE_HOUSE_PORTAL);
					}
					if (exit == AtlasHouse.ExitKind.NEXUS && m.getNexus() != null)
					{
						return new Match(m, tree ? Departure.TREE_HOUSE_NEXUS : Departure.POH_NEXUS);
					}
					if (exit == AtlasHouse.ExitKind.JEWELLERY_BOX && m.getJewelleryBox() != null)
					{
						return new Match(m, tree ? Departure.TREE_HOUSE_JEWELLERY_BOX : Departure.POH_JEWELLERY_BOX);
					}
					if (exit == AtlasHouse.ExitKind.FAIRY_RING && m.getKind() == TravelKind.FAIRY_RING)
					{
						return new Match(m, tree ? Departure.TREE_HOUSE_FAIRY_RING : Departure.POH_FAIRY_RING);
					}
					if (exit == AtlasHouse.ExitKind.SPIRIT_TREE && m.getKind() == TravelKind.SPIRIT_TREE && !tree)
					{
						return new Match(m, Departure.POH_SPIRIT_TREE);
					}
				}
				break;
			}
			case HOP:
			{
				final String type = option.getHop().getType();
				for (TravelMethod m : landingAt(atlas, option.getHop().getTo(), to))
				{
					if (type.equals("FAIRY_RING") && m.getKind() == TravelKind.FAIRY_RING)
					{
						return new Match(m, Departure.FAIRY_RING_NEARBY);
					}
					if (type.equals("SPIRIT_TREE") && m.getKind() == TravelKind.SPIRIT_TREE
						|| type.equals("HOT_AIR_BALLOON") && m.getKind() == TravelKind.BALLOON)
					{
						return new Match(m, Departure.DIRECT);
					}
					if (type.equals("QUETZAL") && m.getKind() == TravelKind.QUETZAL)
					{
						return new Match(m, Departure.RENU);
					}
				}
				break;
			}
			case TELEPORT_HOP:
			{
				// A teleport to a ring the plugin knows a way to (the Ardougne cloak to the Monastery's ring...)
				if (!option.getHop().getType().equals("FAIRY_RING"))
				{
					break;
				}
				final List<TravelItem> items = AtlasLinks.items(atlas, option.getTeleport());
				final WorldPoint hub = atlas.place(option.getHop().getFrom()).getPoint();
				for (FairyRingAccess way : FairyRingAccess.values())
				{
					final WorldPoint ring = way.getRing() == null ? null : ring(atlas, way.getRing());
					if (way.getItems().stream().noneMatch(items::contains) || ring == null || ring.getPlane() != hub.getPlane()
						|| ring.distanceTo2D(hub) > RING_TILES)
					{
						continue;
					}
					for (TravelMethod m : landingAt(atlas, option.getHop().getTo(), to))
					{
						if (m.getKind() == TravelKind.FAIRY_RING)
						{
							return new Match(m, Departure.of(way));
						}
					}
				}
				break;
			}
			default:
				break;
		}
		return new Match(null, Departure.ATLAS);
	}

	/** The travel methods to this stop that land where this atlas place is. */
	private static List<TravelMethod> landingAt(TravelAtlas atlas, int place, Location to)
	{
		final List<TravelMethod> methodsTo = TO.get(to);
		if (methodsTo == null)
		{
			return Collections.emptyList();
		}
		final WorldPoint at = atlas.place(place).getPoint();
		final List<TravelMethod> methods = new ArrayList<>();
		for (TravelMethod m : methodsTo)
		{
			if (AtlasLinks.sameLanding(at, AtlasLinks.landing(m)))
			{
				methods.add(m);
			}
		}
		return methods;
	}

	/** Where a fairy ring code (e.g. "AJR") lands, from the atlas's rings; null if it has none. */
	private static WorldPoint ring(TravelAtlas atlas, String code)
	{
		return RINGS.computeIfAbsent(code, c ->
		{
			for (TravelAtlas.Jump hop : atlas.getHops())
			{
				if (hop.getType().equals("FAIRY_RING") && hop.getName().replace(" ", "").equals(c))
				{
					return atlas.place(hop.getTo()).getPoint();
				}
			}
			return null;
		});
	}

	/** The daily-limited travel method this teleport is (the Ardougne cloak's farm teleport...), or null. */
	static TravelMethod daily(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		final WorldPoint at = atlas.place(teleport.getTo()).getPoint();
		final List<TravelItem> items = AtlasLinks.items(atlas, teleport);
		for (TravelMethod m : DailyLimits.usedTodayVarbits().keySet())
		{
			if (items.contains(m.getItem()) && AtlasLinks.sameLanding(at, AtlasLinks.landing(m)))
			{
				return m;
			}
		}
		return null;
	}

	/**
	 * The plugin's teleport item for this teleport that the player can use ({@code usable}), the first of them if none,
	 * or null if the teleport isn't one of the plugin's items.
	 */
	static TravelItem item(TravelAtlas atlas, TravelAtlas.Jump teleport, Predicate<TravelItem> usable)
	{
		final List<TravelItem> items = AtlasLinks.items(atlas, teleport);
		for (TravelItem item : items)
		{
			if (usable.test(item))
			{
				return item;
			}
		}
		return items.isEmpty() ? null : items.get(0);
	}

	/** The way described for the guide, the supply list and the highlights. */
	static AtlasLeg leg(TravelAtlas atlas, AtlasOption option, Holdings holdings,
		Predicate<TravelItem> usable)
	{
		final TravelAtlas.Jump teleport = option.getTeleport();
		final TravelAtlas.Jump hop = option.getHop();
		final AtlasHouse.Exit exit = option.getHouseExit();
		final TravelItem item = teleport == null ? null : item(atlas, teleport, usable);
		final Spell spell = teleport == null ? null : AtlasLinks.spell(atlas, teleport);
		final List<JourneyStep> rides = new ArrayList<>();
		final String way;
		WorldPoint start = null;
		final WorldPoint landing;
		switch (option.getKind())
		{
			case TELEPORT:
				way = teleportName(teleport);
				landing = point(atlas, teleport.getTo());
				break;
			case HOUSE:
				way = "House " + exitName(exit);
				landing = point(atlas, exit.getLanding());
				break;
			case TREE_HOUSE:
				way = "Spirit tree to your house, then " + (exit.getKind() == AtlasHouse.ExitKind.PORTAL
					? "out of the portal" : "the " + exitName(exit));
				start = point(atlas, option.getToHub().getTo());
				landing = point(atlas, exit.getLanding());
				break;
			case HOP:
				way = hopName(hop);
				start = point(atlas, hop.getFrom());
				landing = point(atlas, hop.getTo());
				break;
			case TELEPORT_HOP:
				way = teleportName(teleport);
				rides.addAll(rides(atlas, option.getToHub()));
				rides.add(hopStep(atlas, hop));
				landing = point(atlas, teleport.getTo());
				break;
			default:
				way = "Walk";
				landing = null;
				break;
		}
		rides.addAll(rides(atlas, option.getWay()));

		// Items the plugin doesn't plan itself: a teleport it doesn't track, fares and other things the way needs
		final List<AtlasLeg.ItemNeed> items = new ArrayList<>();
		if (teleport != null && spell == null)
		{
			final Set<Integer> tracked = new HashSet<>();
			for (TravelItem linked : AtlasLinks.items(atlas, teleport))
			{
				for (int id : linked.getItemIds())
				{
					tracked.add(id);
				}
			}
			// A spell the plugin doesn't know uses up its runes each cast; a tablet or other one-use teleport is used up too
			addItems(atlas, teleport.getNeeds(), tracked, holdings,
				teleport.getType().equals("TELEPORTATION_SPELL") || teleport.isConsumable() && item == null, true, items);
		}
		if (hop != null)
		{
			addItems(atlas, hop.getNeeds(), Collections.emptySet(), holdings, false, false, items);
		}
		for (TravelAtlas.Way w : option.ways())
		{
			addItems(atlas, w.getNeeds(), Collections.emptySet(), holdings, false, false, items);
		}
		final boolean fairyRing = hop != null && hop.getType().equals("FAIRY_RING")
			|| exit != null && exit.getKind() == AtlasHouse.ExitKind.FAIRY_RING;
		final int balloons = hop != null && hop.getType().equals("HOT_AIR_BALLOON") ? 1 : 0;
		return new AtlasLeg(way, landing, start, item, spell, option.getKind() == AtlasOption.Kind.HOUSE,
			option.getKind() == AtlasOption.Kind.TREE_HOUSE, fairyRing, balloons, items, rides,
			teleport == null ? null : daily(atlas, teleport), teleport != null && teleport.isConsumable() && item == null
			&& spell == null, picks(teleport, hop), option(teleport));
	}

	/**
	 * The option or list entry a teleport item needs, from its name: "Skills necklace: Crafting Guild" is "Crafting Guild".
	 * None for spells (cast from the spellbook) or an item with one teleport.
	 */
	private static String option(TravelAtlas.Jump teleport)
	{
		if (teleport == null || teleport.getType().startsWith("TELEPORTATION_SPELL"))
		{
			return null;
		}
		final String name = teleportName(teleport);
		final int colon = name.indexOf(": ");
		return colon < 0 ? null : name.substring(colon + 2);
	}

	/** Where to pick on the whistle's map (the teleport), or Renu's or a balloon's (the hop). */
	private static List<MenuPick> picks(TravelAtlas.Jump teleport, TravelAtlas.Jump hop)
	{
		final List<MenuPick> picks = new ArrayList<>();
		if (teleport != null && teleport.getType().equals("QUETZAL_WHISTLE"))
		{
			// "Quetzal whistle: Kastori"
			final String name = teleport.getName();
			picks.add(new MenuPick(MenuPick.Menu.WHISTLE, name.substring(name.indexOf(": ") + 2)));
		}
		if (hop != null && hop.getType().equals("QUETZAL"))
		{
			picks.add(new MenuPick(MenuPick.Menu.RENU, destination(hop)));
		}
		if (hop != null && hop.getType().equals("HOT_AIR_BALLOON"))
		{
			picks.add(new MenuPick(MenuPick.Menu.BALLOON, destination(hop)));
		}
		return picks;
	}

	/**
	 * The item needs on a teleport, hop or way, by the branch the player holds (or the first), leaving out what the
	 * plugin tracks itself and staves that cover runes.
	 */
	private static void addItems(TravelAtlas atlas, int[] needs, Set<Integer> tracked, Holdings holdings, boolean usedUp,
		boolean teleport, List<AtlasLeg.ItemNeed> items)
	{
		for (int id : needs)
		{
			final AtlasNeed need = atlas.need(id);
			if (need.getKind() != AtlasNeed.Kind.ITEMS)
			{
				continue;
			}
			AtlasNeed.Branch chosen = null;
			boolean planned = false;
			for (AtlasNeed.Branch branch : need.getBranches())
			{
				// An unlock instead (a diary), the plugin's own item, or a staff or off-hand the player has that covers it
				if (branch.getUnlock() != null || anyIn(branch.getItems(), tracked) || holdings.countAny(branch.getCovers()) > 0)
				{
					planned = true;
					break;
				}
				if (holdings.countAny(branch.getItems()) >= branch.getQuantity())
				{
					chosen = branch;
					break;
				}
				chosen = chosen == null ? branch : chosen;
			}
			if (!planned && chosen != null && chosen.getItems().length > 0)
			{
				final boolean coins = chosen.getItems().length == 1 && chosen.getItems()[0] == ItemID.COINS;
				items.add(new AtlasLeg.ItemNeed(chosen.getItems(), chosen.getQuantity(), usedUp || coins, teleport));
			}
		}
	}

	private static boolean anyIn(int[] ids, Set<Integer> set)
	{
		for (int id : ids)
		{
			if (set.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Boats, portals and levers on a way, and anything else that carries the player off (an NPC who leads the way, a
	 * cart): each a step, done when the player is moved. Doors, stairs and shortcuts are part of the walk.
	 */
	private static List<JourneyStep> rides(TravelAtlas atlas, TravelAtlas.Way way)
	{
		final List<JourneyStep> rides = new ArrayList<>();
		for (int id : way.getSteps())
		{
			final TravelAtlas.Step step = atlas.step(id);
			if (RIDES.contains(step.getType()))
			{
				final String what = step.getType().equals("TELEPORTATION_PORTAL") ? "portal"
					: step.getType().equals("TELEPORTATION_LEVER") ? "lever" : "boat";
				rides.add(JourneyStep.ride(TravelAtlas.target(step.getMenu()), "Take the " + what + ": " + step.getName(),
					what + ": " + step.getName(), step.getOrigin()));
			}
			else if (step.getType().equals("TRANSPORT") && step.getOrigin().getPlane() == step.getDestination().getPlane()
				&& step.getOrigin().distanceTo2D(step.getDestination()) > CARRIED_TILES)
			{
				// e.g. "Follow Elkoy", "Transport Brother Tranquility": the menu option and who to use it on
				final String name = step.getName();
				rides.add(JourneyStep.ride(TravelAtlas.target(step.getMenu()), name,
					Character.toLowerCase(name.charAt(0)) + name.substring(1), step.getOrigin()));
			}
		}
		return rides;
	}

	/** Walking to a hop and taking it, e.g. "Take the gnome glider to Ta Quir Priw". */
	private static JourneyStep hopStep(TravelAtlas atlas, TravelAtlas.Jump hop)
	{
		final String to = destination(hop);
		final String brief;
		final String text;
		switch (hop.getType())
		{
			case "FAIRY_RING":
				brief = "fairy ring " + to.replace(" ", "");
				text = "Use the fairy ring: " + to.replace(" ", "");
				break;
			case "QUETZAL":
				brief = "Renu to " + to;
				text = "Take Renu to " + to;
				break;
			default:
				brief = hopKind(hop) + " to " + to;
				text = "Take the " + brief;
				break;
		}
		// Who to use it on, outlined when they're near (Renu, a ship's crew); a ring or a tree isn't found among NPCs
		return JourneyStep.ride(TravelAtlas.target(hop.getMenu()), text, brief, point(atlas, hop.getFrom()));
	}

	/** e.g. "spirit tree", "gnome glider", "charter ship". */
	private static String hopKind(TravelAtlas.Jump hop)
	{
		return hop.getType().toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	/** Where a hop goes, without a spirit tree's menu number ("1: Tree Gnome Village"). */
	private static String destination(TravelAtlas.Jump hop)
	{
		final String to = hop.getName();
		final int colon = to.indexOf(": ");
		return colon >= 0 && colon < 3 ? to.substring(colon + 2) : to;
	}

	/** e.g. "Fairy ring CKR", "Spirit tree (Tree Gnome Village)", "Gnome glider (Ta Quir Priw)". */
	static String hopName(TravelAtlas.Jump hop)
	{
		final String to = destination(hop);
		switch (hop.getType())
		{
			case "FAIRY_RING":
				return "Fairy ring " + to.replace(" ", "");
			case "SPIRIT_TREE":
				return "Spirit tree (" + to + ")";
			case "QUETZAL":
				return "Renu (" + to + ")";
			case "HOT_AIR_BALLOON":
				return "Hot air balloon (" + to + ")";
			case "GNOME_GLIDER":
				return "Gnome glider (" + to + ")";
			case "CHARTER_SHIP":
				return "Charter ship (" + to + ")";
			case "CANOE":
				return "Canoe (" + to + ")";
			case "MINECART":
				return "Minecart (" + to + ")";
			case "MAGIC_CARPET":
				return "Magic carpet (" + to + ")";
			case "MAGIC_MUSHTREE":
				return "Magic mushtree (" + to + ")";
			default:
				return to;
		}
	}

	/** e.g. "portal nexus: Kourend Castle", "jewellery box: Ranging Guild", "fairy ring CKR". */
	private static String exitName(AtlasHouse.Exit exit)
	{
		final String name = exit.getName().substring(exit.getName().indexOf(": ") + 2);
		switch (exit.getKind())
		{
			case NEXUS:
				return "portal nexus: " + (name.endsWith(" Portal") ? name.substring(0, name.length() - " Portal".length())
					: name);
			case JEWELLERY_BOX:
				return "jewellery box: " + name;
			case FAIRY_RING:
				return "fairy ring " + name.replace(" ", "");
			case SPIRIT_TREE:
				return "spirit tree: " + name.substring(name.indexOf(": ") + 2);
			default:
				return name;
		}
	}

	/** The teleport's name without Shortest Path's "(Outside)" for the house setting. */
	private static String teleportName(TravelAtlas.Jump teleport)
	{
		return teleport.getName().replace(" (Outside)", "");
	}

	private static WorldPoint point(TravelAtlas atlas, int place)
	{
		return atlas.place(place).getPoint();
	}
}
