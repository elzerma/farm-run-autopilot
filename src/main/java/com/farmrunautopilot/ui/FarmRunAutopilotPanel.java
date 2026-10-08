package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.route.RunOverrides;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.run.RunSession;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.run.ShortestPathBridge;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import javax.inject.Inject;
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
 * Sidebar root: a Run tab and a Setup tab (SPEC section 13).
 */
public class FarmRunAutopilotPanel extends PluginPanel
{
	/** Countdowns are shown to the minute, so a 10 second refresh is plenty. */
	private static final int REFRESH_MILLIS = 10_000;

	private final RunPanel runPanel;
	private final SetupPanel setupPanel;
	private final SetupPanel rulesPanel;
	private final Timer refreshTimer;

	@Inject
	public FarmRunAutopilotPanel(SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker,
		RunOverrides runOverrides, RunService runService, RunSession runSession, ClientThread clientThread,
		ShortestPathBridge shortestPath, FarmRunAutopilotConfig config, ConfigManager configManager,
		ColorPickerManager colorPickers)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		runPanel = new RunPanel(settings, runOverrides, runService::markDirty, this::rebuildSetup,
			new RunPanel.RunControls()
			{
				@Override
				public RunView view()
				{
					return runSession.getView();
				}

				@Override
				public void start()
				{
					clientThread.invoke(runSession::start);
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

				@Override
				public boolean shortestPathAvailable()
				{
					return shortestPath.isAvailable();
				}
			});
		final JPanel run = new JPanel(new BorderLayout());
		run.setBackground(ColorScheme.DARK_GRAY_COLOR);
		run.add(runPanel, BorderLayout.NORTH);

		setupPanel = new SetupPanel(SetupPanel.Page.SETUP, settings, accessChecker, patchTracker, null);
		rulesPanel = new SetupPanel(SetupPanel.Page.RULES, settings, accessChecker, patchTracker,
			new GuidanceSettings(config, configManager, colorPickers));

		final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(0, 0, 10, 0));

		final MaterialTab runTab = new MaterialTab("Run", tabGroup, run);
		final MaterialTab setupTab = new MaterialTab("Setup", tabGroup, top(setupPanel));
		final MaterialTab rulesTab = new MaterialTab("Rules", tabGroup, top(rulesPanel));
		tabGroup.addTab(runTab);
		tabGroup.addTab(setupTab);
		tabGroup.addTab(rulesTab);
		tabGroup.select(runTab);

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		refreshTimer = new Timer(REFRESH_MILLIS, e -> refreshPatches());
		refreshTimer.start();
	}

	/** Re-reads patch predictions. Call on the Swing thread. */
	public void refreshPatches()
	{
		rulesPanel.refreshPatches();
	}

	/** Shows a new supply plan in the Run tab. Call on the Swing thread. */
	public void updateRun(RunPlan plan, boolean loggedIn)
	{
		runPanel.update(plan, loggedIn);
	}

	/** Rebuilds the Setup tab from saved settings and account access. Call on the Swing thread. */
	public void rebuildSetup()
	{
		setupPanel.rebuild();
		rulesPanel.rebuild();
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
