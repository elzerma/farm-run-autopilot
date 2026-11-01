package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Outlines the quantity button to click in the tool leprechaun's window, "Click": 5 for the fives, 1 for the ones, and 5
 * again to finish, his default (elzerma, 2026-11-01: "13 would change to 5, 2x click bucket remove, change to 1, 3x click
 * bucket remove. change back to 5"). The game sets the quantity the moment the button's clicked, so the outline moves on
 * to the items at once.
 */
public class LeprechaunButtonOverlay extends Overlay
{
	private static final String[] CLICK_LABEL = {"Click"};

	private final Client client;
	private final LeprechaunClicks clicks;
	private final FarmRunAutopilotConfig config;
	private final VineFrame vine = new VineFrame();

	@Inject
	LeprechaunButtonOverlay(Client client, LeprechaunClicks clicks, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.clicks = clicks;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Widget button = buttonToClick();
		if (button == null)
		{
			vine.clear();
			return null;
		}
		final Rectangle bounds = button.getBounds();
		if (config.vineHighlights())
		{
			vine.draw(graphics, bounds);
		}
		else
		{
			graphics.setColor(config.itemColour());
			graphics.setStroke(new BasicStroke(2));
			graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}
		ItemHighlightOverlay.drawLabel(graphics, bounds, CLICK_LABEL, config.itemColour());
		return null;
	}

	/** His quantity button to click first, while his window is open; else null. */
	private Widget buttonToClick()
	{
		if (!config.highlightItems() || !isVisible(InterfaceID.FarmingTools.UNIVERSE))
		{
			return null;
		}
		final LeprechaunClicks.Next next = clicks.now();
		if (!next.switchFirst)
		{
			return null;
		}
		final Widget button = client.getWidget(next.target == LeprechaunClicks.Quantity.FIVE
			? InterfaceID.FarmingTools.QUANTITY_5 : InterfaceID.FarmingTools.QUANTITY_1);
		return button != null && !button.isHidden() ? button : null;
	}

	private boolean isVisible(int widgetId)
	{
		final Widget widget = client.getWidget(widgetId);
		return widget != null && !widget.isHidden();
	}
}
