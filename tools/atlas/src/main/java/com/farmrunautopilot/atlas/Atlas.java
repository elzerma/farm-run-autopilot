package com.farmrunautopilot.atlas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import shortestpath.transport.Transport;

/**
 * Sprint T1 of docs/plans/travel-rework.md: builds the travel atlas. For every patch and bank, finds the places worth
 * starting from (Sweep), asks Shortest Path's pathfinder in the bubble for the fastest way from each with no further
 * teleport, peels what each way needs for the next-best ways, and writes the plugin's data files and a report.
 * Usage (from tools/atlas): ../../gradlew -p . atlas
 */
public final class Atlas
{
	/**
	 * How far a start may be from a patch to be asked (Shortest Path's units: tiles walked plus transport ticks; 300 is
	 * 90 s of running, or 3 minutes of rides). A start further away always loses to a nearer teleport.
	 */
	static final int SWEEP_BUDGET = 300;
	/** The same for a bank or a hub (a fairy ring, spirit tree...): only walked to from close by. */
	static final int BANK_SWEEP_BUDGET = 150;
	/** Ways slower than this are dropped: another teleport beats them in game. */
	static final double WAY_BUDGET_S = 300;
	static final int MAX_QUESTIONS = 8;
	static final int MAX_WAYS = 6;
	/** A search still going after this long is heading for a place the account can't reach; it's stopped. */
	static final long TIME_LIMIT_MS = 4000;
	/** A spirit tree hop within this of a spirit tree patch uses the tree the player planted there. */
	static final int PLANTED_TREE_TILES = 6;

	private Atlas()
	{
	}

	/** One pair of places and the ways between them, quickest first. */
	static final class Answer
	{
		final Places.Place from;
		final Places.Place to;
		final List<Way> ways;

		Answer(Places.Place from, Places.Place to, List<Way> ways)
		{
			this.from = from;
			this.to = to;
			this.ways = ways;
		}
	}

	public static void main(String[] args) throws Exception
	{
		final Path patchFile = Paths.get(arg(args, 0, "patches.tsv"));
		final Path outDir = Paths.get(arg(args, 1, "../../src/main/resources/com/farmrunautopilot/atlas"));
		final Path reportFile = Paths.get(arg(args, 2, "report.md"));
		final String commit = arg(args, 3, "unknown");
		final int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);

		final long started = System.nanoTime();
		final BubbleConfig.World world = BubbleConfig.World.load();
		final shortestpath.pathfinder.CollisionMap map = new shortestpath.pathfinder.CollisionMap(world.map);
		final Places places = Places.build(world, map, Tsv.read(patchFile));
		final Report report = new Report(commit, Report.Previous.read(outDir));
		report.count("Places: patches (by tile)", places.all.stream().filter(p -> p.kind == Places.Kind.PATCH).count());
		report.count("Places: banks near a patch", places.all.stream().filter(p -> p.kind == Places.Kind.BANK).count());
		report.count("Places: teleport and hop landings", places.all.stream().filter(p -> p.kind == Places.Kind.LANDING).count());
		report.count("Places: hubs (where a hop is taken)", places.all.stream().filter(p -> p.kind == Places.Kind.HUB).count());
		report.count("Teleports (anywhere, or from the house)", places.teleports.size());
		report.count("Hops (fairy rings, spirit trees, quetzals, gliders, charter ships...)", places.hops.size());
		System.out.println("Places: " + places.all.size() + ", teleports: " + places.teleports.size() + ", hops: "
			+ places.hops.size());

