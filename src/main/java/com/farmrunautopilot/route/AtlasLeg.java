package com.farmrunautopilot.route;

import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * A leg the travel atlas found that isn't one of the plugin's own travel methods (docs/plans/travel-rework.md, sprint
 * T3): any teleport, hop or way out of the house the player can use. Everything the guide, the supply list and the
 * highlights need, worked out when it was planned, so none of them reads the atlas.
 */
@Value
public class AtlasLeg
{
	/** Something to carry for the way: items the plugin doesn't track (a teleport, a fare, a log), by any of its ids. */
	@Value
	public static class ItemNeed
	{
		int[] ids;
		int quantity;
		/** Used up on the way (coins, a tablet), not just carried. */
		boolean usedUp;
		/** The item that teleports (outlined while travelling), not something the way needs. */
		boolean teleport;
	}

	/** The way there, e.g. "Ectophial" or "Varrock Teleport, then the spirit tree to the Gnome Stronghold". */
	String way;
	/** Where the teleport, hop or house exit lands; null for a walk. */
	WorldPoint landing;
	/** Where to walk first (a hop or a spirit tree) before teleporting or riding anything; null if nothing. */
	WorldPoint start;
	/** The plugin's teleport item it uses, or null. */
	TravelItem item;
	/** The plugin's spell it casts (or that spell's tablet), or null. */
	Spell spell;
	/** Teleport to House first, then the house's exit. */
	boolean viaHouse;
	/** Into the house by a spirit tree, then the house's exit. */
	boolean byTree;
	/** A fairy ring on the way (a staff unless the Lumbridge & Draynor elite diary is done). */
	boolean fairyRing;
	/** Hot air balloon flights on the way, a normal log each. */
	int balloons;
	/** Other things to carry. */
	List<ItemNeed> items;
	/** The steps after the way there, in order: a hop after the teleport, the house's exit, a boat on the way. */
	List<JourneyStep> rides;
	/** The daily-limited teleport it uses (counted with that method's uses), or null. */
	TravelMethod daily;
	/** A teleport used up or limited only by its own item (a tablet the plugin doesn't track, say). */
	boolean consumable;
	/** Where to pick on Renu's, the whistle's or a balloon's map on the way. */
	List<MenuPick> picks;
	/** The teleport item's option or list entry to pick, e.g. "Crafting Guild" for a skills necklace; null if none. */
	String option;
}
