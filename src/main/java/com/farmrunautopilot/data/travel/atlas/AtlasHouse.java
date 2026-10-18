package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.settings.PohSetup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * The player's house as a way out (docs/plans/travel-rework.md, section 4): Teleport to House, then a nexus portal,
 * the jewellery box, the house's fairy ring or spirit tree, each landing at an atlas place. Built from the house the
 * player set up (PohSetup); the atlas's house rows don't know which portals a house has.
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

	/** One way out of the house: where it lands, its ticks, and what it is. */
	@Value
	public static class Exit
	{
		int landing;
		int ticks;
		String name;
	}

	/** Teleporting in and walking to the exit, in seconds. */
	private final double seconds;
	private final List<Exit> exits;

	private AtlasHouse(double seconds, List<Exit> exits)
	{
		this.seconds = seconds;
		this.exits = exits;
	}

	public double getSeconds()
	{
		return seconds;
	}

	public List<Exit> getExits()
	{
		return Collections.unmodifiableList(exits);
	}

	/** The house's ways out for this player, or null without a known house. */
	public static AtlasHouse of(TravelAtlas atlas, AtlasAccount account, PohSetup poh)
	{
		if (poh == null || poh.getPortal() == null || !canTeleportIn(atlas, account))
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
		final Map<Integer, JewelleryBoxTier> boxTierAt = new java.util.HashMap<>();
		for (TravelAtlas.Jump t : atlas.getTeleports())
		{
			final TravelItem item = t.getType().equals("TELEPORTATION_ITEM") ? AtlasLinks.item(atlas, t) : null;
			if (item != null && BOX_TIER.containsKey(item))
			{
				boxTierAt.merge(t.getTo(), BOX_TIER.get(item), (a, b) -> a.ordinal() <= b.ordinal() ? a : b);
			}
		}
		for (TravelAtlas.Jump t : atlas.getTeleports())
		{
			if (t.getType().equals("TELEPORTATION_PORTAL_POH") && nexus.contains(t.getName()))
			{
				exits.add(new Exit(t.getTo(), t.getTicks(), "nexus: " + t.getName()));
			}
			else if (t.getType().equals("TELEPORTATION_BOX") && poh.getJewelleryBox() != null)
			{
				final JewelleryBoxTier needed = boxTierAt.get(t.getTo());
				if (needed != null && poh.getJewelleryBox().includes(needed))
				{
					exits.add(new Exit(t.getTo(), t.getTicks(), "jewellery box: " + t.getName()));
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
				exits.add(new Exit(hop.getTo(), hop.getTicks(), (ring ? "house fairy ring: " : "house spirit tree: ")
					+ hop.getName()));
			}
		}
		final double in = HOUSE_TELEPORT_TICKS * SECONDS_PER_TICK + (poh.isTeleportOutside() ? TravelTimes.HOUSE_DOOR : 0)
			+ TravelTimes.HOUSE_WALK;
		return new AtlasHouse(in, exits);
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
