package com.farmrunautopilot.ui;

import com.farmrunautopilot.settings.SettingsStore;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/**
 * "I NEED YOUR HELP!" at the bottom of each tab: features that couldn't be tried in development (no account
 * with the item or unlock, or a game value not checked yet), what to try, and a result to pick for each.
 * Results are kept for the client and logged ("Help list: ...") so they can be followed up. Remove an item
 * once it's confirmed (see CLAUDE.md).
 */
@Slf4j
final class HelpWanted
{
	static final String ISSUES_URL = "https://github.com/elzerma/farm-run-autopilot/issues";
	private static final String TITLE = "I NEED YOUR HELP!";
	private static final int TEXT_WIDTH = 160;
	private static final int CONTROL_WIDTH = 190;
	private static final String NOT_TRIED = "Not tried yet";
	private static final String GOOD = "Good to go";
	private static final String ATTENTION = "Needs attention";
	private static final String[] RESULTS = {NOT_TRIED, GOOD, ATTENTION};

	enum Tab
	{
		RUN("Run"), FARM("Farm"), TRAVEL("Travel"), ACCOUNT("Account");

		private final String label;

		Tab(String label)
		{
			this.label = label;
		}
	}

	/** One thing to try. The key names its saved result, so don't change it once released. */
	enum Item
	{
		CIVITAS_QUETZAL(Tab.RUN, "Quetzal after the Civitas teleport",
			"Set Travel > Civitas illa Fortis (Ortus Farm) to Civitas illa Fortis Teleport and start a run with the "
				+ "Civitas herb patch. Once you land in the city, the step should say \"Take the quetzal to the "
				+ "Hunter Guild\" and the quetzal nearby should be outlined. After the ride, the step should switch "
				+ "to walking to the patch."),
		SPELLBOOK_SWAP(Tab.RUN, "Spellbook Swap",
			"On the Lunar spellbook (96 Magic, Dream Mentor done) with no house altar for the other spellbook, "
				+ "plan a stop whose teleport is from another spellbook and that you have no tablet for. The Run "
				+ "tab should warn that you need Spellbook Swap, its runes should be on the supply list, and the "
				+ "spell should be outlined in your spellbook on that step."),
		WEISS_FIRE(Tab.FARM, "Fire of Nourishment at Weiss",
			"If you've built the Fire of Nourishment, untick it in Account > Unlocks, then go to Weiss. It should "
				+ "tick itself once the fire is in view, and the Weiss herb patch should join your herb runs."),
		FORTIS_CHAMPION(Tab.FARM, "Civitas herbs and the Colosseum",
			"With \"Champion rank at the Fortis Colosseum\" ticked in Account > Unlocks, the Civitas herb patch "
				+ "should count as disease-free: no plant cure or protection asked for it."),
		ATES_CHARGES(Tab.TRAVEL, "Pendant of Ates charges",
			"With the pendant on you, Account > Detected should show the same charges as its Check. Teleport once "
				+ "and it should drop by one. Start a teleport and cancel it: it shouldn't change."),
		CHAT_CHARGES(Tab.TRAVEL, "Xeric's talisman and quetzal whistle charges",
			"Check the talisman or whistle. Account > Detected should show the same number. Teleport once and it "
				+ "should drop by one. At 0 charges, Auto (best) should stop picking it."),
		DAILY_TELEPORTS(Tab.TRAVEL, "Teleports left today",
			"With an Ardougne cloak 2 or 3, or Explorer's ring 2 or 3: Account > Detected has a \"Daily teleports "
				+ "used (checking)\" line. Use a farm or cabbage teleport and note how its numbers change. Once "
				+ "the day's uses are gone, Auto (best) should stop picking it."),
		KHARYRLL(Tab.TRAVEL, "Kharyrll Teleport highlight",
			"On the Ancient spellbook with Kharyrll Teleport planned for Port Phasmatys (and no tablet), the "
				+ "spell should be outlined in your spellbook on that step."),
		VARBIT_UNLOCKS(Tab.ACCOUNT, "Kastori quetzal and statues of Ates",
			"If you've built the Kastori quetzal landing site or activated a statue of Ates (Nemus Retreat, north "
				+ "of Kastori), Account > Unlocks should show it ticked with \"(detected)\". An unticked box means "
				+ "it wasn't detected."),
		SPIRIT_TREES(Tab.ACCOUNT, "Planted spirit trees",
			"Untick your planted spirit tree in Account > Unlocks, then go to it (Port Sarim, Brimhaven or the "
				+ "Farming Guild). It should tick itself once the grown tree is in view."),
		HOUSE_SCAN(Tab.ACCOUNT, "House scanning (beta)",
			"Tick \"Detect furniture when I enter my house\" in Account > My house, then enter your house: your "
				+ "portal nexus, jewellery box, pool and altar should fill in. Then visit someone else's house: "
				+ "nothing of yours should change.");

		private final Tab tab;
		private final String title;
		private final String howToTry;

		Item(Tab tab, String title, String howToTry)
		{
			this.tab = tab;
			this.title = title;
			this.howToTry = howToTry;
		}

		String key()
		{
			return "help." + name();
		}
	}

	private HelpWanted()
	{
	}

	/** How many items are still to try: all of them for players, those not yet Good to go for the developer. */
	static int toTry(SettingsStore settings, boolean developer)
	{
		int count = 0;
		for (Item item : Item.values())
		{
			count += developer && GOOD.equals(settings.getClientValue(item.key())) ? 0 : 1;
		}
		return count;
	}

