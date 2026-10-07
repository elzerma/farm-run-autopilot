package com.farmrunautopilot.ui;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar root: a Run tab and a Setup tab (SPEC section 13). Both are placeholders until later milestones.
 */
public class FarmRunAutopilotPanel extends PluginPanel
{
	public FarmRunAutopilotPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(0, 0, 10, 0));

		final MaterialTab runTab = new MaterialTab("Run", tabGroup, placeholder("Your farm run will appear here."));
		final MaterialTab setupTab = new MaterialTab("Setup", tabGroup, placeholder("Run settings will appear here."));
		tabGroup.addTab(runTab);
		tabGroup.addTab(setupTab);
		tabGroup.select(runTab);

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
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
