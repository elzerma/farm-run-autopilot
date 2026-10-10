package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.route.RunOverrides;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.run.RunSession;
import com.farmrunautopilot.run.RunTimings;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.testing.TestRunner;
import com.farmrunautopilot.testing.TestView;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;

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
	/** Built after the Run tab, which can switch to the Account tab. */
	private TabBar tabs;
	private final RunSession runSession;
	private final Timer refreshTimer;

	@Inject
	public FarmRunAutopilotPanel(SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker,
		RunOverrides runOverrides, RunService runService, RunSession runSession, ClientThread clientThread,
		FarmRunAutopilotConfig config, ConfigManager configManager,
		ColorPickerManager colorPickers, HoldingsTracker holdingsTracker, RunTimings timings,
		TestRunner testRunner, @Named("developerMode") boolean developerMode)
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

				@Override
				public void openUnlocks()
				{
					accountPanel.revealUnlocks();
					tabs.select("Account");
				}

				@Override
				public TestView testView()
				{
					return testRunner.getView();
				}

				@Override
				public void answerTest(boolean yes)
				{
					testRunner.answer(yes);
				}

				@Override
				public void skipTestStep()
				{
					testRunner.skip();
				}

				@Override
				public void cancelTest()
				{
					testRunner.cancel();
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
		runStack.add(HelpWanted.button(settings, developerMode, this::openHelp));
		final JPanel run = new JPanel(new BorderLayout());
		run.setBackground(ColorScheme.DARK_GRAY_COLOR);
		run.add(runStack, BorderLayout.NORTH);

		farmPanel = new SetupPanel(SetupPanel.Page.FARM, settings, accessChecker, patchTracker,
			this::rebuildSetup, null);
		travelPanel = new SetupPanel(SetupPanel.Page.TRAVEL, settings, accessChecker, patchTracker,
			this::rebuildSetup, null);
		final GuidanceSettings guidance = new GuidanceSettings(config, configManager, colorPickers);
		accountPanel = new SetupPanel(SetupPanel.Page.ACCOUNT, settings, accessChecker, patchTracker,
			this::rebuildSetup, guidance);
		final Runnable resetRunSettings = () ->
		{
			settings.resetRunConfig();
			guidance.resetDefaults();
		};
		accountPanel.setResetActions(resetRunSettings, () ->
		{
			resetRunSettings.run();
			settings.resetAccountAndPresets();
			timings.clear();
		});
		accountPanel.showHelpList(developerMode, testRunner, () -> tabs.select("Run"));
		accountPanel.showHoldings(holdingsTracker::getHoldings);
		travelPanel.showHoldings(holdingsTracker::getHoldings);
		travelPanel.showAutoPicks(runService);

		tabs = new TabBar(display);
		tabs.setBorder(new EmptyBorder(0, 0, 10, 0));
		tabs.addTab("Run", run);
		tabs.addTab("Farm", top(farmPanel, HelpWanted.button(settings, developerMode, this::openHelp)));
		tabs.addTab("Travel", top(travelPanel, HelpWanted.button(settings, developerMode, this::openHelp)));
		tabs.addTab("Account", top(accountPanel, HelpWanted.button(settings, developerMode, this::openHelp)));

		add(tabs, BorderLayout.NORTH);
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
		// The supply plan is redone when holdings change, so the Detected lines may have too
		accountPanel.refreshDetected();
		travelPanel.refreshAutoPicks();
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

	/** Account > Testing & debug, scrolled to the help list. */
	private void openHelp()
	{
		accountPanel.revealHelpList();
		tabs.select("Account");
	}

	/** Pins a page, with its help section under it, to the top of its tab instead of stretching it. */
	private static JPanel top(JPanel page, JComponent help)
	{
		final JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		page.setAlignmentX(LEFT_ALIGNMENT);
		help.setAlignmentX(LEFT_ALIGNMENT);
		stack.add(page);
		stack.add(help);
		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(stack, BorderLayout.NORTH);
		return wrapper;
	}

	public void shutDown()
	{
		refreshTimer.stop();
		runPanel.shutDown();
	}
}
