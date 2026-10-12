package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;

/**
 * The Farm tab: which patches, what to plant, protection, compost and extras to bring.
 */
class FarmPage extends SetupPanel
{
	FarmPage(SettingsStore settings, AccessChecker accessChecker, Runnable changed)
	{
		super(settings, accessChecker, changed);
		rebuild();
	}

	@Override
	void addSections(RunConfig config, AccountSettings account, AccessSnapshot access)
	{
		add(patchesSection(config, access));
		add(cropsSection(config, access));
		add(diseaseFreeSection(config, access));
		add(protectionSection(config));
		add(compostSection(config));
		add(extrasSection(config));
		add(advancedFarmSection(config));
	}

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
		s.help("Untick patches you don't want in your runs; locked ones show what they need on hover. Choose trees, "
			+ "fruit trees and herbs at the top of the Run tab: each joins a run once its patches are due.");
		// One group per type that opens and closes, two tick boxes to a row (Sean)
		for (PatchType type : PatchType.values())
		{
			final JPanel grid = new JPanel(new GridLayout(0, 2, 4, 0));
			grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
			int typeUsable = 0;
			int typeTicked = 0;
			for (Patch patch : Patch.values())
			{
				if (patch.getType() != type)
				{
					continue;
				}
				final List<Requirement> missing = access.missingFor(patch);
				final boolean locked = !missing.isEmpty();
				final boolean on = !locked && !config.getDisabledPatches().contains(patch);
				typeUsable += locked ? 0 : 1;
				typeTicked += on ? 1 : 0;
				// Locked ones are greyed, with what they need on hover
				grid.add(halfCheckBox(patch.getLocation().getDisplayName(), on, !locked,
					locked ? "Locked: " + AccessSnapshot.describe(missing) : null,
					tick -> saveRun(() ->
					{
						if (tick)
						{
							config.getDisabledPatches().remove(patch);
						}
						else
						{
							config.getDisabledPatches().add(patch);
						}
					})));
			}
			final CollapsibleSection group = section(type.getDisplayName() + " patches",
				typeTicked + " of " + typeUsable);
			grid.setMaximumSize(new Dimension(CONTROL_WIDTH, grid.getPreferredSize().height));
			group.addContent(grid);
			s.addContent(group);
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

		// One line per choice (Sean): "1st [drop-down]"; the 3rd only once the 2nd is set
		for (PatchType type : PatchType.values())
		{
			s.addContent(subheader(type.getDisplayName()));
			final JComponent first = combo(cropChoices(type, farming, false), config.cropFor(type, farming),
				crop -> saveRun(() -> config.getCrops().put(type, crop)));
			if (!config.isUseBackupCrops())
			{
				s.addContent(first);
				continue;
			}
			s.addContent(labeled("1st", first));

			final List<Crop> backups = config.getBackupCrops().getOrDefault(type, new ArrayList<>());
			for (int i = 0; i < RunConfig.MAX_BACKUP_CROPS && i <= backups.size(); i++)
			{
				final int index = i;
				s.addContent(labeled(i == 0 ? "2nd" : "3rd", combo(cropChoices(type, farming, true),
					index < backups.size() ? backups.get(index) : null,
					crop ->
					{
						saveRun(() -> setBackup(config, type, index, crop));
						// Setting or clearing one choice shows or hides the next
						rebuild();
					})));
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
		s.help("Troll Stronghold, Weiss and Harmony Island, plus Hosidius and Civitas once their "
			+ "unlock is done. They get these herbs first, most valuable first (by GE price); other patches never do.");
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
		s.addContent(spinnerRow("Plant cures to bring (backup)", spinner(config.getPlantCureDoses(), 0, 40, 1,
			v -> saveRunQuietly(() -> config.setPlantCureDoses(v)))));
		return s;
	}

	/** Settings most players never need to change. */
	private JComponent advancedFarmSection(RunConfig config)
	{
		final boolean perPatch = config.isUsingProtectionOverrides();
		final CollapsibleSection s = section("Advanced",
			perPatch ? overrides(config.getProtectionOverrides().size()) : null);
		s.addContent(spinnerRow("Include a run type when this % of its patches are due",
			spinner(config.getDueThresholdPercent(), 1, 100, 5,
				v -> saveRunQuietly(() -> config.setDueThresholdPercent(v)))));

		// Rarely used, so behind a tick box (Sean); ticking it off keeps the choices but ignores them
		s.addContent(checkBox("Different protection for single patches", perPatch, true,
			"Choose another protection for a patch than its type has in Protection. Ticking this off keeps your "
				+ "choices but ignores them.",
			on ->
			{
				saveRun(() -> config.setUseProtectionOverrides(on));
				rebuild();
			}));
		if (!perPatch)
		{
			return s;
		}
		// Only the patches that differ, then a way to add another
		final List<Choice<Protection>> choices = new ArrayList<>();
		choices.add(Choice.of(null, "Same as its type"));
		choices.addAll(enumChoices(Protection.values()));
		final List<Choice<Patch>> addable = new ArrayList<>();
		addable.add(Choice.of(null, "Add a patch..."));
		for (Patch patch : Patch.values())
		{
			if (!patch.getType().isProtectable())
			{
				continue;
			}
			final Protection override = config.getProtectionOverrides().get(patch);
			if (override == null)
			{
				addable.add(Choice.of(patch, patch.getDisplayName()));
				continue;
			}
			s.addContent(label(patch.getDisplayName()));
			s.addContent(combo(choices, override, p ->
			{
				saveRun(() ->
				{
					if (p == null)
					{
						config.getProtectionOverrides().remove(patch);
					}
					else
					{
						config.getProtectionOverrides().put(patch, p);
					}
				});
				rebuild();
			}));
		}
		if (addable.size() > 1)
		{
			s.addContent(combo(addable, null, patch ->
			{
				if (patch == null)
				{
					return;
				}
				// Starts as the other choice from its type's, so adding it changes something
				final Protection type = config.getProtection().get(patch.getType());
				saveRun(() -> config.getProtectionOverrides().put(patch,
					type == Protection.PAY_GARDENER ? Protection.COMPOST_ONLY : Protection.PAY_GARDENER));
				rebuild();
			}));
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
			"Magic secateurs (+10% herb yield) as an optional item; worn when nothing else needs the weapon slot",
			on -> saveRun(() -> config.setRecommendEquipmentBoosts(on))));
		return s;
	}

}
