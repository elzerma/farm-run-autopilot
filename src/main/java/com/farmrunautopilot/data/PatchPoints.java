package com.farmrunautopilot.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Where each patch is on the map, used for hint arrows and Shortest Path directions.
 *
 * <p>Coordinates are from Farming-Helper (BSD-2, "Copyright (c) 2025, JThomasDevs", see THIRD_PARTY_NOTICES).
 * Each one is checked against the patch's RuneLite region by a unit test. Ortus Farm (Civitas) and the Farming
 * Guild fruit tree were corrected from the patch objects' logged positions in game.
 */
public final class PatchPoints
{
	private static final Map<Patch, WorldPoint> POINTS = new EnumMap<>(Patch.class);

	static
	{
		POINTS.put(Patch.LUMBRIDGE_TREE, new WorldPoint(3193, 3231, 0));
		POINTS.put(Patch.VARROCK_TREE, new WorldPoint(3229, 3459, 0));
		POINTS.put(Patch.FALADOR_TREE, new WorldPoint(3000, 3373, 0));
		POINTS.put(Patch.TAVERLEY_TREE, new WorldPoint(2936, 3438, 0));
		POINTS.put(Patch.GNOME_STRONGHOLD_TREE, new WorldPoint(2436, 3415, 0));
		POINTS.put(Patch.FARMING_GUILD_TREE, new WorldPoint(1232, 3736, 0));
		POINTS.put(Patch.NEMUS_RETREAT_TREE, new WorldPoint(1366, 3321, 0));

		POINTS.put(Patch.GNOME_STRONGHOLD_FRUIT_TREE, new WorldPoint(2475, 3446, 0));
		POINTS.put(Patch.TREE_GNOME_VILLAGE_FRUIT_TREE, new WorldPoint(2490, 3180, 0));
		POINTS.put(Patch.CATHERBY_FRUIT_TREE, new WorldPoint(2860, 3433, 0));
		POINTS.put(Patch.BRIMHAVEN_FRUIT_TREE, new WorldPoint(2764, 3212, 0));
		POINTS.put(Patch.LLETYA_FRUIT_TREE, new WorldPoint(2346, 3162, 0));
		POINTS.put(Patch.FARMING_GUILD_FRUIT_TREE, new WorldPoint(1242, 3758, 0));
		POINTS.put(Patch.KASTORI_FRUIT_TREE, new WorldPoint(1350, 3057, 0));

		POINTS.put(Patch.FALADOR_HERB, new WorldPoint(3058, 3307, 0));
		POINTS.put(Patch.PORT_PHASMATYS_HERB, new WorldPoint(3601, 3525, 0));
		POINTS.put(Patch.CATHERBY_HERB, new WorldPoint(2813, 3463, 0));
		POINTS.put(Patch.ARDOUGNE_HERB, new WorldPoint(2670, 3374, 0));
		POINTS.put(Patch.HOSIDIUS_HERB, new WorldPoint(1738, 3550, 0));
		POINTS.put(Patch.TROLL_STRONGHOLD_HERB, new WorldPoint(2824, 3696, 0));
		POINTS.put(Patch.HARMONY_ISLAND_HERB, new WorldPoint(3789, 2837, 0));
		POINTS.put(Patch.WEISS_HERB, new WorldPoint(2847, 3931, 0));
		POINTS.put(Patch.FARMING_GUILD_HERB, new WorldPoint(1238, 3726, 0));
		POINTS.put(Patch.CIVITAS_HERB, new WorldPoint(1581, 3094, 0));
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
