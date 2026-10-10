package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Setup and Rules tabs (SPEC 13.2, split by Sean 2026-10-07): Setup holds what changes now and then,
 * Rules what is set once. Rebuilt from the saved settings whenever they reload or the account's access
 * changes; every edit is saved straight away.
 */
class SetupPanel extends JPanel
{
	enum Page
	{
		SETUP,
		RULES
	}

	/** Room left for controls after the sidebar's and sections' borders. */
	private static final int CONTROL_WIDTH = PluginPanel.PANEL_WIDTH - 40;

	private final SettingsStore settings;
	private final AccessChecker accessChecker;
	private final PatchDebugPanel patchDebugPanel;
	private final Page page;
	/** Run guidance settings, shown on the Rules page only (null on Setup). */
	private final GuidanceSettings guidance;

	SetupPanel(Page page, SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker,
		GuidanceSettings guidance)
	{
		this.page = page;
		this.guidance = guidance;
		this.settings = settings;
		this.accessChecker = accessChecker;
		this.patchDebugPanel = new PatchDebugPanel(patchTracker);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		rebuild();
	}

	void refreshPatches()
	{
		patchDebugPanel.refresh();
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

		if (page == Page.SETUP)
		{
			add(cropsSection(config, access));
			add(runOptionsSection(config));
			add(presetsSection());
		}
		else
		{
			add(patchesSection(config, access));
			add(protectionSection(config));
			add(travelSection(config, access));
			add(pohSection(account));
			add(unlocksSection(account, access));
			add(routeSection(config));
			if (guidance != null)
			{
				add(guidanceSection());
			}
			add(storageSection(config));

			final CollapsibleSection debug = section("Testing & debug");
			debug.addContent(checkBox("Count every patch (full run)", config.isSupplyFullRun(), true,
				"Supplies for every selected patch, not just the ones that are due. Handy for checking numbers.",
				on -> saveRun(() -> config.setSupplyFullRun(on))));
			debug.addContent(patchDebugPanel);
			add(debug);
		}

		revalidate();
		repaint();
	}

	// Sections

