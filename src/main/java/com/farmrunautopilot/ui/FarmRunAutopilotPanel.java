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
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
	private final PresetsPage presetsPanel;
	private final FarmPage farmPanel;
	private final TravelPage travelPanel;
	private final AccountPage accountPanel;
	private final SettingsPage settingsPanel;
	/** Built after the Run tab, which can switch to the Account tab. */
	private TabBar tabs;
	private final RunSession runSession;
	private final Timer refreshTimer;
	private final SettingsStore settings;
	private final TestRunner testRunner;
	/** The "I NEED YOUR HELP!" button at the bottom of each tab, whose count changes as tests are run. */
	private final List<JButton> helpButtons = new ArrayList<>();

	@Inject
	public FarmRunAutopilotPanel(SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker,
		RunOverrides runOverrides, RunService runService, RunSession runSession, ClientThread clientThread,
		FarmRunAutopilotConfig config, ConfigManager configManager,
		ColorPickerManager colorPickers, HoldingsTracker holdingsTracker, RunTimings timings,
		TestRunner testRunner)
	{
		this.runSession = runSession;
		this.settings = settings;
		this.testRunner = testRunner;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(6, 6, 6, 6));
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
				public void setStepTicked(int step, boolean ticked)
				{
					clientThread.invoke(() -> runSession.setStepTicked(step, ticked));
				}

				@Override
				public void setRowTicked(int row, boolean ticked)
				{
					clientThread.invoke(() -> runSession.setRowTicked(row, ticked));
				}

				@Override
				public void openUnlocks()
				{
					accountPanel.revealUnlocks();
					tabs.select("Account");
				}

				@Override
				public void openPresets()
				{
					settingsPanel.revealPresets();
					tabs.select("Settings");
				}

				@Override
				public boolean seedVaultSeen()
				{
					return holdingsTracker.hasSeenSeedVault();
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

				@Override
				public void openTestReport()
				{
					testRunner.openReport();
				}

				@Override
				public void closeTestReport()
				{
					testRunner.closeReport();
					runPanel.refresh();
				}
			});
		// Presets cover Farm and Travel, so they're managed under the Run tab's preset picker
		presetsPanel = new PresetsPage(settings, accessChecker, this::rebuildSetup);
		final JPanel runStack = new JPanel();
		runStack.setLayout(new BoxLayout(runStack, BoxLayout.Y_AXIS));
		runStack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		runPanel.setAlignmentX(LEFT_ALIGNMENT);
		runStack.add(runPanel);
		runStack.add(helpButton());
		final JPanel run = new JPanel(new BorderLayout());
		run.setBackground(ColorScheme.DARK_GRAY_COLOR);
		run.add(runStack, BorderLayout.NORTH);

		farmPanel = new FarmPage(settings, accessChecker, this::rebuildSetup);
		travelPanel = new TravelPage(settings, accessChecker, this::rebuildSetup);
		final GuidanceSettings guidance = new GuidanceSettings(config, configManager, colorPickers);
		accountPanel = new AccountPage(settings, accessChecker, this::rebuildSetup);
		settingsPanel = new SettingsPage(settings, accessChecker, patchTracker, this::rebuildSetup, guidance,
			presetsPanel);
		final Runnable resetRunSettings = () ->
		{
			settings.resetRunConfig();
			guidance.resetDefaults();
		};
		settingsPanel.setResetActions(resetRunSettings, () ->
		{
			resetRunSettings.run();
			settings.resetAccountAndPresets();
			timings.clear();
		});
		settingsPanel.showHelpList(testRunner, () -> tabs.select("Run"));
		settingsPanel.showRunTimes(timings::learned);
		settingsPanel.showHoldings(holdingsTracker::getHoldings);
		travelPanel.showHoldings(holdingsTracker::getHoldings);
		travelPanel.showAutoPicks(runService);

		tabs = new TabBar(display);
		tabs.setBorder(new EmptyBorder(0, 0, 6, 0));
		tabs.addTab("Run", TabIcon.RUN, run);
		tabs.addTab("Farm", TabIcon.FARM, top(farmPanel, helpButton()));
		tabs.addTab("Travel", TabIcon.TRAVEL, top(travelPanel, helpButton()));
		tabs.addTab("Account", TabIcon.ACCOUNT, top(accountPanel, helpButton()));
		// No help link here: the list itself is the last section
		tabs.addTab("Settings", TabIcon.SETTINGS, top(settingsPanel, null));

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		refreshTimer = new Timer(REFRESH_MILLIS, e -> refreshPatches());
		refreshTimer.start();
	}

	/** Re-reads patch predictions. Call on the Swing thread. */
	public void refreshPatches()
	{
		settingsPanel.refreshPatches();
		runPanel.refreshPatches();
	}

	/** What the plugin has detected changed (charges, the leprechaun, the bucket). Call on the Swing thread. */
	public void refreshDetected()
	{
		settingsPanel.refreshDetected();
	}

	/** Shows a new supply plan in the Run tab. Call on the Swing thread. */
	public void updateRun(RunPlan plan, boolean loggedIn)
	{
		runPanel.update(plan, loggedIn);
		// The supply plan is redone when holdings change, so the Detected lines may have too
		settingsPanel.refreshDetected();
		travelPanel.refreshAutoPicks();
	}

	/** Rebuilds the settings tabs from saved settings and account access. Call on the Swing thread. */
	public void rebuildSetup()
	{
		farmPanel.rebuild();
		travelPanel.rebuild();
		accountPanel.rebuild();
		settingsPanel.rebuild();
		presetsPanel.rebuild();
		// The Run tab shows the run types and preset from the same settings
		runPanel.refresh();
		for (JButton button : helpButtons)
		{
			HelpWanted.refresh(button, settings, testRunner.asDeveloper());
		}
	}

	private JButton helpButton()
	{
		final JButton button = HelpWanted.button(settings, testRunner.asDeveloper(), this::openHelp);
		helpButtons.add(button);
		return button;
	}

	/** Settings > Help improve this plugin, scrolled to the help list. */
	private void openHelp()
	{
		settingsPanel.revealHelpList();
		tabs.select("Settings");
	}

	/** Pins a page, with its help section under it, to the top of its tab instead of stretching it. */
	private static JPanel top(JPanel page, JComponent help)
	{
		final JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setBackground(ColorScheme.DARK_GRAY_COLOR);
		page.setAlignmentX(LEFT_ALIGNMENT);
		stack.add(page);
		if (help != null)
		{
			help.setAlignmentX(LEFT_ALIGNMENT);
			stack.add(help);
		}
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
