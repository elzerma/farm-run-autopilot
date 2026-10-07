package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.Location.ARDOUGNE_FARM;
import static com.farmrunautopilot.data.Location.BRIMHAVEN;
import static com.farmrunautopilot.data.Location.CATHERBY;
import static com.farmrunautopilot.data.Location.CIVITAS_ILLA_FORTIS;
import static com.farmrunautopilot.data.Location.FALADOR_FARM;
import static com.farmrunautopilot.data.Location.FALADOR_PARK;
import static com.farmrunautopilot.data.Location.FARMING_GUILD;
import static com.farmrunautopilot.data.Location.GNOME_STRONGHOLD;
import static com.farmrunautopilot.data.Location.HARMONY_ISLAND;
import static com.farmrunautopilot.data.Location.HOSIDIUS;
import static com.farmrunautopilot.data.Location.KASTORI;
import static com.farmrunautopilot.data.Location.LLETYA;
import static com.farmrunautopilot.data.Location.LUMBRIDGE;
import static com.farmrunautopilot.data.Location.NEMUS_RETREAT;
import static com.farmrunautopilot.data.Location.PORT_PHASMATYS;
import static com.farmrunautopilot.data.Location.TAVERLEY;
import static com.farmrunautopilot.data.Location.TREE_GNOME_VILLAGE;
import static com.farmrunautopilot.data.Location.TROLL_STRONGHOLD;
import static com.farmrunautopilot.data.Location.VARROCK;
import static com.farmrunautopilot.data.Location.WEISS;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The OSRS Wiki "Farming runs" orders for each run type (SPEC 8.5), used by Meta route mode.
 */
public final class MetaOrder
{
	private static final List<Location> TREE = Collections.unmodifiableList(Arrays.asList(
		LUMBRIDGE, VARROCK, FALADOR_PARK, TAVERLEY, GNOME_STRONGHOLD, FARMING_GUILD, NEMUS_RETREAT));

	private static final List<Location> FRUIT_TREE = Collections.unmodifiableList(Arrays.asList(
		GNOME_STRONGHOLD, TREE_GNOME_VILLAGE, CATHERBY, FARMING_GUILD, LLETYA, BRIMHAVEN, KASTORI));

	private static final List<Location> HERB = Collections.unmodifiableList(Arrays.asList(
		FALADOR_FARM, PORT_PHASMATYS, ARDOUGNE_FARM, CATHERBY, HOSIDIUS, FARMING_GUILD, CIVITAS_ILLA_FORTIS,
		TROLL_STRONGHOLD, WEISS, HARMONY_ISLAND));

	private MetaOrder()
	{
	}

	public static List<Location> forType(PatchType type)
	{
		switch (type)
		{
			case TREE:
				return TREE;
			case FRUIT_TREE:
				return FRUIT_TREE;
			case HERB:
				return HERB;
			default:
				throw new IllegalArgumentException("No meta order for " + type);
		}
	}
}
