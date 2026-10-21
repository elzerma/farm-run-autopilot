package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * While the seed vault or the bank is open for a step of the run: how many more of each seed or sapling to take, or
 * how many to put back, along the bottom of the inventory beside it (elzerma, 2026-10-18: "less to guess about how many
 * more to click"; 2026-10-19: the bank too). They cover the instruction under the player, so this is where the count
 * can be seen. While the step is to deposit everything, the bank's Deposit inventory button is outlined: the growing
 * vine, or a plain outline with vines off (elzerma, 2026-10-20: the plain one was easy to miss). Worked out by
 * {@link RunSession} each tick; only drawn here.
 */
@Slf4j
public class InventoryCountOverlay extends Overlay
{
	private static final Color BACKGROUND = new Color(0, 0, 0, 170);
	private static final BasicStroke OUTLINE = new BasicStroke(2);

	private final Client client;
	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final VineFrame vine = new VineFrame();
	/** The vault's or bank's inventory was missing while there were counts to show: logged once. */
	private boolean loggedMissing;

	@Inject
	InventoryCountOverlay(Client client, RunSession session, FarmRunAutopilotConfig config)
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
		draw(graphics, session.getVaultCounts(), InterfaceID.SeedVaultDeposit.INV, "Seed vault");
		draw(graphics, session.getBankCounts(), InterfaceID.Bankside.ITEMS, "Bank");
		drawDepositButton(graphics);
		return null;
	}

	private void drawDepositButton(Graphics2D graphics)
	{
		final Widget button = session.isDepositAllStep() ? client.getWidget(InterfaceID.Bankmain.DEPOSITINV) : null;
		if (button == null || button.isHidden())
		{
			vine.clear();
			return;
		}
		final Rectangle bounds = button.getBounds();
		if (config.vineHighlights())
		{
			vine.draw(graphics, bounds);
			return;
		}
		vine.clear();
		graphics.setColor(GuidanceOverlay.INSTRUCTION);
		graphics.setStroke(OUTLINE);
		graphics.drawRect(bounds.x - 1, bounds.y - 1, bounds.width + 1, bounds.height + 1);
	}

	private void draw(Graphics2D graphics, List<RunSession.InventoryCount> counts, int inventoryId, String where)
	{
		if (counts.isEmpty())
		{
			return;
		}
		final Widget inventory = client.getWidget(inventoryId);
		if (inventory == null || inventory.isHidden())
		{
			if (!loggedMissing)
			{
				loggedMissing = true;
				log.debug("{} open but its inventory isn't there to show: {}", where, counts);
			}
			return;
		}
		final Rectangle bounds = inventory.getBounds();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics metrics = graphics.getFontMetrics();
		final int lineHeight = metrics.getHeight();
		// Along the bottom, where an inventory is emptiest, the last line on its bottom edge
		int y = bounds.y + bounds.height - metrics.getDescent() - 2 - (counts.size() - 1) * lineHeight;
		for (RunSession.InventoryCount count : counts)
		{
			final String text = count.getText();
			final int width = metrics.stringWidth(text);
			final int x = bounds.x + Math.max(0, (bounds.width - width) / 2);
			graphics.setColor(BACKGROUND);
			graphics.fillRect(x - 3, y - metrics.getAscent() - 1, width + 6, lineHeight);
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, x + 1, y + 1);
			graphics.setColor(count.isDeposit() ? GuidanceOverlay.REMINDER : GuidanceOverlay.INSTRUCTION);
			graphics.drawString(text, x, y);
			y += lineHeight;
		}
	}
}