	private JComponent patchesSection(RunConfig config, AccessSnapshot access)
	{
		final CollapsibleSection s = section("Patches");
		s.addContent(note("Untick patches you don't want in your runs. Locked patches show what they need."));
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

		s.addContent(subheader("Disease-free patches"));
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
	private JComponent protectionSection(RunConfig config)
	{
		final CollapsibleSection s = section("Protection");
		s.addContent(checkBox("Bring gardener payments noted (trees and fruit trees)", config.isPayWithNotes(), true,
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
			s.addContent(label("Compost"));
			s.addContent(combo(enumChoices(Compost.values()), config.getCompost().get(type),
				c -> saveRun(() -> config.getCompost().put(type, c))));
		}
		final CollapsibleSection overrides = section("Per-patch overrides");
		overrides.addContent(note("Use a different protection for single patches."));
		for (Patch patch : Patch.values())
		{
			if (!patch.getType().isProtectable())
			{
				continue;
			}
			final List<Choice<Protection>> choices = new ArrayList<>();
			choices.add(Choice.of(null, "Default"));
			choices.addAll(enumChoices(Protection.values()));
			overrides.addContent(label(patch.getDisplayName()));
			overrides.addContent(combo(choices, config.getProtectionOverrides().get(patch),
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
		s.addContent(overrides);

		s.addContent(subheader("Herb patches"));
		s.addContent(label("Compost"));
		s.addContent(combo(enumChoices(Compost.values()), config.getCompost().get(PatchType.HERB),
			c -> saveRun(() -> config.getCompost().put(PatchType.HERB, c))));
		s.addContent(checkBox("Use Cure Plant (Lunar)", config.isUseCurePlant(), true, null,
			on -> saveRun(() -> config.setUseCurePlant(on))));
		s.addContent(checkBox("Use Resurrect Crops (Arceuus)", config.isUseResurrectCrops(), true, null,
			on -> saveRun(() -> config.setUseResurrectCrops(on))));
		s.addContent(checkBox("Suggest yield boosts", config.isRecommendEquipmentBoosts(), true,
			"Magic secateurs and Farming cape/outfit as optional items",
			on -> saveRun(() -> config.setRecommendEquipmentBoosts(on))));
		return s;
	}

	private JComponent travelSection(RunConfig config, AccessSnapshot access)
	{
		final CollapsibleSection s = section("Travel");
		s.addContent(note("Auto picks the fastest method you have. Locked methods show what they need."));
		s.addContent(note("\"Runes instead of tablets everywhere\" is in Setup > Run options."));

		s.addContent(subheader("Fairy ring access"));
		s.addContent(note("How to get to a fairy ring before dialling a code. If your pick can't be used (not "
			+ "carried or owned), Auto chooses. Your house ring is set in My POH."));
		final List<Choice<FairyRingAccess>> ways = new ArrayList<>();
		ways.add(Choice.of(null, "Auto (fastest)"));
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
			final String needs = !way.getItems().isEmpty()
				? "Needs: " + way.getItems().get(way.getItems().size() - 1).getDisplayName()
				+ (way.getItems().size() > 1 ? " or higher" : "")
				: "Needs: " + way.getSpell().getDisplayName() + " (spell or tablet)";
			ways.add(new Choice<>(way, way.getDisplayName() + (locked ? " (locked)" : ""), !locked,
				locked ? AccessSnapshot.describe(missing) : needs));
		}
		s.addContent(label("Way to a fairy ring"));
		s.addContent(combo(ways, config.getFairyRingWay(), w -> saveRun(() -> config.setFairyRingWay(w))));
		s.addContent(checkBox("Use the ring by the last stop when quicker", config.isUseNearbyFairyRing(), true,
			"Walk to the fairy ring next to the stop you just finished (e.g. CIR after the Farming Guild)",
			on -> saveRun(() -> config.setUseNearbyFairyRing(on))));

		for (Location location : Location.values())
		{
			final List<Choice<TravelMethod>> choices = new ArrayList<>();
			choices.add(Choice.of(null, "Auto (best)"));
			boolean hasSpell = false;
			for (TravelMethod method : TravelMethod.values())
			{
				if (method.getDestination() != location)
				{
					continue;
				}
				hasSpell |= method.getSpell() != null;
				final List<Requirement> missing = access.missingFor(method);
				final boolean locked = !missing.isEmpty();
				choices.add(new Choice<>(method, method.getDisplayName() + (locked ? " (locked)" : ""), !locked,
					locked ? AccessSnapshot.describe(missing) : method.getNote()));
			}

			s.addContent(subheader(location.getDisplayName()));
			s.addContent(combo(choices, config.getTravel().get(location),
				m -> saveRun(() ->
				{
					if (m == null)
					{
						config.getTravel().remove(location);
					}
					else
					{
						config.getTravel().put(location, m);
					}
				})));
			if (hasSpell)
			{
				s.addContent(checkBox("Runes instead of tabs here", config.getRunesNotTabsAt().contains(location),
					true, "Setup > Run options can switch this on for every location",
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

	private JComponent pohSection(AccountSettings account)
	{
		final PohSetup poh = account.getPoh();
		final CollapsibleSection s = section("My POH");
		s.addContent(note(poh.getLastDetected() > 0
			? "Furniture last detected " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
			.format(new Date(poh.getLastDetected() * 1000)) + ". Edit anything below."
			: "Enter your house (or leave and come back in) to detect the jewellery box, pool, altar, fairy ring "
			+ "and spirit tree. "
			+ "Set the portal location and nexus by hand."));
		final JButton rescan = smallButton("Rescan house");
		rescan.setToolTipText("<html>Detection only adds furniture. If you removed or downgraded something, rescan:<br>"
			+ "this clears the detected furniture and your next house visit fills it in again.<br>"
			+ "The portal location and nexus destinations are kept.</html>");
		rescan.addActionListener(e ->
		{
			PohDetector.clearDetected(poh);
			settings.saveAccount(true);
			accessChecker.requestRefresh();
		});
		s.addContent(rescan);

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

	private JComponent unlocksSection(AccountSettings account, AccessSnapshot access)
	{
		final CollapsibleSection s = section("Unlocks");
		s.addContent(note("Things the plugin can't detect. Tick the ones you have."));
		if (access.isKnown())
		{
			// Also a check that the game's quest point total is read right
			s.addContent(note("Quest points: " + access.getQuestPoints() + " / "
				+ (access.getMaxQuestPoints() > 0 ? access.getMaxQuestPoints() : "unknown")
				+ (access.isMet(Requirement.allQuests()) ? " (quest point cape usable)" : "")));
		}
		for (Unlock unlock : Unlock.values())
		{
			if (AccessChecker.isDetected(unlock))
			{
				final boolean has = access.isKnown() && access.getUnlocks().contains(unlock);
				s.addContent(checkBox(unlock.getDescription(), has, false, "Detected from your quests", on ->
				{
				}));
				continue;
			}
			s.addContent(checkBox(unlock.getDescription(), account.getManualUnlocks().contains(unlock), true, null,
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
		return s;
	}

	private JComponent routeSection(RunConfig config)
	{
		final CollapsibleSection s = section("Route");
		final List<Choice<Location>> starts = new ArrayList<>();
		for (Location location : Location.values())
		{
			starts.add(Choice.of(location, location.getDisplayName()));
		}
		s.addContent(label("Start at"));
		s.addContent(combo(starts, config.getStartLocation(), l -> saveRun(() -> config.setStartLocation(l))));
		s.addContent(checkBox("Finish near a bank", config.isEndNearBank(), true, null,
			on -> saveRun(() -> config.setEndNearBank(on))));
		s.addContent(checkBox("Walk when it's nearly as quick as teleporting", config.isPreferWalking(), true,
			"A walk up to about 20 seconds slower is used instead of a teleport, saving charges and clicks "
				+ "(e.g. one Falador Teleport for Falador Park and Taverley)",
			on -> saveRun(() -> config.setPreferWalking(on))));
		s.addContent(spinnerRow("Include a run type when this % of its patches are due",
			spinner(config.getDueThresholdPercent(), 1, 100, 5, v -> saveRun(() -> config.setDueThresholdPercent(v)))));
		return s;
	}

	private JComponent runOptionsSection(RunConfig config)
	{
		final CollapsibleSection s = section("Run options");
		s.addContent(label("Route"));
		s.addContent(combo(enumChoices(RouteMode.values()), config.getRouteMode(),
			m -> saveRun(() -> config.setRouteMode(m))));
		s.addContent(checkBox("Runes instead of tablets everywhere", config.isUseRunesNotTabs(), true,
			"Per-location choices are in Rules > Travel",
			on -> saveRun(() -> config.setUseRunesNotTabs(on))));
		s.addContent(label("Outfit to bring"));
		s.addContent(combo(enumChoices(Outfit.values()), config.getOutfit(), o -> saveRun(() -> config.setOutfit(o))));
		s.addContent(checkBox("Remind me to drop weeds and pots", config.isRemindToDrop(), true,
			"During a run, a reminder under your character while you carry weeds or empty plant pots",
			on -> saveRun(() -> config.setRemindToDrop(on))));
		s.addContent(spinnerRow("Stamina doses to bring", spinner(config.getStaminaDoses(), 0, 40, 1,
			v -> saveRun(() -> config.setStaminaDoses(v)))));
		s.addContent(spinnerRow("Plant cures to bring (backup)", spinner(config.getPlantCureDoses(), 0, 40, 1,
			v -> saveRun(() -> config.setPlantCureDoses(v)))));
		return s;
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
	}

	/**
	 * @param affectsAccess the change can lock or unlock patches or methods, so re-check access
	 */
	private void saveAccount(Runnable change, boolean affectsAccess)
	{
		change.run();
		settings.saveAccount(false);
		if (affectsAccess)
		{
			accessChecker.requestRefresh();
		}
	}

	/** Highlights, hint arrow and colours shown during a run (SPEC 13.4). Global, not per account. */
	private JComponent guidanceSection()
	{
		final CollapsibleSection s = section("Run guidance");
		final FarmRunAutopilotConfig c = guidance.get();
		s.addContent(checkBox("Highlight the patch", c.highlightPatch(), true,
			"Outline the patch the current step is about", on -> guidance.set("highlightPatch", on)));
		s.addContent(checkBox("Hint arrow to the patch", c.hintArrow(), true,
			"The game's hint arrow over the patch, also shown on the minimap", on -> guidance.set("hintArrow", on)));
		s.addContent(checkBox("Highlight the gardener", c.highlightGardener(), true,
			"Outline the gardener when the step is to pay them", on -> guidance.set("highlightGardener", on)));
		s.addContent(checkBox("Highlight items to use", c.highlightItems(), true,
			"Outline the seed, compost, tool or teleport to use next in your inventory and equipment",
			on -> guidance.set("highlightItems", on)));
		s.addContent(colourRow("Patch colour", c.patchColour(), "patchColour"));
		s.addContent(colourRow("Gardener colour", c.npcColour(), "npcColour"));
		s.addContent(colourRow("Item colour", c.itemColour(), "itemColour"));
		return s;
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
		final CollapsibleSection s = section("Presets");
		s.addContent(note("Save these settings under a name (e.g. \"Quick herbs\") and switch between them at the "
			+ "top of the Run tab. Presets cover crops, rules, travel and route; My POH and unlocks are shared."));
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
		return new CollapsibleSection(title, settings.isSectionOpen(title), open -> settings.setSectionOpen(title, open));
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
			if (choice.getValue() == selected)
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
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
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
