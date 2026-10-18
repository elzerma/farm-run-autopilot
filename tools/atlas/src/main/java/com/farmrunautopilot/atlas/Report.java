package com.farmrunautopilot.atlas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/** tools/atlas/report.md: what was built, the checks, today's travel methods against the atlas, and what changed. */
final class Report
{
	private static final Path METHODS = Paths.get("../walk/methods.tsv");
	private static final Path TRAVEL_TIMES = Paths.get("../../src/main/java/com/farmrunautopilot/data/travel/TravelTimes.java");
	private static final String[] FILES = {"places.tsv", "teleports.tsv", "hops.tsv", "transports.tsv", "requirements.tsv",
		"ways.tsv"};

	private final String commit;
	private final Previous previous;
	private final Map<String, Long> counts = new LinkedHashMap<>();
	private final Map<String, Long> sizes = new LinkedHashMap<>();

	Report(String commit, Previous previous)
	{
		this.commit = commit;
		this.previous = previous;
	}

	void count(String label, long value)
	{
		counts.put(label, value);
	}

	void files(Path dir) throws IOException
	{
		for (String name : FILES)
		{
			sizes.put(name, Files.size(dir.resolve(name)));
		}
	}

	void write(Path file, Places places, List<Atlas.Answer> answers, Atlas.Writer writer) throws IOException
	{
		final StringBuilder s = new StringBuilder();
		s.append("# Travel atlas report\n\nBuilt ").append(LocalDate.now()).append(" by `tools/atlas` from Shortest Path `")
			.append(commit).append("`. Times are walk tiles x 0.3 s + transport ticks x 0.6 s (a 0-tick transport counts ")
			.append("as 1, an agility shortcut as 2), before the plugin's set-off overhead. Sweep budget ")
			.append(Atlas.SWEEP_BUDGET).append(" (banks ").append(Atlas.BANK_SWEEP_BUDGET).append("), ways over ").append((int) Atlas.WAY_BUDGET_S).append(" s dropped, up to ")
			.append(Atlas.MAX_QUESTIONS).append(" questions and ").append(Atlas.MAX_WAYS).append(" ways a pair.\n\n");

		s.append("## Build\n\n| What | Count |\n|---|---|\n");
		counts.forEach((k, v) -> s.append("| ").append(k).append(" | ").append(v).append(" |\n"));
		long total = 0;
		for (Map.Entry<String, Long> e : sizes.entrySet())
		{
			s.append("| `").append(e.getKey()).append("` | ").append(e.getValue() / 1024).append(" KB |\n");
			total += e.getValue();
		}
		s.append("| All data files | ").append(total / 1024).append(" KB |\n");

		// Per place: the ways that end there
		final Map<Places.Place, List<Atlas.Answer>> into = new HashMap<>();
		for (Atlas.Answer a : answers)
		{
			into.computeIfAbsent(a.to, k -> new ArrayList<>()).add(a);
		}

		s.append("\n## Checks\n\n");
		final List<String> noLanding = new ArrayList<>();
		final List<String> needsSomething = new ArrayList<>();
		final List<String> noWay = new ArrayList<>();
		for (Places.Patch patch : places.patches)
		{
			final Places.Place place = places.placeOfPatch.get(patch.id);
			final List<Atlas.Answer> list = into.getOrDefault(place, List.of());
			final boolean fromLanding = list.stream().anyMatch(a -> a.from.kind == Places.Kind.LANDING);
			final boolean free = list.stream().anyMatch(a -> a.ways.stream().anyMatch(w -> w.needs.isEmpty()));
			final String name = patch.id + (patch.plugin ? "" : " (atlas only)");
			if (list.isEmpty())
			{
				noWay.add(name);
			}
			else if (!fromLanding && patch.plugin)
			{
				noLanding.add(name);
			}
			if (!list.isEmpty() && !free)
			{
				needsSomething.add(name);
			}
		}
		s.append(noWay.isEmpty() ? "- Every patch has a way from somewhere.\n"
			: "- **No way to:** " + String.join(", ", noWay) + "\n");
		s.append(noLanding.isEmpty() ? "- Every patch the plugin runs has a way from a teleport or hop landing.\n"
			: "- **No way from any landing (plugin patches):** " + String.join(", ", noLanding) + "\n");
		s.append(needsSomething.isEmpty() ? "- Every reachable patch has a way that needs nothing.\n"
			: "- Only reachable with a requirement (no \"needs nothing\" way within the budget): "
				+ String.join(", ", needsSomething) + "\n");

		methods(s, places, answers);
		guessed(s, writer);
		changes(s, places, answers);
		Files.writeString(file, s);
	}

