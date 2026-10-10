package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.supply.SupplyPlan;
import lombok.Value;

/**
 * A bank visit partway through a run whose supplies don't fit in one inventory: after {@link #afterStop} the
 * player banks near that stop, leaving what they no longer need and taking the rest of the run's supplies.
 */
@Value
public class BankStop
{
	/** Index in the route of the stop the bank visit follows. */
	int afterStop;
	/** Where to bank: near the stop just finished. */
	Location location;
	/** What the rest of the run needs, picked up at the bank. */
	SupplyPlan supplies;
}
