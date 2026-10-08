package com.farmrunautopilot.supply;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.RuneAmount;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelKind;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchStatusText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Works out what to bring for the next run (SPEC 11). Pure: everything it needs is passed in, so it can
 * run anywhere and be unit tested.
 */
public final class SupplyCalculator
{
	private static final int INVENTORY_SLOTS = 28;
	private static final int STAMINA_DOSES_PER_POTION = 4;
	private static final String[] ORDINALS = {"1st", "2nd", "3rd"};

	// Where each kind of line is changed, shown in its tooltip
	private static final String CROPS = "Setup > Crops";
	private static final String RUN_OPTIONS = "Setup > Run options";
	private static final String PROTECTION = "Rules > Protection";
	private static final String TRAVEL = "Rules > Travel";

	private SupplyCalculator()
	{
	}

	/**
	 * @param predictions current prediction per patch (null for never-seen patches)
	 * @param fullRun count every selected patch, not just the due ones
	 * @param itemName display name for an item ID
	 * @param price Grand Exchange price of an item ID, used to rank herbs for disease-free patches
	 */
	public static SupplyPlan calculate(RunConfig config, AccessSnapshot access, Holdings holdings,
		Function<Patch, PatchPrediction> predictions, long now, boolean fullRun, IntFunction<String> itemName,
		IntToLongFunction price)
	{
		final List<SupplyLine> lines = new ArrayList<>();
		final List<String> notDue = new ArrayList<>();
		final List<String> travelPlan = new ArrayList<>();
		final List<String> warnings = new ArrayList<>();
		final Map<PatchType, Integer> patchCounts = new EnumMap<>(PatchType.class);

		// Which patches are in this run
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (!config.isPatchSelected(patch) || !access.missingFor(patch).isEmpty())
			{
				continue;
			}
			final PatchPrediction prediction = predictions.apply(patch);
			if (!fullRun && !isDue(prediction))
			{
				notDue.add(patch.getDisplayName() + ": " + PatchStatusText.describe(prediction, now));
				continue;
			}
			patches.add(patch);
			patchCounts.merge(patch.getType(), 1, Integer::sum);
		}
		if (patches.isEmpty())
		{
			return new SupplyPlan(lines, patchCounts, notDue, travelPlan, warnings, 0, "", 0, Collections.emptyMap());
		}

