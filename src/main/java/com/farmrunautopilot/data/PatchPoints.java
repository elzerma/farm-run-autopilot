package com.farmrunautopilot.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Where each patch is on the map, used for the hint arrow until the patch object loads, and to recognise it.
 *
 * <p>Coordinates are from Farming-Helper (BSD-2, "Copyright (c) 2025, JThomasDevs", see THIRD_PARTY_NOTICES).
 * Each one is checked against the patch's RuneLite region by a unit test. All but Weiss and Harmony Island
 * were corrected to the patch objects' positions logged in game (October 2026).
 */
public final class PatchPoints
{
	private static final Map<Patch, WorldPoint> POINTS = new EnumMap<>(Patch.class);

	static
	{
		POINTS.put(Patch.LUMBRIDGE_TREE, new WorldPoint(3193, 3231, 0));
		POINTS.put(Patch.VARROCK_TREE, new WorldPoint(3229, 3459, 0));
		POINTS.put(Patch.FALADOR_TREE, new WorldPoint(3004, 3373, 0));
		POINTS.put(Patch.TAVERLEY_TREE, new WorldPoint(2936, 3438, 0));
		POINTS.put(Patch.GNOME_STRONGHOLD_TREE, new WorldPoint(2436, 3415, 0));
		POINTS.put(Patch.FARMING_GUILD_TREE, new WorldPoint(1232, 3736, 0));
		POINTS.put(Patch.NEMUS_RETREAT_TREE, new WorldPoint(1366, 3321, 0));

		POINTS.put(Patch.GNOME_STRONGHOLD_FRUIT_TREE, new WorldPoint(2475, 3445, 0));
		POINTS.put(Patch.TREE_GNOME_VILLAGE_FRUIT_TREE, new WorldPoint(2489, 3179, 0));
		POINTS.put(Patch.CATHERBY_FRUIT_TREE, new WorldPoint(2860, 3433, 0));
		POINTS.put(Patch.BRIMHAVEN_FRUIT_TREE, new WorldPoint(2764, 3212, 0));
		POINTS.put(Patch.LLETYA_FRUIT_TREE, new WorldPoint(2346, 3161, 0));
		POINTS.put(Patch.FARMING_GUILD_FRUIT_TREE, new WorldPoint(1242, 3758, 0));
		POINTS.put(Patch.KASTORI_FRUIT_TREE, new WorldPoint(1349, 3056, 0));

		POINTS.put(Patch.FALADOR_HERB, new WorldPoint(3058, 3311, 0));
		POINTS.put(Patch.PORT_PHASMATYS_HERB, new WorldPoint(3605, 3529, 0));
		POINTS.put(Patch.CATHERBY_HERB, new WorldPoint(2813, 3463, 0));
		POINTS.put(Patch.ARDOUGNE_HERB, new WorldPoint(2670, 3374, 0));
		POINTS.put(Patch.HOSIDIUS_HERB, new WorldPoint(1738, 3550, 0));
		POINTS.put(Patch.TROLL_STRONGHOLD_HERB, new WorldPoint(2826, 3694, 0));
		POINTS.put(Patch.HARMONY_ISLAND_HERB, new WorldPoint(3789, 2837, 0));
		POINTS.put(Patch.WEISS_HERB, new WorldPoint(2847, 3931, 0));
		POINTS.put(Patch.FARMING_GUILD_HERB, new WorldPoint(1238, 3726, 0));
		POINTS.put(Patch.CIVITAS_HERB, new WorldPoint(1581, 3094, 0));

		// Allotment sets and hops: the centre of each set on the wiki's patch maps (Allotment patch/Patches, Flower patch,
		// Hops patch/Patches). UNVERIFIED: not yet logged in game (guided test NEW_PATCH_POINTS); patch objects are told
		// apart by varbit, so a few tiles off is fine.
		set(new WorldPoint(3055, 3308, 0), Patch.FALADOR_ALLOTMENT_NW, Patch.FALADOR_ALLOTMENT_SE, Patch.FALADOR_FLOWER);
		set(new WorldPoint(2810, 3464, 0), Patch.CATHERBY_ALLOTMENT_N, Patch.CATHERBY_ALLOTMENT_S, Patch.CATHERBY_FLOWER);
		set(new WorldPoint(2667, 3375, 0), Patch.ARDOUGNE_ALLOTMENT_N, Patch.ARDOUGNE_ALLOTMENT_S, Patch.ARDOUGNE_FLOWER);
		set(new WorldPoint(3602, 3526, 0), Patch.PORT_PHASMATYS_ALLOTMENT_NW, Patch.PORT_PHASMATYS_ALLOTMENT_SE,
			Patch.PORT_PHASMATYS_FLOWER);
		set(new WorldPoint(1735, 3555, 0), Patch.HOSIDIUS_ALLOTMENT_NE, Patch.HOSIDIUS_ALLOTMENT_SW, Patch.HOSIDIUS_FLOWER);
		set(new WorldPoint(1266, 3730, 0), Patch.FARMING_GUILD_ALLOTMENT_N, Patch.FARMING_GUILD_ALLOTMENT_S,
			Patch.FARMING_GUILD_FLOWER);
		set(new WorldPoint(1586, 3099, 0), Patch.CIVITAS_ALLOTMENT_N, Patch.CIVITAS_ALLOTMENT_S, Patch.CIVITAS_FLOWER);
		set(new WorldPoint(3291, 6100, 0), Patch.PRIFDDINAS_ALLOTMENT_N, Patch.PRIFDDINAS_ALLOTMENT_S,
			Patch.PRIFDDINAS_FLOWER);
		set(new WorldPoint(3794, 2836, 0), Patch.HARMONY_ISLAND_ALLOTMENT);
		set(new WorldPoint(1353, 3023, 0), Patch.KASTORI_FLOWER);
		set(new WorldPoint(3229, 3315, 0), Patch.LUMBRIDGE_HOPS);
		set(new WorldPoint(2667, 3526, 0), Patch.SEERS_VILLAGE_HOPS);
		set(new WorldPoint(2576, 3105, 0), Patch.YANILLE_HOPS);
		set(new WorldPoint(1365, 2939, 0), Patch.ALDARIN_HOPS);
		// Sprint 1b: from the travel atlas's patch places (Farming-Helper's points), to correct from the game log
		set(new WorldPoint(3181, 3357, 0), Patch.CHAMPIONS_GUILD_BUSH);
		set(new WorldPoint(2940, 3221, 0), Patch.RIMMINGTON_BUSH);
		set(new WorldPoint(2591, 3863, 0), Patch.ETCETERIA_BUSH);
		set(new WorldPoint(2617, 3225, 0), Patch.ARDOUGNE_MONASTERY_BUSH);
		set(new WorldPoint(1260, 3733, 0), Patch.FARMING_GUILD_BUSH);
		set(new WorldPoint(3315, 3202, 0), Patch.AL_KHARID_CACTUS);
		set(new WorldPoint(1264, 3747, 0), Patch.FARMING_GUILD_CACTUS);
		set(new WorldPoint(3451, 3472, 0), Patch.CANIFIS_MUSHROOM);
		set(new WorldPoint(3086, 3354, 0), Patch.DRAYNOR_MANOR_BELLADONNA);
		set(new WorldPoint(1450, 3354, 0), Patch.AUBURNVALE_BELLADONNA);
		set(new WorldPoint(3733, 10273, 1), Patch.FOSSIL_ISLAND_SEAWEED_NORTH);
		set(new WorldPoint(3733, 10267, 1), Patch.FOSSIL_ISLAND_SEAWEED_SOUTH);
	}

	private static void set(WorldPoint point, Patch... patches)
	{
		for (Patch patch : patches)
		{
			POINTS.put(patch, point);
		}
	}

	private PatchPoints()
	{
	}

	public static WorldPoint of(Patch patch)
	{
		return POINTS.get(patch);
	}

	public static Map<Patch, WorldPoint> all()
	{
		return Collections.unmodifiableMap(POINTS);
	}
}
