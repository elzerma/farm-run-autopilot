package com.farmrunautopilot.data.travel;

import static com.farmrunautopilot.data.PatchType.FRUIT_TREE;
import static com.farmrunautopilot.data.PatchType.HERB;
import static com.farmrunautopilot.data.PatchType.TREE;
import static com.farmrunautopilot.data.Walk.LONG;
import static com.farmrunautopilot.data.Walk.MEDIUM;
import static com.farmrunautopilot.data.Walk.SHORT;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.Walk;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * Ways to reach each location (SPEC 8.2). Walk distances are starting estimates (UNVERIFIED); M8
 * replaces them with the player's own timings. "Primary" marks the wiki's first choice for that run
 * type, used by Meta mode and as the default when the player has it.
 *
 * <p>Gnome gliders, charter ships, the Lovakengj minecart and similar transports that must be boarded
 * somewhere specific are left to the route planner's link edges (M6).
 */
@Getter
public enum TravelMethod
{
	// Lumbridge
	LUMBRIDGE_TELEPORT(spell(Location.LUMBRIDGE, MEDIUM, Spell.LUMBRIDGE_TELEPORT).primaryFor(TREE)),
	LUMBRIDGE_HOME_TELEPORT(spell(Location.LUMBRIDGE, MEDIUM, Spell.LUMBRIDGE_HOME_TELEPORT)
		.note("Free, but slow to cast")),
	DIARY_CAPE_LUMBRIDGE(item(Location.LUMBRIDGE, "Achievement diary cape (Hatius Cosaintus)", MEDIUM,
		TravelKind.CAPE, TravelItem.ACHIEVEMENT_DIARY_CAPE)),

	// Varrock
	VARROCK_TELEPORT(spell(Location.VARROCK, MEDIUM, Spell.VARROCK_TELEPORT).primaryFor(TREE)),
	DIARY_CAPE_VARROCK(item(Location.VARROCK, "Achievement diary cape (Toby)", MEDIUM,
		TravelKind.CAPE, TravelItem.ACHIEVEMENT_DIARY_CAPE)),
	SKILLS_NECKLACE_COOKS_GUILD(item(Location.VARROCK, "Skills necklace (Cooks' Guild)", MEDIUM,
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)),
	RING_OF_WEALTH_GRAND_EXCHANGE(item(Location.VARROCK, "Ring of wealth (Grand Exchange)", LONG,
		TravelKind.JEWELLERY, TravelItem.RING_OF_WEALTH).jewelleryBox(JewelleryBoxTier.ORNATE)),
	SPIRIT_TREE_GRAND_EXCHANGE(spiritTree(Location.VARROCK, "Spirit tree (Grand Exchange)", LONG)),

	// Falador Park
	RING_OF_WEALTH_FALADOR_PARK(item(Location.FALADOR_PARK, "Ring of wealth (Falador Park)", SHORT,
		TravelKind.JEWELLERY, TravelItem.RING_OF_WEALTH).jewelleryBox(JewelleryBoxTier.ORNATE).primaryFor(TREE)),
	FALADOR_TELEPORT_TO_PARK(spell(Location.FALADOR_PARK, MEDIUM, Spell.FALADOR_TELEPORT)),
	SKILLS_NECKLACE_MINING_GUILD(item(Location.FALADOR_PARK, "Skills necklace (Mining Guild)", MEDIUM,
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)),

	// Taverley
	HOUSE_PORTAL_TAVERLEY(housePortal(Location.TAVERLEY, HousePortal.TAVERLEY, SHORT).primaryFor(TREE)),
	TAVERLEY_TABLET(item(Location.TAVERLEY, "Taverley teleport tablet", SHORT,
		TravelKind.TABLET, TravelItem.TAVERLEY_TABLET).primaryFor(TREE)),
	CONSTRUCTION_CAPE_TAVERLEY(item(Location.TAVERLEY, "Construction cape (Taverley)", SHORT,
		TravelKind.CAPE, TravelItem.CONSTRUCTION_CAPE)),
	FALADOR_TELEPORT_TO_TAVERLEY(spell(Location.TAVERLEY, LONG, Spell.FALADOR_TELEPORT)),
	GAMES_NECKLACE_BURTHORPE(item(Location.TAVERLEY, "Games necklace (Burthorpe)", LONG,
		TravelKind.JEWELLERY, TravelItem.GAMES_NECKLACE).jewelleryBox(JewelleryBoxTier.BASIC)),
	COMBAT_BRACELET_WARRIORS_GUILD(item(Location.TAVERLEY, "Combat bracelet (Warriors' Guild)", LONG,
		TravelKind.JEWELLERY, TravelItem.COMBAT_BRACELET).jewelleryBox(JewelleryBoxTier.FANCY)),

