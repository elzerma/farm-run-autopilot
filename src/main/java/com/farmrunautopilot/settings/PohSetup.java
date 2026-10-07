package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import java.util.EnumSet;
import java.util.Set;
import lombok.Data;

/**
 * The player's house (SPEC 5 PohSetup, 13.2 "My POH"). Jewellery box, pool, fairy ring and spirit tree
 * are detected when the player is in their house; everything can be edited by hand.
 */
@Data
public class PohSetup
{
	/** Null if unknown or no house. */
	private HousePortal portal;
	/** House set to "teleport outside", so Teleport to House lands at the portal. */
	private boolean teleportOutside;
	private Set<PortalNexus.Destination> nexusDestinations = EnumSet.noneOf(PortalNexus.Destination.class);
	/** Null if none built. */
	private JewelleryBoxTier jewelleryBox;
	/** Null if none built. */
	private PoolTier pool;
	private boolean fairyRing;
	private boolean spiritTree;
	/** Epoch seconds of the last automatic detection, 0 if never. */
	private long lastDetected;

	public PohSetup sanitise()
	{
		nexusDestinations = RunConfig.cleanSet(nexusDestinations, PortalNexus.Destination.class,
			EnumSet.noneOf(PortalNexus.Destination.class));
		return this;
	}
}
