package com.farmrunautopilot.ui;

import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Run tab (SPEC 13.1): which patches are in the next run and the supply checklist. Route, presets
 * and step guidance arrive in later milestones.
 */
class RunPanel extends JPanel
{
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 40;
	private static final Color CARRIED = new Color(0x4C, 0xC1, 0x52);
	private static final Color IN_STORAGE = new Color(0xE8, 0xC5, 0x3A);
	private static final Color MISSING = new Color(0xE0, 0x55, 0x55);
	/** Run type labels short enough for three across the sidebar, in {@link PatchType} order. */
	private static final String[] SHORT_TYPE_NAMES = {"Trees", "Fruit", "Herbs"};

	private final SettingsStore settings;
	private SupplyPlan plan = SupplyPlan.EMPTY;
	private boolean loggedIn;

	RunPanel(SettingsStore settings)
	{
		this.settings = settings;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		rebuild();
	}

	/** Show a new plan. Call on the Swing thread. */
	void update(SupplyPlan plan, boolean loggedIn)
	{
		this.plan = plan;
		this.loggedIn = loggedIn;
		rebuild();
	}

	private void rebuild()
	{
		removeAll();

		// Run types: what this run covers, chosen right here
		final JPanel types = new JPanel(new GridLayout(1, PatchType.values().length));
		types.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (PatchType type : PatchType.values())
		{
			final JCheckBox box = new JCheckBox(SHORT_TYPE_NAMES[type.ordinal()],
				settings.getRunConfig().getEnabledTypes().contains(type));
			box.setToolTipText("Include " + type.getDisplayName().toLowerCase() + " patches in this run");
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setFont(FontManager.getRunescapeSmallFont());
			box.addActionListener(e ->
			{
				if (box.isSelected())
				{
					settings.getRunConfig().getEnabledTypes().add(type);
				}
				else
				{
					settings.getRunConfig().getEnabledTypes().remove(type);
				}
				settings.saveRunConfig();
			});
			types.add(box);
		}
		types.setMaximumSize(new Dimension(Integer.MAX_VALUE, types.getPreferredSize().height));
		add(left(types));

		final JCheckBox fullRun = new JCheckBox(UiText.wrap("Count every patch (full run)", TEXT_WIDTH - 30),
			settings.getRunConfig().isSupplyFullRun());
		fullRun.setToolTipText("Off: only patches that are ready, dead, diseased, empty or never seen");
		fullRun.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fullRun.setFont(FontManager.getRunescapeSmallFont());
		fullRun.addActionListener(e ->
		{
			settings.getRunConfig().setSupplyFullRun(fullRun.isSelected());
			settings.saveRunConfig();
		});
		add(left(fullRun));

		if (!loggedIn)
		{
			add(note("Log in to see what to bring."));
			finish();
			return;
		}

		add(legend());
		add(heading(summary()));
		if (!plan.getNotDue().isEmpty())
		{
			final JLabel notDue = note(plan.getNotDue().size() + " selected patch"
				+ (plan.getNotDue().size() == 1 ? " is" : "es are") + " not due yet (hover for details)");
			notDue.setToolTipText("<html>" + String.join("<br>", escapeAll(plan.getNotDue())) + "</html>");
			add(notDue);
		}
		for (String warning : plan.getWarnings())
		{
			final JLabel label = note(warning);
			label.setForeground(ColorScheme.BRAND_ORANGE);
			add(label);
		}

		SupplyLine.Group group = null;
		for (SupplyLine line : plan.getLines())
		{
			if (line.getGroup() != group)
			{
				group = line.getGroup();
				add(heading(group.getDisplayName()));
			}
			add(row(line));
		}

		if (!plan.getTravelPlan().isEmpty())
		{
			add(heading("Getting there"));
			for (String stop : plan.getTravelPlan())
			{
				add(note(stop));
			}
		}

		if (!plan.getLines().isEmpty())
		{
			add(heading("Totals"));
			add(note("Coins: " + String.format("%,d", plan.getCoins())));
			if (!plan.getRuneSummary().isEmpty())
			{
				add(note("Runes: " + plan.getRuneSummary()));
			}
			add(note("Starting inventory: about " + plan.getSlots() + " / 28 slots"));
		}
		finish();
	}