	// Gnome Stronghold (tree + fruit tree)
	SLAYER_RING_STRONGHOLD_CAVE(item(Location.GNOME_STRONGHOLD, "Slayer ring (Stronghold Slayer Cave)", SHORT,
		TravelKind.JEWELLERY, TravelItem.SLAYER_RING).primaryFor(TREE)),
	SPIRIT_TREE_GNOME_STRONGHOLD(spiritTree(Location.GNOME_STRONGHOLD, "Spirit tree (Gnome Stronghold)", MEDIUM)
		.primaryFor(FRUIT_TREE)),
	ROYAL_SEED_POD(item(Location.GNOME_STRONGHOLD, "Royal seed pod", MEDIUM, TravelKind.ITEM, TravelItem.ROYAL_SEED_POD)),

	// Farming Guild (tree, herb, fruit tree)
	FARMING_CAPE(item(Location.FARMING_GUILD, "Farming cape", MEDIUM, TravelKind.CAPE, TravelItem.FARMING_CAPE)
		.primaryFor(TREE, FRUIT_TREE, HERB)),
	SKILLS_NECKLACE_FARMING_GUILD(item(Location.FARMING_GUILD, "Skills necklace (Farming Guild)", MEDIUM,
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)
		.primaryFor(TREE, FRUIT_TREE, HERB).note("Lands inside the guild with 45 Farming")),
	MAX_CAPE_FARMING_GUILD(item(Location.FARMING_GUILD, "Max cape (Farming Guild)", MEDIUM,
		TravelKind.CAPE, TravelItem.MAX_CAPE)),
	SPIRIT_TREE_FARMING_GUILD(spiritTree(Location.FARMING_GUILD, "Spirit tree (Farming Guild)", MEDIUM)
		.requires(Requirement.unlock(Unlock.SPIRIT_TREE_FARMING_GUILD))),
	FAIRY_RING_CIR(fairyRing(Location.FARMING_GUILD, "CIR", MEDIUM)),
	BATTLEFRONT_TELEPORT(spell(Location.FARMING_GUILD, LONG, Spell.BATTLEFRONT_TELEPORT)),
	RADAS_BLESSING_MOUNT_KARUULM(item(Location.FARMING_GUILD, "Rada's blessing (Mount Karuulm)", MEDIUM,
		TravelKind.DIARY_ITEM, TravelItem.RADAS_BLESSING)),

	// Nemus Retreat
	PENDANT_OF_ATES_NEMUS_RETREAT(item(Location.NEMUS_RETREAT, "Pendant of Ates (Nemus Retreat)", SHORT,
		TravelKind.ITEM, TravelItem.PENDANT_OF_ATES)
		.requires(Requirement.unlock(Unlock.ATES_STATUE_NEMUS_RETREAT)).primaryFor(TREE)),
	QUETZAL_AUBURNVALE(quetzal(Location.NEMUS_RETREAT, "Quetzal whistle (Auburnvale)", MEDIUM)),
	FAIRY_RING_AIS(fairyRing(Location.NEMUS_RETREAT, "AIS", MEDIUM)
		.requires(Requirement.skill(Skill.AGILITY, 36)).note("Stepping stones (24 Agility) and broken wall (36)")),

	// Tree Gnome Village
	SPIRIT_TREE_TREE_GNOME_VILLAGE(spiritTree(Location.TREE_GNOME_VILLAGE, "Spirit tree (Tree Gnome Village)", MEDIUM)
		.primaryFor(FRUIT_TREE).note("Lands in the village; follow Elkoy out of the maze to the patch")
		.directions("Talk to Elkoy and follow him out of the maze")),
	FAIRY_RING_CIQ(fairyRing(Location.TREE_GNOME_VILLAGE, "CIQ", MEDIUM)),

