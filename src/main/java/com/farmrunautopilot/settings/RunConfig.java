package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.Data;

/**
 * Everything the player chooses for a run (SPEC 5 RunConfig). Saved as JSON per account; presets
 * are named copies of it. Fields are mutable so Gson can fill them; call {@link #sanitise()}
 * after loading.
 */
@Data
public class RunConfig
{
	public static final int DEFAULT_DUE_THRESHOLD = 100;
	/** 2nd and 3rd choice. */
	public static final int MAX_BACKUP_CROPS = 2;

	// Run types
	/** New players start with trees, fruit trees and herbs; the types added later are ticked by the player (elzerma). */
	private Set<PatchType> enabledTypes = originalTypes();
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
	/**
	 * Use {@link #protectionOverrides}; when off they're kept but ignored (elzerma: hidden behind a tick box). Null in
	 * settings saved before it existed: on if there are any overrides, so nothing changes for them.
	 */
	private Boolean useProtectionOverrides;
	/** Bring gardener payments noted (one slot each); confirmed preferable in-game. */
	private boolean payWithNotes = true;
	/** Run types where the gardener is paid 200 coins to clear a grown tree instead of chopping it. */
	private Set<PatchType> payToClear = EnumSet.of(PatchType.TREE, PatchType.FRUIT_TREE);
	private Map<PatchType, Compost> compost = defaultCompost();
	private int plantCureDoses = 0;
	private boolean useCurePlant = false;
	/** Suggest magic secateurs (+10% herb yield) as an optional item. */
	private boolean recommendEquipmentBoosts = true;

	// Travel. Every stop's way is planned from the travel atlas; nothing the player picks overrules it (elzerma,
	// 2026-10-20: the Travel tab's per-location list went, and old saves' picks with it)
	/** Cast teleports from runes at every stop, even when a tablet is held (tablets are used first otherwise). */
	private boolean useRunesNotTabs = false;
	/**
	 * Saves from before 2026-10-20 chose runes per stop: any stop turns {@link #useRunesNotTabs} on. Read only, then
	 * cleared on load.
	 */
	private Set<Location> runesNotTabsAt;
	/** Hold back the last charge of rechargeable jewellery (skills necklace, glory, ring of wealth, ...). */
	private boolean keepLastCharge = false;
	/** How each stop's way is chosen when ways are close (Travel style). */
	private TravelStyle travelStyle = TravelStyle.FASTEST;
	/**
	 * The teleport a guided test needs at a stop (testing/GuidedTests), cast or used directly whenever it can be. Never
	 * shown or set by the player: the settings put back after the test have none.
	 */
	private Map<Location, TravelMethod> testTravel = new EnumMap<>(Location.class);
	/**
	 * The Travel tab's families the player turned on or off (sprint T7); one not here has its default. Like the single
	 * ways and the Wilderness, kept for the account when a preset is applied (elzerma, 2026-10-20).
	 */
	private Map<TravelFamily, Boolean> travelFamilies = new EnumMap<>(TravelFamily.class);
	/** Single ways the player turned off ("don't use this way"), by the atlas's key, e.g. "TELEPORTATION_ITEM|Ectophial". */
	private Set<String> waysOff = new TreeSet<>();
	/** Keep the route out of the Wilderness. */
	private boolean avoidWilderness = true;
	/**
	 * Cast a spell from another spellbook after Spellbook Swap or a trip to the house altar, when that's quicker
	 * (elzerma, 2026-10-20: a tick box beside Avoid the Wilderness, off to start with). Kept for the account like the ways.
	 */
	private boolean switchSpellbooks = false;

	// Route
	private RouteMode routeMode = RouteMode.AUTOPILOT;
	private List<Location> customOrder = new ArrayList<>();
	/** Where the run starts, or null for anywhere: the quickest first stop (or the stop the player stands at). */
	private Location startLocation;
	/** Null in saves from before "Anywhere" was the default: the old default (Farming Guild) becomes Anywhere. */
	private Boolean startAnywhereByDefault;
	private boolean endNearBank = true;
	private Outfit outfit = Outfit.NONE;
	/** During a run, remind the player to drop weeds and empty plant pots. */
	private boolean remindToDrop = true;
	/** Walk instead of teleporting when the walk is only a little slower (saves charges and clicks). */
	private boolean preferWalking = true;

	// Run tab
	/** Supplies for every selected patch instead of only the due ones. */
	private boolean supplyFullRun = false;

	// Storage sources: counted by default, so items in them "just work" (each is only read once it has been
	// opened: group storage only exists for group ironmen)
	private boolean useGroupStorage = true;
	private boolean useSeedVault = true;
	/** Null in saves from before storage was on by default: switches both on once. */
	private Boolean storageOnByDefault;
	/** How a run that banks partway gets the seeds kept in the seed vault (issue #8). */
	private VaultBanking vaultBanking = VaultBanking.TAKE_ALL_AT_START;

