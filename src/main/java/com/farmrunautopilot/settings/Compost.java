package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.DataConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum Compost
{
	NONE("No compost", DataConstants.NONE),
	COMPOST("Compost", ItemID.BUCKET_COMPOST),
	SUPERCOMPOST("Supercompost", ItemID.BUCKET_SUPERCOMPOST),
	ULTRACOMPOST("Ultracompost", ItemID.BUCKET_ULTRACOMPOST);

	private final String displayName;
	private final int itemId;

	@Override
	public String toString()
	{
		return displayName;
	}
}
