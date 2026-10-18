package com.farmrunautopilot.run;

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
 * While the seed vault is open for a step of the run: how many more of each seed or sapling to take, or how many to
 * put back, along the bottom of the inventory beside it (elzerma, 2026-10-18: "less to guess about how many more to
 * click"). The vault covers the instruction under the player, so this is where the count can be seen. Worked out by
 * {@link RunSession} each tick; only drawn here.
 */
@Slf4j
public class SeedVaultCountOverlay extends Overlay
{
	private static final Color BACKGROUND = new Color(0, 0, 0, 170);

	private final Client client;
	private final RunSession session;
	/** The vault's inventory was missing while there were counts to show: logged once. */
	private boolean loggedMissing;

	@Inject
	SeedVaultCountOverlay(Client client, RunSession session)
	{
		this.client = client;
		this.session = session;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final List<RunSession.VaultCount> counts = session.getVaultCounts();
		if (counts.isEmpty())
		{
			return null;
		}
		final Widget inventory = client.getWidget(InterfaceID.SeedVaultDeposit.INV);
		if (inventory == null || inventory.isHidden())
		{
			if (!loggedMissing)
			{
				loggedMissing = true;
				log.debug("Seed vault open but its inventory isn't there to show: {}", counts);
			}
			return null;
		}
		final Rectangle bounds = inventory.getBounds();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		final FontMetrics metrics = graphics.getFontMetrics();
		final int lineHeight = metrics.getHeight();
		// Along the bottom, where an inventory is emptiest, the last line on its bottom edge
		int y = bounds.y + bounds.height - metrics.getDescent() - 2 - (counts.size() - 1) * lineHeight;
		for (RunSession.VaultCount count : counts)
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
		return null;
	}
}
