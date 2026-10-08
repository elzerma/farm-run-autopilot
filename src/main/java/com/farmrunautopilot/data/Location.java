package com.farmrunautopilot.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Places a run travels to. A location can hold several patches (SPEC 5.1); its patches are listed in
 * {@link Patch}.
 *
 * <p>Every v1 location has a tool leprechaun (confirmed in-game). Bank walks are estimates.
 */
@Getter
public enum Location
{
	LUMBRIDGE("Lumbridge", null, true, null),
	VARROCK("Varrock", Walk.LONG, true, null),
	FALADOR_PARK("Falador Park", Walk.MEDIUM, true, null),
	TAVERLEY("Taverley", null, true, null),
	// Bank is short from the tree patch, medium from the fruit tree.
	GNOME_STRONGHOLD("Gnome Stronghold", Walk.SHORT, true, Walk.MEDIUM),
	// Bank chest on site.
	FARMING_GUILD("Farming Guild", Walk.SHORT, true, Walk.MEDIUM),
	// Bank buffalo on site (verified on the wiki).
	NEMUS_RETREAT("Nemus Retreat", Walk.SHORT, true, null),
	TREE_GNOME_VILLAGE("Tree Gnome Village", null, true, null),
	// Herb patch (north farm) and fruit tree (east beach) treated as one stop.
	CATHERBY("Catherby", Walk.MEDIUM, true, Walk.MEDIUM),
	BRIMHAVEN("Brimhaven", null, true, null),
	LLETYA("Lletya", Walk.MEDIUM, true, null),
	KASTORI("Kastori", null, true, null),
	FALADOR_FARM("Falador farm (south)", null, true, null),
	PORT_PHASMATYS("Port Phasmatys", Walk.LONG, true, null),
	ARDOUGNE_FARM("Ardougne farm (north)", null, true, null),
	HOSIDIUS("Hosidius", null, true, null),
	TROLL_STRONGHOLD("Troll Stronghold", null, true, null),
	HARMONY_ISLAND("Harmony Island", null, true, null),
	WEISS("Weiss", null, true, null),
	CIVITAS_ILLA_FORTIS("Civitas illa Fortis (Ortus Farm)", null, true, null);

	private final String displayName;
	/** Walk to the nearest bank, or null if there is no practical bank nearby. */
	private final Walk bankWalk;
	private final boolean hasToolLeprechaun;
	/** Walk between this location's patches, or null if it has only one patch. */
	private final Walk internalWalk;

	Location(String displayName, Walk bankWalk, boolean hasToolLeprechaun, Walk internalWalk)
	{
		this.displayName = displayName;
		this.bankWalk = bankWalk;
		this.hasToolLeprechaun = hasToolLeprechaun;
		this.internalWalk = internalWalk;
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
