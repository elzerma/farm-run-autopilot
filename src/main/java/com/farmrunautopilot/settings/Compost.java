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

	/** From game text such as "ultracompost", or null. */
	public static Compost fromName(String name)
	{
		for (Compost compost : values())
		{
			if (compost != NONE && compost.displayName.equalsIgnoreCase(name))
			{
				return compost;
			}
		}
		return null;
	}

	/** The tool leprechaun's bottomless bucket type value, or null. UNVERIFIED: assumed 1 compost, 2 super, 3 ultra. */
	public static Compost fromBucketType(int type)
	{
		switch (type)
		{
			case 1:
				return COMPOST;
			case 2:
				return SUPERCOMPOST;
			case 3:
				return ULTRACOMPOST;
			default:
				return null;
		}
	}
}
