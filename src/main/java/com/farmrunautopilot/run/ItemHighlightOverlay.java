package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the item to use next in the inventory and equipment: seed, compost, tool, payment or teleport
 * (SPEC 13.4).
 */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final ItemManager itemManager;
	/** Outline images, made once per item and colour rather than every frame. */
	private final Map<Long, BufferedImage> outlineCache = new HashMap<>();

	@Inject
	ItemHighlightOverlay(RunSession session, FarmRunAutopilotConfig config, ItemManager itemManager)
	{
		this.session = session;
		this.config = config;
		this.itemManager = itemManager;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightItems() || !session.getView().getHighlights().getItemIds().contains(itemId))
		{
			return;
		}
		final Color colour = config.itemColour();
		final long key = ((long) itemId << 32) | (colour.getRGB() & 0xFFFFFFFFL);
		final BufferedImage outline = outlineCache.computeIfAbsent(key,
			k -> itemManager.getItemOutline(itemId, widgetItem.getQuantity(), colour));
		final Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
	}

	/** Forget cached outlines (colour changed, plugin stopped). */
	public void clearCache()
	{
		outlineCache.clear();
	}
}
