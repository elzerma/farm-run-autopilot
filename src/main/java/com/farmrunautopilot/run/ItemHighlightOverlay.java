package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the item to use next in the inventory and equipment: seed, compost, tool, payment or teleport
 * (SPEC 13.4). Items to put on get their own colour and a "Wear" label, so an item to wear is never mistaken for
 * the next teleport.
 */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private static final int MAX_CACHED_OUTLINES = 100;
	/**
	 * How far inside the slot the vine is given its bounds: it leaves 3 px round a button, but round an item it sits
	 * 1 px outside the slot, so its leaves don't reach into the next slots.
	 */
	private static final int VINE_INSET = 2;
	/** Items to put on: a colour no other highlight uses. */
	static final Color WEAR_COLOUR = new Color(0x40, 0xC8, 0xFF);
	private static final String WEAR_LABEL = "Wear";
	private static final Color LABEL_BACKGROUND = new Color(0, 0, 0, 170);

	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final ItemManager itemManager;
	/** Each item's vine in each interface. */
	private final Map<String, VineFrame> vines = new HashMap<>();
	/** Items given their vine in the interface being drawn. */
	private final Set<Integer> vined = new HashSet<>();
	/**
	 * Outline images, made once per item, stack size and colour rather than every frame; cleared when the colour
	 * changes.
	 */
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

	/**
	 * Drawn once for each interface showing items (the inventory, worn equipment, the bank's inventory), so the vine
	 * goes round the first slot in each holding an item to use (elzerma, 2026-10-20: the item with a vine, as the spell).
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		vined.clear();
		return super.render(graphics);
	}

	/** The vine round this item in this interface, kept from frame to frame for its falling petals. */
	private VineFrame vineFor(int itemId, WidgetItem widgetItem)
	{
		final String key = (widgetItem.getWidget() != null ? widgetItem.getWidget().getId() : -1) + ":" + itemId;
		if (vines.size() > MAX_CACHED_OUTLINES && !vines.containsKey(key))
		{
			vines.clear();
		}
		return vines.computeIfAbsent(key, k -> new VineFrame());
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
		final Rectangle bounds = widgetItem.getCanvasBounds();
		if (!wear && config.vineHighlights())
		{
			// Only round the first slot holding it, so a few buckets of compost aren't a tangle of vines
			if (vined.add(itemId))
			{
				vineFor(itemId, widgetItem).draw(graphics, new Rectangle(bounds.x + VINE_INSET, bounds.y + VINE_INSET,
					bounds.width - 2 * VINE_INSET, bounds.height - 2 * VINE_INSET));
			}
		}
		else
		{
			// Stacks change picture with their size (a pile of seeds, a heap of coins), so the outline depends on it
			final int quantity = widgetItem.getQuantity();
			final String key = itemId + ":" + quantity + ":" + wear;
			if (outlineCache.size() > MAX_CACHED_OUTLINES && !outlineCache.containsKey(key))
			{
				outlineCache.clear();
			}
			final BufferedImage outline = outlineCache.computeIfAbsent(key,
				k -> itemManager.getItemOutline(itemId, quantity, colour));
			graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
		}
		if (wear)
		{
			drawLabel(graphics, bounds);
		}
	}

	/** "Wear" on a dark strip along the bottom of the slot, outlined all round, so it reads on pale items too. */
	private static void drawLabel(Graphics2D graphics, Rectangle bounds)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics metrics = graphics.getFontMetrics();
		final int width = metrics.stringWidth(WEAR_LABEL);
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
					graphics.drawString(WEAR_LABEL, x + dx, y + dy);
				}
			}
		}
		graphics.setColor(WEAR_COLOUR);
		graphics.drawString(WEAR_LABEL, x, y);
	}

	/** Forget cached outlines (colour changed, plugin stopped). */
	public void clearCache()
	{
		outlineCache.clear();
	}
}
