package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RouteMode;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.Component;
import java.awt.Dimension;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Setup tab (SPEC 13.2). Rebuilt from the saved settings whenever they reload or the account's
 * access changes; every edit is saved straight away.
 */
class SetupPanel extends JPanel
{
	/** Room left for controls after the sidebar's and sections' borders. */
	private static final int CONTROL_WIDTH = PluginPanel.PANEL_WIDTH - 40;

	private final SettingsStore settings;
	private final AccessChecker accessChecker;
	private final PatchDebugPanel patchDebugPanel;
	/** Which sections are open, remembered across rebuilds. All start closed. */
	private final Map<String, Boolean> expanded = new HashMap<>();

	SetupPanel(SettingsStore settings, AccessChecker accessChecker, PatchTracker patchTracker)
	{
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

		add(runTypesSection(config));
		add(patchesSection(config, access));
		add(cropsSection(config, access));
		add(protectionSection(config));
		add(travelSection(config, access));
		add(pohSection(account));
		add(unlocksSection(account, access));
		add(routeSection(config));
		add(storageSection(config));

		final CollapsibleSection debug = section("Patch states (debug)");
		debug.addContent(patchDebugPanel);
		add(debug);

		revalidate();
		repaint();
	}

	// Sections

	private JComponent runTypesSection(RunConfig config)
	{
		final CollapsibleSection s = section("Run types");
		for (PatchType type : PatchType.values())
		{
			s.addContent(checkBox(type.getDisplayName() + " runs", config.getEnabledTypes().contains(type), true, null,
				on -> saveRun(() ->
				{
					if (on)
					{
						config.getEnabledTypes().add(type);
					}
					else
					{
						config.getEnabledTypes().remove(type);
					}
				})));
		}
		s.addContent(label("Include a run type when this % of its patches are due:"));
		s.addContent(spinner(config.getDueThresholdPercent(), 1, 100, 5,
			v -> saveRun(() -> config.setDueThresholdPercent(v))));
		return s;
	}

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
		s.addContent(note("One seed or sapling per run type."));
		final int farming = access.isKnown() ? access.level(Skill.FARMING) : 99;
		for (PatchType type : PatchType.values())
		{
			final List<Choice<Crop>> choices = new ArrayList<>();
			for (Crop crop : Crop.values())
			{
				if (crop.getType() == type)
				{
					final boolean canPlant = crop.getFarmingLevel() <= farming;
					choices.add(new Choice<>(crop, crop.getDisplayName() + " (" + crop.getFarmingLevel() + ")", canPlant,
						canPlant ? null : "Needs " + crop.getFarmingLevel() + " Farming"));
				}
			}
			s.addContent(label(type.getDisplayName()));
			s.addContent(combo(choices, config.cropFor(type, farming),
				crop -> saveRun(() -> config.getCrops().put(type, crop))));
		}
		return s;
	}

	private JComponent protectionSection(RunConfig config)
	{
		final CollapsibleSection s = section("Protection");
		for (PatchType type : PatchType.values())
		{
			if (!type.isProtectable())
			{
				continue;
			}
			s.addContent(subheader(type.getDisplayName() + " patches"));
			s.addContent(combo(enumChoices(Protection.values()), config.getProtection().get(type),
				p -> saveRun(() -> config.getProtection().put(type, p))));
			s.addContent(checkBox("Pay 200 coins to clear grown trees", config.getPayToClear().contains(type), true,
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
		s.addContent(checkBox("Bring payments noted", config.isPayWithNotes(), true,
			"Gardeners accept noted payment; one inventory slot per item type",
			on -> saveRun(() -> config.setPayWithNotes(on))));

		s.addContent(subheader("Per-patch overrides"));
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

		s.addContent(subheader("Herb patches"));
		s.addContent(label("Compost"));
		s.addContent(combo(enumChoices(Compost.values()), config.getCompost().get(PatchType.HERB),
			c -> saveRun(() -> config.getCompost().put(PatchType.HERB, c))));
		s.addContent(label("Plant cure doses to bring (backup):"));
		s.addContent(spinner(config.getPlantCureDoses(), 0, 40, 1, v -> saveRun(() -> config.setPlantCureDoses(v))));
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
		s.addContent(checkBox("Use runes instead of tabs everywhere", config.isUseRunesNotTabs(), true, null,
			on -> saveRun(() -> config.setUseRunesNotTabs(on))));

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
					!config.isUseRunesNotTabs(), null,
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
			: "Enter your house to detect the jewellery box, pool, fairy ring and spirit tree. "
			+ "Set the portal location and nexus by hand."));

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
		s.addContent(label("Route optimisation"));
		s.addContent(combo(enumChoices(RouteMode.values()), config.getRouteMode(),
			m -> saveRun(() -> config.setRouteMode(m))));

		final List<Choice<Location>> starts = new ArrayList<>();
		for (Location location : Location.values())
		{
			starts.add(Choice.of(location, location.getDisplayName()));
		}
		s.addContent(label("Start at"));
		s.addContent(combo(starts, config.getStartLocation(), l -> saveRun(() -> config.setStartLocation(l))));
		s.addContent(checkBox("Finish near a bank", config.isEndNearBank(), true, null,
			on -> saveRun(() -> config.setEndNearBank(on))));
		s.addContent(label("Restore run energy below (%):"));
		s.addContent(spinner(config.getEnergyThreshold(), 0, 100, 5, v -> saveRun(() -> config.setEnergyThreshold(v))));
		s.addContent(label("...before a walk of at least (tiles):"));
		s.addContent(spinner(config.getEnergyMinTiles(), 0, 200, 5, v -> saveRun(() -> config.setEnergyMinTiles(v))));
		s.addContent(label("Stamina doses to bring:"));
		s.addContent(spinner(config.getStaminaDoses(), 0, 40, 1, v -> saveRun(() -> config.setStaminaDoses(v))));
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

	// Building blocks

	private CollapsibleSection section(String title)
	{
		return new CollapsibleSection(title, expanded.getOrDefault(title, false), open -> expanded.put(title, open));
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

	/**
	 * HTML that wraps to {@code width} screen pixels, since plain Swing text never wraps. Swing's HTML
	 * renderer scales CSS "px" by 1.3 (javax.swing.text.html.CSS), so divide that back out.
	 */
	private static String wrap(String text, int width)
	{
		return "<html><body style='width:" + (width * 10 / 13) + "px'>" + text + "</body></html>";
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
