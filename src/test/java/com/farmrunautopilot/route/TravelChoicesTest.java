package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.poh.HousePortal;
import com.farmrunautopilot.data.poh.PortalNexus;
import com.farmrunautopilot.data.travel.BankTeleport;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.atlas.AtlasLegs;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/**
 * The route follows the Travel tab (docs/plans/travel-rework.md, sprint T7): a family or a single way turned off is
 * never used, and the run is planned with what's left; home and minigame teleports only with their family on, once
 * each per run, once their cooldown is over; the Wilderness only when the player lets it.
 */
public class TravelChoicesTest
{
	/** Varplayers holding the minute (since 1970) a minigame or home teleport was last used. */
	private static final int LAST_MINIGAME_TELEPORT = 888;
	private static final int LAST_HOME_TELEPORT = 892;

	@Test
	public void aWayTurnedOffIsntUsedAndTheStopStillHasAWay()
	{
		final AccessSnapshot access = ElzermaAccount.access();
		final Holdings holdings = ElzermaAccount.holdings();
		for (Location to : Arrays.asList(Location.CATHERBY, Location.HOSIDIUS, Location.ARDOUGNE_FARM))
		{
			final RouteStop before = between(null, to, config(), access, holdings);
			assertFalse(to + ": " + before.getBreakdown(), before.getWays().isEmpty());
			final String used = before.getWays().get(0);
			final RunConfig config = config();
			if (AtlasWays.keyFamily(used) != null)
			{
				config.setOn(AtlasWays.keyFamily(used), false);
			}
			else
			{
				config.getWaysOff().add(used);
			}
			final RouteStop after = between(null, to, config, access, holdings);
			assertFalse(to + " still by " + used + ": " + after.getBreakdown(), after.getWays().contains(used));
			assertNotEquals(to + ": " + after.getBreakdown(), Departure.NONE, after.getDeparture());
			System.out.println(to.getDisplayName() + ": " + before.describeTravel() + " (" + used + " turned off: "
				+ after.describeTravel() + ")");
		}
	}

	@Test
	public void aFamilyTurnedOffIsntUsedAnywhereInTheRun()
	{
		final AccessSnapshot access = ElzermaAccount.access();
		final Holdings holdings = withCoins(ElzermaAccount.holdings());
		for (TravelFamily family : Arrays.asList(TravelFamily.SPELLS, TravelFamily.TELEPORT_ITEMS,
			TravelFamily.FAIRY_RINGS, TravelFamily.SPIRIT_TREES, TravelFamily.QUETZALS, TravelFamily.BOATS))
		{
			final RunConfig config = config();
			config.setOn(family, false);
			final Route route = elzermasRun(config, access, holdings);
			for (RouteStop stop : route.getStops())
			{
				for (String key : stop.getWays())
				{
					final TravelFamily used = AtlasWays.keyFamily(key) != null ? AtlasWays.keyFamily(key) : family(key);
					assertNotEquals(family + " off, yet " + stop.getLocation() + ": " + stop.describeTravel(), family, used);
				}
			}
		}
	}

	@Test
	public void houseOffKeepsTheRouteOutOfTheHouse()
	{
		// No spells: Teleport to House by tablet, then the nexus's Catherby portal (a Rimmington house, "inside")
		final AccessSnapshot noSpells = accessWith(Skill.MAGIC, 1);
		final AccessSnapshot access = new AccessSnapshot(true, noSpells.getQuests(), noSpells.getRealLevels(),
			noSpells.getCompletedDiaries(), noSpells.getUnlocks(), HousePortal.RIMMINGTON, Spellbook.STANDARD,
			Collections.emptySet());
		final Holdings holdings = bank(ItemID.POH_TABLET_TELEPORTTOHOUSE, 10);
		final PohSetup poh = new PohSetup();
		poh.setNexusDestinations(EnumSet.of(PortalNexus.Destination.CATHERBY));
		final RunConfig config = config();
		final RouteStop through = RoutePlanner.between(null, Location.CATHERBY, config, access, holdings, poh,
			AtlasPlayers.with(access, holdings, Map.of(), RunService.ways(config), Map.of(), 0));
		assertTrue(through.getBreakdown(), through.getWays().contains(AtlasWays.familyKey(TravelFamily.HOUSE)));

		config.setOn(TravelFamily.HOUSE, false);
		final RouteStop not = RoutePlanner.between(null, Location.CATHERBY, config, access, holdings, poh,
			AtlasPlayers.with(access, holdings, Map.of(), RunService.ways(config), Map.of(), 0));
		assertFalse(not.getBreakdown(), not.getWays().contains(AtlasWays.familyKey(TravelFamily.HOUSE)));
		assertFalse(not.getBreakdown(), not.getDeparture().isViaHouse() || not.getAtlas() != null
			&& (not.getAtlas().isViaHouse() || not.getAtlas().isByTree()));
	}

