package com.farmrunautopilot.ui;

import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.testing.TestItem;
import com.farmrunautopilot.testing.TestRunner;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * "I NEED YOUR HELP!" at the bottom of each tab: features that couldn't be tried in development (no account
 * with the item or unlock, or a game value not checked yet), what to try, and a result to pick for each.
 * Results are kept for the client and logged ("Help list: ...") so they can be followed up. Remove an item
 * once it's confirmed (see CLAUDE.md).
 */
@Slf4j
final class HelpWanted
{
	static final String ISSUES_URL = "https://github.com/elzerma/farm-run-autopilot/issues";
	private static final String TITLE = "I NEED YOUR HELP!";
	private static final int TEXT_WIDTH = 160;
	private static final int CONTROL_WIDTH = 190;
	private static final String NOT_TRIED = "Not tried yet";
	private static final String GOOD = TestRunner.GOOD;
	private static final String ATTENTION = TestRunner.ATTENTION;
	private static final String[] RESULTS = {NOT_TRIED, GOOD, ATTENTION};

	private HelpWanted()
	{
	}

	/** How many items are still to try: all of them for players, those not yet Good to go for the developer. */
	static int toTry(SettingsStore settings, boolean developer)
	{
		int count = 0;
		for (TestItem item : TestItem.values())
		{
			count += developer && GOOD.equals(settings.getClientValue(item.key())) ? 0 : 1;
		}
		return count;
	}

	/** The button at the bottom of every tab, which opens the list in Account > Testing & debug. */
	static JComponent button(SettingsStore settings, boolean developer, Runnable open)
	{
		final int count = toTry(settings, developer);
		final JButton button = new JButton(count == 0 ? TITLE + " (all good)" : TITLE + " (" + count + " to try)");
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(ColorScheme.BRAND_ORANGE);
		button.setFocusPainted(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setToolTipText("Features I couldn't test, with steps to try them");
		button.addActionListener(e -> open.run());
		return button;
	}

	/**
	 * The list for Account > Testing & debug: every item with steps to try it, grouped by tab. The developer
	 * (running the dev client) gets a result to pick for each; everyone else gets the issues button.
	 */
	static JPanel list(SettingsStore settings, boolean developer, TestRunner runner, Runnable showRun)
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JLabel heading = text(TITLE, ColorScheme.BRAND_ORANGE, 4);
		heading.setFont(FontManager.getRunescapeBoldFont());
		add(panel, heading);
		add(panel, text("I can't test these myself. If you can, press Start test: the Run tab walks you through it, "
			+ "your settings are put back afterwards, and a GitHub issue opens with the results ready to submit.",
			Color.WHITE, 6));
		for (TestItem.Tab tab : TestItem.Tab.values())
		{
			boolean first = true;
			for (TestItem item : TestItem.values())
			{
				if (item.getTab() != tab)
				{
					continue;
				}
				if (first)
				{
					add(panel, text(tab.getLabel() + " tab", Color.WHITE, 4));
					first = false;
				}
				addItem(panel, item, settings, developer, runner, showRun);
			}
		}
		final JButton report = new JButton("Open GitHub issues");
		report.setFont(FontManager.getRunescapeSmallFont());
		report.setFocusPainted(false);
		report.setToolTipText(ISSUES_URL);
		report.addActionListener(e -> LinkBrowser.browse(ISSUES_URL));
		add(panel, report);
		return panel;
	}

	private static void add(JPanel panel, JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(component);
	}

	private static void addItem(JPanel panel, TestItem item, SettingsStore settings, boolean developer,
		TestRunner runner, Runnable showRun)
	{
		final JLabel title = text(item.getTitle(), ColorScheme.BRAND_ORANGE, 2);
		title.setFont(FontManager.getRunescapeBoldFont());
		add(panel, title);
		add(panel, text(item.getHowToTry(), ColorScheme.LIGHT_GRAY_COLOR, 4));
		final List<String> missing = runner.missing(item);
		final JButton start = new JButton("Start test");
		start.setFont(FontManager.getRunescapeSmallFont());
		start.setFocusPainted(false);
		start.setEnabled(missing.isEmpty());
		start.setToolTipText(missing.isEmpty() ? "Walks you through it on the Run tab"
			: "<html>Needs:<br>" + String.join("<br>", missing).replace("&", "&amp;") + "</html>");
		start.addActionListener(e ->
		{
			runner.start(item);
			showRun.run();
		});
		add(panel, start);
		if (!missing.isEmpty())
		{
			add(panel, text("Needs: " + String.join("; ", missing), ColorScheme.MEDIUM_GRAY_COLOR, 0));
		}
		add(panel, text("", ColorScheme.MEDIUM_GRAY_COLOR, developer ? 0 : 6));
		if (!developer)
		{
			return;
		}

		final String saved = settings.getClientValue(item.key());
		final JComboBox<String> result = new JComboBox<>(RESULTS);
		result.setSelectedItem(saved != null ? saved : NOT_TRIED);
		result.setFont(FontManager.getRunescapeSmallFont());
		result.setMaximumSize(new Dimension(CONTROL_WIDTH, 24));
		result.setPreferredSize(new Dimension(CONTROL_WIDTH, 24));
		add(panel, result);

		final JTextField note = new JTextField(noteOf(item, settings));
		note.setToolTipText("What went wrong (saved when you press Enter or click away)");
		note.setMaximumSize(new Dimension(CONTROL_WIDTH, 24));
		note.setPreferredSize(new Dimension(CONTROL_WIDTH, 24));
		note.setVisible(ATTENTION.equals(result.getSelectedItem()));
		add(panel, note);
		add(panel, text("", ColorScheme.MEDIUM_GRAY_COLOR, 6));

		result.addActionListener(e ->
		{
			final String picked = (String) result.getSelectedItem();
			if (picked == null || picked.equals(valueOr(settings.getClientValue(item.key()), NOT_TRIED)))
			{
				return;
			}
			settings.setClientValue(item.key(), NOT_TRIED.equals(picked) ? null : picked);
			// Infrequent and user-made, so INFO; read back from the client log to follow up
			log.info("Help list: {} = {}{}", item.name(), picked, noteSuffix(item, settings));
			note.setVisible(ATTENTION.equals(picked));
			panel.revalidate();
		});
		final Runnable saveNote = () ->
		{
			final String text = note.getText().trim();
			if (!text.equals(noteOf(item, settings)))
			{
				settings.setClientValue(item.key() + ".note", text);
				log.info("Help list: {} = {}{}", item.name(), result.getSelectedItem(), noteSuffix(item, settings));
			}
		};
		note.addActionListener(e -> saveNote.run());
		note.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				saveNote.run();
			}
		});
	}

	private static String noteOf(TestItem item, SettingsStore settings)
	{
		return valueOr(settings.getClientValue(item.key() + ".note"), "");
	}

	private static String noteSuffix(TestItem item, SettingsStore settings)
	{
		final String note = noteOf(item, settings);
		return note.isEmpty() ? "" : " (note: " + note + ")";
	}

	private static String valueOr(String value, String fallback)
	{
		return value != null ? value : fallback;
	}

	private static JLabel text(String text, Color colour, int gapBelow)
	{
		final JLabel label = new JLabel(text.isEmpty() ? " " : "<html><div style='width:" + TEXT_WIDTH + "px'>"
			+ text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
		label.setForeground(colour);
		label.setFont(FontManager.getRunescapeFont());
		label.setBorder(new EmptyBorder(0, 0, gapBelow, 0));
		return label;
	}
}
