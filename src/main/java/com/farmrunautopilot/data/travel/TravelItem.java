package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Requirement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Teleport items. Each lists every item ID that can do the teleport (charge variants, imbued and
 * trimmed versions); uncharged versions are left out.
 *
 * <p>Requirements are what it takes to use the item. Most are also needed to get the item, so they mostly
 * matter before the supply calculator (M4) checks what the player owns.
 */
@Getter
public enum TravelItem
{
	RING_OF_WEALTH("Ring of wealth",
		ItemID.RING_OF_WEALTH_1, ItemID.RING_OF_WEALTH_2, ItemID.RING_OF_WEALTH_3, ItemID.RING_OF_WEALTH_4,
		ItemID.RING_OF_WEALTH_5, ItemID.RING_OF_WEALTH_I1, ItemID.RING_OF_WEALTH_I2, ItemID.RING_OF_WEALTH_I3,
		ItemID.RING_OF_WEALTH_I4, ItemID.RING_OF_WEALTH_I5),
	SKILLS_NECKLACE("Skills necklace",
		ItemID.JEWL_NECKLACE_OF_SKILLS_1, ItemID.JEWL_NECKLACE_OF_SKILLS_2, ItemID.JEWL_NECKLACE_OF_SKILLS_3,
		ItemID.JEWL_NECKLACE_OF_SKILLS_4, ItemID.JEWL_NECKLACE_OF_SKILLS_5, ItemID.JEWL_NECKLACE_OF_SKILLS_6),
	GAMES_NECKLACE("Games necklace",
		ItemID.NECKLACE_OF_MINIGAMES_1, ItemID.NECKLACE_OF_MINIGAMES_2, ItemID.NECKLACE_OF_MINIGAMES_3,
		ItemID.NECKLACE_OF_MINIGAMES_4, ItemID.NECKLACE_OF_MINIGAMES_5, ItemID.NECKLACE_OF_MINIGAMES_6,
		ItemID.NECKLACE_OF_MINIGAMES_7, ItemID.NECKLACE_OF_MINIGAMES_8),
	COMBAT_BRACELET("Combat bracelet",
		ItemID.JEWL_BRACELET_OF_COMBAT_1, ItemID.JEWL_BRACELET_OF_COMBAT_2, ItemID.JEWL_BRACELET_OF_COMBAT_3,
		ItemID.JEWL_BRACELET_OF_COMBAT_4, ItemID.JEWL_BRACELET_OF_COMBAT_5, ItemID.JEWL_BRACELET_OF_COMBAT_6),
	AMULET_OF_GLORY("Amulet of glory",
		ItemID.AMULET_OF_GLORY_1, ItemID.AMULET_OF_GLORY_2, ItemID.AMULET_OF_GLORY_3, ItemID.AMULET_OF_GLORY_4,
		ItemID.AMULET_OF_GLORY_5, ItemID.AMULET_OF_GLORY_6, ItemID.AMULET_OF_GLORY_INF),
	SLAYER_RING("Slayer ring",
		ItemID.SLAYER_RING_1, ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_3, ItemID.SLAYER_RING_4, ItemID.SLAYER_RING_5,
		ItemID.SLAYER_RING_6, ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_8, ItemID.SLAYER_RING_ETERNAL),
	EXPLORERS_RING("Explorer's ring 2/3/4",
		needs(Requirement.diary(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.MEDIUM)),
		ItemID.LUMBRIDGE_RING_MEDIUM, ItemID.LUMBRIDGE_RING_HARD, ItemID.LUMBRIDGE_RING_ELITE),
	ARDOUGNE_CLOAK("Ardougne cloak 2/3/4",
		needs(Requirement.diary(AchievementDiary.ARDOUGNE, AchievementDiary.Tier.MEDIUM)),
		ItemID.ARDY_CAPE_MEDIUM, ItemID.ARDY_CAPE_HARD, ItemID.ARDY_CAPE_ELITE),
	/** Teleports to Kandarin Monastery only; the higher cloaks above also reach the farm. */
	ARDOUGNE_CLOAK_1("Ardougne cloak 1",
		needs(Requirement.diary(AchievementDiary.ARDOUGNE, AchievementDiary.Tier.EASY)), ItemID.ARDY_CAPE_EASY),
	QUEST_POINT_CAPE("Quest point cape", ItemID.SKILLCAPE_QP, ItemID.SKILLCAPE_QP_TRIMMED),
	RADAS_BLESSING("Rada's blessing 3/4",
		needs(Requirement.diary(AchievementDiary.KOUREND_KEBOS, AchievementDiary.Tier.HARD)),
		ItemID.ZEAH_BLESSING_HARD, ItemID.ZEAH_BLESSING_ELITE),
	XERICS_TALISMAN("Xeric's talisman", ItemID.XERIC_TALISMAN),
	KHAREDSTS_MEMOIRS("Kharedst's memoirs / Book of the dead", needs(Requirement.quest(Quest.CLIENT_OF_KOUREND)),
		ItemID.VEOS_KHAREDSTS_MEMOIRS, ItemID.BOOK_OF_THE_DEAD),
	ECTOPHIAL("Ectophial", needs(Requirement.quest(Quest.GHOSTS_AHOY)), ItemID.ECTOPHIAL),
	STONY_BASALT("Stony basalt", needs(Requirement.quest(Quest.MAKING_FRIENDS_WITH_MY_ARM)), ItemID.STRONGHOLD_TELEPORT_BASALT),
	ICY_BASALT("Icy basalt", needs(Requirement.quest(Quest.MAKING_FRIENDS_WITH_MY_ARM)), ItemID.WEISS_TELEPORT_BASALT),
	ROYAL_SEED_POD("Royal seed pod", needs(Requirement.quest(Quest.MONKEY_MADNESS_II)), ItemID.MM2_ROYAL_SEED_POD),
	// UNVERIFIED: PRIF_TELEPORT_CRYSTAL is assumed to be the eternal teleport crystal.
	TELEPORT_CRYSTAL("Teleport crystal", needs(Requirement.questStarted(Quest.MOURNINGS_END_PART_I)),
		ItemID.MOURNING_TELEPORT_CRYSTAL_1, ItemID.MOURNING_TELEPORT_CRYSTAL_2, ItemID.MOURNING_TELEPORT_CRYSTAL_3,
		ItemID.MOURNING_TELEPORT_CRYSTAL_4, ItemID.MOURNING_TELEPORT_CRYSTAL_5, ItemID.PRIF_TELEPORT_CRYSTAL),
	QUETZAL_WHISTLE("Quetzal whistle", needs(Requirement.quest(Quest.CHILDREN_OF_THE_SUN)),
		ItemID.HG_QUETZALWHISTLE_BASIC, ItemID.HG_QUETZALWHISTLE_ENHANCED, ItemID.HG_QUETZALWHISTLE_PERFECTED,
		ItemID.HG_QUETZALWHISTLE_PERFECTED_INFINITE),
	PENDANT_OF_ATES("Pendant of Ates", needs(Requirement.quest(Quest.CHILDREN_OF_THE_SUN)), ItemID.PENDANT_OF_ATES),
	FARMING_CAPE("Farming cape", needs(Requirement.skill(Skill.FARMING, 99)), ItemID.SKILLCAPE_FARMING, ItemID.SKILLCAPE_FARMING_TRIMMED),
	HUNTER_CAPE("Hunter cape", needs(Requirement.skill(Skill.HUNTER, 99)), ItemID.SKILLCAPE_HUNTING, ItemID.SKILLCAPE_HUNTING_TRIMMED),
	CONSTRUCTION_CAPE("Construction cape", needs(Requirement.skill(Skill.CONSTRUCTION, 99)),
		ItemID.SKILLCAPE_CONSTRUCTION, ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED),
	MAX_CAPE("Max cape", needs(Requirement.allSkills(99)), ItemID.SKILLCAPE_MAX, ItemID.SKILLCAPE_MAX_WORN),
	ACHIEVEMENT_DIARY_CAPE("Achievement diary cape", needs(Requirement.allDiaries(AchievementDiary.Tier.ELITE)),
		ItemID.SKILLCAPE_AD, ItemID.SKILLCAPE_AD_TRIMMED),
	TAVERLEY_TABLET("Taverley teleport tablet", ItemID.NZONE_TELETAB_TAVERLEY),
	BRIMHAVEN_TABLET("Brimhaven teleport tablet", ItemID.NZONE_TELETAB_BRIMHAVEN),
	HOSIDIUS_TABLET("Hosidius teleport tablet", ItemID.NZONE_TELETAB_KOUREND);

	private final String displayName;
	private final int[] itemIds;
	private final List<Requirement> requirements;

	TravelItem(String displayName, int... itemIds)
	{
		this(displayName, new Requirement[0], itemIds);
	}

	TravelItem(String displayName, Requirement[] requirements, int... itemIds)
	{
		this.displayName = displayName;
		this.requirements = Collections.unmodifiableList(Arrays.asList(requirements));
		this.itemIds = itemIds;
	}

	private static Requirement[] needs(Requirement... requirements)
	{
		return requirements;
	}

	public int[] getItemIds()
	{
		return itemIds.clone();
	}
}