	@Test
	public void boatsOffTakesAnotherWayToBrimhaven()
	{
		// Ardougne Teleport and Captain Barnaby's boat, or something without a boat or ship once they're off
		final AccessSnapshot access = ElzermaAccount.access();
		final Holdings holdings = withCoins(ElzermaAccount.holdings());
		final RunConfig noBoats = config();
		noBoats.setOn(TravelFamily.BOATS, false);
		final RouteStop stop = between(null, Location.BRIMHAVEN, noBoats, access, holdings);
		assertNotEquals(stop.getBreakdown(), Departure.NONE, stop.getDeparture());
		assertFalse(stop.getBreakdown(), stop.getWays().contains(AtlasWays.familyKey(TravelFamily.BOATS)));
		assertFalse(stop.describeTravel(), stop.describeTravel().toLowerCase(Locale.ROOT).contains("boat"));
	}

	@Test
	public void agilityShortcutsOffGoesTheLongWayRound()
	{
		// Falador Park to Taverley over the rocks with 66 Agility, by the gate without shortcuts
		final AccessSnapshot agile = access(99, Collections.emptySet());
		final RouteStop rocks = between(Location.FALADOR_PARK, Location.TAVERLEY, config(), agile, bank());
		final RunConfig noShortcuts = config();
		noShortcuts.setOn(TravelFamily.AGILITY_SHORTCUTS, false);
		final RouteStop round = between(Location.FALADOR_PARK, Location.TAVERLEY, noShortcuts, agile, bank());
		assertTrue(rocks.getBreakdown() + " / " + round.getBreakdown(), round.getLegSeconds() > rocks.getLegSeconds() + 5);
	}

	@Test
	public void theWildernessOnlyWhenThePlayerLetsIt()
	{
		// The burning amulet's teleports land in the Wilderness
		final AccessSnapshot access = access(99, Collections.emptySet());
		final Holdings holdings = bank(ItemID.BURNING_AMULET_5, 1);
		final int avoiding = usable(access, holdings, AtlasWays.DEFAULT);
		final int through = usable(access, holdings, new AtlasWays(EnumSet.of(TravelFamily.COOLDOWN),
			Collections.emptySet(), false));
		assertTrue(avoiding + " / " + through, through > avoiding);
	}

	@Test
	public void cooldownTeleportsOnlyWithTheirFamilyOnAndTheirCooldownOver()
	{
		final AccessSnapshot access = access(99, Collections.emptySet());
		final Holdings holdings = bank();
		final AtlasWays on = new AtlasWays(EnumSet.noneOf(TravelFamily.class), Collections.emptySet(), true);
		final int off = usable(access, holdings, AtlasWays.DEFAULT);
		final int ready = usable(access, holdings, on);
		assertTrue(off + " / " + ready, ready > off);

		// Both used a minute ago: still cooling down
		final long now = 29_000_000;
		final Map<Integer, Integer> justUsed = Map.of(LAST_MINIGAME_TELEPORT, (int) now - 1, LAST_HOME_TELEPORT,
			(int) now - 1);
		final RouteAtlas cooling = AtlasPlayers.with(access, holdings, Map.of(), on, justUsed, now);
		assertEquals(off, new AtlasLegs(cooling.getAtlas(), cooling.getAccount()).usableTeleports());
		// Long ago: ready
		final Map<Integer, Integer> longAgo = Map.of(LAST_MINIGAME_TELEPORT, (int) now - 60, LAST_HOME_TELEPORT,
			(int) now - 60);
		final RouteAtlas over = AtlasPlayers.with(access, holdings, Map.of(), on, longAgo, now);
		assertEquals(ready, new AtlasLegs(over.getAtlas(), over.getAccount()).usableTeleports());
	}

