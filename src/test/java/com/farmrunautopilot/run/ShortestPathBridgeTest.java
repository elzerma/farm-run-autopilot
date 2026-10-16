package com.farmrunautopilot.run;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The walk-only overrides only work if every key and value is one Shortest Path knows: an unknown one is
 * ignored and the player's own setting wins (the "NONE" vs "None" bug sent elzerma to a bank, 2026-10-11).
 */
public class ShortestPathBridgeTest
{
	/** Shortest Path's transport and bank config keys at commit 7e81a40 (ShortestPathConfig keyName). */
	private static final Set<String> KNOWN_KEYS = new HashSet<>(Arrays.asList(
		"highlightBankPickupItems", "highlightInventoryItems", "highlightSpellbookSpells", "includeBankPath",
		"showBankPickupInfo", "useAgilityShortcuts", "useBoats", "useCanoes", "useCharterShips", "useFairyRings",
		"useGnomeGliders", "useGrappleShortcuts", "useHotAirBalloons", "useMagicCarpets", "useMagicMushtrees",
		"useMinecarts", "usePoh", "usePohFairyRing", "usePohMountedItems", "usePohObelisk", "usePohSpiritTree",
		"useQuetzals", "useSailingMoves", "useSeasonalTransports", "useShips", "useSpiritTrees",
		"useTeleportationItems", "useTeleportationLevers", "useTeleportationMinigames", "useTeleportationPortals",
		"useTeleportationPortalsPoh", "useTeleportationSpells", "useTeleportationSpellsHome",
		"useWildernessObelisks"));
	/** Its TeleportationItem names, matched exactly. */
	private static final Set<String> TELEPORTATION_ITEMS = new HashSet<>(Arrays.asList("None", "Inventory",
		"Inventory (perm)", "Inventory and Bank", "Inventory and Bank (perm)", "Unlocked", "Unlocked (perm)", "All",
		"All (perm)"));

	@Test
	public void everyOverrideIsAKeyShortestPathKnows()
	{
		for (String key : ShortestPathBridge.WALK_ONLY.keySet())
		{
			assertTrue(key, KNOWN_KEYS.contains(key));
		}
	}

	@Test
	public void teleportItemsAreOffByTheirExactName()
	{
		final Object value = ShortestPathBridge.WALK_ONLY.get("useTeleportationItems");
		assertEquals("None", value);
		assertTrue(TELEPORTATION_ITEMS.contains(value));
	}

	@Test
	public void everyWayToTravelButWalkingIsOff()
	{
		// Agility shortcuts are walking, so they stay as the player has them
		for (String key : KNOWN_KEYS)
		{
			if (key.startsWith("use") && !key.equals("useAgilityShortcuts") && !key.equals("useTeleportationItems"))
			{
				final Map<String, Object> overrides = ShortestPathBridge.WALK_ONLY;
				assertEquals(key, Boolean.FALSE, overrides.get(key));
			}
		}
	}
}
