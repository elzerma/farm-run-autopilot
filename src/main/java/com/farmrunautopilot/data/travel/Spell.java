package com.farmrunautopilot.data.travel;

import static com.farmrunautopilot.data.DataConstants.NONE;
import static com.farmrunautopilot.data.travel.Rune.AIR;
import static com.farmrunautopilot.data.travel.Rune.ASTRAL;
import static com.farmrunautopilot.data.travel.Rune.COSMIC;
import static com.farmrunautopilot.data.travel.Rune.BLOOD;
import static com.farmrunautopilot.data.travel.Rune.EARTH;
import static com.farmrunautopilot.data.travel.Rune.FIRE;
import static com.farmrunautopilot.data.travel.Rune.LAW;
import static com.farmrunautopilot.data.travel.Rune.NATURE;
import static com.farmrunautopilot.data.travel.Rune.SOUL;
import static com.farmrunautopilot.data.travel.Rune.WATER;
import static com.farmrunautopilot.data.travel.RuneAmount.of;
import com.farmrunautopilot.data.Requirement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.gameval.ItemID;

/**
 * Spells used for travel and farming (SPEC 8.1 and 7.1), with the tablet that replaces the cast.
 */
@Getter
public enum Spell
{
	LUMBRIDGE_HOME_TELEPORT("Lumbridge Home Teleport", Spellbook.STANDARD, 1, NONE, null),
	VARROCK_TELEPORT("Varrock Teleport", Spellbook.STANDARD, 25, ItemID.POH_TABLET_VARROCKTELEPORT, null,
		of(3, AIR), of(1, FIRE), of(1, LAW)),
	LUMBRIDGE_TELEPORT("Lumbridge Teleport", Spellbook.STANDARD, 31, ItemID.POH_TABLET_LUMBRIDGETELEPORT, null,
		of(3, AIR), of(1, EARTH), of(1, LAW)),
	FALADOR_TELEPORT("Falador Teleport", Spellbook.STANDARD, 37, ItemID.POH_TABLET_FALADORTELEPORT, null,
		of(3, AIR), of(1, WATER), of(1, LAW)),
	TELEPORT_TO_HOUSE("Teleport to House", Spellbook.STANDARD, 40, ItemID.POH_TABLET_TELEPORTTOHOUSE, null,
		of(1, AIR), of(1, EARTH), of(1, LAW)),
	CAMELOT_TELEPORT("Camelot Teleport", Spellbook.STANDARD, 45, ItemID.POH_TABLET_CAMELOTTELEPORT, null,
		of(5, AIR), of(1, LAW)),
	ARDOUGNE_TELEPORT("Ardougne Teleport", Spellbook.STANDARD, 51, ItemID.POH_TABLET_ARDOUGNETELEPORT,
		Quest.PLAGUE_CITY,
		of(2, WATER), of(2, LAW)),
	CIVITAS_ILLA_FORTIS_TELEPORT("Civitas illa Fortis Teleport", Spellbook.STANDARD, 54, ItemID.POH_TABLET_FORTISTELEPORT,
		Quest.TWILIGHTS_PROMISE,
		of(1, EARTH), of(1, FIRE), of(2, LAW)),
	// The tablet is the redirected "Trollheim teleport" tablet. UNVERIFIED that it lands where the spell does.
	TROLLHEIM_TELEPORT("Trollheim Teleport", Spellbook.STANDARD, 61, ItemID.NZONE_TELETAB_TROLLHEIM, Quest.EADGARS_RUSE,
		of(2, FIRE), of(2, LAW)),

	DRAYNOR_MANOR_TELEPORT("Draynor Manor Teleport", Spellbook.ARCEUUS, 17, ItemID.TELETAB_DRAYNOR, null,
		of(1, EARTH), of(1, WATER), of(1, LAW)),
	BATTLEFRONT_TELEPORT("Battlefront Teleport", Spellbook.ARCEUUS, 23, ItemID.TELETAB_BATTLEFRONT, null,
		of(1, EARTH), of(1, FIRE), of(1, LAW)),
	FENKENSTRAINS_CASTLE_TELEPORT("Fenkenstrain's Castle Teleport", Spellbook.ARCEUUS, 48, ItemID.TELETAB_FENK,
		Quest.PRIEST_IN_PERIL,
		of(1, EARTH), of(1, SOUL), of(1, LAW)),
	HARMONY_ISLAND_TELEPORT("Harmony Island Teleport", Spellbook.ARCEUUS, 65, ItemID.TELETAB_HARMONY,
		Quest.THE_GREAT_BRAIN_ROBBERY,
		of(1, LAW), of(1, NATURE), of(1, SOUL)),
	RESURRECT_CROPS("Resurrect Crops", Spellbook.ARCEUUS, 78, NONE, null,
		of(25, EARTH), of(8, BLOOD), of(12, NATURE), of(8, SOUL)),

	KHARYRLL_TELEPORT("Kharyrll Teleport", Spellbook.ANCIENT, 66, ItemID.TABLET_KHARYLL, Quest.DESERT_TREASURE_I,
		of(1, BLOOD), of(2, LAW)),

	CURE_PLANT("Cure Plant", Spellbook.LUNAR, 66, NONE, Quest.LUNAR_DIPLOMACY,
		of(1, ASTRAL), of(8, EARTH)),
	FERTILE_SOIL("Fertile Soil", Spellbook.LUNAR, 83, NONE, Quest.LUNAR_DIPLOMACY,
		of(15, EARTH), of(3, ASTRAL), of(2, NATURE)),
	FISHING_GUILD_TELEPORT("Fishing Guild Teleport", Spellbook.LUNAR, 85, ItemID.LUNAR_TABLET_FISHING_GUILD_TELEPORT,
		Quest.LUNAR_DIPLOMACY,
		of(10, WATER), of(3, ASTRAL), of(3, LAW)),
	CATHERBY_TELEPORT("Catherby Teleport", Spellbook.LUNAR, 87, ItemID.LUNAR_TABLET_CATHERBY_TELEPORT,
		Quest.LUNAR_DIPLOMACY,
		of(10, WATER), of(3, ASTRAL), of(3, LAW)),
	/** One cast from another spellbook, only while on Lunar (lasts one spell or two minutes). */
	SPELLBOOK_SWAP("Spellbook Swap", Spellbook.LUNAR, 96, NONE, Quest.DREAM_MENTOR,
		of(3, ASTRAL), of(2, COSMIC), of(1, LAW));

	private final String displayName;
	private final Spellbook spellbook;
	private final int magicLevel;
	/** Tablet that does the same thing, or {@link com.farmrunautopilot.data.DataConstants#NONE}. */
	private final int tabletItemId;
	private final List<Requirement> requirements;
	private final List<RuneAmount> runes;

	Spell(String displayName, Spellbook spellbook, int magicLevel, int tabletItemId, Quest quest, RuneAmount... runes)
	{
		this.displayName = displayName;
		this.spellbook = spellbook;
		this.magicLevel = magicLevel;
		this.tabletItemId = tabletItemId;
		this.requirements = quest == null
			? Collections.emptyList()
			: Collections.singletonList(Requirement.quest(quest));
		this.runes = Collections.unmodifiableList(Arrays.asList(runes));
	}

	public boolean hasTablet()
	{
		return tabletItemId != NONE;
	}
}
