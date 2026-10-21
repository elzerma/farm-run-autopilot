package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.data.travel.atlas.AtlasLegs;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/** The Travel tab's families and ways, each with how it stands for the player (sprint T7). */
public class TravelWaysTest
{
	private static final int LAST_MINIGAME_TELEPORT = 888;

	@Test
	public void spellsAreListedWithWhatTheyNeed()
	{
		final TravelWays ways = ways(config(), access(99, Quest.EADGARS_RUSE), bank());
		assertEquals(TravelWays.State.READY, way(ways, TravelFamily.SPELLS, "Camelot Teleport").getState());
		final TravelWays.Way trollheim = way(ways, TravelFamily.SPELLS, "Trollheim Teleport");
		assertEquals(TravelWays.State.NEEDS, trollheim.getState());
		assertEquals("Eadgar's Ruse", trollheim.getNeeds());
		// Greyed out on the tab: the hover says why
		assertTrue(trollheim.getDetail(), trollheim.getDetail().startsWith("Trollheim Teleport."));
		assertTrue(trollheim.getDetail(), trollheim.getDetail().contains("Missing: Eadgar's Ruse."));
	}

	@Test
	public void teleportItemsAreOnlyWhatThePlayerOwns()
	{
		final TravelWays ways = ways(config(), access(99), bank(ItemID.JEWL_NECKLACE_OF_SKILLS_4, 1));
		final TravelWays.Way guild = way(ways, TravelFamily.TELEPORT_ITEMS, "Skills necklace: Farming Guild");
		assertEquals(TravelWays.State.READY, guild.getState());
		assertTrue(guild.getDetail(), guild.getDetail().contains("4 charges left"));
		assertNull(find(ways, TravelFamily.TELEPORT_ITEMS, "Amulet of glory: Edgeville"));
		assertTrue(ways.get(TravelFamily.TABLETS).getWays().isEmpty());
	}

