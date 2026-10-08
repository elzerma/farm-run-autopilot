package com.farmrunautopilot.ui;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunOverrides;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.route.TypeOverride;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.tracking.PatchStatusText;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Run tab (SPEC 13.1): what the next run covers, the route, and the supply checklist.
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
	private final RunOverrides overrides;
	/** Ask for a new plan (after an override or reorder). */
	private final Runnable replan;
	/** Tell the other tabs a setting changed here (route mode after a reorder). */
	private final Runnable settingsChanged;
	private RunPlan plan = RunPlan.EMPTY;
	private boolean loggedIn;

	RunPanel(SettingsStore settings, RunOverrides overrides, Runnable replan, Runnable settingsChanged)
	{
		this.settings = settings;
		this.overrides = overrides;
		this.replan = replan;
		this.settingsChanged = settingsChanged;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		rebuild();
	}

	/** Show a new plan. Call on the Swing thread. */
	void update(RunPlan plan, boolean loggedIn)
	{
		this.plan = plan;
		this.loggedIn = loggedIn;
		rebuild();
	}

	private void rebuild()
	{
		removeAll();
		add(runTypeToggles());

		if (!loggedIn)
		{
			add(note("Log in to see your next run."));
			finish();
			return;
		}

		final SupplyPlan supplies = plan.getSupplies();
		add(heading(summary(supplies)));
		if (settings.getRunConfig().isSupplyFullRun())
		{
			final JLabel full = note("Counting every patch (full run is on in Rules > Testing & debug)");
			full.setForeground(ColorScheme.BRAND_ORANGE);
			add(full);
		}
		addRunTypeStatus(plan.getSelection());
		if (!supplies.getNotDue().isEmpty())
		{
			final JLabel notDue = note(supplies.getNotDue().size() + " patch"
				+ (supplies.getNotDue().size() == 1 ? " isn't" : "es aren't") + " due yet (hover for details)");
			notDue.setToolTipText("<html>" + String.join("<br>", escapeAll(supplies.getNotDue())) + "</html>");
			add(notDue);
		}
		for (String warning : supplies.getWarnings())
		{
			final JLabel label = note(warning);
			label.setForeground(ColorScheme.BRAND_ORANGE);
			add(label);
		}

		addRoute(plan.getRoute());

		if (!supplies.getLines().isEmpty())
		{
			add(legend());
		}
		SupplyLine.Group group = null;
		for (SupplyLine line : supplies.getLines())
		{
			if (line.getGroup() != group)
			{
				group = line.getGroup();
				add(heading(group.getDisplayName()));
			}
			add(row(line));
		}

		if (!supplies.getLines().isEmpty())
		{
			final Route route = plan.getRoute();
			add(heading("Totals"));
			add(note("Time: about " + PatchStatusText.duration((long) route.getTravelSeconds()) + " travelling, "
				+ PatchStatusText.duration((long) (route.getTravelSeconds() + route.getPatchSeconds())) + " in all"));
			add(note("Coins: " + String.format("%,d", supplies.getCoins())));
			if (!supplies.getRuneSummary().isEmpty())
			{
				add(note("Runes: " + supplies.getRuneSummary()));
			}
			add(note("Starting inventory: about " + supplies.getSlots() + " / 28 slots"));
		}
		finish();
	}

	// Run types

	private JComponent runTypeToggles()
	{
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
		return left(types);
	}

	/** One line per ticked run type: in this run or not, why, and a button to override it. */
	private void addRunTypeStatus(RunSelection selection)
	{
		final long now = Instant.now().getEpochSecond();
		final Map<PatchType, TypeOverride> current = overrides.get();
		for (PatchType type : PatchType.values())
		{
			if (!settings.getRunConfig().getEnabledTypes().contains(type))
			{
				continue;
			}
			final String name = type.getDisplayName() + "s";
			final TypeOverride override = current.get(type);
			final String text;
			final String buttonText;
			final TypeOverride buttonAction;
			if (selection.getIncludedTypes().contains(type))
			{
				text = name + ": " + selection.getDueCounts().getOrDefault(type, 0) + " due"
					+ (override == TypeOverride.INCLUDE ? " (included anyway)" : "");
				buttonText = override == TypeOverride.INCLUDE ? "Undo" : "Skip";
				buttonAction = override == TypeOverride.INCLUDE ? null : TypeOverride.SKIP;
			}
			else if (selection.getSkippedTypes().containsKey(type))
			{
				final long readyAt = selection.getSkippedTypes().get(type);
				if (override == TypeOverride.SKIP)
				{
					text = name + ": skipped this run";
					buttonText = "Undo";
					buttonAction = null;
				}
				else
				{
					text = name + ": ready " + (readyAt > now ? "in " + PatchStatusText.duration(readyAt - now) : "soon");
					buttonText = "Include";
					buttonAction = TypeOverride.INCLUDE;
				}
			}
			else
			{
				// Ticked but no usable patches (all locked or unticked)
				add(note(name + ": no patches available"));
				continue;
			}
			add(typeRow(text, buttonText, () ->
			{
				overrides.set(type, buttonAction);
				replan.run();
			}));
		}
	}

	private JComponent typeRow(String text, String buttonText, Runnable onClick)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		final JLabel label = new JLabel(UiText.wrap(text, TEXT_WIDTH - 70));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		final JButton button = new JButton(buttonText);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.addActionListener(e -> onClick.run());
		row.add(label, BorderLayout.CENTER);
		row.add(button, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return left(row);
	}

	private String summary(SupplyPlan supplies)
	{
		if (supplies.patchTotal() == 0)
		{
			return plan.getSelection().getSkippedTypes().isEmpty() ? "No patches selected" : "Nothing is due yet";
		}
		final List<String> parts = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			final int n = supplies.getPatchCounts().getOrDefault(type, 0);
			if (n > 0)
			{
				parts.add(n + " " + type.getDisplayName().toLowerCase() + (n == 1 ? "" : "s"));
			}
		}
		return "This run: " + String.join(", ", parts);
	}

	// Route

	private void addRoute(Route route)
	{
		if (route.getStops().isEmpty())
		{
			return;
		}
		add(heading("Route (" + modeName(route.getMode()) + ")"));

		final DefaultListModel<RouteStop> model = new DefaultListModel<>();
		for (RouteStop stop : route.getStops())
		{
			model.addElement(stop);
		}
		final JList<RouteStop> list = new JList<>(model);
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new StopRenderer());
		list.setDragEnabled(true);
		list.setDropMode(DropMode.INSERT);
		list.setTransferHandler(new ReorderHandler(list, model));
		add(left(list));

		add(note(route.getMode() == RouteMode.OFF
			? "Your own order. Drag stops to change it; switch back in Setup > Run options."
			: "Drag a stop to reorder (switches to your own order)."));
	}

	private static String modeName(RouteMode mode)
	{
		switch (mode)
		{
			case META:
				return "wiki order";
			case OFF:
				return "your order";
			default:
				return "fastest";
		}
	}

	/** Saves the dragged order as the player's own and switches the route to it (SPEC 12.1). */
	private void saveOrder(DefaultListModel<RouteStop> model)
	{
		final List<Location> order = new ArrayList<>();
		for (int i = 0; i < model.size(); i++)
		{
			order.add(model.get(i).getLocation());
		}
		// Keep stops that aren't in this run where they were in the saved order
		for (Location location : settings.getRunConfig().getCustomOrder())
		{
			if (!order.contains(location))
			{
				order.add(location);
			}
		}
		settings.getRunConfig().setCustomOrder(order);
		settings.getRunConfig().setRouteMode(RouteMode.OFF);
		settings.saveRunConfig();
		settingsChanged.run();
	}

	private static final class StopRenderer implements ListCellRenderer<RouteStop>
	{
		@Override
		public Component getListCellRendererComponent(JList<? extends RouteStop> list, RouteStop stop, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			final JPanel cell = new JPanel(new BorderLayout());
			cell.setBackground(isSelected ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			cell.setBorder(new EmptyBorder(3, 6, 3, 6));

			final JLabel name = new JLabel((index + 1) + ". " + stop.getLocation().getDisplayName());
			name.setForeground(ColorScheme.TEXT_COLOR);
			name.setFont(FontManager.getRunescapeSmallFont());
			final JLabel travel = new JLabel(UiText.wrap(stop.describeTravel()
				+ " - about " + PatchStatusText.duration((long) stop.getLegSeconds()), TEXT_WIDTH - 20));
			travel.setForeground(stop.isNeedsSupplies() ? IN_STORAGE : ColorScheme.LIGHT_GRAY_COLOR);
			travel.setFont(FontManager.getRunescapeSmallFont());

			cell.add(name, BorderLayout.NORTH);
			cell.add(travel, BorderLayout.CENTER);
			final JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
			wrapper.add(cell);
			return wrapper;
		}
	}

	/** Drag-and-drop reordering within the route list. */
	private final class ReorderHandler extends TransferHandler
	{
		private final JList<RouteStop> list;
		private final DefaultListModel<RouteStop> model;
		private int from = -1;

		ReorderHandler(JList<RouteStop> list, DefaultListModel<RouteStop> model)
		{
			this.list = list;
			this.model = model;
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return MOVE;
		}

		@Override
		protected Transferable createTransferable(JComponent c)
		{
			from = list.getSelectedIndex();
			return new StringSelection(Integer.toString(from));
		}

		@Override
		public boolean canImport(TransferSupport support)
		{
			return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor) && from >= 0;
		}

		@Override
		public boolean importData(TransferSupport support)
		{
			if (!canImport(support))
			{
				return false;
			}
			int to = ((JList.DropLocation) support.getDropLocation()).getIndex();
			if (to == from || to == from + 1)
			{
				return false;
			}
			final RouteStop moved = model.remove(from);
			if (to > from)
			{
				to--;
			}
			model.add(to, moved);
			from = -1;
			saveOrder(model);
			return true;
		}
	}

	// Supplies

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

	/** Colour key, so the three colours explain themselves. */
	private static JComponent legend()
	{
		final JLabel label = new JLabel("<html><font color='#4CC152'>Carried</font>  "
			+ "<font color='#E8C53A'>In storage</font>  <font color='#E05555'>Missing</font></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(8, 0, 0, 0));
		return left(label);
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
