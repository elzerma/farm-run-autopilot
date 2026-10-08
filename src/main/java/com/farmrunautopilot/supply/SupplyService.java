package com.farmrunautopilot.supply;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.tracking.PatchTracker;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemManager;

/**
 * Recalculates the supply plan on the client thread, at most once per game tick, when something it
 * depends on changes (SPEC 11: "debounce to once per tick").
 */
@Singleton
public class SupplyService
{
	/** Patches become due as they grow, so recalculate about once a minute regardless. */
	private static final int REFRESH_TICKS = 100;

	private final Client client;
	private final ItemManager itemManager;
	private final SettingsStore settings;
	private final AccessChecker accessChecker;
	private final PatchTracker patchTracker;
	private final HoldingsTracker holdingsTracker;
	/** Item names are fixed, so look each up once. */
	private final Map<Integer, String> names = new HashMap<>();

	private volatile boolean dirty = true;
	/** Null until the first plan after login, so that one is always published. */
	private volatile SupplyPlan plan;
	private int ticksSinceRefresh;

	@Inject
	SupplyService(Client client, ItemManager itemManager, SettingsStore settings, AccessChecker accessChecker,
		PatchTracker patchTracker, HoldingsTracker holdingsTracker)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.settings = settings;
		this.accessChecker = accessChecker;
		this.patchTracker = patchTracker;
		this.holdingsTracker = holdingsTracker;
	}

	public SupplyPlan getPlan()
	{
		final SupplyPlan current = plan;
		return current != null ? current : SupplyPlan.EMPTY;
	}

	/** Forget the last plan (login, account switch) so the next one is always published. Any thread. */
	public void reset()
	{
		plan = null;
		dirty = true;
	}

	/** Recalculate on the next game tick. Any thread. */
	public void markDirty()
	{
		dirty = true;
	}

	/**
	 * @return whether a new plan was made
	 */
	public boolean onGameTick()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		final boolean holdingsChanged = holdingsTracker.rebuildIfDirty();
		if (!holdingsChanged && !dirty && ++ticksSinceRefresh < REFRESH_TICKS)
		{
			return false;
		}
		dirty = false;
		ticksSinceRefresh = 0;

		final SupplyPlan next = SupplyCalculator.calculate(settings.getRunConfig(), accessChecker.getSnapshot(),
			holdingsTracker.getHoldings(), patchTracker::predict, Instant.now().getEpochSecond(),
			settings.getRunConfig().isSupplyFullRun(), this::itemName, itemManager::getItemPrice);
		if (next.equals(plan))
		{
			return false;
		}
		plan = next;
		return true;
	}

	private String itemName(int itemId)
	{
		return names.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).getName());
	}
}
