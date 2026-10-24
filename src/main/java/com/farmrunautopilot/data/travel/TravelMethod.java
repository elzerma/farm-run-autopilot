package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PortalNexus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * The plugin's own ways to each location (SPEC 8.2), with the names the guide shows for them. Routes are planned on the
 * travel atlas (docs/plans/travel-rework.md), which knows every way and how far each walks; an atlas way that lands
 * where one of these does, the same way (AtlasLinks has each landing), is named as it and keeps its item, spell, charges
 * and daily uses. Its journey (boats, NPCs, shortcuts) is written from the atlas's own steps (sprint T5).
 */
@Getter
public enum TravelMethod
{
	// Lumbridge
	LUMBRIDGE_TELEPORT(spell(Location.LUMBRIDGE, Spell.LUMBRIDGE_TELEPORT)),
	LUMBRIDGE_HOME_TELEPORT(spell(Location.LUMBRIDGE, Spell.LUMBRIDGE_HOME_TELEPORT)),
	DIARY_CAPE_LUMBRIDGE(item(Location.LUMBRIDGE, "Achievement diary cape (Hatius Cosaintus)",
		TravelKind.CAPE, TravelItem.ACHIEVEMENT_DIARY_CAPE)),

	// Varrock
	VARROCK_TELEPORT(spell(Location.VARROCK, Spell.VARROCK_TELEPORT)),
	DIARY_CAPE_VARROCK(item(Location.VARROCK, "Achievement diary cape (Toby)",
		TravelKind.CAPE, TravelItem.ACHIEVEMENT_DIARY_CAPE)),
	SKILLS_NECKLACE_COOKS_GUILD(item(Location.VARROCK, "Skills necklace (Cooks' Guild)",
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)),
	RING_OF_WEALTH_GRAND_EXCHANGE(item(Location.VARROCK, "Ring of wealth (Grand Exchange)",
		TravelKind.JEWELLERY, TravelItem.RING_OF_WEALTH).jewelleryBox(JewelleryBoxTier.ORNATE)),
	SPIRIT_TREE_GRAND_EXCHANGE(spiritTree(Location.VARROCK, "Spirit tree (Grand Exchange)")),

	// Falador Park
	RING_OF_WEALTH_FALADOR_PARK(item(Location.FALADOR_PARK, "Ring of wealth (Falador Park)",
		TravelKind.JEWELLERY, TravelItem.RING_OF_WEALTH).jewelleryBox(JewelleryBoxTier.ORNATE)),
	FALADOR_TELEPORT_TO_PARK(spell(Location.FALADOR_PARK, Spell.FALADOR_TELEPORT)),
	SKILLS_NECKLACE_MINING_GUILD(item(Location.FALADOR_PARK, "Skills necklace (Mining Guild)",
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)),

	// Taverley
	HOUSE_PORTAL_TAVERLEY(housePortal(Location.TAVERLEY, HousePortal.TAVERLEY)),
	TAVERLEY_TABLET(item(Location.TAVERLEY, "Taverley teleport tablet",
		TravelKind.TABLET, TravelItem.TAVERLEY_TABLET)),
	CONSTRUCTION_CAPE_TAVERLEY(item(Location.TAVERLEY, "Construction cape (Taverley)",
		TravelKind.CAPE, TravelItem.CONSTRUCTION_CAPE)),
	FALADOR_TELEPORT_TO_TAVERLEY(spell(Location.TAVERLEY, Spell.FALADOR_TELEPORT)),
	// From the Grand Tree's basket after the Gnome Stronghold stop (elzerma): 6 tiles from the fruit tree, lands 16 from
	// the tree patch; one normal log a flight
	HOT_AIR_BALLOON_TAVERLEY(balloon(Location.TAVERLEY, "Hot air balloon from the Grand Tree")),
	GAMES_NECKLACE_BURTHORPE(item(Location.TAVERLEY, "Games necklace (Burthorpe)",
		TravelKind.JEWELLERY, TravelItem.GAMES_NECKLACE).jewelleryBox(JewelleryBoxTier.BASIC)),
	COMBAT_BRACELET_WARRIORS_GUILD(item(Location.TAVERLEY, "Combat bracelet (Warriors' Guild)",
		TravelKind.JEWELLERY, TravelItem.COMBAT_BRACELET).jewelleryBox(JewelleryBoxTier.FANCY)),

