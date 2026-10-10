package com.farmrunautopilot.settings;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
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
	private static final String PRESETS_KEY = "presets";
	/** Open the first time: the ones changed most often. */
	private static final List<String> DEFAULT_OPEN_SECTIONS = Arrays.asList("Crops", "Defaults for every stop", "Detected");

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final List<Runnable> saveListeners = new CopyOnWriteArrayList<>();

	@Getter
	private volatile RunConfig runConfig = new RunConfig();
	@Getter
	private volatile AccountSettings account = new AccountSettings();
	/** Replaced, never changed in place. */
	private volatile List<Preset> presets = new ArrayList<>();

	@Inject
	public SettingsStore(ConfigManager configManager, Gson gson)
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

	/** A value kept once for the whole client (not per account), e.g. "help.<item>"; null if never set. */
	public String getClientValue(String key)
	{
		return configManager.getConfiguration(FarmRunAutopilotConfig.GROUP, key);
	}

	public void setClientValue(String key, String value)
	{
		if (value == null || value.isEmpty())
		{
			configManager.unsetConfiguration(FarmRunAutopilotConfig.GROUP, key);
		}
		else
		{
			configManager.setConfiguration(FarmRunAutopilotConfig.GROUP, key, value);
		}
	}

	/** Whether there is an account profile to save to (i.e. the player has logged in). */
	public boolean hasProfile()
	{
		return configManager.getRSProfileKey() != null;
	}

	/** Reloads everything from the current profile, e.g. after login or switching accounts. */
	public void load()
	{
		runConfig = read(RUN_CONFIG_KEY, RunConfig.class, new RunConfig()).sanitise();
		account = read(ACCOUNT_KEY, AccountSettings.class, new AccountSettings()).sanitise();
		final List<Preset> loaded = new ArrayList<>();
		for (Preset preset : read(PRESETS_KEY, Preset[].class, new Preset[0]))
		{
			if (preset != null && preset.getName() != null && preset.getConfig() != null)
			{
				preset.getConfig().sanitise();
				loaded.add(preset);
			}
		}
		presets = loaded;
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

	/** Farm and Travel tab settings back to defaults. Swing thread. */
	public void resetRunConfig()
	{
		runConfig = new RunConfig().sanitise();
		saveRunConfig();
		changedEverywhere();
	}

	/**
	 * Also this account's house, unlocks, tracked charges and presets, as if freshly installed (learned times
	 * are cleared separately). Swing thread.
	 */
	public void resetAccountAndPresets()
	{
		account = new AccountSettings().sanitise();
		presets = new ArrayList<>();
		write(ACCOUNT_KEY, account);
		write(PRESETS_KEY, new Preset[0]);
		changedEverywhere();
	}

	/** Tell the tabs to redraw and the plan to be redone. */
	private void changedEverywhere()
	{
		notifyListeners();
		for (Runnable listener : saveListeners)
		{
			listener.run();
		}
	}

	// Presets (SPEC 13.5). Swing thread.

	public List<String> presetNames()
	{
		final List<String> names = new ArrayList<>();
		for (Preset preset : presets)
		{
			names.add(preset.getName());
		}
		return names;
	}

	/** The preset the current settings match exactly, or null if they've been changed since. */
	public String activePreset()
	{
		for (Preset preset : presets)
		{
			if (preset.getConfig().equals(runConfig))
			{
				return preset.getName();
			}
		}
		return null;
	}

	/** Save the current settings under this name, replacing a preset with the same name. */
	public void savePreset(String name)
	{
		final List<Preset> updated = new ArrayList<>(presets);
		final Preset preset = new Preset(name, copy(runConfig));
		final int index = indexOf(name);
		if (index >= 0)
		{
			updated.set(index, preset);
		}
		else
		{
			updated.add(preset);
		}
		writePresets(updated);
	}

	/** Switch the current settings to this preset. */
	public void applyPreset(String name)
	{
		final int index = indexOf(name);
		if (index < 0)
		{
			return;
		}
		runConfig = copy(presets.get(index).getConfig());
		saveRunConfig();
		notifyListeners();
	}

	public void renamePreset(String from, String to)
	{
		final int index = indexOf(from);
		if (index < 0 || indexOf(to) >= 0)
		{
			return;
		}
		final List<Preset> updated = new ArrayList<>(presets);
		updated.set(index, new Preset(to, presets.get(index).getConfig()));
		writePresets(updated);
	}

	public void deletePreset(String name)
	{
		final List<Preset> updated = new ArrayList<>(presets);
		updated.removeIf(p -> p.getName().equals(name));
		writePresets(updated);
	}

	private int indexOf(String name)
	{
		for (int i = 0; i < presets.size(); i++)
		{
			if (presets.get(i).getName().equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	private void writePresets(List<Preset> updated)
	{
		presets = updated;
		write(PRESETS_KEY, updated.toArray(new Preset[0]));
		notifyListeners();
	}

	/** A deep copy, so editing the current settings never changes a saved preset. */
	private RunConfig copy(RunConfig config)
	{
		return gson.fromJson(gson.toJson(config), RunConfig.class).sanitise();
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
