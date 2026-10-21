package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.data.travel.TravelFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ways the player lets the route use (the Travel tab's families and single ways, docs/plans/travel-rework.md,
 * sprint T7), and which family each of the atlas's teleports, hops and transports belongs to. A single way is kept by a
 * key that stays the same when the atlas is rebuilt: its kind and its name, e.g. "TELEPORTATION_ITEM|Ectophial" or
 * "FAIRY_RING|CKR"; a whole family by "FAMILY|HOUSE".
 */
public final class AtlasWays
{
	private static final String FAMILY = "FAMILY|";
	/** Shortest Path's transport types each family covers, for the atlas's "any fairy ring" needs. */
	private static final Map<TravelFamily, Set<String>> TYPES = Map.of(
		TravelFamily.FAIRY_RINGS, Set.of("FAIRY_RING"),
		TravelFamily.SPIRIT_TREES, Set.of("SPIRIT_TREE"),
		TravelFamily.QUETZALS, Set.of("QUETZAL"),
		TravelFamily.BOATS, Set.of("SHIP", "BOAT", "CHARTER_SHIP", "CANOE"),
		TravelFamily.GLIDERS, Set.of("GNOME_GLIDER", "HOT_AIR_BALLOON", "MINECART", "MAGIC_CARPET", "MAGIC_MUSHTREE"));

	/** A new player's: every family but home and minigame teleports, nothing else off, the Wilderness avoided. */
	public static final AtlasWays DEFAULT = new AtlasWays(EnumSet.of(TravelFamily.COOLDOWN), Collections.emptySet(),
		true);
	/** Every family on and nothing off, the Wilderness avoided: for what a way would need if it were on. */
	public static final AtlasWays ALL = new AtlasWays(EnumSet.noneOf(TravelFamily.class), Collections.emptySet(), true);
	/** Where each fairy ring code is ("AIQ" is "Mudskipper Point"), from the trinket of fairies' teleports; once. */
	private static final Map<String, String> RING_PLACES = new ConcurrentHashMap<>();
	private static volatile boolean ringPlacesRead;

	private final Set<TravelFamily> off;
	private final Set<String> waysOff;
	private final boolean avoidWilderness;
	/** The transport types of the families that are off. */
	private final Set<String> typesOff = new HashSet<>();

	/**
	 * @param off             the families turned off
	 * @param waysOff         the single ways turned off, by {@link #key}
	 * @param avoidWilderness keep the route out of the Wilderness
	 */
	public AtlasWays(Set<TravelFamily> off, Set<String> waysOff, boolean avoidWilderness)
	{
		this.off = off.isEmpty() ? EnumSet.noneOf(TravelFamily.class) : EnumSet.copyOf(off);
		this.waysOff = Collections.unmodifiableSet(new HashSet<>(waysOff));
		this.avoidWilderness = avoidWilderness;
		for (TravelFamily family : this.off)
		{
			typesOff.addAll(TYPES.getOrDefault(family, Collections.emptySet()));
		}
	}

	public boolean isOn(TravelFamily family)
	{
		return !off.contains(family);
	}

	/** The player turned this single way off ("don't use this way"). */
	public boolean isOff(String key)
	{
		return waysOff.contains(key);
	}

	public boolean avoidsWilderness()
	{
		return avoidWilderness;
	}

	/** A teleport or hop the route may use: its family is on and the player hasn't turned it off. */
	public boolean allows(TravelAtlas.Jump jump)
	{
		final TravelFamily family = family(jump);
		return (family == null || isOn(family)) && !waysOff.contains(key(jump));
	}

	/** A transport on the way (a boat, an Agility shortcut) the route may use: its family is on. */
	public boolean allows(TravelAtlas.Step step)
	{
		final TravelFamily family = stepFamily(step.getType());
		return family == null || isOn(family);
	}

	/** One of Shortest Path's transport types (an atlas "any fairy ring" need) is off, as its family is. */
	public boolean typeOff(String type)
	{
		return typesOff.contains(type);
	}

	// Families

	/**
	 * The family of a teleport or hop, or null for those no family covers (a Wilderness obelisk, which only "Avoid the
	 * Wilderness" governs).
	 */
	public static TravelFamily family(TravelAtlas.Jump jump)
	{
		return jump.getFrom() >= 0 ? hopFamily(jump.getType()) : teleportFamily(jump.getType(), jump.getName());
	}

