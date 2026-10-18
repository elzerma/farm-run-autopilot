package com.farmrunautopilot.atlas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;

/** One way the bubble found: walking tiles, transport ticks, the transports in order and what they need. */
final class Way
{
	/** Running, two tiles a tick, as the plugin's TravelTimes. */
	static final double SECONDS_PER_TILE = 0.3;
	static final double SECONDS_PER_TICK = 0.6;

	final String account;
	final int walkTiles;
	final int ticks;
	final List<Transport> transports;
	final List<Requirement> needs;

	Way(String account, int walkTiles, int ticks, List<Transport> transports, List<Requirement> needs)
	{
		this.account = account;
		this.walkTiles = walkTiles;
		this.ticks = ticks;
		this.transports = transports;
		this.needs = needs;
	}

	/**
	 * Ticks a closed door or gate takes beyond Shortest Path's 1: it opens, then the player walks on (elzerma,
	 * 2026-10-18: a route through Lumbridge castle's doors, which let one through at a time).
	 */
	static final int DOOR_EXTRA_TICKS = 2;

	/**
	 * A transport's ticks. With {@code minimum}, a 0 in Shortest Path's data (a gap, not a free climb) counts as 2
	 * ticks for an agility shortcut and 1 for anything else, and a door or gate gets its extra ticks.
	 */
	static int ticks(Transport t, boolean minimum)
	{
		if (!minimum)
		{
			return t.getDuration();
		}
		final int ticks = t.getDuration() > 0 ? t.getDuration() : t.getType() == TransportType.AGILITY_SHORTCUT ? 2 : 1;
		return ticks + (isDoor(t) ? DOOR_EXTRA_TICKS : 0);
	}

	/** A closed door or gate to open or go through (not a trapdoor, which is climbed). */
	static boolean isDoor(Transport t)
	{
		if (t.getType() != TransportType.TRANSPORT || t.getObjectInfo() == null)
		{
			return false;
		}
		final String info = t.getObjectInfo().toLowerCase(Locale.ROOT);
		return (info.startsWith("open ") || info.startsWith("go-through ") || info.startsWith("pass "))
			&& (info.contains("door") || info.contains("gate")) && !info.contains("trapdoor");
	}

	/** Whether this transport's 0 ticks were raised to a minimum. */
	static boolean guessed(Transport t)
	{
		return t.getDuration() <= 0;
	}

	double seconds()
	{
		return walkTiles * SECONDS_PER_TILE + ticks * SECONDS_PER_TICK;
	}

	String time()
	{
		return String.format(Locale.ROOT, "%.1f s", seconds());
	}

	/** The transports used, in order, e.g. "walk 67 tiles; Cross Stepping stone (2t)". */
	String way()
	{
		if (transports.isEmpty())
		{
			return "walk " + walkTiles;
		}
		final List<String> parts = new ArrayList<>();
		for (Transport t : transports)
		{
			parts.add(name(t) + " (" + t.getDuration() + "t)");
		}
		return "walk " + walkTiles + " tiles; " + String.join(" > ", parts);
	}

	String needsText()
	{
		final List<String> labels = new ArrayList<>();
		for (Requirement r : needs)
		{
			labels.add(r.label);
		}
		return labels.isEmpty() ? "nothing" : String.join(", ", labels);
	}

	String signature()
	{
		return walkTiles + "|" + way();
	}

	String describe()
	{
		return time() + ", " + way() + " [" + account + "]";
	}

	/** True when this way is at least as quick as {@code other} and needs nothing {@code other} doesn't. */
	boolean dominates(Way other)
	{
		if (seconds() > other.seconds() + 0.05)
		{
			return false;
		}
		final Set<String> theirs = new HashSet<>();
		for (Requirement r : other.needs)
		{
			theirs.add(r.label);
		}
		for (Requirement r : needs)
		{
			if (!theirs.contains(r.label))
			{
				return false;
			}
		}
		return true;
	}

	static String name(Transport t)
	{
		if (t.getDisplayInfo() != null && !t.getDisplayInfo().isBlank())
		{
			return t.getDisplayInfo();
		}
		if (t.getObjectInfo() != null && !t.getObjectInfo().isBlank())
		{
			// "Cross Stepping stone 12345": drop the id
			return t.getObjectInfo().replaceAll("\\s+-?\\d+$", "");
		}
		return t.getType().name().toLowerCase().replace('_', ' ');
	}
}