	/** Each TravelMethod's landing (tools/walk/methods.tsv) to its stop, against TravelTimes. */
	private void methods(StringBuilder s, Places places, List<Atlas.Answer> answers) throws IOException
	{
		if (!Files.exists(METHODS) || !Files.exists(TRAVEL_TIMES))
		{
			return;
		}
		final Map<String, Integer> tiles = new HashMap<>();
		final Map<String, Double> extra = new HashMap<>();
		final String source = Files.readString(TRAVEL_TIMES);
		final Matcher t = Pattern.compile("tiles\\(TravelMethod\\.(\\w+), (\\d+)\\)").matcher(source);
		while (t.find())
		{
			tiles.put(t.group(1), Integer.parseInt(t.group(2)));
		}
		final Matcher x = Pattern.compile("extra\\(TravelMethod\\.(\\w+), ([\\d.]+)\\)").matcher(source);
		while (x.find())
		{
			extra.put(x.group(1), Double.parseDouble(x.group(2)));
		}
		final Map<Integer, Places.Place> landingByTile = new HashMap<>();
		for (Places.Place p : places.all)
		{
			if (p.kind == Places.Kind.LANDING)
			{
				landingByTile.put(p.tile, p);
			}
		}

		s.append("\n## Today's travel methods against the atlas\n\nEach `TravelMethod` from its landing (`tools/walk/methods.tsv`) ")
			.append("to the nearest patch the plugin runs at its stop. Plugin = `TravelTimes` tiles x 0.3 s + its extra seconds. ")
			.append("Flagged when 3 s or more apart.\n\n| Method | Plugin | Atlas fastest | Needs | Atlas, needs nothing | Gap |\n")
			.append("|---|---|---|---|---|---|\n");
		final Tsv methods = Tsv.read(METHODS);
		final int method = methods.col("method");
		final int location = methods.col("location");
		final int raw = methods.col("raw_landing");
		for (String[] r : methods.rows)
		{
			final Integer methodTiles = tiles.get(r[method]);
			final double plugin = methodTiles == null ? Double.NaN
				: methodTiles * Way.SECONDS_PER_TILE + extra.getOrDefault(r[method], 0.0);
			final Places.Place landing = landing(landingByTile, Tsv.point(r[raw]));
			Way fastest = null;
			Way free = null;
			if (landing != null)
			{
				for (Atlas.Answer a : answers)
				{
					if (a.from != landing || !servesStop(places, a.to, r[location]))
					{
						continue;
					}
					for (Way w : a.ways)
					{
						fastest = fastest == null || w.seconds() < fastest.seconds() ? w : fastest;
						if (w.needs.isEmpty())
						{
							free = free == null || w.seconds() < free.seconds() ? w : free;
						}
					}
				}
			}
			final String gap = fastest == null || Double.isNaN(plugin) ? ""
				: String.format(Locale.ROOT, "%+.1f s%s", fastest.seconds() - plugin,
					Math.abs(fastest.seconds() - plugin) >= 3 ? " **" : "");
			s.append("| ").append(r[method]).append(" | ")
				.append(Double.isNaN(plugin) ? "" : String.format(Locale.ROOT, "%.1f s", plugin)).append(" | ")
				.append(landing == null ? "not a landing in the atlas" : fastest == null ? "no way" : fastest.time() + " (" + fastest.way() + ")")
				.append(" | ").append(fastest == null ? "" : fastest.needsText()).append(" | ")
				.append(free == null ? "" : free.time()).append(" | ").append(gap).append(" |\n");
		}
	}

