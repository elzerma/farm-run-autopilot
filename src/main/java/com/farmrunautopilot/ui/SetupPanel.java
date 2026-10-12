package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * What the Farm, Travel and Account tabs, and preset management on the Run tab, have in common (SPEC 13.2;
 * layout in docs/plans/sidebar-ux.md): sections that remember being open, the controls, and saving. Each page
 * is its own class ({@link FarmPage}, {@link TravelPage}, {@link AccountPage}, {@link PresetsPage}). Rebuilt from
 * the saved settings whenever they reload or the account's access changes; every edit is saved straight away.
 */
abstract class SetupPanel extends JPanel
{
	/** Room left for controls after the sidebar's and sections' borders. */
	static final int CONTROL_WIDTH = PluginPanel.PANEL_WIDTH - 40;

	final SettingsStore settings;
	final AccessChecker accessChecker;
	/** Redraws every tab after a change, so summaries and Override tags stay current. */
	final Runnable changed;
	/** What the player holds (Travel and Account pages). */
	Supplier<Holdings> holdings = () -> Holdings.EMPTY;

	SetupPanel(SettingsStore settings, AccessChecker accessChecker, Runnable changed)
	{
		this.changed = changed;
		this.settings = settings;
		this.accessChecker = accessChecker;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	/** What the player holds, for the pages that show it. */
	void showHoldings(Supplier<Holdings> holdings)
	{
		this.holdings = holdings;
	}

	/** Rebuilds every control from the current settings and access. Call on the Swing thread. */
	void rebuild()
	{
		removeAll();
		final AccessSnapshot access = accessChecker.getSnapshot();
		if (!settings.hasProfile())
		{
			add(note("Log in to load and save your setup."));
		}
		else if (!access.isKnown())
		{
			add(note("Checking your quests, diaries and levels... nothing is greyed out until that's done."));
		}
		addSections(settings.editRunConfig(), settings.editAccount(), access);
		revalidate();
		repaint();
	}

	/** This page's sections, from the working settings (Swing thread). */
	abstract void addSections(RunConfig config, AccountSettings account, AccessSnapshot access);

	// Saving

	void saveRun(Runnable change)
	{
		change.run();
		settings.saveRunConfig();
		SwingUtilities.invokeLater(changed);
	}

	/** Saves without redrawing the tabs: number boxes would lose focus on every click, and they change no summary. */
	void saveRunQuietly(Runnable change)
	{
		change.run();
		settings.saveRunConfig();
	}

	/**
	 * @param affectsAccess the change can lock or unlock patches or methods, so re-check access
	 */
	void saveAccount(Runnable change, boolean affectsAccess)
	{
		change.run();
		settings.saveAccount(false);
		SwingUtilities.invokeLater(changed);
		if (affectsAccess)
		{
			accessChecker.requestRefresh();
		}
	}

	static JButton smallButton(String text)
	{
		final JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setAlignmentX(LEFT_ALIGNMENT);
		return button;
	}

	// Building blocks

	/** Sections remember being open or closed between sessions. */
	CollapsibleSection section(String title)
	{
		return section(title, null);
	}

	/** With a summary of what's set inside, shown even when the section is closed. */
	CollapsibleSection section(String title, String summary)
	{
		return new CollapsibleSection(title, summary, settings.isSectionOpen(title),
			open -> settings.setSectionOpen(title, open));
	}

	/** e.g. "2 overrides", or null for none. */
	static String overrides(int count)
	{
		return count == 0 ? null : count + (count == 1 ? " override" : " overrides");
	}

	/** A short label with its number box on the same line. */
	static JComponent spinnerRow(String text, JSpinner spinner)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 3, 0));
		final JLabel label = new JLabel(wrap(text, CONTROL_WIDTH - 85));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.CENTER);
		row.add(spinner, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(CONTROL_WIDTH, row.getPreferredSize().height));
		return row;
	}

	static JCheckBox checkBox(String text, boolean selected, boolean enabled, String tooltip,
		Consumer<Boolean> onChange)
	{
		final JCheckBox box = new JCheckBox((String) null, selected);
		box.setEnabled(enabled);
		// HTML text ignores the disabled state, so grey it by hand (before setting the text).
		box.setForeground(enabled ? ColorScheme.TEXT_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		box.setText(wrap(text, CONTROL_WIDTH - 30));
		box.setToolTipText(tooltip);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.addActionListener(e -> onChange.accept(box.isSelected()));
		return box;
	}

	static <T> JComboBox<Choice<T>> combo(List<Choice<T>> choices, T selected, Consumer<T> onChange)
	{
		final JComboBox<Choice<T>> combo = new JComboBox<>();
		Choice<T> current = choices.isEmpty() ? null : choices.get(0);
		for (Choice<T> choice : choices)
		{
			combo.addItem(choice);
			if (Objects.equals(choice.getValue(), selected))
			{
				current = choice;
			}
		}
		combo.setSelectedItem(current);
		combo.setRenderer(new ChoiceRenderer());
		combo.setPreferredSize(new Dimension(CONTROL_WIDTH, 24));
		combo.setMaximumSize(new Dimension(CONTROL_WIDTH, 24));
		combo.setFont(FontManager.getRunescapeSmallFont());

		final Object[] previous = {current};
		combo.addActionListener(e ->
		{
			@SuppressWarnings("unchecked")
			final Choice<T> picked = (Choice<T>) combo.getSelectedItem();
			if (picked == null || picked == previous[0])
			{
				return;
			}
			if (!picked.isEnabled())
			{
				combo.setSelectedItem(previous[0]);
				return;
			}
			previous[0] = picked;
			onChange.accept(picked.getValue());
		});
		return combo;
	}

	static <E extends Enum<E>> List<Choice<E>> enumChoices(E[] values)
	{
		final List<Choice<E>> choices = new ArrayList<>();
		for (E value : values)
		{
			choices.add(Choice.of(value, value.toString()));
		}
		return choices;
	}

	static JSpinner spinner(int value, int min, int max, int step, IntConsumer onChange)
	{
		final JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
		spinner.setPreferredSize(new Dimension(70, 24));
		spinner.setMaximumSize(new Dimension(70, 24));
		spinner.addChangeListener(e -> onChange.accept((Integer) spinner.getValue()));
		return spinner;
	}

	static JLabel label(String text)
	{
		final JLabel label = new JLabel(wrap(text));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(4, 0, 2, 0));
		return label;
	}

	static JLabel subheader(String text)
	{
		final JLabel label = new JLabel(wrap(text));
		label.setForeground(ColorScheme.TEXT_COLOR);
		label.setBorder(new EmptyBorder(8, 0, 2, 0));
		return label;
	}

	static JLabel note(String text)
	{
		final JLabel label = new JLabel(wrap(text));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 0, 6, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	static String wrap(String text)
	{
		return wrap(text, CONTROL_WIDTH - 20);
	}

	static String wrap(String text, int width)
	{
		return UiText.wrap(text, width);
	}

	static String title(String enumName)
	{
		final String spaced = enumName.replace('_', ' ').toLowerCase();
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	/** Greys out disabled choices and shows their tooltip. */
	static class ChoiceRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
			boolean cellHasFocus)
		{
			final Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof Choice)
			{
				final Choice<?> choice = (Choice<?>) value;
				if (!choice.isEnabled())
				{
					// Never highlight locked entries: grey text on the grey hover colour is invisible.
					c.setBackground(list.getBackground());
					c.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				}
				setToolTipText(choice.getTooltip());
			}
			return c;
		}
	}
}
