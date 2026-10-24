package com.farmrunautopilot.data.travel.atlas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.TravelTimes;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.supply.Holdings;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/**
 * The shipped travel atlas (tools/atlas, docs/plans/travel-rework.md sprint T2) and legs built from it for pretend
 * players: the cases sprint T0 found the plugin's own tables get wrong.
 */
public class TravelAtlasTest
{
	private static final TravelAtlas ATLAS = TravelAtlas.get();
	/** A POH_HOUSE_LOCATION value the plugin doesn't know: a place the game added later. */
	private static final int NEW_PLACE = 99;

	@Test
	public void readsTheShippedAtlas()
	{
		assertFalse(ATLAS.isEmpty());
		assertTrue(ATLAS.getTeleports().size() > 500);
		assertTrue(ATLAS.getHops().size() > 1000);
		assertFalse(ATLAS.getNeeds().stream().anyMatch(n -> n.getKind() == AtlasNeed.Kind.UNKNOWN));
	}

	@Test
	public void everyPatchThePluginRunsCanBeReachedFromALanding()
	{
		for (Patch patch : PatchPoints.all().keySet())
		{
			final TravelAtlas.Place place = ATLAS.patchPlace(patch.name());
			assertNotNull(patch + " has no place in the atlas", place);
			assertTrue(patch + " has no way from a teleport or hop landing", ATLAS.into(place.getId()).stream()
				.anyMatch(from -> ATLAS.place(from).getKind() == TravelAtlas.Kind.LANDING));
		}
	}

	@Test
	public void anItemNeedIsAnyOneOfItsBranches()
	{
		final AtlasNeed necklace = AtlasNeed.parse("ITEMS", "11105=1;11107=1", "any skills necklace");
		assertEquals(2, necklace.getBranches().size());
		final AtlasNeed air = AtlasNeed.parse("ITEMS", "556|4695=3/1381|1397", "air runes");
		assertEquals(3, air.getBranches().get(0).getQuantity());
		assertEquals(2, air.getBranches().get(0).getCovers().length);
		final AtlasNeed unlock = AtlasNeed.parse("ITEMS", "unlock:KARAMJA_ELITE", "Karamja elite");
		assertEquals("KARAMJA_ELITE", unlock.getBranches().get(0).getUnlock());

		final AtlasAccount withSkillsNecklace3 = account(99, items(ItemID.JEWL_NECKLACE_OF_SKILLS_3, 1));
		assertTrue(withSkillsNecklace3.meets(necklace));
		assertFalse(withSkillsNecklace3.meets(air));
		assertTrue(account(99, items(1381, 1)).meets(air));
	}

	@Test
	public void hosidiusBySkillsNecklaceNeeds45AgilityForTheSteppingStone()
	{
		final Map<Integer, Integer> necklace = items(ItemID.JEWL_NECKLACE_OF_SKILLS_4, 1);
		final AtlasLegs.Leg with = new AtlasLegs(ATLAS, account(99, necklace)).quickest(-1, places("HOSIDIUS_HERB"));
		final AtlasLegs.Leg without = new AtlasLegs(ATLAS, account(44, necklace)).quickest(-1, places("HOSIDIUS_HERB"));
		assertNotNull(with);
		assertNotNull(without);
		assertTrue(with.getHow(), with.getHow().startsWith("Skills necklace"));
		// 122 tiles round instead of 79 and the stone (T0)
		assertTrue(with.getSeconds() + " against " + without.getSeconds(), without.getSeconds() > with.getSeconds() + 8);
	}

	@Test
	public void faladorParkToTaverleyGoesOverTheRocksWith66Agility()
	{
		final int park = ATLAS.patchPlace("FALADOR_TREE").getId();
		final AtlasLegs.Leg rocks = new AtlasLegs(ATLAS, account(99, Map.of())).quickest(park, places("TAVERLEY_TREE"));
		final AtlasLegs.Leg gate = new AtlasLegs(ATLAS, account(65, Map.of())).quickest(park, places("TAVERLEY_TREE"));
		assertNotNull(rocks);
		assertNotNull(gate);
		assertTrue(rocks.isWalking());
		assertTrue(rocks.getSeconds() + " against " + gate.getSeconds(), gate.getSeconds() > rocks.getSeconds() + 5);
	}