	private static boolean servesStop(Places places, Places.Place to, String stop)
	{
		if (to.kind != Places.Kind.PATCH)
		{
			return false;
		}
		for (String name : to.names)
		{
			for (Places.Patch p : places.patches)
			{
				if (p.id.equals(name) && p.plugin && p.stop.equals(stop))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** The landing at this tile, or the nearest within 2 tiles. */
	private static Places.Place landing(Map<Integer, Places.Place> byTile, int[] p)
	{
		for (int r = 0; r <= 2; r++)
		{
			for (int dx = -r; dx <= r; dx++)
			{
				for (int dy = -r; dy <= r; dy++)
				{
					final Places.Place found = byTile.get(WorldPointUtil.packWorldPoint(p[0] + dx, p[1] + dy, p[2]));
					if (found != null)
					{
						return found;
					}
				}
			}
		}
		return null;
	}

	private static void guessed(StringBuilder s, Atlas.Writer writer)
	{
		s.append("\n## Transports with 0 ticks in Shortest Path's data\n\nCounted as 1 tick (2 for an agility shortcut). ")
			.append("Worth a real duration from the wiki or the dev log when one decides a route.\n\n");
		final Map<String, Integer> names = new TreeMap<>();
		for (Transport t : writer.transports)
		{
			if (Way.guessed(t))
			{
				names.merge(t.getType() + ": " + Way.name(t), 1, Integer::sum);
			}
		}
		if (names.isEmpty())
		{
			s.append("None.\n");
		}
		names.forEach((k, v) -> s.append("- ").append(k).append(v > 1 ? " (" + v + ")" : "").append('\n'));
	}

	private void changes(StringBuilder s, Places places, List<Atlas.Answer> answers)
	{
		s.append("\n## Changes since the last build\n\n");
		if (previous == null)
		{
			s.append("First build.\n");
			return;
		}
		final Map<String, Double> now = new HashMap<>();
		for (Atlas.Answer a : answers)
		{
			now.put(key(a.from) + " > " + key(a.to), a.ways.get(0).seconds());
		}
		int added = 0;
		int removed = 0;
		final List<String> moved = new ArrayList<>();
		for (Map.Entry<String, Double> e : now.entrySet())
		{
			final Double before = previous.best.get(e.getKey());
			if (before == null)
			{
				added++;
			}
			else if (Math.abs(e.getValue() - before) > 0.1 * before && Math.abs(e.getValue() - before) >= 1)
			{
				moved.add(String.format(Locale.ROOT, "- %s: %.1f s, was %.1f s", e.getKey(), e.getValue(), before));
			}
		}
		for (String k : previous.best.keySet())
		{
			if (!now.containsKey(k))
			{
				removed++;
			}
		}
		s.append("Pairs added: ").append(added).append(", removed: ").append(removed).append(", fastest way moved more ")
			.append("than 10%: ").append(moved.size()).append(".\n\n");
		moved.stream().limit(80).forEach(m -> s.append(m).append('\n'));
		if (moved.size() > 80)
		{
			s.append("- ... and ").append(moved.size() - 80).append(" more\n");
		}
	}

	static String key(Places.Place p)
	{
		return p.kind + " " + Tsv.text(p.tile);
	}

	/** The fastest way per pair in the atlas files already there, to compare a rebuild with. */
	static final class Previous
	{
		final Map<String, Double> best = new HashMap<>();

		static Previous read(Path dir) throws IOException
		{
			final Path places = dir.resolve("places.tsv");
			final Path ways = dir.resolve("ways.tsv");
			if (!Files.exists(places) || !Files.exists(ways))
			{
				return null;
			}
			final Map<String, String> keys = new HashMap<>();
			final Tsv p = Tsv.read(places);
			for (String[] r : p.rows)
			{
				keys.put(r[p.col("id")], r[p.col("kind")] + " " + r[p.col("x")] + " " + r[p.col("y")] + " " + r[p.col("plane")]);
			}
			final Previous previous = new Previous();
			final Tsv w = Tsv.read(ways);
			for (String[] r : w.rows)
			{
				final String key = keys.get(r[w.col("from")]) + " > " + keys.get(r[w.col("to")]);
				final double seconds = Integer.parseInt(r[w.col("tiles")]) * Way.SECONDS_PER_TILE
					+ Integer.parseInt(r[w.col("ticks")]) * Way.SECONDS_PER_TICK;
				previous.best.merge(key, seconds, Math::min);
			}
			return previous;
		}
	}
}