	@Test
	public void oneMinigameTeleportPerRun()
	{
		// No spells, nothing to teleport with: the grouping tab's teleports are the quickest ways to these stops
		final AccessSnapshot access = accessWith(Skill.MAGIC, 1);
		final Holdings holdings = bank();
		final RunConfig config = config();
		config.setOn(TravelFamily.COOLDOWN, true);
		// Tithe Farm, Nightmare Zone and Trouble Brewing (then Brother Tranquility)
		final List<Patch> patches = Arrays.asList(Patch.HOSIDIUS_HERB, Patch.YANILLE_HOPS, Patch.HARMONY_ISLAND_HERB);
		int alone = 0;
		for (Patch patch : patches)
		{
			final RouteStop stop = between(null, patch.getLocation(), config, access, holdings);
			alone += minigame(stop) ? 1 : 0;
		}
		assertTrue("At most one stop on its own is reached by a minigame teleport, so this tests nothing", alone > 1);

		final Route route = RoutePlanner.plan(patches, config, access, holdings, new PohSetup(),
			AtlasPlayers.with(access, holdings, Map.of(), RunService.ways(config), Map.of(), 0), null);
		long used = route.getStops().stream().filter(TravelChoicesTest::minigame).count();
		assertTrue(describe(route), used <= 1);
	}

	@Test
	public void spellsFromAnotherBookOnlyWithSwitchingOn()
	{
		// On Lunar with Spellbook Swap: Varrock Teleport is a standard spell, cast after a swap once switching is on
		final AccessSnapshot lunar = onLunar();
		final TravelAtlas.Jump varrock = teleport("Varrock Teleport");
		final RunConfig switching = config();
		switching.setSwitchSpellbooks(true);
		assertTrue(usableWith(lunar, switching, varrock));
		// Off to start with (elzerma)
		final RunConfig noSwitching = config();
		assertFalse(noSwitching.isSwitchSpellbooks());
		assertFalse(usableWith(lunar, noSwitching, varrock));
		// A Lunar spell, on the book already, either way
		final TravelAtlas.Jump catherby = teleport("Catherby Teleport");
		assertTrue(usableWith(lunar, noSwitching, catherby));
	}

	@Test
	public void aBankTeleportTurnedOffIsntCarried()
	{
		final AccessSnapshot access = ElzermaAccount.access();
		final Holdings holdings = bank(ItemID.RING_OF_DUELING_8, 1, ItemID.RING_OF_WEALTH_5, 1);
		final RunConfig config = config();
		assertEquals(BankTeleport.RING_OF_DUELING, BankTeleport.best(access, holdings,
			t -> RunService.allowed(config, t)));
		config.getWaysOff().add("TELEPORTATION_ITEM|Ring of dueling: Castle Wars");
		assertEquals(BankTeleport.RING_OF_WEALTH, BankTeleport.best(access, holdings,
			t -> RunService.allowed(config, t)));
		config.setOn(TravelFamily.TELEPORT_ITEMS, false);
		assertEquals(null, BankTeleport.best(access, holdings, t -> RunService.allowed(config, t)));
	}

	/** A stop reached by one of the grouping tab's minigame teleports. */
	private static boolean minigame(RouteStop stop)
	{
		return stop.getWays().stream().anyMatch(k -> k.startsWith("TELEPORTATION_MINIGAME|"));
	}

	/** The family of a single way's key, by its kind ("TELEPORTATION_SPELL|...", "FAIRY_RING|..."). */
	private static TravelFamily family(String key)
	{
		final String type = key.substring(0, key.indexOf('|'));
		final String name = key.substring(key.indexOf('|') + 1);
		for (com.farmrunautopilot.data.travel.atlas.TravelAtlas.Jump jump : AtlasPlayers.ATLAS.getHops())
		{
			if (jump.getType().equals(type))
			{
				return AtlasWays.family(jump);
			}
		}
		for (com.farmrunautopilot.data.travel.atlas.TravelAtlas.Jump jump : AtlasPlayers.ATLAS.getTeleports())
		{
			if (AtlasWays.key(jump).equals(key))
			{
				return AtlasWays.family(jump);
			}
		}
		throw new AssertionError("No way " + key + " (" + name + ")");
	}

