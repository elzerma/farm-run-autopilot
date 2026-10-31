package com.farmrunautopilot.data;

import com.farmrunautopilot.settings.Compost;
import lombok.Value;

/**
 * A compost bin's state from its varbit value, as RuneLite's Time Tracking reads it (PatchImplementation COMPOST and
 * BIG_COMPOST, BSD-2, see THIRD_PARTY_NOTICES): empty, filling, closed and rotting, rotted (closed until opened), or open
 * with compost to take out. Tomatoes, which rot into rotten tomatoes, are left to the player: their compost is NONE.
 */
@Value
public class BinState
{
	public enum Stage
	{
		EMPTY, FILLING, ROTTING, ROTTED, READY
	}

	public static final BinState EMPTY = new BinState(Stage.EMPTY, Compost.NONE, 0);

	Stage stage;
	/** What it makes or holds: compost, supercompost or ultracompost; NONE when empty or for tomatoes. */
	Compost compost;
	/** Items in it while filling, buckets of compost left to take out once open; 0 otherwise. */
	int count;

	/** The state for this varbit value, or null for one RuneLite doesn't know. */
	public static BinState decode(int value, boolean big)
	{
		if (value == 0)
		{
			return EMPTY;
		}
		final BinState shared = decodeShared(value);
		if (shared != null)
		{
			return shared;
		}
		return big ? decodeBig(value) : decodeRegular(value);
	}

	/** The values both kinds of bin share. */
	private static BinState decodeShared(int value)
	{
		if (value >= 1 && value <= 15)
		{
			return new BinState(Stage.FILLING, Compost.COMPOST, value);
		}
		if (value >= 16 && value <= 30)
		{
			return new BinState(Stage.READY, Compost.COMPOST, value - 15);
		}
		if (value >= 33 && value <= 47)
		{
			return new BinState(Stage.FILLING, Compost.SUPERCOMPOST, value - 32);
		}
		if (value >= 48 && value <= 62)
		{
			return new BinState(Stage.READY, Compost.SUPERCOMPOST, value - 47);
		}
		if (value >= 129 && value <= 143)
		{
			return new BinState(Stage.FILLING, Compost.NONE, value - 128);
		}
		if (value >= 144 && value <= 158)
		{
			return new BinState(Stage.READY, Compost.NONE, value - 143);
		}
		if (value == 159 || value == 160)
		{
			return new BinState(Stage.ROTTING, Compost.NONE, 0);
		}
		return null;
	}

	private static BinState decodeRegular(int value)
	{
		switch (value)
		{
			case 31:
			case 32:
				return new BinState(Stage.ROTTING, Compost.COMPOST, 0);
			case 94:
				return new BinState(Stage.ROTTED, Compost.COMPOST, 0);
			case 95:
			case 96:
				return new BinState(Stage.ROTTING, Compost.SUPERCOMPOST, 0);
			case 126:
				return new BinState(Stage.ROTTED, Compost.SUPERCOMPOST, 0);
			default:
				return value >= 176 && value <= 190 ? new BinState(Stage.READY, Compost.ULTRACOMPOST, value - 175) : null;
		}
	}

	/** The big bin's: 16 to 30 items and buckets in ranges of their own. */
	private static BinState decodeBig(int value)
	{
		if (value >= 63 && value <= 77)
		{
			return new BinState(Stage.FILLING, Compost.COMPOST, value - 62 + 15);
		}
		if (value >= 78 && value <= 92)
		{
			return new BinState(Stage.READY, Compost.COMPOST, value - 77 + 15);
		}
		if (value >= 100 && value <= 114)
		{
			return new BinState(Stage.READY, Compost.SUPERCOMPOST, value - 99 + 15);
		}
		if (value >= 161 && value <= 175)
		{
			return new BinState(Stage.FILLING, Compost.SUPERCOMPOST, value - 160 + 15);
		}
		if (value >= 176 && value <= 205)
		{
			return new BinState(Stage.READY, Compost.ULTRACOMPOST, value - 175);
		}
		if (value >= 207 && value <= 221)
		{
			return new BinState(Stage.READY, Compost.NONE, value - 206 + 15);
		}
		if (value >= 223 && value <= 237)
		{
			return new BinState(Stage.FILLING, Compost.NONE, value - 222 + 15);
		}
		// UNVERIFIED: 97 to 99 and 127 to 128 have no object IDs in RuneLite's notes, unlike the others; the run log's
		// "Compost bin at Farming Guild" lines will show them
		switch (value)
		{
			case 93:
				return new BinState(Stage.ROTTED, Compost.COMPOST, 0);
			case 127:
			case 128:
				return new BinState(Stage.ROTTING, Compost.COMPOST, 0);
			case 97:
			case 98:
				return new BinState(Stage.ROTTING, Compost.SUPERCOMPOST, 0);
			// The last of its rotting stages, as 126 is the regular bin's
			case 99:
				return new BinState(Stage.ROTTED, Compost.SUPERCOMPOST, 0);
			case 222:
				return new BinState(Stage.ROTTED, Compost.NONE, 0);
			default:
				return null;
		}
	}

	/** It has compost to take out, or will once opened. */
	public boolean hasCompost()
	{
		return compost != Compost.NONE && (stage == Stage.ROTTED || stage == Stage.READY);
	}

	/**
	 * Volcanic ash is worth adding to make its supercompost ultracompost: rotted, or open with enough left that the ash
	 * for the bin is no more than for its buckets, at 2 a bucket (OSRS Wiki, Ultracompost). The bin takes all of it
	 * however many are left (elzerma's run of 2026-10-31: 25 for the 13 left after taking 2 out).
	 *
	 * @param ash the bin's: 25, or 50 for the big bin
	 */
	public boolean takesAsh(int ash)
	{
		return compost == Compost.SUPERCOMPOST && (stage == Stage.ROTTED || stage == Stage.READY && count * 2 >= ash);
	}

	/** Crops can go in: empty, or filling with compost, not the player's own tomatoes. */
	public boolean fills()
	{
		return stage == Stage.EMPTY || stage == Stage.FILLING && compost != Compost.NONE;
	}
}
