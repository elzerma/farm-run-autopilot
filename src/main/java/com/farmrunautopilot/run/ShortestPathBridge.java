package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.util.Collections;
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
 * Asks the Shortest Path plugin (Plugin Hub) to draw the walk for our "run to ..." steps. Uses its public plugin
 * message API ("shortestpath" / "path" with a "target" point and a "config" map of overrides, and "clear"). The
 * overrides switch off every teleport, jewellery, transport, house and bank option for our path only, so it
 * draws just the walk; our own steps cover the travel. The overrides last until "clear", which also puts the
 * player's own Shortest Path settings back. Nothing happens if Shortest Path isn't installed or the "Walking lines"
 * setting is off. Client thread.
 */
@Singleton
public class ShortestPathBridge
{
	private static final String NAMESPACE = "shortestpath";
	/** Its name in the plugin list. */
	private static final String PLUGIN_NAME = "Shortest Path";
	/** Walking only: agility shortcuts and doors are left as the player has them. */
	static final Map<String, Object> WALK_ONLY;

	static
	{
		final Map<String, Object> config = new HashMap<>();
		// Matched exactly against its TeleportationItem type names ("None", not "NONE"). Anything else is ignored
		// and the player's own setting is used, which sent elzerma to a bank for a teleport item (2026-10-11). Key
		// names checked against Shortest Path's ShortestPathConfig on 2026-10-11 and again at commit 7e81a40 (2026-10-13,
		// ShortestPathBridgeTest pins the list).
		config.put("useTeleportationItems", "None");
		for (String key : new String[]{"useTeleportationSpells", "useTeleportationSpellsHome", "useFairyRings",
			"useSpiritTrees", "useGnomeGliders", "useQuetzals", "useBoats", "useCanoes", "useCharterShips", "useShips",
			"useHotAirBalloons", "useMagicCarpets", "useMagicMushtrees", "useMinecarts", "useSeasonalTransports",
			"useTeleportationLevers", "useTeleportationMinigames", "useTeleportationPortals", "useWildernessObelisks",
			"usePoh", "usePohFairyRing", "usePohSpiritTree", "usePohObelisk", "useTeleportationPortalsPoh",
			"usePohMountedItems", "useSailingMoves", "useGrappleShortcuts",
			"includeBankPath", "showBankPickupInfo", "highlightBankPickupItems", "highlightInventoryItems",
			"highlightSpellbookSpells"})
		{
			config.put(key, false);
		}
		WALK_ONLY = Collections.unmodifiableMap(config);
	}

	private final EventBus eventBus;
	private final FarmRunAutopilotConfig config;
	private final PluginManager pluginManager;
	private WorldPoint target;

	@Inject
	ShortestPathBridge(EventBus eventBus, FarmRunAutopilotConfig config, PluginManager pluginManager)
	{
		this.eventBus = eventBus;
		this.config = config;
		this.pluginManager = pluginManager;
	}

	/**
	 * Our walking lines are on and Shortest Path is there to draw them, so its line shows the way and our steps needn't
	 * spell it out (elzerma, 2026-10-18: "Follow line to patch").
	 */
	public boolean linesShown()
	{
		if (!config.shortestPathLines())
		{
			return false;
		}
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginEnabled(plugin))
			{
				return true;
			}
		}
		return false;
	}

	/** Draw the walk to {@code point}, or clear it for null. Only sends a message when the target changes. */
	public void setTarget(WorldPoint point)
	{
		if (point == null || !config.shortestPathLines())
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
		data.put("config", WALK_ONLY);
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
	}

	/** One of our walking lines is drawn, so Shortest Path is set to walking only until it's cleared. */
	public boolean isDrawing()
	{
		return target != null;
	}

	/**
	 * The player was carried somewhere (a teleport, a boat): the same target is sent again, as a walk-only line asked
	 * for from the other side of the sea has no path and isn't drawn once they land (elzerma, 2026-10-26).
	 */
	public void moved()
	{
		final WorldPoint again = target;
		if (again != null)
		{
			target = null;
			setTarget(again);
		}
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
