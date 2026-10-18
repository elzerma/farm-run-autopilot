package com.farmrunautopilot.supply;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.DataConstants;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.DailyLimits;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.farmrunautopilot.settings.Compost;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarbitComposition;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * Keeps {@link Holdings} up to date (SPEC 11, 14). Inventory, equipment, rune pouch and leprechaun are
 * read live; the bank, group storage and seed vault are cached per account when their windows open,
 * because the game only sends them then. All methods except {@link #getHoldings()} run on the client
 * thread.
 */
@Slf4j
@Singleton
public class HoldingsTracker
{
	private static final String BANK_KEY = "cache.bank";
	private static final String GROUP_STORAGE_KEY = "cache.groupStorage";
	private static final String SEED_VAULT_KEY = "cache.seedVault";
	/** FARMING_BLOCKWEEDS value when Auto-weed is owned and switched on. */
	private static final int AUTOWEED_ON = 2;

	private static final int[] POUCH_TYPE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] POUCH_AMOUNT_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};

	/** Varbits whose changes affect holdings. */
	private static final Set<Integer> WATCHED_VARBITS = new HashSet<>();

	static
	{
		for (int varbit : POUCH_TYPE_VARBITS)
		{
			WATCHED_VARBITS.add(varbit);
		}
		for (int varbit : POUCH_AMOUNT_VARBITS)
		{
			WATCHED_VARBITS.add(varbit);
		}
		for (LeprechaunItem item : LeprechaunItem.values())
		{
			WATCHED_VARBITS.add(item.getBaseVarbit());
			if (item.getExtraVarbit() != DataConstants.NONE)
			{
				WATCHED_VARBITS.add(item.getExtraVarbit());
			}
		}
		WATCHED_VARBITS.add(VarbitID.FARMING_BLOCKWEEDS);
		WATCHED_VARBITS.add(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
		WATCHED_VARBITS.add(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY);
		WATCHED_VARBITS.addAll(DailyLimits.usedTodayVarbits().values());
		WATCHED_VARBITS.addAll(DailyLimits.usedTodayHighBits().values());
	}

	private final Client client;
	private final ConfigManager configManager;
	private final Gson gson;
	private final ItemManager itemManager;
	private final SettingsStore settings;
	private final BottomlessBucketTracker bucket;
	private final ItemChargeTracker itemCharges;
	private final DailyUseTracker dailyUses;
	/** The last difference between a daily teleport's game value and chat count that was logged. */
	private String loggedDaily;
	/** The last split daily count that was logged, with where its parts sit. */
	private String loggedLayout;

	private Map<Integer, Integer> bank;
	private Map<Integer, Integer> groupStorage;
	private Map<Integer, Integer> seedVault;
	private boolean dirty = true;
	private volatile Holdings holdings = Holdings.EMPTY;

	@Inject
	HoldingsTracker(Client client, ConfigManager configManager, Gson gson, ItemManager itemManager,
		SettingsStore settings, BottomlessBucketTracker bucket, ItemChargeTracker itemCharges,
		DailyUseTracker dailyUses)
	{
		this.client = client;
		this.configManager = configManager;
		this.gson = gson;
		this.itemManager = itemManager;
		this.settings = settings;
		this.bucket = bucket;
		this.itemCharges = itemCharges;
		this.dailyUses = dailyUses;
	}

	public Holdings getHoldings()
	{
		return holdings;
	}

	/** The seed vault has been opened on this account, so its contents are known. */
	public boolean hasSeenSeedVault()
	{
		return seedVault != null;
	}

	/** Reload the cached containers for the current account (startup, account switch). */
	public void loadCaches()
	{
		bank = readCache(BANK_KEY);
		groupStorage = readCache(GROUP_STORAGE_KEY);
		seedVault = readCache(SEED_VAULT_KEY);
		dirty = true;
	}

	public void markDirty()
	{
		dirty = true;
	}

	public void onItemContainerChanged(int containerId, ItemContainer container)
	{
		switch (containerId)
		{
			case InventoryID.INV:
			case InventoryID.WORN:
				dirty = true;
				break;
			case InventoryID.BANK:
				bank = writeCache(BANK_KEY, container);
				dirty = true;
				break;
			case InventoryID.SEED_VAULT:
				seedVault = writeCache(SEED_VAULT_KEY, container);
				dirty = true;
				break;
			case InventoryID.INV_GROUP_TEMP:
				// Only save group storage once the player's edits are committed (as Quest Helper does).
				if (settings.getRunConfig().isUseGroupStorage()
					&& client.getVarbitValue(VarbitID.GIM_SHARED_BANK_HASEDITED) == 0)
				{
					groupStorage = writeCache(GROUP_STORAGE_KEY, container);
					dirty = true;
				}
				break;
			default:
				break;
		}
	}

	public void onVarbitChanged(int varbitId)
	{
		if (WATCHED_VARBITS.contains(varbitId))
		{
			dirty = true;
		}
	}

	/**
	 * Rebuilds the holdings if anything changed. Call once per game tick.
	 *
	 * @return whether the holdings changed
	 */
	public boolean rebuildIfDirty()
	{
		if (!dirty)
		{
			return false;
		}
		dirty = false;

		final RunConfig config = settings.getRunConfig();
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		final Map<Integer, Integer> inventory = read(client.getItemContainer(InventoryID.INV));
		final Map<Integer, Integer> worn = read(client.getItemContainer(InventoryID.WORN));
		items.put(Holdings.Source.INVENTORY, inventory);
		items.put(Holdings.Source.WORN, worn);
		if (bank != null)
		{
			items.put(Holdings.Source.BANK, bank);
		}
		items.put(Holdings.Source.LEPRECHAUN, readLeprechaun());
		if (config.isUseGroupStorage() && groupStorage != null)
		{
			items.put(Holdings.Source.GROUP_STORAGE, groupStorage);
		}
		if (config.isUseSeedVault() && seedVault != null)
		{
			items.put(Holdings.Source.SEED_VAULT, seedVault);
		}

		final Set<Rune> infinite = EnumSet.noneOf(Rune.class);
		for (Integer id : worn.keySet())
		{
			final Set<Rune> runes = SupplyItems.INFINITE_RUNE_ITEMS.get(id);
			if (runes != null)
			{
				infinite.addAll(runes);
			}
		}

		final Holdings next = new Holdings(Collections.unmodifiableMap(items), readRunePouch(inventory),
			Collections.unmodifiableSet(infinite), bank != null,
			client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS) == AUTOWEED_ON,
			bucket.getUses(), bucket.getCompost(), readUsedToday(), itemCharges.getCharges());
		if (next.equals(holdings))
		{
			return false;
		}
		holdings = next;
		return true;
	}

	private Map<Integer, Integer> read(ItemContainer container)
	{
		final Map<Integer, Integer> counts = new HashMap<>();
		if (container == null)
		{
			return counts;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0)
			{
				// Noted items and placeholders count as the real item.
				counts.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
			}
		}
		return counts;
	}

	private Map<Integer, Integer> readRunePouch(Map<Integer, Integer> inventory)
	{
		boolean hasPouch = false;
		for (int pouch : SupplyItems.RUNE_POUCHES)
		{
			hasPouch |= inventory.containsKey(pouch);
		}
		if (!hasPouch)
		{
			return Collections.emptyMap();
		}

		final EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		final Map<Integer, Integer> runes = new HashMap<>();
		for (int i = 0; i < POUCH_TYPE_VARBITS.length; i++)
		{
			final int type = client.getVarbitValue(POUCH_TYPE_VARBITS[i]);
			final int amount = client.getVarbitValue(POUCH_AMOUNT_VARBITS[i]);
			if (type != 0 && amount > 0)
			{
				runes.merge(runeEnum.getIntValue(type), amount, Integer::sum);
			}
		}
		return Collections.unmodifiableMap(runes);
	}

	private Map<Integer, Integer> readLeprechaun()
	{
		final Map<Integer, Integer> counts = new HashMap<>();
		for (LeprechaunItem item : LeprechaunItem.values())
		{
			if (item == LeprechaunItem.MAGIC_SECATEURS)
			{
				continue;
			}
			final int base = client.getVarbitValue(item.getBaseVarbit());
			final int extra = item.getExtraVarbit() == DataConstants.NONE ? 0 : client.getVarbitValue(item.getExtraVarbit());
			final int total = item.total(base, extra);
			if (total > 0)
			{
				counts.put(item.getItemId(), total);
			}
		}
		// A bottomless compost bucket stored here: type 1 is an empty one, higher a filled one (the game's
		// farming_tools_getstored script)
		final int bucketType = client.getVarbitValue(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
		if (bucketType == Compost.EMPTY_BUCKET_TYPE)
		{
			counts.put(ItemID.BOTTOMLESS_COMPOST_BUCKET, 1);
		}
		else if (bucketType > Compost.EMPTY_BUCKET_TYPE)
		{
			counts.put(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
		}
		// The secateurs slot holds magic secateurs when this flag is set.
		if (client.getVarbitValue(LeprechaunItem.MAGIC_SECATEURS.getBaseVarbit()) > 0)
		{
			final Integer secateurs = counts.remove(LeprechaunItem.SECATEURS.getItemId());
			if (secateurs != null)
			{
				counts.put(LeprechaunItem.MAGIC_SECATEURS.getItemId(), secateurs);
			}
		}
		return counts;
	}

	/**
	 * Today's uses of each teleport limited per day: the game value, or what the game last said in chat today if
	 * that's more (the value left a used-up Ardougne cloak planned, elzerma's run 2026-10-18).
	 */
	private Map<TravelMethod, Integer> readUsedToday()
	{
		final Map<TravelMethod, Integer> said = dailyUses.usedToday();
		final Map<TravelMethod, Integer> used = new EnumMap<>(TravelMethod.class);
		DailyLimits.usedTodayVarbits().forEach((method, varbit) ->
		{
			final int value = usedTodayValue(method, varbit);
			final Integer chat = said.get(method);
			final String differs = method + " used today: game value " + value + ", chat " + chat;
			if (chat != null && chat != value && chat != DailyUseTracker.ALL && !differs.equals(loggedDaily))
			{
				// Once per change, for checking what the game value counts
				loggedDaily = differs;
				log.debug(differs);
			}
			used.put(method, chat != null ? Math.max(value, chat) : value);
		});
		return Collections.unmodifiableMap(used);
	}

	/**
	 * Today's uses of one teleport from the game, its two varbits put together when the count is split (the Ardougne
	 * cloak's): the low part's width comes from the game's own layout of it. The dev log shows both parts and where
	 * they sit, once per change, until they're confirmed.
	 */
	private int usedTodayValue(TravelMethod method, int lowVarbit)
	{
		final int low = client.getVarbitValue(lowVarbit);
		final Integer highVarbit = DailyLimits.usedTodayHighBits().get(method);
		if (highVarbit == null)
		{
			return low;
		}
		final VarbitComposition lowBits = client.getVarbit(lowVarbit);
		final VarbitComposition highBits = client.getVarbit(highVarbit);
		if (lowBits == null || highBits == null)
		{
			return low;
		}
		final int high = client.getVarbitValue(highVarbit);
		final int width = lowBits.getMostSignificantBit() - lowBits.getLeastSignificantBit() + 1;
		final int value = DailyLimits.combine(low, high, width);
		final String layout = method + " used today: " + value + " (low " + low + ", varp " + lowBits.getIndex()
			+ " bits " + lowBits.getLeastSignificantBit() + "-" + lowBits.getMostSignificantBit() + "; high " + high
			+ ", varp " + highBits.getIndex() + " bits " + highBits.getLeastSignificantBit() + "-"
			+ highBits.getMostSignificantBit() + ")";
		if (!layout.equals(loggedLayout))
		{
			loggedLayout = layout;
			log.debug(layout);
		}
		return value;
	}

	private Map<Integer, Integer> writeCache(String key, ItemContainer container)
	{
		final Map<Integer, Integer> counts = Collections.unmodifiableMap(read(container));
		final int[] pairs = new int[counts.size() * 2];
		int i = 0;
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			pairs[i++] = e.getKey();
			pairs[i++] = e.getValue();
		}
		configManager.setRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key, gson.toJson(pairs));
		return counts;
	}

	private Map<Integer, Integer> readCache(String key)
	{
		final String json = configManager.getRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key);
		if (json == null)
		{
			return null;
		}
		try
		{
			final int[] pairs = gson.fromJson(json, int[].class);
			final Map<Integer, Integer> counts = new HashMap<>();
			for (int i = 0; pairs != null && i + 1 < pairs.length; i += 2)
			{
				counts.put(pairs[i], pairs[i + 1]);
			}
			return Collections.unmodifiableMap(counts);
		}
		catch (JsonParseException e)
		{
			log.warn("Couldn't read cached {}", key, e);
			return null;
		}
	}
}
