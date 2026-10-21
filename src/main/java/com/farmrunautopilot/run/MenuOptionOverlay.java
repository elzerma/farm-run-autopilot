package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Frames the option a leg needs in its teleport item's open right-click menu (elzerma, 2026-10-20: the vine as the
 * plugin's one "click here"), the one TeleportMenuHighlighter colours: a growing vine on the option's row, or the item
 * colour's outline with vines off. An option in a sub-menu frames the entry opening it until the sub-menu is open.
 * Above everything, as the menu is drawn over the game's interfaces.
 */
@Slf4j
public class MenuOptionOverlay extends Overlay
{
	/**
	 * The menu's "Choose Option" header, above its first row, and each row, in pixels: the game's, as RuneLite's own
	 * Interact Highlight reads them.
	 */
	static final int HEADER = 19;
	static final int ROW = 15;
	/** The menu's border, kept clear either side of a row. */
	private static final int BORDER = 1;

	private final Client client;
	private final FarmRunAutopilotConfig config;
	private final TeleportMenuHighlighter highlighter;
	private final VineFrame vine = new VineFrame();
	/** The menu last logged, so its layout is logged once while it's open. */
	private int loggedMenu;

	@Inject
	MenuOptionOverlay(Client client, FarmRunAutopilotConfig config, TeleportMenuHighlighter highlighter)
	{
		this.client = client;
		this.config = config;
		this.highlighter = highlighter;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Rectangle row = config.highlightItems() && client.isMenuOpen() ? row() : null;
		if (row == null)
		{
			vine.clear();
			loggedMenu = 0;
			return null;
		}
		if (config.vineHighlights())
		{
			// The vine sits its gap outside what it frames; framing the row itself keeps it off the options either side
			final int gap = VineFrame.GAP;
			vine.draw(graphics, new Rectangle(row.x + gap, row.y + gap, row.width - 2 * gap, row.height - 2 * gap));
			return null;
		}
		graphics.setColor(config.itemColour());
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRect(row.x, row.y, row.width, row.height);
		return null;
	}

	/** The row to frame in the open menu, or null if it isn't the teleport item's or doesn't show it. */
	private Rectangle row()
	{
		final Menu menu = client.getMenu();
		final MenuEntry[] entries = menu.getMenuEntries();
		final TeleportMenuHighlighter.Found found = highlighter.find(entries);
		if (!found.isFound())
		{
			return null;
		}
		final int scroll = client.isMenuScrollable() ? client.getMenuScroll() : 0;
		if (found.parent >= 0)
		{
			final Menu sub = entries[found.parent].getSubMenu();
			// UNVERIFIED: a sub-menu that isn't open has no size
			if (sub != null && sub.getMenuWidth() > 0 && sub.getMenuHeight() > 0)
			{
				final int count = sub.getMenuEntries().length;
				// A sub-menu's header, if it has one, is whatever its rows leave of its height
				final int header = Math.max(0, Math.min(HEADER, sub.getMenuHeight() - count * ROW));
				log(sub, count, header, 0, found);
				return row(sub, header, count, found.index, 0);
			}
			log(menu, entries.length, HEADER, scroll, found);
			return row(menu, HEADER, entries.length, found.parent, scroll);
		}
		log(menu, entries.length, HEADER, scroll, found);
		return row(menu, HEADER, entries.length, found.index, scroll);
	}

	/**
	 * An entry's row in a menu, or null if it's scrolled out of sight. The last entry is the top row, under the header.
	 */
	static Rectangle row(Menu menu, int header, int count, int index, int scroll)
	{
		final int fromTop = count - 1 - index - scroll;
		final int y = menu.getMenuY() + header + fromTop * ROW;
		if (fromTop < 0 || y + ROW > menu.getMenuY() + menu.getMenuHeight())
		{
			return null;
		}
		return new Rectangle(menu.getMenuX() + BORDER, y, menu.getMenuWidth() - 2 * BORDER, ROW);
	}

	/** The menu's layout, once while it's open, to put the rows right if they're off. */
	private void log(Menu menu, int count, int header, int scroll, TeleportMenuHighlighter.Found found)
	{
		final int key = 31 * (31 * menu.getMenuX() + menu.getMenuY()) + count + found.parent * 7 + found.index * 101;
		if (key == loggedMenu)
		{
			return;
		}
		loggedMenu = key;
		log.debug("Menu option framed: menu at {},{} size {}x{}, {} entries, header {}, scroll {}, entry {} (sub-menu of {})",
			menu.getMenuX(), menu.getMenuY(), menu.getMenuWidth(), menu.getMenuHeight(), count, header, scroll,
			found.index, found.parent);
	}
}
