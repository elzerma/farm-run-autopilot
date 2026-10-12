package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.DailyLimits;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.ChargeBudget;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.LeprechaunItem;
import com.farmrunautopilot.testing.TestRunner;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Settings tab (Sean: Account is what you have; this is how the plugin behaves): presets are managed here,
 * then how the run is shown in game, debug information (what was detected, patch states), resetting, and the
 * testing list last.
 */
class SettingsPage extends SetupPanel
{
	/** Section titles, also how other tabs open them. */
	static final String DEBUG = "Debug";
	static final String TESTING = "Help improve this plugin";

	private final PatchDebugPanel patchDebugPanel;
	/** The Detected lines, refilled without rebuilding the page. */
	private final JPanel detected = new JPanel();
	/** What the Reset button does; null until set. */
	private Runnable resetRunSettings;
	private Runnable resetEverything;
	/** Runs guided tests for the help list in Testing; null until shown. */
	private TestRunner testRunner;
	private Runnable showRunTab;
	/** The help list as last drawn, to scroll to it. */
	private JComponent helpListShown;
	/** Preset management, its own page shown as this tab's first section. */
	private final PresetsPage presets;
	/** Run guidance settings (highlights, colours, walking lines). */
	private final GuidanceSettings guidance;

	SettingsPage(SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker, Runnable changed,
		GuidanceSettings guidance, PresetsPage presets)
	{
		super(settings, accessChecker, changed);
		this.guidance = guidance;
		this.presets = presets;
		this.patchDebugPanel = new PatchDebugPanel(patchTracker);
		detected.setLayout(new BoxLayout(detected, BoxLayout.Y_AXIS));
		detected.setBackground(ColorScheme.DARK_GRAY_COLOR);
		rebuild();
	}

	@Override
	void showHoldings(Supplier<Holdings> holdings)
	{
		super.showHoldings(holdings);
		refreshDetected();
	}

	@Override
	void addSections(RunConfig config, AccountSettings account, AccessSnapshot access)
	{
		if (presets != null)
		{
			presets.setAlignmentX(LEFT_ALIGNMENT);
			add(presets);
		}
		add(displaySection(config));
		add(debugSection(config));
		add(resetSection());
		add(testingSection());
	}

	/** Open the Presets section and scroll to it. Swing thread. */
	void revealPresets()
	{
		settings.setSectionOpen(PresetsPage.TITLE, true);
		presets.rebuild();
		SwingUtilities.invokeLater(() -> presets.scrollRectToVisible(new Rectangle(0, 0, presets.getWidth(),
			presets.getHeight())));
	}

	void refreshPatches()
	{
		patchDebugPanel.refresh();
	}

	/** Show the help list in Testing. */
	void showHelpList(TestRunner testRunner, Runnable showRunTab)
	{
		this.testRunner = testRunner;
		this.showRunTab = showRunTab;
		rebuild();
	}

	/** Open Testing and scroll to the help list. Swing thread. */
	void revealHelpList()
	{
		settings.setSectionOpen(TESTING, true);
		rebuild();
		SwingUtilities.invokeLater(() ->
		{
			final JComponent list = helpListShown;
			if (list != null)
			{
				list.scrollRectToVisible(new Rectangle(0, 0, list.getWidth(), list.getHeight()));
			}
		});
	}

