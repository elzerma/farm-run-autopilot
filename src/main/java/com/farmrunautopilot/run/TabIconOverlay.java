package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The vine (or plain outline) round a side-panel tab's icon when the step needs that tab and it isn't open (elzerma,
 * 2026-10-20): the inventory for a tablet or scroll, worn equipment for jewellery or a cape to operate, the spellbook
 * for a spell. Gone once the tab is open, where {@link ItemHighlightOverlay} and {@link SpellHighlightOverlay} take
 * over. Drawing only: the tab is clicked as normal.
 */
public class TabIconOverlay extends Overlay
{
	private final Client client;
	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final VineFrame vine = new VineFrame();

	@Inject
	TabIconOverlay(Client client, RunSession session, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.session = session;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Widget icon = iconToHighlight();
		if (icon == null)
		{
			vine.clear();
			return null;
		}
		final Rectangle bounds = icon.getBounds();
		if (config.vineHighlights())
		{
			vine.draw(graphics, bounds);
			return null;
		}
		graphics.setColor(config.itemColour());
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		return null;
	}

	/** The icon of the tab the step needs, while it isn't open and nothing covers the tabs; else null. */
	private Widget iconToHighlight()
	{
		final Highlights highlights = session.getView().getHighlights();
		if (highlights.getSpell() == null && highlights.getItemIds().isEmpty() && highlights.getWearItemIds().isEmpty())
		{
			return null;
		}
		final ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		final ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		final SideTab tab = SideTab.needed(highlights, id -> inventory != null && inventory.contains(id),
			id -> worn != null && worn.contains(id), config.highlightItems(), config.highlightSpell());
		// In the bank and the seed vault the inventory is beside them, and the bank's own highlights lead
		if (tab == null || tab.isOpen(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)) || isVisible(
			InterfaceID.Bankmain.UNIVERSE) || isVisible(InterfaceID.SeedVault.UNIVERSE))
		{
			return null;
		}
		return iconOf(tab);
	}

	/** The tab's icon in the layout in use, or null if none is shown. */
	private Widget iconOf(SideTab tab)
	{
		for (int id : tab.getIcons())
		{
			final Widget icon = client.getWidget(id);
			if (icon != null && !icon.isHidden())
			{
				return icon;
			}
		}
		return null;
	}

	private boolean isVisible(int widgetId)
	{
		final Widget widget = client.getWidget(widgetId);
		return widget != null && !widget.isHidden();
	}
}
