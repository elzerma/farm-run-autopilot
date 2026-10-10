package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.FairyRingAccess;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * Everything the player chooses for a run (SPEC 5 RunConfig). Saved as JSON per account; milestone M9
 * turns it into named presets. Fields are mutable so Gson can fill them; call {@link #sanitise()}
 * after loading.
 */
@Data
public class RunConfig
{
	public static final int DEFAULT_DUE_THRESHOLD = 100;
	/** 2nd and 3rd choice. */
	public static final int MAX_BACKUP_CROPS = 2;

	// Run types
	private Set<PatchType> enabledTypes = EnumSet.allOf(PatchType.class);
	/** Include a run type when at least this percent of its patches are due (SPEC 10). */
	private int dueThresholdPercent = DEFAULT_DUE_THRESHOLD;

	// Patches and crops
	/** Patches the player unticked. A manual untick always wins over detection. */
	private Set<Patch> disabledPatches = EnumSet.noneOf(Patch.class);
	/** Chosen (1st choice) crop per run type; a missing entry means "highest my Farming level allows". */
	private Map<PatchType, Crop> crops = new EnumMap<>(PatchType.class);
	/** Plant 2nd/3rd choices when the player runs out of the 1st (changed from the spec's single choice). */
	private boolean useBackupCrops = false;
	/** 2nd and 3rd choice per run type, in order. */
	private Map<PatchType, List<Crop>> backupCrops = new EnumMap<>(PatchType.class);
	/** Use {@link #diseaseFreeHerbs}; when off the list is kept but ignored. */
	private boolean prioritiseDiseaseFreeHerbs = false;
	/**
	 * Herbs reserved for disease-free patches: those patches get these first (most valuable first) and other
	 * patches never get them.
	 */
	private Set<Crop> diseaseFreeHerbs = EnumSet.noneOf(Crop.class);

	// Protection
	private Map<PatchType, Protection> protection = defaultProtection();
	private Map<Patch, Protection> protectionOverrides = new EnumMap<>(Patch.class);
	/** Bring gardener payments noted (one slot each); confirmed preferable in-game. */
	private boolean payWithNotes = true;
	/** Run types where the gardener is paid 200 coins to clear a grown tree instead of chopping it. */
	private Set<PatchType> payToClear = EnumSet.of(PatchType.TREE, PatchType.FRUIT_TREE);
	private Map<PatchType, Compost> compost = defaultCompost();
	private int plantCureDoses = 0;
	private boolean useCurePlant = false;
	private boolean useResurrectCrops = false;
	/** Suggest magic secateurs and Farming cape/outfit as optional items. */
	private boolean recommendEquipmentBoosts = true;

	// Travel
	/** Chosen method per location; a missing entry means "Auto (best)". */
	private Map<Location, TravelMethod> travel = new EnumMap<>(Location.class);
	/** How the chosen method is used (e.g. through the house portal nexus); missing means any way. */
	private Map<Location, Departure> travelHow = new EnumMap<>(Location.class);
	/** Old "runes everywhere" setting; now moved into {@link #runesNotTabsAt} for every location on load. */
	private boolean useRunesNotTabs = false;
	/** Stops that cast from runes even when a tablet is held (tablets are used first otherwise). */
	private Set<Location> runesNotTabsAt = EnumSet.noneOf(Location.class);
	/** Hold back the last charge of rechargeable jewellery (skills necklace, glory, ring of wealth, ...). */
	private boolean keepLastCharge = false;
	/** How Auto (best) picks each stop's teleport. */
	private TravelStyle travelStyle = TravelStyle.FASTEST;
	/** Preferred way to get to a fairy ring; null means Auto (the fastest one the player has). */
	private FairyRingAccess fairyRingWay;

	// Route
	private RouteMode routeMode = RouteMode.AUTOPILOT;
	private List<Location> customOrder = new ArrayList<>();
	private Location startLocation = Location.FARMING_GUILD;
	private boolean endNearBank = true;
	private int staminaDoses = 0;
	private Outfit outfit = Outfit.NONE;
	/** During a run, remind the player to drop weeds and empty plant pots. */
	private boolean remindToDrop = true;
	/** Walk instead of teleporting when the walk is only a little slower (saves charges and clicks). */
	private boolean preferWalking = true;

	// Run tab
	/** Supplies for every selected patch instead of only the due ones. */
	private boolean supplyFullRun = false;

	// Storage sources (SPEC 2: both default off)
	private boolean useGroupStorage = false;
	private boolean useSeedVault = false;

	/**
	 * Fills in anything Gson left null (new fields, or enum names that no longer exist) and clamps
	 * numbers to sensible ranges.
	 */
	public RunConfig sanitise()
	{
		enabledTypes = cleanSet(enabledTypes, PatchType.class, EnumSet.allOf(PatchType.class));
		disabledPatches = cleanSet(disabledPatches, Patch.class, EnumSet.noneOf(Patch.class));
		payToClear = cleanSet(payToClear, PatchType.class, EnumSet.noneOf(PatchType.class));
		runesNotTabsAt = cleanSet(runesNotTabsAt, Location.class, EnumSet.noneOf(Location.class));
		if (travelStyle == null)
		{
			travelStyle = TravelStyle.FASTEST;
		}
		if (useRunesNotTabs)
		{
			// "Runes everywhere" became a per-stop choice: keep it on for every stop
			runesNotTabsAt.addAll(EnumSet.allOf(Location.class));
			useRunesNotTabs = false;
		}
		if (fairyRingWay == FairyRingAccess.NEARBY)
		{
			// The nearby ring has its own switch
			fairyRingWay = null;
		}

		crops = cleanMap(crops, PatchType.class);
		crops.entrySet().removeIf(e -> e.getValue().getType() != e.getKey());
		backupCrops = cleanMap(backupCrops, PatchType.class);
		backupCrops.replaceAll((type, list) -> cleanBackups(type, list));
		diseaseFreeHerbs = cleanSet(diseaseFreeHerbs, Crop.class, EnumSet.noneOf(Crop.class));
		diseaseFreeHerbs.removeIf(c -> c.getType() != PatchType.HERB);
		protectionOverrides = cleanMap(protectionOverrides, Patch.class);
		travel = cleanMap(travel, Location.class);
		travel.entrySet().removeIf(e -> e.getValue().getDestination() != e.getKey());
		travelHow = cleanMap(travelHow, Location.class);
		travelHow.keySet().removeIf(l -> !travel.containsKey(l));

		final Map<PatchType, Protection> protectionDefaults = defaultProtection();
		protection = cleanMap(protection, PatchType.class);
		protectionDefaults.forEach(protection::putIfAbsent);
		final Map<PatchType, Compost> compostDefaults = defaultCompost();
		compost = cleanMap(compost, PatchType.class);
		compostDefaults.forEach(compost::putIfAbsent);

		if (customOrder == null)
		{
			customOrder = new ArrayList<>();
		}
		customOrder.removeIf(l -> l == null);
		if (outfit == null)
		{
			outfit = Outfit.NONE;
		}
		if (routeMode == null)
		{
			routeMode = RouteMode.AUTOPILOT;
		}
		if (startLocation == null)
		{
			startLocation = Location.FARMING_GUILD;
		}

		dueThresholdPercent = clamp(dueThresholdPercent, 1, 100);
		staminaDoses = clamp(staminaDoses, 0, 40);
		plantCureDoses = clamp(plantCureDoses, 0, 40);
		return this;
	}

	public boolean isPatchSelected(Patch patch)
	{
		return enabledTypes.contains(patch.getType()) && !disabledPatches.contains(patch);
	}

	public Protection protectionFor(Patch patch)
	{
		final Protection override = protectionOverrides.get(patch);
		return override != null ? override : protection.get(patch.getType());
	}

	/**
	 * The chosen crop, or the highest one {@code farmingLevel} can plant (the lowest if none).
	 */
	public Crop cropFor(PatchType type, int farmingLevel)
	{
		final Crop chosen = crops.get(type);
		if (chosen != null)
		{
			return chosen;
		}
		Crop best = null;
		for (Crop crop : Crop.values())
		{
			if (crop.getType() == type && (best == null || crop.getFarmingLevel() <= farmingLevel))
			{
				best = crop;
			}
		}
		return best;
	}

	/**
	 * The crops to plant for a run type, best first: the 1st choice, then the backups the player can plant
	 * (only when backups are switched on).
	 */
	public List<Crop> cropChoices(PatchType type, int farmingLevel)
	{
		final List<Crop> choices = new ArrayList<>();
		choices.add(cropFor(type, farmingLevel));
		if (useBackupCrops)
		{
			for (Crop crop : backupCrops.getOrDefault(type, Collections.emptyList()))
			{
				if (crop.getFarmingLevel() <= farmingLevel && !choices.contains(crop))
				{
					choices.add(crop);
				}
			}
		}
		return choices;
	}

	public boolean useRunesAt(Location location)
	{
		return useRunesNotTabs || runesNotTabsAt.contains(location);
	}

	private static Map<PatchType, Protection> defaultProtection()
	{
		final Map<PatchType, Protection> map = new EnumMap<>(PatchType.class);
		map.put(PatchType.TREE, Protection.PAY_GARDENER);
		map.put(PatchType.FRUIT_TREE, Protection.PAY_GARDENER);
		return map;
	}

	private static Map<PatchType, Compost> defaultCompost()
	{
		final Map<PatchType, Compost> map = new EnumMap<>(PatchType.class);
		map.put(PatchType.TREE, Compost.NONE);
		map.put(PatchType.FRUIT_TREE, Compost.NONE);
		map.put(PatchType.HERB, Compost.ULTRACOMPOST);
		return map;
	}

	private static List<Crop> cleanBackups(PatchType type, List<Crop> list)
	{
		final List<Crop> clean = new ArrayList<>();
		for (Crop crop : list)
		{
			if (crop != null && crop.getType() == type && !clean.contains(crop) && clean.size() < MAX_BACKUP_CROPS)
			{
				clean.add(crop);
			}
		}
		return clean;
	}

	static <E extends Enum<E>> Set<E> cleanSet(Set<E> set, Class<E> type, Set<E> fallback)
	{
		if (set == null)
		{
			return fallback;
		}
		final Set<E> clean = EnumSet.noneOf(type);
		for (E e : set)
		{
			if (e != null)
			{
				clean.add(e);
			}
		}
		return clean;
	}

	static <K extends Enum<K>, V> Map<K, V> cleanMap(Map<K, V> map, Class<K> type)
	{
		final Map<K, V> clean = new EnumMap<>(type);
		if (map != null)
		{
			map.forEach((k, v) ->
			{
				if (k != null && v != null)
				{
					clean.put(k, v);
				}
			});
		}
		return clean;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
