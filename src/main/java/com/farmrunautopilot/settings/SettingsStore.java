package com.farmrunautopilot.settings;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Loads and saves {@link RunConfig} and {@link AccountSettings} as JSON in the current account's RuneLite
 * profile (SPEC 14). Edits made while logged out stay in memory only.
 */
@Slf4j
@Singleton
public class SettingsStore
{
	private static final String RUN_CONFIG_KEY = "runConfig";
	private static final String ACCOUNT_KEY = "account";
	private static final String OPEN_SECTIONS_KEY = "ui.openSections";
	/** Open the first time: the ones changed most often. */
	private static final List<String> DEFAULT_OPEN_SECTIONS = Arrays.asList("Crops", "Run options");

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final List<Runnable> saveListeners = new CopyOnWriteArrayList<>();

	@Getter
	private volatile RunConfig runConfig = new RunConfig();
	@Getter
	private volatile AccountSettings account = new AccountSettings();

	@Inject
	SettingsStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Called after settings are reloaded or changed by something other than the Setup tab. */
	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	/** Called after every save and reload, e.g. so the supply plan is recalculated. */
	public void addSaveListener(Runnable listener)
	{
		saveListeners.add(listener);
	}

	public void removeSaveListener(Runnable listener)
	{
		saveListeners.remove(listener);
	}

	/**
	 * Whether a sidebar section is open. Stored once for the whole client (not per account), as a
	 * comma-separated list of open section titles.
	 */
	public boolean isSectionOpen(String title)
	{
		final String open = configManager.getConfiguration(FarmRunAutopilotConfig.GROUP, OPEN_SECTIONS_KEY);
		if (open == null)
		{
			return DEFAULT_OPEN_SECTIONS.contains(title);
		}
		return Arrays.asList(open.split(",")).contains(title);
	}

	public void setSectionOpen(String title, boolean isOpen)
	{
		final String saved = configManager.getConfiguration(FarmRunAutopilotConfig.GROUP, OPEN_SECTIONS_KEY);
		final Set<String> open = new LinkedHashSet<>(saved == null ? DEFAULT_OPEN_SECTIONS
			: Arrays.asList(saved.split(",")));
		open.remove("");
		if (isOpen)
		{
			open.add(title);
		}
		else
		{
			open.remove(title);
		}
		configManager.setConfiguration(FarmRunAutopilotConfig.GROUP, OPEN_SECTIONS_KEY, String.join(",", open));
	}

	/** Whether there is an account profile to save to (i.e. the player has logged in). */
	public boolean hasProfile()
	{
		return configManager.getRSProfileKey() != null;
	}

	/** Reloads both from the current profile, e.g. after login or switching accounts. */
	public void load()
	{
		runConfig = read(RUN_CONFIG_KEY, RunConfig.class, new RunConfig()).sanitise();
		account = read(ACCOUNT_KEY, AccountSettings.class, new AccountSettings()).sanitise();
		notifyListeners();
		for (Runnable listener : saveListeners)
		{
			listener.run();
		}
	}

	public void saveRunConfig()
	{
		write(RUN_CONFIG_KEY, runConfig);
	}

	/**
	 * @param external true when the change did not come from the Setup tab, so the tab must refresh
	 */
	public void saveAccount(boolean external)
	{
		write(ACCOUNT_KEY, account);
		if (external)
		{
			notifyListeners();
		}
	}

	private <T> T read(String key, Class<T> type, T fallback)
	{
		final String json = configManager.getRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key);
		if (json == null)
		{
			return fallback;
		}
		try
		{
			final T value = gson.fromJson(json, type);
			return value != null ? value : fallback;
		}
		catch (JsonParseException e)
		{
			log.warn("Couldn't read saved {}, using defaults", key, e);
			return fallback;
		}
	}

	private void write(String key, Object value)
	{
		if (hasProfile())
		{
			configManager.setRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key, gson.toJson(value));
		}
		for (Runnable listener : saveListeners)
		{
			listener.run();
		}
	}

	private void notifyListeners()
	{
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}
}
