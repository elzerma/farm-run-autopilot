package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.List;

/**
 * The account the sidebar renders show (ui/SidebarRenderTest): one like elzerma's, as the scenario tests use, with a
 * house in Rimmington and quest points, so every tab has something in it.
 */
public final class RenderData
{
	private static RouteAtlas atlas;

	private RenderData()
	{
	}

	public static AccessSnapshot access()
	{
		final AccessSnapshot base = ElzermaAccount.access();
		return new AccessSnapshot(true, base.getQuests(), base.getRealLevels(), base.getCompletedDiaries(),
			base.getUnlocks(), HousePortal.RIMMINGTON, base.getSpellbook(), base.getAltarSpellbooks(), 300, 310);
	}

	public static Holdings holdings()
	{
		return ElzermaAccount.holdings();
	}

	public static RouteAtlas atlas()
	{
		if (atlas == null)
		{
			atlas = AtlasPlayers.with(access(), holdings());
		}
		return atlas;
	}

	/** The Travel tab's families and ways for this account. */
	public static TravelWays ways(RunConfig config)
	{
		return TravelWays.of(atlas(), config, access(), holdings(), new PohSetup(), id -> "Item " + id);
	}

	public static Route route(List<Patch> patches, RunConfig config)
	{
		return RoutePlanner.plan(patches, config, access(), holdings(), new PohSetup(), atlas(), null);
	}
}
