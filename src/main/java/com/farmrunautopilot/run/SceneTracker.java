package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;

/**
 * Keeps the loaded patch objects and gardener NPCs, for highlighting. Driven by spawn and despawn events,
 * never by scanning the scene. Client thread only.
 *
 * <p>A patch object is recognised by the varbit that drives it (the patch's own FARMING_TRANSMIT varbit)
 * and being next to the patch's map point, so every look of every patch matches without an ID list.
 */
@Singleton
public class SceneTracker
{
	/** How far a patch object can be from {@link PatchPoints} (the point is one tile of a 2x2 to 5x5 patch). */
	private static final int PATCH_POINT_TILES = 4;

	private final Client client;
	private final Map<Patch, List<GameObject>> patchObjects = new EnumMap<>(Patch.class);
	private final Map<Integer, NPC> gardeners = new HashMap<>();

	@Inject
	SceneTracker(Client client)
	{
		this.client = client;
	}

	public void onObjectSpawned(GameObject object)
	{
		final Patch patch = patchFor(object);
		if (patch != null)
		{
			patchObjects.computeIfAbsent(patch, k -> new ArrayList<>()).add(object);
		}
	}

	public void onObjectDespawned(GameObject object)
	{
		for (List<GameObject> objects : patchObjects.values())
		{
			objects.remove(object);
		}
	}

	public void onNpcSpawned(NPC npc)
	{
		for (Patch patch : Patch.values())
		{
			if (patch.hasGardener() && patch.getGardenerNpcId() == npc.getId())
			{
				gardeners.put(npc.getId(), npc);
				return;
			}
		}
	}

	public void onNpcDespawned(NPC npc)
	{
		gardeners.remove(npc.getId(), npc);
	}

	/** The scene is reloading; objects and NPCs are spawned again afterwards. */
	public void clear()
	{
		patchObjects.clear();
		gardeners.clear();
	}

	public List<GameObject> objectsFor(Patch patch)
	{
		final List<GameObject> objects = patchObjects.get(patch);
		return objects != null ? objects : Collections.emptyList();
	}

	public NPC gardener(int npcId)
	{
		return gardeners.get(npcId);
	}

	private Patch patchFor(GameObject object)
	{
		final WorldPoint location = object.getWorldLocation();
		ObjectComposition composition = null;
		for (Map.Entry<Patch, WorldPoint> e : PatchPoints.all().entrySet())
		{
			final WorldPoint point = e.getValue();
			if (point.getPlane() != location.getPlane() || point.distanceTo2D(location) > PATCH_POINT_TILES)
			{
				continue;
			}
			// Only look the object up once something is near a patch
			if (composition == null)
			{
				composition = client.getObjectDefinition(object.getId());
				if (composition == null || composition.getImpostorIds() == null)
				{
					return null;
				}
			}
			if (composition.getVarbitId() == e.getKey().getVarbitId())
			{
				return e.getKey();
			}
		}
		return null;
	}
}
