package com.farmrunautopilot.ui;

import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.testing.TestItem;
import com.farmrunautopilot.testing.TestRunner;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * "I NEED YOUR HELP!": features that couldn't be tried in development (no account with the item or unlock, or
 * a game value not checked yet), what to try, and a guided test for each. A button at the bottom of every tab
 * opens the list in Account > Testing & debug. Remove an item once it's confirmed (see CLAUDE.md).
 */
final class HelpWanted
{
	static final String ISSUES_URL = "https://github.com/elzerma/farm-run-autopilot/issues";
	private static final String TITLE = "I NEED YOUR HELP!";
	private static final int TEXT_WIDTH = 160;
	/** How a guided test goes, for players, so they finish by sending the GitHub issue. */
	private static final String[] HOW_IT_WORKS = {
		"1. Press Start test on one below. If it's greyed out, it says what you still need.",
		"2. Your plugin settings are saved and changed just for the test.",
		"3. A box at the top of the Run tab tells you what to do. It notices most steps by itself and asks "
			+ "Yes or No for the rest. Cancel stops it at any time.",
		"4. When it's done, your settings go back to how they were.",
		"5. Last step: press \"Open GitHub issues with prefilled test results\". Your results are already filled "
			+ "in. Sign in to GitHub (a free account) if it asks, add anything else you noticed, and press "
			+ "\"Create\" (or \"Submit new issue\"). Nothing is sent until you press it, and it never includes your character name.",
	};

	private HelpWanted()
	{
	}

	/** This client has already run the item's test (for its current revision), pass or not. */
	static boolean isDone(SettingsStore settings, TestItem item)
	{
		return settings.getClientValue(item.key()) != null;
	}

	/** How many tests this client hasn't run yet. */
	static int toTry(SettingsStore settings)
	{
		int count = 0;
		for (TestItem item : TestItem.values())
		{
			count += isDone(settings, item) ? 0 : 1;
		}
		return count;
	}

	/** The button at the bottom of every tab, which opens the list in Account > Testing & debug. */
	static JButton button(SettingsStore settings, boolean developer, Runnable open)
	{
		final JButton button = new JButton();
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(ColorScheme.BRAND_ORANGE);
		button.setFocusPainted(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setToolTipText("Features I couldn't test, with steps to try them");
		button.addActionListener(e -> open.run());
		refresh(button, settings, developer);
		return button;
	}

	/** Update the count; players who have run every test don't see the button at all. */
	static void refresh(JButton button, SettingsStore settings, boolean developer)
	{
		final int count = toTry(settings);
		button.setText(count == 0 ? TITLE + " (all done)" : TITLE + " (" + count + " to try)");
		button.setVisible(count > 0 || developer);
	}

	/**
	 * The list for Account > Testing & debug: every item with steps to try it and its guided test, grouped by
	 * tab. In the dev client a finished test's result goes to the clipboard; for players it opens a GitHub issue.
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
		if (developer)
		{
			add(panel, text("Press Start test: the Run tab walks you through it and your settings are put back "
				+ "afterwards. The result is copied to your clipboard to paste to Claude.", Color.WHITE, 6));
		}
		else
		{
			add(panel, text("I can't test these myself, so your results really help. How it works:", Color.WHITE, 4));
			for (String step : HOW_IT_WORKS)
			{
				add(panel, text(step, ColorScheme.LIGHT_GRAY_COLOR, 3));
			}
			add(panel, text("", ColorScheme.LIGHT_GRAY_COLOR, 3));
		}
		if (toTry(settings) == 0)
		{
			add(panel, text("You've run every test. Thank you!", ColorScheme.PROGRESS_COMPLETE_COLOR, 6));
		}
		for (TestItem.Tab tab : TestItem.Tab.values())
		{
			boolean first = true;
			for (TestItem item : TestItem.values())
			{
				if (item.getTab() != tab || isDone(settings, item))
				{
					continue;
				}
				if (first)
				{
					add(panel, text(tab.getLabel() + " tab", Color.WHITE, 4));
					first = false;
				}
				addItem(panel, item, settings, runner, showRun);
			}
		}
		addDone(panel, settings, developer, runner, showRun);
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

	private static void addItem(JPanel panel, TestItem item, SettingsStore settings, TestRunner runner,
		Runnable showRun)
	{
		final JLabel title = text(item.getTitle(), ColorScheme.BRAND_ORANGE, 2);
		title.setFont(FontManager.getRunescapeBoldFont());
		add(panel, title);
		add(panel, text(item.getHowToTry(), ColorScheme.LIGHT_GRAY_COLOR, 4));
		addStart(panel, item, "Start test", runner, showRun);
	}

	/**
	 * Tests this client already ran, greyed out so nobody is asked twice. The developer can run one again
	 * (e.g. after a fix); a bigger change bumps the item's revision so everyone is asked again.
	 */
	private static void addDone(JPanel panel, SettingsStore settings, boolean developer, TestRunner runner,
		Runnable showRun)
	{
		boolean first = true;
		for (TestItem item : TestItem.values())
		{
			if (!isDone(settings, item))
			{
				continue;
			}
			if (first)
			{
				add(panel, text("Done", Color.WHITE, 4));
				first = false;
			}
			final String result = settings.getClientValue(item.key());
			add(panel, text(item.getTitle() + ": " + result, ColorScheme.MEDIUM_GRAY_COLOR, developer ? 2 : 6));
			if (developer)
			{
				addStart(panel, item, "Run again", runner, showRun);
			}
		}
	}

	private static void addStart(JPanel panel, TestItem item, String label, TestRunner runner, Runnable showRun)
	{
		final List<String> missing = runner.missing(item);
		final JButton start = new JButton(label);
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
		add(panel, text("", ColorScheme.MEDIUM_GRAY_COLOR, 6));
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
