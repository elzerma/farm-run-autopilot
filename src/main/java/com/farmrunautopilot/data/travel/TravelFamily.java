package com.farmrunautopilot.data.travel;

import lombok.Getter;

/**
 * The Travel tab's families of ways to travel (docs/plans/travel-rework.md, sprint T7, agreed with elzerma on
 * 2026-10-20), each turned on or off as a whole; the families with a list can also turn single ways off. Every family is
 * on to start with except home and minigame teleports, which have a cooldown (elzerma, 2026-10-17).
 */
@Getter
public enum TravelFamily
{
	SPELLS("Spells", true, true, "Teleport spells cast from your spellbook (Teleport to House is under House). Off: no "
		+ "spell is cast, though a tablet still can be"),
	TELEPORT_ITEMS("Teleport items", true, true, "Jewellery, capes, diary items, the quetzal whistle and every other "
		+ "teleport item you own. Off: none of them is used"),
	TABLETS("Tablets and scrolls", true, true, "Teleport tablets, scrolls and spheres you own. Off: none is broken, and "
		+ "teleports you can cast are cast from runes"),
	HOUSE("House", true, false, "Teleport to House by spell, tablet or cape, then your house's portals, jewellery box, "
		+ "fairy ring and spirit tree, as set in Account > My house. Off: your house isn't used"),
	FAIRY_RINGS("Fairy rings", true, true, "Fairy rings, reached on foot or after a teleport. Off: no ring is used, your "
		+ "house's included"),
	SPIRIT_TREES("Spirit trees", true, true, "Spirit trees, reached on foot or after a teleport. Off: no spirit tree is "
		+ "used, your house's included"),
	QUETZALS("Quetzals", true, true, "Renu from a quetzal pad (the quetzal whistle is a teleport item). Off: Renu isn't "
		+ "used"),
	BOATS("Boats and ships", true, true, "Charter ships and canoes, and the boats and ships on the way (Captain Barnaby "
		+ "to Brimhaven, Bill Teach to Mos Le'Harmless). Some cost coins, which the Farm run tab brings. Off: none is used"),
	GLIDERS("Gliders, balloons, carts", true, true, "Gnome gliders, hot air balloons, minecarts, magic carpets and "
		+ "magic mushtrees. Off: none is used"),
	COOLDOWN("Home and minigame teleports", false, true, "Home teleports and the minigame teleports in the grouping "
		+ "tab. On: one of each at most per run, once its cooldown is over (30 and 20 minutes). Off to start with"),
	AGILITY_SHORTCUTS("Agility shortcuts", true, false, "Shortcuts your Agility level allows. Off: the route goes the "
		+ "long way round");

	private final String displayName;
	/** On for anyone who hasn't changed it. */
	private final boolean onByDefault;
	/** Opens to a list of its ways, each with its own tick box; House and Agility shortcuts have none. */
	private final boolean listed;
	/** What it covers and what turning it off does, for the hover. */
	private final String description;

	TravelFamily(String displayName, boolean onByDefault, boolean listed, String description)
	{
		this.displayName = displayName;
		this.onByDefault = onByDefault;
		this.listed = listed;
		this.description = description;
	}
}