	/** The family of a teleport, by Shortest Path's type and the teleport's name. */
	static TravelFamily teleportFamily(String type, String name)
	{
		switch (type)
		{
			case "TELEPORTATION_SPELL":
				return name.startsWith("Teleport to House") ? TravelFamily.HOUSE : TravelFamily.SPELLS;
			case "TELEPORTATION_SPELL_HOME":
			case "TELEPORTATION_MINIGAME":
				return TravelFamily.COOLDOWN;
			case "TELEPORTATION_PORTAL_POH":
			case "TELEPORTATION_BOX":
				return TravelFamily.HOUSE;
			case "QUETZAL_WHISTLE":
				return TravelFamily.TELEPORT_ITEMS;
			default:
				if (name.startsWith("Teleport to House") || name.equals("Construction cape: Home")
					|| name.equals("Max cape: Tele to POH"))
				{
					return TravelFamily.HOUSE;
				}
				// Tablets ("Camelot tablet", "Varrock tablet: GE"), scrolls ("Nardah teleport") and spheres
				return name.contains(" tablet") || name.endsWith(" teleport") || name.startsWith("Master scroll book")
					|| name.endsWith(" sphere") ? TravelFamily.TABLETS : TravelFamily.TELEPORT_ITEMS;
		}
	}

	/** The family of a hop, by Shortest Path's type. */
	static TravelFamily hopFamily(String type)
	{
		switch (type)
		{
			case "FAIRY_RING":
				return TravelFamily.FAIRY_RINGS;
			case "SPIRIT_TREE":
				return TravelFamily.SPIRIT_TREES;
			case "QUETZAL":
				return TravelFamily.QUETZALS;
			case "CHARTER_SHIP":
			case "CANOE":
				return TravelFamily.BOATS;
			case "GNOME_GLIDER":
			case "HOT_AIR_BALLOON":
			case "MINECART":
			case "MAGIC_CARPET":
			case "MAGIC_MUSHTREE":
				return TravelFamily.GLIDERS;
			default:
				return null;
		}
	}

	/**
	 * The family of a transport on a way: boats, ships and Agility shortcuts; null for doors, stairs, portals and the
	 * like, which are always used.
	 */
	public static TravelFamily stepFamily(String type)
	{
		switch (type)
		{
			case "BOAT":
			case "SHIP":
				return TravelFamily.BOATS;
			case "AGILITY_SHORTCUT":
				return TravelFamily.AGILITY_SHORTCUTS;
			default:
				return null;
		}
	}

	// Keys and names

	/**
	 * What a leg uses that the player can turn off, by key, for the Run tab's "Don't use ...": its teleport and hop (a
	 * single way, or the whole family for one without a list, such as Teleport to House), the house, and boats or ships
	 * on the way, which go with their family.
	 */
	public static List<String> keys(TravelAtlas atlas, AtlasOption option)
	{
		final List<String> keys = new ArrayList<>();
		if (option.getTeleport() != null)
		{
			add(keys, option.getTeleport());
		}
		if (option.getKind() == AtlasOption.Kind.HOUSE || option.getKind() == AtlasOption.Kind.TREE_HOUSE)
		{
			addKey(keys, familyKey(TravelFamily.HOUSE));
		}
		if (option.getHop() != null)
		{
			add(keys, option.getHop());
		}
		for (TravelAtlas.Way way : option.ways())
		{
			for (int step : way.getSteps())
			{
				if (stepFamily(atlas.step(step).getType()) == TravelFamily.BOATS)
				{
					addKey(keys, familyKey(TravelFamily.BOATS));
				}
			}
		}
		return keys;
	}

	private static void add(List<String> keys, TravelAtlas.Jump jump)
	{
		final TravelFamily family = family(jump);
		if (family != null)
		{
			addKey(keys, family.isListed() ? key(jump) : familyKey(family));
		}
	}

	private static void addKey(List<String> keys, String key)
	{
		if (!keys.contains(key))
		{
			keys.add(key);
		}
	}

	/** The key a teleport or hop is turned off by, e.g. "TELEPORTATION_ITEM|Ectophial", "SPIRIT_TREE|Gnome Stronghold". */
	public static String key(TravelAtlas.Jump jump)
	{
		return jump.getType() + "|" + (jump.getFrom() >= 0 ? destination(jump) : teleportName(jump.getName()));
	}

	/** The key a whole family is turned off by, for the Run tab's "Don't use your house". */
	public static String familyKey(TravelFamily family)
	{
		return FAMILY + family.name();
	}

