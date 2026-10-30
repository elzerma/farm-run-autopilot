package com.farmrunautopilot.ui;

import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.testing.TestItem;
import com.farmrunautopilot.testing.TestRunner;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
 * opens the list in Settings > Help improve this plugin. Remove an item once it's confirmed (see CLAUDE.md).
 */
final class HelpWanted
{
	static final String ISSUES_URL = "https://github.com/elzerma/farm-run-autopilot/issues";
	private static final int TEXT_WIDTH = 184;
	/** How far a group's tests sit in from its heading (docs/BRANDING.md, spacing). */
	private static final int INDENT = 10;
	/** Space above each group's heading, as above a heading. */
	private static final int GROUP_GAP = 8;
	/** Tests opened to show their steps, kept while the page redraws. */
	private static final java.util.Set<TestItem> OPEN_ROWS = java.util.EnumSet.noneOf(TestItem.class);

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
			count += isDone(settings, item) || item.getTab() == TestItem.Tab.DEV ? 0 : 1;
		}
		return count;
	}

	/**
	 * The small line at the bottom of every tab, which opens the list in Settings > Help improve this plugin (elzerma: a quiet
	 * link rather than a big button).
	 */
	static JButton button(SettingsStore settings, boolean developer, Runnable open)
	{
		final JButton button = new JButton();
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(Color.WHITE);
		button.setFocusPainted(false);
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setBorder(new javax.swing.border.EmptyBorder(4, 0, 4, 0));
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.addActionListener(e -> open.run());
		refresh(button, settings, developer);
		return button;
	}

	/** Update the count; players who have run every test don't see the button at all. */
	static void refresh(JButton button, SettingsStore settings, boolean developer)
	{
		final int count = toTry(settings);
		// Says it's easy (elzerma: no coding or heavy lifting, just run a couple of scenarios)
		button.setText("<html><body style='width:146px'>Help improve this plugin: try a few quick things in game, "
			+ "no coding needed. <u>Show me</u></body></html>");
		button.setToolTipText(count == 0 ? "Every test is done" : count + (count == 1 ? " feature" : " features")
			+ " I couldn't test, with steps to try them");
		button.setVisible(count > 0 || developer);
	}

	/**
	 * The guided tests for Settings > Help improve this plugin, in the sidebar's style (Sprint 7): a group per tab
	 * that opens and closes, one line per test ("Start", or what it still needs), its steps and Start button when
	 * opened, the tests already run in a closed Done group, and a small link to the issues page. The explanation
	 * above it is the Settings page's. In the dev client a finished test's result goes to the clipboard; for
	 * players it opens a GitHub issue.
	 */
	static JPanel list(SettingsStore settings, boolean developer, TestRunner runner, Runnable showRun,
		Runnable redraw)
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (toTry(settings) == 0)
		{
			add(panel, text("You've run every test. Thank you!", ColorScheme.PROGRESS_COMPLETE_COLOR, 4));
		}
		for (TestItem.Tab tab : TestItem.Tab.values())
		{
			if (tab == TestItem.Tab.DEV)
			{
				continue;
			}
			final List<TestItem> items = new ArrayList<>();
			for (TestItem item : TestItem.values())
			{
				if (item.getTab() == tab && !isDone(settings, item))
				{
					items.add(item);
				}
			}
			if (items.isEmpty())
			{
				continue;
			}
			final String key = "Guided tests: " + tab.name();
			final boolean open = settings.isSectionOpen(key);
			// Room above each tab's heading, and its tests indented under it, so a heading and a test don't look alike
			// (elzerma, 2026-10-25)
			if (panel.getComponentCount() > 0)
			{
				add(panel, gap(GROUP_GAP));
			}
			add(panel, Card.groupHeader(tab.getLabel() + " tab", items.size() + " to try", open, () ->
			{
				settings.setSectionOpen(key, !open);
				redraw.run();
			}));
			if (open)
			{
				for (TestItem item : items)
				{
					add(panel, indented(testRow(item, runner, showRun, "Start test")));
				}
			}
		}
		addDone(panel, settings, developer, runner, showRun, redraw);
		final JLabel issues = text("See every report on GitHub", Color.WHITE, 0);
		issues.setText("<html><u>See every report on GitHub</u></html>");
		issues.setBorder(new EmptyBorder(4, 0, 2, 0));
		issues.setToolTipText(ISSUES_URL);
		issues.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		issues.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(ISSUES_URL);
			}
		});
		add(panel, issues);
		return panel;
	}

	/** A component set in under its group's heading. */
	private static JComponent indented(JComponent component)
	{
		final JPanel holder = new JPanel(new java.awt.BorderLayout())
		{
			@Override
			public java.awt.Dimension getMaximumSize()
			{
				return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.setBorder(new EmptyBorder(0, INDENT, 0, 0));
		holder.add(component, java.awt.BorderLayout.CENTER);
		return holder;
	}

	/** Empty space of this height. */
	private static JComponent gap(int height)
	{
		return (JComponent) javax.swing.Box.createVerticalStrut(height);
	}

	private static void add(JPanel panel, JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(component);
	}

	/**
	 * One test on one line: its name, and "Start" or the first thing it still needs (said only here); the steps
	 * on hover, and with the Start button when opened.
	 */
	private static JComponent testRow(TestItem item, TestRunner runner, Runnable showRun, String startLabel)
	{
		final List<String> missing = runner.missing(item);
		final ExpandableRow row = new ExpandableRow(item.getTitle(),
			// Short on the line (elzerma: the whole need wrapped into a tall column); what's needed is inside
			missing.isEmpty() ? "Ready" : "Not yet",
			item.getHowToTry() + (missing.isEmpty() ? "" : " Needs: " + String.join("; ", missing) + "."),
			false, TEXT_WIDTH + 40, OPEN_ROWS.contains(item), open ->
			{
				if (open)
				{
					OPEN_ROWS.add(item);
				}
				else
				{
					OPEN_ROWS.remove(item);
				}
			});
		row.addContent(text(item.getHowToTry(), Color.WHITE, 4));
		if (!missing.isEmpty())
		{
			// Instead of a greyed-out button, say what's missing, each need in the sentence's lower case
			final List<String> needs = new ArrayList<>();
			for (String need : missing)
			{
				needs.add(lowerFirst(need));
			}
			row.addContent(text("You need to: " + String.join("; ", needs) + ".", Color.WHITE, 4));
			return row;
		}
		final JButton start = Style.primary(new JButton(startLabel));
		start.setToolTipText("Walks you through it on the Run tab");
		start.addActionListener(e ->
		{
			runner.start(item);
			showRun.run();
		});
		row.addContent(start);
		return row;
	}

	private static String lowerFirst(String text)
	{
		return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	/**
	 * Tests this client already ran, in a closed group so nobody is asked twice. The developer can run one again
	 * (e.g. after a fix); a bigger change bumps the item's revision so everyone is asked again.
	 */
	private static void addDone(JPanel panel, SettingsStore settings, boolean developer, TestRunner runner,
		Runnable showRun, Runnable redraw)
	{
		final List<TestItem> done = new ArrayList<>();
		for (TestItem item : TestItem.values())
		{
			if (isDone(settings, item) && item.getTab() != TestItem.Tab.DEV)
			{
				done.add(item);
			}
		}
		if (done.isEmpty())
		{
			return;
		}
		final String key = "Guided tests: done";
		if (panel.getComponentCount() > 0)
		{
			add(panel, gap(GROUP_GAP));
		}
		final boolean open = settings.isSectionOpen(key);
		add(panel, Card.groupHeader("Done", String.valueOf(done.size()), open, () ->
		{
			settings.setSectionOpen(key, !open);
			redraw.run();
		}));
		if (!open)
		{
			return;
		}
		for (TestItem item : done)
		{
			if (developer)
			{
				add(panel, indented(testRow(item, runner, showRun, "Run again")));
			}
			else
			{
				final JLabel line = text(item.getTitle() + ": " + settings.getClientValue(item.key()),
					ColorScheme.MEDIUM_GRAY_COLOR, 2);
				line.setBorder(new EmptyBorder(1, INDENT, 2, 0));
				add(panel, line);
			}
		}
	}

	/**
	 * Settings > Dev checks (developer client only, elzerma 2026-10-29: "a separate panel just for me"): the guided checks
	 * for the developer's own runs, apart from the players' list. Each can be run again; its last result shows under it.
	 */
	static JPanel devList(SettingsStore settings, TestRunner runner, Runnable showRun)
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (TestItem item : TestItem.values())
		{
			if (item.getTab() != TestItem.Tab.DEV)
			{
				continue;
			}
			add(panel, testRow(item, runner, showRun, isDone(settings, item) ? "Run again" : "Start test"));
			if (isDone(settings, item))
			{
				add(panel, text("Last: " + settings.getClientValue(item.key()), ColorScheme.MEDIUM_GRAY_COLOR, 4));
			}
		}
		if (panel.getComponentCount() == 0)
		{
			add(panel, text("No dev checks right now.", ColorScheme.MEDIUM_GRAY_COLOR, 4));
		}
		return panel;
	}

	private static JLabel text(String text, Color colour, int gapBelow)
	{
		// UiText.wrap scales to screen pixels; an unscaled width ran off the edge inside an opened test (elzerma)
		final JLabel label = new JLabel(text.isEmpty() ? " " : UiText.wrap(text, TEXT_WIDTH));
		label.setForeground(colour);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 0, gapBelow, 0));
		return label;
	}
}
