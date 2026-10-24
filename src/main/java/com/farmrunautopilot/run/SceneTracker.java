package com.farmrunautopilot.run;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Keeps the loaded patch objects and gardener NPCs, for highlighting. Driven by spawn and despawn events,
 * never by scanning the scene. Client thread only.
 *
 * <p>A patch object is recognised by the varbit that drives it (the patch's own FARMING_TRANSMIT varbit)
 * near the patch's map point, so every look of every patch matches without an ID list.
 */
@Slf4j
@Singleton
public class SceneTracker
{
	/**
	 * How far a patch object can be from its {@link PatchPoints} entry. Generous, because some points are a few
	 * tiles off (one was on the neighbouring flower patch); other patches nearby (flowers, allotments) use
	 * different varbits, so they never match.
	 */
	private static final int PATCH_SEARCH_TILES = 30;
	/** Gardeners wander a little around their patch. */
	private static final int GARDENER_TILES = 20;

	private final Client client;
	private final Map<Patch, List<GameObject>> patchObjects = new EnumMap<>(Patch.class);
	private final Map<Patch, NPC> gardeners = new EnumMap<>(Patch.class);
	/** Renu, the quetzal a Renu leg walks back to first (Navigator); everyone else ridden comes from the atlas. */
	private static final Set<String> TRANSFER_NPC_NAMES = Set.of("Renu");
	private final Map<String, NPC> transfers = new HashMap<>();
	/** Everyone the travel atlas's journeys are used on (Renu, a ship's crew, a guide), once it's read. */
	private Set<String> atlasRideNames;

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
			final List<GameObject> objects = patchObjects.computeIfAbsent(patch, k -> new ArrayList<>());
			if (objects.isEmpty())
			{
				// For checking PatchPoints against where patches really are
				log.debug("Patch object for {} at {} (map point {}, {} tiles off)", patch, object.getWorldLocation(),
					PatchPoints.of(patch), PatchPoints.of(patch).distanceTo2D(object.getWorldLocation()));
			}
			objects.add(object);
		}
	}

	public void onObjectDespawned(GameObject object)
	{
		for (List<GameObject> objects : patchObjects.values())
		{
			objects.remove(object);
		}
	}

	/**
	 * Gardeners are matched by name as well as ID, because some have several IDs (Treznor has three), and only
	 * near their own patch.
	 */
	public void onNpcSpawned(NPC npc)
	{
		if (npc.getName() != null && isRideNpc(npc.getName()))
		{
			log.debug("Ride NPC {} #{} at {}", npc.getName(), npc.getId(), npc.getWorldLocation());
			transfers.put(npc.getName(), npc);
			return;
		}
		final WorldPoint location = npc.getWorldLocation();
		for (Patch patch : Patch.values())
		{
			if (!patch.hasGardener())
			{
				continue;
			}
			final boolean same = patch.getGardenerNpcId() == npc.getId() || patch.getGardenerName().equals(npc.getName());
			final WorldPoint point = PatchPoints.of(patch);
			if (same && point.getPlane() == location.getPlane() && point.distanceTo2D(location) <= GARDENER_TILES)
			{
				// One gardener looks after both allotments of a set
				gardeners.put(patch, npc);
			}
		}
	}

	public void onNpcDespawned(NPC npc)
	{
		gardeners.values().removeIf(n -> n == npc);
		transfers.values().removeIf(n -> n == npc);
	}

	/**
	 * The scene is reloading: objects are spawned again afterwards, but NPCs already nearby are not, so the
	 * gardeners are kept (they're removed when they despawn).
	 */
	public void clearObjects()
	{
		patchObjects.clear();
	}

	/** Logged out or hopping: nothing is loaded any more. */
	public void clear()
	{
		patchObjects.clear();
		gardeners.clear();
		transfers.clear();
	}

	public List<GameObject> objectsFor(Patch patch)
	{
		final List<GameObject> objects = patchObjects.get(patch);
		return objects != null ? objects : Collections.emptyList();
	}

	/** The patch whose loaded object has this ID and scene position (as in a menu entry), or null. */
	public Patch patchAt(int objectId, int sceneX, int sceneY)
	{
		for (Map.Entry<Patch, List<GameObject>> e : patchObjects.entrySet())
		{
			for (GameObject object : e.getValue())
			{
				final Point min = object.getSceneMinLocation();
				if (object.getId() == objectId && min.getX() == sceneX && min.getY() == sceneY)
				{
					return e.getKey();
				}
			}
		}
		return null;
	}

	/** The loaded NPC with this name that a travel method rides, or null. */
	public NPC transferNpc(String name)
	{
		return transfers.get(name);
	}

	/** Tiles around where a journey step starts to look for its object, as the atlas's points are a tile or two off. */
	private static final int RIDE_OBJECT_TILES = 3;
	/** The object the current journey step is taken on (a portal, a lever, a ladder), once it's loaded; null if none. */
	private TileObject rideObject;

	/**
	 * Look for the object a journey step is taken on, by the atlas's id near where the step starts (sprint T5); none for
	 * a step on an NPC, which is outlined by name. Once a tick while travelling, over a few tiles only. Client thread.
	 */
	void findRideObject(JourneyStep step)
	{
		rideObject = null;
		if (step == null || step.getId() < 0 || step.getAt() == null || step.getNpc() != null
			&& transfers.containsKey(step.getNpc()))
		{
			return;
		}
		final WorldView view = client.getTopLevelWorldView();
		final WorldPoint at = step.getAt();
		if (view == null || view.getPlane() != at.getPlane())
		{
			return;
		}
		final Tile[][][] tiles = view.getScene().getTiles();
		for (int dx = -RIDE_OBJECT_TILES; dx <= RIDE_OBJECT_TILES; dx++)
		{
			for (int dy = -RIDE_OBJECT_TILES; dy <= RIDE_OBJECT_TILES; dy++)
			{
				final LocalPoint local = LocalPoint.fromWorld(view, at.dx(dx).dy(dy));
				if (local == null)
				{
					continue;
				}
				final Tile tile = tiles[at.getPlane()][local.getSceneX()][local.getSceneY()];
				final TileObject found = tile == null ? null : objectOn(tile, step.getId());
				if (found != null)
				{
					rideObject = found;
					return;
				}
			}
		}
	}

	/** The current journey step's object, for outlining; null if none is loaded. */
	public TileObject getRideObject()
	{
		return rideObject;
	}

	/** The object with this id on a tile (a game object, a wall, a ground or decorative object), or null. */
	private static TileObject objectOn(Tile tile, int id)
	{
		if (tile.getGameObjects() != null)
		{
			for (GameObject object : tile.getGameObjects())
			{
				if (object != null && object.getId() == id)
				{
					return object;
				}
			}
		}
		for (TileObject object : new TileObject[]{tile.getWallObject(), tile.getGroundObject(), tile.getDecorativeObject()})
		{
			if (object != null && object.getId() == id)
			{
				return object;
			}
		}
		return null;
	}

	/** An NPC a journey step rides with: Renu, or one the travel atlas uses. */
	private boolean isRideNpc(String name)
	{
		if (TRANSFER_NPC_NAMES.contains(name))
		{
			return true;
		}
		if (atlasRideNames == null)
		{
			final TravelAtlas atlas = TravelAtlas.ifLoaded();
			if (atlas == null)
			{
				return false;
			}
			atlasRideNames = atlas.targets();
		}
		return atlasRideNames.contains(name);
	}

	/** The patch's gardener if loaded, or null. */
	public NPC gardener(Patch patch)
	{
		return gardeners.get(patch);
	}

	private Patch patchFor(GameObject object)
	{
		final WorldPoint location = object.getWorldLocation();
		ObjectComposition composition = null;
		Patch best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Map.Entry<Patch, WorldPoint> e : PatchPoints.all().entrySet())
		{
			final WorldPoint point = e.getValue();
			final int distance = point.getPlane() == location.getPlane() ? point.distanceTo2D(location)
				: Integer.MAX_VALUE;
			if (distance > PATCH_SEARCH_TILES || distance >= bestDistance)
			{
				continue;
			}
			// Only look the object up once it's near a patch
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
				best = e.getKey();
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Where the patch really is: its loaded object if there is one, otherwise its map point. */
	public WorldPoint locationOf(Patch patch)
	{
		final List<GameObject> objects = objectsFor(patch);
		return objects.isEmpty() ? PatchPoints.of(patch) : objects.get(0).getWorldLocation();
	}
}
