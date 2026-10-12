package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Preset management, the first section of the Settings tab (SPEC 13.5; Sean moved it from the Run tab): save, apply, rename and delete named Farm and Travel setups.
 */
class PresetsPage extends SetupPanel
{
	/** The section's title, also how the Run tab's Manage link opens it. */
	static final String TITLE = "Presets";

	PresetsPage(SettingsStore settings, AccessChecker accessChecker, Runnable changed)
	{
		super(settings, accessChecker, changed);
		rebuild();
	}

	@Override
	void addSections(RunConfig config, AccountSettings account, AccessSnapshot access)
	{
		add(presetsSection());
	}

	/** Save, rename and delete named copies of the run settings (SPEC 13.5). */
	private JComponent presetsSection()
	{
		final CollapsibleSection s = section(TITLE);
		s.help("Save these settings under a name (e.g. \"Quick herbs\") and switch between them at the "
			+ "top of the Run tab. Presets cover the Farm and Travel tabs; your house and unlocks are shared.");
		final JButton save = smallButton("Save current settings as...");
		save.addActionListener(e ->
		{
			final String name = askName("Name for this preset:", settings.activePreset());
			if (name == null)
			{
				return;
			}
			if (settings.presetNames().contains(name) && JOptionPane.showConfirmDialog(this,
				"Replace the preset \"" + name + "\" with the current settings?", "Farm Run Autopilot",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			{
				return;
			}
			settings.savePreset(name);
		});
		s.addContent(save);

		final String active = settings.activePreset();
		for (String name : settings.presetNames())
		{
			s.addContent(subheader(name + (name.equals(active) ? " (in use)" : "")));
			final JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
			buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
			final JButton use = smallButton("Use");
			use.setEnabled(!name.equals(active));
			use.addActionListener(e -> settings.applyPreset(name));
			final JButton rename = smallButton("Rename");
			rename.addActionListener(e ->
			{
				final String to = askName("New name for \"" + name + "\":", name);
				if (to != null && !to.equals(name))
				{
					if (settings.presetNames().contains(to))
					{
						JOptionPane.showMessageDialog(this, "There's already a preset called \"" + to + "\".");
						return;
					}
					settings.renamePreset(name, to);
				}
			});
			final JButton delete = smallButton("Delete");
			delete.addActionListener(e ->
			{
				if (JOptionPane.showConfirmDialog(this, "Delete the preset \"" + name + "\"?", "Farm Run Autopilot",
					JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
				{
					settings.deletePreset(name);
				}
			});
			buttons.add(use);
			buttons.add(rename);
			buttons.add(delete);
			buttons.setMaximumSize(new Dimension(CONTROL_WIDTH, buttons.getPreferredSize().height));
			buttons.setAlignmentX(LEFT_ALIGNMENT);
			s.addContent(buttons);
		}
		return s;
	}

	/** A trimmed, non-empty name from the player, or null if they cancelled. */
	private String askName(String prompt, String initial)
	{
		final Object input = JOptionPane.showInputDialog(this, prompt, "Farm Run Autopilot",
			JOptionPane.PLAIN_MESSAGE, null, null, initial);
		if (input == null)
		{
			return null;
		}
		final String name = input.toString().trim();
		return name.isEmpty() ? null : name;
	}

}
