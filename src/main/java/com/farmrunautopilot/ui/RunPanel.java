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
import java.awt.FlowLayout;
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
	// The sidebar's width less its 6 px edges and a little for the scroll bar (Sean: text stopped short of the card's edge)
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 16;
	/** The run clock, big enough to read at a glance (Sean). */
	private static final float CLOCK_FONT_SIZE = 24f;
	/** Tooltips wrap at this width (in the 13/10 CSS pixels Swing's HTML uses), about the sidebar's. */
	private static final int TOOLTIP_WIDTH = 190;
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

		/** A step of the current stop ticked or unticked by hand. */
		void setStepTicked(int step, boolean ticked);

		/** A stop or bank visit row ticked or unticked by hand (row index, bank visits included). */
		void setRowTicked(int row, boolean ticked);

		/** Show Account > Unlocks. */
		void openUnlocks();

		/** Show Settings > Presets. */
		void openPresets();

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
			clockLabel.setText(GuidanceOverlay.clock((System.currentTimeMillis() - view.getStartedAtMillis()) / 1000));
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
			final JLabel full = tipped(note("Counting every patch"), "Full run is on in Settings > Debug");
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
		final Card box = new Card(null).accent(ColorScheme.BRAND_ORANGE);
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setAlignmentX(LEFT_ALIGNMENT);

		// White like every title; the orange bar marks it (Sprint 7)
		final JLabel title = testText("Test: " + test.getTitle(), Color.WHITE);
		title.setFont(FontManager.getRunescapeFont());
		box.add(title);
		if (test.isFinished())
		{
			box.add(testText(test.getText(), Color.WHITE));
			if (test.isReporting())
			{
				// The last step: the player sends the report themselves
				box.add(testButtons(Style.primary(testButton(
					"<html><center>Open GitHub issues with<br>prefilled test results</center></html>",
					controls::openTestReport))));
			}
			box.add(testButtons(testButton("Close", controls::closeTestReport)));
			box.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, Integer.MAX_VALUE));
			add(box);
			return;
		}
		box.add(testText("Step " + test.getStep() + " of " + test.getSteps(), Color.WHITE));
		box.add(testText(test.getText(), Color.WHITE));
		if (!test.getBring().isEmpty())
		{
			// Any one of the items will do
			box.add(testText(test.getBring().size() == 1 ? "Bring:" : "Bring one of:", Color.WHITE));
			for (String line : test.getBring())
			{
				box.add(testText("- " + line, Color.WHITE));
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
		row.setBorder(new EmptyBorder(0, 0, 3, 0));
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
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 0, 3, 0));
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
		return Style.button(button);
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
		final JButton build = Style.primary(new JButton("Build run"));
		build.setEnabled(!plan.getRoute().getStops().isEmpty());
		build.setFocusPainted(false);
		build.setToolTipText("Show the farm run bank tab and gather supplies. The run starts itself once you have them.");
		build.addActionListener(e -> controls.build());
		build.setAlignmentX(LEFT_ALIGNMENT);
		build.setMaximumSize(new Dimension(Integer.MAX_VALUE, build.getPreferredSize().height));
		add(build);
		// How the last run went, one quiet line under the button; the best times are its tooltip (Sean)
		final String times = view.getLastRun() != null ? view.getLastRun() : view.getBestTimes();
		if (times != null)
		{
			final JLabel line = note(times);
			line.setBorder(new EmptyBorder(4, 0, 0, 0));
			if (view.getLastRun() != null && view.getBestTimes() != null)
			{
				tipped(line, view.getBestTimes());
			}
			add(line);
		}

		// No route summary until the run is built: only the types to include (Sean)
		addRunTypeStatus(plan.getSelection());

		add(heading("Patch timers"));
		patchTimers = new JPanel();
		patchTimers.setLayout(new BoxLayout(patchTimers, BoxLayout.Y_AXIS));
		patchTimers.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fillPatchTimers(patchTimers);
		add(left(patchTimers));

	}

	/**
	 * Every usable, selected patch of each ticked type, soonest ready first: one line each (name, then its state),
	 * under a heading per type that opens and closes and stays that way (Sean: 22 two-line cards was too long).
	 */
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
			int due = 0;
			for (Patch patch : patches)
			{
				if (RunSelector.isDue(predictions.get(patch)))
				{
					due++;
				}
			}
			final String key = "Patch timers: " + type.name();
			final boolean open = settings.isSectionOpen(key);
			panel.add(timerGroupHeader(type.getDisplayName() + "s", due + " of " + patches.size() + " due", open, () ->
			{
				settings.setSectionOpen(key, !open);
				fillPatchTimers(panel);
			}));
			if (open)
			{
				final Card rows = new Card(null);
				rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
				for (Patch patch : patches)
				{
					rows.add(patchTimer(patch, predictions.get(patch), now));
				}
				final JPanel wrapper = fullWidth();
				wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
				wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
				wrapper.add(rows, BorderLayout.CENTER);
				panel.add(left(wrapper));
			}
			any = true;
		}
		if (!any)
		{
			panel.add(note("No patches selected"));
		}
		panel.revalidate();
		panel.repaint();
	}

	/** A patch type's heading in the timers: a card with a chevron, the type and how many are due. */
	private static JComponent timerGroupHeader(String title, String summary, boolean open, Runnable toggle)
	{
		return Card.groupHeader(title, summary, open, toggle);
	}

	/** One patch on one line: where on the left, its state on the right (green when due); crop and all on hover. */
	private static JComponent patchTimer(Patch patch, PatchPrediction prediction, long now)
	{
		final String described = PatchStatusText.describe(prediction, now);
		// "Maple - check health": the state on the line, the whole thing in the tooltip
		final int dash = described.indexOf(" - ");
		final String state = dash >= 0 ? described.substring(dash + 3) : described;
		final JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		final JLabel name = new JLabel(UiText.wrap(patch.getLocation().getDisplayName(), TEXT_WIDTH - 110));
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setVerticalAlignment(JLabel.TOP);
		final JLabel status = new JLabel(state);
		status.setForeground(RunSelector.isDue(prediction) ? CARRIED : Color.WHITE);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setVerticalAlignment(JLabel.TOP);
		row.add(name, BorderLayout.CENTER);
		row.add(status, BorderLayout.EAST);
		row.setToolTipText(described);
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
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
		buttons.add(Style.primary(startNow));
		buttons.add(Style.button(cancel));
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
		add(left(buttons));
		// The next step (open a bank, the Farm run tab...) is under the player only: said once (Sean)
	}

	/** Hover text for a stop: the plan for each patch there, wrapped so it stays on screen (Sean: it ran off). */
	private static String objectivesTooltip(List<String> objectives)
	{
		return objectivesTooltip(null, objectives);
	}

	/** The same, after a first line such as "About 25 s". */
	private static String objectivesTooltip(String first, List<String> objectives)
	{
		final List<String> lines = new ArrayList<>();
		if (first != null)
		{
			lines.add(first);
		}
		if (objectives != null)
		{
			lines.addAll(objectives);
		}
		return lines.isEmpty() ? null : "<html><body style='width:" + TOOLTIP_WIDTH + "px'>"
			+ String.join("<br>", escapeAll(lines)) + "</body></html>";
	}

	private void addRunning(RunView view)
	{
		if (view.getState() == RunView.State.ARMED)
		{
			// The clock on its own line, then the buttons across the full width, so no label is cut off
			final JPanel bar = new JPanel(new BorderLayout(0, 4));
			bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
			clockLabel = new JLabel();
			clockLabel.setForeground(ColorScheme.BRAND_ORANGE);
			updateClock();
			final JPanel buttons = new JPanel(new GridLayout(0, 2, 4, 4));
			buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
			buttons.add(smallButton("Start now", controls::startNow));
			buttons.add(smallButton("Cancel", controls::cancel));
			bar.add(clockLabel, BorderLayout.NORTH);
			bar.add(buttons, BorderLayout.CENTER);
			bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
			add(left(bar));
		}
		else
		{
			// The run finishes by itself after the last stop, so Stop is only for ending it early. The clock, big,
			// and Stop share one centred line (Sean)
			final JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
			bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
			clockLabel = new JLabel();
			clockLabel.setForeground(ColorScheme.BRAND_ORANGE);
			clockLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(CLOCK_FONT_SIZE));
			updateClock();
			final JButton stop = new JButton("Stop");
			stop.setFocusPainted(false);
			stop.setToolTipText("End the run now. It ends by itself after the last stop");
			stop.addActionListener(e -> controls.stop());
			bar.add(clockLabel);
			bar.add(Style.danger(stop));
			bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bar.getPreferredSize().height));
			add(left(bar));
		}

		add(tipped(heading("Complete route"), "Ticks itself as you go. If it misses something, tick it yourself; "
			+ "a box you ticked can be unticked to go back to it"));
		int number = 1;
		final List<RunView.Stop> stops = view.getStops();
		for (int row = 0; row < stops.size(); row++)
		{
			final RunView.Stop stop = stops.get(row);
			add(runningStop(stop.isBank() ? 0 : number++, row, stop));
		}
	}

	private static JButton smallButton(String text, Runnable onClick)
	{
		final JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.addActionListener(e -> onClick.run());
		return Style.button(button);
	}

	/**
	 * A tick box as in Quest Helper (Sean): ticked when done; the plugin's own ticks can't be undone, the player's
	 * can. Disabled when there's nothing the player can do with it.
	 */
	private static JCheckBox tickBox(boolean done, boolean enabled, java.util.function.Consumer<Boolean> onChange)
	{
		final JCheckBox box = new JCheckBox();
		// Green when the plugin saw it done (locked), orange when ticked by hand (can be unticked), an outline when it
		// can be ticked, faint when it can't yet (Sean: the old ones were hard to see)
		Style.checkBox(box);
		box.setSelected(done);
		box.setEnabled(enabled);
		box.setOpaque(false);
		box.setFocusPainted(false);
		box.setBorder(new EmptyBorder(2, 0, 0, 6));
		// Level with the first line of text when it wraps
		box.setVerticalAlignment(JCheckBox.TOP);
		box.addActionListener(e -> onChange.accept(box.isSelected()));
		box.setAlignmentY(Component.TOP_ALIGNMENT);
		return box;
	}

	/** One step of the current stop: its tick box, struck through when done, orange when in hand. */
	private JComponent stepRow(int index, RunView.Step step, boolean bank)
	{
		final String text = UiText.escape(step.getText());
		final String open = "<html><body style='width:" + ((TEXT_WIDTH - 60) * 10 / 13) + "px'>";
		final String close = "</body></html>";
		final boolean done = step.getStatus() == RunView.StopStatus.DONE;
		final JLabel label = new JLabel(open + (done ? "<s>" + text + "</s>" : text) + close);
		label.setForeground(done ? ColorScheme.MEDIUM_GRAY_COLOR
			: step.getStatus() == RunView.StopStatus.CURRENT ? ColorScheme.BRAND_ORANGE : Color.WHITE);
		label.setFont(FontManager.getRunescapeSmallFont());

		// A bank visit's steps are only ticked by the plugin; the bank row itself can be ticked
		final JCheckBox box = tickBox(done, !bank && (!done || step.isManual()),
			ticked -> controls.setStepTicked(index, ticked));
		final JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		// Journey steps sit further in, under the teleport they follow
		row.setBorder(new EmptyBorder(1, step.isSub() ? 16 : 0, 1, 0));
		row.add(box, BorderLayout.WEST);
		row.add(label, BorderLayout.CENTER);
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

	/**
	 * A stop or bank visit while running: its tick box, struck through when done; the current one boxed in orange
	 * with every step under it.
	 *
	 * @param number the stop's number, 0 for a bank visit
	 */
	private JComponent runningStop(int number, int row, RunView.Stop stop)
	{
		final boolean current = stop.getStatus() == RunView.StopStatus.CURRENT;
		final boolean done = stop.getStatus() == RunView.StopStatus.DONE;
		// The current one stands out: a lighter card with an orange bar down its edge (Sean: boxed; facelift: a bar)
		final Card cell = new Card(new BorderLayout())
			.fill(current ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR)
			.accent(current || (stop.isBank() && !done) ? ColorScheme.BRAND_ORANGE : null);

		final String name = UiText.escape(number > 0 ? number + ". " + stop.getLocation() : stop.getLocation());
		final JLabel title = new JLabel(done ? "<html><s>" + name + "</s></html>" : "<html>" + name + "</html>");
		// Titles in white (Sean); the current one is marked by its orange bar, a bank visit by an orange bar too
		title.setForeground(done ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
		title.setFont(current ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		// Only the current row can be ticked, and a row the player ticked can be unticked while it can be gone back to
		final JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.add(tickBox(done, current || (done && stop.isManual()), ticked -> controls.setRowTicked(row, ticked)),
			BorderLayout.WEST);
		head.add(title, BorderLayout.CENTER);
		cell.add(head, BorderLayout.NORTH);

		if (current && !stop.getSteps().isEmpty())
		{
			// Every step of this row, only while it's current. What to do now is under the player, not repeated
			// here (Sean: say each thing in one place)
			final JPanel steps = new JPanel();
			steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
			steps.setOpaque(false);
			steps.setBorder(new EmptyBorder(2, 12, 0, 0));
			final List<RunView.Step> list = stop.getSteps();
			for (int i = 0; i < list.size(); i++)
			{
				steps.add(stepRow(i, list.get(i), stop.isBank()));
			}
			cell.add(steps, BorderLayout.CENTER);
		}
		else if (!current && !done)
		{
			final JLabel detail = new JLabel(UiText.wrap(stop.getTravel(), TEXT_WIDTH - 40));
			detail.setForeground(Color.WHITE);
			detail.setFont(FontManager.getRunescapeSmallFont());
			detail.setBorder(new EmptyBorder(0, 24, 0, 0));
			cell.add(detail, BorderLayout.CENTER);
		}

		final JPanel wrapper = fullWidth();
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
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
		Style.combo(combo);
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setToolTipText("Switch to a saved preset");
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
		row.setBorder(new EmptyBorder(0, 0, 3, 0));
		final JLabel label = new JLabel("Preset");
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.WEST);
		row.add(combo, BorderLayout.CENTER);
		// Saving, renaming and deleting are in Settings > Presets (Sean): a small link there
		final JLabel manage = new JLabel("<html><u>Manage</u></html>");
		manage.setForeground(Color.WHITE);
		manage.setFont(FontManager.getRunescapeSmallFont());
		manage.setToolTipText("Save, rename and delete presets in Settings");
		manage.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		manage.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				controls.openPresets();
			}
		});
		row.add(manage, BorderLayout.EAST);
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
			Style.checkBox(box);
			// Centred in its third of the row (Sean)
			box.setHorizontalAlignment(JCheckBox.CENTER);
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
		// A little room before the button under it (Sean)
		types.setBorder(new EmptyBorder(0, 0, 5, 0));
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
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeSmallFont());
		final JButton button = new JButton(buttonText);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.addActionListener(e -> onClick.run());
		row.add(label, BorderLayout.CENTER);
		row.add(Style.button(button), BorderLayout.EAST);
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

		// Stops, each followed by its bank visit if it has one: a row of its own (Sean)
		final DefaultListModel<Object> model = new DefaultListModel<>();
		for (int i = 0; i < route.getStops().size(); i++)
		{
			model.addElement(route.getStops().get(i));
			final BankStop bank = plan.bankAfter(i);
			if (bank != null)
			{
				model.addElement(bank);
			}
		}
		final JList<Object> list = new JList<>(model);
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new StopRenderer());
		list.setDragEnabled(true);
		list.setDropMode(DropMode.INSERT);
		list.setTransferHandler(new ReorderHandler(list, model));
		// A JList is only as wide as its longest row unless told otherwise: stretch it to the panel (Sean: width
		// wacky). Its height is asked for each layout, as rows wrap differently once it has its real width (Sean: the
		// line under it overlapped the last stop)
		final JPanel holder = fullWidth();
		holder.setOpaque(false);
		holder.add(list, BorderLayout.CENTER);
		add(left(holder));

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
	private void saveOrder(DefaultListModel<Object> model)
	{
		final List<Location> order = new ArrayList<>();
		for (int i = 0; i < model.size(); i++)
		{
			// Bank visits are placed again by the plan
			if (model.get(i) instanceof RouteStop)
			{
				order.add(((RouteStop) model.get(i)).getLocation());
			}
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

	private final class StopRenderer implements ListCellRenderer<Object>
	{
		@Override
		public Component getListCellRendererComponent(JList<? extends Object> list, Object row, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			final Card cell = new Card(new BorderLayout())
				.fill(isSelected ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			final JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
			wrapper.add(cell);

			if (row instanceof BankStop)
			{
				// A bank visit between two stops: where, and how when it's by teleport
				final BankStop bank = (BankStop) row;
				final JLabel title = new JLabel(UiText.wrap(bank.title(), TEXT_WIDTH - 20));
				title.setForeground(Color.WHITE);
				title.setFont(FontManager.getRunescapeSmallFont());
				cell.accent(ColorScheme.BRAND_ORANGE);
				cell.add(title, BorderLayout.NORTH);
				if (bank.getTeleport() != null)
				{
					final JLabel how = new JLabel(UiText.wrap(bank.getTeleport().getItemName(), TEXT_WIDTH - 20));
					how.setForeground(Color.WHITE);
					how.setFont(FontManager.getRunescapeSmallFont());
					cell.add(how, BorderLayout.CENTER);
				}
				return wrapper;
			}

			final RouteStop stop = (RouteStop) row;
			// Numbered by stop, bank visits not counted
			int number = 0;
			for (int i = 0; i <= index; i++)
			{
				if (list.getModel().getElementAt(i) instanceof RouteStop)
				{
					number++;
				}
			}
			final JLabel name = new JLabel(number + ". " + stop.getLocation().getDisplayName());
			name.setForeground(Color.WHITE);
			name.setFont(FontManager.getRunescapeSmallFont());
			final boolean here = index == 0 && controls.view().isAtFirstStop();
			// The leg time is in the tooltip, above the plan (Sean: fewer three-line rows)
			final JLabel travel = new JLabel(UiText.wrap(here ? "You're here" : stop.describeTravel(), TEXT_WIDTH - 20));
			travel.setForeground(stop.isNeedsSupplies() ? IN_STORAGE : Color.WHITE);
			travel.setFont(FontManager.getRunescapeSmallFont());

			cell.add(name, BorderLayout.NORTH);
			cell.add(travel, BorderLayout.CENTER);
			// JList shows the renderer's tooltip for the hovered row
			wrapper.setToolTipText(objectivesTooltip(here ? null : "About " + legTime(stop.getLegSeconds()),
				plan.getObjectives().get(stop.getLocation())));
			return wrapper;
		}
	}

	/** Drag-and-drop reordering within the route list. */
	private final class ReorderHandler extends TransferHandler
	{
		private final JList<Object> list;
		private final DefaultListModel<Object> model;
		private int from = -1;

		ReorderHandler(JList<Object> list, DefaultListModel<Object> model)
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
			// A bank visit goes where the plan puts it, so it can't be dragged
			if (from < 0 || model.get(from) instanceof BankStop)
			{
				from = -1;
				return null;
			}
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
			final Object moved = model.remove(from);
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
		Style.title(label);
		label.setBorder(new EmptyBorder(8, 0, 3, 0));
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
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 0, 3, 0));
		return left(label);
	}

	/**
	 * A panel that fills the width and is as tall as its content, asked at each layout: a height fixed when it's
	 * built is too small once its text wraps (Sean: the line under the route overlapped the last stop).
	 */
	private static JPanel fullWidth()
	{
		return new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
	}

	private static <T extends JComponent> T left(T component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		return component;
	}
}
