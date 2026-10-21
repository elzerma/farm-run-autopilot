package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.atlas.AtlasBanks;
import com.farmrunautopilot.supply.SupplyPlan;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * A bank visit partway through a run whose supplies don't fit in one inventory: after {@link #afterStop} the
 * player banks, near that stop, by a bank teleport, or at the Farming Guild for its seed vault, leaving what they
 * no longer need and taking the rest of the run's supplies.
 */
@Value
@AllArgsConstructor
public class BankStop
{
	/** Index in the route of the stop the bank visit follows. */
	int afterStop;
	/** The stop just finished. */
	Location location;
	/** What the rest of the run needs, picked up at the bank. */
	SupplyPlan supplies;
	/** The bank teleport to use, or null to bank near {@link #location}. */
	BankTeleport teleport;
	/**
	 * The way to the Farming Guild, to bank there by its seed vault ("Bank at the Farming Guild", issue #8); null
	 * otherwise.
	 */
	RouteStop trip;

	public BankStop(int afterStop, Location location, SupplyPlan supplies, BankTeleport teleport)
	{
		this(afterStop, location, supplies, teleport, null);
	}

	/** At the Farming Guild's bank, where the seed vault is: after the guild's own stop, or by a trip there. */
	public boolean isAtGuild()
	{
		return trip != null || teleport == null && location == Location.FARMING_GUILD;
	}

	/** Where the bank is, when it isn't reached by a bank teleport: by the stop, or the Farming Guild's. */
	public Location getBankLocation()
	{
		return trip != null ? Location.FARMING_GUILD : location;
	}

	/** e.g. "Bank near Gnome Stronghold" or "Bank at the Castle Wars bank chest". */
	public String title()
	{
		if (trip != null)
		{
			return "Bank at the Farming Guild";
		}
		// An unplanned visit (an item gone missing) can come where there's no bank close by
		return teleport != null ? "Bank at " + teleport.getBankName()
			: AtlasBanks.near(location) == null ? "Go to a bank" : "Bank near " + location.getDisplayName();
	}

	/** e.g. "Ring of dueling to the Castle Wars bank chest" or "Bank near Gnome Stronghold". */
	public String how()
	{
		if (trip != null)
		{
			return trip.describeWay() + " to the guild's bank";
		}
		return teleport != null ? teleport.getItemName() + " to " + teleport.getBankName() : title();
	}
}
