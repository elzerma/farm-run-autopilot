package com.farmrunautopilot.access;

import com.farmrunautopilot.settings.SettingsStore;

/** An access checker that always returns one snapshot, for the sidebar renders. */
public final class RenderAccess
{
	private RenderAccess()
	{
	}

	public static AccessChecker checker(SettingsStore settings, AccessSnapshot snapshot)
	{
		return new AccessChecker(null, settings)
		{
			@Override
			public AccessSnapshot getSnapshot()
			{
				return snapshot;
			}

			@Override
			public void requestRefresh()
			{
			}
		};
	}
}
