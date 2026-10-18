package com.farmrunautopilot.data.travel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * The travel atlas (tools/atlas, docs/plans/travel-rework.md) is built from tools/atlas/patches.tsv. Its rows for the
 * patches the plugin runs must match PatchPoints and Patch, so the atlas can't drift from where the plugin thinks the
 * patches are.
 */
public class AtlasPatchListTest
{
	@Test
	public void atlasPatchListMatchesThePlugin() throws IOException
	{
		final List<String> lines = Files.readAllLines(Paths.get("tools/atlas/patches.tsv"));
		final Map<String, String[]> plugin = new HashMap<>();
		String[] header = null;
		for (String line : lines)
		{
			if (line.startsWith("#") || line.isBlank())
			{
				continue;
			}
			final String[] row = line.split("\t", -1);
			if (header == null)
			{
				header = row;
				continue;
			}
			if (row[6].equals("plugin"))
			{
				plugin.put(row[0], row);
			}
		}
		assertNotNull(header);
		assertEquals("patch", header[0]);
		assertEquals("runs", header[6]);

		for (Map.Entry<Patch, WorldPoint> e : PatchPoints.all().entrySet())
		{
			final Patch patch = e.getKey();
			final String[] row = plugin.get(patch.name());
			assertNotNull(patch + " is missing from tools/atlas/patches.tsv", row);
			assertEquals(patch + " type", patch.getType().name(), row[1]);
			assertEquals(patch + " stop", patch.getLocation().name(), row[2]);
			assertEquals(patch + " tile", e.getValue(),
				new WorldPoint(Integer.parseInt(row[3]), Integer.parseInt(row[4]), Integer.parseInt(row[5])));
		}
		for (String id : plugin.keySet())
		{
			assertTrue(id + " is marked as run by the plugin but isn't in PatchPoints",
				PatchPoints.all().keySet().stream().anyMatch(p -> p.name().equals(id)));
		}
	}
}
