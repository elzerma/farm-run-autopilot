package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.supply.SupplyPlan;
import lombok.Value;

/**
 * A bank visit partway through a run whose supplies don't fit in one inventory: after {@link #afterStop} the
 * player banks, near that stop or by a bank teleport, leaving what they no longer need and taking the rest of
 * the run's supplies.
 */
@Value
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

	/** e.g. "Bank near Gnome Stronghold" or "Bank at the Castle Wars bank chest". */
	public String title()
	{
		// An unplanned visit (an item gone missing) can come where there's no bank close by
		return teleport != null ? "Bank at " + teleport.getBankName()
			: TravelTimes.bankTiles(location) == null ? "Go to a bank" : "Bank near " + location.getDisplayName();
	}

	/** e.g. "Ring of dueling to the Castle Wars bank chest" or "Bank near Gnome Stronghold". */
	public String how()
	{
		return teleport != null ? teleport.getItemName() + " to " + teleport.getBankName() : title();
	}
}