	// Gnome Stronghold (tree + fruit tree)
	SLAYER_RING_STRONGHOLD_CAVE(item(Location.GNOME_STRONGHOLD, "Slayer ring (Stronghold Slayer Cave)",
		TravelKind.JEWELLERY, TravelItem.SLAYER_RING)),
	SPIRIT_TREE_GNOME_STRONGHOLD(spiritTree(Location.GNOME_STRONGHOLD, "Spirit tree (Gnome Stronghold)")),
	ROYAL_SEED_POD(item(Location.GNOME_STRONGHOLD, "Royal seed pod", TravelKind.ITEM, TravelItem.ROYAL_SEED_POD)),

	// Farming Guild (tree, herb, fruit tree)
	FARMING_CAPE(item(Location.FARMING_GUILD, "Farming cape", TravelKind.CAPE, TravelItem.FARMING_CAPE)),
	SKILLS_NECKLACE_FARMING_GUILD(item(Location.FARMING_GUILD, "Skills necklace (Farming Guild)",
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)),
	MAX_CAPE_FARMING_GUILD(item(Location.FARMING_GUILD, "Max cape (Farming Guild)",
		TravelKind.CAPE, TravelItem.MAX_CAPE)),
	SPIRIT_TREE_FARMING_GUILD(spiritTree(Location.FARMING_GUILD, "Spirit tree (Farming Guild)")
		.requires(Requirement.unlock(Unlock.SPIRIT_TREE_FARMING_GUILD))),
	FAIRY_RING_CIR(fairyRing(Location.FARMING_GUILD, "CIR")),
	BATTLEFRONT_TELEPORT(spell(Location.FARMING_GUILD, Spell.BATTLEFRONT_TELEPORT)),
	RADAS_BLESSING_MOUNT_KARUULM(item(Location.FARMING_GUILD, "Rada's blessing (Mount Karuulm)",
		TravelKind.DIARY_ITEM, TravelItem.RADAS_BLESSING)),

	// Nemus Retreat
	PENDANT_OF_ATES_NEMUS_RETREAT(item(Location.NEMUS_RETREAT, "Pendant of Ates (Nemus Retreat)",
		TravelKind.ITEM, TravelItem.PENDANT_OF_ATES)
		.requires(Requirement.unlock(Unlock.ATES_STATUE_NEMUS_RETREAT))),
	QUETZAL_AUBURNVALE(quetzal(Location.NEMUS_RETREAT, "Quetzal whistle (Auburnvale)")),
	FAIRY_RING_AIS(fairyRing(Location.NEMUS_RETREAT, "AIS")
		.requires(Requirement.skill(Skill.AGILITY, 36))),

	// Tree Gnome Village
	SPIRIT_TREE_TREE_GNOME_VILLAGE(spiritTree(Location.TREE_GNOME_VILLAGE, "Spirit tree (Tree Gnome Village)")),
	FAIRY_RING_CIQ(fairyRing(Location.TREE_GNOME_VILLAGE, "CIQ")),

	// Catherby (herb + fruit tree)
	CAMELOT_TELEPORT(spell(Location.CATHERBY, Spell.CAMELOT_TELEPORT)),
	CATHERBY_TELEPORT(spell(Location.CATHERBY, Spell.CATHERBY_TELEPORT)
		.nexus(PortalNexus.Destination.CATHERBY)),