	// Catherby (herb + fruit tree)
	CAMELOT_TELEPORT(spell(Location.CATHERBY, LONG, Spell.CAMELOT_TELEPORT).primaryFor(FRUIT_TREE)),
	CATHERBY_TELEPORT(spell(Location.CATHERBY, SHORT, Spell.CATHERBY_TELEPORT)
		.nexus(PortalNexus.Destination.CATHERBY).primaryFor(HERB)),

	// Brimhaven
	HOUSE_PORTAL_BRIMHAVEN(housePortal(Location.BRIMHAVEN, HousePortal.BRIMHAVEN, MEDIUM).primaryFor(FRUIT_TREE)),
	BRIMHAVEN_TABLET(item(Location.BRIMHAVEN, "Brimhaven teleport tablet", MEDIUM,
		TravelKind.TABLET, TravelItem.BRIMHAVEN_TABLET).primaryFor(FRUIT_TREE)),
	CONSTRUCTION_CAPE_BRIMHAVEN(item(Location.BRIMHAVEN, "Construction cape (Brimhaven)", MEDIUM,
		TravelKind.CAPE, TravelItem.CONSTRUCTION_CAPE)),
	SPIRIT_TREE_BRIMHAVEN(spiritTree(Location.BRIMHAVEN, "Spirit tree (Brimhaven)", SHORT)
		.requires(Requirement.unlock(Unlock.SPIRIT_TREE_BRIMHAVEN))),
	ARDOUGNE_TELEPORT_AND_BOAT(spell(Location.BRIMHAVEN, LONG, Spell.ARDOUGNE_TELEPORT)
		.coins(30).note("Then the boat from Ardougne docks")),
	GLORY_KARAMJA(item(Location.BRIMHAVEN, "Amulet of glory (Karamja)", LONG,
		TravelKind.JEWELLERY, TravelItem.AMULET_OF_GLORY).jewelleryBox(JewelleryBoxTier.ORNATE)),

	// Lletya
	TELEPORT_CRYSTAL_LLETYA(item(Location.LLETYA, "Teleport crystal (Lletya)", SHORT,
		TravelKind.ITEM, TravelItem.TELEPORT_CRYSTAL).primaryFor(FRUIT_TREE)),

	// Kastori
	QUETZAL_KASTORI(quetzal(Location.KASTORI, "Quetzal whistle (Kastori)", SHORT)
		.requires(Requirement.unlock(Unlock.QUETZAL_KASTORI)).primaryFor(FRUIT_TREE)),
	PENDANT_OF_ATES_NORTH_KASTORI(item(Location.KASTORI, "Pendant of Ates (North of Kastori)", SHORT,
		TravelKind.ITEM, TravelItem.PENDANT_OF_ATES).requires(Requirement.unlock(Unlock.ATES_STATUE_NORTH_KASTORI))),

	// Falador farm (herb)
	EXPLORERS_RING_CABBAGE_PATCH(item(Location.FALADOR_FARM, "Explorer's ring (cabbage patch)", SHORT,
		TravelKind.DIARY_ITEM, TravelItem.EXPLORERS_RING).primaryFor(HERB).note("Ring 2: 3 uses per day")),
	DRAYNOR_MANOR_TELEPORT(spell(Location.FALADOR_FARM, MEDIUM, Spell.DRAYNOR_MANOR_TELEPORT)),
	SPIRIT_TREE_PORT_SARIM(spiritTree(Location.FALADOR_FARM, "Spirit tree (Port Sarim)", MEDIUM)
		.requires(Requirement.unlock(Unlock.SPIRIT_TREE_PORT_SARIM))),
	GLORY_DRAYNOR(item(Location.FALADOR_FARM, "Amulet of glory (Draynor Village)", LONG,
		TravelKind.JEWELLERY, TravelItem.AMULET_OF_GLORY).jewelleryBox(JewelleryBoxTier.ORNATE)),
	FALADOR_TELEPORT_TO_FARM(spell(Location.FALADOR_FARM, LONG, Spell.FALADOR_TELEPORT)),

