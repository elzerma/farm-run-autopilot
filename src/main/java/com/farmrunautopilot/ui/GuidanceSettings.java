package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.Color;
import java.awt.Component;
import java.util.function.Consumer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;

/**
 * Reads and writes the run guidance settings (highlights and colours) for the Account tab. They're RuneLite
 * config items, global for the client, so changes reach the overlays straight away.
 */
final class GuidanceSettings
{
	private final FarmRunAutopilotConfig config;
	private final ConfigManager configManager;
	private final ColorPickerManager colorPickers;

	GuidanceSettings(FarmRunAutopilotConfig config, ConfigManager configManager, ColorPickerManager colorPickers)
	{
		this.config = config;
		this.configManager = configManager;
		this.colorPickers = colorPickers;
	}

	FarmRunAutopilotConfig get()
	{
		return config;
	}

	void set(String key, Object value)
	{
		configManager.setConfiguration(FarmRunAutopilotConfig.GROUP, key, value);
	}

	/** Opens RuneLite's colour picker; each change is saved as it's made. */
	void pickColour(Component parent, String title, Color current, String key, Consumer<Color> onChange)
	{
		final RuneliteColorPicker picker = colorPickers.create(parent, current, title, false);
		picker.setLocationRelativeTo(parent);
		picker.setOnColorChange(colour ->
		{
			set(key, colour);
			onChange.accept(colour);
		});
		picker.setVisible(true);
	}
}
