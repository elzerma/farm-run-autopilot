package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.DataConstants.NONE;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;

/**
 * The v1 patch catalogue (SPEC 6). Region IDs, extra region IDs, varbits, gardener NPCs and the
 * Catherby / Falador bounds rules are copied from RuneLite core {@code timetracking/farming/FarmingWorld}
 * (BSD-2, see THIRD_PARTY_NOTICES).
 *
 * <p>The same {@code FARMING_TRANSMIT_x} varbits are reused in every region, so a varbit only means
 * something together with the region the player is standing in.
 */
@Getter
public enum Patch
{
	// Trees
	LUMBRIDGE_TREE(Location.LUMBRIDGE, PatchType.TREE, 12594, regions(12850), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_TREE_4, "Fayeth"),
	VARROCK_TREE(Location.VARROCK, PatchType.TREE, 12854, regions(12853), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_TREE_3_02, "Treznor"),
	FALADOR_TREE(Location.FALADOR_PARK, PatchType.TREE, 11828, regions(12084), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_TREE_2, "Heskel")
	{
		// Elite Falador diary: never diseased, and Heskel protects it for free.
		@Override
		public Requirement getDiseaseFreeRequirement()
		{
			return Requirement.diary(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE);
		}
	},
	TAVERLEY_TREE(Location.TAVERLEY, PatchType.TREE, 11573, regions(11829), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_TREE_1, "Alain"),
	GNOME_STRONGHOLD_TREE(Location.GNOME_STRONGHOLD, PatchType.TREE, 9781, regions(9782, 9526, 9525),
		VarbitID.FARMING_TRANSMIT_A, NpcID.FARMING_GARDENER_TREE_GNOME, "Prissy Scilla"),
	FARMING_GUILD_TREE(Location.FARMING_GUILD, PatchType.TREE, 4922, farmingGuildRegions(), VarbitID.FARMING_TRANSMIT_G,
		NpcID.FARMING_GARDENER_FARMGUILD_T2, "Rosie",
		Requirement.skill(Skill.FARMING, 65)),
	NEMUS_RETREAT_TREE(Location.NEMUS_RETREAT, PatchType.TREE, 5427, regions(5428, 5684), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_TREE_7, "Aub",
		Requirement.quest(Quest.CHILDREN_OF_THE_SUN)), // Children of the Sun gates all of Varlamore

