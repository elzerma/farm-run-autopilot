package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the item to use next in the inventory and equipment: seed, compost, tool, payment or teleport
 * (SPEC 13.4). Items to put on get their own colour and a "Wear" label, so an item to wear is never mistaken for
 * the next teleport. A teleport whose option isn't the item's left click is labelled "Right-click" in the item colour
 * (elzerma, 2026-10-20: obvious when a right-click is needed); its menu then frames the option (MenuOptionOverlay).
 */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private static final int MAX_CACHED_OUTLINES = 100;
	/** Items to put on: a colour no other highlight uses. */
	static final Color WEAR_COLOUR = new Color(0x40, 0xC8, 0xFF);
	private static final String WEAR_LABEL = "Wear";
	private static final String RIGHT_CLICK_LABEL = "Right-click";
	private static final Color LABEL_BACKGROUND = new Color(0, 0, 0, 170);

	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final ItemManager itemManager;
	/**
	 * Outline images, made once per item, stack size and colour rather than every frame; cleared when the colour
	 * changes.
	 */
	private final Map<String, BufferedImage> outlineCache = new HashMap<>();
	/**
	 * Whether each teleport option needs a right-click on each item, worn or not: guessed from the item's own options,
	 * then as its menu showed it under the mouse.
	 */
	private final Map<String, Boolean> rightClickCache = new HashMap<>();
	private final Client client;
	private final TeleportMenuHighlighter menuHighlighter;

	@Inject
	ItemHighlightOverlay(RunSession session, FarmRunAutopilotConfig config, ItemManager itemManager, Client client,
		TeleportMenuHighlighter menuHighlighter)
	{
		this.session = session;
		this.config = config;
		this.itemManager = itemManager;
		this.client = client;
		this.menuHighlighter = menuHighlighter;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightItems())
		{
			return;
		}
		final Highlights highlights = session.getView().getHighlights();
		final boolean wear = highlights.getWearItemIds().contains(itemId);
		if (!wear && !highlights.getItemIds().contains(itemId))
		{
			return;
		}
		final Color colour = wear ? WEAR_COLOUR : config.itemColour();
		// Stacks change picture with their size (a pile of seeds, a heap of coins), so the outline depends on it
		final int quantity = widgetItem.getQuantity();
		final String key = itemId + ":" + quantity + ":" + wear;
		if (outlineCache.size() > MAX_CACHED_OUTLINES && !outlineCache.containsKey(key))
		{
			outlineCache.clear();
		}
		final BufferedImage outline = outlineCache.computeIfAbsent(key,
			k -> itemManager.getItemOutline(itemId, quantity, colour));
		final Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
		if (wear)
		{
			drawLabel(graphics, bounds, WEAR_LABEL, WEAR_COLOUR);
		}
		else if (needsRightClick(itemId, widgetItem, highlights.getTeleportOption()))
		{
			drawLabel(graphics, bounds, RIGHT_CLICK_LABEL, colour);
		}
	}

	/** Whether the teleport option the leg needs on this item isn't its left click. */
	private boolean needsRightClick(int itemId, WidgetItem widgetItem, String option)
	{
		if (option == null)
		{
			return false;
		}
		final boolean worn = widgetItem.getWidget() != null
			&& WidgetUtil.componentToInterface(widgetItem.getWidget().getId()) == InterfaceID.WORNITEMS;
		final String key = option + ":" + itemId + ":" + worn;
		if (rightClickCache.size() > MAX_CACHED_OUTLINES && !rightClickCache.containsKey(key))
		{
			rightClickCache.clear();
		}
		final Point mouse = client.getMouseCanvasPosition();
		if (!client.isMenuOpen() && mouse != null && widgetItem.getCanvasBounds().contains(mouse.getX(), mouse.getY()))
		{
			// Under the mouse, the game's menu for it says what its left click is, with other plugins' swaps
			final Boolean shown = menuHighlighter.needsRightClick(client.getMenu().getMenuEntries());
			if (shown != null)
			{
				rightClickCache.put(key, shown);
				return shown;
			}
		}
		return rightClickCache.computeIfAbsent(key, k -> TeleportMenuHighlighter.needsRightClick(
			itemManager.getItemComposition(itemId).getInventoryActions(), worn, option));
	}

	/** A label on a dark strip along the bottom of the slot, outlined all round, so it reads on pale items too. */
	private static void drawLabel(Graphics2D graphics, Rectangle bounds, String label, Color colour)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics metrics = graphics.getFontMetrics();
		final int width = metrics.stringWidth(label);
		final int x = (int) bounds.getCenterX() - width / 2;
		final int y = (int) bounds.getMaxY() - 1;
		graphics.setColor(LABEL_BACKGROUND);
		graphics.fillRect(x - 2, y - metrics.getAscent(), width + 4, metrics.getAscent() + 2);
		graphics.setColor(Color.BLACK);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					graphics.drawString(label, x + dx, y + dy);
				}
			}
		}
		graphics.setColor(colour);
		graphics.drawString(label, x, y);
	}

	/** Forget cached outlines (colour changed, plugin stopped). */
	public void clearCache()
	{
		outlineCache.clear();
		rightClickCache.clear();
	}
}
