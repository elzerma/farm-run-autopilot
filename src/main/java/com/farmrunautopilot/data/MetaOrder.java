package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.Location.ALDARIN_HOPS;
import static com.farmrunautopilot.data.Location.AL_KHARID;
import static com.farmrunautopilot.data.Location.ARDOUGNE_MONASTERY;
import static com.farmrunautopilot.data.Location.AUBURNVALE;
import static com.farmrunautopilot.data.Location.CANIFIS;
import static com.farmrunautopilot.data.Location.CHAMPIONS_GUILD;
import static com.farmrunautopilot.data.Location.DRAYNOR_MANOR;
import static com.farmrunautopilot.data.Location.ETCETERIA;
import static com.farmrunautopilot.data.Location.FOSSIL_ISLAND_UNDERWATER;
import static com.farmrunautopilot.data.Location.RIMMINGTON;
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
import static com.farmrunautopilot.data.Location.LUMBRIDGE_HOPS;
import static com.farmrunautopilot.data.Location.NEMUS_RETREAT;
import static com.farmrunautopilot.data.Location.PORT_PHASMATYS;
import static com.farmrunautopilot.data.Location.PRIFDDINAS;
import static com.farmrunautopilot.data.Location.SEERS_VILLAGE_HOPS;
import static com.farmrunautopilot.data.Location.TAVERLEY;
import static com.farmrunautopilot.data.Location.TREE_GNOME_VILLAGE;
import static com.farmrunautopilot.data.Location.TROLL_STRONGHOLD;
import static com.farmrunautopilot.data.Location.VARROCK;
import static com.farmrunautopilot.data.Location.WEISS;
import static com.farmrunautopilot.data.Location.YANILLE_HOPS;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

	// Allotments and flowers sit by the herb patches, so they follow the herb order; hops go round from Lumbridge
	private static final List<Location> ALLOTMENT = Collections.unmodifiableList(Arrays.asList(
		FALADOR_FARM, PORT_PHASMATYS, ARDOUGNE_FARM, CATHERBY, HOSIDIUS, FARMING_GUILD, CIVITAS_ILLA_FORTIS, HARMONY_ISLAND,
		PRIFDDINAS));

	private static final List<Location> FLOWER = Collections.unmodifiableList(Arrays.asList(
		FALADOR_FARM, PORT_PHASMATYS, ARDOUGNE_FARM, CATHERBY, HOSIDIUS, FARMING_GUILD, CIVITAS_ILLA_FORTIS, KASTORI,
		PRIFDDINAS));

	private static final List<Location> HOPS = Collections.unmodifiableList(Arrays.asList(
		LUMBRIDGE_HOPS, SEERS_VILLAGE_HOPS, YANILLE_HOPS, ALDARIN_HOPS));

	// Sprint 1b: the bush run from the guild round Asgarnia, Ardougne and Etceteria; the specials by type
	private static final List<Location> BUSH = Collections.unmodifiableList(Arrays.asList(
		FARMING_GUILD, CHAMPIONS_GUILD, RIMMINGTON, ARDOUGNE_MONASTERY, ETCETERIA));

	private static final List<Location> CACTUS = Collections.unmodifiableList(Arrays.asList(FARMING_GUILD, AL_KHARID));

	private static final List<Location> MUSHROOM = Collections.singletonList(CANIFIS);

	private static final List<Location> BELLADONNA = Collections.unmodifiableList(Arrays.asList(DRAYNOR_MANOR, AUBURNVALE));

	private static final List<Location> SEAWEED = Collections.singletonList(FOSSIL_ISLAND_UNDERWATER);

	// Sprint 1c: the special trees by type
	private static final List<Location> HARDWOOD = Collections.unmodifiableList(Arrays.asList(Location.FOSSIL_ISLAND,
		Location.LOCUS_OASIS, Location.ANGLERS_RETREAT));

	private static final List<Location> CALQUAT = Collections.unmodifiableList(Arrays.asList(Location.TAI_BWO_WANNAI, KASTORI,
		Location.THE_GREAT_CONCH));

	private static final List<Location> GUILD_ONLY = Collections.singletonList(FARMING_GUILD);

	private static final List<Location> SPIRIT_TREE = Collections.unmodifiableList(Arrays.asList(FARMING_GUILD, HOSIDIUS,
		Location.PORT_SARIM, ETCETERIA, BRIMHAVEN));

	private static final List<Location> CRYSTAL_TREE = Collections.singletonList(PRIFDDINAS);

	private MetaOrder()
	{
	}

	/**
	 * The tree, fruit tree and herb orders merged into one: each location appears once, at its first
	 * position (SPEC 8.5).
	 */
	public static List<Location> combined()
	{
		final Set<Location> merged = new LinkedHashSet<>();
		for (PatchType type : PatchType.values())
		{
			merged.addAll(forType(type));
		}
		return Collections.unmodifiableList(new ArrayList<>(merged));
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
			case ALLOTMENT:
				return ALLOTMENT;
			case FLOWER:
				return FLOWER;
			case HOPS:
				return HOPS;
			case BUSH:
				return BUSH;
			case CACTUS:
				return CACTUS;
			case MUSHROOM:
				return MUSHROOM;
			case BELLADONNA:
				return BELLADONNA;
			case SEAWEED:
				return SEAWEED;
			case HARDWOOD:
				return HARDWOOD;
			case CALQUAT:
				return CALQUAT;
			case CELASTRUS:
			case REDWOOD:
				return GUILD_ONLY;
			case SPIRIT_TREE:
				return SPIRIT_TREE;
			case CRYSTAL_TREE:
				return CRYSTAL_TREE;
			default:
				throw new IllegalArgumentException("No meta order for " + type);
		}
	}
}
