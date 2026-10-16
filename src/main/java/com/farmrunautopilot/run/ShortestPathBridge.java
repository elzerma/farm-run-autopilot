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
	private WorldPoint target;

	@Inject
	ShortestPathBridge(EventBus eventBus, FarmRunAutopilotConfig config)
	{
		this.eventBus = eventBus;
		this.config = config;
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

	public void clear()
	{
		if (target != null)
		{
			target = null;
			eventBus.post(new PluginMessage(NAMESPACE, "clear"));
		}
	}
}