	// Brimhaven
	HOUSE_PORTAL_BRIMHAVEN(housePortal(Location.BRIMHAVEN, HousePortal.BRIMHAVEN)),
	BRIMHAVEN_TABLET(item(Location.BRIMHAVEN, "Brimhaven teleport tablet",
		TravelKind.TABLET, TravelItem.BRIMHAVEN_TABLET)),
	CONSTRUCTION_CAPE_BRIMHAVEN(item(Location.BRIMHAVEN, "Construction cape (Brimhaven)",
		TravelKind.CAPE, TravelItem.CONSTRUCTION_CAPE)),
	SPIRIT_TREE_BRIMHAVEN(spiritTree(Location.BRIMHAVEN, "Spirit tree (Brimhaven)")
		.requires(Requirement.unlock(Unlock.SPIRIT_TREE_BRIMHAVEN))),
	ARDOUGNE_TELEPORT_AND_BOAT(spell(Location.BRIMHAVEN, Spell.ARDOUGNE_TELEPORT)
		.coins(30)),
	GLORY_KARAMJA(item(Location.BRIMHAVEN, "Amulet of glory (Karamja)",
		TravelKind.JEWELLERY, TravelItem.AMULET_OF_GLORY).jewelleryBox(JewelleryBoxTier.ORNATE)),

	// Lletya
	TELEPORT_CRYSTAL_LLETYA(item(Location.LLETYA, "Teleport crystal (Lletya)",
		TravelKind.ITEM, TravelItem.TELEPORT_CRYSTAL)),

	// Kastori
	QUETZAL_KASTORI(quetzal(Location.KASTORI, "Quetzal whistle (Kastori)")
		.requires(Requirement.unlock(Unlock.QUETZAL_KASTORI))),
	PENDANT_OF_ATES_NORTH_KASTORI(item(Location.KASTORI, "Pendant of Ates (North of Kastori)",
		TravelKind.ITEM, TravelItem.PENDANT_OF_ATES).requires(Requirement.unlock(Unlock.ATES_STATUE_NORTH_KASTORI))),

	// Falador farm (herb)
	EXPLORERS_RING_CABBAGE_PATCH(item(Location.FALADOR_FARM, "Explorer's ring (cabbage patch)",
		TravelKind.DIARY_ITEM, TravelItem.EXPLORERS_RING)),
	DRAYNOR_MANOR_TELEPORT(spell(Location.FALADOR_FARM, Spell.DRAYNOR_MANOR_TELEPORT)),
	SPIRIT_TREE_PORT_SARIM(spiritTree(Location.FALADOR_FARM, "Spirit tree (Port Sarim)")
		.requires(Requirement.unlock(Unlock.SPIRIT_TREE_PORT_SARIM))),
	GLORY_DRAYNOR(item(Location.FALADOR_FARM, "Amulet of glory (Draynor Village)",
		TravelKind.JEWELLERY, TravelItem.AMULET_OF_GLORY).jewelleryBox(JewelleryBoxTier.ORNATE)),
	FALADOR_TELEPORT_TO_FARM(spell(Location.FALADOR_FARM, Spell.FALADOR_TELEPORT)),

	// Port Phasmatys (herb)
	FAIRY_RING_ALQ(fairyRing(Location.PORT_PHASMATYS, "ALQ")),
	ECTOPHIAL(item(Location.PORT_PHASMATYS, "Ectophial", TravelKind.ITEM, TravelItem.ECTOPHIAL)),
	FENKENSTRAINS_CASTLE_TELEPORT(spell(Location.PORT_PHASMATYS, Spell.FENKENSTRAINS_CASTLE_TELEPORT)
		.nexus(PortalNexus.Destination.FENKENSTRAINS_CASTLE)),
	KHARYRLL_TELEPORT(spell(Location.PORT_PHASMATYS, Spell.KHARYRLL_TELEPORT)
		.nexus(PortalNexus.Destination.KHARYRLL)),

