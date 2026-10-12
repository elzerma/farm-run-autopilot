package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.DailyLimits;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.ChargeBudget;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.PohSetup;
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
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import net.runelite.client.util.LinkBrowser;

/**
 * The Account tab: what was detected, the house, unlocks, storage sources, display settings, and testing.
 */
class AccountPage extends SetupPanel
{
	/** The Unlocks section's title, which is also how the Run tab opens it. */
	static final String UNLOCKS = "Unlocks";
	private static final String DEBUG = "Testing & debug";

	private final PatchDebugPanel patchDebugPanel;
	/** The Detected lines, refilled without rebuilding the page. */
	private final JPanel detected = new JPanel();
	/** What the Reset button does; null until set. */
	private Runnable resetRunSettings;
	private Runnable resetEverything;
	/** The Unlocks section as last drawn, to scroll to it. */
	private JComponent unlocksSectionShown;
	/** Runs guided tests for the help list in Testing & debug; null until shown. */
	private TestRunner testRunner;
	private Runnable showRunTab;
	/** The help list as last drawn, to scroll to it. */
	private JComponent helpListShown;
	/** Run guidance settings (highlights, colours, walking lines). */
	private final GuidanceSettings guidance;

	AccountPage(SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker, Runnable changed,
		GuidanceSettings guidance)
	{
		super(settings, accessChecker, changed);
		this.guidance = guidance;
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
		add(detectedSection());
		add(pohSection(account));
		unlocksSectionShown = unlocksSection(account);
		add(unlocksSectionShown);
		add(storageSection(config));
		add(displaySection(config));
		add(debugSection(config));
	}

	void refreshPatches()
	{
		patchDebugPanel.refresh();
	}

	/** Open the Unlocks section and scroll to it (Account page). Swing thread. */
	void revealUnlocks()
	{
		settings.setSectionOpen(UNLOCKS, true);
		rebuild();
		SwingUtilities.invokeLater(() ->
		{
			final JComponent section = unlocksSectionShown;
			if (section != null)
			{
				section.scrollRectToVisible(new Rectangle(0, 0, section.getWidth(), section.getHeight()));
			}
		});
	}

	/** Show the help list in Testing & debug (Account page). */
	void showHelpList(TestRunner testRunner, Runnable showRunTab)
	{
		this.testRunner = testRunner;
		this.showRunTab = showRunTab;
		rebuild();
	}