	/** Re-read what the plugin has detected, without rebuilding the page. Call on the Swing thread. */
	void refreshDetected()
	{
		detected.removeAll();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final Holdings held = holdings.get();
		if (!access.isKnown())
		{
			detected.add(note("Log in to see what's been detected."));
		}
		else
		{
			addDetected("Spellbook", access.getSpellbook() != null ? title(access.getSpellbook().name()) : "Unknown",
				"Read from the game; teleports on another spellbook are used as tablets");

			final boolean rings = access.getUnlocks().contains(Unlock.FAIRY_RINGS);
			final boolean elite = access.isMet(Requirement.diary(AchievementDiary.LUMBRIDGE_DRAYNOR,
				AchievementDiary.Tier.ELITE));
			addDetected("Fairy rings", !rings ? "Not unlocked" : elite ? "Unlocked, no staff needed"
					: held.countAny(SupplyItems.FAIRY_RING_STAFFS) > 0 ? "Unlocked, staff owned" : "Unlocked, no staff found",
				"From Fairytale II; a dramen or lunar staff is needed until the Elite Lumbridge & Draynor diary");
			addDetected("Spirit trees", access.getUnlocks().contains(Unlock.SPIRIT_TREES) ? "Unlocked" : "Not unlocked",
				"From Tree Gnome Village");
			for (Unlock unlock : Unlock.values())
			{
				// Confirmed game values, so these aren't in Unlocks to tick
				if (unlock.isReadFromGame())
				{
					addDetected(unlock.getDescription(), access.getUnlocks().contains(unlock) ? "Yes" : "No",
						"Read from the game");
				}
			}
			addDetected("Quest points", access.getQuestPoints() + (access.getMaxQuestPoints() > 0
					? " of " + access.getMaxQuestPoints() : ""),
				"The quest point cape only works with every quest done");

			final boolean filled = held.count(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED) > 0;
			final boolean empty = held.count(ItemID.BOTTOMLESS_COMPOST_BUCKET) > 0;
			if (filled || empty)
			{
				final int uses = held.getBucketUses();
				final String holds = held.getBucketCompost() != null
					? " of " + held.getBucketCompost().getDisplayName().toLowerCase() : "";
				addDetected("Bottomless bucket", !filled ? "Empty" : uses >= 0 ? uses + " uses" + holds
						: "Uses not known yet",
					"Kept from the game's messages. Right-click the bucket and choose Check to update it");
			}

			final List<String> stored = new ArrayList<>();
			final Map<Integer, Integer> leprechaun = held.in(Holdings.Source.LEPRECHAUN);
			for (LeprechaunItem item : LeprechaunItem.values())
			{
				final int n = leprechaun.getOrDefault(item.getItemId(), 0);
				if (n > 0)
				{
					final String name = item.name().replace('_', ' ').toLowerCase();
					stored.add(n > 1 ? n + " " + name : name);
				}
			}
			for (TravelItem item : TravelItem.values())
			{
				final Integer charges = ChargeBudget.held(item, held);
				if (charges != null && charges > 0)
				{
					addDetected(item.getDisplayName(), charges + (charges == 1 ? " charge" : " charges"),
						"Every piece you have, in your bank, inventory and worn, added together");
				}
				else if (ChargeBudget.chargesUnknown(item, held))
				{
					// Held, but the count isn't known (never checked, or just recharged): say so rather than hide it
					addDetected(item.getDisplayName(), "Check it",
						"Right-click it and choose Check once, so the plugin can read how many charges it has");
				}
			}
			for (TravelMethod method : DailyLimits.usedTodayVarbits().keySet())
			{
				final Integer left = ChargeBudget.leftToday(method, held);
				if (left != null)
				{
					addDetected(method.getDisplayName(), left + " left today",
						"This teleport can only be used a few times a day with the item you have");
				}
			}
			addDetected("Tool leprechaun", stored.isEmpty() ? "Nothing stored" : String.join(", ", stored),
				"What's stored with the tool leprechaun counts as yours for the supply list");
			if (held.isAutoweedOn())
			{
				addDetected("Auto-weed", "On", "Tithe Farm Auto-weed: no rake needed for weeds");
			}
		}
		detected.revalidate();
		detected.repaint();
	}