	// Port Phasmatys (herb)
	FAIRY_RING_ALQ(fairyRing(Location.PORT_PHASMATYS, "ALQ", MEDIUM).primaryFor(HERB)),
	ECTOPHIAL(item(Location.PORT_PHASMATYS, "Ectophial", MEDIUM, TravelKind.ITEM, TravelItem.ECTOPHIAL)),
	FENKENSTRAINS_CASTLE_TELEPORT(spell(Location.PORT_PHASMATYS, MEDIUM, Spell.FENKENSTRAINS_CASTLE_TELEPORT)
		.nexus(PortalNexus.Destination.FENKENSTRAINS_CASTLE)),
	KHARYRLL_TELEPORT(spell(Location.PORT_PHASMATYS, LONG, Spell.KHARYRLL_TELEPORT)
		.nexus(PortalNexus.Destination.KHARYRLL)),

	// Ardougne farm (herb)
	ARDOUGNE_CLOAK_FARM(item(Location.ARDOUGNE_FARM, "Ardougne cloak (farm)", SHORT,
		TravelKind.DIARY_ITEM, TravelItem.ARDOUGNE_CLOAK).primaryFor(HERB)
		.note("Cloak 2: 3 uses per day, cloak 3: 5, cloak 4: unlimited")),
	SKILLS_NECKLACE_FISHING_GUILD(item(Location.ARDOUGNE_FARM, "Skills necklace (Fishing Guild)", MEDIUM,
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)),
	MAX_CAPE_FISHING_GUILD(item(Location.ARDOUGNE_FARM, "Max cape (Fishing Guild)", MEDIUM,
		TravelKind.CAPE, TravelItem.MAX_CAPE)),
	FISHING_GUILD_TELEPORT(spell(Location.ARDOUGNE_FARM, MEDIUM, Spell.FISHING_GUILD_TELEPORT)
		.nexus(PortalNexus.Destination.FISHING_GUILD)),
	COMBAT_BRACELET_RANGING_GUILD(item(Location.ARDOUGNE_FARM, "Combat bracelet (Ranging Guild)", MEDIUM,
		TravelKind.JEWELLERY, TravelItem.COMBAT_BRACELET).jewelleryBox(JewelleryBoxTier.FANCY)),
	FAIRY_RING_BLR(fairyRing(Location.ARDOUGNE_FARM, "BLR", MEDIUM)),
	ARDOUGNE_TELEPORT(spell(Location.ARDOUGNE_FARM, LONG, Spell.ARDOUGNE_TELEPORT)
		.nexus(PortalNexus.Destination.ARDOUGNE)),

	// Hosidius (herb)
	XERICS_TALISMAN_GLADE(item(Location.HOSIDIUS, "Xeric's talisman (Xeric's Glade)", SHORT,
		TravelKind.ITEM, TravelItem.XERICS_TALISMAN).primaryFor(HERB)),
	HOUSE_PORTAL_HOSIDIUS(housePortal(Location.HOSIDIUS, HousePortal.HOSIDIUS, MEDIUM)),
	HOSIDIUS_TABLET(item(Location.HOSIDIUS, "Hosidius teleport tablet", MEDIUM,
		TravelKind.TABLET, TravelItem.HOSIDIUS_TABLET)),
	CONSTRUCTION_CAPE_HOSIDIUS(item(Location.HOSIDIUS, "Construction cape (Hosidius)", MEDIUM,
		TravelKind.CAPE, TravelItem.CONSTRUCTION_CAPE)),
	KHAREDSTS_MEMOIRS_LANCALLIUMS(item(Location.HOSIDIUS, "Kharedst's memoirs (Lunch by the Lancalliums)", SHORT,
		TravelKind.ITEM, TravelItem.KHAREDSTS_MEMOIRS).requires(Requirement.quest(Quest.THE_DEPTHS_OF_DESPAIR))),
	FAIRY_RING_AKR(fairyRing(Location.HOSIDIUS, "AKR", MEDIUM)),
	SKILLS_NECKLACE_WOODCUTTING_GUILD(item(Location.HOSIDIUS, "Skills necklace (Woodcutting Guild)", MEDIUM,
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)
		.requires(Requirement.skill(Skill.AGILITY, 45)).note("Stepping stones need 45 Agility")),

