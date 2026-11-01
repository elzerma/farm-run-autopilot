package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
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
 * rather than the quantity button. Each menu's options there are logged, to check their wording from a run. Only colours
 * options the menu has: adds none, removes none. Client thread.
 */
@Slf4j
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
		boolean logged = false;
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
			if (!logged)
			{
				logged = true;
				log.debug("Leprechaun window menu on {}: {}", Text.removeTags(entry.getTarget()), optionList(entries));
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

	private static String optionList(MenuEntry[] entries)
	{
		final StringBuilder s = new StringBuilder();
		for (MenuEntry entry : entries)
		{
			s.append(s.length() > 0 ? ", " : "").append(Text.removeTags(entry.getOption()));
		}
		return s.toString();
	}
}
