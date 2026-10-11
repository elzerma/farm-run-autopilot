package com.farmrunautopilot.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Places a run travels to. A location can hold several patches (SPEC 5.1); its patches are listed in
 * {@link Patch}. Distances (banks, walks between stops) are in {@link com.farmrunautopilot.data.travel.TravelTimes}.
 */
@Getter
public enum Location
{
	LUMBRIDGE("Lumbridge"),
	VARROCK("Varrock"),
	FALADOR_PARK("Falador Park"),
	TAVERLEY("Taverley"),
	GNOME_STRONGHOLD("Gnome Stronghold"),
	FARMING_GUILD("Farming Guild"),
	NEMUS_RETREAT("Nemus Retreat"),
	TREE_GNOME_VILLAGE("Tree Gnome Village"),
	// Herb patch (north farm) and fruit tree (east beach) treated as one stop.
	CATHERBY("Catherby"),
	BRIMHAVEN("Brimhaven"),
	LLETYA("Lletya"),
	KASTORI("Kastori"),
	FALADOR_FARM("Falador farm (south)"),
	PORT_PHASMATYS("Port Phasmatys"),
	ARDOUGNE_FARM("Ardougne farm (north)"),
	HOSIDIUS("Hosidius"),
	TROLL_STRONGHOLD("Troll Stronghold"),
	HARMONY_ISLAND("Harmony Island"),
	WEISS("Weiss"),
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis (Ortus Farm)");

	private final String displayName;

	Location(String displayName)
	{
		this.displayName = displayName;
	}

	public List<Patch> getPatches()
	{
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (patch.getLocation() == this)
			{
				patches.add(patch);
			}
		}
		return Collections.unmodifiableList(patches);
	}
}