		// Which starts are worth asking about, per patch, bank and hub; and whether a way that needs nothing could get
		// there. Starts are patches, banks and landings (a hub is only ever walked to).
		final Sweep sweep = new Sweep(world, false);
		final Sweep walking = new Sweep(world, true);
		final List<Places.Place> targets = new ArrayList<>();
		for (Places.Place p : places.all)
		{
			if (p.kind != Places.Kind.LANDING)
			{
				targets.add(p);
			}
		}
		final ExecutorService pool = Executors.newFixedThreadPool(threads);
		final List<Future<List<Pair>>> sweeps = new ArrayList<>();
		for (Places.Place target : targets)
		{
			sweeps.add(pool.submit(() ->
			{
				final int budget = target.kind == Places.Kind.PATCH ? SWEEP_BUDGET : BANK_SWEEP_BUDGET;
				final Sweep.IntIntMap costs = sweep.costsTo(target.standing, budget);
				final Sweep.IntIntMap free = walking.costsTo(target.standing, budget);
				final List<Pair> pairs = new ArrayList<>();
				for (Places.Place from : places.all)
				{
					final boolean useful = from != target && from.kind != Places.Kind.HUB
						&& (target.kind == Places.Kind.PATCH || from.kind != Places.Kind.BANK);
					if (useful && costs.get(from.start) <= budget)
					{
						pairs.add(new Pair(from, target, free.get(from.start) <= budget));
					}
				}
				return pairs;
			}));
		}
		final List<Pair> pairs = new ArrayList<>();
		for (Future<List<Pair>> f : sweeps)
		{
			pairs.addAll(f.get());
		}
		report.count("Pairs asked (start within the sweep budget)", pairs.size());
		System.out.printf(Locale.ROOT, "Sweeps done: %d pairs to ask, %d where a way could need nothing (%.0f s)%n",
			pairs.size(), pairs.stream().filter(p -> p.walkable).count(), seconds(started));
		if (arg(args, 4, "").equals("pairs"))
		{
			// Only counting, to tune the budgets
			pool.shutdown();
			return;
		}

		// Ask the bubble
		final Asker asker = new Asker(world, true, TIME_LIMIT_MS);
		final Account maxed = Account.maxed(Account.everyItem(world)).withoutHops();
		final Account bare = Account.bare();
		final AtomicInteger done = new AtomicInteger();
		final AtomicInteger questions = new AtomicInteger();
		final List<Future<Answer>> futures = new ArrayList<>();
		for (Pair pair : pairs)
		{
			futures.add(pool.submit(() ->
			{
				final List<Way> ways = ways(asker, maxed, pair.walkable ? bare : null, pair.from.start, pair.to.standing,
					questions);
				final int n = done.incrementAndGet();
				if (n % 1000 == 0)
				{
					System.out.printf(Locale.ROOT, "  %d / %d pairs, %d questions (%.0f s)%n", n, pairs.size(),
						questions.get(), seconds(started));
				}
				return new Answer(pair.from, pair.to, ways);
			}));
		}
		final List<Answer> answers = new ArrayList<>();
		for (Future<Answer> f : futures)
		{
			final Answer a = f.get();
			if (!a.ways.isEmpty())
			{
				answers.add(a);
			}
		}
		pool.shutdown();
		answers.sort(Comparator.comparingInt((Answer a) -> a.from.id).thenComparingInt(a -> a.to.id));
		report.count("Questions asked", questions.get());
		report.count("Searches stopped at the " + TIME_LIMIT_MS / 1000 + " s limit", asker.stopped.get());
		report.count("Pairs with at least one way", answers.size());
		report.count("Ways kept", answers.stream().mapToInt(a -> a.ways.size()).sum());
		System.out.printf(Locale.ROOT, "Questions done: %d ways between %d pairs (%.0f s)%n",
			answers.stream().mapToInt(a -> a.ways.size()).sum(), answers.size(), seconds(started));

