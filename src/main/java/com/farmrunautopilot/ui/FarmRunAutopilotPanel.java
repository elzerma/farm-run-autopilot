package com.farmrunautopilot.ui;

import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import javax.inject.Inject;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar root: a Run tab and a Setup tab (SPEC section 13).
 */
public class FarmRunAutopilotPanel extends PluginPanel
{
	/** Countdowns are shown to the minute, so a 10 second refresh is plenty. */
	private static final int REFRESH_MILLIS = 10_000;

	private final PatchDebugPanel patchDebugPanel;
	private final Timer refreshTimer;

	@Inject
	public FarmRunAutopilotPanel(PatchTracker patchTracker)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		patchDebugPanel = new PatchDebugPanel(patchTracker);
		final JPanel setup = new JPanel(new BorderLayout());
		setup.setBackground(ColorScheme.DARK_GRAY_COLOR);
		setup.add(patchDebugPanel, BorderLayout.NORTH);

		final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(0, 0, 10, 0));

		final MaterialTab runTab = new MaterialTab("Run", tabGroup, placeholder("Your farm run will appear here."));
		final MaterialTab setupTab = new MaterialTab("Setup", tabGroup, setup);
		tabGroup.addTab(runTab);
		tabGroup.addTab(setupTab);
		tabGroup.select(runTab);

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		refreshTimer = new Timer(REFRESH_MILLIS, e -> refreshPatches());
		refreshTimer.start();
	}

	/** Re-reads patch predictions. Call on the Swing thread. */
	public void refreshPatches()
	{
		patchDebugPanel.refresh();
	}

	public void shutDown()
	{
		refreshTimer.stop();
	}

	private static JPanel placeholder(String text)
	{
		final JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(label, BorderLayout.NORTH);
		return panel;
	}
}
