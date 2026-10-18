package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.coords.WorldPoint;

/**
 * Ties an atlas teleport to the plugin's own travel item or spell (docs/plans/travel-rework.md, section 4), so the
 * planner can keep what it tracks for them: charges, daily uses, runes and tablets, Spellbook Swap and highlights. A
 * teleport with neither is planned from its needs alone. Also where each of the plugin's travel methods lands, so an
 * atlas teleport or hop landing there can be told apart from another of the same item (the skills necklace's five).
 */
public final class AtlasLinks
{
	/** A landing this close counts as the same (the atlas merges landings within 2 tiles). */
	public static final int SAME_LANDING = 3;

	private static final Map<Integer, TravelItem> ITEMS = new HashMap<>();
	private static final Map<Integer, Spell> TABLETS = new HashMap<>();
	private static final Map<String, Spell> SPELLS = new HashMap<>();
	/**
	 * Where each travel method lands (its teleport, ring, tree, pad or basket), from Shortest Path's data as measured in
	 * tools/walk/methods.tsv (raw_landing), 2026-10-13; the balloon from the atlas.
	 */
	private static final Map<TravelMethod, WorldPoint> LANDINGS = new EnumMap<>(TravelMethod.class);
	/** Each teleport's items and spell, worked out once (the planner asks for every teleport, for every plan). */
	private static final Map<TravelAtlas.Jump, List<TravelItem>> ITEMS_OF = new ConcurrentHashMap<>();
	private static final Map<TravelAtlas.Jump, Optional<Spell>> SPELL_OF = new ConcurrentHashMap<>();

