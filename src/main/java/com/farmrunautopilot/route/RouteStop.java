package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * One stop on the route and how the player gets there from the stop before.
 */
@Value
public class RouteStop
{
	Location location;
	/** Null when walking from the previous stop, for a way only the travel atlas knows, or when nothing is unlocked. */
	TravelMethod method;
	Departure departure;
	/** Estimated seconds for this leg. */
	double legSeconds;
	/** The player doesn't have what this method needs yet; the supply list asks for it. */
	boolean needsSupplies;
	/** How {@link #legSeconds} was worked out, for the log, e.g. "cast 3 + 56 tiles 17 + overhead 8". */
	String breakdown;
	/** The way, for {@link Departure#ATLAS}; otherwise null. */
	AtlasLeg atlas;

	public RouteStop(Location location, TravelMethod method, Departure departure, double legSeconds,
		boolean needsSupplies)
	{
		this(location, method, departure, legSeconds, needsSupplies, "");
	}

	public RouteStop(Location location, TravelMethod method, Departure departure, double legSeconds,
		boolean needsSupplies, String breakdown)
	{
		this(location, method, departure, legSeconds, needsSupplies, breakdown, null);
	}

	public RouteStop(Location location, TravelMethod method, Departure departure, double legSeconds,
		boolean needsSupplies, String breakdown, AtlasLeg atlas)
	{
		this.location = location;
		this.method = method;
		this.departure = departure;
		this.legSeconds = legSeconds;
		this.needsSupplies = needsSupplies;
		this.breakdown = breakdown;
		this.atlas = atlas;
	}

	/**
	 * e.g. "Spirit tree (Tree Gnome Village), then follow Elkoy out of the maze" or "House portal nexus: Catherby
	 * Teleport": the whole journey, so nobody is left guessing about a boat or an NPC.
	 */
	public String describeTravel()
	{
		final String travel = describeDeparture();
		final List<JourneyStep> then = departure == Departure.WALK ? Collections.emptyList()
			: atlas != null ? atlas.getRides() : method != null ? method.getThen() : Collections.emptyList();
		if (then.isEmpty())
		{
			return travel;
		}
		final StringBuilder whole = new StringBuilder(travel);
		for (JourneyStep step : then)
		{
			whole.append(", then ").append(step.getBrief());
		}
		return whole.toString();
	}

	/** The way there without the journey steps after it, e.g. "Trollheim Teleport". */
	public String describeWay()
	{
		return describeDeparture();
	}

	/** The steps after the way there that move the player (a boat, Elkoy, a ladder out, a hop): each a tick box. */
	public List<JourneyStep> rides()
	{
		if (departure == Departure.WALK)
		{
			return Collections.emptyList();
		}
		return atlas != null ? atlas.getRides() : method != null ? method.getRides() : Collections.emptyList();
	}

	/**
	 * Where to pick on Renu's, the quetzal whistle's or a hot air balloon's map on the way here; empty if none. The
	 * plugin's own quetzal methods name their pad in brackets, e.g. "Quetzal whistle (Kastori)".
	 */
	public List<MenuPick> menuPicks()
	{
		if (atlas != null)
		{
			return atlas.getPicks();
		}
		if (method == null || departure == Departure.WALK)
		{
			return Collections.emptyList();
		}
		switch (method.getKind())
		{
			case QUETZAL:
			{
				final String name = method.getDisplayName();
				final String pad = name.substring(name.indexOf('(') + 1, name.indexOf(')'));
				if (departure == Departure.RENU || departure == Departure.DIRECT)
				{
					return Collections.singletonList(new MenuPick(departure == Departure.RENU ? MenuPick.Menu.RENU
						: MenuPick.Menu.WHISTLE, pad));
				}
				return Collections.emptyList();
			}
			case BALLOON:
				return Collections.singletonList(new MenuPick(MenuPick.Menu.BALLOON, location.getDisplayName()));
			default:
				// The Civitas illa Fortis Teleport then Renu to the Hunter Guild, its only quetzal
				return method == TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT
					? Collections.singletonList(new MenuPick(MenuPick.Menu.RENU, "Hunter Guild")) : Collections.emptyList();
		}
	}

	/**
	 * The teleport item's option or list entry to pick on the way here, e.g. "Farming Guild" for a skills necklace, as
	 * the plugin's own methods name it in brackets ("Skills necklace (Farming Guild)", "Slayer ring (Fremennik Slayer
	 * Dungeon), ring AJR"); null if the leg needs none.
	 */
	public String teleportOption()
	{
		if (atlas != null)
		{
			return atlas.getOption();
		}
		final String name;
		if (departure == Departure.DIRECT && method != null && method.getItem() != null)
		{
			name = method.getDisplayName();
		}
		else if (departure.getFairyRingAccess() != null && !departure.getFairyRingAccess().getItems().isEmpty())
		{
			name = departure.getFairyRingAccess().getDisplayName();
		}
		else
		{
			return null;
		}
		final int open = name.indexOf('(');
		final int close = name.indexOf(')', open + 1);
		return open < 0 || close < 0 ? null : name.substring(open + 1, close);
	}

	/** The teleport item this leg uses (the method's, or the atlas leg's), or null. */
	public TravelItem travelItem()
	{
		return atlas != null ? atlas.getItem() : method != null ? method.getItem() : null;
	}

	/** The spell this leg casts directly (or its tablet), or null; Teleport to House for the house isn't counted. */
	public Spell spell()
	{
		return atlas != null ? atlas.getSpell() : method != null && departure == Departure.DIRECT ? method.getSpell() : null;
	}

	private String describeDeparture()
	{
		switch (departure)
		{
			case WALK:
				return "Walk";
			case RENU:
				// The guide adds " to Kastori", so the place isn't named here too
				return "Back to the landing pad, then Renu";
			case NONE:
				return "No unlocked way here";
			case ATLAS:
				return atlas.getWay();
			case POH_NEXUS:
				return "House portal nexus: " + method.getDisplayName();
			case POH_JEWELLERY_BOX:
				return "House jewellery box: " + method.getDisplayName();
			case POH_FAIRY_RING:
				return "House fairy ring: " + method.getDisplayName();
			case POH_SPIRIT_TREE:
				return "House spirit tree: " + method.getDisplayName();
			case TREE_HOUSE_PORTAL:
				return "Spirit tree to your house, then out of the portal";
			case TREE_HOUSE_NEXUS:
				return "Spirit tree to your house, then the portal nexus: " + method.getDisplayName();
			case TREE_HOUSE_JEWELLERY_BOX:
				return "Spirit tree to your house, then the jewellery box: " + method.getDisplayName();
			case TREE_HOUSE_FAIRY_RING:
				return "Spirit tree to your house, then the fairy ring: " + method.getDisplayName();
			case FAIRY_RING_NEARBY:
				return "Walk to the fairy ring here: " + method.getDisplayName();
			case FAIRY_RING_ARDOUGNE_CLOAK:
			case FAIRY_RING_SLAYER_RING:
			case FAIRY_RING_QUEST_CAPE:
				return method.getDisplayName() + " (" + departure.getFairyRingAccess().getVia() + ")";
			default:
				return method.getDisplayName();
		}
	}
}
