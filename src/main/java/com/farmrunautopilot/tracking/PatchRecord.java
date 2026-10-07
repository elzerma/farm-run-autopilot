package com.farmrunautopilot.tracking;

import lombok.Value;

/**
 * One saved observation of a patch: the raw varbit value and when it was seen. Stored as
 * {@code "<value>:<epochSeconds>"}, the same format as RuneLite core Time Tracking.
 */
@Value
public class PatchRecord
{
	int value;
	long observedAt;

	public String format()
	{
		return value + ":" + observedAt;
	}

	/**
	 * @return the record, or null if the text is missing or malformed
	 */
	public static PatchRecord parse(String text)
	{
		if (text == null)
		{
			return null;
		}
		final String[] parts = text.split(":");
		if (parts.length != 2)
		{
			return null;
		}
		try
		{
			final int value = Integer.parseInt(parts[0]);
			final long observedAt = Long.parseLong(parts[1]);
			return observedAt > 0 ? new PatchRecord(value, observedAt) : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
