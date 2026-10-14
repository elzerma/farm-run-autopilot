package com.farmrunautopilot.data.travel;

import com.farmrunautopilot.data.Location;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * How far each stop is to walk from anywhere near it (Sean: start points by walking distance, so Castle Wars
 * isn't "near" Lletya across the forest). Walking tiles from each stop's patches out to {@link #MAX_TILES},
 * measured once on Shortest Path's collision map (BSD-2, commit 7e81a40, see THIRD_PARTY_NOTICES) and kept per
 * 4x4-tile block in {@code walk-grid.bin}, which ships with the plugin; nothing is fetched or measured in game.
 * Ground floor only: a point upstairs is read as the tile below it, a few tiles further.
 */
@Slf4j
public final class WalkGrid
{
	/** How far out the grid was measured. */
	public static final int MAX_TILES = 150;
	private static final int BLOCK = 4;
	/** A point upstairs or down a ladder counts as this much further. */
	private static final int OTHER_FLOOR_TILES = 10;
	private static final String RESOURCE = "/com/farmrunautopilot/walk-grid.bin";

	private static Map<Location, Grid> grids;

	private WalkGrid()
	{
	}

	/** One stop's grid: walking tiles per block, 255 where it's further than {@link #MAX_TILES}. */
	private static final class Grid
	{
		int x0;
		int y0;
		int width;
		int height;
		byte[] tiles;
	}

	/**
	 * Walking tiles from {@code point} to the nearest patch of {@code location}, or null if it's further than
	 * {@link #MAX_TILES} or can't be walked (across water, through a wall). Any thread.
	 */
	public static Integer tiles(Location location, WorldPoint point)
	{
		final Grid grid = grids().get(location);
		if (grid == null || point == null)
		{
			return null;
		}
		final int bx = Math.floorDiv(point.getX(), BLOCK) - grid.x0;
		final int by = Math.floorDiv(point.getY(), BLOCK) - grid.y0;
		if (bx < 0 || by < 0 || bx >= grid.width || by >= grid.height)
		{
			return null;
		}
		final int tiles = grid.tiles[by * grid.width + bx] & 0xff;
		if (tiles == 255)
		{
			return null;
		}
		return tiles + (point.getPlane() == 0 ? 0 : OTHER_FLOOR_TILES);
	}

	/** The grids exist (the data file loaded), so a null from {@link #tiles} really means "too far". */
	public static boolean loaded()
	{
		return !grids().isEmpty();
	}

	private static synchronized Map<Location, Grid> grids()
	{
		if (grids == null)
		{
			grids = load();
		}
		return grids;
	}

	private static Map<Location, Grid> load()
	{
		final Map<Location, Grid> loaded = new EnumMap<>(Location.class);
		try (InputStream raw = WalkGrid.class.getResourceAsStream(RESOURCE))
		{
			if (raw == null)
			{
				log.warn("Walking grid {} is missing; nearby stops fall back to straight-line distance", RESOURCE);
				return Collections.emptyMap();
			}
			try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw)))
			{
				final int count = in.readInt();
				for (int i = 0; i < count; i++)
				{
					final String name = in.readUTF();
					final Grid grid = new Grid();
					grid.x0 = in.readInt();
					grid.y0 = in.readInt();
					grid.width = in.readInt();
					grid.height = in.readInt();
					grid.tiles = new byte[grid.width * grid.height];
					in.readFully(grid.tiles);
					loaded.put(Location.valueOf(name), grid);
				}
			}
		}
		catch (IOException | IllegalArgumentException e)
		{
			log.warn("Couldn't read the walking grid; nearby stops fall back to straight-line distance", e);
			return Collections.emptyMap();
		}
		return loaded;
	}
}