	// Ardougne farm (herb)
	ARDOUGNE_CLOAK_FARM(item(Location.ARDOUGNE_FARM, "Ardougne cloak (farm)",
		TravelKind.DIARY_ITEM, TravelItem.ARDOUGNE_CLOAK)),
	SKILLS_NECKLACE_FISHING_GUILD(item(Location.ARDOUGNE_FARM, "Skills necklace (Fishing Guild)",
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)),
	MAX_CAPE_FISHING_GUILD(item(Location.ARDOUGNE_FARM, "Max cape (Fishing Guild)",
		TravelKind.CAPE, TravelItem.MAX_CAPE)),
	FISHING_GUILD_TELEPORT(spell(Location.ARDOUGNE_FARM, Spell.FISHING_GUILD_TELEPORT)
		.nexus(PortalNexus.Destination.FISHING_GUILD)),
	COMBAT_BRACELET_RANGING_GUILD(item(Location.ARDOUGNE_FARM, "Combat bracelet (Ranging Guild)",
		TravelKind.JEWELLERY, TravelItem.COMBAT_BRACELET).jewelleryBox(JewelleryBoxTier.FANCY)),
	FAIRY_RING_BLR(fairyRing(Location.ARDOUGNE_FARM, "BLR")),
	ARDOUGNE_TELEPORT(spell(Location.ARDOUGNE_FARM, Spell.ARDOUGNE_TELEPORT)
		.nexus(PortalNexus.Destination.ARDOUGNE)),

	// Hosidius (herb)
	XERICS_TALISMAN_GLADE(item(Location.HOSIDIUS, "Xeric's talisman (Xeric's Glade)",
		TravelKind.ITEM, TravelItem.XERICS_TALISMAN)),
	HOUSE_PORTAL_HOSIDIUS(housePortal(Location.HOSIDIUS, HousePortal.HOSIDIUS)),
	HOSIDIUS_TABLET(item(Location.HOSIDIUS, "Hosidius teleport tablet",
		TravelKind.TABLET, TravelItem.HOSIDIUS_TABLET)),
	CONSTRUCTION_CAPE_HOSIDIUS(item(Location.HOSIDIUS, "Construction cape (Hosidius)",
		TravelKind.CAPE, TravelItem.CONSTRUCTION_CAPE)),
	KHAREDSTS_MEMOIRS_LANCALLIUMS(item(Location.HOSIDIUS,
		"Book of the Dead / Kharedst's memoirs (Lunch by the Lancalliums)", TravelKind.ITEM, TravelItem.KHAREDSTS_MEMOIRS)
		.requires(Requirement.quest(Quest.THE_DEPTHS_OF_DESPAIR))),
	FAIRY_RING_AKR(fairyRing(Location.HOSIDIUS, "AKR")),
	SKILLS_NECKLACE_WOODCUTTING_GUILD(item(Location.HOSIDIUS, "Skills necklace (Woodcutting Guild)",
		TravelKind.JEWELLERY, TravelItem.SKILLS_NECKLACE).jewelleryBox(JewelleryBoxTier.FANCY)
		.requires(Requirement.skill(Skill.AGILITY, 45))),

	// Troll Stronghold (herb)
	STONY_BASALT_ROOF(item(Location.TROLL_STRONGHOLD, "Stony basalt (roof)",
		TravelKind.ITEM, TravelItem.STONY_BASALT)
		.requires(Requirement.diary(AchievementDiary.FREMENNIK, AchievementDiary.Tier.HARD),
			Requirement.skill(Skill.AGILITY, 73))),
	// The nexus Troll Stronghold portal lands at the entrance (2845, 3694), or on the roof with the hard Fremennik
	// diary (Shortest Path's data).
	STONY_BASALT_ENTRANCE(item(Location.TROLL_STRONGHOLD, "Stony basalt (entrance)",
		TravelKind.ITEM, TravelItem.STONY_BASALT).nexus(PortalNexus.Destination.TROLL_STRONGHOLD)),
	TROLLHEIM_TELEPORT(spell(Location.TROLL_STRONGHOLD, Spell.TROLLHEIM_TELEPORT)),

