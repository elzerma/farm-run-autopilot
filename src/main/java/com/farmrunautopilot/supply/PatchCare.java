package com.farmrunautopilot.supply;

import com.farmrunautopilot.settings.Compost;
import lombok.Value;

/**
 * How a patch is looked after this run (elzerma, 2026-10-18): whether its gardener is paid, and which compost it gets.
 * Worked out with the supplies, from the patch type's own choices falling back down Pay → ultracompost → supercompost
 * → compost to the best the player has, so the guide asks for what the supply list brought.
 */
@Value
public class PatchCare
{
	/** The gardener is paid to protect it. */
	boolean paid;
	/** The compost it gets, or {@link Compost#NONE}. */
	Compost compost;
}
