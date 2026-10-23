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

	/** The tool leprechaun's bottomless bucket type value for an empty bucket (the game's farming_tools_getstored script). */
	public static final int EMPTY_BUCKET_TYPE = 1;

	/**
	 * The compost in a bucket stored at the tool leprechaun, from its type value, or null (empty, or unknown). The
	 * game's farming_tools_getstored script says 1 is an empty bucket and anything higher a filled one. 4 ultra
	 * confirmed (#20, #29); 2 compost and 3 super UNVERIFIED (guided test BUCKET_COMPOST).
	 */
	public static Compost fromBucketType(int type)
	{
		switch (type)
		{
			case 2:
				return COMPOST;
			case 3:
				return SUPERCOMPOST;
			case 4:
				return ULTRACOMPOST;
			default:
				return null;
		}
	}
}
