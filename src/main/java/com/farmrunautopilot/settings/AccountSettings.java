package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.Unlock;
import java.util.EnumSet;
import java.util.Set;
import lombok.Data;

/**
 * Facts about the account that can't be detected automatically. Kept apart from {@link RunConfig} so
 * presets (M9) don't carry them.
 */
@Data
public class AccountSettings
{
	/** Unlocks the player ticked in Setup. Only used for unlocks with no automatic detection. */
	private Set<Unlock> manualUnlocks = EnumSet.noneOf(Unlock.class);
	private PohSetup poh = new PohSetup();
	/** Uses left in the bottomless compost bucket, from its game messages; null until seen. */
	private Integer bottomlessUses;
	/** What the bottomless compost bucket holds, from its game messages; null until seen. */
	private Compost bottomlessCompost;

	public AccountSettings sanitise()
	{
		manualUnlocks = RunConfig.cleanSet(manualUnlocks, Unlock.class, EnumSet.noneOf(Unlock.class));
		if (poh == null)
		{
			poh = new PohSetup();
		}
		poh.sanitise();
		return this;
	}
}