	/** One read-only line: what was detected and its value. */
	private void addDetected(String what, String value, String tooltip)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		// Both columns wrap: the value column is CONTROL_WIDTH - 90 wide, so the name gets the rest
		final JLabel name = new JLabel(wrap(what, 80));
		name.setVerticalAlignment(JLabel.TOP);
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		final JLabel shown = new JLabel(wrap(value, CONTROL_WIDTH - 90));
		shown.setVerticalAlignment(JLabel.TOP);
		shown.setForeground(ColorScheme.TEXT_COLOR);
		shown.setFont(FontManager.getRunescapeSmallFont());
		row.add(name, BorderLayout.WEST);
		row.add(shown, BorderLayout.EAST);
		row.setToolTipText(tooltip);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(CONTROL_WIDTH, row.getPreferredSize().height));
		detected.add(row);
	}

	/** How the run is shown in game: highlights, colours and reminders. */
	private JComponent displaySection(RunConfig config)
	{
		final CollapsibleSection s = section("Display");
		if (guidance != null)
		{
			addGuidance(s);
		}
		s.addContent(checkBox("Remind me to drop weeds and pots", config.isRemindToDrop(), true,
			"During a run, a reminder under your character while you carry weeds or empty plant pots",
			on -> saveRun(() -> config.setRemindToDrop(on))));
		return s;
	}

	/**
	 * Debug information (Sean: Detected is purely for debug): what the plugin worked out by itself, a full-run count
	 * for checking numbers, and every patch's state.
	 */
	private JComponent debugSection(RunConfig config)
	{
		final CollapsibleSection s = section(DEBUG);
		s.help("What the plugin worked out by itself, and tools for checking it. Hover a line to see how it was "
			+ "worked out.");
		s.addContent(subheader("Detected"));
		s.addContent(detected);
		refreshDetected();
		s.addContent(subheader("Tools"));
		s.addContent(checkBox("Count every patch (full run)", config.isSupplyFullRun(), true,
			"Supplies for every selected patch, not just the ones that are due. Handy for checking numbers.",
			on -> saveRun(() -> config.setSupplyFullRun(on))));
		s.addContent(subheader("Patch details"));
		s.addContent(patchDebugPanel);
		return s;
	}

	private JComponent resetSection()
	{
		final CollapsibleSection s = section("Reset");
		if (resetRunSettings != null)
		{
			final JButton reset = smallButton("Reset to defaults...");
			reset.setToolTipText("Put this plugin's settings back to how they were when it was installed");
			reset.addActionListener(e -> askReset());
			s.addContent(reset);
		}
		return s;
	}

	/** The help list and guided tests, last on the tab (Sean). */
	private JComponent testingSection()
	{
		final CollapsibleSection s = section(TESTING);
		s.help("Features I couldn't test myself, each with a guided test. Running one helps get it right.");
		if (testRunner != null)
		{
			if (testRunner.isDeveloperMode())
			{
				// Dev client only: see the help list and the end of a test the way players do
				s.addContent(checkBox("Preview as a player", !testRunner.asDeveloper(), true,
					"Show the help list and test reports the way players see them (dev client only)", on ->
					{
						testRunner.setPreviewAsPlayer(on);
						SwingUtilities.invokeLater(changed);
					}));
			}
			helpListShown = HelpWanted.list(settings, testRunner.asDeveloper(), testRunner, showRunTab);
			s.addContent(helpListShown);
		}
		return s;
	}

	/** Set what the Reset button does. */
	void setResetActions(Runnable runSettings, Runnable everything)
	{
		this.resetRunSettings = runSettings;
		this.resetEverything = everything;
		rebuild();
	}

	private void askReset()
	{
		final String runOnly = "Run settings";
		final String all = "Everything";
		final Object[] options = {runOnly, all, "Cancel"};
		final int choice = JOptionPane.showOptionDialog(this,
			"<html>Run settings: the Farm and Travel tabs and the highlights.<br>"
				+ "Everything: also your house, unlocks, presets, tracked charges and learned run times.<br><br>"
				+ "This can't be undone.</html>",
			"Reset Farm Run Autopilot", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
			options[2]);
		if (choice == 0)
		{
			resetRunSettings.run();
		}
		else if (choice == 1)
		{
			resetEverything.run();
		}
	}

	/** Highlights, hint arrow and colours shown during a run (SPEC 13.4). Global, not per account. */
	private void addGuidance(CollapsibleSection s)
	{
		final FarmRunAutopilotConfig c = guidance.get();
		s.addContent(checkBox("Highlight the patch", c.highlightPatch(), true,
			"Outline the patch the current step is about", on -> guidance.set("highlightPatch", on)));
		s.addContent(checkBox("Hint arrow to the patch", c.hintArrow(), true,
			"The game's hint arrow over the patch, also shown on the minimap", on -> guidance.set("hintArrow", on)));
		s.addContent(checkBox("Highlight NPCs", c.highlightGardener(), true,
			"Outline the gardener when paying them, and the quetzal after the Civitas teleport",
			on -> guidance.set("highlightGardener", on)));
		s.addContent(checkBox("Highlight items to use", c.highlightItems(), true,
			"Outline the seed, compost, tool or teleport to use next in your inventory and equipment",
			on -> guidance.set("highlightItems", on)));
		s.addContent(checkBox("Highlight the spell to cast", c.highlightSpell(), true,
			"Outline the teleport (or Cure Plant) to cast next in your spellbook", on -> guidance.set("highlightSpell", on)));
		s.addContent(checkBox("Walking lines (Shortest Path)", c.shortestPathLines(), true,
			"If the Shortest Path plugin is installed, it draws the walk for each \"walk to\" step: walking only, no "
				+ "teleports. Your own Shortest Path settings come back when the step changes.",
			on -> guidance.set("shortestPathLines", on)));
		s.addContent(colourRow("Patch colour", c.patchColour(), "patchColour"));
		s.addContent(colourRow("NPC colour", c.npcColour(), "npcColour"));
		s.addContent(colourRow("Item colour", c.itemColour(), "itemColour"));
	}

	/** A label with a colour swatch that opens RuneLite's colour picker. */
	private JComponent colourRow(String text, Color colour, String key)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 3, 0));
		final JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeSmallFont());
		final JButton swatch = new JButton();
		swatch.setFocusPainted(false);
		swatch.setBackground(colour);
		swatch.setPreferredSize(new Dimension(40, 20));
		swatch.setToolTipText("Change the " + text.toLowerCase());
		swatch.addActionListener(e -> guidance.pickColour(this, text, swatch.getBackground(), key,
			swatch::setBackground));
		row.add(label, BorderLayout.CENTER);
		row.add(swatch, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(CONTROL_WIDTH, row.getPreferredSize().height));
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

}