	/**
	 * Fills in anything Gson left null (new fields, or enum names that no longer exist) and clamps
	 * numbers to sensible ranges.
	 */
	public RunConfig sanitise()
	{
		if (useProtectionOverrides == null)
		{
			useProtectionOverrides = protectionOverrides != null && !protectionOverrides.isEmpty();
		}
		enabledTypes = cleanSet(enabledTypes, PatchType.class, originalTypes());
		disabledPatches = cleanSet(disabledPatches, Patch.class, EnumSet.noneOf(Patch.class));
		payToClear = cleanSet(payToClear, PatchType.class, EnumSet.noneOf(PatchType.class));
		if (runesNotTabsAt != null)
		{
			// Runes per stop went with the Locations list: runes at any stop means runes at every stop
			useRunesNotTabs |= !cleanSet(runesNotTabsAt, Location.class, EnumSet.noneOf(Location.class)).isEmpty();
			runesNotTabsAt = null;
		}
		if (storageOnByDefault == null)
		{
			// They used to default off and most players never found the setting
			useGroupStorage = true;
			useSeedVault = true;
			storageOnByDefault = true;
		}
		if (travelStyle == null)
		{
			travelStyle = TravelStyle.FASTEST;
		}
		if (vaultBanking == null)
		{
			vaultBanking = VaultBanking.TAKE_ALL_AT_START;
		}

		crops = cleanMap(crops, PatchType.class);
		crops.entrySet().removeIf(e -> e.getValue().getType() != e.getKey());
		backupCrops = cleanMap(backupCrops, PatchType.class);
		backupCrops.replaceAll((type, list) -> cleanBackups(type, list));
		diseaseFreeHerbs = cleanSet(diseaseFreeHerbs, Crop.class, EnumSet.noneOf(Crop.class));
		diseaseFreeHerbs.removeIf(c -> c.getType() != PatchType.HERB);
		protectionOverrides = cleanMap(protectionOverrides, Patch.class);
		testTravel = cleanMap(testTravel, Location.class);
		testTravel.entrySet().removeIf(e -> e.getValue().getDestination() != e.getKey());
		travelFamilies = cleanMap(travelFamilies, TravelFamily.class);
		final Set<String> off = new TreeSet<>();
		if (waysOff != null)
		{
			for (String key : waysOff)
			{
				if (key != null && !key.trim().isEmpty())
				{
					off.add(key);
				}
			}
		}
		waysOff = off;

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
		if (startAnywhereByDefault == null)
		{
			// The Farming Guild was the default, not a choice most players made
			if (startLocation == Location.FARMING_GUILD)
			{
				startLocation = null;
			}
			startAnywhereByDefault = true;
		}

		dueThresholdPercent = clamp(dueThresholdPercent, 1, 100);
		plantCureDoses = clamp(plantCureDoses, 0, 40);
		return this;
	}

	public boolean isPatchSelected(Patch patch)
	{
		return enabledTypes.contains(patch.getType()) && !disabledPatches.contains(patch);
	}

	/** A Travel tab family is on: as the player set it, or its default. */
	public boolean isOn(TravelFamily family)
	{
		return travelFamilies.getOrDefault(family, family.isOnByDefault());
	}

	public void setOn(TravelFamily family, boolean on)
	{
		travelFamilies.put(family, on);
	}

	/** The families that are off, for the planner (AtlasWays). */
	public Set<TravelFamily> familiesOff()
	{
		final Set<TravelFamily> off = EnumSet.noneOf(TravelFamily.class);
		for (TravelFamily family : TravelFamily.values())
		{
			if (!isOn(family))
			{
				off.add(family);
			}
		}
		return off;
	}

	/** Per-patch protection is switched on. */
	public boolean isUsingProtectionOverrides()
	{
		return Boolean.TRUE.equals(useProtectionOverrides);
	}

	/**
	 * The patch's own choice, else its type's. Allotments are chosen per set (Farm > Protection > Each set), which always
	 * counts; other patches' own choices only while per-patch protection is switched on.
	 */
	public Protection protectionFor(Patch patch)
	{
		final Protection override = isUsingProtectionOverrides() || patch.getType() == PatchType.ALLOTMENT
			? protectionOverrides.get(patch) : null;
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
			if (crop.getType() == type && crop.isChoosable() && (best == null || crop.getFarmingLevel() <= farmingLevel))
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

	private static Map<PatchType, Protection> defaultProtection()
	{
		final Map<PatchType, Protection> map = new EnumMap<>(PatchType.class);
		map.put(PatchType.TREE, Protection.PAY_GARDENER);
		map.put(PatchType.FRUIT_TREE, Protection.PAY_GARDENER);
		// elzerma: allotments are protected by a flower in their set by default, changeable per set
		map.put(PatchType.ALLOTMENT, Protection.FLOWER);
		map.put(PatchType.HOPS, Protection.COMPOST_ONLY);
		return map;
	}

	private static Map<PatchType, Compost> defaultCompost()
	{
		final Map<PatchType, Compost> map = new EnumMap<>(PatchType.class);
		map.put(PatchType.TREE, Compost.NONE);
		map.put(PatchType.FRUIT_TREE, Compost.NONE);
		map.put(PatchType.HERB, Compost.ULTRACOMPOST);
		map.put(PatchType.ALLOTMENT, Compost.ULTRACOMPOST);
		map.put(PatchType.FLOWER, Compost.NONE);
		map.put(PatchType.HOPS, Compost.ULTRACOMPOST);
		return map;
	}

	private static Set<PatchType> originalTypes()
	{
		final Set<PatchType> types = EnumSet.noneOf(PatchType.class);
		for (PatchType type : PatchType.values())
		{
			if (PatchType.isOriginal(type))
			{
				types.add(type);
			}
		}
		return types;
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
