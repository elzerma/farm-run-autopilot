package com.farmrunautopilot.data;

import lombok.Getter;
import net.runelite.api.gameval.VarbitID;

/**
 * Account unlocks that are not a plain quest, diary or skill level. Some are read from the game (a quest, or
 * a varbit); the rest can't be, so the player ticks them in the Account tab.
 */
@Getter
public enum Unlock
{
	FAIRY_RINGS("Fairy rings (Fairytale II started; dramen or lunar staff unless Elite Lumbridge diary)"),
	SPIRIT_TREES("Spirit trees (Tree Gnome Village)"),
	SPIRIT_TREE_PORT_SARIM("Spirit tree planted at Port Sarim (83 Farming)"),
	SPIRIT_TREE_BRIMHAVEN("Spirit tree planted at Brimhaven (83 Farming)"),
	SPIRIT_TREE_FARMING_GUILD("Spirit tree planted at the Farming Guild (85 Farming)"),
	// The varbits below are UNVERIFIED (picked by name; non-zero taken as unlocked)
	QUETZAL_KASTORI("Kastori quetzal landing site built", VarbitID.QUETZAL_KASTORI),
	ATES_STATUE_NEMUS_RETREAT("Statue of Ates activated at Nemus Retreat", VarbitID.PENDANT_OF_ATES_AUBURN_FOUND),
	ATES_STATUE_NORTH_KASTORI("Statue of Ates activated north of Kastori", VarbitID.PENDANT_OF_ATES_TLATI_FOUND),
	FIRE_OF_NOURISHMENT("Fire of Nourishment built at Weiss"),
	FORTIS_CHAMPION("Champion rank at the Fortis Colosseum");

	private final String description;
	/** Non-zero once unlocked, or -1 if the game has no value the plugin can read. */
	private final int varbit;

	Unlock(String description)
	{
		this(description, -1);
	}

	Unlock(String description, int varbit)
	{
		this.description = description;
		this.varbit = varbit;
	}

	public boolean hasVarbit()
	{
		return varbit != -1;
	}
}