	static
	{
		for (TravelItem item : TravelItem.values())
		{
			for (int id : item.getItemIds())
			{
				ITEMS.putIfAbsent(id, item);
			}
		}
		for (Spell spell : Spell.values())
		{
			SPELLS.put(spell.getDisplayName().toLowerCase(Locale.ROOT), spell);
			if (spell.hasTablet())
			{
				TABLETS.put(spell.getTabletItemId(), spell);
			}
		}
		landing(TravelMethod.LUMBRIDGE_TELEPORT, 3221, 3218, 0);
		landing(TravelMethod.LUMBRIDGE_HOME_TELEPORT, 3221, 3218, 0);
		landing(TravelMethod.DIARY_CAPE_LUMBRIDGE, 3234, 3214, 0);
		landing(TravelMethod.VARROCK_TELEPORT, 3213, 3424, 0);
		landing(TravelMethod.DIARY_CAPE_VARROCK, 3225, 3415, 0);
		landing(TravelMethod.SKILLS_NECKLACE_COOKS_GUILD, 3144, 3438, 0);
		landing(TravelMethod.RING_OF_WEALTH_GRAND_EXCHANGE, 3163, 3478, 0);
		landing(TravelMethod.SPIRIT_TREE_GRAND_EXCHANGE, 3185, 3508, 0);
		landing(TravelMethod.RING_OF_WEALTH_FALADOR_PARK, 2995, 3375, 0);
		landing(TravelMethod.FALADOR_TELEPORT_TO_PARK, 2964, 3378, 0);
		landing(TravelMethod.SKILLS_NECKLACE_MINING_GUILD, 3049, 9763, 0);
		landing(TravelMethod.HOUSE_PORTAL_TAVERLEY, 2893, 3465, 0);
		landing(TravelMethod.TAVERLEY_TABLET, 2893, 3465, 0);
		landing(TravelMethod.CONSTRUCTION_CAPE_TAVERLEY, 2892, 3465, 0);
		landing(TravelMethod.FALADOR_TELEPORT_TO_TAVERLEY, 2964, 3378, 0);
		landing(TravelMethod.HOT_AIR_BALLOON_TAVERLEY, 2941, 3420, 0);
		landing(TravelMethod.GAMES_NECKLACE_BURTHORPE, 2899, 3553, 0);
		landing(TravelMethod.COMBAT_BRACELET_WARRIORS_GUILD, 2882, 3547, 0);
		landing(TravelMethod.SLAYER_RING_STRONGHOLD_CAVE, 2431, 3422, 0);
		landing(TravelMethod.SPIRIT_TREE_GNOME_STRONGHOLD, 2461, 3444, 0);
		landing(TravelMethod.ROYAL_SEED_POD, 2465, 3495, 0);
		landing(TravelMethod.FARMING_CAPE, 1248, 3725, 0);
		landing(TravelMethod.SKILLS_NECKLACE_FARMING_GUILD, 1248, 3725, 0);
		landing(TravelMethod.MAX_CAPE_FARMING_GUILD, 1248, 3725, 0);
		landing(TravelMethod.SPIRIT_TREE_FARMING_GUILD, 1251, 3750, 0);
		landing(TravelMethod.FAIRY_RING_CIR, 1302, 3762, 0);
		landing(TravelMethod.BATTLEFRONT_TELEPORT, 1347, 3740, 0);
		landing(TravelMethod.RADAS_BLESSING_MOUNT_KARUULM, 1310, 3796, 0);
		landing(TravelMethod.PENDANT_OF_ATES_NEMUS_RETREAT, 1364, 3277, 0);
		landing(TravelMethod.QUETZAL_AUBURNVALE, 1411, 3361, 0);
		landing(TravelMethod.FAIRY_RING_AIS, 1429, 3324, 0);
		landing(TravelMethod.SPIRIT_TREE_TREE_GNOME_VILLAGE, 2542, 3170, 0);
		landing(TravelMethod.FAIRY_RING_CIQ, 2528, 3127, 0);
		landing(TravelMethod.CAMELOT_TELEPORT, 2757, 3479, 0);
		landing(TravelMethod.CATHERBY_TELEPORT, 2801, 3449, 0);
		landing(TravelMethod.HOUSE_PORTAL_BRIMHAVEN, 2757, 3178, 0);
		landing(TravelMethod.BRIMHAVEN_TABLET, 2757, 3179, 0);
		landing(TravelMethod.CONSTRUCTION_CAPE_BRIMHAVEN, 2756, 3176, 0);
		landing(TravelMethod.SPIRIT_TREE_BRIMHAVEN, 2800, 3203, 0);
		landing(TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT, 2662, 3305, 0);
		landing(TravelMethod.GLORY_KARAMJA, 2918, 3176, 0);
		landing(TravelMethod.TELEPORT_CRYSTAL_LLETYA, 2330, 3171, 0);
		landing(TravelMethod.QUETZAL_KASTORI, 1344, 3022, 0);
		landing(TravelMethod.PENDANT_OF_ATES_NORTH_KASTORI, 1366, 3086, 0);
		landing(TravelMethod.EXPLORERS_RING_CABBAGE_PATCH, 3052, 3291, 0);
		landing(TravelMethod.DRAYNOR_MANOR_TELEPORT, 3109, 3351, 0);
		landing(TravelMethod.SPIRIT_TREE_PORT_SARIM, 3058, 3257, 0);
		landing(TravelMethod.GLORY_DRAYNOR, 3105, 3251, 0);
		landing(TravelMethod.FALADOR_TELEPORT_TO_FARM, 2964, 3378, 0);
		landing(TravelMethod.FAIRY_RING_ALQ, 3597, 3495, 0);
		landing(TravelMethod.ECTOPHIAL, 3660, 3522, 0);
		landing(TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT, 3548, 3529, 0);
		landing(TravelMethod.KHARYRLL_TELEPORT, 3494, 3473, 0);
		landing(TravelMethod.ARDOUGNE_CLOAK_FARM, 2665, 3374, 0);
		landing(TravelMethod.SKILLS_NECKLACE_FISHING_GUILD, 2611, 3390, 0);
		landing(TravelMethod.MAX_CAPE_FISHING_GUILD, 2604, 3401, 0);
		landing(TravelMethod.FISHING_GUILD_TELEPORT, 2613, 3391, 0);
		landing(TravelMethod.COMBAT_BRACELET_RANGING_GUILD, 2653, 3439, 0);
		landing(TravelMethod.FAIRY_RING_BLR, 2740, 3351, 0);
		landing(TravelMethod.ARDOUGNE_TELEPORT, 2662, 3305, 0);
		landing(TravelMethod.XERICS_TALISMAN_GLADE, 1752, 3566, 0);
		landing(TravelMethod.HOUSE_PORTAL_HOSIDIUS, 1743, 3517, 0);
		landing(TravelMethod.HOSIDIUS_TABLET, 1743, 3517, 0);
		landing(TravelMethod.CONSTRUCTION_CAPE_HOSIDIUS, 1743, 3517, 0);
		landing(TravelMethod.KHAREDSTS_MEMOIRS_LANCALLIUMS, 1714, 3611, 0);
		landing(TravelMethod.FAIRY_RING_AKR, 1826, 3540, 0);
		landing(TravelMethod.SKILLS_NECKLACE_WOODCUTTING_GUILD, 1662, 3505, 0);
		landing(TravelMethod.STONY_BASALT_ROOF, 2837, 3695, 0);
		landing(TravelMethod.STONY_BASALT_ENTRANCE, 2845, 3694, 0);
		landing(TravelMethod.TROLLHEIM_TELEPORT, 2890, 3679, 0);
		landing(TravelMethod.HARMONY_ISLAND_TELEPORT, 3797, 2866, 0);
		landing(TravelMethod.ICY_BASALT, 2846, 3938, 0);
		landing(TravelMethod.QUETZAL_HUNTER_GUILD, 1585, 3053, 0);
		landing(TravelMethod.HUNTER_CAPE, 1558, 3046, 0);
		landing(TravelMethod.MAX_CAPE_HUNTER_GUILD, 1558, 3046, 0);
		landing(TravelMethod.CIVITAS_ILLA_FORTIS_TELEPORT, 1681, 3136, 0);
		landing(TravelMethod.FAIRY_RING_AJP, 1651, 3010, 0);
		landing(TravelMethod.LUMBRIDGE_TELEPORT_HOPS, 3221, 3218, 0);
		landing(TravelMethod.VARROCK_TELEPORT_HOPS, 3213, 3424, 0);
		landing(TravelMethod.COMBAT_BRACELET_CHAMPIONS_GUILD, 3192, 3368, 0);
		landing(TravelMethod.CAMELOT_TELEPORT_SEERS_HOPS, 2757, 3479, 0);
		landing(TravelMethod.FAIRY_RING_ALS, 2644, 3495, 0);
		landing(TravelMethod.COMBAT_BRACELET_RANGING_GUILD_HOPS, 2653, 3439, 0);
		landing(TravelMethod.HOUSE_PORTAL_YANILLE, 2544, 3095, 0);
		landing(TravelMethod.FAIRY_RING_CIQ_HOPS, 2528, 3127, 0);
		landing(TravelMethod.HOUSE_PORTAL_ALDARIN, 1422, 2965, 0);
		landing(TravelMethod.FAIRY_RING_CKQ, 1359, 2941, 0);
		landing(TravelMethod.QUETZAL_ALDARIN, 1389, 2901, 0);
		landing(TravelMethod.TELEPORT_CRYSTAL_PRIFDDINAS, 3264, 6066, 0);
		landing(TravelMethod.HOUSE_PORTAL_PRIFDDINAS, 3239, 6076, 0);
	}

