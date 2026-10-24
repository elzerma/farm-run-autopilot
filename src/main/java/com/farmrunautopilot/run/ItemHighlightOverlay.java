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
 * (elzerma, 2026-10-20: obvious when a right-click is needed), on two lines in an equipment slot, which is too narrow
 * for one (elzerma, 2026-10-21); its menu then colours the option (TeleportMenuHighlighter).
 */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private static final int MAX_CACHED_OUTLINES = 100;
	/**
	 * How long the vine waits on a slot whose item is used up before going to the next slot holding it: two game ticks,
	 * by when the step has moved on (elzerma, 2026-10-21: using one bucket of compost, it jumped to the next for a moment).
	 */
	static final long MOVE_AFTER_MILLIS = 1200;
	/** Items to put on: a colour no other highlight uses. */
	static final Color WEAR_COLOUR = new Color(0x40, 0xC8, 0xFF);
	private static final String[] WEAR_LABEL = {"Wear"};
	private static final String[] RIGHT_CLICK_LABEL = {"Right-click"};
	private static final String[] RIGHT_CLICK_WORN_LABEL = {"Right", "click"};
	private static final Color LABEL_BACKGROUND = new Color(0, 0, 0, 170);

	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final ItemManager itemManager;
	/** Each item's vine in each interface. */
	private final Map<String, ItemVine> vines = new HashMap<>();
	/** Items given their vine in the interface being drawn. */
	private final Set<Integer> vined = new HashSet<>();
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

	/** An item's vine in one interface: the slot it's on and when it was last drawn there. */
	private static final class ItemVine
	{
		final VineFrame frame = new VineFrame();
		int slot = -1;
		long seen;
	}

	/**
	 * Drawn once for each interface showing items (the inventory, worn equipment, the bank's inventory), so each item to
	 * use gets one vine in each (elzerma, 2026-10-20: the item with a vine, as the spell).
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		vined.clear();
		return super.render(graphics);
	}

	/**
	 * The vine inside one slot holding this item: the first, so a few buckets of compost aren't a tangle, and then the
	 * same slot while it holds the item.
	 */
	private void drawVine(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		final String key = (widgetItem.getWidget() != null ? widgetItem.getWidget().getId() : -1) + ":" + itemId;
		if (vines.size() > MAX_CACHED_OUTLINES && !vines.containsKey(key))
		{
			vines.clear();
		}
		final ItemVine vine = vines.computeIfAbsent(key, k -> new ItemVine());
		final int slot = widgetItem.getWidget() != null ? widgetItem.getWidget().getIndex() : -1;
		final long now = System.currentTimeMillis();
		if (vined.contains(itemId) || !goesOn(slot, vine.slot, vine.seen, now))
		{
			return;
		}
		vined.add(itemId);
		vine.slot = slot;
		vine.seen = now;
		vine.frame.drawInside(graphics, widgetItem.getCanvasBounds());
	}

	/**
	 * Whether the vine, last drawn on slot {@code current} at {@code seen}, goes on {@code slot} now: always on its own
	 * slot, and on another only once its own has gone {@link #MOVE_AFTER_MILLIS} without the item.
	 */
	static boolean goesOn(int slot, int current, long seen, long now)
	{
		return slot == current || now - seen >= MOVE_AFTER_MILLIS;
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
			drawVine(graphics, itemId, widgetItem);
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
			drawLabel(graphics, bounds, WEAR_LABEL, WEAR_COLOUR);
		}
		else if (needsRightClick(itemId, widgetItem, highlights.getTeleportOption()))
		{
			drawLabel(graphics, bounds, worn(widgetItem) ? RIGHT_CLICK_WORN_LABEL : RIGHT_CLICK_LABEL, colour);
		}
	}

	/** Whether the item is in the Worn Equipment tab rather than the inventory. */
	private static boolean worn(WidgetItem widgetItem)
	{
		return widgetItem.getWidget() != null
			&& WidgetUtil.componentToInterface(widgetItem.getWidget().getId()) == InterfaceID.WORNITEMS;
	}

	/** Whether the teleport option the leg needs on this item isn't its left click. */
	private boolean needsRightClick(int itemId, WidgetItem widgetItem, String option)
	{
		if (option == null)
		{
			return false;
		}
		final boolean worn = worn(widgetItem);
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

	/**
	 * A label on a dark strip along the bottom of the slot, its last line at the bottom, outlined all round so it reads
	 * on pale items too. Kept inside what RuneLite lets the slot draw on (an equipment slot clips to itself), so a wide
	 * one isn't cut off at the edge of the inventory.
	 */
	private static void drawLabel(Graphics2D graphics, Rectangle bounds, String[] lines, Color colour)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics metrics = graphics.getFontMetrics();
		final Rectangle clip = graphics.getClipBounds();
		final int[] xs = new int[lines.length];
		final int[] ys = new int[lines.length];
		graphics.setColor(LABEL_BACKGROUND);
		for (int i = 0; i < lines.length; i++)
		{
			final int up = lines.length - 1 - i;
			final int width = metrics.stringWidth(lines[i]);
			int x = (int) bounds.getCenterX() - width / 2;
			if (clip != null)
			{
				x = Math.max(clip.x + 2, Math.min(x, clip.x + clip.width - width - 2));
			}
			xs[i] = x;
			ys[i] = (int) bounds.getMaxY() - 1 - up * metrics.getHeight();
			// Each line's strip meets the one below without overlapping, so the dark doesn't double up between them
			graphics.fillRect(x - 2, ys[i] - metrics.getAscent(), width + 4,
				up == 0 ? metrics.getAscent() + 2 : metrics.getHeight());
		}
		for (int i = 0; i < lines.length; i++)
		{
			graphics.setColor(Color.BLACK);
			for (int dx = -1; dx <= 1; dx++)
			{
				for (int dy = -1; dy <= 1; dy++)
				{
					if (dx != 0 || dy != 0)
					{
						graphics.drawString(lines[i], xs[i] + dx, ys[i] + dy);
					}
				}
			}
			graphics.setColor(colour);
			graphics.drawString(lines[i], xs[i], ys[i]);
		}
	}

	/** Forget cached outlines (colour changed, plugin stopped). */
	public void clearCache()
	{
		outlineCache.clear();
		rightClickCache.clear();
	}
}
