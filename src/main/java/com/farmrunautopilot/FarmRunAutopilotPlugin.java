package com.farmrunautopilot;

import com.farmrunautopilot.ui.FarmRunAutopilotPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
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
	private ClientToolbar clientToolbar;

	private FarmRunAutopilotPanel panel;
	private NavigationButton navButton;

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
		navButton = null;
		panel = null;
		log.debug("Farm Run Autopilot stopped");
	}

	@Provides
	FarmRunAutopilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmRunAutopilotConfig.class);
	}
}