	/** Open Testing & debug and scroll to the help list (Account page). Swing thread. */
	void revealHelpList()
	{
		settings.setSectionOpen(DEBUG, true);
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

	/** What the plugin worked out by itself: nothing to set here. */
	private JComponent detectedSection()
	{
		final CollapsibleSection s = section("Detected");
		s.help("Worked out from your account and what you hold. Hover a line to see how.");
		s.addContent(detected);
		refreshDetected();
		return s;
	}

	private JComponent pohSection(AccountSettings account)
	{
		final PohSetup poh = account.getPoh();
		final CollapsibleSection s = section("My house");
		s.addContent(checkBox("Detect furniture when I enter my house (beta)", account.isAutoDetectHouse(), true,
			"Fills in the jewellery box, pool, altar, fairy ring and spirit tree below from your house. Beta: it skips "
				+ "other players' houses by how you got in, which can't be fully checked yet; if it gets your house wrong, "
				+ "please report it.",
			on -> saveAccount(() -> account.setAutoDetectHouse(on), false)));
		if (account.isAutoDetectHouse())
		{
			final JButton report = smallButton("Report a problem");
			report.setToolTipText("Opens the plugin's GitHub issues page");
			report.addActionListener(e -> LinkBrowser.browse(HelpWanted.ISSUES_URL));
			s.addContent(report);
			s.addContent(note(poh.getLastDetected() > 0
				? "Last scanned " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
				.format(new Date(poh.getLastDetected() * 1000))
				: "Enter your house to scan it"));
			final JButton rescan = smallButton("Rescan house");
			rescan.setToolTipText("<html>Detection only adds furniture. If you removed or downgraded something, "
				+ "rescan:<br>this clears the detected furniture and your next house visit fills it in again.<br>"
				+ "The portal location and nexus destinations are kept.</html>");
			rescan.addActionListener(e ->
			{
				PohDetector.clearDetected(poh);
				settings.saveAccount(true);
				accessChecker.requestRefresh();
			});
			s.addContent(rescan);
		}
		s.help("Set the portal location and nexus destinations by hand.");

		final List<Choice<HousePortal>> portals = new ArrayList<>();
		portals.add(Choice.of(null, "Unknown / no house"));
		for (HousePortal portal : HousePortal.values())
		{
			portals.add(Choice.of(portal, portal.getDisplayName() + " (" + portal.getConstructionLevel() + " Con)"));
		}
		s.addContent(label("House portal location"));
		s.addContent(combo(portals, poh.getPortal(), p -> saveAccount(() -> poh.setPortal(p), true)));
		s.addContent(checkBox("House set to teleport outside", poh.isTeleportOutside(), true,
			"Teleport to House lands outside the portal instead of inside",
			on -> saveAccount(() -> poh.setTeleportOutside(on), false)));

		final List<Choice<JewelleryBoxTier>> boxes = new ArrayList<>();
		boxes.add(Choice.of(null, "None"));
		for (JewelleryBoxTier tier : JewelleryBoxTier.values())
		{
			boxes.add(Choice.of(tier, title(tier.name()) + " (" + tier.getConstructionLevel() + " Con)"));
		}
		s.addContent(label("Jewellery box"));
		s.addContent(combo(boxes, poh.getJewelleryBox(), b -> saveAccount(() -> poh.setJewelleryBox(b), false)));

		final List<Choice<PoolTier>> pools = new ArrayList<>();
		pools.add(Choice.of(null, "None"));
		for (PoolTier tier : PoolTier.values())
		{
			pools.add(Choice.of(tier, title(tier.name()) + (tier.isRestoresRunEnergy() ? "" : " (no run energy)")));
		}
		s.addContent(label("Pool"));
		s.addContent(combo(pools, poh.getPool(), p -> saveAccount(() -> poh.setPool(p), false)));

		final List<Choice<PohAltar>> altars = new ArrayList<>();
		altars.add(Choice.of(null, "None"));
		for (PohAltar altar : PohAltar.values())
		{
			altars.add(Choice.of(altar, altar.getDisplayName()));
		}
		s.addContent(label("Spellbook altar"));
		s.addContent(combo(altars, poh.getAltar(), a -> saveAccount(() -> poh.setAltar(a), true)));
		s.addContent(checkBox("Fairy ring", poh.isFairyRing(), true, null,
			on -> saveAccount(() -> poh.setFairyRing(on), false)));
		s.addContent(checkBox("Spirit tree", poh.isSpiritTree(), true, null,
			on -> saveAccount(() -> poh.setSpiritTree(on), false)));

		s.addContent(subheader("Portal nexus destinations"));
		for (PortalNexus.Destination destination : PortalNexus.Destination.values())
		{
			s.addContent(checkBox(destination.getDisplayName(), poh.getNexusDestinations().contains(destination), true, null,
				on -> saveAccount(() ->
				{
					if (on)
					{
						poh.getNexusDestinations().add(destination);
					}
					else
					{
						poh.getNexusDestinations().remove(destination);
					}
				}, false)));
		}
		return s;
	}

	private JComponent unlocksSection(AccountSettings account)
	{
		final AccessSnapshot access = accessChecker.getSnapshot();
		final List<Unlock> review = AccessChecker.toReview(settings.editRunConfig(), account);
		final int ticked = account.getManualUnlocks().size();
		final CollapsibleSection s = section(UNLOCKS, ticked == 0 ? null : ticked + " ticked");
		s.help("Ticked for you where the game shows it, or once you pass a planted spirit tree or the Weiss "
			+ "fire. Tick the rest that you have; the plugin can't "
			+ "see them until you do.");
		for (Unlock unlock : Unlock.values())
		{
			if (AccessChecker.isDetected(unlock))
			{
				// Shown in Detected
				continue;
			}
			if (unlock.hasVarbit() && access.getUnlocks().contains(unlock) && !account.getManualUnlocks().contains(unlock))
			{
				s.addContent(checkBox(unlock.getDescription() + " (detected)", true, false,
					"Read from the game", on ->
					{
					}));
				continue;
			}
			final boolean flagged = review.contains(unlock);
			s.addContent(checkBox(unlock.getDescription() + (flagged ? " (check)" : ""),
				account.getManualUnlocks().contains(unlock), true,
				flagged ? "Your run has a patch or a way to one that needs this" : null,
				on -> saveAccount(() ->
				{
					if (on)
					{
						account.getManualUnlocks().add(unlock);
					}
					else
					{
						account.getManualUnlocks().remove(unlock);
					}
				}, true)));
		}
		if (!account.isUnlocksReviewed())
		{
			final JButton done = smallButton("These are right");
			done.setToolTipText("Stop asking on the Run tab; you can still change these any time");
			done.addActionListener(e -> saveAccount(() -> account.setUnlocksReviewed(true), true));
			s.addContent(done);
		}
		return s;
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

	private JComponent debugSection(RunConfig config)
	{
		final CollapsibleSection s = section(DEBUG);
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
			s.addContent(subheader("Tools"));
		}
		s.addContent(checkBox("Count every patch (full run)", config.isSupplyFullRun(), true,
			"Supplies for every selected patch, not just the ones that are due. Handy for checking numbers.",
			on -> saveRun(() -> config.setSupplyFullRun(on))));
		if (resetRunSettings != null)
		{
			final JButton reset = smallButton("Reset to defaults...");
			reset.setToolTipText("Put this plugin's settings back to how they were when it was installed");
			reset.addActionListener(e -> askReset());
			s.addContent(reset);
		}
		s.addContent(subheader("Patch details"));
		s.addContent(patchDebugPanel);
		return s;
	}

	/** Set what the Reset button does (Account page). */
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

	private JComponent storageSection(RunConfig config)
	{
		final CollapsibleSection s = section("Storage sources");
		s.help("Bank, inventory, equipment and the tool leprechaun are always counted.");
		s.addContent(checkBox("Group storage (GIM)", config.isUseGroupStorage(), true, null,
			on -> saveRun(() -> config.setUseGroupStorage(on))));
		s.addContent(checkBox("Seed vault", config.isUseSeedVault(), true, null,
			on -> saveRun(() -> config.setUseSeedVault(on))));
		return s;
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