		final Holdings carried = holdings.carriedOnly();
		final int farming = access.isKnown() ? access.level(Skill.FARMING) : 99;
		// Before access is known, assume no diary: better to bring one payment too many.
		final boolean faladorElite = access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE));
		final Map<Rune, Integer> runeNeed = new EnumMap<>(Rune.class);
		int coins = 0;
		boolean needAxe = false;
		boolean anyWeeds = false;
		final Map<Integer, Integer> payments = new LinkedHashMap<>();
		final Map<Compost, Integer> compost = new EnumMap<>(Compost.class);

		// Which crop goes where (1st choice, or backups when stock runs short)
		final Map<Patch, Crop> plantings = new LinkedHashMap<>();
		final List<Crop> reservedHerbs = diseaseFreeHerbs(config, farming, price);
		for (PatchType type : PatchType.values())
		{
			final List<Patch> ofType = new ArrayList<>();
			for (Patch patch : patches)
			{
				if (patch.getType() == type)
				{
					ofType.add(patch);
				}
			}
			if (!ofType.isEmpty())
			{
				plantings.putAll(CropAllocator.allocate(ofType, config.cropChoices(type, farming),
					crop -> holdings.count(crop.getPlantItemId()),
					patch -> isSafe(patch, config, access, faladorElite),
					type == PatchType.HERB ? reservedHerbs : Collections.emptyList()));
			}
		}

		for (Patch patch : patches)
		{
			final PatchType type = patch.getType();
			final PatchPrediction prediction = predictions.apply(patch);
			final PatchState state = prediction != null ? prediction.getState() : PatchState.UNKNOWN;
			anyWeeds |= state == PatchState.WEEDS;

			if (type.isProtectable())
			{
				if (needsClearing(state))
				{
					if (config.getPayToClear().contains(type))
					{
						coins += Crop.CLEAR_PATCH_COINS;
					}
					else if (state != PatchState.STUMP)
					{
						needAxe = true;
					}
				}

				final Crop crop = plantings.get(patch);
				final boolean freeProtection = patch == Patch.FALADOR_TREE && faladorElite;
				if (config.protectionFor(patch) == Protection.PAY_GARDENER && !freeProtection)
				{
					payments.merge(crop.getPaymentItemId(), crop.getPaymentQuantity(), Integer::sum);
				}
			}

			final Compost c = config.getCompost().get(type);
			if (c != null && c != Compost.NONE)
			{
				compost.merge(c, 1, Integer::sum);
			}
		}

		// Seeds and saplings, one line per crop
		final Map<Crop, List<String>> plantedAt = new LinkedHashMap<>();
		plantings.forEach((patch, crop) -> plantedAt.computeIfAbsent(crop, k -> new ArrayList<>())
			.add(patch.getLocation().getDisplayName()));
		plantedAt.forEach((crop, places) ->
		{
			final int id = crop.getPlantItemId();
			final int n = places.size();
			// Seeds stack; saplings don't.
			final int slots = crop.getType() == PatchType.HERB ? 1 : n;
			final String role = cropRole(crop, config, farming, reservedHerbs);
			lines.add(line(SupplyLine.Group.SEEDS, itemName.apply(id) + (role.isEmpty() ? "" : " - " + role), n, holdings,
				carried, "Planting at: " + String.join(", ", places), slots, id));
		});

		// Payments
		payments.forEach((id, qty) ->
		{
			final String name = itemName.apply(id) + (config.isPayWithNotes() ? " (noted)" : "");
			lines.add(line(SupplyLine.Group.PAYMENTS, name, qty, holdings, carried,
				"Gardener protection payment", config.isPayWithNotes() ? 1 : qty, id));
		});

		// Travel
		final Map<TravelItem, Integer> travelItems = new LinkedHashMap<>();
		final Map<Integer, Integer> tablets = new LinkedHashMap<>();
		boolean fairyRing = false;
		for (Location location : Location.values())
		{
			if (!hasPatchAt(patches, location))
			{
				continue;
			}
			final TravelMethod method = TravelChooser.choose(location, config, access, holdings);
			if (method == null)
			{
				warnings.add("No unlocked way to reach " + location.getDisplayName());
				continue;
			}
			coins += method.getCoins();
			final boolean auto = config.getTravel().get(location) != method;
			String how = method.getDisplayName();

			final Spell spell = method.getSpell();
			if (spell != null)
			{
				final boolean tablet = spell.hasTablet() && !config.useRunesAt(location)
					&& (holdings.count(spell.getTabletItemId()) > 0 || !access.canCast(spell));
				if (tablet)
				{
					tablets.merge(spell.getTabletItemId(), 1, Integer::sum);
					how += " (tablet)";
				}
				else
				{
					for (RuneAmount amount : spell.getRunes())
					{
						runeNeed.merge(amount.getRune(), amount.getQuantity(), Integer::sum);
					}
					how += " (runes, " + title(spell.getSpellbook().name()) + " spellbook)";
				}
			}
			if (method.getItem() != null)
			{
				travelItems.merge(method.getItem(), 1, Integer::sum);
			}
			if (method.getKind() == TravelKind.FAIRY_RING)
			{
				fairyRing = true;
			}
			travelPlan.add(location.getDisplayName() + ": " + how + (auto ? " (auto)" : ""));
		}
		tablets.forEach((id, n) -> lines.add(line(SupplyLine.Group.TRAVEL, itemName.apply(id), n, holdings, carried,
			null, 1, id)));
		travelItems.forEach((item, stops) -> lines.add(line(SupplyLine.Group.TRAVEL, item.getDisplayName(), 1, holdings, carried,
			stops > 1 ? "Used at " + stops + " stops; charges aren't checked" : "Charges aren't checked", 1,
			item.getItemIds())));
		if (fairyRing && !(access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.ELITE))))
		{
			lines.add(line(SupplyLine.Group.TRAVEL, "Dramen or lunar staff", 1, holdings, carried,
				"Needed for fairy rings until the Elite Lumbridge & Draynor diary", 0, SupplyItems.FAIRY_RING_STAFFS));
		}

		// Spells used on patches
		final int herbs = patchCounts.getOrDefault(PatchType.HERB, 0);
		if (config.isUseCurePlant() && herbs > 0)
		{
			addRunes(runeNeed, Spell.CURE_PLANT);
		}
		if (config.isUseResurrectCrops())
		{
			addRunes(runeNeed, Spell.RESURRECT_CROPS);
		}

		// Runes
		final RuneResult runes = runes(runeNeed, holdings, carried, itemName);
		lines.addAll(runes.lines);

		// Coins
		if (coins > 0)
		{
			lines.add(line(SupplyLine.Group.PAYMENTS, "Coins", coins, holdings, carried,
				"200 per tree cleared by the gardener, plus travel fares", 1, ItemID.COINS));
		}

		// Tools and compost
		lines.add(line(SupplyLine.Group.TOOLS, "Spade", 1, holdings, carried, null, 1, ItemID.SPADE));
		if (!holdings.isAutoweedOn() || anyWeeds)
		{
			lines.add(line(SupplyLine.Group.TOOLS, "Rake", 1, holdings, carried,
				holdings.isAutoweedOn() ? "Some patches have weeds" : "Not needed once Tithe Farm Auto-weed is on",
				1, ItemID.RAKE));
		}
		if (herbs > 0)
		{
			lines.add(line(SupplyLine.Group.TOOLS, "Seed dibber", 1, holdings, carried, null, 1, ItemID.DIBBER));
		}
		if (needAxe)
		{
			lines.add(changeIn(line(SupplyLine.Group.TOOLS, "Axe", 1, holdings, carried,
				"To chop grown trees (pay-to-clear is off)", 1, SupplyItems.AXES), PROTECTION));
		}
		final boolean bottomless = holdings.count(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED) > 0;
		compost.forEach((c, n) ->
		{
			final SupplyLine buckets = line(SupplyLine.Group.TOOLS, c.getDisplayName(), n, holdings, carried,
				bottomless ? "You have a filled bottomless bucket (its type and charges aren't checked)" : null,
				bottomless ? 1 : n, c.getItemId());
			lines.add(changeIn(bottomless ? covered(buckets) : buckets, PROTECTION));
		});

		// Optional
		if (config.isRecommendEquipmentBoosts() && herbs > 0)
		{
			lines.add(changeIn(line(SupplyLine.Group.OPTIONAL, "Magic secateurs", 1, holdings, carried,
				"+10% herb yield; can be worn", 0, SupplyItems.MAGIC_SECATEURS), PROTECTION));
		}
		if (config.getPlantCureDoses() > 0)
		{
			lines.add(line(SupplyLine.Group.OPTIONAL, "Plant cure", config.getPlantCureDoses(), holdings, carried,
				"Backup for diseased patches", config.getPlantCureDoses(), ItemID.PLANT_CURE));
		}
		if (config.getStaminaDoses() > 0)
		{
			int doses = 0;
			int carriedDoses = 0;
			for (Map.Entry<Integer, Integer> e : SupplyItems.STAMINA_DOSES.entrySet())
			{
				doses += holdings.count(e.getKey()) * e.getValue();
				carriedDoses += carried.count(e.getKey()) * e.getValue();
			}
			final int[] staminaIds = SupplyItems.STAMINA_DOSES.keySet().stream().mapToInt(Integer::intValue).toArray();
			lines.add(new SupplyLine(SupplyLine.Group.OPTIONAL, "Stamina doses", config.getStaminaDoses(), doses,
				carriedDoses, holdings.where(staminaIds), null,
				(config.getStaminaDoses() + STAMINA_DOSES_PER_POTION - 1) / STAMINA_DOSES_PER_POTION, false, RUN_OPTIONS,
				staminaIds));
		}

		int slots = 0;
		for (SupplyLine line : lines)
		{
			slots += line.getSlots();
		}
		if (!holdings.isBankKnown())
		{
			warnings.add("Open your bank once so its contents can be counted.");
		}
		if (slots > INVENTORY_SLOTS)
		{
			warnings.add("Needs about " + slots + " inventory slots, more than " + INVENTORY_SLOTS + ".");
		}

		lines.sort((a, b) -> a.getGroup().compareTo(b.getGroup()));
		return new SupplyPlan(Collections.unmodifiableList(lines), patchCounts, notDue, travelPlan, warnings, coins,
			runes.summary, slots, Collections.unmodifiableMap(plantings));
	}

	/**
	 * How a crop got on the list, e.g. "1st choice" or "disease-free only". Empty with a single choice and no
	 * disease-free herbs, where there's nothing to explain.
	 */
	static String cropRole(Crop crop, RunConfig config, int farming, List<Crop> reservedHerbs)
	{
		final List<Crop> choices = config.cropChoices(crop.getType(), farming);
		final List<String> parts = new ArrayList<>();
		final int index = choices.indexOf(crop);
		if (index >= 0 && (choices.size() > 1 || !reservedHerbs.isEmpty()))
		{
			parts.add(ORDINALS[Math.min(index, ORDINALS.length - 1)] + " choice");
		}
		if (reservedHerbs.contains(crop))
		{
			parts.add("disease-free only");
		}
		return String.join(", ", parts);
	}

	/** The player's herbs for disease-free patches that they can plant, most valuable seed first. */
	static List<Crop> diseaseFreeHerbs(RunConfig config, int farming, IntToLongFunction price)
	{
		final List<Crop> herbs = new ArrayList<>();
		if (!config.isPrioritiseDiseaseFreeHerbs())
		{
			return herbs;
		}
		for (Crop crop : config.getDiseaseFreeHerbs())
		{
			if (crop.getFarmingLevel() <= farming)
			{
				herbs.add(crop);
			}
		}
		herbs.sort((a, b) -> Long.compare(price.applyAsLong(b.getPlantItemId()), price.applyAsLong(a.getPlantItemId())));
		return herbs;
	}

	/**
	 * Disease-free or protected: always disease-free, a disease-free unlock the player has, or paid for.
	 * Unknown access counts as not having the unlock.
	 */
	static boolean isSafe(Patch patch, RunConfig config, AccessSnapshot access, boolean faladorElite)
	{
		if (patch.isAlwaysDiseaseFree())
		{
			return true;
		}
		final Requirement diseaseFree = patch.getDiseaseFreeRequirement();
		if (diseaseFree != null && access.isKnown() && access.isMet(diseaseFree))
		{
			return true;
		}
		return patch.getType().isProtectable()
			&& (config.protectionFor(patch) == Protection.PAY_GARDENER || (patch == Patch.FALADOR_TREE && faladorElite));
	}

	/** A patch is due unless it is still growing (SPEC 10). Never-seen patches count as due. */
	static boolean isDue(PatchPrediction prediction)
	{
		return prediction == null || prediction.getState() != PatchState.GROWING;
	}

	/** A grown tree or its stump must be cleared before replanting; unknown patches are assumed grown. */
	static boolean needsClearing(PatchState state)
	{
		switch (state)
		{
			case CHECK_HEALTH:
			case HARVESTABLE:
			case STUMP:
			case UNKNOWN:
				return true;
			default:
				return false;
		}
	}

	static final class RuneResult
	{
		final List<SupplyLine> lines = new ArrayList<>();
		String summary = "";
	}

	/** Runes held after free sources, and the combination runes spent to get there. */
	private static final class RuneAllocation
	{
		final Map<Rune, Integer> have = new EnumMap<>(Rune.class);
		final Map<Integer, Integer> comboUsed = new LinkedHashMap<>();
	}

	/**
	 * Rune lines after free sources: equipped staves cover their runes completely, then combination runes
	 * are spent on the biggest shortfalls first (SPEC 11.3). Worked out twice: for everything held, and for
	 * what is carried.
	 */
	static RuneResult runes(Map<Rune, Integer> need, Holdings holdings, Holdings carried, IntFunction<String> itemName)
	{
		final RuneResult result = new RuneResult();
		if (need.isEmpty())
		{
			return result;
		}
		final RuneAllocation all = allocateRunes(need, holdings);
		final RuneAllocation onYou = allocateRunes(need, carried);

		final List<String> bring = new ArrayList<>();
		final List<String> free = new ArrayList<>();
		need.forEach((rune, n) ->
		{
			final boolean infinite = holdings.getInfiniteRunes().contains(rune);
			final String name = itemName.apply(rune.getItemId());
			String note = null;
			if (infinite)
			{
				note = "Your equipped staff or tome supplies these";
				free.add("staff covers " + title(rune.name()));
			}
			else
			{
				bring.add(n + " " + rune.name().toLowerCase());
				final int inPouch = holdings.getRunePouch().getOrDefault(rune.getItemId(), 0);
				if (inPouch > 0)
				{
					note = "Rune pouch has " + inPouch;
				}
			}
			result.lines.add(new SupplyLine(SupplyLine.Group.RUNES, name, n, all.have.get(rune), onYou.have.get(rune),
				holdings.where(rune.getItemId()), note, infinite || inPouch(holdings, rune) ? 0 : 1, false,
				RUN_OPTIONS + " (runes or tablets), " + TRAVEL, new int[]{rune.getItemId()}));
		});
		all.comboUsed.forEach((id, n) -> free.add(n + " " + itemName.apply(id).toLowerCase()));
		result.summary = String.join(", ", bring) + (free.isEmpty() ? "" : " (" + String.join("; ", free) + ")");
		return result;
	}

	private static RuneAllocation allocateRunes(Map<Rune, Integer> need, Holdings holdings)
	{
		final RuneAllocation result = new RuneAllocation();
		final Map<Rune, Integer> shortfall = new EnumMap<>(Rune.class);
		need.forEach((rune, n) ->
		{
			final int held = holdings.getInfiniteRunes().contains(rune) ? n : holdings.count(rune.getItemId());
			result.have.put(rune, held);
			shortfall.put(rune, Math.max(0, n - held));
		});

		final List<Map.Entry<Integer, Set<Rune>>> combos = new ArrayList<>(SupplyItems.COMBINATION_RUNES.entrySet());
		combos.sort((a, b) -> Integer.compare(covers(b.getValue(), shortfall), covers(a.getValue(), shortfall)));
		for (Map.Entry<Integer, Set<Rune>> combo : combos)
		{
			int largest = 0;
			for (Rune rune : combo.getValue())
			{
				largest = Math.max(largest, shortfall.getOrDefault(rune, 0));
			}
			final int use = Math.min(holdings.count(combo.getKey()), largest);
			if (use <= 0)
			{
				continue;
			}
			result.comboUsed.put(combo.getKey(), use);
			for (Rune rune : combo.getValue())
			{
				if (shortfall.containsKey(rune))
				{
					final int covered = Math.min(use, shortfall.get(rune));
					shortfall.merge(rune, -covered, Integer::sum);
					result.have.merge(rune, covered, Integer::sum);
				}
			}
		}
		return result;
	}
	private static boolean inPouch(Holdings holdings, Rune rune)
	{
		return holdings.getRunePouch().getOrDefault(rune.getItemId(), 0) > 0;
	}

	private static int covers(Set<Rune> runes, Map<Rune, Integer> shortfall)
	{
		int total = 0;
		for (Rune rune : runes)
		{
			total += shortfall.getOrDefault(rune, 0);
		}
		return total;
	}

	private static void addRunes(Map<Rune, Integer> need, Spell spell)
	{
		for (RuneAmount amount : spell.getRunes())
		{
			need.merge(amount.getRune(), amount.getQuantity(), Integer::sum);
		}
	}

	private static boolean hasPatchAt(List<Patch> patches, Location location)
	{
		for (Patch patch : patches)
		{
			if (patch.getLocation() == location)
			{
				return true;
			}
		}
		return false;
	}

	private static SupplyLine line(SupplyLine.Group group, String name, int need, Holdings holdings, Holdings carried,
		String note, int slots, int... itemIds)
	{
		return new SupplyLine(group, name, need, holdings.countAny(itemIds), carried.countAny(itemIds),
			holdings.where(itemIds), note, slots, false, defaultChangeIn(group), itemIds.clone());
	}

	private static String defaultChangeIn(SupplyLine.Group group)
	{
		switch (group)
		{
			case TRAVEL:
				return TRAVEL;
			case SEEDS:
				return CROPS;
			case PAYMENTS:
				return PROTECTION;
			case OPTIONAL:
				return RUN_OPTIONS;
			default:
				return null;
		}
	}

	private static SupplyLine covered(SupplyLine line)
	{
		return new SupplyLine(line.getGroup(), line.getName(), line.getNeed(), line.getHave(), line.getCarried(),
			line.getWhere(), line.getNote(), line.getSlots(), true, line.getChangeIn(), line.getItemIds());
	}

	private static SupplyLine changeIn(SupplyLine line, String where)
	{
		return new SupplyLine(line.getGroup(), line.getName(), line.getNeed(), line.getHave(), line.getCarried(),
			line.getWhere(), line.getNote(), line.getSlots(), line.isCoveredOtherwise(), where, line.getItemIds());
	}
	private static String title(String enumName)
	{
		final String lower = enumName.toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