	@Test
	public void harmonyIslandWithoutItsTeleportGoesByBoatAndBrotherTranquility()
	{
		final Map<Integer, Integer> held = new HashMap<>(items(ItemID.ECTOPHIAL, 1));
		held.put(ItemID.ECTOTOKEN, 100);
		held.put(ItemID.COINS, 100_000);
		final AtlasLegs.Leg leg = new AtlasLegs(ATLAS, account(99, held)).quickest(-1, places("HARMONY_ISLAND_HERB"));
		assertNotNull("No way to Harmony Island without its teleport", leg);
		assertTrue(leg.getHow(), leg.getHow().contains("Brother Tranquility"));
	}

	@Test
	public void theHouseNexusPortalIsAWayOut()
	{
		// A Rimmington house teleported into by tablet (its location and "inside" setting as the game has them)
		final Map<Integer, Integer> held = items(ItemID.POH_TABLET_TELEPORTTOHOUSE, 10);
		final AtlasAccount account = new AtlasAccount(access(99, HousePortal.RIMMINGTON.getGameValue()), holdings(held),
			Map.of(VarbitID.POH_HOUSE_LOCATION, 1, VarbitID.POH_TELE_TOGGLE, 1), Map.of());
		final PohSetup poh = new PohSetup();
		poh.setNexusDestinations(EnumSet.of(PortalNexus.Destination.CATHERBY));
		final AtlasHouse house = AtlasHouse.of(ATLAS, account, poh);
		assertNotNull(house);
		assertTrue(house.getExits().stream().anyMatch(e -> e.getName().equals("nexus: Catherby Portal")));
		assertNotNull(house.getPortal());

		final AtlasLegs.Leg with = new AtlasLegs(ATLAS, account, house).quickest(-1, places("CATHERBY_HERB"));
		final AtlasLegs.Leg without = new AtlasLegs(ATLAS, account).quickest(-1, places("CATHERBY_HERB"));
		assertNotNull(with);
		assertTrue(with.getHow(), with.getHow().startsWith("House > nexus: Catherby Portal"));
		assertTrue(without == null || without.getSeconds() > with.getSeconds());

		// In a place the plugin doesn't know yet, the house has every way out but the front door; without one, none
		final AtlasAccount elsewhere = new AtlasAccount(access(99, NEW_PLACE), holdings(held), Map.of(), Map.of());
		final AtlasHouse unplaced = AtlasHouse.of(ATLAS, elsewhere, poh, 10.0, false);
		assertNotNull(unplaced);
		assertTrue(unplaced.getExits().stream().anyMatch(e -> e.getName().equals("nexus: Catherby Portal")));
		assertNull(unplaced.getPortal());
		assertNull(AtlasHouse.of(ATLAS, account(99, held), poh, 10.0, false));
	}

	@Test
	public void eachHousePortalIsWhereItsGameValueTeleportsTo()
	{
		// HousePortal's POH_HOUSE_LOCATION values against the atlas's Teleport to House rows (both Shortest Path's)
		for (HousePortal portal : HousePortal.values())
		{
			final TravelAtlas.Place door = AtlasHouse.portalPlace(ATLAS, portal);
			assertNotNull(portal.name(), door);
			boolean found = false;
			for (TravelAtlas.Jump t : ATLAS.getTeleports())
			{
				if (t.getName().equals("Teleport to House") && t.getTo() == door.getId())
				{
					for (int id : t.getNeeds())
					{
						final AtlasNeed need = ATLAS.need(id);
						found |= need.getKind() == AtlasNeed.Kind.VARBIT && need.getVar() == VarbitID.POH_HOUSE_LOCATION
							&& need.getValue() == portal.getGameValue();
					}
				}
			}
			assertTrue(portal.name(), found);
			assertEquals(portal, HousePortal.of(portal.getGameValue()));
		}
		assertNull(HousePortal.of(0));
	}

	@Test
	public void teleportToHouseLandingInsideWalksOutOfTheDoor()
	{
		// Teleport to House by tablet to a Hosidius house's portal: landing inside (the game's setting at 0) takes the
		// walk out of the door on top, landing outside (1) doesn't. Shortest Path names its two rows the other way
		// round, so the row in use doesn't say which (elzerma's guided test, 2026-10-22)
		assertEquals(TravelTimes.HOUSE_DOOR, houseTabletSeconds(false) - houseTabletSeconds(true), 1e-9);
	}

