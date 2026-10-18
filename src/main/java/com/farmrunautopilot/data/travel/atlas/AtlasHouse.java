package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.settings.PohSetup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * The player's house as a way out (docs/plans/travel-rework.md, section 4): in by Teleport to House, or by "Your house"
 * on any spirit tree once one grows in the house (issue #16), then a nexus portal, the jewellery box, the house's fairy
 * ring or spirit tree, or (in by a tree) out through the front door; each lands at an atlas place. Built from the house
 * the player set up (PohSetup); the atlas's house rows don't know which portals a house has.
 */
public final class AtlasHouse
{
	private static final double SECONDS_PER_TICK = 0.6;
	/** Teleport to House: Shortest Path's 4 ticks. */
	private static final int HOUSE_TELEPORT_TICKS = 4;
	/** The jewellery box tier each item's teleports need. */
	private static final Map<TravelItem, JewelleryBoxTier> BOX_TIER = new EnumMap<>(TravelItem.class);

	static
	{
		BOX_TIER.put(TravelItem.GAMES_NECKLACE, JewelleryBoxTier.BASIC);
		BOX_TIER.put(TravelItem.COMBAT_BRACELET, JewelleryBoxTier.FANCY);
		BOX_TIER.put(TravelItem.SKILLS_NECKLACE, JewelleryBoxTier.FANCY);
		BOX_TIER.put(TravelItem.AMULET_OF_GLORY, JewelleryBoxTier.ORNATE);
		BOX_TIER.put(TravelItem.RING_OF_WEALTH, JewelleryBoxTier.ORNATE);
	}

	/** What a way out of the house is. */
	public enum ExitKind
	{
		/** Out of the front door to the house portal (only after coming in by a spirit tree). */
		PORTAL,
		NEXUS,
		JEWELLERY_BOX,
		FAIRY_RING,
		SPIRIT_TREE
	}

	/** One way out of the house: what it is, where it lands, the seconds to use it, and its name. */
	@Value
	public static class Exit
	{
		ExitKind kind;
		int landing;
		double seconds;
		String name;
	}

	/** Teleporting in and walking to the exits, in seconds; null if the player can't teleport in. */
	private final Double teleportIn;
	/** A spirit tree grows in the house, so any spirit tree goes there ("Your house"). */
	private final boolean treeIn;
	private final List<Exit> exits;
	/** Out of the front door to the house portal, or null if the house's place isn't known. */
	private final Exit portal;

	private AtlasHouse(Double teleportIn, boolean treeIn, List<Exit> exits, Exit portal)
	{
		this.teleportIn = teleportIn;
		this.treeIn = treeIn;
		this.exits = exits;
		this.portal = portal;
	}

	/** Seconds to teleport in and walk to the exits, or null if the player can't teleport in. */
	public Double getTeleportIn()
	{
		return teleportIn;
	}

	/** Whether every spirit tree goes to the house: one grows there. */
	public boolean isTreeIn()
	{
		return treeIn;
	}

	/** The nexus, jewellery box, fairy ring and spirit tree exits. */
	public List<Exit> getExits()
	{
		return Collections.unmodifiableList(exits);
	}

	/** Out of the front door to the house portal, or null. */
	public Exit getPortal()
	{
		return portal;
	}

	/**
	 * The house's ways out for this player, or null without a known house; whether they can teleport in comes from the
	 * atlas's Teleport to House rows (runes or tablet, and the house's location, read from the game), at Shortest
	 * Path's timing. Spirit trees into the house aren't counted. For the referee and the tests.
	 */
	public static AtlasHouse of(TravelAtlas atlas, AtlasAccount account, PohSetup poh)
	{
		if (poh == null || !canTeleportIn(atlas, account))
		{
			return null;
		}
		final double in = HOUSE_TELEPORT_TICKS * SECONDS_PER_TICK + (poh.isTeleportOutside() ? TravelTimes.HOUSE_DOOR : 0)
			+ TravelTimes.HOUSE_WALK;
		return of(atlas, account, poh, in, false);
	}

	/**
	 * The house's ways out for this player, or null without a known house or any way in.
	 *
	 * @param teleportIn seconds to teleport in and walk to the exits (the planner's own rules: a cape, the spell or a
	 *     tablet), or null if the player can't teleport in
	 * @param treeIn a spirit tree grows in the house and the player can use spirit trees
	 */
	public static AtlasHouse of(TravelAtlas atlas, AtlasAccount account, PohSetup poh, Double teleportIn, boolean treeIn)
	{
		if (poh == null || poh.getPortal() == null || (teleportIn == null && !treeIn))
		{
			return null;
		}
		final List<Exit> exits = new ArrayList<>();
		final Set<String> nexus = new HashSet<>();
		for (PortalNexus.Destination d : poh.getNexusDestinations())
		{
			nexus.add(d.getDisplayName() + " Portal");
		}
		// The jewellery box's rows land where the item's own teleport does: that item says which tier they need
		final Map<Integer, JewelleryBoxTier> boxTierAt = new HashMap<>();
		Exit portal = null;
		final String portalName = "Construction cape: " + poh.getPortal().getDisplayName();
		for (TravelAtlas.Jump t : atlas.getTeleports())
		{
			final TravelItem item = t.getType().equals("TELEPORTATION_ITEM") ? AtlasLinks.item(atlas, t) : null;
			if (item != null && BOX_TIER.containsKey(item))
			{
				boxTierAt.merge(t.getTo(), BOX_TIER.get(item), (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
			}
			// The construction cape's teleport to a house portal lands where walking out of the house does
			if (portal == null && t.getName().equals(portalName))
			{
				portal = new Exit(ExitKind.PORTAL, t.getTo(), 0, "out of the house");
			}
		}
		for (TravelAtlas.Jump t : atlas.getTeleports())
		{
			if (t.getType().equals("TELEPORTATION_PORTAL_POH") && nexus.contains(t.getName()))
			{
				exits.add(new Exit(ExitKind.NEXUS, t.getTo(), TravelTimes.TELEPORT, "nexus: " + t.getName()));
			}
			else if (t.getType().equals("TELEPORTATION_BOX") && poh.getJewelleryBox() != null)
			{
				final JewelleryBoxTier needed = boxTierAt.get(t.getTo());
				if (needed != null && poh.getJewelleryBox().includes(needed))
				{
					exits.add(new Exit(ExitKind.JEWELLERY_BOX, t.getTo(), TravelTimes.TELEPORT,
						"jewellery box: " + t.getName()));
				}
			}
		}
		// The house's ring or tree goes where any ring or tree does: one exit per place it lands
		final Set<Integer> hopLandings = new HashSet<>();
		for (TravelAtlas.Jump hop : atlas.getHops())
		{
			final boolean ring = hop.getType().equals("FAIRY_RING") && poh.isFairyRing();
			final boolean tree = hop.getType().equals("SPIRIT_TREE") && poh.isSpiritTree();
			if ((ring || tree) && account.meetsAll(atlas, hop.getNeeds()) && hopLandings.add(hop.getTo()))
			{
				exits.add(ring ? new Exit(ExitKind.FAIRY_RING, hop.getTo(), TravelTimes.FAIRY_RING,
					"house fairy ring: " + hop.getName())
					: new Exit(ExitKind.SPIRIT_TREE, hop.getTo(), TravelTimes.SPIRIT_TREE, "house spirit tree: " + hop.getName()));
			}
		}
		return new AtlasHouse(teleportIn, treeIn && poh.isSpiritTree(), exits, portal);
	}

	/**
	 * The player can cast Teleport to House or break its tablet: one of the atlas's rows for it has its needs met (the
	 * runes or tablet, and the house's location and teleport setting, read from the game).
	 */
	private static boolean canTeleportIn(TravelAtlas atlas, AtlasAccount account)
	{
		for (TravelAtlas.Jump t : atlas.getTeleports())
		{
			if (t.getName().startsWith("Teleport to House") && account.meetsAll(atlas, t.getNeeds()))
			{
				return true;
			}
		}
		return false;
	}
}
