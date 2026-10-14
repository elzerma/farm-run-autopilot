import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * One-off (Sprint 8): for each stop, walking tiles from its patches out to MAX tiles on Shortest Path's collision
 * map, kept per BLOCKxBLOCK block (the smallest in the block), plane 0. Written as a gzip file the plugin reads:
 * int count; then per stop: UTF location name, int blockX0, int blockY0, int width, int height, width*height
 * unsigned bytes (tiles / 1, 255 = not within MAX).
 * Usage: java Grid collision-map.zip patches.tsv out.bin
 */
public class Grid
{
	static final int MAX = 150, BLOCK = 4;

	/** Doors and gates anyone can use: ground floor, a step or two, no skills, items, quests or varbits. */
	static Map<Long, List<Long>> doors(Path transports) throws IOException
	{
		final Map<Long, List<Long>> doors = new HashMap<>();
		for (String line : Files.readAllLines(transports))
		{
			if (line.startsWith("#") || line.isBlank()) continue;
			final String[] r = line.split("	", -1);
			if (r.length < 8 || !r[3].isEmpty() || !r[4].isEmpty() || !r[5].isEmpty() || !r[6].isEmpty() || !r[7].isEmpty()) continue;
			final String[] a = r[0].trim().split(" "), b = r[1].trim().split(" ");
			if (a.length < 3 || b.length < 3 || !a[2].equals("0") || !b[2].equals("0")) continue;
			final int ax = Integer.parseInt(a[0]), ay = Integer.parseInt(a[1]), bx = Integer.parseInt(b[0]), by = Integer.parseInt(b[1]);
			if (Math.abs(ax - bx) + Math.abs(ay - by) > 3) continue;
			doors.computeIfAbsent(Walk.key(ax, ay), k -> new ArrayList<>()).add(Walk.key(bx, by));
		}
		return doors;
	}

	public static void main(String[] args) throws Exception
	{
		Walk.main(new String[]{args[0]});
		final Map<Long, List<Long>> doors = doors(Paths.get(args[0]).getParent().resolve("transports").resolve("transports.tsv"));
		System.out.println(doors.size() + " door and gate tiles");
		final Map<String, List<int[]>> patches = new LinkedHashMap<>();
		final List<String> lines = Files.readAllLines(Paths.get(args[1]));
		for (String line : lines.subList(1, lines.size()))
		{
			final String[] r = line.split("\t", -1);
			if (Integer.parseInt(r[4]) != 0) continue;
			patches.computeIfAbsent(r[1], k -> new ArrayList<>()).add(new int[]{Integer.parseInt(r[2]), Integer.parseInt(r[3])});
		}
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(args[2]))))
		{
			out.writeInt(patches.size());
			for (Map.Entry<String, List<int[]>> e : patches.entrySet())
			{
				final Map<Long, Integer> dist = new HashMap<>();
				final ArrayDeque<Long> q = new ArrayDeque<>();
				for (int[] p : e.getValue())
					for (long k : Measure.aroundPatch(p[0], p[1], 0)) { dist.put(k, 0); q.add(k); }
				while (!q.isEmpty())
				{
					final long k = q.poll();
					final int d = dist.get(k);
					if (d >= MAX) continue;
					final int x = (int) (k >> 32), y = (int) k, z = 0;
					Measure.step(q, dist, x, y + 1, d, Walk.n(x, y, z));
					Measure.step(q, dist, x, y - 1, d, Walk.s(x, y, z));
					Measure.step(q, dist, x + 1, y, d, Walk.e(x, y, z));
					Measure.step(q, dist, x - 1, y, d, Walk.w(x, y, z));
					Measure.step(q, dist, x + 1, y + 1, d, Walk.n(x, y, z) && Walk.e(x, y + 1, z) && Walk.e(x, y, z) && Walk.n(x + 1, y, z));
					Measure.step(q, dist, x - 1, y + 1, d, Walk.n(x, y, z) && Walk.w(x, y + 1, z) && Walk.w(x, y, z) && Walk.n(x - 1, y, z));
					Measure.step(q, dist, x + 1, y - 1, d, Walk.s(x, y, z) && Walk.e(x, y - 1, z) && Walk.e(x, y, z) && Walk.s(x + 1, y, z));
					Measure.step(q, dist, x - 1, y - 1, d, Walk.s(x, y, z) && Walk.w(x, y - 1, z) && Walk.w(x, y, z) && Walk.s(x - 1, y, z));
					for (long to : doors.getOrDefault(k, Collections.emptyList()))
					{
						if (!dist.containsKey(to)) { dist.put(to, d + 1); q.add(to); }
					}
				}
				int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
				for (long k : dist.keySet())
				{
					final int bx = Math.floorDiv((int) (k >> 32), BLOCK), by = Math.floorDiv((int) k, BLOCK);
					minX = Math.min(minX, bx); minY = Math.min(minY, by); maxX = Math.max(maxX, bx); maxY = Math.max(maxY, by);
				}
				final int w = maxX - minX + 1, h = maxY - minY + 1;
				final byte[] grid = new byte[w * h];
				Arrays.fill(grid, (byte) 255);
				for (Map.Entry<Long, Integer> d : dist.entrySet())
				{
					final int bx = Math.floorDiv((int) (d.getKey() >> 32), BLOCK) - minX, by = Math.floorDiv((int) (long) d.getKey(), BLOCK) - minY;
					final int i = by * w + bx;
					if ((grid[i] & 0xff) > d.getValue()) grid[i] = (byte) (int) d.getValue();
				}
				out.writeUTF(e.getKey());
				out.writeInt(minX); out.writeInt(minY); out.writeInt(w); out.writeInt(h);
				out.write(grid);
				System.out.println(e.getKey() + ": " + dist.size() + " tiles, " + w + "x" + h + " blocks");
			}
		}
	}
}
