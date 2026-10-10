package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.DailyLimits;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.ChargeBudget;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.route.TravelPick;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.settings.TravelStyle;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.LeprechaunItem;
import com.farmrunautopilot.testing.TestRunner;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The Farm, Travel and Account tabs, and preset management on the Run tab (SPEC 13.2; layout in
 * docs/plans/sidebar-ux.md). Rebuilt from the saved settings whenever they reload or the account's access
 * changes; every edit is saved straight away.
 */
class SetupPanel extends JPanel
{
	/** One tab per question: what to grow, how to get around, what the account has; presets sit on the Run tab. */
	enum Page
	{
		FARM,
		TRAVEL,
		ACCOUNT,
		PRESETS
	}

	/** Room left for controls after the sidebar's and sections' borders. */
	private static final int CONTROL_WIDTH = PluginPanel.PANEL_WIDTH - 40;
	/** Remembered like an open section: the Locations list shows every location. */
	private static final String SHOW_ALL_LOCATIONS = "Show every location";
	/** The Unlocks section's title, which is also how the Run tab opens it. */
	static final String UNLOCKS = "Unlocks";
	private static final String DEBUG = "Testing & debug";

	private final SettingsStore settings;
	private final AccessChecker accessChecker;
	private final PatchDebugPanel patchDebugPanel;
	private final Page page;
	/** Redraws every tab after a change, so summaries and Override tags stay current. */
	private final Runnable changed;
	/** The Detected lines (Account page), refilled without rebuilding the page. */
	private final JPanel detected = new JPanel();
	private Supplier<Holdings> holdings = () -> Holdings.EMPTY;
	/** Location rows the player has open, kept across redraws (Travel page). */
	private final Set<Location> openRows = EnumSet.noneOf(Location.class);
	/** Auto (best)'s picks, for the Travel page; null elsewhere. */
	private RunService runService;
	/** The picks the Travel page was last drawn with, to redraw only when they change. */
	private Map<Location, TravelPick> shownPicks = Collections.emptyMap();
	/** What the Reset button does (Account page); null elsewhere. */
	private Runnable resetRunSettings;
	private Runnable resetEverything;
	/** The Unlocks section as last drawn (Account page), to scroll to it; null elsewhere. */
	private JComponent unlocksSectionShown;
	/** Runs guided tests for the help list in Testing & debug (Account page); null elsewhere. */
	private TestRunner testRunner;
	private Runnable showRunTab;
	/** The help list as last drawn, to scroll to it. */
	private JComponent helpListShown;
	/** Run guidance settings, shown on the Account page only (null elsewhere). */
	private final GuidanceSettings guidance;

