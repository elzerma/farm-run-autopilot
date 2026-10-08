package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.PatchPoints;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
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
		}
		if (highlights.isGardener() && config.highlightGardener())
		{
			final NPC gardener = scene.gardener(highlights.getPatch());
			if (gardener != null)
			{
				outlines.drawOutline(gardener, OUTLINE_WIDTH, config.npcColour(), OUTLINE_FEATHER);
			}
		}
		return null;
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
