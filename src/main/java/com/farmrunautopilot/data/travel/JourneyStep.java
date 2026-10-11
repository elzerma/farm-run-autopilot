package com.farmrunautopilot.data.travel;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * One part of a journey after the teleport lands and before the walk to the patch: a boat, a quetzal, an NPC
 * who leads the way, or a walk to where that starts. The run guide shows them one at a time.
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
	/** Into the Troll Stronghold and up to the roof patch (My Arm's Big Adventure). */
	public static final JourneyStep STRONGHOLD_ROOF = way("Climb the Rocks beside the entrance up to the roof "
		+ "(73 Agility); without it, Enter the stronghold, go through the door in the west wall and Climb-up the "
		+ "ladder to the roof", "up to the roof");

	Kind kind;
	/** The step in the guide, e.g. "Pay-fare to Captain Barnaby at the Ardougne docks (30 coins)". */
	String text;
	/** Short, for the route list after the teleport's name, e.g. "boat from the Ardougne docks". */
	String brief;
	/** An NPC to outline (and walk to), matched by name; null if none. */
	String npc;
	/** Where the step happens, for the walking line; null to use the NPC's position once it's loaded. */
	WorldPoint at;

	public static JourneyStep ride(String npc, String text, String brief, WorldPoint at)
	{
		return new JourneyStep(Kind.RIDE, text, brief, npc, at);
	}

	public static JourneyStep walk(String text, String brief, WorldPoint at)
	{
		return new JourneyStep(Kind.WALK, text, brief, null, at);
	}

	/** Directions for the way to the patch (shortcuts, gates, ladders): shown until the patch is reached. */
	public static JourneyStep way(String text, String brief)
	{
		return new JourneyStep(Kind.WALK, text, brief, null, null);
	}

	/** Leaving somewhere by a portal or ladder: done when the player is moved. */
	public static JourneyStep exit(String text, String brief, WorldPoint at)
	{
		return new JourneyStep(Kind.RIDE, text, brief, null, at);
	}
}