	SetupPanel(Page page, SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker,
		Runnable changed,
		GuidanceSettings guidance)
	{
		this.page = page;
		this.changed = changed;
		this.guidance = guidance;
		this.settings = settings;
		this.accessChecker = accessChecker;
		this.patchDebugPanel = new PatchDebugPanel(patchTracker);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		detected.setLayout(new BoxLayout(detected, BoxLayout.Y_AXIS));
		detected.setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		rebuild();
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

	/** Show Auto (best)'s picks on the Travel page. */
	void showAutoPicks(RunService runService)
	{
		this.runService = runService;
		rebuild();
	}

	/** Redraw the Travel page if Auto (best)'s picks changed. Call on the Swing thread. */
	void refreshAutoPicks()
	{
		if (page == Page.TRAVEL && runService != null && !runService.getAutoPicks().equals(shownPicks))
		{
			rebuild();
		}
	}

	/** What the player holds, for the Detected section (Account page). */
	void showHoldings(Supplier<Holdings> holdings)
	{
		this.holdings = holdings;
		refreshDetected();
	}

	/** Re-read what the plugin has detected, without rebuilding the page. Call on the Swing thread. */
	void refreshDetected()
	{
		if (page != Page.ACCOUNT)
		{
			return;
		}
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
			final long scanned = settings.getAccount().getPoh().getLastDetected();
			addDetected("House", !settings.getAccount().isAutoDetectHouse() ? "Detection off (beta)"
					: scanned > 0 ? "Scanned " + DateFormat.getDateInstance(DateFormat.SHORT)
					.format(new Date(scanned * 1000)) : "Not scanned yet",
				"Enter your house to detect its furniture; Rescan is in My house");
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
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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
		s.addContent(note("Worked out from your account and what you hold. Hover a line to see how."));
		s.addContent(detected);
		refreshDetected();
		return s;
	}

	/** Rebuilds every control from the current settings and access. Call on the Swing thread. */
	void rebuild()
	{
		removeAll();
		final RunConfig config = settings.getRunConfig();
		final AccountSettings account = settings.getAccount();
		final AccessSnapshot access = accessChecker.getSnapshot();

		if (!settings.hasProfile())
		{
			add(note("Log in to load and save your setup."));
		}
		else if (!access.isKnown())
		{
			add(note("Checking your quests, diaries and levels... nothing is greyed out until that's done."));
		}

		switch (page)
		{
			case FARM:
				add(patchesSection(config, access));
				add(cropsSection(config, access));
				add(diseaseFreeSection(config, access));
				add(protectionSection(config));
				add(compostSection(config));
				add(extrasSection(config));
				add(advancedFarmSection(config));
				break;
			case TRAVEL:
				add(travelDefaultsSection(config, access));
				add(routeSection(config));
				add(travelSection(config, access));
				break;
			case ACCOUNT:
				add(detectedSection());
				add(pohSection(account));
				unlocksSectionShown = unlocksSection(account);
				add(unlocksSectionShown);
				add(storageSection(config));
				add(displaySection(config));
				add(debugSection(config));
				break;
			default:
				add(presetsSection());
				break;
		}

		revalidate();
		repaint();
	}

	// Sections

	private JComponent patchesSection(RunConfig config, AccessSnapshot access)
	{
		int usable = 0;
		int ticked = 0;
		for (Patch patch : Patch.values())
		{
			if (access.missingFor(patch).isEmpty())
			{
				usable++;
				ticked += config.getDisabledPatches().contains(patch) ? 0 : 1;
			}
		}
		final CollapsibleSection s = section("Patches", ticked + " of " + usable + " ticked");
		s.addContent(note("Untick patches you don't want in your runs; locked ones show what they need. Choose trees, "
			+ "fruit trees and herbs at the top of the Run tab: each joins a run once its patches are due."));
		for (PatchType type : PatchType.values())
		{
			s.addContent(subheader(type.getDisplayName() + " patches"));
			for (Patch patch : Patch.values())
			{
				if (patch.getType() != type)
				{
					continue;
				}
				final List<Requirement> missing = access.missingFor(patch);
				final boolean locked = !missing.isEmpty();
				final String name = patch.getLocation().getDisplayName() + (locked ? " (locked)" : "");
				s.addContent(checkBox(name, !locked && !config.getDisabledPatches().contains(patch), !locked,
					locked ? AccessSnapshot.describe(missing) : null,
					on -> saveRun(() ->
					{
						if (on)
						{
							config.getDisabledPatches().remove(patch);
						}
						else
						{
							config.getDisabledPatches().add(patch);
						}
					})));
			}
		}
		return s;
	}

	private JComponent cropsSection(RunConfig config, AccessSnapshot access)
	{
		final CollapsibleSection s = section("Crops");
		final int farming = access.isKnown() ? access.level(Skill.FARMING) : 99;
		s.addContent(checkBox("Use backup choices if I run out", config.isUseBackupCrops(), true,
			"Plant your 2nd, then 3rd choice once you run out of your 1st. Disease-free and protected patches get "
				+ "the best crops first.",
			on ->
			{
				saveRun(() -> config.setUseBackupCrops(on));
				rebuild();
			}));

		for (PatchType type : PatchType.values())
		{
			s.addContent(subheader(type.getDisplayName()));
			if (config.isUseBackupCrops())
			{
				s.addContent(label("1st choice"));
			}
			s.addContent(combo(cropChoices(type, farming, false), config.cropFor(type, farming),
				crop -> saveRun(() -> config.getCrops().put(type, crop))));
			if (!config.isUseBackupCrops())
			{
				continue;
			}

			final List<Crop> backups = config.getBackupCrops().getOrDefault(type, new ArrayList<>());
			for (int i = 0; i < RunConfig.MAX_BACKUP_CROPS; i++)
			{
				final int index = i;
				s.addContent(label(i == 0 ? "2nd choice" : "3rd choice"));
				s.addContent(combo(cropChoices(type, farming, true), index < backups.size() ? backups.get(index) : null,
					crop ->
					{
						saveRun(() -> setBackup(config, type, index, crop));
						// Setting or clearing one choice can shift the other
						rebuild();
					}));
			}
		}

		return s;
	}

	/** Which herbs go in the patches that never get diseased. */
	private JComponent diseaseFreeSection(RunConfig config, AccessSnapshot access)
	{
		final CollapsibleSection s = section("Disease-free herbs",
			config.isPrioritiseDiseaseFreeHerbs() ? config.getDiseaseFreeHerbs().size() + " picked" : "off");
		final int farming = access.isKnown() ? access.level(Skill.FARMING) : 99;
		s.addContent(checkBox("Prioritise herbs for disease-free patches", config.isPrioritiseDiseaseFreeHerbs(), true,
			"Pick herbs that only go in disease-free patches",
			on ->
			{
				saveRun(() -> config.setPrioritiseDiseaseFreeHerbs(on));
				rebuild();
			}));
		if (!config.isPrioritiseDiseaseFreeHerbs())
		{
			return s;
		}
		s.addContent(note("Troll Stronghold, Weiss and Harmony Island, plus Hosidius and Civitas once their "
			+ "unlock is done. They get these herbs first, most valuable first (by GE price); other patches never do."));
		for (Crop crop : Crop.values())
		{
			if (crop.getType() != PatchType.HERB)
			{
				continue;
			}
			final boolean canPlant = crop.getFarmingLevel() <= farming;
			s.addContent(checkBox(crop.getDisplayName() + " (" + crop.getFarmingLevel() + ")",
				config.getDiseaseFreeHerbs().contains(crop), canPlant,
				canPlant ? null : "Needs " + crop.getFarmingLevel() + " Farming",
				on -> saveRun(() ->
				{
					if (on)
					{
						config.getDiseaseFreeHerbs().add(crop);
					}
					else
					{
						config.getDiseaseFreeHerbs().remove(crop);
					}
				})));
		}
		return s;
	}

	private static List<Choice<Crop>> cropChoices(PatchType type, int farming, boolean allowNone)
	{
		final List<Choice<Crop>> choices = new ArrayList<>();
		if (allowNone)
		{
			choices.add(Choice.of(null, "None"));
		}
		for (Crop crop : Crop.values())
		{
			if (crop.getType() == type)
			{
				final boolean canPlant = crop.getFarmingLevel() <= farming;
				choices.add(new Choice<>(crop, crop.getDisplayName() + " (" + crop.getFarmingLevel() + ")", canPlant,
					canPlant ? null : "Needs " + crop.getFarmingLevel() + " Farming"));
			}
		}
		return choices;
	}

	/** Sets the 2nd (index 0) or 3rd (index 1) choice; "None" clears it and anything after it. */
	private static void setBackup(RunConfig config, PatchType type, int index, Crop crop)
	{
		final List<Crop> backups = new ArrayList<>(config.getBackupCrops().getOrDefault(type, new ArrayList<>()));
		while (backups.size() > index)
		{
			backups.remove(backups.size() - 1);
		}
		if (crop != null)
		{
			backups.add(crop);
		}
		config.getBackupCrops().put(type, backups);
	}
	/** Paying the gardener to protect trees and fruit trees. */
	private JComponent protectionSection(RunConfig config)
	{
		final CollapsibleSection s = section("Protection", overrides(config.getProtectionOverrides().size()));
		s.addContent(checkBox("Bring gardener payments noted", config.isPayWithNotes(), true,
			"Gardeners accept noted payment; one inventory slot per item type",
			on -> saveRun(() -> config.setPayWithNotes(on))));

		for (PatchType type : PatchType.values())
		{
			if (!type.isProtectable())
			{
				continue;
			}
			s.addContent(subheader(type.getDisplayName() + " patches"));
			s.addContent(combo(enumChoices(Protection.values()), config.getProtection().get(type),
				p -> saveRun(() -> config.getProtection().put(type, p))));
			s.addContent(checkBox("Pay 200 coins to clear grown " + type.getDisplayName().toLowerCase() + "s",
				config.getPayToClear().contains(type), true,
				"The gardener removes the old tree, so no axe is needed",
				on -> saveRun(() ->
				{
					if (on)
					{
						config.getPayToClear().add(type);
					}
					else
					{
						config.getPayToClear().remove(type);
					}
				})));
		}
		s.addContent(note("Single patches can differ: Advanced > Per-patch protection."));
		return s;
	}

	/** Compost for each patch type, and what to do about disease. */
	private JComponent compostSection(RunConfig config)
	{
		final CollapsibleSection s = section("Compost and cures");
		for (PatchType type : PatchType.values())
		{
			s.addContent(label(type.getDisplayName() + " patches"));
			s.addContent(combo(enumChoices(Compost.values()), config.getCompost().get(type),
				c -> saveRun(() -> config.getCompost().put(type, c))));
		}
		s.addContent(subheader("Disease"));
		s.addContent(checkBox("Use Cure Plant (Lunar)", config.isUseCurePlant(), true, null,
			on -> saveRun(() -> config.setUseCurePlant(on))));
		s.addContent(checkBox("Use Resurrect Crops (Arceuus)", config.isUseResurrectCrops(), true, null,
			on -> saveRun(() -> config.setUseResurrectCrops(on))));
		s.addContent(spinnerRow("Plant cures to bring (backup)", spinner(config.getPlantCureDoses(), 0, 40, 1,
			v -> saveRunQuietly(() -> config.setPlantCureDoses(v)))));
		return s;
	}

	/** Settings most players never need to change. */
	private JComponent advancedFarmSection(RunConfig config)
	{
		final CollapsibleSection s = section("Advanced", overrides(config.getProtectionOverrides().size()));
		s.addContent(spinnerRow("Include a run type when this % of its patches are due",
			spinner(config.getDueThresholdPercent(), 1, 100, 5,
				v -> saveRunQuietly(() -> config.setDueThresholdPercent(v)))));
		s.addContent(subheader("Per-patch protection"));
		s.addContent(note("Use a different protection for single patches. Anything but Default overrides the "
			+ "setting for its patch type in Protection."));
		for (Patch patch : Patch.values())
		{
			if (!patch.getType().isProtectable())
			{
				continue;
			}
			final List<Choice<Protection>> choices = new ArrayList<>();
			choices.add(Choice.of(null, "Default"));
			choices.addAll(enumChoices(Protection.values()));
			s.addContent(label(patch.getDisplayName()));
			s.addContent(combo(choices, config.getProtectionOverrides().get(patch),
				p -> saveRun(() ->
				{
					if (p == null)
					{
						config.getProtectionOverrides().remove(patch);
					}
					else
					{
						config.getProtectionOverrides().put(patch, p);
					}
				})));
		}
		return s;
	}

	/** Optional things to bring that help but aren't needed. */
	private JComponent extrasSection(RunConfig config)
	{
		final CollapsibleSection s = section("Extras to bring");
		s.addContent(label("Outfit"));
		s.addContent(combo(enumChoices(Outfit.values()), config.getOutfit(), o -> saveRun(() -> config.setOutfit(o))));
		s.addContent(checkBox("Suggest yield boosts", config.isRecommendEquipmentBoosts(), true,
			"Magic secateurs and Farming cape/outfit as optional items",
			on -> saveRun(() -> config.setRecommendEquipmentBoosts(on))));
		return s;
	}

	/** Settings for every stop; each location below can override its own teleport. */
	private JComponent travelDefaultsSection(RunConfig config, AccessSnapshot access)
	{
		final CollapsibleSection s = section("Defaults for every stop");
		final List<Choice<TravelStyle>> styles = new ArrayList<>();
		for (TravelStyle style : TravelStyle.values())
		{
			styles.add(new Choice<>(style, style.getDisplayName(), true, style.getDescription()));
		}
		s.addContent(label("Auto (best) picks"));
		s.addContent(combo(styles, config.getTravelStyle(), st -> saveRun(() -> config.setTravelStyle(st))));
		s.addContent(checkBox("Keep the last charge of my rechargeable jewellery", config.isKeepLastCharge(), true,
			"Off: every charge is used. On: the last charge of your last skills necklace, glory, ring of wealth, combat "
				+ "bracelet or teleport crystal is kept back, and another way is used instead",
			on -> saveRun(() -> config.setKeepLastCharge(on))));
		s.addContent(checkBox("Walk when it's nearly as quick as teleporting", config.isPreferWalking(), true,
			"A walk up to about 20 seconds slower is used instead of a teleport, saving charges and clicks "
				+ "(e.g. one Falador Teleport for Falador Park and Taverley)",
			on -> saveRun(() -> config.setPreferWalking(on))));
		s.addContent(spinnerRow("Stamina doses to bring", spinner(config.getStaminaDoses(), 0, 40, 1,
			v -> saveRunQuietly(() -> config.setStaminaDoses(v)))));

		final List<Choice<FairyRingAccess>> ways = new ArrayList<>();
		ways.add(Choice.of(null, "Auto (best)"));
		for (FairyRingAccess way : FairyRingAccess.values())
		{
			if (way == FairyRingAccess.NEARBY)
			{
				continue;
			}
			// Locked when no item for it can be used (the quest point cape also needs every quest done)
			List<Requirement> missing = new ArrayList<>();
			for (TravelItem item : way.getItems())
			{
				missing = access.missing(item.getRequirements());
				if (missing.isEmpty())
				{
					break;
				}
			}
			final boolean locked = !missing.isEmpty();
			final String needs = "Needs: " + way.getItems().get(way.getItems().size() - 1).getDisplayName()
				+ (way.getItems().size() > 1 ? " or higher" : "");
			ways.add(new Choice<>(way, way.getDisplayName() + (locked ? " (locked)" : ""), !locked,
				locked ? AccessSnapshot.describe(missing) : needs));
		}
		s.addContent(label("Way to a fairy ring"));
		s.addContent(combo(ways, config.getFairyRingWay(), w -> saveRun(() -> config.setFairyRingWay(w))));
		s.addContent(note("The ring by the stop you just finished is used whenever it's quicker, and your house "
			+ "ring is set in Account > My house."));
		return s;
	}

	/** A teleport and how it's used (directly, or through the house), as a dropdown entry. */
	@Value
	private static class Way
	{
		TravelMethod method;
		Departure how;
	}

	/**
	 * One row per location: Auto (best)'s pick, or the teleport and how the player chose, and whether a spell
	 * is cast from runes instead of its tablet.
	 */
	private JComponent travelSection(RunConfig config, AccessSnapshot access)
	{
		// Stops a run can include: a ticked, unlocked patch of a ticked run type
		final Set<Location> inRuns = EnumSet.noneOf(Location.class);
		for (Patch patch : Patch.values())
		{
			if (config.getEnabledTypes().contains(patch.getType()) && config.isPatchSelected(patch)
				&& access.missingFor(patch).isEmpty())
			{
				inRuns.add(patch.getLocation());
			}
		}
		int overridden = 0;
		for (Location location : Location.values())
		{
			overridden += isOverridden(config, location) ? 1 : 0;
		}
		final boolean showAll = settings.isSectionOpen(SHOW_ALL_LOCATIONS);
		final Map<Location, TravelPick> picks = runService != null ? runService.getAutoPicks()
			: Collections.emptyMap();
		shownPicks = picks;

		final CollapsibleSection s = section("Locations", overrides(overridden));
		s.addContent(note("Auto (best) shows what it picked for each stop, by the style above. Click a stop to "
			+ "choose a teleport and "
			+ "how to use it instead; that overrides Auto for that stop. Tablets are used before runes."));
		s.addContent(checkBox("Show every location", showAll, true,
			"Off: only stops your runs can include, plus any you've changed",
			on ->
			{
				settings.setSectionOpen(SHOW_ALL_LOCATIONS, on);
				SwingUtilities.invokeLater(changed);
			}));
		for (Location location : Location.values())
		{
			if (!showAll && !inRuns.contains(location) && !isOverridden(config, location))
			{
				continue;
			}
			final TravelPick pick = picks.get(location);
			final String auto = pick != null ? pick.describe() : null;
			final List<Choice<Way>> choices = new ArrayList<>();
			choices.add(Choice.of(null, auto != null ? "Auto (best): " + auto : "Auto (best)"));
			for (TravelMethod method : TravelMethod.values())
			{
				if (method.getDestination() == location)
				{
					for (Departure how : waysToUse(method))
					{
						choices.add(wayChoice(new Way(method, how), location, access));
					}
				}
			}

			final TravelMethod chosen = config.getTravel().get(location);
			final Departure chosenHow = config.getTravelHow().get(location);
			final Way selected = chosen != null ? new Way(chosen, chosenHow != null ? chosenHow : Departure.DIRECT)
				: null;
			final ExpandableRow row = new ExpandableRow(location.getDisplayName(),
				selected != null ? wayLabel(selected, location) : auto != null ? "Auto: " + auto : "Auto",
				isOverridden(config, location), CONTROL_WIDTH, openRows.contains(location),
				open ->
				{
					if (open)
					{
						openRows.add(location);
					}
					else
					{
						openRows.remove(location);
					}
				});
			s.addContent(row);
			row.addContent(combo(choices, selected,
				w -> saveRun(() ->
				{
					if (w == null)
					{
						config.getTravel().remove(location);
						config.getTravelHow().remove(location);
					}
					else
					{
						config.getTravel().put(location, w.getMethod());
						config.getTravelHow().put(location, w.getHow());
					}
				})));

			// Tablets come first; a spell the player can cast may use runes here instead
			final Spell spell = selected != null
				? (selected.getHow() == Departure.DIRECT ? selected.getMethod().getSpell() : null)
				: pick != null && pick.getStop().getDeparture() == Departure.DIRECT && pick.getStop().getMethod() != null
				? pick.getStop().getMethod().getSpell() : null;
			final boolean runesHere = config.getRunesNotTabsAt().contains(location);
			if (runesHere || (spell != null && spell.hasTablet() && access.canCast(spell)))
			{
				row.addContent(checkBox("Use runes instead of a tablet", runesHere, true,
					"Cast this stop's teleport from runes even when you have its tablet",
					on -> saveRun(() ->
					{
						if (on)
						{
							config.getRunesNotTabsAt().add(location);
						}
						else
						{
							config.getRunesNotTabsAt().remove(location);
						}
					})));
			}
		}
		return s;
	}

	/** Every way a method can be used: directly, and through the house where the house has it. */
	private static List<Departure> waysToUse(TravelMethod method)
	{
		final List<Departure> ways = new ArrayList<>();
		ways.add(Departure.DIRECT);
		if (method.getNexus() != null)
		{
			ways.add(Departure.POH_NEXUS);
		}
		if (method.getJewelleryBox() != null)
		{
			ways.add(Departure.POH_JEWELLERY_BOX);
		}
		if (method.getKind() == TravelKind.FAIRY_RING)
		{
			ways.add(Departure.POH_FAIRY_RING);
		}
		if (method.getKind() == TravelKind.SPIRIT_TREE)
		{
			ways.add(Departure.POH_SPIRIT_TREE);
		}
		return ways;
	}

	/** e.g. "Camelot Teleport (tablet)", "House portal nexus: Catherby Teleport". */
	private String wayLabel(Way way, Location location)
	{
		final Spell spell = way.getMethod().getSpell();
		final boolean tablet = spell != null && way.getHow() == Departure.DIRECT && runService != null
			&& runService.usesTablet(spell, location);
		return new TravelPick(new RouteStop(location, way.getMethod(), way.getHow(), 0, false), tablet).describe();
	}

	/** A dropdown entry, greyed out with the reason when it can't be used. */
	private Choice<Way> wayChoice(Way way, Location location, AccessSnapshot access)
	{
		final TravelMethod method = way.getMethod();
		final List<Requirement> missing = access.missingFor(method);
		if (!missing.isEmpty())
		{
			return new Choice<>(way, wayLabel(way, location) + " (locked)", false, AccessSnapshot.describe(missing));
		}
		final PohSetup poh = settings.getAccount().getPoh();
		final boolean inHouse;
		switch (way.getHow())
		{
			case POH_NEXUS:
				inHouse = poh.getNexusDestinations().contains(method.getNexus());
				break;
			case POH_JEWELLERY_BOX:
				inHouse = poh.getJewelleryBox() != null && poh.getJewelleryBox().includes(method.getJewelleryBox());
				break;
			case POH_FAIRY_RING:
				inHouse = poh.isFairyRing();
				break;
			case POH_SPIRIT_TREE:
				inHouse = poh.isSpiritTree();
				break;
			default:
				inHouse = true;
				break;
		}
		if (!inHouse)
		{
			return new Choice<>(way, wayLabel(way, location) + " (not in your house)", false,
				"Set your house's furniture in Account > My house");
		}
		// A spell that can't be cast right now still works as a tablet, so say why rather than lock it
		final Spell spell = method.getSpell();
		if (way.getHow() == Departure.DIRECT && method.getKind() == TravelKind.SPELL && access.isKnown()
			&& !access.canCast(spell))
		{
			final String why = !access.isOnSpellbook(spell) ? title(spell.getSpellbook().name()) + " spellbook"
				: "needs " + spell.getMagicLevel() + " Magic";
			return new Choice<>(way, method.getDisplayName() + " (tablet only: " + why + ")", true,
				"You can't cast this right now (" + why + "). It's used if you carry its teleport tablet; "
					+ "otherwise another way is picked.");
		}
		// Uses left today (Ardougne cloak 2/3, Explorer's ring 2)
		final Integer today = way.getHow() == Departure.DIRECT ? ChargeBudget.leftToday(method, holdings.get()) : null;
		if (today != null)
		{
			return new Choice<>(way, wayLabel(way, location) + (today == 0 ? " (used up today)"
				: " (" + today + " left today)"), true, "Limited per day; resets each day");
		}
		// Charges held, pooled across every piece of the item
		final TravelItem item = way.getHow() == Departure.DIRECT ? method.getItem() : null;
		final Integer charges = item != null ? ChargeBudget.held(item, holdings.get()) : null;
		if (charges != null)
		{
			return new Choice<>(way, wayLabel(way, location) + (charges == 0 ? " (no charges)"
				: " (" + charges + (charges == 1 ? " charge)" : " charges)")), true,
				"Charges are shared by every teleport this item has, across the whole run");
		}
		return new Choice<>(way, wayLabel(way, location), true, method.getNote());
	}

	private JComponent pohSection(AccountSettings account)
	{
		final PohSetup poh = account.getPoh();
		final CollapsibleSection s = section("My house");
		s.addContent(checkBox("Detect furniture when I enter my house (beta)", account.isAutoDetectHouse(), true,
			"Fills in the jewellery box, pool, altar, fairy ring and spirit tree below from your house",
			on -> saveAccount(() -> account.setAutoDetectHouse(on), false)));
		if (account.isAutoDetectHouse())
		{
			s.addContent(note("Beta: it skips other players' houses by how you got in, which can't be fully checked "
				+ "yet. If it gets your house wrong, please report it."));
			final JButton report = smallButton("Report a problem");
			report.setToolTipText("Opens the plugin's GitHub issues page");
			report.addActionListener(e -> LinkBrowser.browse(HelpWanted.ISSUES_URL));
			s.addContent(report);
			s.addContent(note(poh.getLastDetected() > 0
				? "Furniture last detected " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
				.format(new Date(poh.getLastDetected() * 1000)) + ". Edit anything below."
				: "Enter your house (or leave and come back in) to detect its furniture."));
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
		s.addContent(note("Set the portal location and nexus destinations by hand."));

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
		final List<Unlock> review = AccessChecker.toReview(settings.getRunConfig(), account);
		final int ticked = account.getManualUnlocks().size();
		final CollapsibleSection s = section(UNLOCKS, !review.isEmpty() ? review.size() + " to check"
			: ticked == 0 ? null : ticked + " ticked");
		s.addContent(note("Ticked for you where the game shows it, or once you pass a planted spirit tree or the Weiss "
			+ "fire. Tick the rest that you have; the plugin can't "
			+ "see them until you do."));
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

	private JComponent routeSection(RunConfig config)
	{
		final CollapsibleSection s = section("Route");
		s.addContent(label("Order"));
		s.addContent(combo(enumChoices(RouteMode.values()), config.getRouteMode(),
			m -> saveRun(() -> config.setRouteMode(m))));
		s.addContent(note("Off keeps your own order: drag the stops in the route list on the Run tab."));
		final List<Choice<Location>> starts = new ArrayList<>();
		for (Location location : Location.values())
		{
			starts.add(Choice.of(location, location.getDisplayName()));
		}
		s.addContent(label("Start at"));
		s.addContent(combo(starts, config.getStartLocation(), l -> saveRun(() -> config.setStartLocation(l))));
		s.addContent(checkBox("Finish near a bank", config.isEndNearBank(), true, null,
			on -> saveRun(() -> config.setEndNearBank(on))));
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
		s.addContent(note("Bank, inventory, equipment and the tool leprechaun are always counted."));
		s.addContent(checkBox("Group storage (GIM)", config.isUseGroupStorage(), true, null,
			on -> saveRun(() -> config.setUseGroupStorage(on))));
		s.addContent(checkBox("Seed vault", config.isUseSeedVault(), true, null,
			on -> saveRun(() -> config.setUseSeedVault(on))));
		return s;
	}

	// Saving

	private void saveRun(Runnable change)
	{
		change.run();
		settings.saveRunConfig();
		SwingUtilities.invokeLater(changed);
	}

	/** Saves without redrawing the tabs: number boxes would lose focus on every click, and they change no summary. */
	private void saveRunQuietly(Runnable change)
	{
		change.run();
		settings.saveRunConfig();
	}

	/**
	 * @param affectsAccess the change can lock or unlock patches or methods, so re-check access
	 */
	private void saveAccount(Runnable change, boolean affectsAccess)
	{
		change.run();
		settings.saveAccount(false);
		SwingUtilities.invokeLater(changed);
		if (affectsAccess)
		{
			accessChecker.requestRefresh();
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
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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

	/** Save, rename and delete named copies of the run settings (SPEC 13.5). */
	private JComponent presetsSection()
	{
		final CollapsibleSection s = section("Manage presets");
		s.addContent(note("Save these settings under a name (e.g. \"Quick herbs\") and switch between them at the "
			+ "top of the Run tab. Presets cover the Farm and Travel tabs; your house and unlocks are shared."));
		final JButton save = smallButton("Save current settings as...");
		save.addActionListener(e ->
		{
			final String name = askName("Name for this preset:", settings.activePreset());
			if (name == null)
			{
				return;
			}
			if (settings.presetNames().contains(name) && JOptionPane.showConfirmDialog(this,
				"Replace the preset \"" + name + "\" with the current settings?", "Farm Run Autopilot",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			{
				return;
			}
			settings.savePreset(name);
		});
		s.addContent(save);

		final String active = settings.activePreset();
		for (String name : settings.presetNames())
		{
			s.addContent(subheader(name + (name.equals(active) ? " (in use)" : "")));
			final JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
			buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
			final JButton use = smallButton("Use");
			use.setEnabled(!name.equals(active));
			use.addActionListener(e -> settings.applyPreset(name));
			final JButton rename = smallButton("Rename");
			rename.addActionListener(e ->
			{
				final String to = askName("New name for \"" + name + "\":", name);
				if (to != null && !to.equals(name))
				{
					if (settings.presetNames().contains(to))
					{
						JOptionPane.showMessageDialog(this, "There's already a preset called \"" + to + "\".");
						return;
					}
					settings.renamePreset(name, to);
				}
			});
			final JButton delete = smallButton("Delete");
			delete.addActionListener(e ->
			{
				if (JOptionPane.showConfirmDialog(this, "Delete the preset \"" + name + "\"?", "Farm Run Autopilot",
					JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
				{
					settings.deletePreset(name);
				}
			});
			buttons.add(use);
			buttons.add(rename);
			buttons.add(delete);
			buttons.setMaximumSize(new Dimension(CONTROL_WIDTH, buttons.getPreferredSize().height));
			buttons.setAlignmentX(LEFT_ALIGNMENT);
			s.addContent(buttons);
		}
		return s;
	}

	/** A trimmed, non-empty name from the player, or null if they cancelled. */
	private String askName(String prompt, String initial)
	{
		final Object input = JOptionPane.showInputDialog(this, prompt, "Farm Run Autopilot",
			JOptionPane.PLAIN_MESSAGE, null, null, initial);
		if (input == null)
		{
			return null;
		}
		final String name = input.toString().trim();
		return name.isEmpty() ? null : name;
	}

	private static JButton smallButton(String text)
	{
		final JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setAlignmentX(LEFT_ALIGNMENT);
		return button;
	}

	// Building blocks

	/** Sections remember being open or closed between sessions. */
	private CollapsibleSection section(String title)
	{
		return section(title, null);
	}

	/** With a summary of what's set inside, shown even when the section is closed. */
	private CollapsibleSection section(String title, String summary)
	{
		return new CollapsibleSection(title, summary, settings.isSectionOpen(title),
			open -> settings.setSectionOpen(title, open));
	}

	/** A chosen teleport, or runes instead of a tablet at this stop. */
	private static boolean isOverridden(RunConfig config, Location location)
	{
		return config.getTravel().containsKey(location) || config.getRunesNotTabsAt().contains(location);
	}

	/** e.g. "2 overrides", or null for none. */
	private static String overrides(int count)
	{
		return count == 0 ? null : count + (count == 1 ? " override" : " overrides");
	}

	/** A short label with its number box on the same line. */
	private static JComponent spinnerRow(String text, JSpinner spinner)
	{
		final JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 0, 3, 0));
		final JLabel label = new JLabel(wrap(text, CONTROL_WIDTH - 85));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.CENTER);
		row.add(spinner, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(CONTROL_WIDTH, row.getPreferredSize().height));
		return row;
	}

	private static JCheckBox checkBox(String text, boolean selected, boolean enabled, String tooltip,
		Consumer<Boolean> onChange)
	{
		final JCheckBox box = new JCheckBox((String) null, selected);
		box.setEnabled(enabled);
		// HTML text ignores the disabled state, so grey it by hand (before setting the text).
		box.setForeground(enabled ? ColorScheme.TEXT_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);
		box.setText(wrap(text, CONTROL_WIDTH - 30));
		box.setToolTipText(tooltip);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.addActionListener(e -> onChange.accept(box.isSelected()));
		return box;
	}

	private static <T> JComboBox<Choice<T>> combo(List<Choice<T>> choices, T selected, Consumer<T> onChange)
	{
		final JComboBox<Choice<T>> combo = new JComboBox<>();
		Choice<T> current = choices.isEmpty() ? null : choices.get(0);
		for (Choice<T> choice : choices)
		{
			combo.addItem(choice);
			if (Objects.equals(choice.getValue(), selected))
			{
				current = choice;
			}
		}
		combo.setSelectedItem(current);
		combo.setRenderer(new ChoiceRenderer());
		combo.setPreferredSize(new Dimension(CONTROL_WIDTH, 24));
		combo.setMaximumSize(new Dimension(CONTROL_WIDTH, 24));
		combo.setFont(FontManager.getRunescapeSmallFont());

		final Object[] previous = {current};
		combo.addActionListener(e ->
		{
			@SuppressWarnings("unchecked")
			final Choice<T> picked = (Choice<T>) combo.getSelectedItem();
			if (picked == null || picked == previous[0])
			{
				return;
			}
			if (!picked.isEnabled())
			{
				combo.setSelectedItem(previous[0]);
				return;
			}
			previous[0] = picked;
			onChange.accept(picked.getValue());
		});
		return combo;
	}

	private static <E extends Enum<E>> List<Choice<E>> enumChoices(E[] values)
	{
		final List<Choice<E>> choices = new ArrayList<>();
		for (E value : values)
		{
			choices.add(Choice.of(value, value.toString()));
		}
		return choices;
	}

	private static JSpinner spinner(int value, int min, int max, int step, IntConsumer onChange)
	{
		final JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
		spinner.setPreferredSize(new Dimension(70, 24));
		spinner.setMaximumSize(new Dimension(70, 24));
		spinner.addChangeListener(e -> onChange.accept((Integer) spinner.getValue()));
		return spinner;
	}

	private static JLabel label(String text)
	{
		final JLabel label = new JLabel(wrap(text));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(4, 0, 2, 0));
		return label;
	}

	private static JLabel subheader(String text)
	{
		final JLabel label = new JLabel(wrap(text));
		label.setForeground(ColorScheme.TEXT_COLOR);
		label.setBorder(new EmptyBorder(8, 0, 2, 0));
		return label;
	}

	private static JLabel note(String text)
	{
		final JLabel label = new JLabel(wrap(text));
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 0, 6, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static String wrap(String text)
	{
		return wrap(text, CONTROL_WIDTH - 20);
	}

	private static String wrap(String text, int width)
	{
		return UiText.wrap(text, width);
	}

	private static String title(String enumName)
	{
		final String spaced = enumName.replace('_', ' ').toLowerCase();
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	/** Greys out disabled choices and shows their tooltip. */
	private static class ChoiceRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
			boolean cellHasFocus)
		{
			final Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof Choice)
			{
				final Choice<?> choice = (Choice<?>) value;
				if (!choice.isEnabled())
				{
					// Never highlight locked entries: grey text on the grey hover colour is invisible.
					c.setBackground(list.getBackground());
					c.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				}
				setToolTipText(choice.getTooltip());
			}
			return c;
		}
	}
}
