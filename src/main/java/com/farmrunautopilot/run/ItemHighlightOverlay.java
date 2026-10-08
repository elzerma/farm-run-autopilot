package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
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
	private static final int MAX_CACHED_OUTLINES = 100;

	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final ItemManager itemManager;
	/** Outline images, made once per item and stack size rather than every frame; cleared when the colour changes. */
	private final Map<String, BufferedImage> outlineCache = new HashMap<>();

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
		// Stacks change picture with their size (a pile of seeds, a heap of coins), so the outline depends on it
		final int quantity = widgetItem.getQuantity();
		final String key = itemId + ":" + quantity;
		if (outlineCache.size() > MAX_CACHED_OUTLINES && !outlineCache.containsKey(key))
		{
			outlineCache.clear();
		}
		final BufferedImage outline = outlineCache.computeIfAbsent(key,
			k -> itemManager.getItemOutline(itemId, quantity, config.itemColour()));
		final Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
	}

	/** Forget cached outlines (colour changed, plugin stopped). */
	public void clearCache()
	{
		outlineCache.clear();
	}
}
