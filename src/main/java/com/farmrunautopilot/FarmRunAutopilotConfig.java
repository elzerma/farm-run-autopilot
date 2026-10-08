package com.farmrunautopilot;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * Simple global settings: run guidance highlights and colours (SPEC 13.4, 14). Everything else is set in the
 * sidebar and stored per account.
 */
@ConfigGroup(FarmRunAutopilotConfig.GROUP)
public interface FarmRunAutopilotConfig extends Config
{
	String GROUP = "farmrunautopilot";

	@ConfigSection(
		name = "Run guidance",
		description = "Highlights and arrows shown during a run",
		position = 0
	)
	String GUIDANCE = "guidance";

	@ConfigItem(
		keyName = "highlightPatch",
		name = "Highlight the patch",
		description = "Outline the patch the current step is about",
		section = GUIDANCE,
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
		section = GUIDANCE,
		position = 2
	)
	default boolean hintArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightGardener",
		name = "Highlight the gardener",
		description = "Outline the gardener when the step is to pay them",
		section = GUIDANCE,
		position = 3
	)
	default boolean highlightGardener()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightItems",
		name = "Highlight items to use",
		description = "Outline the seed, compost, tool or teleport to use next in your inventory and equipment",
		section = GUIDANCE,
		position = 4
	)
	default boolean highlightItems()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "patchColour",
		name = "Patch colour",
		description = "Outline colour for the patch",
		section = GUIDANCE,
		position = 5
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
		section = GUIDANCE,
		position = 6
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
		section = GUIDANCE,
		position = 7
	)
	default Color itemColour()
	{
		return new Color(0, 255, 0, 220);
	}
}
