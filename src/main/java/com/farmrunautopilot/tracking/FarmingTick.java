package com.farmrunautopilot.tracking;

import lombok.Value;

/**
 * Farming tick maths, following RuneLite core {@code FarmingTracker.getTickTime}. Crops grow on fixed
 * minute boundaries (every 5, 20, 40, 160 ... minutes), shifted by an account-specific offset that is
 * learned by watching a crop grow.
 */
@Value
public class FarmingTick
{
	/** Offset in minutes (stored as a positive number), or null if never observed. */
	Integer offsetMinutes;
	/** Tick rate the offset was observed on; longer rates give a more precise offset. */
	Integer offsetPrecisionMinutes;

	public static final FarmingTick UNKNOWN = new FarmingTick(null, null);

	/**
	 * Time (epoch seconds) of the tick boundary {@code ticks} ticks after the boundary at or before
	 * {@code epochSeconds}.
	 */
	public long tickTime(int tickMinutes, int ticks, long epochSeconds)
	{
		final long offsetSeconds = offsetSecondsFor(tickMinutes);
		final long shifted = epochSeconds + offsetSeconds;
		final long tickSeconds = tickMinutes * 60L;
		final long currentTick = shifted - (shifted % tickSeconds);
		return currentTick + ticks * tickSeconds - offsetSeconds;
	}

	private long offsetSecondsFor(int tickMinutes)
	{
		if (offsetMinutes == null || offsetPrecisionMinutes == null)
		{
			return 0;
		}
		if (offsetPrecisionMinutes >= tickMinutes || offsetPrecisionMinutes >= 40)
		{
			return (offsetMinutes % tickMinutes) * 60L;
		}
		return 0;
	}

	/**
	 * The offset implied by seeing a growth tick happen at {@code epochSeconds} on a crop with this tick
	 * rate.
	 */
	public static int observedOffsetMinutes(int tickMinutes, long epochSeconds)
	{
		return (int) Math.abs(((epochSeconds / 60) % tickMinutes) - tickMinutes);
	}
}
