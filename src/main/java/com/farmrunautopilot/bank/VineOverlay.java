package com.farmrunautopilot.bank;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.run.VineFrame;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The vine round the bank's Farm run button while the guide is waiting for it to be clicked (issue #5), or round the
 * seed vault's Farm Run Autopilot button the same way (issue #8). With the plain highlight style the buttons only
 * flash.
 */
public class VineOverlay extends Overlay
{
	private final FarmBankTabButton button;
	private final SeedVaultSection seedVault;
	private final FarmRunAutopilotConfig config;
	private final VineFrame vine = new VineFrame();

	@Inject
	VineOverlay(FarmBankTabButton button, SeedVaultSection seedVault, FarmRunAutopilotConfig config)
	{
		this.button = button;
		this.seedVault = seedVault;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// The bank and the seed vault are never open together, so it's one or the other
		final Rectangle bank = button.callingBounds();
		final Rectangle bounds = bank != null ? bank : seedVault.callingBounds();
		if (bounds == null || !config.vineHighlights())
		{
			vine.clear();
			return null;
		}
		vine.draw(graphics, bounds);
		return null;
	}
}