	private AtlasLinks()
	{
	}

	private static void landing(TravelMethod method, int x, int y, int plane)
	{
		LANDINGS.put(method, new WorldPoint(x, y, plane));
	}

	/** Where a travel method lands, or null if not known. */
	public static WorldPoint landing(TravelMethod method)
	{
		return LANDINGS.get(method);
	}

	/** Every travel method's landing, for tests. */
	public static Map<TravelMethod, WorldPoint> landings()
	{
		return Collections.unmodifiableMap(LANDINGS);
	}

	/** Two landings count as the same: on one plane, within {@link #SAME_LANDING} tiles. */
	public static boolean sameLanding(WorldPoint a, WorldPoint b)
	{
		return a != null && b != null && a.getPlane() == b.getPlane()
			&& Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) <= SAME_LANDING;
	}

	/** The plugin's teleport item this teleport uses (any item it needs that a TravelItem lists), or null. */
	public static TravelItem item(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		for (int id : itemIds(atlas, teleport))
		{
			final TravelItem item = ITEMS.get(id);
			if (item != null)
			{
				return item;
			}
		}
		return null;
	}

	/**
	 * Every one of the plugin's teleport items that can make this teleport, in the atlas's order: the Monastery
	 * teleport takes any Ardougne cloak, which the plugin tracks as two items (cloak 1, and cloaks 2 to 4).
	 */
	public static List<TravelItem> items(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		return ITEMS_OF.computeIfAbsent(teleport, t ->
		{
			final Set<TravelItem> items = new LinkedHashSet<>();
			for (int id : itemIds(atlas, t))
			{
				final TravelItem item = ITEMS.get(id);
				if (item != null)
				{
					items.add(item);
				}
			}
			return Collections.unmodifiableList(new ArrayList<>(items));
		});
	}

	/**
	 * The plugin's spell this teleport casts, matched by name ("Camelot Teleport: Seers'" is Camelot Teleport), or
	 * whose tablet it breaks; null if neither.
	 */
	public static Spell spell(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		return SPELL_OF.computeIfAbsent(teleport, t -> Optional.ofNullable(findSpell(atlas, t))).orElse(null);
	}

	private static Spell findSpell(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		if (teleport.getType().startsWith("TELEPORTATION_SPELL"))
		{
			String name = teleport.getName();
			final int colon = name.indexOf(':');
			name = colon >= 0 ? name.substring(0, colon) : name;
			final int bracket = name.indexOf(" (");
			name = bracket >= 0 ? name.substring(0, bracket) : name;
			return SPELLS.get(name.trim().toLowerCase(Locale.ROOT));
		}
		for (int id : itemIds(atlas, teleport))
		{
			final Spell spell = TABLETS.get(id);
			if (spell != null)
			{
				return spell;
			}
		}
		return null;
	}

	/** Every item id the teleport's item needs name (any branch). */
	private static int[] itemIds(TravelAtlas atlas, TravelAtlas.Jump teleport)
	{
		int count = 0;
		for (int needId : teleport.getNeeds())
		{
			final AtlasNeed need = atlas.need(needId);
			for (AtlasNeed.Branch branch : need.getBranches())
			{
				count += branch.getItems().length;
			}
		}
		final int[] ids = new int[count];
		int i = 0;
		for (int needId : teleport.getNeeds())
		{
			for (AtlasNeed.Branch branch : atlas.need(needId).getBranches())
			{
				for (int id : branch.getItems())
				{
					ids[i++] = id;
				}
			}
		}
		return ids;
	}
}
