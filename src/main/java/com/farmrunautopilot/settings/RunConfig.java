package com.farmrunautopilot.settings;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.ArrayList;
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
	public static final int DEFAULT_ENERGY_THRESHOLD = 30;
	public static final int DEFAULT_ENERGY_MIN_TILES = 15;

	// Run types
	private Set<PatchType> enabledTypes = EnumSet.allOf(PatchType.class);
	/** Include a run type when at least this percent of its patches are due (SPEC 10). */
	private int dueThresholdPercent = DEFAULT_DUE_THRESHOLD;

	// Patches and crops
	/** Patches the player unticked. A manual untick always wins over detection. */
	private Set<Patch> disabledPatches = EnumSet.noneOf(Patch.class);
	/** Chosen crop per run type; a missing entry means "highest my Farming level allows". */
	private Map<PatchType, Crop> crops = new EnumMap<>(PatchType.class);

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
	private boolean useRunesNotTabs = false;
	private Set<Location> runesNotTabsAt = EnumSet.noneOf(Location.class);

	// Route
	private RouteMode routeMode = RouteMode.AUTOPILOT;
	private List<Location> customOrder = new ArrayList<>();
	private Location startLocation = Location.FARMING_GUILD;
	private boolean endNearBank = true;
	private int energyThreshold = DEFAULT_ENERGY_THRESHOLD;
	private int energyMinTiles = DEFAULT_ENERGY_MIN_TILES;
	private int staminaDoses = 0;

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

		crops = cleanMap(crops, PatchType.class);
		crops.entrySet().removeIf(e -> e.getValue().getType() != e.getKey());
		protectionOverrides = cleanMap(protectionOverrides, Patch.class);
		travel = cleanMap(travel, Location.class);
		travel.entrySet().removeIf(e -> e.getValue().getDestination() != e.getKey());

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
		if (routeMode == null)
		{
			routeMode = RouteMode.AUTOPILOT;
		}
		if (startLocation == null)
		{
			startLocation = Location.FARMING_GUILD;
		}

		dueThresholdPercent = clamp(dueThresholdPercent, 1, 100);
		energyThreshold = clamp(energyThreshold, 0, 100);
		energyMinTiles = clamp(energyMinTiles, 0, 200);
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