		final Writer writer = new Writer(commit);
		writer.write(outDir, places, answers);
		report.files(outDir);
		report.count("Hops kept (walked to and leading somewhere)", writer.keptHops);
		report.count("Build time (s)", Math.round(seconds(started)));
		report.write(reportFile, places, answers, writer);
		System.out.println("Wrote " + outDir.toAbsolutePath() + " and " + reportFile.toAbsolutePath());
	}

	/** A start and a patch or bank to ask about; {@code walkable} when a way that needs nothing could reach it. */
	static final class Pair
	{
		final Places.Place from;
		final Places.Place to;
		final boolean walkable;

		Pair(Places.Place from, Places.Place to, boolean walkable)
		{
			this.from = from;
			this.to = to;
			this.walkable = walkable;
		}
	}

	/**
	 * The ways from {@code start} to any of {@code targets}: the fastest for a maxed account, then for each thing it
	 * needed, the fastest without it (up to {@link #MAX_QUESTIONS} questions), and the fastest that needs nothing at all
	 * (when {@code bare} is given). Ways over the budget, and ways another kept way beats while needing no more, are
	 * dropped.
	 */
	static List<Way> ways(Asker asker, Account maxed, Account bare, int start, Set<Integer> targets, AtomicInteger asked)
	{
		final List<Way> found = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		final Set<String> accounts = new HashSet<>();
		final Deque<Account> queue = new ArrayDeque<>();
		queue.add(maxed);
		int questions = 0;
		while (!queue.isEmpty() && questions < MAX_QUESTIONS)
		{
			final Account account = queue.poll();
			if (!accounts.add(account.name))
			{
				continue;
			}
			questions++;
			final Way way = asker.ask(account, start, targets, false);
			if (way == null || way.seconds() > WAY_BUDGET_S)
			{
				continue;
			}
			if (seen.add(way.signature()))
			{
				found.add(way);
			}
			for (Requirement requirement : way.needs)
			{
				queue.add(account.without(requirement));
			}
		}
		if (bare != null && found.stream().noneMatch(w -> w.needs.isEmpty()))
		{
			questions++;
			final Way way = asker.ask(bare, start, targets, false);
			if (way != null && way.seconds() <= WAY_BUDGET_S && seen.add(way.signature()))
			{
				found.add(way);
			}
		}
		asked.addAndGet(questions);

		found.sort(Comparator.comparingDouble(Way::seconds));
		final List<Way> kept = new ArrayList<>();
		for (Way way : found)
		{
			if (kept.stream().noneMatch(k -> k.dominates(way)))
			{
				kept.add(way);
			}
		}
		return kept.size() > MAX_WAYS ? new ArrayList<>(kept.subList(0, MAX_WAYS)) : kept;
	}

	private static String arg(String[] args, int i, String fallback)
	{
		return args.length > i && !args[i].isBlank() ? args[i] : fallback;
	}

	private static double seconds(long since)
	{
		return (System.nanoTime() - since) / 1e9;
	}

	/** Writes the plugin's atlas files: places, teleports, transports, requirements and ways. */
	static final class Writer
	{
		private final String commit;
		final Map<String, Integer> requirementIds = new LinkedHashMap<>();
		final List<Requirement> requirements = new ArrayList<>();
		final Map<String, Integer> transportIds = new LinkedHashMap<>();
		final List<Transport> transports = new ArrayList<>();
		int keptHops;

		Writer(String commit)
		{
			this.commit = commit;
		}

		void write(Path dir, Places places, List<Answer> answers) throws IOException
		{
			Files.createDirectories(dir);
			final String header = "# Generated by tools/atlas from Shortest Path " + commit
				+ " (BSD-2, see THIRD_PARTY_NOTICES). Do not edit by hand; see docs/plans/travel-rework.md.\n";

			final StringBuilder ways = new StringBuilder(header);
			ways.append("from\tto\ttiles\tticks\tsteps\tneeds\n");
			for (Answer a : answers)
			{
				for (Way w : a.ways)
				{
					ways.append(a.from.id).append('\t').append(a.to.id).append('\t').append(w.walkTiles).append('\t')
						.append(w.ticks).append('\t').append(transportList(w.transports)).append('\t')
						.append(requirementList(w.needs)).append('\n');
				}
			}

			final StringBuilder teleports = new StringBuilder(header);
			teleports.append("id\ttype\tlanding\tticks\tconsumable\twilderness\tneeds\tname\tmenu\n");
			int id = 0;
			for (Map.Entry<Transport, Places.Place> e : places.teleports.entrySet())
			{
				final Transport t = e.getKey();
				final List<Requirement> needs = new ArrayList<>(Requirement.of(List.of(t)));
				needs.removeIf(r -> r.kind == Requirement.Kind.TYPE);
				teleports.append(id++).append('\t').append(t.getType()).append('\t').append(e.getValue().id).append('\t')
					.append(t.getDuration()).append('\t').append(t.isConsumable()).append('\t')
					.append(t.getMaxWildernessLevel()).append('\t').append(requirementList(needs)).append('\t')
					.append(clean(Way.name(t))).append('\t').append(clean(t.getObjectInfo())).append('\n');
			}

			// Hops worth keeping: some way walks to where they're taken, and some way leaves where they land
			final Set<Places.Place> walkedTo = new HashSet<>();
			final Set<Places.Place> leftFrom = new HashSet<>();
			for (Answer a : answers)
			{
				walkedTo.add(a.to);
				leftFrom.add(a.from);
			}
			// A spirit tree a player plants: a hop from or to it needs it planted (Shortest Path checks that outside its data)
			final List<Places.Patch> plantedTrees = new ArrayList<>();
			for (Places.Patch p : places.patches)
			{
				if (p.type.equals("SPIRIT_TREE"))
				{
					plantedTrees.add(p);
				}
			}
			final StringBuilder hops = new StringBuilder(header);
			hops.append("id\ttype\tfrom\tto\tticks\tneeds\tname\tmenu\n");
			int hopId = 0;
			for (Places.Hop h : places.hops)
			{
				if (h.to == null || !walkedTo.contains(h.from) || !leftFrom.contains(h.to))
				{
					continue;
				}
				final Transport t = h.transport;
				final List<Requirement> needs = new ArrayList<>(Requirement.of(List.of(t)));
				if (t.getType() == shortestpath.transport.TransportType.SPIRIT_TREE)
				{
					for (Places.Patch tree : plantedTrees)
					{
						if (Places.distance(tree.tile, t.getOrigin()) <= PLANTED_TREE_TILES
							|| Places.distance(tree.tile, t.getDestination()) <= PLANTED_TREE_TILES)
						{
							needs.add(Requirement.planted(tree.id));
						}
					}
				}
				hops.append(hopId++).append('\t').append(t.getType()).append('\t').append(h.from.id).append('\t')
					.append(h.to.id).append('\t').append(Way.ticks(t, true)).append('\t')
					.append(requirementList(needs)).append('\t').append(clean(Way.name(t))).append('\t')
					.append(clean(t.getObjectInfo())).append('\n');
			}
			keptHops = hopId;

			final StringBuilder placeRows = new StringBuilder(header);
			placeRows.append("id\tkind\tx\ty\tplane\tstop\tnames\n");
			for (Places.Place p : places.all)
			{
				final int[] xyz = Places.unpack(p.tile);
				placeRows.append(p.id).append('\t').append(p.kind).append('\t').append(xyz[0]).append('\t').append(xyz[1])
					.append('\t').append(xyz[2]).append('\t').append(p.stop).append('\t')
					.append(clean(String.join(" | ", p.names))).append('\n');
			}

			final StringBuilder transportRows = new StringBuilder(header);
			transportRows.append("id\ttype\torigin\tdestination\tticks\tguessed\tneeds\tmenu\tname\n");
			for (int i = 0; i < transports.size(); i++)
			{
				final Transport t = transports.get(i);
				transportRows.append(i).append('\t').append(t.getType()).append('\t').append(Tsv.text(t.getOrigin()))
					.append('\t').append(Tsv.text(t.getDestination())).append('\t').append(Way.ticks(t, true)).append('\t')
					.append(Way.guessed(t)).append('\t').append(requirementList(Requirement.of(List.of(t)))).append('\t')
					.append(clean(t.getObjectInfo())).append('\t').append(clean(Way.name(t))).append('\n');
			}

			final StringBuilder requirementRows = new StringBuilder(header);
			requirementRows.append("id\tkind\tkey\tname\n");
			for (int i = 0; i < requirements.size(); i++)
			{
				final Requirement r = requirements.get(i);
				requirementRows.append(i).append('\t').append(r.kind).append('\t').append(r.key).append('\t')
					.append(clean(r.label)).append('\n');
			}

			Files.writeString(dir.resolve("ways.tsv"), ways);
			Files.writeString(dir.resolve("teleports.tsv"), teleports);
			Files.writeString(dir.resolve("hops.tsv"), hops);
			Files.writeString(dir.resolve("places.tsv"), placeRows);
			Files.writeString(dir.resolve("transports.tsv"), transportRows);
			Files.writeString(dir.resolve("requirements.tsv"), requirementRows);
		}

		private String transportList(List<Transport> list)
		{
			final StringBuilder s = new StringBuilder();
			for (Transport t : list)
			{
				final String key = t.getType() + "|" + t.getOrigin() + "|" + t.getDestination() + "|" + t.getObjectInfo()
					+ "|" + t.getDisplayInfo();
				Integer id = transportIds.get(key);
				if (id == null)
				{
					id = transports.size();
					transportIds.put(key, id);
					transports.add(t);
					// Their requirements get ids too, so the transports file can name them
					requirementList(Requirement.of(List.of(t)));
				}
				s.append(s.length() == 0 ? "" : ",").append(id);
			}
			return s.toString();
		}

		String requirementList(List<Requirement> list)
		{
			final StringBuilder s = new StringBuilder();
			for (Requirement r : list)
			{
				Integer id = requirementIds.get(r.label);
				if (id == null)
				{
					id = requirements.size();
					requirementIds.put(r.label, id);
					requirements.add(r);
				}
				s.append(s.length() == 0 ? "" : ",").append(id);
			}
			return s.toString();
		}

		private static String clean(String text)
		{
			return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').trim();
		}
	}
}
