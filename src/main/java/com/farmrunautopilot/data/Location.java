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
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis (Ortus Farm)"),
	// Allotments and hops (docs/plans/crop-types.md, Sprint 1a)
	PRIFDDINAS("Prifddinas"),
	LUMBRIDGE_HOPS("Lumbridge (north farm)"),
	SEERS_VILLAGE_HOPS("Seers' Village"),
	YANILLE_HOPS("Yanille"),
	ALDARIN_HOPS("Aldarin"),
	// Bushes, cactus, mushroom, belladonna and seaweed (Sprint 1b): the travel atlas's stops of the same names
	CHAMPIONS_GUILD("Champions' Guild"),
	RIMMINGTON("Rimmington"),
	ARDOUGNE_MONASTERY("Ardougne monastery"),
	ETCETERIA("Etceteria"),
	AL_KHARID("Al Kharid"),
	CANIFIS("Canifis"),
	DRAYNOR_MANOR("Draynor Manor"),
	// The belladonna patch, 84 tiles east of the Nemus Retreat tree patch in the same RuneLite region
	AUBURNVALE("Auburnvale"),
	FOSSIL_ISLAND_UNDERWATER("Fossil Island (underwater)"),
	// The special trees (Sprint 1c): the travel atlas's stops of the same names
	FOSSIL_ISLAND("Fossil Island"),
	LOCUS_OASIS("Locus Oasis"),
	ANGLERS_RETREAT("Anglers' Retreat"),
	TAI_BWO_WANNAI("Tai Bwo Wannai"),
	THE_GREAT_CONCH("The Great Conch"),
	PORT_SARIM("Port Sarim");

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
