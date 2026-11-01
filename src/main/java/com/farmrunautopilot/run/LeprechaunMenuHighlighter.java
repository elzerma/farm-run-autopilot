package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Colours the option in the tool leprechaun's window that takes or stores the amount the visit says (elzerma,
 * 2026-11-01: "highlights in leprechaun menu with rt click items highlighted?"), e.g. "-X" for 13 empty buckets or
 * "-All" for all the ultracompost carried, as the item's outline labels it "Right-click". Matched by the amount after
 * the dash, as the options' first word is UNVERIFIED: each menu's options there are logged, to check it from a run.
 * Only colours options the menu has: adds none, removes none. Client thread.
 */
@Slf4j
@Singleton
public class LeprechaunMenuHighlighter
{
	private final RunSession session;
	private final FarmRunAutopilotConfig config;

	@Inject
	LeprechaunMenuHighlighter(RunSession session, FarmRunAutopilotConfig config)
	{
		this.session = session;
		this.config = config;
	}

	public void onMenuOpened(MenuOpened event)
	{
		final MenuEntry[] entries = event.getMenuEntries();
		if (!config.highlightItems() || entries == null)
		{
			return;
		}
		final Highlights highlights = session.getView().getHighlights();
		boolean logged = false;
		for (MenuEntry entry : entries)
		{
			final Widget widget = entry.getWidget();
			final int group = widget != null ? WidgetUtil.componentToInterface(widget.getId()) : -1;
			if (group != InterfaceID.FARMING_TOOLS && group != InterfaceID.FARMING_TOOLS_SIDE)
			{
				continue;
			}
			if (!logged)
			{
				logged = true;
				log.debug("Leprechaun window menu on {}: {}", Text.removeTags(entry.getTarget()), optionList(entries));
			}
			final Map<Integer, Integer> amounts = group == InterfaceID.FARMING_TOOLS ? highlights.getLeprechaunTake()
				: highlights.getLeprechaunStore();
			final Integer amount = amounts.get(widget.getItemId());
			final String option = Text.removeTags(entry.getOption());
			if (amount != null && amount > 0 && forAmount(option, amount, widget.getItemQuantity()))
			{
				entry.setOption(ColorUtil.wrapWithColorTag(option, config.itemColour()));
			}
		}
	}

	/**
	 * The option takes or stores this amount, of what's there: "-All" for all of it, "-1" and "-5" for those, else "-X";
	 * e.g. "Remove-X" for 13 of 20.
	 */
	static boolean forAmount(String option, int amount, int there)
	{
		final String wanted = amount >= there ? "all" : amount == 1 ? "1" : amount == 5 ? "5" : "x";
		final String text = option.toLowerCase();
		return text.endsWith("-" + wanted) || text.endsWith(" " + wanted);
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
