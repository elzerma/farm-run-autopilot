package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.TravelMethod;
import lombok.Value;

/**
 * One stop on the route and how the player gets there from the stop before.
 */
@Value
public class RouteStop
{
	Location location;
	/** Null when walking from the previous stop, or when no method is unlocked. */
	TravelMethod method;
	Departure departure;
	/** Estimated seconds for this leg. */
	double legSeconds;
	/** The player doesn't have what this method needs yet; the supply list asks for it. */
	boolean needsSupplies;
	/** How {@link #legSeconds} was worked out, for the log, e.g. "cast 3 + 56 tiles 17 + overhead 8". */
	String breakdown;

	public RouteStop(Location location, TravelMethod method, Departure departure, double legSeconds,
		boolean needsSupplies)
	{
		this(location, method, departure, legSeconds, needsSupplies, "");
	}

	public RouteStop(Location location, TravelMethod method, Departure departure, double legSeconds,
		boolean needsSupplies, String breakdown)
	{
		this.location = location;
		this.method = method;
		this.departure = departure;
		this.legSeconds = legSeconds;
		this.needsSupplies = needsSupplies;
		this.breakdown = breakdown;
	}

	/**
	 * e.g. "Spirit tree (Tree Gnome Village), then follow Elkoy out of the maze" or "House portal nexus: Catherby
	 * Teleport": the whole journey, so nobody is left guessing about a boat or an NPC.
	 */
	public String describeTravel()
	{
		final String travel = describeDeparture();
		if (method == null || method.getThen().isEmpty() || departure == Departure.WALK)
		{
			return travel;
		}
		final StringBuilder whole = new StringBuilder(travel);
		for (JourneyStep step : method.getThen())
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
			case POH_NEXUS:
				return "House portal nexus: " + method.getDisplayName();
			case POH_JEWELLERY_BOX:
				return "House jewellery box: " + method.getDisplayName();
			case POH_FAIRY_RING:
				return "House fairy ring: " + method.getDisplayName();
			case POH_SPIRIT_TREE:
				return "House spirit tree: " + method.getDisplayName();
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
