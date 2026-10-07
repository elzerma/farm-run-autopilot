package com.farmrunautopilot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Account unlocks that are not a plain quest, diary or skill level. Milestone M3 maps each one to
 * automatic detection where a varbit is known, otherwise to a manual toggle in the Setup tab.
 */
@Getter
@RequiredArgsConstructor
public enum Unlock
{
	FAIRY_RINGS("Fairy rings (Fairytale II started; dramen or lunar staff unless Elite Lumbridge diary)"),
	SPIRIT_TREES("Spirit trees (Tree Gnome Village)"),
	SPIRIT_TREE_PORT_SARIM("Spirit tree planted at Port Sarim (83 Farming)"),
	SPIRIT_TREE_BRIMHAVEN("Spirit tree planted at Brimhaven (83 Farming)"),
	SPIRIT_TREE_FARMING_GUILD("Spirit tree planted at the Farming Guild (85 Farming)"),
	QUETZAL_KASTORI("Kastori quetzal landing site built"),
	ATES_STATUE_NEMUS_RETREAT("Statue of Ates activated at Nemus Retreat"),
	ATES_STATUE_NORTH_KASTORI("Statue of Ates activated north of Kastori"),
	FIRE_OF_NOURISHMENT("Fire of Nourishment built at Weiss"),
	FORTIS_CHAMPION("Champion rank at the Fortis Colosseum");

	private final String description;
}