	// Fruit trees
	GNOME_STRONGHOLD_FRUIT_TREE(Location.GNOME_STRONGHOLD, PatchType.FRUIT_TREE, 9781, regions(9782, 9526, 9525),
		VarbitID.FARMING_TRANSMIT_B, NpcID.FARMING_GARDENER_FRUIT_1, "Bolongo"),
	TREE_GNOME_VILLAGE_FRUIT_TREE(Location.TREE_GNOME_VILLAGE, PatchType.FRUIT_TREE, 9777, regions(10033),
		VarbitID.FARMING_TRANSMIT_A, NpcID.FARMING_GARDENER_FRUIT_2, "Gileth"),
	CATHERBY_FRUIT_TREE(Location.CATHERBY, PatchType.FRUIT_TREE, 11317, regions(), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_FRUIT_4, "Ellena")
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			// The fruit tree patch is always sent when upstairs in 11317
			return loc.getX() >= 2840 || loc.getY() < 3440 || loc.getPlane() == 1;
		}
	},
	BRIMHAVEN_FRUIT_TREE(Location.BRIMHAVEN, PatchType.FRUIT_TREE, 11058, regions(11057), VarbitID.FARMING_TRANSMIT_A,
		NpcID.GARTH, "Garth"),
	LLETYA_FRUIT_TREE(Location.LLETYA, PatchType.FRUIT_TREE, 9265, regions(11103), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_FRUIT_TREE_5, "Liliwen",
		Requirement.questStarted(Quest.MOURNINGS_END_PART_I)),
	FARMING_GUILD_FRUIT_TREE(Location.FARMING_GUILD, PatchType.FRUIT_TREE, 4922, farmingGuildRegions(),
		VarbitID.FARMING_TRANSMIT_K, NpcID.FARMING_GARDENER_FARMGUILD_T3, "Nikkie",
		Requirement.skill(Skill.FARMING, 85)),
	KASTORI_FRUIT_TREE(Location.KASTORI, PatchType.FRUIT_TREE, 5423, regions(5167, 5424), VarbitID.FARMING_TRANSMIT_B,
		NpcID.FARMING_GARDENER_FRUIT_7, "Ehecatl",
		Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),

	// Herbs: gardeners cannot protect herbs.
	FALADOR_HERB(Location.FALADOR_FARM, PatchType.HERB, 12083, regions(), VarbitID.FARMING_TRANSMIT_D)
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			// Not on region boundary due to Port Sarim Spirit Tree patch
			return loc.getY() >= 3272;
		}
	},
	PORT_PHASMATYS_HERB(Location.PORT_PHASMATYS, PatchType.HERB, 14391, regions(14390), VarbitID.FARMING_TRANSMIT_D,
		Requirement.quest(Quest.PRIEST_IN_PERIL)),
	CATHERBY_HERB(Location.CATHERBY, PatchType.HERB, 11062, regions(11061, 11318, 11317), VarbitID.FARMING_TRANSMIT_D)
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			if (loc.getX() >= 2816 && loc.getY() < 3456)
			{
				//Upstairs sends different varbits
				return loc.getX() < 2840 && loc.getY() >= 3440 && loc.getPlane() == 0;
			}
			return true;
		}
	},
	ARDOUGNE_HERB(Location.ARDOUGNE_FARM, PatchType.HERB, 10548, regions(), VarbitID.FARMING_TRANSMIT_D),
	HOSIDIUS_HERB(Location.HOSIDIUS, PatchType.HERB, 6967, regions(6711), VarbitID.FARMING_TRANSMIT_D)
	{
		@Override
		public Requirement getDiseaseFreeRequirement()
		{
			return Requirement.diary(AchievementDiary.KOUREND_KEBOS, AchievementDiary.Tier.EASY);
		}
	},
	TROLL_STRONGHOLD_HERB(Location.TROLL_STRONGHOLD, PatchType.HERB, 11321, regions(), VarbitID.FARMING_TRANSMIT_A,
		Requirement.quest(Quest.MY_ARMS_BIG_ADVENTURE))
	{
		// Watched over by an NPC rather than disease-free itself, with the same effect.
		@Override
		public boolean isAlwaysDiseaseFree()
		{
			return true;
		}
	},
	// The island itself needs The Great Brain Robbery started (wiki: Harmony Island; elzerma's account has the elite
	// diary, but not the island, 2026-10-19)
	HARMONY_ISLAND_HERB(Location.HARMONY_ISLAND, PatchType.HERB, 15148, regions(), VarbitID.FARMING_TRANSMIT_B,
		Requirement.diary(AchievementDiary.MORYTANIA, AchievementDiary.Tier.ELITE),
		Requirement.questStarted(Quest.THE_GREAT_BRAIN_ROBBERY))
	{
		@Override
		public boolean isAlwaysDiseaseFree()
		{
			return true;
		}
	},
	WEISS_HERB(Location.WEISS, PatchType.HERB, 11325, regions(), VarbitID.FARMING_TRANSMIT_A,
		Requirement.quest(Quest.MAKING_FRIENDS_WITH_MY_ARM), Requirement.unlock(Unlock.FIRE_OF_NOURISHMENT))
	{
		// Watched over by an NPC rather than disease-free itself, with the same effect.
		@Override
		public boolean isAlwaysDiseaseFree()
		{
			return true;
		}
	},
	FARMING_GUILD_HERB(Location.FARMING_GUILD, PatchType.HERB, 4922, farmingGuildRegions(), VarbitID.FARMING_TRANSMIT_E,
		Requirement.skill(Skill.FARMING, 65)),
	CIVITAS_HERB(Location.CIVITAS_ILLA_FORTIS, PatchType.HERB, 6192, regions(6447, 6448, 6449, 6191, 6193),
		VarbitID.FARMING_TRANSMIT_D,
		Requirement.quest(Quest.CHILDREN_OF_THE_SUN))
	{
		@Override
		public Requirement getDiseaseFreeRequirement()
		{
			return Requirement.unlock(Unlock.FORTIS_CHAMPION);
		}
	},

	// Allotments: two to a set, protected one at a time by the set's gardener, or by a grown flower in the set's flower
	// patch (docs/plans/crop-types.md). Region, varbit and gardener from RuneLite FarmingWorld; the part names are its.
	FALADOR_ALLOTMENT_NW("north-west", Location.FALADOR_FARM, PatchType.ALLOTMENT, 12083, regions(),
		VarbitID.FARMING_TRANSMIT_A, NpcID.ELSTAN, "Elstan")
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			return loc.getY() >= 3272;
		}
	},
	FALADOR_ALLOTMENT_SE("south-east", Location.FALADOR_FARM, PatchType.ALLOTMENT, 12083, regions(),
		VarbitID.FARMING_TRANSMIT_B, NpcID.ELSTAN, "Elstan")
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			return loc.getY() >= 3272;
		}
	},
	CATHERBY_ALLOTMENT_N("north", Location.CATHERBY, PatchType.ALLOTMENT, 11062, regions(11061, 11318, 11317),
		VarbitID.FARMING_TRANSMIT_A, NpcID.DANTAERA, "Dantaera")
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			return CATHERBY_HERB.isInBounds(loc);
		}
	},
	CATHERBY_ALLOTMENT_S("south", Location.CATHERBY, PatchType.ALLOTMENT, 11062, regions(11061, 11318, 11317),
		VarbitID.FARMING_TRANSMIT_B, NpcID.DANTAERA, "Dantaera")
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			return CATHERBY_HERB.isInBounds(loc);
		}
	},
	ARDOUGNE_ALLOTMENT_N("north", Location.ARDOUGNE_FARM, PatchType.ALLOTMENT, 10548, regions(),
		VarbitID.FARMING_TRANSMIT_A, NpcID.KRAGEN, "Kragen"),
	ARDOUGNE_ALLOTMENT_S("south", Location.ARDOUGNE_FARM, PatchType.ALLOTMENT, 10548, regions(),
		VarbitID.FARMING_TRANSMIT_B, NpcID.KRAGEN, "Kragen"),
	// The wiki lists no requirement; Morytania needs Priest in Peril as for the herb patch next to it
	PORT_PHASMATYS_ALLOTMENT_NW("north-west", Location.PORT_PHASMATYS, PatchType.ALLOTMENT, 14391, regions(14390),
		VarbitID.FARMING_TRANSMIT_A, NpcID.LYRA, "Lyra", Requirement.quest(Quest.PRIEST_IN_PERIL)),
	PORT_PHASMATYS_ALLOTMENT_SE("south-east", Location.PORT_PHASMATYS, PatchType.ALLOTMENT, 14391, regions(14390),
		VarbitID.FARMING_TRANSMIT_B, NpcID.LYRA, "Lyra", Requirement.quest(Quest.PRIEST_IN_PERIL)),
	HOSIDIUS_ALLOTMENT_NE("north-east", Location.HOSIDIUS, PatchType.ALLOTMENT, 6967, regions(6711),
		VarbitID.FARMING_TRANSMIT_A, NpcID.HOSIDIUS_ALLOTMENT_GARDENER, "Marisi")
	{
		@Override
		public Requirement getDiseaseFreeRequirement()
		{
			return HOSIDIUS_HERB.getDiseaseFreeRequirement();
		}
	},
	HOSIDIUS_ALLOTMENT_SW("south-west", Location.HOSIDIUS, PatchType.ALLOTMENT, 6967, regions(6711),
		VarbitID.FARMING_TRANSMIT_B, NpcID.HOSIDIUS_ALLOTMENT_GARDENER, "Marisi")
	{
		@Override
		public Requirement getDiseaseFreeRequirement()
		{
			return HOSIDIUS_HERB.getDiseaseFreeRequirement();
		}
	},
	FARMING_GUILD_ALLOTMENT_N("north", Location.FARMING_GUILD, PatchType.ALLOTMENT, 4922, farmingGuildRegions(),
		VarbitID.FARMING_TRANSMIT_C, NpcID.FARMING_GARDENER_FARMGUILD_T1, "Alan", Requirement.skill(Skill.FARMING, 45)),
	FARMING_GUILD_ALLOTMENT_S("south", Location.FARMING_GUILD, PatchType.ALLOTMENT, 4922, farmingGuildRegions(),
		VarbitID.FARMING_TRANSMIT_D, NpcID.FARMING_GARDENER_FARMGUILD_T1, "Alan", Requirement.skill(Skill.FARMING, 45)),
	CIVITAS_ALLOTMENT_N("north", Location.CIVITAS_ILLA_FORTIS, PatchType.ALLOTMENT, 6192,
		regions(6447, 6448, 6449, 6191, 6193), VarbitID.FARMING_TRANSMIT_A, NpcID.FORTIS_GARDENER, "Harminia",
		Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),
	CIVITAS_ALLOTMENT_S("south", Location.CIVITAS_ILLA_FORTIS, PatchType.ALLOTMENT, 6192,
		regions(6447, 6448, 6449, 6191, 6193), VarbitID.FARMING_TRANSMIT_B, NpcID.FORTIS_GARDENER, "Harminia",
		Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),
	PRIFDDINAS_ALLOTMENT_N("north", Location.PRIFDDINAS, PatchType.ALLOTMENT, 13151, prifddinasRegions(),
		VarbitID.FARMING_TRANSMIT_A, NpcID.PRIF_GARDENER, "Oswallt", Requirement.quest(Quest.SONG_OF_THE_ELVES)),
	PRIFDDINAS_ALLOTMENT_S("south", Location.PRIFDDINAS, PatchType.ALLOTMENT, 13151, prifddinasRegions(),
		VarbitID.FARMING_TRANSMIT_B, NpcID.PRIF_GARDENER, "Oswallt", Requirement.quest(Quest.SONG_OF_THE_ELVES)),
	// One allotment, no flower patch and no gardener: it can't be protected (wiki: Harmony Island)
	HARMONY_ISLAND_ALLOTMENT(Location.HARMONY_ISLAND, PatchType.ALLOTMENT, 15148, regions(), VarbitID.FARMING_TRANSMIT_A,
		Requirement.questStarted(Quest.THE_GREAT_BRAIN_ROBBERY)),

	// Flowers: no gardener protects them (wiki: Farming)
	FALADOR_FLOWER(Location.FALADOR_FARM, PatchType.FLOWER, 12083, regions(), VarbitID.FARMING_TRANSMIT_C)
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			return loc.getY() >= 3272;
		}
	},
	CATHERBY_FLOWER(Location.CATHERBY, PatchType.FLOWER, 11062, regions(11061, 11318, 11317), VarbitID.FARMING_TRANSMIT_C)
	{
		@Override
		public boolean isInBounds(WorldPoint loc)
		{
			return CATHERBY_HERB.isInBounds(loc);
		}
	},
	ARDOUGNE_FLOWER(Location.ARDOUGNE_FARM, PatchType.FLOWER, 10548, regions(), VarbitID.FARMING_TRANSMIT_C),
	PORT_PHASMATYS_FLOWER(Location.PORT_PHASMATYS, PatchType.FLOWER, 14391, regions(14390), VarbitID.FARMING_TRANSMIT_C,
		Requirement.quest(Quest.PRIEST_IN_PERIL)),
	HOSIDIUS_FLOWER(Location.HOSIDIUS, PatchType.FLOWER, 6967, regions(6711), VarbitID.FARMING_TRANSMIT_C)
	{
		@Override
		public Requirement getDiseaseFreeRequirement()
		{
			return HOSIDIUS_HERB.getDiseaseFreeRequirement();
		}
	},
	FARMING_GUILD_FLOWER(Location.FARMING_GUILD, PatchType.FLOWER, 4922, farmingGuildRegions(), VarbitID.FARMING_TRANSMIT_H,
		Requirement.skill(Skill.FARMING, 45)),
	CIVITAS_FLOWER(Location.CIVITAS_ILLA_FORTIS, PatchType.FLOWER, 6192, regions(6447, 6448, 6449, 6191, 6193),
		VarbitID.FARMING_TRANSMIT_C, Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),
	PRIFDDINAS_FLOWER(Location.PRIFDDINAS, PatchType.FLOWER, 13151, prifddinasRegions(), VarbitID.FARMING_TRANSMIT_C,
		Requirement.quest(Quest.SONG_OF_THE_ELVES)),
	// On its own, with no allotments to protect
	KASTORI_FLOWER(Location.KASTORI, PatchType.FLOWER, 5423, regions(5167, 5424), VarbitID.FARMING_TRANSMIT_C,
		Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),

	// Hops. Entrana is left out for now: its no-weapons rule may stop magic secateurs and worn teleports (UNVERIFIED).
	LUMBRIDGE_HOPS(Location.LUMBRIDGE_HOPS, PatchType.HOPS, 12851, regions(), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_HOPS_3, "Vasquen"),
	SEERS_VILLAGE_HOPS(Location.SEERS_VILLAGE_HOPS, PatchType.HOPS, 10551, regions(10550), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_HOPS_4, "Rhonen"),
	YANILLE_HOPS(Location.YANILLE_HOPS, PatchType.HOPS, 10288, regions(), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_HOPS_1, "Selena"),
	ALDARIN_HOPS(Location.ALDARIN_HOPS, PatchType.HOPS, 5421, regions(5165, 5166, 5422, 5677, 5678),
		VarbitID.FARMING_TRANSMIT_A, NpcID.FARMING_GARDENER_HOPS_5, "Ercos", Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),

	// Sprint 1b. Regions, varbits and gardeners from RuneLite FarmingWorld (master, 2026-10-24); names match the travel
	// atlas's places
	CHAMPIONS_GUILD_BUSH(Location.CHAMPIONS_GUILD, PatchType.BUSH, 12596, regions(), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_BUSH_1, "Dreven"),
	RIMMINGTON_BUSH(Location.RIMMINGTON, PatchType.BUSH, 11570, regions(11826), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_BUSH_2, "Taria"),
	// Reached by Rellekka's boat after The Fremennik Trials; whether Throne of Miscellania is also needed is UNVERIFIED
	ETCETERIA_BUSH(Location.ETCETERIA, PatchType.BUSH, 10300, regions(), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_BUSH_3, "Rhazien", Requirement.quest(Quest.THE_FREMENNIK_TRIALS)),
	ARDOUGNE_MONASTERY_BUSH(Location.ARDOUGNE_MONASTERY, PatchType.BUSH, 10290, regions(10546), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_BUSH_4, "Torrell"),
	FARMING_GUILD_BUSH(Location.FARMING_GUILD, PatchType.BUSH, 4922, farmingGuildRegions(), VarbitID.FARMING_TRANSMIT_B,
		NpcID.FARMING_GARDENER_FARMGUILD_T1, "Alan", Requirement.skill(Skill.FARMING, 45)),
	AL_KHARID_CACTUS(Location.AL_KHARID, PatchType.CACTUS, 13106, regions(13362, 13105), VarbitID.FARMING_TRANSMIT_A,
		NpcID.FARMING_GARDENER_CACTUS, "Ayesha"),
	FARMING_GUILD_CACTUS(Location.FARMING_GUILD, PatchType.CACTUS, 4922, farmingGuildRegions(),
		VarbitID.FARMING_TRANSMIT_F, NpcID.FARMING_GARDENER_FARMGUILD_T1, "Alan", Requirement.skill(Skill.FARMING, 45)),
	// No gardener: mushrooms and belladonna can't be protected
	CANIFIS_MUSHROOM(Location.CANIFIS, PatchType.MUSHROOM, 13622, regions(13878), VarbitID.FARMING_TRANSMIT_A,
		Requirement.quest(Quest.PRIEST_IN_PERIL)),
	DRAYNOR_MANOR_BELLADONNA(Location.DRAYNOR_MANOR, PatchType.BELLADONNA, 12340, regions(), VarbitID.FARMING_TRANSMIT_A),
	AUBURNVALE_BELLADONNA(Location.AUBURNVALE, PatchType.BELLADONNA, 5427, regions(5428, 5684), VarbitID.FARMING_TRANSMIT_B,
		Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),
	// Underwater off Fossil Island; Mernia protects each patch
	FOSSIL_ISLAND_SEAWEED_NORTH("north", Location.FOSSIL_ISLAND_UNDERWATER, PatchType.SEAWEED, 15008, regions(),
		VarbitID.FARMING_TRANSMIT_A, NpcID.FOSSIL_GARDENER_UNDERWATER, "Mernia", Requirement.quest(Quest.BONE_VOYAGE)),
	FOSSIL_ISLAND_SEAWEED_SOUTH("south", Location.FOSSIL_ISLAND_UNDERWATER, PatchType.SEAWEED, 15008, regions(),
		VarbitID.FARMING_TRANSMIT_B, NpcID.FOSSIL_GARDENER_UNDERWATER, "Mernia", Requirement.quest(Quest.BONE_VOYAGE));

	private final Location location;
	private final PatchType type;
	private final int regionId;
	private final int[] extraRegionIds;
	private final int varbitId;
	/** Protection gardener, or {@link DataConstants#NONE} for herbs. */
	private final int gardenerNpcId;
	private final String gardenerName;
	private final List<Requirement> requirements;
	/** Which one of two of a type at a stop ("north-west" allotment), or null. */
	private final String part;

	Patch(String part, Location location, PatchType type, int regionId, int[] extraRegionIds, int varbitId,
		int gardenerNpcId, String gardenerName, Requirement... requirements)
	{
		this.part = part;
		this.location = location;
		this.type = type;
		this.regionId = regionId;
		this.extraRegionIds = extraRegionIds;
		this.varbitId = varbitId;
		this.gardenerNpcId = gardenerNpcId;
		this.gardenerName = gardenerName;
		this.requirements = Collections.unmodifiableList(Arrays.asList(requirements));
	}

	Patch(Location location, PatchType type, int regionId, int[] extraRegionIds, int varbitId,
		int gardenerNpcId, String gardenerName, Requirement... requirements)
	{
		this(null, location, type, regionId, extraRegionIds, varbitId, gardenerNpcId, gardenerName, requirements);
	}

	Patch(Location location, PatchType type, int regionId, int[] extraRegionIds, int varbitId,
		Requirement... requirements)
	{
		this(null, location, type, regionId, extraRegionIds, varbitId, NONE, null, requirements);
	}

	public int[] getExtraRegionIds()
	{
		return extraRegionIds.clone();
	}

	/**
	 * Never gets diseased, no unlock needed.
	 */
	public boolean isAlwaysDiseaseFree()
	{
		return false;
	}

	/**
	 * Never gets diseased once this requirement is met, or null.
	 */
	public Requirement getDiseaseFreeRequirement()
	{
		return null;
	}

	public boolean hasGardener()
	{
		return gardenerNpcId != NONE;
	}

	/**
	 * Whether the game sends this patch's varbit at this position. Only called when the position is in
	 * {@link #getRegionId()} or one of {@link #getExtraRegionIds()}.
	 */
	public boolean isInBounds(WorldPoint loc)
	{
		return true;
	}

	public String getDisplayName()
	{
		return location.getDisplayName() + " " + (part != null ? part + " " : "") + type.getDisplayName().toLowerCase();
	}

	/** Where it is, for lists already grouped by type: "Falador farm (south)", or "... (north-west)" for one of two. */
	public String getPlaceName()
	{
		return location.getDisplayName() + (part != null ? " (" + part + ")" : "");
	}

	/** "NW" for a north-west allotment, or "" if it's the only one of its type at its stop. */
	public String getPartInitials()
	{
		if (part == null)
		{
			return "";
		}
		final StringBuilder initials = new StringBuilder();
		for (String word : part.split("-"))
		{
			initials.append(Character.toUpperCase(word.charAt(0)));
		}
		return initials.toString();
	}

	/** The other patches of the same set at this stop: the allotments and flower patch that share a gardener and flower. */
	public List<Patch> getSetMates()
	{
		final List<Patch> mates = new ArrayList<>();
		for (Patch patch : location.getPatches())
		{
			if (patch != this && (patch.type == PatchType.ALLOTMENT || patch.type == PatchType.FLOWER))
			{
				mates.add(patch);
			}
		}
		return mates;
	}

	private static int[] regions(int... ids)
	{
		return ids;
	}

	/** All of Prifddinas and its underground (RuneLite FarmingWorld). */
	private static int[] prifddinasRegions()
	{
		return new int[]{12895, 12894, 13150, 12994, 12993, 12737, 12738, 12126, 12127, 13250};
	}

	/** Full 3x3 region area centred on the Farming Guild. */
	private static int[] farmingGuildRegions()
	{
		return new int[]{5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667};
	}
}
