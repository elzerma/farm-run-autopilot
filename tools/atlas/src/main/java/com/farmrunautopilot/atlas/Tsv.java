package com.farmrunautopilot.atlas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import shortestpath.WorldPointUtil;

/** Tab-separated files: '#' lines are comments, the first other line is the header. */
final class Tsv
{
	final List<String> header;
	final List<String[]> rows;

	private Tsv(List<String> header, List<String[]> rows)
	{
		this.header = header;
		this.rows = rows;
	}

	static Tsv read(Path file) throws IOException
	{
		List<String> header = null;
		final List<String[]> rows = new ArrayList<>();
		for (String line : Files.readAllLines(file))
		{
			if (line.startsWith("#") || line.isBlank())
			{
				continue;
			}
			if (header == null)
			{
				header = Arrays.asList(line.split("\t", -1));
				continue;
			}
			rows.add(line.split("\t", -1));
		}
		return new Tsv(header == null ? List.of() : header, rows);
	}

	/** The rows without the header. */
	static List<String[]> rows(Path file) throws IOException
	{
		return read(file).rows;
	}

	/** The index of the first of these columns the header has. */
	int col(String... names)
	{
		for (String name : names)
		{
			final int i = header.indexOf(name);
			if (i >= 0)
			{
				return i;
			}
		}
		throw new IllegalArgumentException("No column " + Arrays.toString(names) + " in " + header);
	}

	/** "x y plane" to a point. */
	static int[] point(String text)
	{
		final String[] xyz = text.trim().split("\\s+");
		return new int[]{Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])};
	}

	/** A packed point as "x y plane". */
	static String text(int packed)
	{
		return WorldPointUtil.unpackWorldX(packed) + " " + WorldPointUtil.unpackWorldY(packed) + " "
			+ WorldPointUtil.unpackWorldPlane(packed);
	}
}
