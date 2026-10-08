package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
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

	/** e.g. "Spirit tree (Tree Gnome Village)" or "POH nexus: Catherby Teleport". */
	public String describeTravel()
	{
		switch (departure)
		{
			case WALK:
				return "Walk";
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
			default:
				return method.getDisplayName();
		}
	}
}
