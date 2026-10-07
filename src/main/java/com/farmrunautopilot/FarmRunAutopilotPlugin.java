package com.farmrunautopilot;

import com.farmrunautopilot.tracking.PatchTracker;
import com.farmrunautopilot.ui.FarmRunAutopilotPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Farm Run Autopilot",
	description = "Plans and guides combined tree, fruit tree and herb farming runs",
	tags = {"farming", "farm", "run", "herb", "tree", "fruit", "route", "planner"}
)
public class FarmRunAutopilotPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PatchTracker patchTracker;

	private FarmRunAutopilotPanel panel;
	private NavigationButton navButton;

	private WorldPoint lastTickLocation;
	private boolean lastTickPostLogin;
	private int lastModalCloseTick;

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(FarmRunAutopilotPanel.class);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Farm Run Autopilot")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		log.debug("Farm Run Autopilot started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		panel.shutDown();
		navButton = null;
		panel = null;
		lastTickLocation = null;
		lastTickPostLogin = false;
		patchTracker.reset();
		log.debug("Farm Run Autopilot stopped");
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		// Patch varbits are only sent after leaving the post-login welcome screen
		final Widget motw = client.getWidget(InterfaceID.WelcomeScreen.MOTW);
		if (motw != null && !motw.isHidden())
		{
			lastTickPostLogin = true;
			return;
		}
		if (lastTickPostLogin)
		{
			lastTickPostLogin = false;
			return;
		}

		final WorldPoint location = lastTickLocation;
		final Player player = client.getLocalPlayer();
		lastTickLocation = player == null ? null : player.getWorldLocation();

		// Skip ticks where the player crossed a region boundary
		if (location == null || lastTickLocation == null || location.getRegionID() != lastTickLocation.getRegionID())
		{
			return;
		}

		if (patchTracker.update(location, client.getTickCount() - lastModalCloseTick))
		{
			final FarmRunAutopilotPanel p = panel;
			SwingUtilities.invokeLater(p::refreshPatches);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getModalMode() != WidgetModalMode.NON_MODAL)
		{
			lastModalCloseTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		patchTracker.reset();
		final FarmRunAutopilotPanel p = panel;
		SwingUtilities.invokeLater(p::refreshPatches);
	}

	@Provides
	FarmRunAutopilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmRunAutopilotConfig.class);
	}
}
