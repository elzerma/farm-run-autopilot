package com.farmrunautopilot;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Global settings. The run guidance highlights and colours (SPEC 13.4) are hidden here and edited in the sidebar's
 * Settings > Display with everything else, so each is in one place; the only visible item opens the sidebar. The hidden
 * items' names and descriptions are the sidebar's (docs/plans/fresh-eyes-audit.md, C3).
 */
@ConfigGroup(FarmRunAutopilotConfig.GROUP)
public interface FarmRunAutopilotConfig extends Config
{
	String GROUP = "farmrunautopilot";
	String OPEN_SIDEBAR_KEY = "openSidebar";

	/** Works like a button: any click (tick or untick) opens the sidebar. */
	@ConfigItem(
		keyName = OPEN_SIDEBAR_KEY,
		name = "Click to open the sidebar",
		description = "Every setting is in the Farm Run Autopilot panel. Click here, or its icon in RuneLite's sidebar, to "
			+ "open it.",
		position = 0
	)
	default boolean openSidebar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightPatch",
		name = "Highlight the patch",
		description = "Outline the patch the current step is about",
		hidden = true,
		position = 1
	)
	default boolean highlightPatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hintArrow",
		name = "Hint arrow to the patch",
		description = "The game's hint arrow over the patch, also shown on the minimap",
		hidden = true,
		position = 2
	)
	default boolean hintArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightGardener",
		name = "Highlight NPCs",
		description = "Outline the gardener when paying them, the tool leprechaun when he's needed, and the NPC or object to "
			+ "use on the way (Renu, a quetzal, a boat, a portal)",
		hidden = true,
		position = 3
	)
	default boolean highlightGardener()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightItems",
		name = "Highlight items to use",
		description = "Outline the seed, compost, tool or teleport to use next in your inventory and equipment, and the "
			+ "teleport's option to pick in its right-click menu or list of places",
		hidden = true,
		position = 4
	)
	default boolean highlightItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSpell",
		name = "Highlight the spell or place to pick",
		description = "Outline the teleport (or Cure Plant) to cast next in your spellbook, and where to go on Renu's, the "
			+ "quetzal whistle's or a hot air balloon's map. In the item colour.",
		hidden = true,
		position = 5
	)
	default boolean highlightSpell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "vineHighlights",
		name = "Growing vine highlights",
		description = "A vine that grows, flowers and wilts round the spell to cast or item to use (and round its tab's icon "
			+ "while you're on another tab), the place to pick on a travel map, the bank's Farm run button and its Deposit "
			+ "inventory button. Untick for a plain outline.",
		hidden = true,
		position = 10
	)
	default boolean vineHighlights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shortestPathLines",
		name = "Walking lines (Shortest Path)",
		description = "If the Shortest Path plugin is installed, it draws the walk for each \"walk to\" step: walking only, no "
			+ "teleports. Your own Shortest Path settings come back when the step changes.",
		hidden = true,
		position = 9
	)
	default boolean shortestPathLines()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "patchColour",
		name = "Patch colour",
		description = "The patch, its tile and the compost bin",
		hidden = true,
		position = 6
	)
	default Color patchColour()
	{
		return new Color(0, 255, 255, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "npcColour",
		name = "NPC colour",
		description = "Gardeners, the tool leprechaun and what to use on the way",
		hidden = true,
		position = 7
	)
	default Color npcColour()
	{
		return new Color(255, 215, 0, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "itemColour",
		name = "Item colour",
		description = "Items, spells, menu options and places on a travel map",
		hidden = true,
		position = 8
	)
	default Color itemColour()
	{
		return new Color(0, 255, 0, 220);
	}
}
