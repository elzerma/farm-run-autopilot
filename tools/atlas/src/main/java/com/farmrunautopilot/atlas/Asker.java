package com.farmrunautopilot.atlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.PathStep;
import shortestpath.pathfinder.PathTerminationReason;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderResult;
import shortestpath.transport.Transport;

/**
 * Asks Shortest Path's pathfinder one question at a time. Each thread keeps the configs of the accounts it asked for
 * lately, since setting one up rereads every transport. A search toward a place the account can't reach goes on until
 * it has covered the whole world, so a search running longer than the time limit is stopped and counted.
 */
final class Asker
{
	private static final int CONFIGS_PER_THREAD = 8;

	private final BubbleConfig.World world;
	private final boolean minimumTicks;
	private final long timeLimitMillis;
	private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r ->
	{
		final Thread t = new Thread(r, "atlas watchdog");
		t.setDaemon(true);
		return t;
	});
	/** Searches stopped at the time limit. */
	final AtomicInteger stopped = new AtomicInteger();
	private final ThreadLocal<Map<String, BubbleConfig>> configs = ThreadLocal.withInitial(() ->
		new LinkedHashMap<String, BubbleConfig>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, BubbleConfig> eldest)
			{
				return size() > CONFIGS_PER_THREAD;
			}
		});

	/**
	 * @param minimumTicks    count a transport with 0 ticks in Shortest Path's data as a short wait rather than free
	 * @param timeLimitMillis stop a search after this long (0: never)
	 */
	Asker(BubbleConfig.World world, boolean minimumTicks, long timeLimitMillis)
	{
		this.world = world;
		this.minimumTicks = minimumTicks;
		this.timeLimitMillis = timeLimitMillis;
	}

	/** The fastest way from {@code start} to any of {@code targets} for this account; null if there's none. */
	Way ask(Account account, int start, Set<Integer> targets, boolean teleports)
	{
		final String key = account.name + (teleports ? " | teleports" : "");
		BubbleConfig config = configs.get().get(key);
		if (config == null)
		{
			config = new BubbleConfig(account, teleports, world);
			config.refresh();
			configs.get().put(key, config);
		}
		final Pathfinder pathfinder = new Pathfinder(config, start, targets);
		final ScheduledFuture<?> limit = timeLimitMillis > 0
			? watchdog.schedule(pathfinder::cancel, timeLimitMillis, TimeUnit.MILLISECONDS) : null;
		pathfinder.run();
		if (limit != null)
		{
			limit.cancel(false);
		}
		final PathfinderResult result = pathfinder.getResult();
		if (result == null || result.getTerminationReason() == PathTerminationReason.CANCELLED)
		{
			stopped.incrementAndGet();
			return null;
		}
		if (!result.isReached())
		{
			return null;
		}
		final List<PathStep> path = pathfinder.getPath();
		int walkTiles = 0;
		int ticks = 0;
		final List<Transport> transports = new ArrayList<>();
		for (int i = 1; i < path.size(); i++)
		{
			final Transport transport = path.get(i).getTransport();
			if (transport != null)
			{
				ticks += Way.ticks(transport, minimumTicks);
				transports.add(transport);
			}
			else
			{
				walkTiles += WorldPointUtil.distanceBetween(path.get(i - 1).getPackedPosition(),
					path.get(i).getPackedPosition());
			}
		}
		return new Way(account.name, walkTiles, ticks, transports, Requirement.of(transports));
	}
}
