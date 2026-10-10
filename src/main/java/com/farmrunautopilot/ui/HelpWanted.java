package com.farmrunautopilot.ui;

import com.farmrunautopilot.settings.SettingsStore;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * "I NEED YOUR HELP!" at the bottom of each tab: features that couldn't be tried in development (no account
 * with the item or unlock), and a link to report how they went. Remove lines here once they're confirmed.
 */
final class HelpWanted
{
	static final String ISSUES_URL = "https://github.com/elzerma/farm-run-autopilot/issues";
	private static final String TITLE = "I NEED YOUR HELP!";
	private static final int TEXT_WIDTH = 160;

	enum Tab
	{
		RUN(Arrays.asList(
			"After the Civitas illa Fortis Teleport, the step says to take the quetzal and outlines it",
			"The Spellbook Swap warning and highlight when a stop needs a Lunar-only teleport")),
		FARM(Arrays.asList(
			"Weiss herb patch: Fire of Nourishment ticks itself once you see the built fire",
			"Civitas herbs counted as disease-free with the Fortis Colosseum Champion rank")),
		TRAVEL(Arrays.asList(
			"Pendant of Ates charges",
			"Xeric's talisman and quetzal whistle charges from their Check messages",
			"Ardougne cloak farm teleports and Explorer's ring cabbage teleports left today",
			"Kharyrll Teleport highlighted in the Ancient spellbook")),
		ACCOUNT(Arrays.asList(
			"Kastori quetzal landing site and statues of Ates detected",
			"Planted spirit trees (Port Sarim, Brimhaven, Farming Guild) ticked when you pass them",
			"House scanning (beta), and not scanning a friend's house"));

		private final List<String> items;

		Tab(List<String> items)
		{
			this.items = items;
		}
	}

	private HelpWanted()
	{
	}

	static JComponent section(Tab tab, SettingsStore settings)
	{
		final CollapsibleSection s = new CollapsibleSection(TITLE, tab.items.size() + " to try",
			settings.isSectionOpen(TITLE), open -> settings.setSectionOpen(TITLE, open));
		s.addContent(text("I can't test these myself. If you can, please tell me whether they work, "
			+ "or what went wrong:", ColorScheme.LIGHT_GRAY_COLOR));
		for (String item : tab.items)
		{
			s.addContent(text("- " + item, ColorScheme.MEDIUM_GRAY_COLOR));
		}
		final JButton report = new JButton("Open GitHub issues");
		report.setFont(FontManager.getRunescapeSmallFont());
		report.setFocusPainted(false);
		report.setToolTipText(ISSUES_URL);
		report.addActionListener(e -> LinkBrowser.browse(ISSUES_URL));
		s.addContent(report);
		return s;
	}

	private static JLabel text(String text, Color colour)
	{
		final JLabel label = new JLabel("<html><div style='width:" + TEXT_WIDTH + "px'>"
			+ text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
		label.setForeground(colour);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 0, 4, 0));
		return label;
	}
}