	/** Colour key, so the three colours explain themselves. */
	private static JComponent legend()
	{
		final JLabel label = new JLabel("<html><font color='#4CC152'>Carried</font>  "
			+ "<font color='#E8C53A'>In storage</font>  <font color='#E05555'>Missing</font></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(4, 0, 0, 0));
		return left(label);
	}

	private String summary()
	{
		if (plan.patchTotal() == 0)
		{
			return plan.getNotDue().isEmpty() ? "No patches selected" : "Nothing is due yet";
		}
		final List<String> parts = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			final int n = plan.getPatchCounts().getOrDefault(type, 0);
			if (n > 0)
			{
				parts.add(n + " " + type.getDisplayName().toLowerCase() + (n == 1 ? "" : "s"));
			}
		}
		return "This run: " + String.join(", ", parts);
	}

	private JComponent row(SupplyLine line)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));

		final Color colour = colour(line);

		final JLabel name = new JLabel();
		name.setForeground(colour);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setText(UiText.wrap(line.getName(), TEXT_WIDTH - 70));

		final JLabel count = new JLabel(line.isMet() && line.getHave() < line.getNeed()
			? "ok" : line.getHave() + " / " + line.getNeed());
		count.setForeground(colour);
		count.setFont(FontManager.getRunescapeSmallFont());

		row.add(name, BorderLayout.CENTER);
		row.add(count, BorderLayout.EAST);
		row.setToolTipText(tooltip(line));

		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
		wrapper.add(row, BorderLayout.CENTER);
		return left(wrapper);
	}

	private static Color colour(SupplyLine line)
	{
		switch (line.getStatus())
		{
			case CARRIED:
				return CARRIED;
			case IN_STORAGE:
				return IN_STORAGE;
			default:
				// Missing optional items aren't a problem
				return line.getGroup() == SupplyLine.Group.OPTIONAL ? ColorScheme.LIGHT_GRAY_COLOR : MISSING;
		}
	}

	private static String tooltip(SupplyLine line)
	{
		final List<String> parts = new ArrayList<>();
		parts.add("Need " + line.getNeed() + ", carrying " + line.getCarried() + ", have " + line.getHave() + " in total");
		if (line.getWhere().isEmpty())
		{
			parts.add("None found");
		}
		else
		{
			final List<String> where = new ArrayList<>();
			for (Map.Entry<Holdings.Source, Integer> e : line.getWhere().entrySet())
			{
				where.add(e.getValue() + " in " + e.getKey().getLabel());
			}
			parts.add(String.join(", ", where));
			if (line.getWhere().containsKey(Holdings.Source.LEPRECHAUN))
			{
				parts.add("Withdraw leprechaun items at your first stop");
			}
		}
		if (line.getNote() != null)
		{
			parts.add(line.getNote());
		}
		if (line.getChangeIn() != null)
		{
			parts.add("Change in " + line.getChangeIn());
		}
		return "<html>" + String.join("<br>", escapeAll(parts)) + "</html>";
	}

	private static List<String> escapeAll(List<String> lines)
	{
		final List<String> escaped = new ArrayList<>();
		for (String line : lines)
		{
			escaped.add(UiText.escape(line));
		}
		return escaped;
	}

	private void finish()
	{
		revalidate();
		repaint();
	}

	private static JLabel heading(String text)
	{
		final JLabel label = new JLabel(UiText.wrap(text, TEXT_WIDTH));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(10, 0, 4, 0));
		return left(label);
	}

	private static JLabel note(String text)
	{
		final JLabel label = new JLabel(UiText.wrap(text, TEXT_WIDTH));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 0, 4, 0));
		return left(label);
	}

	private static <T extends JComponent> T left(T component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		return component;
	}
}