	// Harmony Island (herb)
	HARMONY_ISLAND_TELEPORT(spell(Location.HARMONY_ISLAND, Spell.HARMONY_ISLAND_TELEPORT)
		.nexus(PortalNexus.Destination.HARMONY_ISLAND)),

	// Weiss (herb)
	ICY_BASALT(item(Location.WEISS, "Icy basalt", TravelKind.ITEM, TravelItem.ICY_BASALT)
		.nexus(PortalNexus.Destination.WEISS)),

	// Civitas illa Fortis / Ortus Farm (herb)
	QUETZAL_HUNTER_GUILD(quetzal(Location.CIVITAS_ILLA_FORTIS, "Quetzal whistle (Hunter Guild)")),
	HUNTER_CAPE(item(Location.CIVITAS_ILLA_FORTIS, "Hunter cape", TravelKind.CAPE, TravelItem.HUNTER_CAPE)),
	MAX_CAPE_HUNTER_GUILD(item(Location.CIVITAS_ILLA_FORTIS, "Max cape (Hunter Guild)",
		TravelKind.CAPE, TravelItem.MAX_CAPE)),
	CIVITAS_ILLA_FORTIS_TELEPORT(spell(Location.CIVITAS_ILLA_FORTIS, Spell.CIVITAS_ILLA_FORTIS_TELEPORT)
		.nexus(PortalNexus.Destination.CIVITAS_ILLA_FORTIS)),
	FAIRY_RING_AJP(fairyRing(Location.CIVITAS_ILLA_FORTIS, "AJP")),

	// Prifddinas (allotments)
	TELEPORT_CRYSTAL_PRIFDDINAS(item(Location.PRIFDDINAS, "Teleport crystal (Prifddinas)",
		TravelKind.ITEM, TravelItem.TELEPORT_CRYSTAL).requires(Requirement.quest(Quest.SONG_OF_THE_ELVES))),
	HOUSE_PORTAL_PRIFDDINAS(housePortal(Location.PRIFDDINAS, HousePortal.PRIFDDINAS)),

	// Hops, north of Lumbridge
	LUMBRIDGE_TELEPORT_HOPS(spell(Location.LUMBRIDGE_HOPS, Spell.LUMBRIDGE_TELEPORT)),
	VARROCK_TELEPORT_HOPS(spell(Location.LUMBRIDGE_HOPS, Spell.VARROCK_TELEPORT)),
	COMBAT_BRACELET_CHAMPIONS_GUILD(item(Location.LUMBRIDGE_HOPS, "Combat bracelet (Champions' Guild)",
		TravelKind.JEWELLERY, TravelItem.COMBAT_BRACELET).jewelleryBox(JewelleryBoxTier.FANCY)),

	// Hops, Seers' Village (north of McGrubor's Wood)
	CAMELOT_TELEPORT_SEERS_HOPS(spell(Location.SEERS_VILLAGE_HOPS, Spell.CAMELOT_TELEPORT)),
	FAIRY_RING_ALS(fairyRing(Location.SEERS_VILLAGE_HOPS, "ALS")),
	COMBAT_BRACELET_RANGING_GUILD_HOPS(item(Location.SEERS_VILLAGE_HOPS, "Combat bracelet (Ranging Guild)",
		TravelKind.JEWELLERY, TravelItem.COMBAT_BRACELET).jewelleryBox(JewelleryBoxTier.FANCY)),

	// Hops, Yanille
	HOUSE_PORTAL_YANILLE(housePortal(Location.YANILLE_HOPS, HousePortal.YANILLE)),
	FAIRY_RING_CIQ_HOPS(fairyRing(Location.YANILLE_HOPS, "CIQ")),

