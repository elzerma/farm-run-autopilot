package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Test;

/**
 * A spell's other places and Teleport to House's Inside and Outside are picked on its right-click (elzerma,
 * 2026-10-23): the stop says which, as an item's place is, and leaves out walking out of the house when Teleport to
 * House already lands by its portal.
 */
public class RightClickOptionTest
{
	@Test
	public void theOptionFromTheAtlasName()
	{
		assertEquals("Seers'", AtlasTravel.spellOption(teleport("Camelot Teleport: Seers'")));
		assertEquals("Yanille", AtlasTravel.spellOption(teleport("Watchtower Teleport: Yanille")));
		assertEquals("Grand Exchange", AtlasTravel.spellOption(teleport("Varrock Teleport: Grand Exchange")));
		// Shortest Path's "(Outside)" rows are Teleport to House's Outside, on the spell's and the tablet's right-click
		assertEquals("Outside", AtlasTravel.spellOption(teleport("Teleport to House (Outside)")));
		assertEquals("Outside", AtlasTravel.spellOption(teleport("Teleport to House tablet (Outside)")));
		assertNull(AtlasTravel.spellOption(teleport("Camelot Teleport")));
		assertNull(AtlasTravel.spellOption(teleport("Teleport to House")));
		// An item's place is its own option, not a spell's
		assertNull(AtlasTravel.spellOption(teleport("Skills necklace: Crafting Guild")));
	}

	@Test
	public void theWayNamesItsOption()
	{
		final RouteStop outside = stop(Location.HOSIDIUS, TravelMethod.HOUSE_PORTAL_HOSIDIUS, Departure.DIRECT, "Outside");
		assertEquals("Teleport to House: Outside (Hosidius portal)", outside.describeWay());
		assertEquals("Outside", outside.teleportOption());
		// Already by the portal, so nothing about leaving the house after it
		assertTrue(outside.rides().isEmpty());
		assertEquals(outside.describeWay(), outside.describeTravel());
		// A spirit tree into the house still leaves through its exit portal
		assertEquals(Collections.singletonList(JourneyStep.LEAVE_HOUSE), new RouteStop(Location.HOSIDIUS,
			TravelMethod.HOUSE_PORTAL_HOSIDIUS, Departure.TREE_HOUSE_PORTAL, 30, false).rides());

		final RouteStop seers = stop(Location.SEERS_VILLAGE_HOPS, TravelMethod.CAMELOT_TELEPORT_SEERS_HOPS,
			Departure.DIRECT, "Seers'");
		assertEquals("Camelot Teleport: Seers'", seers.describeWay());
		assertEquals("Seers'", seers.teleportOption());

		final RouteStop inside = stop(Location.CATHERBY, TravelMethod.CATHERBY_TELEPORT, Departure.POH_NEXUS, "Inside");
		assertEquals("Teleport to House: Inside, then house portal nexus: Catherby Teleport", inside.describeWay());
		assertEquals("Inside", inside.teleportOption());
	}

	@Test
	public void toAHousesPortalByOutside()
	{
		// A Hosidius house set to teleport inside: to the Hosidius patch by Teleport to House's Outside, landing by the
		// portal, not walking out of the house
		final RouteStop stop = between(Location.HOSIDIUS, player(HousePortal.HOSIDIUS.getGameValue(), false,
			Collections.emptySet()), new PohSetup());
		assertEquals(stop.getBreakdown(), "Outside", stop.teleportOption());
		assertTrue(stop.describeTravel(), stop.describeTravel().startsWith("Teleport to House: Outside"));
		assertFalse(stop.describeTravel(), stop.describeTravel().contains("out of your house"));
	}

	@Test
	public void throughAHouseSetToTeleportOutsideByInside()
	{
		// A Rimmington house set to teleport outside, Catherby on its nexus: in by Teleport to House's Inside
		final PohSetup poh = new PohSetup();
		poh.setNexusDestinations(EnumSet.of(PortalNexus.Destination.CATHERBY));
		final RouteStop stop = between(Location.CATHERBY, player(HousePortal.RIMMINGTON.getGameValue(), true,
			Collections.emptySet()), poh);
		assertEquals(stop.getBreakdown(), "Inside", stop.teleportOption());
		assertTrue(stop.describeTravel(), stop.describeTravel().startsWith("Teleport to House: Inside, then "));
	}

	@Test
	public void seersVillageHopsBySeersOnCamelotTeleport()
	{
		// With the hard Kandarin diary, Camelot Teleport's Seers' lands by Seers' bank, nearer the hops than Camelot
		final RouteStop stop = between(Location.SEERS_VILLAGE_HOPS, player(0, false, Collections.singleton(
			AccessSnapshot.diaryKey(AchievementDiary.KANDARIN, AchievementDiary.Tier.HARD))), new PohSetup());
		assertEquals(stop.getBreakdown(), "Seers'", stop.teleportOption());
		assertTrue(stop.describeTravel(), stop.describeTravel().startsWith("Camelot Teleport: Seers'"));
	}

	private static RouteStop stop(Location location, TravelMethod method, Departure departure, String option)
	{
		return new RouteStop(location, method, departure, 20, false, "", null, Collections.emptyList(), option);
	}

	private static RouteStop between(Location to, AccessSnapshot access, PohSetup poh)
	{
		final RunConfig config = new RunConfig().sanitise();
		return RoutePlanner.between(null, to, config, access, Holdings.EMPTY, poh,
			AtlasPlayers.with(access, Holdings.EMPTY, Map.of(), RunService.ways(config), Map.of(), 0));
	}

	/** 99 everywhere, no quests, on the standard spellbook, with these diaries and a house there (0: none). */
	private static AccessSnapshot player(int houseLocation, boolean teleportOutside, Set<String> diaries)
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		return new AccessSnapshot(true, Collections.emptyMap(), levels, diaries, Collections.emptySet(), houseLocation,
			teleportOutside, Spellbook.STANDARD, Collections.emptySet(), 0, 0, false);
	}

	private static TravelAtlas.Jump teleport(String name)
	{
		for (TravelAtlas.Jump t : AtlasPlayers.ATLAS.getTeleports())
		{
			if (t.getName().equals(name))
			{
				return t;
			}
		}
		throw new AssertionError("No teleport " + name);
	}
}