	/** The button at the bottom of every tab, which opens the list in Account > Testing & debug. */
	static JComponent button(SettingsStore settings, boolean developer, Runnable open)
	{
		final int count = toTry(settings, developer);
		final JButton button = new JButton(count == 0 ? TITLE + " (all good)" : TITLE + " (" + count + " to try)");
		button.setFont(FontManager.getRunescapeBoldFont());
		button.setForeground(ColorScheme.BRAND_ORANGE);
		button.setFocusPainted(false);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setToolTipText("Features I couldn't test, with steps to try them");
		button.addActionListener(e -> open.run());
		return button;
	}

	/**
	 * The list for Account > Testing & debug: every item with steps to try it, grouped by tab. The developer
	 * (running the dev client) gets a result to pick for each; everyone else gets the issues button.
	 */
	static JPanel list(SettingsStore settings, boolean developer)
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JLabel heading = text(TITLE, ColorScheme.BRAND_ORANGE, 4);
		heading.setFont(FontManager.getRunescapeBoldFont());
		add(panel, heading);
		add(panel, text(developer
			? "Try each one and pick how it went. For anything that needs attention, add a note."
			: "I can't test these myself. If you can, please try them and tell me how it went on GitHub, "
				+ "especially if something's wrong.", Color.WHITE, 6));
		for (Tab tab : Tab.values())
		{
			boolean first = true;
			for (Item item : Item.values())
			{
				if (item.tab != tab)
				{
					continue;
				}
				if (first)
				{
					add(panel, text(tab.label + " tab", Color.WHITE, 4));
					first = false;
				}
				addItem(panel, item, settings, developer);
			}
		}
		final JButton report = new JButton("Open GitHub issues");
		report.setFont(FontManager.getRunescapeSmallFont());
		report.setFocusPainted(false);
		report.setToolTipText(ISSUES_URL);
		report.addActionListener(e -> LinkBrowser.browse(ISSUES_URL));
		add(panel, report);
		return panel;
	}

	private static void add(JPanel panel, JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(component);
	}

	private static void addItem(JPanel panel, Item item, SettingsStore settings, boolean developer)
	{
		final JLabel title = text(item.title, ColorScheme.BRAND_ORANGE, 2);
		title.setFont(FontManager.getRunescapeBoldFont());
		add(panel, title);
		add(panel, text(item.howToTry, ColorScheme.LIGHT_GRAY_COLOR, developer ? 4 : 8));
		if (!developer)
		{
			return;
		}

		final String saved = settings.getClientValue(item.key());
		final JComboBox<String> result = new JComboBox<>(RESULTS);
		result.setSelectedItem(saved != null ? saved : NOT_TRIED);
		result.setFont(FontManager.getRunescapeSmallFont());
		result.setMaximumSize(new Dimension(CONTROL_WIDTH, 24));
		result.setPreferredSize(new Dimension(CONTROL_WIDTH, 24));
		add(panel, result);

		final JTextField note = new JTextField(noteOf(item, settings));
		note.setToolTipText("What went wrong (saved when you press Enter or click away)");
		note.setMaximumSize(new Dimension(CONTROL_WIDTH, 24));
		note.setPreferredSize(new Dimension(CONTROL_WIDTH, 24));
		note.setVisible(ATTENTION.equals(result.getSelectedItem()));
		add(panel, note);
		add(panel, text("", ColorScheme.MEDIUM_GRAY_COLOR, 6));

		result.addActionListener(e ->
		{
			final String picked = (String) result.getSelectedItem();
			if (picked == null || picked.equals(valueOr(settings.getClientValue(item.key()), NOT_TRIED)))
			{
				return;
			}
			settings.setClientValue(item.key(), NOT_TRIED.equals(picked) ? null : picked);
			// Infrequent and user-made, so INFO; read back from the client log to follow up
			log.info("Help list: {} = {}{}", item.name(), picked, noteSuffix(item, settings));
			note.setVisible(ATTENTION.equals(picked));
			panel.revalidate();
		});
		final Runnable saveNote = () ->
		{
			final String text = note.getText().trim();
			if (!text.equals(noteOf(item, settings)))
			{
				settings.setClientValue(item.key() + ".note", text);
				log.info("Help list: {} = {}{}", item.name(), result.getSelectedItem(), noteSuffix(item, settings));
			}
		};
		note.addActionListener(e -> saveNote.run());
		note.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				saveNote.run();
			}
		});
	}

	private static String noteOf(Item item, SettingsStore settings)
	{
		return valueOr(settings.getClientValue(item.key() + ".note"), "");
	}

	private static String noteSuffix(Item item, SettingsStore settings)
	{
		final String note = noteOf(item, settings);
		return note.isEmpty() ? "" : " (note: " + note + ")";
	}

	private static String valueOr(String value, String fallback)
	{
		return value != null ? value : fallback;
	}

	private static JLabel text(String text, Color colour, int gapBelow)
	{
		final JLabel label = new JLabel(text.isEmpty() ? " " : "<html><div style='width:" + TEXT_WIDTH + "px'>"
			+ text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
		label.setForeground(colour);
		label.setFont(FontManager.getRunescapeFont());
		label.setBorder(new EmptyBorder(0, 0, gapBelow, 0));
		return label;
	}
}