	/** The planner can use this teleport, by its own rules (casting, spellbooks), for a player with nothing in the bank. */
	private static boolean usableWith(AccessSnapshot access, RunConfig config, TravelAtlas.Jump teleport)
	{
		final Holdings holdings = bank();
		final RouteAtlas atlas = AtlasPlayers.with(access, holdings, Map.of(VarbitID.SPELLBOOK, 2), RunService.ways(config),
			Map.of(), 0);
		return new AtlasLegs(atlas.getAtlas(), atlas.getAccount(), null,
			RoutePlanner.startRules(config, access, holdings, new PohSetup(), atlas)).usable(teleport);
	}

	/** The atlas's teleport of this name. */
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

	/** 99 everywhere, every quest done, on the Lunar spellbook (so Spellbook Swap can be cast), no house altar. */
	private static AccessSnapshot onLunar()
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES, Unlock.QUETZAL_KASTORI), null, Spellbook.LUNAR,
			Collections.emptySet());
	}

	private static int usable(AccessSnapshot access, Holdings holdings, AtlasWays ways)
	{
		final RouteAtlas atlas = AtlasPlayers.with(access, holdings, ways);
		return new AtlasLegs(atlas.getAtlas(), atlas.getAccount()).usableTeleports();
	}

	private static Route elzermasRun(RunConfig config, AccessSnapshot access, Holdings holdings)
	{
		final List<Patch> patches = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (ElzermaAccount.STOPS.contains(patch.getLocation()))
			{
				patches.add(patch);
			}
		}
		return RoutePlanner.plan(patches, config, access, holdings, new PohSetup(),
			AtlasPlayers.with(access, holdings, Map.of(), RunService.ways(config), Map.of(), 0), null);
	}

	private static RouteStop between(Location from, Location to, RunConfig config, AccessSnapshot access,
		Holdings holdings)
	{
		return RoutePlanner.between(from, to, config, access, holdings, new PohSetup(),
			AtlasPlayers.with(access, holdings, Map.of(), RunService.ways(config), Map.of(), 0));
	}

	private static RunConfig config()
	{
		return new RunConfig().sanitise();
	}

	private static String describe(Route route)
	{
		final StringBuilder s = new StringBuilder();
		for (RouteStop stop : route.getStops())
		{
			s.append(String.format(Locale.ROOT, "  %s: %s, %.0f s = %s%n", stop.getLocation().getDisplayName(),
				stop.describeTravel(), stop.getLegSeconds(), stop.getBreakdown()));
		}
		return s.toString();
	}

	/** Every quest done, every skill at {@code level}, these diaries; fairy rings and spirit trees open. */
	private static AccessSnapshot access(int level, Set<String> diaries)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, level);
		}
		return new AccessSnapshot(true, quests, levels, diaries,
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES, Unlock.QUETZAL_KASTORI), null, Spellbook.STANDARD,
			Collections.emptySet());
	}

	/** 99 everywhere but one skill. */
	private static AccessSnapshot accessWith(Skill skill, int level)
	{
		final AccessSnapshot all = access(99, Collections.emptySet());
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill s : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(s, s == skill ? level : 99);
		}
		return new AccessSnapshot(true, all.getQuests(), levels, Collections.emptySet(),
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES, Unlock.QUETZAL_KASTORI), null, Spellbook.STANDARD,
			Collections.emptySet());
	}

	/** In the bank: item id, count, item id, count... */
	private static Holdings bank(int... idsAndCounts)
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		for (int i = 0; i < idsAndCounts.length; i += 2)
		{
			bank.put(idsAndCounts[i], idsAndCounts[i + 1]);
		}
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}

	/** The same holdings with coins in the bank, as any real player has for fares. */
	private static Holdings withCoins(Holdings holdings)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		final Map<Integer, Integer> bank = new HashMap<>(holdings.in(Holdings.Source.BANK));
		bank.put(ItemID.COINS, 1_000_000);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}
}
