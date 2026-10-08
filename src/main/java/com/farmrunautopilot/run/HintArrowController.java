package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Points the game's hint arrow (which also shows on the minimap) at the current patch. Only touches the
 * arrow when the target changes, and only clears an arrow it set itself. Client thread only.
 */
@Singleton
public class HintArrowController
{
	private final Client client;
	private final RunSession session;
	private final SceneTracker scene;
	private final FarmRunAutopilotConfig config;
	/** Where our arrow is, or null if we haven't set one. */
	private LocalPoint shown;

	@Inject
	HintArrowController(Client client, RunSession session, SceneTracker scene, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.session = session;
		this.scene = scene;
		this.config = config;
	}

	/** Call every game tick, after the run session has updated. */
	public void update()
	{
		final Patch patch = session.getView().getHighlights().getPatch();
		final LocalPoint target = patch != null && config.hintArrow() ? pointFor(patch) : null;
		if (Objects.equals(target, shown))
		{
			return;
		}
		if (target == null)
		{
			clear();
			return;
		}
		client.setHintArrow(target);
		shown = target;
	}

	/** Remove our arrow, if it's still up and nothing else has replaced it with an NPC or player arrow. */
	public void clear()
	{
		if (shown != null)
		{
			if (client.hasHintArrow() && client.getHintArrowNpc() == null && client.getHintArrowPlayer() == null)
			{
				client.clearHintArrow();
			}
			shown = null;
		}
	}

	/** The middle of the patch: a game object's location is its centre, so a 2x2 herb patch gets the middle of 4 tiles. */
	private LocalPoint pointFor(Patch patch)
	{
		final List<GameObject> objects = scene.objectsFor(patch);
		if (!objects.isEmpty())
		{
			return objects.get(0).getLocalLocation();
		}
		final WorldPoint point = PatchPoints.of(patch);
		return point == null ? null : LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
	}
}
