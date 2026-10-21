package com.farmrunautopilot;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Global settings. The run guidance highlights and colours (SPEC 13.4) are hidden here and edited in the sidebar's
 * Account tab with everything else; the only visible item opens the sidebar.
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
		description = "All Farm Run Autopilot settings are in its sidebar panel. Each click here opens it.",
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
		description = "Point the game's hint arrow (also on the minimap) at the patch",
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
		description = "Outline the gardener when paying them, and the quetzal after the Civitas teleport",
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
		description = "Outline the teleport (or Cure Plant) to cast next in your spellbook, and where to go on a travel map",
		hidden = true,
		position = 5
	)
	default boolean highlightSpell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "vineHighlights",
		name = "Vine highlights",
		description = "A growing vine round the spell to cast, the place to pick on a travel map, the bank's Farm run "
			+ "button and its Deposit inventory button, instead of a plain outline",
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
		description = "Ask the Shortest Path plugin, if installed, to draw the walk for each \"walk to\" step",
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
		description = "Outline colour for the patch",
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
		name = "Gardener colour",
		description = "Outline colour for the gardener",
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
		description = "Outline colour for items to use",
		hidden = true,
		position = 8
	)
	default Color itemColour()
	{
		return new Color(0, 255, 0, 220);
	}
}
