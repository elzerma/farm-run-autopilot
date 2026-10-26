package com.farmrunautopilot.data.travel;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * The "Select an option" line a ride needs, for rides whose right-click opens a list of places: the atlas has the
 * right-click ("Travel Rowboat") but not the line (elzerma, 2026-10-25).
 */
public final class RideDialogs
{
	/** A landing within this many tiles of a line's counts as it. */
	private static final int NEAR = 5;

	private static final class Line
	{
		final int id;
		final WorldPoint to;
		final String text;

		Line(int id, WorldPoint to, String text)
		{
			this.id = id;
			this.to = to;
			this.text = text;
		}
	}

	/** Fossil Island's rowboats, as their lists word them (elzerma's screenshots, 2026-10-25). */
	private static final List<Line> LINES = List.of(
		new Line(ObjectID.FOSSIL_ROWBOAT_CAMP, new WorldPoint(3734, 3893, 0), "Row to the north of the island"),
		new Line(ObjectID.FOSSIL_ROWBOAT_CAMP, new WorldPoint(3764, 3899, 0), "Row out to sea, north of the island"),
		new Line(ObjectID.FOSSIL_ROWBOAT_CAMP, new WorldPoint(3362, 3445, 0), "Row to the barge and travel to the Digsite"),
		// Diving with a weapon or shield on asks "Your hands are full."; walking keeps them on, and the seaweed is
		// reached on foot from the anchor rope (elzerma's screenshot and choice, 2026-10-25). Not asked with empty hands.
		new Line(ObjectID.FOSSIL_ROWBOAT_DIVING, new WorldPoint(3732, 10281, 1), "Dive and walk on the seabed"));

	private RideDialogs()
	{
	}

	/** The line to pick for a ride on this object or NPC landing here, or null if its option goes straight there. */
	public static String line(int id, WorldPoint to)
	{
		if (to == null)
		{
			return null;
		}
		for (Line line : LINES)
		{
			if (line.id == id && line.to.getPlane() == to.getPlane() && line.to.distanceTo2D(to) <= NEAR)
			{
				return line.text;
			}
		}
		return null;
	}
}
