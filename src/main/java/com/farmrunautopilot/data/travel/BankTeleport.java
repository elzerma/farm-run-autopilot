package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.supply.Holdings;
import java.util.function.Predicate;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * A teleport straight to a bank, carried on a run too big for one inventory so it can bank partway wherever
 * that's quickest, not only at a stop with a bank nearby. Quickest first.
 */
@Getter
public enum BankTeleport
{
	CRAFTING_CAPE("Crafting cape", "the Crafting Guild bank", new WorldPoint(2936, 3281, 0), 9,
		Requirement.skill(Skill.CRAFTING, 99), "Crafting cape: Teleport",
		ItemID.SKILLCAPE_CRAFTING, ItemID.SKILLCAPE_CRAFTING_TRIMMED),
	RING_OF_DUELING("Ring of dueling", "the Castle Wars bank chest", new WorldPoint(2443, 3083, 0), 10, null,
		"Ring of dueling: Castle Wars",
		ItemID.RING_OF_DUELING_1, ItemID.RING_OF_DUELING_2, ItemID.RING_OF_DUELING_3, ItemID.RING_OF_DUELING_4,
		ItemID.RING_OF_DUELING_5, ItemID.RING_OF_DUELING_6, ItemID.RING_OF_DUELING_7, ItemID.RING_OF_DUELING_8),
	MAX_CAPE("Max cape", "the Crafting Guild bank", new WorldPoint(2936, 3281, 0), 11, null,
		"Max cape: Crafting Guild", TravelItem.MAX_CAPE.getItemIds()),
	RING_OF_WEALTH("Ring of wealth", "the Grand Exchange", new WorldPoint(3164, 3489, 0), 15, null,
		"Ring of wealth: Grand Exchange", TravelItem.RING_OF_WEALTH.getItemIds());

	private final String itemName;
	/** Where it lands, e.g. "the Castle Wars bank chest". */
	private final String bankName;
	/** The bank chest or booth it lands by (Shortest Path's bank list), so the guide knows the player got there. */
	private final WorldPoint bankPoint;
	/** Teleport, landing and the walk to the bank booth or chest. */
	private final double seconds;
	private final Requirement requirement;
	/** The travel atlas's key for this teleport (AtlasWays), so it isn't used once the player turns it off. */
	private final String wayKey;
	private final int[] itemIds;

	BankTeleport(String itemName, String bankName, WorldPoint bankPoint, double seconds, Requirement requirement,
		String atlasName, int... itemIds)
	{
		this.itemName = itemName;
		this.bankName = bankName;
		this.bankPoint = bankPoint;
		this.seconds = seconds;
		this.requirement = requirement;
		this.wayKey = "TELEPORTATION_ITEM|" + atlasName;
		this.itemIds = itemIds;
	}

	public int[] getItemIds()
	{
		return itemIds.clone();
	}

	/** The quickest bank teleport the player has anywhere the plugin can see, or null. */
	public static BankTeleport best(AccessSnapshot access, Holdings holdings)
	{
		return best(access, holdings, t -> true);
	}

	/**
	 * The quickest bank teleport the player has anywhere the plugin can see and lets the route use ({@code allowed}:
	 * the Travel tab's teleport items on, this one not turned off), or null.
	 */
	public static BankTeleport best(AccessSnapshot access, Holdings holdings, Predicate<BankTeleport> allowed)
	{
		for (BankTeleport teleport : values())
		{
			if ((teleport.requirement == null || access.isMet(teleport.requirement))
				&& holdings.countAny(teleport.itemIds) > 0 && allowed.test(teleport))
			{
				return teleport;
			}
		}
		return null;
	}
}
