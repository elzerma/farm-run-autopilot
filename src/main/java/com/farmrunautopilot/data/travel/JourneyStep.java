package com.farmrunautopilot.data.travel;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * One part of a journey after the teleport lands and before the walk to the patch: a boat, a quetzal, an NPC
 * who leads the way, or a walk to where that starts. The run guide shows them one at a time. Written from the travel
 * atlas's steps (docs/plans/travel-rework.md, sprint T5), but for leaving the house.
 */
@Value
public class JourneyStep
{
	public enum Kind
	{
		/** A boat, ride or escort: done when the player is moved (a jump in position). */
		RIDE,
		/** A walk somewhere: done on getting close to {@link #at}. */
		WALK
	}

	/** Teleport to House lands inside the house: out through the exit portal first. */
	public static final JourneyStep LEAVE_HOUSE = exit("Leave your house through the exit portal",
		"out of your house", null);

	Kind kind;
	/** The step in the guide, e.g. "Captain Barnaby: Brimhaven (30 coins)". */
	String text;
	/** Short, for the route list after the teleport's name, e.g. "Captain Barnaby to Brimhaven". */
	String brief;
	/** An NPC to outline (and walk to), matched by name; null if none. */
	String npc;
	/** Where the step happens, for the walking line; null to use the NPC's position once it's loaded. */
	WorldPoint at;
	/** The NPC's or object's id from the atlas, for outlining an object (a portal, a ladder) near {@link #at}; -1 if none. */
	int id;

	public static JourneyStep ride(String npc, String text, String brief, WorldPoint at)
	{
		return ride(npc, -1, text, brief, at);
	}

	/** A ride on an NPC or object the atlas names by id. */
	public static JourneyStep ride(String npc, int id, String text, String brief, WorldPoint at)
	{
		return new JourneyStep(Kind.RIDE, text, brief, npc, at, id);
	}

	public static JourneyStep walk(String text, String brief, WorldPoint at)
	{
		return new JourneyStep(Kind.WALK, text, brief, null, at, -1);
	}

	/** Directions for the way to the patch (shortcuts, ladders, stairs): shown until the patch is reached. */
	public static JourneyStep way(String text, String brief)
	{
		return new JourneyStep(Kind.WALK, text, brief, null, null, -1);
	}

	/** Leaving somewhere by a portal or ladder: done when the player is moved. */
	public static JourneyStep exit(String text, String brief, WorldPoint at)
	{
		return new JourneyStep(Kind.RIDE, text, brief, null, at, -1);
	}
}
