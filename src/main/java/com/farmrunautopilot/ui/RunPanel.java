package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.route.BankStop;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunOverrides;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.route.TypeOverride;
import com.farmrunautopilot.run.GuidanceOverlay;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.testing.TestView;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchStatusText;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
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
	/** Shown in the preset picker when the settings no longer match any preset. */
	private static final String EDITED_PRESET = "Custom (changed)";

	private final SettingsStore settings;
	private final RunOverrides overrides;
	/** Ask for a new plan (after an override or reorder). */
	private final Runnable replan;
	/** Tell the other tabs a setting changed here (route mode after a reorder). */
	private final Runnable settingsChanged;
	private final RunControls controls;
	private final PatchTracker patchTracker;
	private final AccessChecker accessChecker;
	private RunPlan plan = RunPlan.EMPTY;
	private boolean loggedIn;
	/** The running timer, updated every second without rebuilding the panel. */
	private JLabel clockLabel;
	private final Timer clockTimer;
	/** Patch timers while off, refreshed without rebuilding the panel; null otherwise. */
	private JPanel patchTimers;

	/** Build, cancel, start, stop and skip, plus the session's current view. */
	interface RunControls
	{
		RunView view();

		void build();

		void cancel();

		void startNow();

		void stop();

		void skip();

		/** Show Account > Unlocks. */
		void openUnlocks();

		/** The seed vault has been opened on this account, so its contents are counted. */
		boolean seedVaultSeen();

		/** The guided test in progress, or {@link TestView#NONE}. */
		TestView testView();

		void answerTest(boolean yes);

		void skipTestStep();

		void cancelTest();

		/** Open a finished test's pre-filled GitHub issue. */
		void openTestReport();

		/** Close a finished test's last step. */
		void closeTestReport();
	}

	RunPanel(SettingsStore settings, RunOverrides overrides, Runnable replan, Runnable settingsChanged,
		PatchTracker patchTracker, AccessChecker accessChecker, RunControls controls)
	{
		this.settings = settings;
		this.overrides = overrides;
		this.replan = replan;
		this.settingsChanged = settingsChanged;
		this.patchTracker = patchTracker;
		this.accessChecker = accessChecker;
		this.controls = controls;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		clockTimer = new Timer(1000, e -> updateClock());
		clockTimer.start();
		rebuild();
	}

	void shutDown()
	{
		clockTimer.stop();
	}

	private void updateClock()
	{
		final RunView view = controls.view();
		if (clockLabel == null)
		{
			return;
		}
		if (view.getState() == RunView.State.RUNNING)
		{
			clockLabel.setText("Running " + GuidanceOverlay.clock((System.currentTimeMillis() - view.getStartedAtMillis()) / 1000));
		}
		else if (view.getState() == RunView.State.ARMED)
		{
			clockLabel.setText("Armed");
			clockLabel.setToolTipText("The timer starts when you teleport or click your first patch");
		}
	}

	/** Re-read the patch timers shown while off. Call on the Swing thread. */
	void refreshPatches()
	{
		if (patchTimers != null)
		{
			fillPatchTimers(patchTimers);
			finish();
		}
	}

	/** Show a new plan. Call on the Swing thread. */
	void update(RunPlan plan, boolean loggedIn)
	{
		this.plan = plan;
		this.loggedIn = loggedIn;
		rebuild();
	}

	/** Redraw from the current settings (e.g. a preset was picked). Call on the Swing thread. */
	void refresh()
	{
		rebuild();
	}

	private void rebuild()
	{
		removeAll();
		addTest(controls.testView());
		final JComponent presets = presetPicker();
		if (presets != null)
		{
			add(presets);
		}
		add(runTypeToggles());

		if (!loggedIn)
		{
			add(note("Log in to see your next run."));
			finish();
			return;
		}

		final SupplyPlan supplies = plan.getSupplies();
		final RunView view = controls.view();
		clockLabel = null;
		patchTimers = null;
		if (view.getState() == RunView.State.RUNNING || view.getState() == RunView.State.ARMED)
		{
			addRunning(view);
			finish();
			return;
		}
		if (view.getState() == RunView.State.OFF)
		{
			addOff(view);
			addUnlocksToCheck();
			addSeedVaultNote();
			finish();
			return;
		}
		addBuilding();
		add(heading(summary(supplies)));
		if (settings.getRunConfig().isSupplyFullRun())
		{
			final JLabel full = note("Counting every patch (full run is on in Account > Testing & debug)");
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
		addUnlocksToCheck();
		addSeedVaultNote();

		addRoute(plan.getRoute());
		addBankStop(plan);
		addSupplies(supplies);
		addTotals(supplies);
		finish();
	}

	/** Until the seed vault has been opened, its seeds and saplings can't be counted: say so. */
	private void addSeedVaultNote()
	{
		if (settings.getRunConfig().isUseSeedVault() && !controls.seedVaultSeen())
		{
			add(note("Open your seed vault at the Farming Guild once, so seeds and saplings in it count."));
		}
	}

	/** The guided test in progress at the top of the tab: its step, Yes/No when it asks, and what to bring. */
	private void addTest(TestView test)
	{
		if (!test.isActive())
		{
			return;
		}
		final JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(2, 2, 2, 2, ColorScheme.BRAND_ORANGE), new EmptyBorder(6, 6, 6, 6)));
		box.setAlignmentX(LEFT_ALIGNMENT);

		final JLabel title = testText("TEST: " + test.getTitle(), ColorScheme.BRAND_ORANGE);
		title.setFont(FontManager.getRunescapeBoldFont());
		box.add(title);
		if (test.isFinished())
		{
			box.add(testText(test.getText(), Color.WHITE));
			if (test.isReporting())
			{
				// The last step: the player sends the report themselves
				box.add(testButtons(testButton(
					"<html><center>Open GitHub issues with<br>prefilled test results</center></html>",
					controls::openTestReport)));
			}
			box.add(testButtons(testButton("Close", controls::closeTestReport)));
			box.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, Integer.MAX_VALUE));
			add(box);
			return;
		}
		box.add(testText("Step " + test.getStep() + " of " + test.getSteps(), ColorScheme.LIGHT_GRAY_COLOR));
		box.add(testText(test.getText(), Color.WHITE));
		if (!test.getBring().isEmpty())
		{
			// Any one of the items will do
			box.add(testText(test.getBring().size() == 1 ? "Bring:" : "Bring one of:", ColorScheme.LIGHT_GRAY_COLOR));
			for (String line : test.getBring())
			{
				box.add(testText("- " + line, ColorScheme.LIGHT_GRAY_COLOR));
			}
		}

		if (test.isQuestion())
		{
			box.add(testButtons(testButton("Yes", () -> controls.answerTest(true)),
				testButton("No", () -> controls.answerTest(false))));
		}
		box.add(test.isOptional()
			? testButtons(testButton("Skip", controls::skipTestStep), testButton("Cancel", controls::cancelTest))
			: testButtons(testButton("Cancel", controls::cancelTest)));
		box.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, Integer.MAX_VALUE));
		add(box);
		final JLabel gap = new JLabel(" ");
		gap.setAlignmentX(LEFT_ALIGNMENT);
		add(gap);
	}

	/** A row of buttons sharing the box's width. */
	private static JPanel testButtons(JButton... buttons)
	{
		final JPanel row = new JPanel(new GridLayout(1, 0, 4, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(0, 0, 4, 0));
		for (JButton button : buttons)
		{
			row.add(button);
		}
		return row;
	}

	private static JLabel testText(String text, Color colour)
	{
		// Narrower than the box: the orange border, padding and scroll bar take about 50px
		final JLabel label = new JLabel("<html><div style='width:" + (TEXT_WIDTH - 55) + "px'>"
			+ text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
		label.setForeground(colour);
		label.setFont(FontManager.getRunescapeFont());
		label.setBorder(new EmptyBorder(0, 0, 4, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JButton testButton(String text, Runnable action)
	{
		final JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.addActionListener(e -> action.run());
		return button;
	}

	/**
	 * Unlocks the plugin can't read that would change this run: one orange line that opens Account > Unlocks,
	 * until the player has checked them.
	 */
	private void addUnlocksToCheck()
	{
		final List<Unlock> review = AccessChecker.toReview(settings.getRunConfig(), settings.getAccount());
		if (review.isEmpty())
		{
			return;
		}
		final JLabel label = note("Check your unlocks: " + review.size() + " not seen yet"
			+ " (click to open)");
		label.setForeground(ColorScheme.BRAND_ORANGE);
		final List<String> names = new ArrayList<>();
		for (Unlock unlock : review)
		{
			names.add(unlock.getDescription());
		}
		label.setToolTipText("<html>Spirit trees and the Weiss fire tick when you pass them; tick the rest by hand:<br>" + String.join("<br>", escapeAll(names))
			+ "</html>");
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				controls.openUnlocks();
			}
		});
		add(label);
	}

	private void addSupplies(SupplyPlan supplies)
	{
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
	}

	private void addTotals(SupplyPlan supplies)
	{
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
	}

	// Off, building and running

	/** Not planning a run: what's due, patch timers and a Build run button. Nothing shows in game. */
	private void addOff(RunView view)
	{
		final JButton build = new JButton("Build run");
		build.setEnabled(!plan.getRoute().getStops().isEmpty());
		build.setFocusPainted(false);
		build.setToolTipText("Show the farm run bank tab and gather supplies. The run starts itself once you have them.");
		build.addActionListener(e -> controls.build());
		build.setAlignmentX(LEFT_ALIGNMENT);
		build.setMaximumSize(new Dimension(Integer.MAX_VALUE, build.getPreferredSize().height));
		add(build);

		add(heading(summary(plan.getSupplies())));
		addRunTypeStatus(plan.getSelection());

		add(heading("Patch timers"));
		patchTimers = new JPanel();
		patchTimers.setLayout(new BoxLayout(patchTimers, BoxLayout.Y_AXIS));
		patchTimers.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fillPatchTimers(patchTimers);
		add(left(patchTimers));

		if (view.getLastRun() != null || view.getBestTimes() != null)
		{
			add(heading("Times"));
		}
		if (view.getLastRun() != null)
		{
			add(note(view.getLastRun()));
		}
		if (view.getBestTimes() != null)
		{
			add(note(view.getBestTimes()));
		}
	}

	/** Every usable, selected patch of each ticked type, soonest ready first. */
	private void fillPatchTimers(JPanel panel)
	{
		panel.removeAll();
		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final long now = Instant.now().getEpochSecond();
		boolean any = false;
		for (PatchType type : PatchType.values())
		{
			if (!config.getEnabledTypes().contains(type))
			{
				continue;
			}
			final List<Patch> patches = new ArrayList<>();
			final Map<Patch, PatchPrediction> predictions = new EnumMap<>(Patch.class);
			for (Patch patch : Patch.values())
			{
				if (patch.getType() == type && config.isPatchSelected(patch) && access.missingFor(patch).isEmpty())
				{
					patches.add(patch);
					predictions.put(patch, patchTracker.predict(patch));
				}
			}
			if (patches.isEmpty())
			{
				continue;
			}
			// Due patches first, then by when they'll be ready
			patches.sort(Comparator.comparingLong(p -> RunSelector.isDue(predictions.get(p))
				? Long.MIN_VALUE : predictions.get(p).getDoneAt()));
			final JLabel title = new JLabel(type.getDisplayName() + "s");
			title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			title.setFont(FontManager.getRunescapeSmallFont());
			title.setBorder(new EmptyBorder(any ? 6 : 0, 0, 2, 0));
			panel.add(left(title));
			for (Patch patch : patches)
			{
				panel.add(patchTimer(patch, predictions.get(patch), now));
			}
			any = true;
		}
		if (!any)
		{
			panel.add(note("No patches selected"));
		}
	}

	private static JComponent patchTimer(Patch patch, PatchPrediction prediction, long now)
	{
		final JPanel cell = new JPanel(new BorderLayout());
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(new EmptyBorder(3, 6, 3, 6));
		final JLabel name = new JLabel(UiText.wrap(patch.getLocation().getDisplayName(), TEXT_WIDTH - 20));
		name.setForeground(ColorScheme.TEXT_COLOR);
		name.setFont(FontManager.getRunescapeSmallFont());
		final JLabel status = new JLabel(UiText.wrap(PatchStatusText.describe(prediction, now), TEXT_WIDTH - 20));
		status.setForeground(RunSelector.isDue(prediction) ? CARRIED : ColorScheme.LIGHT_GRAY_COLOR);
		status.setFont(FontManager.getRunescapeSmallFont());
		cell.add(name, BorderLayout.NORTH);
		cell.add(status, BorderLayout.CENTER);

		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
		wrapper.add(cell, BorderLayout.CENTER);
		return left(wrapper);
	}

	/** Gathering supplies: cancel, or start without everything. */
	private void addBuilding()
	{
		final JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		final JButton startNow = new JButton("Start now");
		startNow.setFocusPainted(false);
		startNow.setEnabled(!plan.getRoute().getStops().isEmpty());
		startNow.setToolTipText("Start the run and its timer without everything");
		startNow.addActionListener(e -> controls.startNow());
		final JButton cancel = new JButton("Cancel");
		cancel.setFocusPainted(false);
		cancel.addActionListener(e -> controls.cancel());
		buttons.add(startNow);
		buttons.add(cancel);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
		add(left(buttons));
		if (!plan.getRoute().getStops().isEmpty())
		{
			final JLabel ready = note("Grab the yellow and red items. The run starts itself once you have them; "
				+ "the timer starts when you teleport or click your first patch.");
			ready.setForeground(IN_STORAGE);
			add(ready);
		}
	}

	/** Hover text for a stop: the plan for each patch there. */
	private static String objectivesTooltip(List<String> objectives)
	{
		return objectives == null || objectives.isEmpty() ? null
			: "<html>" + String.join("<br>", escapeAll(objectives)) + "</html>";
	}

	private void addRunning(RunView view)
	{
		final JPanel bar = new JPanel(new BorderLayout(6, 0));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		clockLabel = new JLabel();
		clockLabel.setForeground(ColorScheme.BRAND_ORANGE);
		updateClock();
		final JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		if (view.getState() == RunView.State.ARMED)
		{
			buttons.add(smallButton("Start now", controls::startNow));
			buttons.add(smallButton("Cancel", controls::cancel));
		}
		else
		{
			buttons.add(smallButton("Skip step", controls::skip));
			buttons.add(smallButton("Stop", controls::stop));
		}
		bar.add(clockLabel, BorderLayout.CENTER);
		bar.add(buttons, BorderLayout.EAST);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
		add(left(bar));

		add(heading("Route"));
		int number = 1;
		for (RunView.Stop routeStop : view.getStops())
		{
			add(runningStop(number++, routeStop));
		}
	}

	private static JButton smallButton(String text, Runnable onClick)
	{
		final JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.addActionListener(e -> onClick.run());
		return button;
	}

	/** A stop while running: struck through when done, highlighted with its next step when current. */
	private JComponent runningStop(int number, RunView.Stop stop)
	{
		final JPanel cell = new JPanel(new BorderLayout());
		final boolean current = stop.getStatus() == RunView.StopStatus.CURRENT;
		cell.setBackground(current ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(new EmptyBorder(3, 6, 3, 6));

		final String name = UiText.escape(number + ". " + stop.getLocation());
		final JLabel title = new JLabel(stop.getStatus() == RunView.StopStatus.DONE
			? "<html><s>" + name + "</s></html>" : "<html>" + name + "</html>");
		title.setForeground(stop.getStatus() == RunView.StopStatus.DONE ? ColorScheme.MEDIUM_GRAY_COLOR
			: current ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
		title.setFont(FontManager.getRunescapeSmallFont());
		cell.add(title, BorderLayout.NORTH);

		if (stop.getStatus() != RunView.StopStatus.DONE)
		{
			final JLabel detail = new JLabel(UiText.wrap(current && stop.getInstruction() != null
				? stop.getInstruction() : stop.getTravel(), TEXT_WIDTH - 20));
			detail.setForeground(current ? IN_STORAGE : ColorScheme.LIGHT_GRAY_COLOR);
			detail.setFont(FontManager.getRunescapeSmallFont());
			cell.add(detail, BorderLayout.CENTER);
		}

		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
		wrapper.add(cell, BorderLayout.CENTER);
		wrapper.setToolTipText(objectivesTooltip(stop.getObjectives()));
		return left(wrapper);
	}

	// Presets

	/** One-click switch between saved presets (SPEC 13.5), or null if none are saved. */
	private JComponent presetPicker()
	{
		final List<String> names = settings.presetNames();
		final RunView.State state = controls.view().getState();
		if (names.isEmpty() || state == RunView.State.ARMED || state == RunView.State.RUNNING)
		{
			return null;
		}
		final String active = settings.activePreset();
		final JComboBox<String> combo = new JComboBox<>();
		if (active == null)
		{
			combo.addItem(EDITED_PRESET);
		}
		for (String name : names)
		{
			combo.addItem(name);
		}
		combo.setSelectedItem(active != null ? active : EDITED_PRESET);
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setToolTipText("Switch to a saved preset. Save and manage presets under Manage presets below.");
		combo.addActionListener(e ->
		{
			final Object picked = combo.getSelectedItem();
			if (picked != null && !EDITED_PRESET.equals(picked) && !picked.equals(active))
			{
				settings.applyPreset((String) picked);
			}
		});

		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(0, 0, 4, 0));
		final JLabel label = new JLabel("Preset");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.WEST);
		row.add(combo, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return left(row);
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
			? "Your own order. Drag stops to change it; switch back in Travel > Route."
			: "Drag a stop to reorder (switches to your own order)."));
	}

	/** A run too big for one inventory: where it banks partway, and what to take there. */
	private void addBankStop(RunPlan plan)
	{
		final BankStop bank = plan.getBankStop();
		if (bank == null)
		{
			return;
		}
		final List<RouteStop> stops = plan.getRoute().getStops();
		add(heading("Bank stop"));
		add(note("Everything doesn't fit in one inventory, so the run banks near "
			+ bank.getLocation().getDisplayName() + " after stop " + (bank.getAfterStop() + 1) + ". The supply list "
			+ "below is for the stops before it; at the bank, deposit what's outlined and take the rest from the "
			+ "Farm run bank tab:"));
		for (SupplyLine line : bank.getSupplies().getLines())
		{
			if (line.getGroup() != SupplyLine.Group.OPTIONAL && !line.isCoveredOtherwise())
			{
				add(note("- " + line.getNeed() + " x " + line.getName()));
			}
		}
		if (bank.getAfterStop() + 1 < stops.size())
		{
			add(note("Then on to " + stops.get(bank.getAfterStop() + 1).getLocation().getDisplayName() + "."));
		}
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

	private final class StopRenderer implements ListCellRenderer<RouteStop>
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
			final boolean here = index == 0 && controls.view().isAtFirstStop();
			final JLabel travel = new JLabel(UiText.wrap(here ? "You're here" : stop.describeTravel()
				+ " - about " + PatchStatusText.duration((long) stop.getLegSeconds()), TEXT_WIDTH - 20));
			travel.setForeground(stop.isNeedsSupplies() ? IN_STORAGE : ColorScheme.LIGHT_GRAY_COLOR);
			travel.setFont(FontManager.getRunescapeSmallFont());

			cell.add(name, BorderLayout.NORTH);
			cell.add(travel, BorderLayout.CENTER);
			final JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
			wrapper.add(cell);
			// JList shows the renderer's tooltip for the hovered row
			wrapper.setToolTipText(objectivesTooltip(plan.getObjectives().get(stop.getLocation())));
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
