package com.farmrunautopilot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * The compost bins by the allotments (docs/plans/patch-actions.md, P3): each one's farming area and the varbit its state is
 * sent in while the player's there, as RuneLite's Time Tracking has them (FarmingWorld, BSD-2, see THIRD_PARTY_NOTICES).
 * The Farming Guild's is the big bin.
 */
@Getter
@RequiredArgsConstructor
public enum CompostBin
{
	ARDOUGNE(Location.ARDOUGNE_FARM, 10548, VarbitID.FARMING_TRANSMIT_E, false),
	CATHERBY(Location.CATHERBY, 11062, VarbitID.FARMING_TRANSMIT_E, false),
	CIVITAS_ILLA_FORTIS(Location.CIVITAS_ILLA_FORTIS, 6192, VarbitID.FARMING_TRANSMIT_E, false),
	FALADOR(Location.FALADOR_FARM, 12083, VarbitID.FARMING_TRANSMIT_E, false),
	HOSIDIUS(Location.HOSIDIUS, 6967, VarbitID.FARMING_TRANSMIT_E, false),
	PORT_PHASMATYS(Location.PORT_PHASMATYS, 14391, VarbitID.FARMING_TRANSMIT_E, false),
	PRIFDDINAS(Location.PRIFDDINAS, 13151, VarbitID.FARMING_TRANSMIT_D, false),
	FARMING_GUILD(Location.FARMING_GUILD, 4922, VarbitID.FARMING_TRANSMIT_N, true);

	private final Location location;
	private final int regionId;
	private final int varbitId;
	private final boolean big;

	/** Items it takes before it can be closed, and the buckets of compost it gives: 15, or 30 for the big bin (OSRS Wiki). */
	public int capacity()
	{
		return big ? 30 : 15;
	}

	/** Volcanic ash that turns its supercompost into ultracompost: 25, or 50 for the big bin (OSRS Wiki, Ultracompost). */
	public int ashForUltracompost()
	{
		return big ? 50 : 25;
	}

	/** The item an allotment crop is picked as, for putting it in a bin; -1 for any other crop. */
	public static int cropItem(Crop crop)
	{
		switch (crop)
		{
			case POTATO:
				return ItemID.POTATO;
			case ONION:
				return ItemID.ONION;
			case CABBAGE:
				return ItemID.CABBAGE;
			case TOMATO:
				return ItemID.TOMATO;
			case SWEETCORN:
				return ItemID.SWEETCORN;
			case STRAWBERRY:
				return ItemID.STRAWBERRY;
			case WATERMELON:
				return ItemID.WATERMELON;
			default:
				return -1;
		}
	}

	/** The bin at this farming area, or null. */
	public static CompostBin at(Location location)
	{
		for (CompostBin bin : values())
		{
			if (bin.location == location)
			{
				return bin;
			}
		}
		return null;
	}
}
