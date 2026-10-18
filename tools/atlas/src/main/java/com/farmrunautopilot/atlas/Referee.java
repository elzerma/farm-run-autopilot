package com.farmrunautopilot.atlas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import shortestpath.pathfinder.CollisionMap;

/**
 * The referee (docs/plans/travel-rework.md, section 3 step 10): Shortest Path's own fastest way, teleports included,
 * from every stop the plugin runs to every other, for three pretend players. The plugin's choices are compared with
 * these answers in sprint T2. The house is left out here; the plugin plans it itself.
 * Usage (from tools/atlas): ../../gradlew -p . referee
 */
public final class Referee
{
	/** Runes enough for any teleport spell. */
	private static final String[] RUNES = {"AIRRUNE", "WATERRUNE", "EARTHRUNE", "FIRERUNE", "MINDRUNE", "CHAOSRUNE",
		"DEATHRUNE", "BLOODRUNE", "LAWRUNE", "NATURERUNE", "COSMICRUNE", "BODYRUNE", "SOULRUNE", "ASTRALRUNE"};

	private Referee()
	{
	}

	public static void main(String[] args) throws Exception
	{
		final Path patchFile = Paths.get(args.length > 0 ? args[0] : "patches.tsv");
		final Path outFile = Paths.get(args.length > 1 ? args[1] : "referee.tsv");
		final String commit = args.length > 2 ? args[2] : "unknown";

		final BubbleConfig.World world = BubbleConfig.World.load();
		final CollisionMap map = new CollisionMap(world.map);
		final Places places = Places.build(world, map, Tsv.read(patchFile));
		final Asker asker = new Asker(world, true, 10_000);
		final Account maxed = Account.maxed(Account.everyItem(world));

		// The stops the plugin runs: a start tile (its first patch) and every standing tile of its patches
		final Map<String, Integer> starts = new LinkedHashMap<>();
		final Map<String, Set<Integer>> targets = new LinkedHashMap<>();
		for (Places.Patch patch : places.patches)
		{
			if (!patch.plugin)
			{
				continue;
			}
			final Places.Place place = places.placeOfPatch.get(patch.id);
			starts.putIfAbsent(patch.stop, place.start);
			targets.computeIfAbsent(patch.stop, k -> new LinkedHashSet<>()).addAll(place.standing);
		}

		// The house is the plugin's to plan: lock its teleports out (otherwise every house location counts at once)
		final Map<Account, String> players = new LinkedHashMap<>();
		for (Account player : List.of(likeElzerma(maxed), midLevel(maxed), maxed))
		{
			players.put(player.without(Requirement.var(VarbitID.POH_HOUSE_LOCATION)), player.name);
		}
		final ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
		final List<Future<String>> rows = new ArrayList<>();
		for (Map.Entry<Account, String> entry : players.entrySet())
		{
			for (Map.Entry<String, Integer> from : starts.entrySet())
			{
				for (Map.Entry<String, Set<Integer>> to : targets.entrySet())
				{
					if (from.getKey().equals(to.getKey()))
					{
						continue;
					}
					rows.add(pool.submit(() ->
					{
						final Way way = asker.ask(entry.getKey(), from.getValue(), to.getValue(), true);
						return entry.getValue() + "\t" + from.getKey() + "\t" + to.getKey() + "\t"
							+ (way == null ? "\t\t\t\tno way\t" : String.format(Locale.ROOT, "%.1f", way.seconds()) + "\t"
							+ way.walkTiles + "\t" + way.ticks + "\t" + (way.transports.isEmpty() ? "walk"
							: Way.name(way.transports.get(0))) + "\t" + way.way() + "\t" + way.needsText());
					}));
				}
			}
		}
		final StringBuilder out = new StringBuilder("# Shortest Path's fastest way with teleports (no house), from tools/atlas ")
			.append("Referee.java at Shortest Path ").append(commit).append(". Seconds as the atlas: walk tiles x 0.3 + ticks x 0.6.\n")
			.append("player\tfrom\tto\tseconds\ttiles\tticks\tfirst\tway\tneeds\n");
		for (Future<String> f : rows)
		{
			out.append(f.get()).append('\n');
		}
		pool.shutdown();
		Files.writeString(outFile, out);
		System.out.println("Wrote " + rows.size() + " answers to " + outFile.toAbsolutePath());
	}

	/**
	 * Like the plugin's ElzermaAccount test account: every quest, 90 in every skill, the standard spellbook, and its
	 * teleport items and runes carried.
	 */
	private static Account likeElzerma(Account base)
	{
		final Map<Integer, Integer> items = new HashMap<>();
		for (String name : new String[]{"SLAYER_RING_8", "DRAMEN_STAFF", "RING_OF_WEALTH_5", "LUMBRIDGE_RING_MEDIUM",
			"ARDY_CAPE_MEDIUM", "ECTOPHIAL", "VEOS_KHAREDSTS_MEMOIRS", "MOURNING_TELEPORT_CRYSTAL_5",
			"HG_QUETZALWHISTLE_PERFECTED_INFINITE", "RING_OF_DUELING_8"})
		{
			items.put(Names.itemId(name), 1);
		}
		addRunes(items);
		return base.as("like elzerma", levels(90), EnumSet.noneOf(Quest.class), Map.of(VarbitID.SPELLBOOK, 0), items);
	}

	/**
	 * A mid-level player: 70 in every skill, every quest but the late ones, the standard spellbook, common jewellery
	 * and runes carried.
	 */
	private static Account midLevel(Account base)
	{
		final Map<Integer, Integer> items = new HashMap<>();
		for (String name : new String[]{"DRAMEN_STAFF", "NECKLACE_OF_MINIGAMES_8", "RING_OF_DUELING_8",
			"JEWL_NECKLACE_OF_SKILLS_4", "AMULET_OF_GLORY_4", "ECTOPHIAL"})
		{
			items.put(Names.itemId(name), 1);
		}
		addRunes(items);
		final Set<Quest> late = EnumSet.noneOf(Quest.class);
		for (String name : new String[]{"SONG_OF_THE_ELVES", "DRAGON_SLAYER_II", "DESERT_TREASURE_II__THE_FALLEN_EMPIRE",
			"MONKEY_MADNESS_II", "A_KINGDOM_DIVIDED", "TWILIGHTS_PROMISE", "THE_CURSE_OF_ARRAV", "WHILE_GUTHIX_SLEEPS",
			"MY_ARMS_BIG_ADVENTURE", "MAKING_FRIENDS_WITH_MY_ARM", "THE_FREMENNIK_EXILES", "SINS_OF_THE_FATHER"})
		{
			try
			{
				late.add(Quest.valueOf(name));
			}
			catch (IllegalArgumentException ignored)
			{
				// A quest name RuneLite doesn't have; leave it
			}
		}
		return base.as("mid-level", levels(70), late, Map.of(VarbitID.SPELLBOOK, 0), items);
	}

	private static Map<Skill, Integer> levels(int level)
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			levels.put(skill, level);
		}
		return levels;
	}

	private static void addRunes(Map<Integer, Integer> items)
	{
		for (String rune : RUNES)
		{
			items.put(Names.itemId(rune), 10_000);
		}
	}
}
