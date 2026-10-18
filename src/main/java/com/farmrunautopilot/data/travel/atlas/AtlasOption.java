package com.farmrunautopilot.data.travel.atlas;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * One way of making a leg from the atlas (docs/plans/travel-rework.md, section 4): a walk from where the player is, a
 * teleport, the house, or a hop (fairy ring, spirit tree, quetzal...) reached on foot or after a teleport, then the
 * atlas's way to the patch. Its seconds come from the plugin's own timings for each kind of teleport and hop, plus
 * walking tiles and transport ticks; the leg's set-off overhead is added by the planner.
 */
@Value
public class AtlasOption
{
	public enum Kind
	{
		/** On foot (doors, stairs, shortcuts and boats on the way) from where the player is. */
		WALK,
		/** A teleport, then the way from where it lands. */
		TELEPORT,
		/** Teleport to House, then a portal, the jewellery box, the house's fairy ring or spirit tree. */
		HOUSE,
		/** On foot to a spirit tree, "Your house", then a way out of the house (issue #16). */
		TREE_HOUSE,
		/** On foot to a hop (a ring, a tree, a pad), the hop, then the way from where it lands. */
		HOP,
		/** A teleport, on foot to a hop, the hop, then the way from where it lands. */
		TELEPORT_HOP
	}

	Kind kind;
	/** The teleport, or null. */
	TravelAtlas.Jump teleport;
	/** The house's way out, or null. */
	AtlasHouse.Exit houseExit;
	/** The hop, or null. */
	TravelAtlas.Jump hop;
	/** The way to the hop's hub or the spirit tree into the house, or null. */
	TravelAtlas.Way toHub;
	/** The way to the patch. */
	TravelAtlas.Way way;
	/** Seconds, before the leg's set-off overhead; how they add up is AtlasLegs.breakdown's, for the log. */
	double seconds;

	/** Every way the option walks: to the hub (if any), then to the patch. */
	public List<TravelAtlas.Way> ways()
	{
		final List<TravelAtlas.Way> ways = new ArrayList<>();
		if (toHub != null)
		{
			ways.add(toHub);
		}
		ways.add(way);
		return ways;
	}

	/** On foot only: no teleport, house or hop. */
	public boolean isWalking()
	{
		return kind == Kind.WALK;
	}
}
