package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Colours options for a visit to the tool leprechaun (elzerma, 2026-11-01: "highlights in leprechaun menu with rt click
 * items highlighted?"): "Exchange" on him, which opens his window, during a visit with things to take or store there;
 * and in the window, the "-5" or "-1" option for the quantity the clicks are in (LeprechaunClicks), for a right-click
 * rather than the quantity button: Remove-1/5/X/All on his side, Store-1/5/X/All on the inventory side, as elzerma's run
 * of 2026-11-01 showed. Only colours options the menu has: adds none, removes none. Client thread.
 */
@Singleton
public class LeprechaunMenuHighlighter
{
	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final LeprechaunClicks clicks;

	@Inject
	LeprechaunMenuHighlighter(RunSession session, FarmRunAutopilotConfig config, LeprechaunClicks clicks)
	{
		this.session = session;
		this.config = config;
		this.clicks = clicks;
	}

	public void onMenuOpened(MenuOpened event)
	{
		final MenuEntry[] entries = event.getMenuEntries();
		if (!config.highlightItems() || entries == null)
		{
			return;
		}
		final Highlights highlights = session.getView().getHighlights();
		final boolean windowWork = !highlights.getLeprechaunTake().isEmpty() || !highlights.getLeprechaunStore().isEmpty();
		for (MenuEntry entry : entries)
		{
			final String option = Text.removeTags(entry.getOption());
			final NPC npc = entry.getNpc();
			if (npc != null && PatchGuide.TOOL_LEPRECHAUN.equals(npc.getName()))
			{
				if (windowWork && option.equalsIgnoreCase("Exchange"))
				{
					colour(entry, option);
				}
				continue;
			}
			final Widget widget = entry.getWidget();
			final LeprechaunClicks.Side side = LeprechaunClicks.sideOf(widget);
			if (side == null)
			{
				continue;
			}
			final LeprechaunClicks.Next next = clicks.now();
			if ((side == LeprechaunClicks.Side.TAKE ? next.take : next.store).containsKey(widget.getItemId())
				&& forTarget(option, next.target))
			{
				colour(entry, option);
			}
		}
	}

	/** The option moves the quantity the clicks are in: "-5" for fives, "-1" for ones. */
	static boolean forTarget(String option, LeprechaunClicks.Quantity target)
	{
		return LeprechaunClicks.moves(option) == (target == LeprechaunClicks.Quantity.FIVE ? 5 : 1);
	}

	private void colour(MenuEntry entry, String option)
	{
		entry.setOption(ColorUtil.wrapWithColorTag(option, config.itemColour()));
	}
}
