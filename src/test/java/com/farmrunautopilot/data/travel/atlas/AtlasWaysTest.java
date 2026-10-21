package com.farmrunautopilot.data.travel.atlas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.travel.TravelFamily;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** The Travel tab's families and single ways over the atlas's teleports, hops and transports (sprint T7). */
public class AtlasWaysTest
{
	private static final TravelAtlas ATLAS = TravelAtlas.get();

	@Test
	public void teleportsFallIntoTheirFamilies()
	{
		assertEquals(TravelFamily.SPELLS, AtlasWays.teleportFamily("TELEPORTATION_SPELL", "Camelot Teleport"));
		assertEquals(TravelFamily.HOUSE, AtlasWays.teleportFamily("TELEPORTATION_SPELL", "Teleport to House (Outside)"));
		assertEquals(TravelFamily.HOUSE, AtlasWays.teleportFamily("TELEPORTATION_ITEM", "Teleport to House tablet"));
		assertEquals(TravelFamily.HOUSE, AtlasWays.teleportFamily("TELEPORTATION_ITEM", "Construction cape: Home"));
		assertEquals(TravelFamily.TABLETS, AtlasWays.teleportFamily("TELEPORTATION_ITEM", "Varrock tablet: GE"));
		assertEquals(TravelFamily.TABLETS, AtlasWays.teleportFamily("TELEPORTATION_ITEM", "Nardah teleport"));
		assertEquals(TravelFamily.TABLETS, AtlasWays.teleportFamily("TELEPORTATION_ITEM", "Dorgesh-kaan sphere"));
		assertEquals(TravelFamily.TELEPORT_ITEMS, AtlasWays.teleportFamily("TELEPORTATION_ITEM",
			"Skills necklace: Farming Guild"));
		assertEquals(TravelFamily.TELEPORT_ITEMS, AtlasWays.teleportFamily("TELEPORTATION_ITEM", "Crafting cape: Teleport"));
		assertEquals(TravelFamily.TELEPORT_ITEMS, AtlasWays.teleportFamily("QUETZAL_WHISTLE", "Quetzal whistle: Kastori"));
		assertEquals(TravelFamily.COOLDOWN, AtlasWays.teleportFamily("TELEPORTATION_MINIGAME",
			"Tithe Farm Minigame Teleport"));
		assertEquals(TravelFamily.COOLDOWN, AtlasWays.teleportFamily("TELEPORTATION_SPELL_HOME", "Lumbridge Home Teleport"));
		assertEquals(TravelFamily.HOUSE, AtlasWays.teleportFamily("TELEPORTATION_BOX", "Skills necklace: Farming Guild"));
	}

	@Test
	public void everyTeleportAndHopHasAFamilyButTheObelisks()
	{
		for (TravelAtlas.Jump t : ATLAS.getTeleports())
		{
			assertTrue(t.getName(), AtlasWays.family(t) != null);
		}
		for (TravelAtlas.Jump hop : ATLAS.getHops())
		{
			assertEquals(hop.getType(), hop.getType().equals("WILDERNESS_OBELISK"), AtlasWays.family(hop) == null);
		}
		assertEquals(TravelFamily.BOATS, AtlasWays.stepFamily("SHIP"));
		assertEquals(TravelFamily.AGILITY_SHORTCUTS, AtlasWays.stepFamily("AGILITY_SHORTCUT"));
		assertNull(AtlasWays.stepFamily("TRANSPORT"));
	}

	@Test
	public void keysAndNamesLeaveOutMenuNumbers()
	{
		assertEquals("Xeric's talisman: Xeric's Lookout", AtlasWays.teleportName("Xeric's talisman: 1. Xeric's Lookout"));
		assertEquals("Teleport to House", AtlasWays.teleportName("Teleport to House (Outside)"));
		assertEquals("Desert amulet 4: Nardah", AtlasWays.teleportName("Desert amulet 4: Nardah"));
		final TravelAtlas.Jump tree = hop("SPIRIT_TREE", "Gnome Stronghold");
		assertEquals("SPIRIT_TREE|Gnome Stronghold", AtlasWays.key(tree));
		assertEquals("Gnome Stronghold", AtlasWays.name(ATLAS, tree));
		assertEquals("Spirit tree (Gnome Stronghold)", AtlasWays.label(AtlasWays.key(tree)));
		final TravelAtlas.Jump ring = hop("FAIRY_RING", "CKR");
		assertEquals("FAIRY_RING|CKR", AtlasWays.key(ring));
		assertEquals("Fairy ring CKR", AtlasWays.label(AtlasWays.key(ring)));
		// Where the ring is, from the trinket of fairies' teleports
		assertEquals("CKR (South of Tai Bwo Wannai)", AtlasWays.name(ATLAS, ring));
	}

	@Test
	public void familyKeysTurnOffAFamily()
	{
		final String house = AtlasWays.familyKey(TravelFamily.HOUSE);
		assertEquals(TravelFamily.HOUSE, AtlasWays.keyFamily(house));
		assertEquals("your house", AtlasWays.label(house));
		assertEquals("boats and ships", AtlasWays.label(AtlasWays.familyKey(TravelFamily.BOATS)));
		assertNull(AtlasWays.keyFamily("FAIRY_RING|CKR"));
		assertNull(AtlasWays.keyFamily("FAMILY|NO_SUCH_FAMILY"));
	}

	@Test
	public void choicesAllowWhatsOn()
	{
		final TravelAtlas.Jump tree = hop("SPIRIT_TREE", "Gnome Stronghold");
		final TravelAtlas.Jump other = hop("SPIRIT_TREE", "Grand Exchange");
		assertTrue(AtlasWays.DEFAULT.allows(tree));
		assertFalse(AtlasWays.DEFAULT.isOn(TravelFamily.COOLDOWN));
		assertTrue(AtlasWays.DEFAULT.avoidsWilderness());
		assertFalse(AtlasWays.DEFAULT.typeOff("SPIRIT_TREE"));

		final Set<String> off = new HashSet<>(Collections.singleton(AtlasWays.key(tree)));
		final AtlasWays one = new AtlasWays(EnumSet.noneOf(TravelFamily.class), off, true);
		assertFalse(one.allows(tree));
		assertTrue(one.allows(other));

		final AtlasWays noTrees = new AtlasWays(EnumSet.of(TravelFamily.SPIRIT_TREES), Collections.emptySet(), true);
		assertFalse(noTrees.allows(other));
		assertTrue(noTrees.typeOff("SPIRIT_TREE"));
		final AtlasWays noBoats = new AtlasWays(EnumSet.of(TravelFamily.BOATS), Collections.emptySet(), true);
		assertTrue(noBoats.typeOff("CHARTER_SHIP") && noBoats.typeOff("SHIP") && noBoats.typeOff("BOAT"));
	}

	/** A hop to this place (a spirit tree's destination, a fairy ring's code). */
	private static TravelAtlas.Jump hop(String type, String to)
	{
		for (TravelAtlas.Jump hop : ATLAS.getHops())
		{
			if (hop.getType().equals(type) && AtlasWays.destination(hop).equals(to))
			{
				return hop;
			}
		}
		throw new AssertionError("No " + type + " to " + to);
	}
}