	/** Seconds for the Teleport to House tablet row this player can use: a Hosidius house, landing outside or not. */
	private static double houseTabletSeconds(boolean outside)
	{
		final AccessSnapshot base = access(99, HousePortal.HOSIDIUS.getGameValue());
		final AccessSnapshot access = new AccessSnapshot(true, base.getQuests(), base.getRealLevels(),
			base.getCompletedDiaries(), base.getUnlocks(), base.getHouseLocation(), outside, null, Collections.emptySet(),
			0, 0, false);
		final AtlasAccount account = new AtlasAccount(access, holdings(items(ItemID.POH_TABLET_TELEPORTTOHOUSE, 10)),
			Map.of(VarbitID.POH_HOUSE_LOCATION, access.getHouseLocation(), VarbitID.POH_TELE_TOGGLE, outside ? 1 : 0),
			Map.of());
		final AtlasLegs legs = new AtlasLegs(ATLAS, account);
		for (TravelAtlas.Jump t : ATLAS.getTeleports())
		{
			if (t.getName().startsWith("Teleport to House tablet") && legs.usable(t))
			{
				return legs.teleportTime(t);
			}
		}
		throw new AssertionError("No usable Teleport to House tablet " + (outside ? "outside" : "inside"));
	}

	@Test
	public void theBasaltsNeedMakingFriendsWithMyArmFinishedWhateverItsVarbitReads()
	{
		// Shortest Path checks MY2ARM_STATUS = 207, which isn't confirmed in game; the plugin checks the quest itself
		final AtlasNeed finished = ATLAS.getNeeds().stream().filter(n -> n.getKind() == AtlasNeed.Kind.VARBIT
			&& n.getVar() == VarbitID.MY2ARM_STATUS && "=".equals(n.getCheck()) && n.getValue() == 207).findFirst()
			.orElse(null);
		assertNotNull(finished);
		assertTrue(account(99, Map.of()).meets(finished));
		final AtlasAccount notDone = new AtlasAccount(access(99, Quest.MAKING_FRIENDS_WITH_MY_ARM), holdings(Map.of()),
			Map.of(VarbitID.MY2ARM_STATUS, 207), Map.of());
		assertFalse(notDone.meets(finished));
		assertEquals("Making Friends with My Arm", notDone.missing(finished));

		final AtlasLegs.Leg weiss = new AtlasLegs(ATLAS, account(99, items(ItemID.WEISS_TELEPORT_BASALT, 1)))
			.quickest(-1, places("WEISS_HERB"));
		assertNotNull(weiss);
		assertTrue(weiss.getHow(), weiss.getHow().startsWith("Icy basalt"));
	}

	@Test
	public void noWayGoesThroughTheCastleWarsRecruiters()
	{
		// They only take a player with no cape and nothing but combat items, into a team's waiting room
		for (TravelAtlas.Place from : ATLAS.getPlaces())
		{
			for (TravelAtlas.Place to : ATLAS.getPlaces())
			{
				for (TravelAtlas.Way way : ATLAS.ways(from.getId(), to.getId()))
				{
					for (int step : way.getSteps())
					{
						final String menu = ATLAS.step(step).getMenu();
						assertFalse(menu, menu.startsWith("Join Castle Wars "));
					}
				}
			}
		}
	}

	private static List<Integer> places(String patch)
	{
		return List.of(ATLAS.patchPlace(patch).getId());
	}

	private static Map<Integer, Integer> items(int id, int quantity)
	{
		final Map<Integer, Integer> items = new HashMap<>();
		items.put(id, quantity);
		return items;
	}

	/** Every quest done, every skill at {@code level}, no diaries or unlocks, holding these items in the bank. */
	private static AtlasAccount account(int level, Map<Integer, Integer> bank)
	{
		return new AtlasAccount(access(level), holdings(bank), Map.of(), Map.of());
	}

	/** Every quest done but these, every skill at {@code level}, no diaries or unlocks, no house. */
	private static AccessSnapshot access(int level, Quest... notDone)
	{
		return access(level, 0, notDone);
	}

	/** As {@link #access(int, Quest...)}, with the house where the game's POH_HOUSE_LOCATION puts it (0: none). */
	private static AccessSnapshot access(int level, int houseLocation, Quest... notDone)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, Arrays.asList(notDone).contains(quest) ? QuestState.NOT_STARTED : QuestState.FINISHED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			levels.put(skill, level);
		}
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(), EnumSet.noneOf(Unlock.class),
			houseLocation, false, null, Collections.emptySet(), 0, 0, false);
	}

	private static Holdings holdings(Map<Integer, Integer> bank)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}
}