	@Test
	public void chargesAndTodaysUsesShow()
	{
		final RunConfig keep = config();
		keep.setKeepLastCharge(true);
		final Map<TravelMethod, Integer> used = new EnumMap<>(TravelMethod.class);
		used.put(TravelMethod.ARDOUGNE_CLOAK_FARM, 3);
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, new HashMap<>(Map.of(ItemID.JEWL_NECKLACE_OF_SKILLS_1, 1,
			ItemID.ARDY_CAPE_MEDIUM, 1)));
		final Holdings holdings = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false, -1,
			null, used);
		final TravelWays ways = ways(keep, access(99), holdings);
		final TravelWays.Way necklace = way(ways, TravelFamily.TELEPORT_ITEMS, "Skills necklace: Farming Guild");
		assertEquals(TravelWays.State.LAST_CHARGE_KEPT, necklace.getState());
		assertTrue(necklace.getDetail(), necklace.getDetail().contains("last charge is kept back"));
		final TravelWays.Way cloak = way(ways, TravelFamily.TELEPORT_ITEMS, "Ardougne cloak: Ardougne Farm");
		assertEquals(TravelWays.State.NONE_TODAY, cloak.getState());
		assertTrue(cloak.getDetail(), cloak.getDetail().contains("No uses left today."));
	}

	@Test
	public void aWayTurnedOffSaysSoAndIsntCounted()
	{
		final TravelWays before = ways(config(), access(99), bank());
		final RunConfig config = config();
		config.getWaysOff().add("TELEPORTATION_SPELL|Camelot Teleport");
		final TravelWays after = ways(config, access(99), bank());
		final TravelWays.Way camelot = way(after, TravelFamily.SPELLS, "Camelot Teleport");
		assertTrue(camelot.isOff());
		assertEquals(TravelWays.State.READY, camelot.getState());
		assertTrue(camelot.getDetail(), camelot.getDetail().contains("Turned off"));
		assertEquals(before.get(TravelFamily.SPELLS).getUsable() - 1, after.get(TravelFamily.SPELLS).getUsable());
	}

	@Test
	public void aFamilyEveryWayOfWhichNeedsTheSameSaysWhat()
	{
		// No staff and no elite Lumbridge diary; Twilight's Promise not done
		final TravelWays ways = ways(config(), access(99, Quest.TWILIGHTS_PROMISE), bank());
		assertEquals("needs a dramen or lunar staff", ways.get(TravelFamily.FAIRY_RINGS).summary());
		assertEquals("needs Twilight's Promise", ways.get(TravelFamily.QUETZALS).summary());

		final TravelWays staff = ways(config(), access(99), bank(ItemID.DRAMEN_STAFF, 1));
		assertTrue(staff.get(TravelFamily.FAIRY_RINGS).summary(), staff.get(TravelFamily.FAIRY_RINGS).getUsable() > 10);
	}

	@Test
	public void cooldownsAndTheWildernessShow()
	{
		final long now = 29_000_000;
		final Holdings holdings = bank(ItemID.RING_OF_DUELING_8, 1, ItemID.BURNING_AMULET_5, 1);
		final RouteAtlas atlas = AtlasPlayers.with(access(99), holdings, Map.of(), AtlasWays.DEFAULT,
			Map.of(LAST_MINIGAME_TELEPORT, (int) now - 1), now);
		final TravelWays ways = TravelWays.of(atlas, config(), access(99), holdings, new PohSetup(), id -> "item " + id);
		assertEquals(TravelWays.State.COOLING_DOWN, way(ways, TravelFamily.COOLDOWN, "Tithe Farm Minigame Teleport")
			.getState());
		assertEquals(TravelWays.State.WILDERNESS, way(ways, TravelFamily.TELEPORT_ITEMS, "Burning amulet: Lava Maze")
			.getState());
		// The Ferox Enclave is a safe zone (elzerma), though inside the Wilderness's bounds
		assertEquals(TravelWays.State.READY, way(ways, TravelFamily.TELEPORT_ITEMS, "Ring of dueling: Ferox Enclave")
			.getState());
		assertEquals(TravelWays.State.READY, way(ways, TravelFamily.TELEPORT_ITEMS, "Ring of dueling: Castle Wars")
			.getState());
	}

	@Test
	public void spellsFromAnotherBookAreGreyedOutWithSwitchingOff()
	{
		// On Lunar, where Spellbook Swap reaches the standard spellbook's Varrock Teleport
		final AccessSnapshot lunar = access(99, Spellbook.LUNAR);
		final Map<Integer, Integer> onLunar = Map.of(VarbitID.SPELLBOOK, 2);
		final RunConfig switching = config();
		switching.setSwitchSpellbooks(true);
		final TravelWays on = TravelWays.of(AtlasPlayers.with(lunar, bank(), onLunar, RunService.ways(switching), Map.of(),
			0), switching, lunar, bank(), new PohSetup(), id -> "item " + id);
		assertEquals(TravelWays.State.READY, way(on, TravelFamily.SPELLS, "Varrock Teleport").getState());

		final RunConfig noSwitching = config();
		final TravelWays off = TravelWays.of(AtlasPlayers.with(lunar, bank(), onLunar, RunService.ways(noSwitching),
			Map.of(), 0), noSwitching, lunar, bank(), new PohSetup(), id -> "item " + id);
		final TravelWays.Way varrock = way(off, TravelFamily.SPELLS, "Varrock Teleport");
		assertEquals(TravelWays.State.NEEDS, varrock.getState());
		assertEquals("the standard spellbook (Switch spellbook to cast is off)", varrock.getNeeds());
		assertEquals(TravelWays.State.READY, way(off, TravelFamily.SPELLS, "Catherby Teleport").getState());
	}

	@Test
	public void familiesWithoutAListHaveNoWays()
	{
		final TravelWays ways = ways(config(), access(99), bank());
		assertTrue(ways.get(TravelFamily.HOUSE).getWays().isEmpty());
		assertTrue(ways.get(TravelFamily.AGILITY_SHORTCUTS).getWays().isEmpty());
		assertEquals(TravelFamily.values().length, ways.getFamilies().size());
	}

	@Test
	public void readyIsWhatThePlannerCanUse()
	{
		// Every teleport the planner can use shows as ready, and every ready one is one it can use
		final AccessSnapshot access = ElzermaAccount.access();
		final Holdings holdings = ElzermaAccount.holdings();
		final RunConfig config = config();
		final RouteAtlas atlas = AtlasPlayers.with(access, holdings);
		final TravelWays ways = TravelWays.of(atlas, config, access, holdings, new PohSetup(), id -> "item " + id);
		final AtlasLegs legs = new AtlasLegs(atlas.getAtlas(), atlas.getAccount(), null,
			RoutePlanner.startRules(config, access, holdings, new PohSetup(), atlas));
		final Set<TravelFamily> teleports = EnumSet.of(TravelFamily.SPELLS, TravelFamily.TELEPORT_ITEMS,
			TravelFamily.TABLETS);
		final Map<String, Boolean> planned = new HashMap<>();
		for (TravelAtlas.Jump t : atlas.getAtlas().getTeleports())
		{
			if (teleports.contains(AtlasWays.family(t)))
			{
				planned.merge(AtlasWays.key(t), legs.usable(t), Boolean::logicalOr);
			}
		}
		int ready = 0;
		for (TravelFamily family : teleports)
		{
			for (TravelWays.Way way : ways.get(family).getWays())
			{
				final boolean isReady = way.getState() == TravelWays.State.READY;
				assertEquals(way.getKey() + ": " + way.getDetail(), isReady, planned.getOrDefault(way.getKey(), false));
				ready += isReady ? 1 : 0;
			}
		}
		for (Map.Entry<String, Boolean> e : planned.entrySet())
		{
			if (e.getValue())
			{
				assertNotNull("Planned but not listed: " + e.getKey(), find(ways, e.getKey()));
			}
		}
		assertTrue(ready > 10);
		assertFalse(ways.get(TravelFamily.SPELLS).getWays().isEmpty());
	}

	private static TravelWays ways(RunConfig config, AccessSnapshot access, Holdings holdings)
	{
		return TravelWays.of(AtlasPlayers.with(access, holdings, Map.of(), RunService.ways(config), Map.of(), 0), config,
			access, holdings, new PohSetup(), id -> "item " + id);
	}

	private static TravelWays.Way way(TravelWays ways, TravelFamily family, String name)
	{
		final TravelWays.Way way = find(ways, family, name);
		assertNotNull("No " + name + " in " + family, way);
		return way;
	}

	private static TravelWays.Way find(TravelWays ways, TravelFamily family, String name)
	{
		for (TravelWays.Way way : ways.get(family).getWays())
		{
			if (way.getName().equals(name))
			{
				return way;
			}
		}
		return null;
	}

	private static TravelWays.Way find(TravelWays ways, String key)
	{
		for (TravelWays.Family family : ways.getFamilies())
		{
			for (TravelWays.Way way : family.getWays())
			{
				if (way.getKey().equals(key))
				{
					return way;
				}
			}
		}
		return null;
	}

	private static RunConfig config()
	{
		return new RunConfig().sanitise();
	}

	/** Every quest done, every skill at {@code level}, on this spellbook (no house altar). */
	private static AccessSnapshot access(int level, Spellbook spellbook)
	{
		final AccessSnapshot standard = access(level);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, level);
		}
		return new AccessSnapshot(true, standard.getQuests(), levels, Collections.emptySet(),
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES, Unlock.QUETZAL_KASTORI), null, spellbook,
			Collections.emptySet());
	}

	/** Every quest done but these, every skill at {@code level}, no diaries; fairy rings and spirit trees open. */
	private static AccessSnapshot access(int level, Quest... notDone)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		for (Quest quest : notDone)
		{
			quests.put(quest, QuestState.NOT_STARTED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, level);
		}
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(),
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
}
