package com.farmrunautopilot.data.travel.atlas;

import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * Atlas teleports tied to the plugin's travel items and spells (sprint T3's groundwork), and a cross-check: every
 * TravelMethod lands where a teleport of its item or spell lands in the atlas (tools/walk/methods.tsv's landings).
 */
public class AtlasLinksTest
{
	private static final TravelAtlas ATLAS = TravelAtlas.get();
	/** A landing this close counts as the same (the atlas merges landings within 2 tiles). */
	private static final int SAME_LANDING = 3;

	@Test
	public void everyTravelItemAndSpellHasAnAtlasTeleport()
	{
		final List<String> missing = new ArrayList<>();
		for (TravelMethod method : TravelMethod.values())
		{
			if (method.getItem() != null && teleports(method.getItem(), null).isEmpty())
			{
				missing.add(method + " (item " + method.getItem() + ")");
			}
			if (method.getSpell() != null && teleports(null, method.getSpell()).isEmpty())
			{
				missing.add(method + " (spell " + method.getSpell() + ")");
			}
		}
		assertTrue("No atlas teleport for: " + missing, missing.isEmpty());
	}

	@Test
	public void travelMethodsLandWhereTheAtlasSays() throws IOException
	{
		final Map<String, WorldPoint> landings = new HashMap<>();
		String[] header = null;
		for (String line : Files.readAllLines(Paths.get("tools/walk/methods.tsv")))
		{
			final String[] r = line.split("\t", -1);
			if (header == null)
			{
				header = r;
				continue;
			}
			final String[] xyz = r[5].split(" ");
			landings.put(r[0], new WorldPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
		}
		final List<String> off = new ArrayList<>();
		for (TravelMethod method : TravelMethod.values())
		{
			final WorldPoint landing = landings.get(method.name());
			if (landing == null || method.getItem() == null && method.getSpell() == null)
			{
				continue;
			}
			int nearest = Integer.MAX_VALUE;
			for (TravelAtlas.Jump t : teleports(method.getItem(), method.getSpell()))
			{
				final WorldPoint p = ATLAS.place(t.getTo()).getPoint();
				if (p.getPlane() == landing.getPlane())
				{
					nearest = Math.min(nearest, Math.max(Math.abs(p.getX() - landing.getX()), Math.abs(p.getY() - landing.getY())));
				}
			}
			if (nearest > SAME_LANDING)
			{
				off.add(method + " (" + (nearest == Integer.MAX_VALUE ? "no landing on its plane" : nearest + " tiles") + ")");
			}
		}
		assertTrue("Landing elsewhere than any atlas teleport of the same item or spell: " + off, off.isEmpty());
	}

	@Test
	public void everyTravelMethodHasItsLandingAndAnAtlasWayThere() throws IOException
	{
		// The landings in the code are tools/walk/methods.tsv's (the balloon's from the atlas)
		final Map<String, String> measured = new HashMap<>();
		for (String line : Files.readAllLines(Paths.get("tools/walk/methods.tsv")))
		{
			final String[] r = line.split("\t", -1);
			measured.put(r[0], r[5]);
		}
		final List<String> wrong = new ArrayList<>();
		for (TravelMethod method : TravelMethod.values())
		{
			final WorldPoint landing = AtlasLinks.landing(method);
			if (landing == null)
			{
				wrong.add(method + " (no landing)");
				continue;
			}
			final String tsv = measured.get(method.name());
			if (tsv != null && !tsv.equals(landing.getX() + " " + landing.getY() + " " + landing.getPlane()))
			{
				wrong.add(method + " (" + landing + ", methods.tsv " + tsv + ")");
			}
			// So the planner can tell an atlas way is this method: a teleport or hop of its kind lands there
			if (!recognisable(method, landing))
			{
				wrong.add(method + " (no atlas teleport or hop of its kind lands at " + landing + ")");
			}
		}
		assertTrue("Travel methods the planner can't tell from the atlas: " + wrong, wrong.isEmpty());
	}

	/** An atlas teleport of the method's item or spell, or a hop of its kind, lands at the method's landing. */
	private static boolean recognisable(TravelMethod method, WorldPoint landing)
	{
		final String hopType;
		switch (method.getKind())
		{
			case FAIRY_RING:
				hopType = "FAIRY_RING";
				break;
			case SPIRIT_TREE:
				hopType = "SPIRIT_TREE";
				break;
			case BALLOON:
				hopType = "HOT_AIR_BALLOON";
				break;
			default:
				hopType = null;
				break;
		}
		if (hopType != null)
		{
			return ATLAS.getHops().stream().anyMatch(h -> h.getType().equals(hopType)
				&& AtlasLinks.sameLanding(ATLAS.place(h.getTo()).getPoint(), landing));
		}
		final List<TravelAtlas.Jump> teleports = method.getKind() == com.farmrunautopilot.data.travel.TravelKind.QUETZAL
			? quetzalWhistle() : teleports(method.getItem(), method.getSpell());
		return teleports.stream().anyMatch(t -> AtlasLinks.sameLanding(ATLAS.place(t.getTo()).getPoint(), landing));
	}

	private static List<TravelAtlas.Jump> quetzalWhistle()
	{
		final List<TravelAtlas.Jump> found = new ArrayList<>();
		for (TravelAtlas.Jump t : ATLAS.getTeleports())
		{
			if (t.getType().equals("QUETZAL_WHISTLE"))
			{
				found.add(t);
			}
		}
		return found;
	}

	/** Atlas teleports of this item, or of this spell (either may be null). */
	private static List<TravelAtlas.Jump> teleports(TravelItem item, Spell spell)
	{
		final List<TravelAtlas.Jump> found = new ArrayList<>();
		for (TravelAtlas.Jump t : ATLAS.getTeleports())
		{
			if (item != null && AtlasLinks.item(ATLAS, t) == item || spell != null && AtlasLinks.spell(ATLAS, t) == spell)
			{
				found.add(t);
			}
		}
		return found;
	}
}
