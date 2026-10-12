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
import java.awt.Insets;
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
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
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

		/** Skip everything left at the current stop: getting there, its patches and its bank visit. */
		void skipStop();

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
		// Shown only with the complete route, i.e. once the run is built (Sean)
		add(heading("Route summary"));
		addTime();
		if (settings.editRunConfig().isSupplyFullRun())
		{
			final JLabel full = tipped(note("Counting every patch"), "Full run is on in Account > Testing & debug");
			full.setForeground(ColorScheme.BRAND_ORANGE);
			add(full);
		}
		addParts();
		addRunTypeStatus(plan.getSelection());
		if (!supplies.getNotDue().isEmpty())
		{
			final JLabel notDue = note(supplies.getNotDue().size() + " patch"
				+ (supplies.getNotDue().size() == 1 ? " isn't" : "es aren't") + " due yet");
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

		// The items themselves are on the Farm run bank tab, so they aren't listed here too (docs/REBUILD.md, 5.5)
		addRoute(plan.getRoute());
		finish();
	}

	/** Until the seed vault has been opened, its seeds and saplings can't be counted: say so. */
	private void addSeedVaultNote()
	{
		if (settings.editRunConfig().isUseSeedVault() && !controls.seedVaultSeen())
		{
			add(tipped(note("Open your seed vault once"),
				"At the Farming Guild, so the seeds and saplings in it count"));
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
		button.setMargin(new Insets(2, 2, 2, 2));
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
		final List<Unlock> review = AccessChecker.toReview(settings.editRunConfig(), settings.editAccount());
		if (review.isEmpty())
		{
			return;
		}
		// Which ones, and how they get ticked, is in Account > Unlocks: only the count here (said once, Sean)
		final JLabel label = tipped(note("Check your unlocks: " + review.size() + " not seen yet"),
			"Click to open Account > Unlocks");
		label.setForeground(ColorScheme.BRAND_ORANGE);
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

	/** How long the run should take, first in the route summary. */
	private void addTime()
	{
		final Route route = plan.getRoute();
		if (!route.getStops().isEmpty())
		{
			add(note("About " + PatchStatusText.duration((long) (route.getTravelSeconds() + route.getPatchSeconds()))
				+ " (" + PatchStatusText.duration((long) route.getTravelSeconds()) + " travelling)"));
		}
	}

	/**
	 * The run's patches by type, one line per inventory, with each bank stop between them (Sean): e.g. "4 trees,
	 * 6 herbs (18/28 slots)", "Bank near Catherby", "3 trees, 2 herbs (12/28 slots)". The slots are here, not
	 * in Totals.
	 */
	private void addParts()
	{
		final List<RouteStop> stops = plan.getRoute().getStops();
		if (stops.isEmpty())
		{
			return;
		}
		Map<PatchType, Integer> counts = new EnumMap<>(PatchType.class);
		int slots = plan.getSupplies().getSlots();
		for (int i = 0; i < stops.size(); i++)
		{
			for (Patch patch : plan.getSelection().getPatches())
			{
				if (patch.getLocation() == stops.get(i).getLocation())
				{
					counts.merge(patch.getType(), 1, Integer::sum);
				}
			}
			final BankStop bank = plan.bankAfter(i);
			if (bank != null || i == stops.size() - 1)
			{
				add(tipped(note(partText(counts) + " (" + slots + "/28 slots)"),
					"Patches done with this inventory, and the slots it needs"));
				counts = new EnumMap<>(PatchType.class);
			}
			if (bank != null)
			{
				final JLabel banking = note(bank.title());
				banking.setForeground(ColorScheme.BRAND_ORANGE);
				add(banking);
				slots = bank.getSupplies().getSlots();
			}
		}
	}

	/** "4 trees, 1 fruit tree, 6 herbs". */
	private static String partText(Map<PatchType, Integer> counts)
	{
		final List<String> parts = new ArrayList<>();
		for (Map.Entry<PatchType, Integer> e : counts.entrySet())
		{
			final int n = e.getValue();
			parts.add(n + " " + e.getKey().getDisplayName().toLowerCase() + (n == 1 ? "" : "s"));
		}
		return parts.isEmpty() ? "No patches" : String.join(", ", parts);
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

		// No route summary until the run is built: only the types to include (Sean)
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
		final RunConfig config = settings.editRunConfig();
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
		// The next step (open a bank, the Farm run tab...) is under the player only: said once (Sean)
	}

	/** Hover text for a stop: the plan for each patch there. */
	private static String objectivesTooltip(List<String> objectives)
	{
		return objectives == null || objectives.isEmpty() ? null
			: "<html>" + String.join("<br>", escapeAll(objectives)) + "</html>";
	}

	private void addRunning(RunView view)
	{
		// The clock on its own line, then the buttons across the full width, so no label is cut off
		final JPanel bar = new JPanel(new BorderLayout(0, 4));
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		clockLabel = new JLabel();
		clockLabel.setForeground(ColorScheme.BRAND_ORANGE);
		updateClock();
		// Two to a row: three across the sidebar cut their labels off (Sean)
		final JPanel buttons = new JPanel(new GridLayout(0, 2, 4, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		if (view.getState() == RunView.State.ARMED)
		{
			buttons.add(smallButton("Start now", controls::startNow));
			buttons.add(smallButton("Cancel", controls::cancel));
		}
		else
		{
			buttons.add(smallButton("Mark done", controls::skip));
			buttons.add(smallButton("Skip stop", controls::skipStop));
			buttons.add(smallButton("Stop", controls::stop));
		}
		bar.add(clockLabel, BorderLayout.NORTH);
		bar.add(buttons, BorderLayout.CENTER);
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
	/** One step of the current stop: struck through when done, "> " and orange when in hand, "- " when to come. */
	private static JLabel stepLabel(RunView.Step step)
	{
		final String text = UiText.escape(step.getText());
		final String open = "<html><body style='width:" + ((TEXT_WIDTH - 30) * 10 / 13) + "px'>";
		final String close = "</body></html>";
		final JLabel label;
		switch (step.getStatus())
		{
			case DONE:
				label = new JLabel(open + "<s>" + text + "</s>" + close);
				label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				break;
			case CURRENT:
				label = new JLabel(open + "&gt; " + text + close);
				label.setForeground(ColorScheme.BRAND_ORANGE);
				break;
			default:
				label = new JLabel(open + "- " + text + close);
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				break;
		}
		label.setFont(FontManager.getRunescapeSmallFont());
		// Journey steps sit further in, under the teleport they follow
		label.setBorder(new EmptyBorder(1, step.isSub() ? 16 : 4, 1, 0));
		return label;
	}

	private JComponent runningStop(int number, RunView.Stop stop)
	{
		final JPanel cell = new JPanel(new BorderLayout());
		final boolean current = stop.getStatus() == RunView.StopStatus.CURRENT;
		cell.setBackground(current ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		// The current stop is boxed in orange (Sean)
		cell.setBorder(current
			? new CompoundBorder(new LineBorder(ColorScheme.BRAND_ORANGE, 1), new EmptyBorder(3, 6, 3, 6))
			: new EmptyBorder(3, 6, 3, 6));

		final String name = UiText.escape(number + ". " + stop.getLocation());
		final JLabel title = new JLabel(stop.getStatus() == RunView.StopStatus.DONE
			? "<html><s>" + name + "</s></html>" : "<html>" + name + "</html>");
		title.setForeground(stop.getStatus() == RunView.StopStatus.DONE ? ColorScheme.MEDIUM_GRAY_COLOR
			: current ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
		title.setFont(current ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		cell.add(title, BorderLayout.NORTH);

		if (current && !stop.getSteps().isEmpty())
		{
			// Every step of this stop, done ones struck through, the one in hand marked. The full instruction for it
			// is under the player, so it isn't repeated here (Sean: say each thing in one place)
			final JPanel steps = new JPanel();
			steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
			steps.setOpaque(false);
			for (RunView.Step step : stop.getSteps())
			{
				steps.add(stepLabel(step));
			}
			cell.add(steps, BorderLayout.CENTER);
		}
		else if (!current && stop.getStatus() != RunView.StopStatus.DONE)
		{
			final JLabel detail = new JLabel(UiText.wrap(stop.getTravel(), TEXT_WIDTH - 20));
			detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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
				settings.editRunConfig().getEnabledTypes().contains(type));
			box.setToolTipText("Include " + type.getDisplayName().toLowerCase() + " patches in this run");
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setFont(FontManager.getRunescapeSmallFont());
			box.addActionListener(e ->
			{
				if (box.isSelected())
				{
					settings.editRunConfig().getEnabledTypes().add(type);
				}
				else
				{
					settings.editRunConfig().getEnabledTypes().remove(type);
				}
				settings.saveRunConfig();
				// The Travel tab's locations and the preset picker depend on the run types
				settingsChanged.run();
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
			if (!settings.editRunConfig().getEnabledTypes().contains(type))
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
				if (override != TypeOverride.INCLUDE)
				{
					// Counted in the parts above; no Skip button: unticking the type at the top does that (Sean)
					continue;
				}
				text = name + ": included anyway";
				buttonText = "Undo";
				buttonAction = null;
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

	// Route

	private void addRoute(Route route)
	{
		if (route.getStops().isEmpty())
		{
			return;
		}
		add(tipped(heading("Complete route"), route.getMode() == RouteMode.OFF
			? "Your own order. Drag stops to change it; switch back in Travel > Route."
			: "Order: " + modeName(route.getMode()) + ". Drag a stop to reorder it (switches to your own order). "
				+ "Hover a stop for its plan."));

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

	}

	/** A leg's time: "25 s" (to the nearest 5) under two minutes, otherwise "2m 10s". */
	static String legTime(double seconds)
	{
		final long rounded = Math.max(5, Math.round(seconds / 5) * 5);
		if (rounded < 120)
		{
			return rounded + " s";
		}
		return rounded / 60 + "m " + (rounded % 60 < 10 ? "0" : "") + rounded % 60 + "s";
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
		for (Location location : settings.editRunConfig().getCustomOrder())
		{
			if (!order.contains(location))
			{
				order.add(location);
			}
		}
		settings.editRunConfig().setCustomOrder(order);
		settings.editRunConfig().setRouteMode(RouteMode.OFF);
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
				+ " - about " + legTime(stop.getLegSeconds()), TEXT_WIDTH - 20));
			travel.setForeground(stop.isNeedsSupplies() ? IN_STORAGE : ColorScheme.LIGHT_GRAY_COLOR);
			travel.setFont(FontManager.getRunescapeSmallFont());

			cell.add(name, BorderLayout.NORTH);
			cell.add(travel, BorderLayout.CENTER);
			// A bank break after this stop shows in place, under it
			final BankStop bank = plan.bankAfter(index);
			if (bank != null)
			{
				final JLabel banking = new JLabel(UiText.wrap("Then bank: " + bank.how(), TEXT_WIDTH - 20));
				banking.setForeground(ColorScheme.BRAND_ORANGE);
				banking.setFont(FontManager.getRunescapeSmallFont());
				cell.add(banking, BorderLayout.SOUTH);
			}
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

	/** The same label with an explanation on hover. */
	private static JLabel tipped(JLabel label, String text)
	{
		label.setToolTipText(UiText.wrap(text, 250));
		return label;
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
