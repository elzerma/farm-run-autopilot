package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.supply.SupplyPlan;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

/** elzerma asked not to be sent across spellbooks unless a house altar makes it practical. */
public class SpellbookRuleTest
{
	private static AccessSnapshot onBook(Spellbook book, Set<Spellbook> altar)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.PRIEST_IN_PERIL, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, 99);
		levels.put(Skill.FARMING, 99);
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(), Collections.emptySet(), null, book,
			altar);
	}

	private static RouteStop portPhasmatys(AccessSnapshot access)
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.PORT_PHASMATYS);
		return RoutePlanner.plan(Collections.singletonList(Patch.PORT_PHASMATYS_HERB), config, access, Holdings.EMPTY,
			new PohSetup(), AtlasPlayers.with(access, Holdings.EMPTY), null).getStops().get(0);
	}

	@Test
	public void spellsFromAnotherBookNeedAnAltar()
	{
		final AccessSnapshot standard = onBook(Spellbook.STANDARD, Collections.emptySet());
		assertTrue(standard.canCast(Spell.CAMELOT_TELEPORT));
		assertFalse(standard.canCast(Spell.FENKENSTRAINS_CASTLE_TELEPORT));

		final AccessSnapshot withAltar = onBook(Spellbook.STANDARD, PohAltar.DARK.getSpellbooks());
		assertTrue(withAltar.canCast(Spell.FENKENSTRAINS_CASTLE_TELEPORT));
		assertFalse(withAltar.isOnSpellbook(Spell.FENKENSTRAINS_CASTLE_TELEPORT));
	}

	@Test
	public void unreachableSpellIsATabletNotAnAltarSwitch()
	{
		// GitHub #2: standard book, no altar, runes preferred, and Port Phasmatys pinned to an Arceuus spell (as a
		// guided test can)
		final RunConfig config = new RunConfig().sanitise();
		config.setEnabledTypes(java.util.EnumSet.of(com.farmrunautopilot.data.PatchType.HERB));
		for (Patch patch : Patch.values())
		{
			if (patch != Patch.PORT_PHASMATYS_HERB)
			{
				config.getDisabledPatches().add(patch);
			}
		}
		config.getTestTravel().put(Location.PORT_PHASMATYS, TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT);
		config.setUseRunesNotTabs(true);
		final AccessSnapshot access = onBook(Spellbook.STANDARD, Collections.emptySet());

		final RunSelection selection = RunSelector.select(config, access, p -> null, 0, true, Collections.emptyMap());
		final Route route = RoutePlanner.plan(selection.getPatches(), config, access, Holdings.EMPTY, new PohSetup(),
			AtlasPlayers.with(access, Holdings.EMPTY), null);
		final SupplyPlan supplies = SupplyCalculator.calculate(config, access, Holdings.EMPTY, selection, route,
			p -> null, id -> "item " + id, id -> 0);

		final String travel = String.join(" | ", supplies.getTravelPlan());
		assertTrue(travel, travel.contains("tablet"));
		assertFalse(travel, travel.contains("altar"));
	}

	@Test
	public void standingAtAStopStartsThereWithNoTravel()
	{
		// GitHub #1: already in Catherby, so no teleport there even though the start location is elsewhere
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.FARMING_GUILD);
		final AccessSnapshot access = onBook(Spellbook.STANDARD, Collections.emptySet());
		final Route route = RoutePlanner.plan(java.util.Arrays.asList(Patch.CATHERBY_HERB, Patch.FARMING_GUILD_HERB,
			Patch.ARDOUGNE_HERB), config, access, Holdings.EMPTY, new PohSetup(), AtlasPlayers.with(access, Holdings.EMPTY),
			Location.CATHERBY);
		final RouteStop first = route.getStops().get(0);
		assertEquals(Location.CATHERBY, first.getLocation());
		assertEquals(Departure.WALK, first.getDeparture());
		assertEquals(0, first.getLegSeconds(), 1e-9);
	}

	@Test
	public void routeOnlyCastsFromTheCurrentBook()
	{
		// On the standard book with no altar: it can't be cast, so it's only planned as a tablet to bring.
		final RouteStop standard = portPhasmatys(onBook(Spellbook.STANDARD, Collections.emptySet()));
		assertTrue(standard.getMethod() != TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT || standard.isNeedsSupplies());
		// On Arceuus it's cast from runes.
		final RouteStop arceuus = portPhasmatys(onBook(Spellbook.ARCEUUS, Collections.emptySet()));
		assertEquals(TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT, arceuus.getMethod());
		assertFalse(arceuus.isNeedsSupplies());
	}

	private static RouteStop catherby(Holdings holdings)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.LUNAR_DIPLOMACY, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, 99);
		levels.put(Skill.FARMING, 99);
		final AccessSnapshot standard = new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			Collections.emptySet(), null, Spellbook.STANDARD, Collections.emptySet());
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.CATHERBY);
		config.getTestTravel().put(Location.CATHERBY, TravelMethod.CATHERBY_TELEPORT);
		return RoutePlanner.plan(Collections.singletonList(Patch.CATHERBY_HERB), config, standard, holdings,
			new PohSetup(), AtlasPlayers.with(standard, holdings), null).getStops().get(0);
	}

	@Test
	public void pinnedSpellOffTheBookFallsBackToOneThatCanBeCast()
	{
		// GitHub #2: Catherby pinned to Catherby Teleport (Lunar) on the standard book, no altar, no tablet
		final RouteStop noTablet = catherby(Holdings.EMPTY);
		assertEquals(TravelMethod.CAMELOT_TELEPORT, noTablet.getMethod());
		assertFalse(noTablet.isNeedsSupplies());

		// With the tablet the pinned teleport still works
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.INVENTORY, Collections.singletonMap(Spell.CATHERBY_TELEPORT.getTabletItemId(), 1));
		final RouteStop tablet = catherby(new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true,
			false));
		assertEquals(TravelMethod.CATHERBY_TELEPORT, tablet.getMethod());
		assertFalse(tablet.isNeedsSupplies());
	}

	@Test
	public void oldPicksAreIgnored()
	{
		// Standard book, no tablet, Catherby on the nexus; saved before 2026-10-20 with Camelot Teleport picked for it
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.LUNAR_DIPLOMACY, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, 99);
		levels.put(Skill.FARMING, 99);
		final AccessSnapshot standard = new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			Collections.emptySet(), null, Spellbook.STANDARD, Collections.emptySet());
		final RunConfig config = new Gson().fromJson("{\"travel\":{\"CATHERBY\":\"CAMELOT_TELEPORT\"},"
			+ "\"travelHow\":{\"CATHERBY\":\"DIRECT\"}}", RunConfig.class).sanitise();
		config.setStartLocation(Location.FARMING_GUILD);
		final PohSetup poh = new PohSetup();
		poh.getNexusDestinations().add(com.farmrunautopilot.data.poh.PortalNexus.Destination.CATHERBY);

		// The nexus is quicker than Camelot's walk, so the route takes it
		final RouteStop stop = RoutePlanner.plan(Collections.singletonList(Patch.CATHERBY_HERB), config, standard,
			Holdings.EMPTY, poh, AtlasPlayers.with(standard, Holdings.EMPTY), null).getStops().get(0);
		assertEquals(TravelMethod.CATHERBY_TELEPORT, stop.getMethod());
		assertEquals(Departure.POH_NEXUS, stop.getDeparture());
	}

	private static AccessSnapshot lunarCaster(Spellbook book, int magic)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.LUNAR_DIPLOMACY, QuestState.FINISHED);
		quests.put(Quest.DREAM_MENTOR, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, magic);
		levels.put(Skill.FARMING, 99);
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(), Collections.emptySet(), null, book,
			Collections.emptySet());
	}

	@Test
	public void spellbookSwapOnlyFromLunar()
	{
		// On Lunar with 96 Magic and Dream Mentor: a standard spell is castable with one Spellbook Swap
		final AccessSnapshot lunar = lunarCaster(Spellbook.LUNAR, 96);
		assertTrue(lunar.canCast(Spell.CAMELOT_TELEPORT));
		assertTrue(lunar.needsLunarSwap(Spell.CAMELOT_TELEPORT));
		assertFalse(lunar.needsLunarSwap(Spell.CATHERBY_TELEPORT));
		// Below 96 Magic there's no Swap
		assertFalse(lunarCaster(Spellbook.LUNAR, 95).canCast(Spell.CAMELOT_TELEPORT));
		// On the standard book, Spellbook Swap never counts: Lunar spells stay tablet or nexus only
		final AccessSnapshot standard = lunarCaster(Spellbook.STANDARD, 99);
		assertFalse(standard.canCast(Spell.CATHERBY_TELEPORT));
		assertFalse(standard.needsLunarSwap(Spell.CATHERBY_TELEPORT));
		// An altar for the book means no Swap is needed
		final AccessSnapshot altar = new AccessSnapshot(true, Collections.emptyMap(), Collections.singletonMap(
			Skill.MAGIC, 99), Collections.emptySet(), Collections.emptySet(), null, Spellbook.LUNAR,
			java.util.EnumSet.of(Spellbook.STANDARD));
		assertFalse(altar.needsLunarSwap(Spell.CAMELOT_TELEPORT));
	}

	@Test
	public void swapRunesAndWarningOnTheSupplyList()
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setEnabledTypes(java.util.EnumSet.of(com.farmrunautopilot.data.PatchType.HERB));
		for (Patch patch : Patch.values())
		{
			if (patch != Patch.CATHERBY_HERB)
			{
				config.getDisabledPatches().add(patch);
			}
		}
		config.setStartLocation(Location.FARMING_GUILD);
		// Pinned, as the Spellbook Swap guided test does: Catherby Teleport is on Lunar and would win. Switching
		// spellbook is off to start with (Travel tab), so on, as the guided test turns it on
		config.getTestTravel().put(Location.CATHERBY, TravelMethod.CAMELOT_TELEPORT);
		config.setSwitchSpellbooks(true);
		final AccessSnapshot lunar = lunarCaster(Spellbook.LUNAR, 99);
		final RunSelection selection = RunSelector.select(config, lunar, p -> null, 0, true, Collections.emptyMap());
		final Route route = RoutePlanner.plan(selection.getPatches(), config, lunar, Holdings.EMPTY, new PohSetup(),
			AtlasPlayers.with(lunar, Holdings.EMPTY), null);
		final SupplyPlan supplies = SupplyCalculator.calculate(config, lunar, Holdings.EMPTY, selection, route,
			p -> null, id -> "item " + id, id -> 0);
		final String warnings = String.join(" | ", supplies.getWarnings());
		assertTrue(warnings, warnings.contains("Spellbook Swap"));
		assertTrue(supplies.getRuneSummary(), supplies.getRuneSummary().toLowerCase().contains("cosmic"));
	}
}