	// Troll Stronghold (herb)
	STONY_BASALT_ROOF(item(Location.TROLL_STRONGHOLD, "Stony basalt (roof)", SHORT,
		TravelKind.ITEM, TravelItem.STONY_BASALT).primaryFor(HERB)
		.requires(Requirement.diary(AchievementDiary.FREMENNIK, AchievementDiary.Tier.HARD),
			Requirement.skill(Skill.AGILITY, 73))),
	// UNVERIFIED that the nexus Troll Stronghold portal lands at the basalt entrance spot.
	STONY_BASALT_ENTRANCE(item(Location.TROLL_STRONGHOLD, "Stony basalt (entrance)", LONG,
		TravelKind.ITEM, TravelItem.STONY_BASALT).nexus(PortalNexus.Destination.TROLL_STRONGHOLD)),
	TROLLHEIM_TELEPORT(spell(Location.TROLL_STRONGHOLD, LONG, Spell.TROLLHEIM_TELEPORT)),

	// Harmony Island (herb)
	HARMONY_ISLAND_TELEPORT(spell(Location.HARMONY_ISLAND, SHORT, Spell.HARMONY_ISLAND_TELEPORT)
		.nexus(PortalNexus.Destination.HARMONY_ISLAND).primaryFor(HERB)),

	// Weiss (herb)
	ICY_BASALT(item(Location.WEISS, "Icy basalt", SHORT, TravelKind.ITEM, TravelItem.ICY_BASALT)
		.nexus(PortalNexus.Destination.WEISS).primaryFor(HERB)),

	// Civitas illa Fortis / Ortus Farm (herb)
	QUETZAL_HUNTER_GUILD(quetzal(Location.CIVITAS_ILLA_FORTIS, "Quetzal whistle (Hunter Guild)", MEDIUM).primaryFor(HERB)),
	HUNTER_CAPE(item(Location.CIVITAS_ILLA_FORTIS, "Hunter cape", MEDIUM, TravelKind.CAPE, TravelItem.HUNTER_CAPE)),
	MAX_CAPE_HUNTER_GUILD(item(Location.CIVITAS_ILLA_FORTIS, "Max cape (Hunter Guild)", MEDIUM,
		TravelKind.CAPE, TravelItem.MAX_CAPE)),
	CIVITAS_ILLA_FORTIS_TELEPORT(spell(Location.CIVITAS_ILLA_FORTIS, LONG, Spell.CIVITAS_ILLA_FORTIS_TELEPORT)
		.nexus(PortalNexus.Destination.CIVITAS_ILLA_FORTIS).note("Then the quetzal to the Hunter Guild")
		// The quetzal at the city's landing site, Renu, is matched by name: its ID is one of the baby quetzal colours
		.transfer("Renu", "Take the quetzal to the Hunter Guild")),
	FAIRY_RING_AJP(fairyRing(Location.CIVITAS_ILLA_FORTIS, "AJP", MEDIUM));

	private final Location destination;
	private final String displayName;
	private final TravelKind kind;
	private final Walk walk;
	/** The spell to cast (or its tablet), or null. HOUSE_PORTAL methods use Teleport to House. */
	private final Spell spell;
	/** The teleport item, or null. */
	private final TravelItem item;
	private final int coins;
	/** Fairy ring code for FAIRY_RING methods, otherwise null. */
	private final String fairyRingCode;
	private final String note;
	/** What to do after arriving to reach the patch, shown in the run guide; null if it's just a walk. */
	private final String directions;
	/**
	 * An NPC to ride after landing and before walking to the patch, by name (Renu, the Civitas quetzal), or null.
	 * It's faster than running, so the guide says to take it and outlines it.
	 */
	private final String transferNpcName;
	/** What to do with {@link #transferNpcName}, e.g. "Take the quetzal to the Hunter Guild"; null if none. */
	private final String transferText;
	/** Requirements beyond the spell's own and owning the item. */
	private final List<Requirement> requirements;
	private final Set<PatchType> primaryFor;
	/** The same teleport from a POH portal nexus, or null. */
	private final PortalNexus.Destination nexus;
	/** The same teleport from a POH jewellery box of at least this tier, or null. */
	private final JewelleryBoxTier jewelleryBox;

