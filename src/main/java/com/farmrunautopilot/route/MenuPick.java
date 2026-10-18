package com.farmrunautopilot.route;

import lombok.Value;

/**
 * A place to pick on a travel map on the way to a stop (elzerma, 2026-10-18: the vine on Renu's, the whistle's and the
 * balloon's buttons), named as the map names it.
 */
@Value
public class MenuPick
{
	public enum Menu
	{
		/** Renu's map, at a quetzal landing pad. */
		RENU,
		/** The quetzal whistle's map. */
		WHISTLE,
		/** A hot air balloon's map. */
		BALLOON
	}

	Menu menu;
	/** e.g. "Kastori", "Hunter Guild", "Taverley". */
	String place;
}
