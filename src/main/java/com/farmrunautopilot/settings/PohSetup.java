package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import java.util.EnumSet;
import java.util.Set;
import lombok.Data;

/**
 * The player's house (SPEC 5 PohSetup, 13.2 "My POH"). Jewellery box, pool, fairy ring and spirit tree
 * are detected when the player is in their house; everything can be edited by hand. Where the house is and how
 * Teleport to House lands are read from the game instead (AccessSnapshot): saves from before 2026-10-21 still
 * load, and their picked location and "teleport outside" are dropped.
 */
@Data
public class PohSetup
{
	private Set<PortalNexus.Destination> nexusDestinations = EnumSet.noneOf(PortalNexus.Destination.class);
	/** Null if none built. */
	private JewelleryBoxTier jewelleryBox;
	/** Null if none built. */
	private PoolTier pool;
	/** Spellbook altar, or null if none. */
	private PohAltar altar;
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
