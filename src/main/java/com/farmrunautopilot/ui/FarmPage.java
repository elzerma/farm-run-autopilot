package com.farmrunautopilot.ui;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.EmptyBuckets;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
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
		add(atThePatchesSection(config));
		add(contractsSection(config));
		add(extrasSection(config));
		add(advancedFarmSection(config));
	}

	/**
	 * A run type's name on its one-line rows and in lists, as on the Run tab ("Trees", "Fruit trees"); "Hardwood" and
	 * "Celastrus" without "trees", so every crop's name fits beside them (docs/plans/fresh-eyes-audit.md, F1).
	 */
	private static String rowName(PatchType type)
	{
		switch (type)
		{
			case HARDWOOD:
				return "Hardwood";
			case CELASTRUS:
				return "Celastrus";
			default:
				return type.getPluralName();
		}
	}

	private JComponent patchesSection(RunConfig config, AccessSnapshot access)
	{
		// Counted over the run types in your runs (docs/plans/fresh-eyes-audit.md, F5)
		int usable = 0;
		int ticked = 0;
		for (Patch patch : Patch.values())
		{
			if (config.getEnabledTypes().contains(patch.getType()) && access.missingFor(patch).isEmpty())
			{
				usable++;
				ticked += config.getDisabledPatches().contains(patch) ? 0 : 1;
			}
		}
		final CollapsibleSection s = section("Patches", ticked + " of " + usable + " ticked");
		s.help("Untick patches you don't want in your runs; locked ones show what they need on hover. Choose the run "
			+ "types at the top of the Run tab: each joins a run once its patches are due.");
		// One group per type that opens and closes, two tick boxes to a row (elzerma)
		for (PatchType type : PatchType.values())
		{
			final List<JComponent> boxes = new ArrayList<>();
			final List<Patch> ofType = new ArrayList<>();
			int typeUsable = 0;
			int typeTicked = 0;
			for (Patch patch : Patch.values())
			{
				if (patch.getType() != type)
				{
					continue;
				}
				ofType.add(patch);
				final List<Requirement> missing = access.missingFor(patch);
				final boolean locked = !missing.isEmpty();
				final boolean on = !locked && !config.getDisabledPatches().contains(patch);
				typeUsable += locked ? 0 : 1;
				typeTicked += on ? 1 : 0;
				// Locked ones are greyed, with what they need on hover
				boxes.add(halfCheckBox(patch.getPlaceName(), on, !locked,
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
			final boolean inRuns = config.getEnabledTypes().contains(type);
			final CollapsibleSection group = section(type.getDisplayName() + " patches",
				inRuns ? typeTicked + " of " + typeUsable : "not in runs");
			if (!inRuns)
			{
				// The Run tab's box and this one look like one switch and aren't (docs/plans/fresh-eyes-audit.md, F5)
				group.help("Tick " + RunPanel.runTypeBox(type) + " at the top of the Run tab to put them in your runs.");
			}
			// Ticks or unticks the whole type, as a Travel tab family's box does (elzerma, 2026-10-22); locked patches
			// too, so one unlocked later follows the rest of its type
			final JCheckBox all = groupBox(GroupTick.of(typeUsable, typeTicked),
				"Tick or untick every " + type.getDisplayName().toLowerCase() + " patch", on -> saveRun(() ->
				{
					if (on)
					{
						config.getDisabledPatches().removeAll(ofType);
					}
					else
					{
						config.getDisabledPatches().addAll(ofType);
					}
				}));
			all.setEnabled(typeUsable > 0);
			group.lead(all);
			group.addContent(twoPerRow(boxes));
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

		// Nothing to choose where the type grows one crop (mushroom, belladonna, seaweed, calquat, celastrus, redwood,
		// spirit and crystal trees: elzerma, 2026-10-26)
		final List<PatchType> types = new ArrayList<>();
		final List<String> names = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			if (Arrays.stream(Crop.values()).filter(c -> c.getType() == type && c.isChoosable()).count() >= 2)
			{
				types.add(type);
				names.add(rowName(type));
			}
		}
		final int width = labelWidth(names);
		// One line per choice (elzerma): "1st [drop-down]"; the 3rd only once the 2nd is set
		for (PatchType type : types)
		{
			final Crop firstCrop = config.cropFor(type, farming);
			final JComponent first = combo(cropChoices(type, farming, false, Collections.emptyList()), firstCrop,
				crop ->
				{
					saveRun(() ->
					{
						config.getCrops().put(type, crop);
						// A crop can only be one of the choices: drop it from the backups
						config.getBackupCrops().getOrDefault(type, new ArrayList<>()).remove(crop);
					});
					rebuild();
				});
			if (!config.isUseBackupCrops())
			{
				// The type and its crop on one line (docs/plans/fresh-eyes-audit.md, F1)
				s.addContent(labeled(rowName(type), first, width));
				continue;
			}
			s.addContent(subheader(rowName(type)));
			s.addContent(labeled("1st", first));

			final List<Crop> backups = config.getBackupCrops().getOrDefault(type, new ArrayList<>());
			for (int i = 0; i < RunConfig.MAX_BACKUP_CROPS && i <= backups.size(); i++)
			{
				final int index = i;
				// Each list leaves out the choices above it (elzerma)
				final List<Crop> above = new ArrayList<>(backups.subList(0, Math.min(index, backups.size())));
				above.add(firstCrop);
				s.addContent(labeled(i == 0 ? "2nd" : "3rd", combo(cropChoices(type, farming, true, above),
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
		// Reserved for those patches rather than put first (CropAllocator; docs/plans/fresh-eyes-audit.md, F4)
		s.addContent(checkBox("Save herbs for disease-free patches", config.isPrioritiseDiseaseFreeHerbs(), true,
			"The herbs you tick go only in disease-free herb patches, most valuable first; your other herb patches never "
				+ "get them.",
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
		final List<JComponent> herbs = new ArrayList<>();
		for (Crop crop : Crop.values())
		{
			if (crop.getType() != PatchType.HERB)
			{
				continue;
			}
			final boolean canPlant = crop.getFarmingLevel() <= farming;
			herbs.add(halfCheckBox(crop.getDisplayName() + " (" + crop.getFarmingLevel() + ")",
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
		s.addContent(twoPerRow(herbs));
		return s;
	}

	/** @param taken crops already chosen above, left out of this list */
	private static List<Choice<Crop>> cropChoices(PatchType type, int farming, boolean allowNone,
		List<Crop> taken)
	{
		final List<Choice<Crop>> choices = new ArrayList<>();
		if (allowNone)
		{
			choices.add(Choice.of(null, "None"));
		}
		for (Crop crop : Crop.values())
		{
			if (crop.getType() == type && crop.isChoosable() && !taken.contains(crop))
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
		s.help("Pay gardener: when there's not enough of the payment, the patch gets the best compost you have instead "
			+ "(ultracompost, then supercompost, then compost). Paid trees and fruit trees get no compost; paid "
			+ "allotments and hops still do, as it raises the harvest.");
		s.addContent(checkBox("Bring gardener payments noted", config.isPayWithNotes(), true,
			"Gardeners accept noted payment; one inventory slot per item type",
			on -> saveRun(() -> config.setPayWithNotes(on))));

		final List<String> names = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			if (type.isProtectable())
			{
				names.add(rowName(type));
			}
		}
		final int width = labelWidth(names);
		// What's done to a grown crop (leaving it growing, paying to clear it) is under At the patches
		// (docs/plans/fresh-eyes-audit.md, F2)
		for (PatchType type : PatchType.values())
		{
			if (!type.isProtectable())
			{
				continue;
			}
			// The type and its protection on one line (docs/plans/fresh-eyes-audit.md, F1)
			s.addContent(labeled(rowName(type), combo(protectionChoices(type), config.getProtection().get(type), p ->
			{
				saveRun(() -> config.getProtection().put(type, p));
				rebuild();
			}), width));
			if (type == PatchType.ALLOTMENT)
			{
				s.addContent(allotmentSets(config));
			}
		}
		singlePatches(config, s);
		return s;
	}

	/**
	 * Another protection for single patches than their type's (allotments are chosen per set above): all protection in one
	 * place (docs/plans/fresh-eyes-audit.md, F3, from Advanced).
	 */
	private void singlePatches(RunConfig config, CollapsibleSection s)
	{
		// Rarely used, so behind a tick box (elzerma); ticking it off keeps the choices but ignores them
		final boolean perPatch = config.isUsingProtectionOverrides();
		s.addContent(checkBox("Different protection for single patches", perPatch, true,
			"Choose another protection for a patch than its type has above. Ticking this off keeps your choices but "
				+ "ignores them.",
			on ->
			{
				saveRun(() -> config.setUseProtectionOverrides(on));
				rebuild();
			}));
		if (!perPatch)
		{
			return;
		}
		// Only the patches that differ, then a way to add another
		final List<Choice<Protection>> choices = new ArrayList<>();
		choices.add(Choice.of(null, "Same as its type"));
		choices.addAll(protectionChoices(PatchType.TREE));
		final List<Choice<Patch>> addable = new ArrayList<>();
		addable.add(Choice.of(null, "Add a patch..."));
		for (Patch patch : Patch.values())
		{
			// Allotments are chosen per set
			if (!patch.getType().isProtectable() || patch.getType() == PatchType.ALLOTMENT)
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
	}

	/** Flower protection is for allotments only. */
	private static List<Choice<Protection>> protectionChoices(PatchType type)
	{
		final List<Choice<Protection>> choices = new ArrayList<>();
		for (Choice<Protection> choice : enumChoices(Protection.values()))
		{
			if (choice.getValue() != Protection.FLOWER || type == PatchType.ALLOTMENT)
			{
				choices.add(choice);
			}
		}
		return choices;
	}

	/**
	 * One choice per allotment set (elzerma: the player picks per set), stored on both of its allotments. Sets without
	 * a flower patch or gardener (Harmony Island) have nothing to choose.
	 */
	private JComponent allotmentSets(RunConfig config)
	{
		int changed = 0;
		final List<Patch> firsts = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (patch.getType() == PatchType.ALLOTMENT && patch.hasGardener() && firsts.stream()
				.noneMatch(other -> other.getLocation() == patch.getLocation()))
			{
				firsts.add(patch);
				changed += config.getProtectionOverrides().containsKey(patch) ? 1 : 0;
			}
		}
		final CollapsibleSection sets = section("Each set", changed == 0 ? null : changed + " changed");
		sets.help("Flower: the set's flower patch gets the flower that protects what's planted (a scarecrow for "
			+ "sweetcorn, white lily for anything), and a grown one is left in place. Where no flower you can plant covers "
			+ "the crop, the gardener is paid instead.");
		final List<Choice<Protection>> choices = new ArrayList<>();
		choices.add(Choice.of(null, "Same as all allotments"));
		choices.addAll(protectionChoices(PatchType.ALLOTMENT));
		for (Patch first : firsts)
		{
			final List<Patch> both = new ArrayList<>();
			for (Patch patch : first.getLocation().getPatches())
			{
				if (patch.getType() == PatchType.ALLOTMENT)
				{
					both.add(patch);
				}
			}
			sets.addContent(label(first.getLocation().getDisplayName()));
			sets.addContent(combo(choices, config.getProtectionOverrides().get(first), p ->
			{
				saveRun(() ->
				{
					for (Patch patch : both)
					{
						if (p == null)
						{
							config.getProtectionOverrides().remove(patch);
						}
						else
						{
							config.getProtectionOverrides().put(patch, p);
						}
					}
				});
				rebuild();
			}));
		}
		return sets;
	}

	/** Compost for each patch type, and what to do about disease. */
	private JComponent compostSection(RunConfig config)
	{
		final CollapsibleSection s = section("Compost and cures");
		final List<String> names = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			names.add(rowName(type));
		}
		final int width = labelWidth(names);
		for (PatchType type : PatchType.values())
		{
			// The type and its compost on one line (docs/plans/fresh-eyes-audit.md, F1)
			s.addContent(labeled(rowName(type), combo(enumChoices(Compost.values()), config.getCompost().get(type),
				c -> saveRun(() -> config.getCompost().put(type, c))), width));
		}
		s.addContent(subheader("Disease"));
		s.addContent(checkBox("Use Cure Plant (Lunar)", config.isUseCurePlant(), true,
			"On a diseased patch, cast Cure Plant (on the Lunar spellbook) instead of using a plant cure. A run with herbs "
				+ "brings runes for one cast.",
			on -> saveRun(() -> config.setUseCurePlant(on))));
		s.addContent(tip(spinnerRow("Plant cures to bring", spinner(config.getPlantCureDoses(), 0, 40, 1,
			v -> saveRunQuietly(() -> config.setPlantCureDoses(v)))),
			"In case a patch is diseased. None are brought when every patch on the run is disease-free or protected."));
		return s;
	}

	/**
	 * What's done at each stop besides planting (patch actions P1, the section elzerma liked): noting the produce on the tool
	 * leprechaun and what happens to empty buckets. Cleaning herbs and making compost join it in P2 and P3.
	 */
	/** Farming Guild contracts (docs/plans/farming-contracts.md; elzerma, 2026-10-17 and 2026-11-02). */
	private JComponent contractsSection(RunConfig config)
	{
		final CollapsibleSection s = section("Farming contracts");
		s.help("The Farming Guild's patches hold the wiki's pre-plants for Guildmaster Jane's contracts, left alone until a "
			+ "contract asks for one, and anything else there is picked once grown and the pre-plant planted, once you have "
			+ "its seed. A contract's "
			+ "crop is checked or picked once grown and the patch replanted, one that isn't growing is planted, and the "
			+ "guild stop ends at Jane: hand one in, take another (the highest tier your level allows), or ask for an "
			+ "easier one when a slow crop's patch is taken.");
		s.addContent(checkBox("Build farming contracts into runs", config.isFarmingContracts(), true,
			"The guild's patches are kept for contracts instead of your usual crops, and a run stops at the guild whenever "
				+ "a contract needs it",
			on -> saveRun(() -> config.setFarmingContracts(on))));
		return s;
	}

	private JComponent atThePatchesSection(RunConfig config)
	{
		final CollapsibleSection s = section("At the patches");
		s.help("Each patch is harvested, then planted and composted before the next. The tool leprechaun is visited only when "
			+ "needed: for compost, a tool or room, to store a compost bin's compost, or for the next bin's buckets. Each visit "
			+ "also notes the produce, stores the empty buckets and takes compost for the next stops when there's room.");
		s.addContent(checkBox("Clean herbs", config.isCleanHerbs(), true,
			"After picking herbs, a step to clean the ones your Herblore level allows, before noting them",
			on -> saveRun(() -> config.setCleanHerbs(on))));
		s.addContent(checkBox("Note produce on the tool leprechaun", config.isNoteProduce(), true,
			"The produce is noted on visits to the leprechaun, saving inventory slots",
			on -> saveRun(() -> config.setNoteProduce(on))));
		s.addContent(label("Empty buckets"));
		s.addContent(combo(enumChoices(EmptyBuckets.values()), config.getEmptyBuckets(),
			b -> saveRun(() -> config.setEmptyBuckets(b))));
		// Patch actions P3, in the shape elzerma liked (2026-10-24)
		s.addContent(checkBox("Make compost in the compost bins", config.isMakeCompost(), true,
			"At stops with a compost bin: empty it when it's ready, then fill it with the crops below as they're picked",
			on -> saveRun(() -> config.setMakeCompost(on))));
		if (config.isMakeCompost())
		{
			s.addContent(checkBox("Make ultracompost (brings volcanic ash)", config.isMakeUltracompost(), true,
				"A bin of supercompost gets 25 volcanic ash (50 in the Farming Guild's big bin) before it's emptied",
				on -> saveRun(() -> config.setMakeUltracompost(on))));
			s.addContent(label("Crops for the bin"));
			for (Crop crop : Crop.values())
			{
				// Never snape grass (elzerma: worth more sold or for potions)
				if (crop.getType() != PatchType.ALLOTMENT || crop == Crop.SNAPE_GRASS)
				{
					continue;
				}
				s.addContent(checkBox(crop.getDisplayName(), config.getBinCrops().contains(crop), true,
					crop == Crop.WATERMELON ? "Makes supercompost, as long as nothing else goes in the bin"
						: "Makes the bin regular compost", on -> saveRun(() ->
					{
						if (on)
						{
							config.getBinCrops().add(crop);
						}
						else
						{
							config.getBinCrops().remove(crop);
						}
					})));
			}
		}
		grownCrops(config, s);
		return s;
	}

	/**
	 * What's done to a grown crop, two lists two to a row (docs/plans/fresh-eyes-audit.md, F2, from Protection): picked and
	 * left growing (fruit trees, bushes, cacti: elzerma, 2026-10-24) or kept for travel (spirit trees), and paying the
	 * gardener to clear a grown tree.
	 */
	private void grownCrops(RunConfig config, CollapsibleSection s)
	{
		s.addContent(subheader("Leave growing after picking"));
		final List<JComponent> keep = new ArrayList<>();
		for (PatchType type : PatchType.values())
		{
			if (!type.keepable())
			{
				continue;
			}
			keep.add(halfCheckBox(rowName(type), config.keepsGrowing(type), true, type == PatchType.SPIRIT_TREE
				? "Check their health once grown, then leave them standing for travel rather than digging them up to replant"
				: "Check health and pick each run, without clearing and replanting; a dead one is replanted",
				on -> saveRun(() ->
				{
					if (on)
					{
						config.getKeepGrowing().add(type);
					}
					else
					{
						config.getKeepGrowing().remove(type);
					}
				})));
		}
		s.addContent(twoPerRow(keep));

		// Bushes, cacti and spirit trees are dug up with the spade every run brings; only Alexandra clears a redwood
		s.addContent(subheader("Pay 200 coins to clear grown",
			"A grown redwood is always cleared by paying Alexandra 2,000 coins: nobody else can."));
		final List<JComponent> clear = new ArrayList<>();
		for (PatchType type : EnumSet.of(PatchType.TREE, PatchType.FRUIT_TREE, PatchType.HARDWOOD, PatchType.CALQUAT,
			PatchType.CELASTRUS))
		{
			clear.add(halfCheckBox(rowName(type), config.getPayToClear().contains(type), true,
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
		s.addContent(twoPerRow(clear));
	}

	/** Settings most players never need to change. Different protection for single patches is under Protection. */
	private JComponent advancedFarmSection(RunConfig config)
	{
		final CollapsibleSection s = section("Advanced");
		s.addContent(tip(spinnerRow("Include a run type when this % of its patches are due",
			spinner(config.getDueThresholdPercent(), 1, 100, 5,
				v -> saveRunQuietly(() -> config.setDueThresholdPercent(v)))),
			"A run type joins your run once this share of its patches is due. 100: only when all of them are."));
		return s;
	}

	/** Optional things to bring that help but aren't needed. */
	private JComponent extrasSection(RunConfig config)
	{
		final CollapsibleSection s = section("Extras to bring");
		// On one line (docs/plans/fresh-eyes-audit.md, F1)
		s.addContent(labeled("Outfit", combo(enumChoices(Outfit.values()), config.getOutfit(),
			o -> saveRun(() -> config.setOutfit(o))), labelWidth(Collections.singletonList("Outfit"))));
		s.addContent(checkBox("Suggest yield boosts", config.isRecommendEquipmentBoosts(), true,
			"Magic secateurs (+10% herb, allotment and hops yield) as an optional item; worn when nothing else needs "
				+ "the weapon slot",
			on -> saveRun(() -> config.setRecommendEquipmentBoosts(on))));
		return s;
	}

}
