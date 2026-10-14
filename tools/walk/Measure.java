import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * One-off (Sprint 8): walking tiles on Shortest Path's collision map from each landing to the nearest patch of
 * its stop, each patch to its bank, spirit tree, quetzal pad and fairy ring, and a few named walks.
 * Usage: java Measure collision-map.zip sp8-dir
 */
public class Measure
{
	static final int MAX = 3000;

	/** Patches: object tile; a player stands next to it, so any walkable tile in [x-1, x+3] x [y-1, y+3]. */
	static Set<Long> aroundPatch(int x, int y, int z)
	{
		final Set<Long> t = new HashSet<>();
		for (int dx = -1; dx <= 3; dx++) for (int dy = -1; dy <= 3; dy++)
			if (!Walk.blocked(x + dx, y + dy, z)) t.add(Walk.key(x + dx, y + dy));
		return t;
	}

	/** Objects you use (booth, tree, ring, pad): walkable tiles within 2. */
	static Set<Long> around(int x, int y, int z, int r)
	{
		final Set<Long> t = new HashSet<>();
		for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++)
			if (!Walk.blocked(x + dx, y + dy, z)) t.add(Walk.key(x + dx, y + dy));
		return t;
	}

	static int bfs(Set<Long> from, Set<Long> to, int z)
	{
		if (from.isEmpty() || to.isEmpty()) return -2;
		final Map<Long, Integer> dist = new HashMap<>();
		final ArrayDeque<Long> q = new ArrayDeque<>();
		for (long k : from) { dist.put(k, 0); q.add(k); }
		while (!q.isEmpty())
		{
			final long k = q.poll();
			final int d = dist.get(k);
			if (to.contains(k)) return d;
			if (d > MAX) return -1;
			final int x = (int) (k >> 32), y = (int) k;
			step(q, dist, x, y + 1, d, Walk.n(x, y, z));
			step(q, dist, x, y - 1, d, Walk.s(x, y, z));
			step(q, dist, x + 1, y, d, Walk.e(x, y, z));
			step(q, dist, x - 1, y, d, Walk.w(x, y, z));
			step(q, dist, x + 1, y + 1, d, Walk.n(x, y, z) && Walk.e(x, y + 1, z) && Walk.e(x, y, z) && Walk.n(x + 1, y, z));
			step(q, dist, x - 1, y + 1, d, Walk.n(x, y, z) && Walk.w(x, y + 1, z) && Walk.w(x, y, z) && Walk.n(x - 1, y, z));
			step(q, dist, x + 1, y - 1, d, Walk.s(x, y, z) && Walk.e(x, y - 1, z) && Walk.e(x, y, z) && Walk.s(x + 1, y, z));
			step(q, dist, x - 1, y - 1, d, Walk.s(x, y, z) && Walk.w(x, y - 1, z) && Walk.w(x, y, z) && Walk.s(x - 1, y, z));
		}
		return -1;
	}

	static void step(ArrayDeque<Long> q, Map<Long, Integer> dist, int x, int y, int d, boolean ok)
	{
		final long k = Walk.key(x, y);
		if (ok && !dist.containsKey(k)) { dist.put(k, d + 1); q.add(k); }
	}

	static List<String[]> tsv(Path p) throws IOException
	{
		final List<String[]> rows = new ArrayList<>();
		for (String line : Files.readAllLines(p))
		{
			if (line.startsWith("#") || line.isBlank()) continue;
			rows.add(line.split("\t", -1));
		}
		return rows.subList(1, rows.size());
	}

	public static void main(String[] args) throws Exception
	{
		Walk.main(new String[]{args[0]});
		final Path dir = Paths.get(args[1]);
		// location -> patch tiles
		final Map<String, List<int[]>> patches = new LinkedHashMap<>();
		final Map<String, int[]> patchByName = new LinkedHashMap<>();
		for (String[] r : tsv(dir.resolve("patches.tsv")))
		{
			final int[] t = {Integer.parseInt(r[2]), Integer.parseInt(r[3]), Integer.parseInt(r[4])};
			patches.computeIfAbsent(r[1], k -> new ArrayList<>()).add(t);
			patchByName.put(r[0], t);
		}
		System.out.println("== landing -> nearest patch of its stop");
		for (String[] r : tsv(dir.resolve("methods.tsv")))
		{
			final int x = Integer.parseInt(r[2]), y = Integer.parseInt(r[3]), z = Integer.parseInt(r[4]);
			Set<Long> to = new HashSet<>();
			for (int[] p : patches.getOrDefault(r[1], Collections.emptyList())) if (p[2] == z) to.addAll(aroundPatch(p[0], p[1], z));
			System.out.println("M\t" + r[0] + "\t" + bfs(around(x, y, z, 1), to, z));
		}
		System.out.println("== patch -> bank");
		for (String[] r : tsv(dir.resolve("banks.tsv")))
		{
			final int bx = Integer.parseInt(r[1]), by = Integer.parseInt(r[2]), bz = Integer.parseInt(r[3]);
			for (Map.Entry<String, int[]> e : patchByName.entrySet())
			{
				final int[] p = e.getValue();
				if (!patchLocation(dir, e.getKey()).equals(r[0])) continue;
				System.out.println("B\t" + r[0] + "\t" + e.getKey() + "\t" + (p[2] != bz ? "plane" : bfs(aroundPatch(p[0], p[1], p[2]), around(bx, by, bz, 1), bz)));
			}
		}
		System.out.println("== patch -> extras (tree, ring, pad, balloon)");
		for (String[] r : tsv(dir.resolve("extras.tsv")))
		{
			final int ex = Integer.parseInt(r[3]), ey = Integer.parseInt(r[4]), ez = Integer.parseInt(r[5]);
			for (Map.Entry<String, int[]> e : patchByName.entrySet())
			{
				if (!patchLocation(dir, e.getKey()).equals(r[1])) continue;
				final int[] p = e.getValue();
				System.out.println("X\t" + r[0] + "\t" + r[2] + "\t" + e.getKey() + "\t" + (p[2] != ez ? "plane" : bfs(aroundPatch(p[0], p[1], p[2]), around(ex, ey, ez, 2), ez)));
			}
		}
		System.out.println("== named walks");
		for (int i = 2; i + 6 < args.length; i += 7)
		{
			final int z = 0;
			System.out.println("W\t" + args[i] + "\t" + bfs(around(Integer.parseInt(args[i + 1]), Integer.parseInt(args[i + 2]), z, Integer.parseInt(args[i + 5])),
				around(Integer.parseInt(args[i + 3]), Integer.parseInt(args[i + 4]), z, Integer.parseInt(args[i + 6])), z));
		}
	}

	static Map<String, String> locs;
	static String patchLocation(Path dir, String patch) throws IOException
	{
		if (locs == null)
		{
			locs = new HashMap<>();
			for (String[] r : tsv(dir.resolve("patches.tsv"))) locs.put(r[0], r[1]);
		}
		return locs.getOrDefault(patch, "");
	}
}
