package com.farmrunautopilot.tracking;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.DecodedPatch;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchStateDecoder;
import com.farmrunautopilot.data.PatchType;

/**
 * Works a patch observation forward to "now" (SPEC 10.3), following RuneLite core
 * {@code FarmingTracker.predictPatch}.
 *
 * <p>Unlike RuneLite, weeds are not predicted to grow back on empty patches: in-game testing showed a raked
 * patch staying clear well past a 5 minute tick, so the last seen weed level is kept.
 */
public final class PatchPredictor
{
	/** A grown fruit tree holds 0-6 fruit. */
	static final int FRUIT_STAGES = 7;

	private PatchPredictor()
	{
	}

	public static PatchPrediction predict(Patch patch, PatchRecord record, long plantedAt,
		PatchPrediction.Source source, long now, FarmingTick tick, boolean leagues)
	{
		final DecodedPatch decoded = PatchStateDecoder.decode(patch.getType(), record.getValue());
		final Crop crop = decoded.getCrop();
		PatchState state = decoded.getState();
		int stage = decoded.getStage();
		int stages = 1;
		int tickMinutes = 0;

		if (state == PatchState.GROWING && crop != null)
		{
			tickMinutes = crop.getTickMinutes();
			stages = crop.getStages();
		}
		else if (state == PatchState.HARVESTABLE && patch.getType() == PatchType.FRUIT_TREE)
		{
			tickMinutes = Crop.FRUIT_REGROW_MINUTES;
			stages = FRUIT_STAGES;
		}

		if (leagues)
		{
			// Farming ticks on leagues worlds are 1 minute instead of 5
			tickMinutes /= 5;
		}

		long doneAt = 0;
		if (tickMinutes > 0)
		{
			final long tickNow = tick.tickTime(tickMinutes, 0, now);
			final long tickSeen = tick.tickTime(tickMinutes, 0, record.getObservedAt());
			final int ticksPassed = (int) ((tickNow - tickSeen) / (tickMinutes * 60L));

			doneAt = tick.tickTime(tickMinutes, stages - 1 - stage, tickSeen);
			stage = Math.min(stage + Math.max(ticksPassed, 0), stages - 1);

			if (state == PatchState.GROWING && stage == stages - 1)
			{
				state = patch.getType().isProtectable() ? PatchState.CHECK_HEALTH : PatchState.HARVESTABLE;
			}
		}

		return new PatchPrediction(patch, crop, state, stage, stages, doneAt, record.getObservedAt(), plantedAt, source);
	}

	/**
	 * Whether going from {@code previous} to {@code current} can only be the game's growth tick (not a
	 * player action), so its timing reveals the account's tick offset. Mirrors RuneLite core
	 * {@code FarmingTracker.isObservedGrowthTick}.
	 */
	public static boolean isObservedGrowthTick(PatchType type, DecodedPatch previous, DecodedPatch current)
	{
		final Crop crop = previous.getCrop();
		if (crop == null || current.getCrop() != crop)
		{
			return false;
		}

		switch (previous.getState())
		{
			case GROWING:
				switch (current.getState())
				{
					case GROWING:
					case CHECK_HEALTH:
						return current.getStage() - previous.getStage() == 1;
					case DISEASED:
						return true;
					case HARVESTABLE:
						// Trees only reach HARVESTABLE through the player's check-health.
						return !type.isProtectable();
					default:
						return false;
				}
			case DISEASED:
				return current.getState() == PatchState.DEAD;
			default:
				return false;
		}
	}

	/** Whether this change means the player just planted something. */
	public static boolean isPlanting(DecodedPatch previous, DecodedPatch current)
	{
		return current.getState() == PatchState.GROWING && current.getCrop() != null && current.getStage() == 0
			&& (previous.getCrop() == null || previous.getState() != PatchState.GROWING);
	}
}
