package com.farmrunautopilot.data.travel;

/**
 * The plugin's own timing for each kind of travel, in seconds (docs/plans/travel-rework.md, section 5): casting,
 * rubbing, a fairy ring's dial, a quetzal's flight, the house, and setting off on each leg, set from elzerma's
 * dev-client logs. Where every way goes and how far it walks is the travel atlas's (data/travel/atlas); the
 * hand-measured tables of walks that were here went in sprint T3.
 */
public final class TravelTimes
{
	/** Running speed: 2 tiles a game tick. */
	public static final double SECONDS_PER_TILE = 0.3;
	/** Casting a standard teleport, rubbing jewellery, a fairy ring dial, a spirit tree, a quetzal: seconds. */
	public static final double TELEPORT = 3.0;
	public static final double ITEM_TELEPORT = 3.6;
	public static final double HOME_TELEPORT = 10.5;
	/**
	 * Renu's flight from a landing pad (no whistle charge). elzerma's dev-client run of 2026-10-18 took 6, 14 and 10 s from
	 * the pad to landing: too few and too spread to change it yet (at 10 the whistle takes over Varlamore's chains).
	 */
	public static final double QUETZAL = 6.0;
	/**
	 * Blowing the quetzal whistle, waiting for the quetzal and flying: slower than a teleport. elzerma's recorded whistle
	 * leg to the Hunter Guild (2026-10-11) had about 21 s on top of the walk, overhead 8 included.
	 */
	public static final double QUETZAL_WHISTLE = 13.0;
	/**
	 * Dialling a fairy ring and the teleport: elzerma's dev-client run of 2026-10-18 took about 15 s and 20 s from the ring
	 * to landing (AJR to CKQ, CKQ to CIQ); the 4 s it was had their CIR leg of 2026-10-15 38 s over its estimate.
	 */
	public static final double FAIRY_RING = 15.0;
	public static final double SPIRIT_TREE = 4.5;
	/**
	 * A hot air balloon flight: the basket, the log, the map and the flight. elzerma's dev-client run of 2026-10-18 took
	 * about 26 s from the Crafting Guild's basket to landing in Taverley (Shortest Path's 5 ticks are the take-off).
	 */
	public static final double BALLOON = 26.0;
	/** A charter ship: the crew's map and the crossing. elzerma's run of 2026-10-18: about 9 s (the Pandemonium to Brimhaven). */
	public static final double CHARTER_SHIP = 9.0;
	/**
	 * Other hops with a map or menu to pick from (gnome gliders, minecarts, magic carpets, mushtrees, canoes): at least
	 * this, until elzerma's logs time them; Shortest Path's ticks are only the animation.
	 */
	public static final double OTHER_HOP = 8.0;
	/** Walking out of the house to its portal, or into it from outside. */
	public static final double HOUSE_DOOR = 5.0;
	/** Walking from the house's arrival point to the nexus, jewellery box, fairy ring or spirit tree. */
	public static final double HOUSE_WALK = 2.0;
	/**
	 * Every leg but a walk between neighbouring stops: finishing the last patch, opening the menu or spellbook,
	 * the animation and the load. elzerma's recorded legs (2026-10-10) ran 1-1.3x the estimates with it.
	 */
	public static final double LEG_OVERHEAD = 8;
	/** A walk between neighbouring stops still means finishing the last patch and setting off. */
	public static final double WALK_OVERHEAD = 3;

	private TravelTimes()
	{
	}
}
