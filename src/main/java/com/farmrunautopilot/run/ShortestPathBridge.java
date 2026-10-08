package com.farmrunautopilot.run;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/**
 * Asks the Shortest Path plugin (Plugin Hub) to show the way to the run's first stop, from wherever the
 * player starts (Sean's call: our own route rules take over from the first stop on). Uses its public
 * plugin message API ("shortestpath" / "path" with a "target" point, and "clear"), so Shortest Path's own
 * settings decide the path. Does nothing visible if Shortest Path isn't installed.
 */
@Singleton
public class ShortestPathBridge
{
	private static final String NAMESPACE = "shortestpath";
	private static final String PLUGIN_NAME = "Shortest Path";

	private final EventBus eventBus;
	private final PluginManager pluginManager;
	private WorldPoint target;

	@Inject
	ShortestPathBridge(EventBus eventBus, PluginManager pluginManager)
	{
		this.eventBus = eventBus;
		this.pluginManager = pluginManager;
	}

	/** Whether Shortest Path is installed and switched on. */
	public boolean isAvailable()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginEnabled(plugin))
			{
				return true;
			}
		}
		return false;
	}

	/** Point Shortest Path at {@code point}; only sends a message when the target changes. */
	public void setTarget(WorldPoint point)
	{
		if (point == null)
		{
			clear();
			return;
		}
		if (Objects.equals(point, target))
		{
			return;
		}
		target = point;
		final Map<String, Object> data = new HashMap<>();
		data.put("target", point);
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
	}

	public void clear()
	{
		if (target != null)
		{
			target = null;
			eventBus.post(new PluginMessage(NAMESPACE, "clear"));
		}
	}
}
