package com.farmrunautopilot.tracking;

import com.farmrunautopilot.data.PatchType;

/**
 * Short plain-language descriptions of patch predictions for the sidebar.
 */
public final class PatchStatusText
{
	private PatchStatusText()
	{
	}

	/**
	 * e.g. "Ranarr - ready in 34m", "Magic - check health", "Empty".
	 */
	public static String describe(PatchPrediction p, long now)
	{
		if (p == null)
		{
			return "Not seen yet";
		}

		final String crop = p.getCrop() != null ? p.getCrop().getDisplayName() : null;
		final PatchType type = p.getPatch().getType();
		switch (p.getState())
		{
			case EMPTY:
				return "Empty";
			case WEEDS:
				return "Weeds";
			case GROWING:
				if (crop == null)
				{
					return "Something else growing";
				}
				return crop + " - ready in " + duration(p.getDoneAt() - now);
			case CHECK_HEALTH:
				return crop + " - check health";
			case HARVESTABLE:
				if (crop == null)
				{
					return "Something else ready";
				}
				if (type == PatchType.HERB)
				{
					return crop + " - ready to pick";
				}
				if (type == PatchType.FRUIT_TREE)
				{
					final int fruit = p.getStage();
					final String count = crop + " - " + fruit + " fruit";
					return fruit < p.getStages() - 1 && p.getDoneAt() > now
						? count + " (full in " + duration(p.getDoneAt() - now) + ")"
						: count;
				}
				return crop + " - grown, ready to clear";
			case STUMP:
				return crop + " - stump";
			case DISEASED:
				return (crop != null ? crop : "Crop") + " - diseased";
			case DEAD:
				return "Dead " + (crop != null ? crop : type.getDisplayName()).toLowerCase();
			default:
				return "Unknown state";
		}
	}

	/**
	 * e.g. "2h 05m", "45m", "<1m".
	 */
	public static String duration(long seconds)
	{
		if (seconds < 60)
		{
			return "<1m";
		}
		final long minutes = (seconds + 59) / 60;
		final long hours = minutes / 60;
		final long mins = minutes % 60;
		if (hours == 0)
		{
			return mins + "m";
		}
		return hours + "h " + (mins < 10 ? "0" : "") + mins + "m";
	}

	/**
	 * e.g. "seen 3h 10m ago".
	 */
	public static String age(long epochSeconds, long now)
	{
		final long seconds = now - epochSeconds;
		return seconds < 60 ? "seen just now" : "seen " + duration(seconds) + " ago";
	}
}
