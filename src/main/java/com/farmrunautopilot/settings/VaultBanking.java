package com.farmrunautopilot.settings;

import lombok.Getter;

/**
 * How a run that banks partway gets the seeds kept in the seed vault (issue #8): left to the player, worded by
 * elzerma in the issue ledger.
 */
@Getter
public enum VaultBanking
{
	BANK_AT_GUILD("Bank at the Farming Guild", "Take only the seeds this part of the run needs from the seed vault. "
		+ "When the run banks partway, it banks at the Farming Guild, so the rest come from the vault then."),
	TAKE_ALL_AT_START("Take all seeds at the start", "Take every seed the run needs from the seed vault before you set "
		+ "off, and put the ones for later in your bank. When the run banks partway, take them from your bank.");

	private final String displayName;
	private final String tooltip;

	VaultBanking(String displayName, String tooltip)
	{
		this.displayName = displayName;
		this.tooltip = tooltip;
	}
}
