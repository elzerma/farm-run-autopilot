import java.io.*;
import java.util.*;
import java.util.zip.*;

/** One-off: walking tiles between points on Shortest Path's collision map (BFS, 8 directions, game diagonal rules). */
public class Walk
{
	static final int R = 64, FLAGS = 4, PER_PLANE = R * R * FLAGS;
	static final Map<Long, BitSet> regions = new HashMap<>();

	static boolean get(int x, int y, int z, int flag)
	{
		final BitSet b = regions.get(((long) (x / R) << 32) | (y / R));
		if (b == null) return false;
		return b.get(z * PER_PLANE + ((y % R) * R + (x % R)) * FLAGS + flag);
	}
	static boolean n(int x, int y, int z) { return get(x, y, z, 0); }
	static boolean e(int x, int y, int z) { return get(x, y, z, 1); }
	static boolean s(int x, int y, int z) { return n(x, y - 1, z); }
	static boolean w(int x, int y, int z) { return e(x - 1, y, z); }
	static boolean blocked(int x, int y, int z) { return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z); }

	/** Steps from any walkable tile within `near` of (sx,sy) to any within `near` of (tx,ty). */
	static int walk(int sx, int sy, int tx, int ty, int z, int near)
	{
		final Map<Long, Integer> dist = new HashMap<>();
		final ArrayDeque<int[]> q = new ArrayDeque<>();
		for (int dx = -near; dx <= near; dx++) for (int dy = -near; dy <= near; dy++)
			if (!blocked(sx + dx, sy + dy, z)) { dist.put(key(sx + dx, sy + dy), 0); q.add(new int[]{sx + dx, sy + dy}); }
		while (!q.isEmpty())
		{
			final int[] p = q.poll();
			final int x = p[0], y = p[1], d = dist.get(key(x, y));
			if (Math.abs(x - tx) <= near && Math.abs(y - ty) <= near && !blocked(x, y, z)) return d;
			if (d > 2000) break;
			step(q, dist, x, y + 1, d, n(x, y, z));
			step(q, dist, x, y - 1, d, s(x, y, z));
			step(q, dist, x + 1, y, d, e(x, y, z));
			step(q, dist, x - 1, y, d, w(x, y, z));
			step(q, dist, x + 1, y + 1, d, n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z));
			step(q, dist, x - 1, y + 1, d, n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z));
			step(q, dist, x + 1, y - 1, d, s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z));
			step(q, dist, x - 1, y - 1, d, s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z));
		}
		return -1;
	}
	static void step(ArrayDeque<int[]> q, Map<Long, Integer> dist, int x, int y, int d, boolean ok)
	{
		if (ok && !dist.containsKey(key(x, y))) { dist.put(key(x, y), d + 1); q.add(new int[]{x, y}); }
	}
	static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

	public static void main(String[] args) throws Exception
	{
		try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0])))
		{
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null)
			{
				final String[] n = entry.getName().split("_");
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				in.transferTo(out);
				regions.put(((long) Integer.parseInt(n[0]) << 32) | Integer.parseInt(n[1]), BitSet.valueOf(out.toByteArray()));
			}
		}
		for (int i = 1; i + 5 < args.length; i += 6)
		{
			final String name = args[i];
			final int d = walk(Integer.parseInt(args[i + 1]), Integer.parseInt(args[i + 2]), Integer.parseInt(args[i + 3]),
				Integer.parseInt(args[i + 4]), 0, Integer.parseInt(args[i + 5]));
			System.out.println(name + ": " + (d < 0 ? "no path" : d + " tiles (" + Math.round(d * 0.3) + " s running)"));
		}
	}
}
