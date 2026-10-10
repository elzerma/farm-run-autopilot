package com.farmrunautopilot.testing;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.run.RunSession;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.run.SceneTracker;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.ItemChargeTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;

/**
 * What a guided test can see while it runs, and what it has found for the report. Client thread while the
 * test runs; read on the Swing thread only once it has finished.
 */
public class TestContext
{
	@Getter
	private final Client client;
	private final AccessChecker accessChecker;
	private final HoldingsTracker holdingsTracker;
	private final ItemChargeTracker itemCharges;
	private final RunSession runSession;
	private final RunService runService;
	@Getter
	private final SceneTracker scene;
	private final PohDetector pohDetector;
	private final SettingsStore settings;

	/** Game messages since the last check. */
	private final List<String> chat = new ArrayList<>();
	/** Values for the report, in the order found. */
	@Getter
	private final Map<String, String> captured = new LinkedHashMap<>();
	@Getter
	private final List<String> problems = new ArrayList<>();
	/** Anything a test wants to remember between steps. */
	private final Map<String, Object> memory = new HashMap<>();

	TestContext(Client client, AccessChecker accessChecker, HoldingsTracker holdingsTracker,
		ItemChargeTracker itemCharges, RunSession runSession, RunService runService, SceneTracker scene,
		PohDetector pohDetector, SettingsStore settings)
	{
		this.client = client;
		this.accessChecker = accessChecker;
		this.holdingsTracker = holdingsTracker;
		this.itemCharges = itemCharges;
		this.runSession = runSession;
		this.runService = runService;
		this.scene = scene;
		this.pohDetector = pohDetector;
		this.settings = settings;
	}

	void addChat(String message)
	{
		chat.add(message);
	}

	void clearChat()
	{
		chat.clear();
	}

	/** Game messages since the last check. */
	public List<String> chat()
	{
		return Collections.unmodifiableList(chat);
	}

	public void capture(String what, Object value)
	{
		captured.put(what, String.valueOf(value));
	}

	public void problem(String text)
	{
		problems.add(text);
	}

	public void remember(String key, Object value)
	{
		memory.put(key, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T recall(String key)
	{
		return (T) memory.get(key);
	}

	public AccessSnapshot access()
	{
		return accessChecker.getSnapshot();
	}

	public Holdings holdings()
	{
		return holdingsTracker.getHoldings();
	}

	/** In the inventory or worn. */
	public boolean carries(int[] itemIds)
	{
		return holdings().carriedOnly().countAny(itemIds) > 0;
	}

	/** The plugin's count of an item's charges, or null if unknown. */
	public Integer charges(TravelItem item)
	{
		return itemCharges.getCharges().get(item);
	}

	public int varbit(int varbitId)
	{
		return client.getVarbitValue(varbitId);
	}

	public RunView runView()
	{
		return runSession.getView();
	}

	public RunPlan plan()
	{
		return runService.getPlan();
	}

	/** Ticked or detected in the plugin's settings right now. */
	public boolean hasUnlock(Unlock unlock)
	{
		return settings.getAccount().getManualUnlocks().contains(unlock) || access().getUnlocks().contains(unlock);
	}

	public boolean inOwnHouse()
	{
		return pohDetector.isScanningHouse();
	}

	public boolean visitingHouse()
	{
		return client.isInInstancedRegion() && pohDetector.isVisiting();
	}

	public String houseSummary()
	{
		return String.valueOf(settings.getAccount().getPoh());
	}

	public WorldPoint location()
	{
		final Player player = client.getLocalPlayer();
		return player != null ? player.getWorldLocation() : null;
	}

	public int region()
	{
		final WorldPoint location = location();
		return location != null ? location.getRegionID() : -1;
	}

	public boolean inRegion(Integer... regions)
	{
		return Arrays.asList(regions).contains(region());
	}

	/**
	 * Loaded objects whose name contains this, with their ID, place and options. Looks at the whole scene, so
	 * only call it once, when a step needs it for the report.
	 */
	public String objectsNamed(String name)
	{
		final List<String> found = new ArrayList<>();
		final Scene scene = client.getTopLevelWorldView().getScene();
		final Tile[][][] tiles = scene.getTiles();
		final int plane = client.getTopLevelWorldView().getPlane();
		for (Tile[] column : tiles[plane])
		{
			for (Tile tile : column)
			{
				if (tile == null || tile.getGameObjects() == null)
				{
					continue;
				}
				for (GameObject object : tile.getGameObjects())
				{
					if (object == null || found.size() >= 10)
					{
						continue;
					}
					ObjectComposition composition = client.getObjectDefinition(object.getId());
					if (composition != null && composition.getImpostorIds() != null)
					{
						composition = composition.getImpostor();
					}
					if (composition != null && composition.getName() != null
						&& composition.getName().toLowerCase().contains(name.toLowerCase()))
					{
						found.add(composition.getName() + " #" + object.getId() + " at " + object.getWorldLocation()
							+ " " + Arrays.toString(composition.getActions()));
					}
				}
			}
		}
		return found.isEmpty() ? "none" : String.join("; ", found);
	}

	/** Loaded NPCs whose name contains this, with their ID and place. */
	public String npcsNamed(String name)
	{
		final List<String> found = new ArrayList<>();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc.getName() != null && npc.getName().toLowerCase().contains(name.toLowerCase()) && found.size() < 10)
			{
				found.add(npc.getName() + " #" + npc.getId() + " at " + npc.getWorldLocation());
			}
		}
		return found.isEmpty() ? "none" : String.join("; ", found);
	}
}
