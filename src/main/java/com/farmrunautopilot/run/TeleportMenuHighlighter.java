package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Colours the option a leg needs in its teleport item's right-click menu (elzerma, 2026-10-18: "Jewelery and item
 * right click menus should also highlight option that is needed"), e.g. "Crafting Guild" on a worn skills necklace.
 * When the item's places are behind "Rub" (or "Teleport", "Reminisce"...), that option instead; TravelMenuOverlay then
 * rings the place in the list. Only colours options the menu has: adds none, removes none. Client thread.
 */
@Singleton
public class TeleportMenuHighlighter
{
	/** Options that open an item's list of places. */
	private static final Set<String> OPENERS = Set.of("rub", "teleport", "teleports", "reminisce", "commune", "travel");

	private final RunSession session;
	private final FarmRunAutopilotConfig config;

	@Inject
	TeleportMenuHighlighter(RunSession session, FarmRunAutopilotConfig config)
	{
		this.session = session;
		this.config = config;
	}

	public void onMenuOpened(MenuOpened event)
	{
		final Highlights highlights = session.getView().getHighlights();
		final String option = highlights.getTeleportOption();
		if (option == null || !config.highlightItems())
		{
			return;
		}
		final MenuEntry[] entries = event.getMenuEntries();
		final String[] options = new String[entries.length];
		final int[] items = new int[entries.length];
		for (int i = 0; i < entries.length; i++)
		{
			options[i] = entries[i].getOption();
			items[i] = entries[i].getItemId();
		}
		final int pick = pick(options, items, highlights.getItemIds(), option);
		if (pick >= 0)
		{
			entries[pick].setOption(ColorUtil.wrapWithColorTag(Text.removeTags(options[pick]), config.itemColour()));
		}
	}

	/**
	 * Which of a menu's options to colour (its index), or -1: the teleport item's option naming the place (e.g.
	 * "Ardougne Farm" for "farm"), or else the one that opens its list of places.
	 *
	 * @param options each option's text
	 * @param items each option's item id
	 */
	static int pick(String[] options, int[] items, Set<Integer> teleportIds, String option)
	{
		final String place = option.toLowerCase(Locale.ROOT);
		int opener = -1;
		for (int i = 0; i < options.length; i++)
		{
			if (!teleportIds.contains(items[i]) || options[i] == null)
			{
				continue;
			}
			final String text = Text.removeTags(options[i]).toLowerCase(Locale.ROOT);
			if (text.contains(place))
			{
				return i;
			}
			if (opener < 0 && OPENERS.contains(text))
			{
				opener = i;
			}
		}
		return opener;
	}
}
