package com.farmrunautopilot.atlas;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/**
 * Works backwards from a patch, bank or hub over Shortest Path's map and every transport that isn't a teleport or a
 * hop, giving how far every tile within a budget is from it (in Shortest Path's units: a tile walked is 1, a transport
 * its ticks). Only for choosing which starting places are worth asking the bubble about; the bubble gives the real
 * answers.
 */
final class Sweep
{
	private final BubbleConfig.World world;
	/** Transport destination to its origins and costs: {origin, cost, origin, cost, ...}. */
	private final Map<Integer, int[]> reverse = new HashMap<>();
	private final int maxTransportCost;
	private final ThreadLocal<CollisionMap> map;

	/**
	 * @param needsNothing only transports that need nothing at all (doors, stairs and the like), for the "needs nothing"
	 *                     account; otherwise every transport that isn't a teleport
	 */
	Sweep(BubbleConfig.World world, boolean needsNothing)
	{
		this.world = world;
		this.map = ThreadLocal.withInitial(() -> new CollisionMap(world.map));
		int max = 1;
		for (Set<Transport> set : world.transports.values())
		{
			for (Transport t : set)
			{
				if (Places.isTeleport(t) || Places.HUB_TYPES.contains(t.getType()) || t.getType() == TransportType.SEASONAL_TRANSPORTS
					|| t.getOrigin() == Transport.UNDEFINED_ORIGIN || Places.insidePoh(t.getOrigin())
					|| Places.insidePoh(t.getDestination()) || needsNothing && !Requirement.of(List.of(t)).isEmpty())
				{
					continue;
				}
				final int cost = Math.max(1, t.getDuration());
				max = Math.max(max, cost);
				final int[] old = reverse.get(t.getDestination());
				final int[] grown = old == null ? new int[2] : Arrays.copyOf(old, old.length + 2);
				grown[grown.length - 2] = t.getOrigin();
				grown[grown.length - 1] = cost;
				reverse.put(t.getDestination(), grown);
			}
		}
		this.maxTransportCost = max;
	}

	/** Cost to reach any of {@code targets} from every tile within {@code budget}. */
	IntIntMap costsTo(Set<Integer> targets, int budget)
	{
		final CollisionMap m = map.get();
		final IntIntMap best = new IntIntMap(1 << 16);
		final IntList[] buckets = new IntList[budget + maxTransportCost + 2];
		for (int t : targets)
		{
			best.put(t, 0);
			bucket(buckets, 0).add(t);
		}
		for (int cost = 0; cost <= budget; cost++)
		{
			final IntList bucket = buckets[cost];
			if (bucket == null)
			{
				continue;
			}
			for (int i = 0; i < bucket.size; i++)
			{
				final int tile = bucket.items[i];
				if (best.get(tile) != cost)
				{
					continue;
				}
				final int x = WorldPointUtil.unpackWorldX(tile);
				final int y = WorldPointUtil.unpackWorldY(tile);
				final int z = WorldPointUtil.unpackWorldPlane(tile);
				// Walking: the tiles that can step onto this one (the game's diagonal rule, as tools/walk)
				relax(best, buckets, budget, x, y - 1, z, cost + 1, m.n(x, y - 1, z));
				relax(best, buckets, budget, x, y + 1, z, cost + 1, m.s(x, y + 1, z));
				relax(best, buckets, budget, x - 1, y, z, cost + 1, m.e(x - 1, y, z));
				relax(best, buckets, budget, x + 1, y, z, cost + 1, m.w(x + 1, y, z));
				relax(best, buckets, budget, x - 1, y - 1, z, cost + 1, m.n(x - 1, y - 1, z) && m.e(x - 1, y, z)
					&& m.e(x - 1, y - 1, z) && m.n(x, y - 1, z));
				relax(best, buckets, budget, x + 1, y - 1, z, cost + 1, m.n(x + 1, y - 1, z) && m.w(x + 1, y, z)
					&& m.w(x + 1, y - 1, z) && m.n(x, y - 1, z));
				relax(best, buckets, budget, x - 1, y + 1, z, cost + 1, m.s(x - 1, y + 1, z) && m.e(x - 1, y, z)
					&& m.e(x - 1, y + 1, z) && m.s(x, y + 1, z));
				relax(best, buckets, budget, x + 1, y + 1, z, cost + 1, m.s(x + 1, y + 1, z) && m.w(x + 1, y, z)
					&& m.w(x + 1, y + 1, z) && m.s(x, y + 1, z));
				final int[] origins = reverse.get(tile);
				if (origins != null)
				{
					for (int k = 0; k < origins.length; k += 2)
					{
						final int origin = origins[k];
						final int next = cost + origins[k + 1];
						if (next <= budget && next < best.get(origin))
						{
							best.put(origin, next);
							bucket(buckets, next).add(origin);
						}
					}
				}
			}
			buckets[cost] = null;
		}
		return best;
	}

	private static void relax(IntIntMap best, IntList[] buckets, int budget, int x, int y, int z, int cost, boolean ok)
	{
		if (!ok || cost > budget)
		{
			return;
		}
		final int tile = WorldPointUtil.packWorldPoint(x, y, z);
		if (cost < best.get(tile))
		{
			best.put(tile, cost);
			bucket(buckets, cost).add(tile);
		}
	}

	private static IntList bucket(IntList[] buckets, int cost)
	{
		if (buckets[cost] == null)
		{
			buckets[cost] = new IntList();
		}
		return buckets[cost];
	}

	/** A growable list of ints. */
	static final class IntList
	{
		int[] items = new int[64];
		int size;

		void add(int value)
		{
			if (size == items.length)
			{
				items = Arrays.copyOf(items, size * 2);
			}
			items[size++] = value;
		}
	}

	/** An int to int map; missing keys read as Integer.MAX_VALUE. */
	static final class IntIntMap
	{
		private static final int EMPTY = Integer.MIN_VALUE;
		private int[] keys;
		private int[] values;
		private int size;

		IntIntMap(int capacity)
		{
			keys = new int[capacity];
			values = new int[capacity];
			Arrays.fill(keys, EMPTY);
		}

		int get(int key)
		{
			int i = index(key, keys.length);
			while (keys[i] != EMPTY)
			{
				if (keys[i] == key)
				{
					return values[i];
				}
				i = (i + 1) & (keys.length - 1);
			}
			return Integer.MAX_VALUE;
		}

		void put(int key, int value)
		{
			if (size * 2 >= keys.length)
			{
				grow();
			}
			int i = index(key, keys.length);
			while (keys[i] != EMPTY && keys[i] != key)
			{
				i = (i + 1) & (keys.length - 1);
			}
			if (keys[i] == EMPTY)
			{
				size++;
			}
			keys[i] = key;
			values[i] = value;
		}

		int size()
		{
			return size;
		}

		private void grow()
		{
			final int[] oldKeys = keys;
			final int[] oldValues = values;
			keys = new int[oldKeys.length * 2];
			values = new int[oldKeys.length * 2];
			Arrays.fill(keys, EMPTY);
			size = 0;
			for (int i = 0; i < oldKeys.length; i++)
			{
				if (oldKeys[i] != EMPTY)
				{
					put(oldKeys[i], oldValues[i]);
				}
			}
		}

		private static int index(int key, int length)
		{
			int h = key * 0x9E3779B9;
			return (h ^ (h >>> 16)) & (length - 1);
		}
	}
}