	TravelMethod(Def def)
	{
		this.destination = def.destination;
		this.displayName = def.displayName;
		this.kind = def.kind;
		this.walk = def.walk;
		this.spell = def.spell;
		this.item = def.item;
		this.coins = def.coins;
		this.fairyRingCode = def.fairyRingCode;
		this.note = def.note;
		this.directions = def.directions;
		this.transferNpcName = def.transferNpcName;
		this.transferText = def.transferText;
		this.requirements = Collections.unmodifiableList(def.requirements);
		this.primaryFor = Collections.unmodifiableSet(def.primaryFor);
		this.nexus = def.nexus;
		this.jewelleryBox = def.jewelleryBox;
	}

	public boolean isPrimaryFor(PatchType type)
	{
		return primaryFor.contains(type);
	}

	private static Def spell(Location destination, Walk walk, Spell spell)
	{
		final Def def = new Def(destination, spell.getDisplayName(), TravelKind.SPELL, walk);
		def.spell = spell;
		return def;
	}

	private static Def item(Location destination, String displayName, Walk walk, TravelKind kind, TravelItem item)
	{
		final Def def = new Def(destination, displayName, kind, walk);
		def.item = item;
		return def;
	}

	private static Def fairyRing(Location destination, String code, Walk walk)
	{
		final Def def = new Def(destination, "Fairy ring " + code, TravelKind.FAIRY_RING, walk);
		def.fairyRingCode = code;
		def.requirements.add(Requirement.unlock(Unlock.FAIRY_RINGS));
		return def;
	}

	private static Def spiritTree(Location destination, String displayName, Walk walk)
	{
		final Def def = new Def(destination, displayName, TravelKind.SPIRIT_TREE, walk);
		def.requirements.add(Requirement.unlock(Unlock.SPIRIT_TREES));
		return def;
	}

	private static Def housePortal(Location destination, HousePortal portal, Walk walk)
	{
		final Def def = new Def(destination, "Teleport to House (" + portal.getDisplayName() + " portal)",
			TravelKind.HOUSE_PORTAL, walk);
		def.spell = Spell.TELEPORT_TO_HOUSE;
		def.requirements.add(Requirement.housePortal(portal));
		def.note = "Lands inside the house unless it is set to teleport outside";
		return def;
	}

	private static Def quetzal(Location destination, String displayName, Walk walk)
	{
		final Def def = new Def(destination, displayName, TravelKind.QUETZAL, walk);
		def.item = TravelItem.QUETZAL_WHISTLE;
		return def;
	}

	private static final class Def
	{
		private final Location destination;
		private final String displayName;
		private final TravelKind kind;
		private final Walk walk;
		private Spell spell;
		private TravelItem item;
		private int coins;
		private String fairyRingCode;
		private String note;
		private String directions;
		private String transferNpcName;
		private String transferText;
		private final List<Requirement> requirements = new ArrayList<>();
		private final Set<PatchType> primaryFor = EnumSet.noneOf(PatchType.class);
		private PortalNexus.Destination nexus;
		private JewelleryBoxTier jewelleryBox;

		private Def(Location destination, String displayName, TravelKind kind, Walk walk)
		{
			this.destination = destination;
			this.displayName = displayName;
			this.kind = kind;
			this.walk = walk;
		}

		Def primaryFor(PatchType... types)
		{
			primaryFor.addAll(Arrays.asList(types));
			return this;
		}

		Def requires(Requirement... more)
		{
			requirements.addAll(Arrays.asList(more));
			return this;
		}

		Def coins(int amount)
		{
			coins = amount;
			return this;
		}

		Def note(String text)
		{
			note = text;
			return this;
		}

		Def directions(String text)
		{
			directions = text;
			return this;
		}

		Def transfer(String npcName, String text)
		{
			transferNpcName = npcName;
			transferText = text;
			return this;
		}

		Def nexus(PortalNexus.Destination destination)
		{
			nexus = destination;
			return this;
		}

		Def jewelleryBox(JewelleryBoxTier tier)
		{
			jewelleryBox = tier;
			return this;
		}
	}
}