	/** The family a {@link #familyKey} turns off, or null for a single way's key. */
	public static TravelFamily keyFamily(String key)
	{
		if (!key.startsWith(FAMILY))
		{
			return null;
		}
		try
		{
			return TravelFamily.valueOf(key.substring(FAMILY.length()));
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/**
	 * A way's full name, from its key, as the Run tab names it: "Civitas illa Fortis Teleport", "Fairy ring CKR", "Spirit
	 * tree (Gnome Stronghold)", "your house".
	 */
	public static String label(String key)
	{
		final TravelFamily family = keyFamily(key);
		if (family != null)
		{
			return family == TravelFamily.HOUSE ? "your house" : family == TravelFamily.AGILITY_SHORTCUTS
				? family.getDisplayName() : family.getDisplayName().toLowerCase();
		}
		final int bar = key.indexOf('|');
		final String type = bar < 0 ? "" : key.substring(0, bar);
		final String name = key.substring(bar + 1);
		switch (type)
		{
			case "FAIRY_RING":
				return name.equals("ZANARIS") ? "Fairy ring to Zanaris" : "Fairy ring " + name;
			case "SPIRIT_TREE":
				return "Spirit tree (" + name + ")";
			case "QUETZAL":
				return "Renu (" + name + ")";
			case "CHARTER_SHIP":
				return "Charter ship (" + name + ")";
			case "CANOE":
				return "Canoe (" + name + ")";
			case "GNOME_GLIDER":
				return "Gnome glider (" + name + ")";
			case "HOT_AIR_BALLOON":
				return "Hot air balloon (" + name + ")";
			case "MINECART":
				return "Minecart (" + name + ")";
			case "MAGIC_CARPET":
				return "Magic carpet (" + name + ")";
			case "MAGIC_MUSHTREE":
				return "Magic mushtree (" + name + ")";
			default:
				return name;
		}
	}

	/**
	 * A way's name in its family's list, where the family says what it is: "CKR (South of Tai Bwo Wannai)" among the
	 * fairy rings, "Gnome Stronghold" among the spirit trees, the full name elsewhere.
	 */
	public static String name(TravelAtlas atlas, TravelAtlas.Jump jump)
	{
		if (jump.getFrom() < 0)
		{
			return teleportName(jump.getName());
		}
		final String to = destination(jump);
		switch (jump.getType())
		{
			case "FAIRY_RING":
			{
				if (to.equals("ZANARIS"))
				{
					return "Zanaris";
				}
				final String place = ringPlace(atlas, to);
				return place != null ? to + " (" + place + ")" : to;
			}
			case "SPIRIT_TREE":
			case "QUETZAL":
				return to;
			default:
				return label(key(jump));
		}
	}

	/**
	 * Where a hop goes: a fairy ring's code without its spaces ("CKR"; a ring dialled through others is "AIR, DLR, DJQ,
	 * AJS"), otherwise the place without a menu number ("2: Gnome Stronghold", "1. House on the Hill").
	 */
	static String destination(TravelAtlas.Jump hop)
	{
		final String to = hop.getName();
		if (hop.getType().equals("FAIRY_RING"))
		{
			return to.replace(" - ", ", ").replace(" ", "").replace(",", ", ");
		}
		return withoutNumber(to);
	}

	/** A teleport's name without Shortest Path's "(Outside)" and menu numbers ("Xeric's talisman: 1. Xeric's Lookout"). */
	static String teleportName(String name)
	{
		final String plain = name.replace(" (Outside)", "");
		final int colon = plain.indexOf(": ");
		return colon < 0 ? withoutNumber(plain) : plain.substring(0, colon + 2) + withoutNumber(plain.substring(colon + 2));
	}

	/** "2: Gnome Stronghold" or "1. House on the Hill" without the menu's number or letter. */
	private static String withoutNumber(String name)
	{
		if (name.length() > 3 && (name.charAt(1) == ':' || name.charAt(1) == '.') && name.charAt(2) == ' '
			&& Character.isLetterOrDigit(name.charAt(0)))
		{
			return name.substring(3);
		}
		return name;
	}

	/** Where a fairy ring code is, from the trinket of fairies' "Fairy Ring - AIQ (Mudskipper Point)"; null if unknown. */
	private static String ringPlace(TravelAtlas atlas, String code)
	{
		if (!ringPlacesRead)
		{
			for (TravelAtlas.Jump t : atlas.getTeleports())
			{
				final String name = t.getName();
				final int ring = name.indexOf("Fairy Ring - ");
				final int open = ring < 0 ? -1 : name.indexOf(" (", ring);
				if (open > ring && name.endsWith(")"))
				{
					RING_PLACES.putIfAbsent(name.substring(ring + "Fairy Ring - ".length(), open),
						name.substring(open + 2, name.length() - 1));
				}
			}
			ringPlacesRead = true;
		}
		return RING_PLACES.get(code);
	}
}
