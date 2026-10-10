package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.route.RunOverrides;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.run.RunSession;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar root: Run, Farm, Travel and Account tabs (SPEC section 13, docs/plans/sidebar-ux.md).
 */
public class FarmRunAutopilotPanel extends PluginPanel
{
	/** Countdowns are shown to the minute, so a 10 second refresh is plenty. */
	private static final int REFRESH_MILLIS = 10_000;

	private final RunPanel runPanel;
	private final SetupPanel presetsPanel;
	private final SetupPanel farmPanel;
	private final SetupPanel travelPanel;
	private final SetupPanel accountPanel;
	private final RunSession runSession;
	private final Timer refreshTimer;

	@Inject
	public FarmRunAutopilotPanel(SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker,
		RunOverrides runOverrides, RunService runService, RunSession runSession, ClientThread clientThread,
		FarmRunAutopilotConfig config, ConfigManager configManager,
		ColorPickerManager colorPickers)
	{
		this.runSession = runSession;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		runPanel = new RunPanel(settings, runOverrides, runService::markDirty, this::rebuildSetup,
			patchTracker, accessChecker,
			new RunPanel.RunControls()
			{
				@Override
				public RunView view()
				{
					return runSession.getView();
				}

				@Override
				public void build()
				{
					clientThread.invoke(runSession::build);
				}

				@Override
				public void cancel()
				{
					clientThread.invoke(runSession::cancel);
				}

				@Override
				public void startNow()
				{
					clientThread.invoke(runSession::startNow);
				}

				@Override
				public void stop()
				{
					clientThread.invoke(() -> runSession.stop(false));
				}

				@Override
				public void skip()
				{
					clientThread.invoke(runSession::skip);
				}
			});
		// Presets cover Farm and Travel, so they're managed under the Run tab's preset picker
		presetsPanel = new SetupPanel(SetupPanel.Page.PRESETS, settings, accessChecker, patchTracker,
			this::rebuildSetup, null);
		final JPanel runStack = new JPanel();
		runStack.setLayout(new BoxLayout(runStack, BoxLayout.Y_AXIS));
		runStack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		runPanel.setAlignmentX(LEFT_ALIGNMENT);
		presetsPanel.setAlignmentX(LEFT_ALIGNMENT);
		runStack.add(runPanel);
		runStack.add(presetsPanel);
		final JPanel run = new JPanel(new BorderLayout());
		run.setBackground(ColorScheme.DARK_GRAY_COLOR);
		run.add(runStack, BorderLayout.NORTH);

		farmPanel = new SetupPanel(SetupPanel.Page.FARM, settings, accessChecker, patchTracker,
			this::rebuildSetup, null);
		travelPanel = new SetupPanel(SetupPanel.Page.TRAVEL, settings, accessChecker, patchTracker,
			this::rebuildSetup, null);
		accountPanel = new SetupPanel(SetupPanel.Page.ACCOUNT, settings, accessChecker, patchTracker, this::rebuildSetup,
			new GuidanceSettings(config, configManager, colorPickers));

		final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(0, 0, 10, 0));

		final MaterialTab runTab = new MaterialTab("Run", tabGroup, run);
		tabGroup.addTab(runTab);
		tabGroup.addTab(new MaterialTab("Farm", tabGroup, top(farmPanel)));
		tabGroup.addTab(new MaterialTab("Travel", tabGroup, top(travelPanel)));
		tabGroup.addTab(new MaterialTab("Account", tabGroup, top(accountPanel)));
		tabGroup.select(runTab);

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		refreshTimer = new Timer(REFRESH_MILLIS, e -> refreshPatches());
		refreshTimer.start();
	}

	/** Re-reads patch predictions. Call on the Swing thread. */
	public void refreshPatches()
	{
		accountPanel.refreshPatches();
		runPanel.refreshPatches();
	}

	/** Shows a new supply plan in the Run tab. Call on the Swing thread. */
	public void updateRun(RunPlan plan, boolean loggedIn)
	{
		runPanel.update(plan, loggedIn);
		// Managing presets is for between runs
		presetsPanel.setVisible(loggedIn && runSession.getView().getState() == RunView.State.OFF);
	}

	/** Rebuilds the settings tabs from saved settings and account access. Call on the Swing thread. */
	public void rebuildSetup()
	{
		farmPanel.rebuild();
		travelPanel.rebuild();
		accountPanel.rebuild();
		presetsPanel.rebuild();
		// The Run tab shows the run types and preset from the same settings
		runPanel.refresh();
	}

	/** Pins a page to the top of its tab instead of stretching it. */
	private static JPanel top(JPanel page)
	{
		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(page, BorderLayout.NORTH);
		return wrapper;
	}

	public void shutDown()
	{
		refreshTimer.stop();
		runPanel.shutDown();
	}
}
