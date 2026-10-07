package com.farmrunautopilot.data;

import static com.farmrunautopilot.data.DataConstants.NONE;
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
	HARMONY_ISLAND_HERB(Location.HARMONY_ISLAND, PatchType.HERB, 15148, regions(), VarbitID.FARMING_TRANSMIT_B,
		Requirement.diary(AchievementDiary.MORYTANIA, AchievementDiary.Tier.ELITE))
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
	};

	private final Location location;
	private final PatchType type;
	private final int regionId;
	private final int[] extraRegionIds;
	private final int varbitId;
	/** Protection gardener, or {@link DataConstants#NONE} for herbs. */
	private final int gardenerNpcId;
	private final String gardenerName;
	private final List<Requirement> requirements;

	Patch(Location location, PatchType type, int regionId, int[] extraRegionIds, int varbitId,
		int gardenerNpcId, String gardenerName, Requirement... requirements)
	{
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
		Requirement... requirements)
	{
		this(location, type, regionId, extraRegionIds, varbitId, NONE, null, requirements);
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
		return location.getDisplayName() + " " + type.getDisplayName().toLowerCase();
	}

	private static int[] regions(int... ids)
	{
		return ids;
	}

	/** Full 3x3 region area centred on the Farming Guild. */
	private static int[] farmingGuildRegions()
	{
		return new int[]{5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667};
	}
}