	// Hops, Aldarin
	FAIRY_RING_CKQ(fairyRing(Location.ALDARIN_HOPS, "CKQ")),
	QUETZAL_ALDARIN(quetzal(Location.ALDARIN_HOPS, "Quetzal whistle (Aldarin)")),
	HOUSE_PORTAL_ALDARIN(housePortal(Location.ALDARIN_HOPS, HousePortal.ALDARIN));

	private final Location destination;
	private final String displayName;
	private final TravelKind kind;
	/** The spell to cast (or its tablet), or null. HOUSE_PORTAL methods use Teleport to House. */
	private final Spell spell;
	/** The teleport item, or null. */
	private final TravelItem item;
	private final int coins;
	/** Requirements beyond the spell's own and owning the item. */
	private final List<Requirement> requirements;
	/** The same teleport from a POH portal nexus, or null. */
	private final PortalNexus.Destination nexus;
	/** The same teleport from a POH jewellery box of at least this tier, or null. */
	private final JewelleryBoxTier jewelleryBox;

	TravelMethod(Def def)
	{
		this.destination = def.destination;
		this.displayName = def.displayName;
		this.kind = def.kind;
		this.spell = def.spell;
		this.item = def.item;
		this.coins = def.coins;
		this.requirements = Collections.unmodifiableList(def.requirements);
		this.nexus = def.nexus;
		this.jewelleryBox = def.jewelleryBox;
	}

	private static Def spell(Location destination, Spell spell)
	{
		final Def def = new Def(destination, spell.getDisplayName(), TravelKind.SPELL);
		def.spell = spell;
		return def;
	}

	private static Def item(Location destination, String displayName, TravelKind kind, TravelItem item)
	{
		final Def def = new Def(destination, displayName, kind);
		def.item = item;
		return def;
	}

	private static Def fairyRing(Location destination, String code)
	{
		final Def def = new Def(destination, "Fairy ring " + code, TravelKind.FAIRY_RING);
		def.requirements.add(Requirement.unlock(Unlock.FAIRY_RINGS));
		return def;
	}

	private static Def spiritTree(Location destination, String displayName)
	{
		final Def def = new Def(destination, displayName, TravelKind.SPIRIT_TREE);
		def.requirements.add(Requirement.unlock(Unlock.SPIRIT_TREES));
		return def;
	}

	/** A balloon flight: Enlightened Journey, and the route from the basket it leaves from unlocked. */
	private static Def balloon(Location destination, String displayName)
	{
		final Def def = new Def(destination, displayName, TravelKind.BALLOON);
		def.requirements.add(Requirement.quest(Quest.ENLIGHTENED_JOURNEY));
		def.requirements.add(Requirement.unlock(Unlock.BALLOON_GRAND_TREE));
		return def;
	}

	private static Def housePortal(Location destination, HousePortal portal)
	{
		final Def def = new Def(destination, "Teleport to House (" + portal.getDisplayName() + " portal)",
			TravelKind.HOUSE_PORTAL);
		def.spell = Spell.TELEPORT_TO_HOUSE;
		def.requirements.add(Requirement.housePortal(portal));
		return def;
	}

	private static Def quetzal(Location destination, String displayName)
	{
		final Def def = new Def(destination, displayName, TravelKind.QUETZAL);
		def.item = TravelItem.QUETZAL_WHISTLE;
		return def;
	}

	private static final class Def
	{
		private final Location destination;
		private final String displayName;
		private final TravelKind kind;
		private Spell spell;
		private TravelItem item;
		private int coins;
		private final List<Requirement> requirements = new ArrayList<>();
		private PortalNexus.Destination nexus;
		private JewelleryBoxTier jewelleryBox;

		private Def(Location destination, String displayName, TravelKind kind)
		{
			this.destination = destination;
			this.displayName = displayName;
			this.kind = kind;
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
