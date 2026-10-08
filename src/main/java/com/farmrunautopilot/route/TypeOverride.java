package com.farmrunautopilot.route;

/**
 * The player's one-off choice for a run type, overriding the due threshold (SPEC 10).
 */
public enum TypeOverride
{
	/** Include it even though too few patches are due. */
	INCLUDE,
	/** Leave it out even though it is due. */
	SKIP
}
