package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.PatchPoints;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Perspective;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines the patch the current step is about and, when paying, the gardener (SPEC 13.4). Reads only the
 * session's precomputed highlights and the tracked objects, so each frame is cheap.
 */
public class HighlightOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 2;

	private final Client client;
	private final RunSession session;
	private final SceneTracker scene;
	private final FarmRunAutopilotConfig config;
	private final ModelOutlineRenderer outlines;

	@Inject
	HighlightOverlay(Client client, RunSession session, SceneTracker scene, FarmRunAutopilotConfig config,
		ModelOutlineRenderer outlines)
	{
		this.client = client;
		this.session = session;
		this.scene = scene;
		this.config = config;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Highlights highlights = session.getView().getHighlights();
		if (highlights.getPatch() != null && config.highlightPatch())
		{
			final List<GameObject> objects = scene.objectsFor(highlights.getPatch());
			if (objects.isEmpty())
			{
				// Not matched to an object (or not loaded yet): mark the patch's tile instead
				markTile(graphics, PatchPoints.of(highlights.getPatch()));
			}
			for (GameObject object : objects)
			{
				outlines.drawOutline(object, OUTLINE_WIDTH, config.patchColour(), OUTLINE_FEATHER);
			}
			// And its clickbox over the tile the walking line and arrow go to, its nearest to the player (elzerma,
			// 2026-11-01: "clickbox highlighting for nearest tile of the patch?")
			if (!objects.isEmpty())
			{
				markPatchTile(graphics, objects, highlights.getPatchTile());
			}
		}
		// The compost bin to open, fill or empty (P3), in the patch colour
		final GameObject bin = highlights.getBin() != null ? scene.binObject(highlights.getBin()) : null;
		if (bin != null && config.highlightPatch())
		{
			outlines.drawOutline(bin, OUTLINE_WIDTH, config.patchColour(), OUTLINE_FEATHER);
		}
		if (highlights.isGardener() && config.highlightGardener())
		{
			final NPC gardener = scene.gardener(highlights.getPatch());
			if (gardener != null)
			{
				outlines.drawOutline(gardener, OUTLINE_WIDTH, config.npcColour(), OUTLINE_FEATHER);
			}
		}
		if (highlights.getTravelNpc() != null && config.highlightGardener())
		{
			// The ride after the teleport, e.g. the Civitas quetzal
			final NPC ride = scene.transferNpc(highlights.getTravelNpc());
			if (ride != null)
			{
				outlines.drawOutline(ride, OUTLINE_WIDTH, config.npcColour(), OUTLINE_FEATHER);
			}
		}
		// A ride taken on an object instead, from the atlas: a portal, a lever, a ladder (sprint T5)
		final TileObject rideObject = highlights.getTravelNpc() == null ? null : scene.getRideObject();
		if (rideObject != null && config.highlightGardener())
		{
			outlines.drawOutline(rideObject, OUTLINE_WIDTH, config.npcColour(), OUTLINE_FEATHER);
		}
		return null;
	}

	/**
	 * The patch's clickbox over one of its tiles, filled in the patch colour: the part of it to click. The ground tile
	 * instead while the clickbox isn't on screen.
	 */
	private void markPatchTile(Graphics2D graphics, List<GameObject> objects, WorldPoint tile)
	{
		final LocalPoint local = tile != null ? LocalPoint.fromWorld(client.getTopLevelWorldView(), tile) : null;
		final Polygon ground = local != null ? Perspective.getCanvasTilePoly(client, local) : null;
		if (ground == null)
		{
			return;
		}
		final Area clickbox = new Area();
		int height = 0;
		for (GameObject object : objects)
		{
			final Shape shape = object.getClickbox();
			if (shape != null)
			{
				clickbox.add(new Area(shape));
			}
			final Renderable model = object.getRenderable();
			if (model != null)
			{
				height = Math.max(height, model.getModelHeight());
			}
		}
		clickbox.intersect(column(ground, Perspective.getCanvasTilePoly(client, local, height)));
		OverlayUtil.renderPolygon(graphics, clickbox.isEmpty() ? ground : clickbox, config.patchColour());
	}

	/** On screen, the box standing on a tile's ground up to the same tile raised: the ground, the top and the four sides. */
	private static Area column(Polygon ground, Polygon top)
	{
		final Area column = new Area(ground);
		if (top == null || top.npoints != ground.npoints)
		{
			return column;
		}
		column.add(new Area(top));
		for (int i = 0; i < ground.npoints; i++)
		{
			final int j = (i + 1) % ground.npoints;
			column.add(new Area(new Polygon(
				new int[]{ground.xpoints[i], ground.xpoints[j], top.xpoints[j], top.xpoints[i]},
				new int[]{ground.ypoints[i], ground.ypoints[j], top.ypoints[j], top.ypoints[i]}, 4)));
		}
		return column;
	}

	private void markTile(Graphics2D graphics, WorldPoint point)
	{
		if (point == null)
		{
			return;
		}
		final LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
		if (local == null)
		{
			return;
		}
		final Polygon tile = Perspective.getCanvasTilePoly(client, local);
		if (tile != null)
		{
			OverlayUtil.renderPolygon(graphics, tile, config.patchColour());
		}
	}
}
