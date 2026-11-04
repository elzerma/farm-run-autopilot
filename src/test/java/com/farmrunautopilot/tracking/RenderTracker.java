package com.farmrunautopilot.tracking;

import com.farmrunautopilot.data.Patch;
import java.util.function.Function;

/** A patch tracker with made-up predictions, for the sidebar renders. */
public final class RenderTracker
{
	private RenderTracker()
	{
	}

	public static PatchTracker tracker(Function<Patch, PatchPrediction> predict)
	{
		return new PatchTracker(null, null)
		{
			@Override
			public PatchPrediction predict(Patch patch)
			{
				return predict.apply(patch);
			}
		};
	}
}
