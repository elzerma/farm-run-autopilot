package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.travel.TravelTimes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * The bank by each stop, from the travel atlas (docs/plans/travel-rework.md, section 4): of the banks the atlas keeps
 * by a stop, the one it walks to quickest on foot from the stop's patches (no boat or portal), timed from the patch
 * furthest from it (the one most likely done last). The Tree Gnome Stronghold's bank by its tree patch is upstairs and
 * Shortest Path's map has no way up, so it keeps its one hand-measured entry until the atlas learns the ladder.
 */
public final class AtlasBanks
{
	/**
	 * A bank further than this from the stop's patch furthest from it isn't by the stop: the old tables' longest walk to
	 * a bank was Port Phasmatys's, 116 tiles on the atlas (through the energy barrier).
	 */
	static final int NEAR_TILES = 120;

	/** A stop's bank: where it is and the walk to it. */
	@Value
	public static class Bank
	{
		/** The bank booth or chest, for the walking line to it. */
		WorldPoint point;
		/** Walking tiles from the stop's patch furthest from it. */
		int tiles;
		/** Seconds from that patch: running, and anything on the way (a door, a boat). */
		double seconds;
	}

	/** Banks the atlas can't reach: the Stronghold's, upstairs by the tree patch (an estimate: the ladder isn't mapped). */
	private static final Map<Location, Bank> BY_HAND = Collections.singletonMap(Location.GNOME_STRONGHOLD,
		new Bank(new WorldPoint(2445, 3424, 1), 10, 10 * TravelTimes.SECONDS_PER_TILE));

	private static TravelAtlas banksOf;
	private static Map<Location, Bank> banks = Collections.emptyMap();

	private AtlasBanks()
	{
	}

	/** The bank by this stop, or null if there's none, or the atlas hasn't been read yet. Any thread. */
	public static Bank near(Location location)
	{
		final TravelAtlas atlas = TravelAtlas.ifLoaded();
		return atlas == null || atlas.isEmpty() ? null : of(atlas).get(location);
	}

	/** Every stop's bank in this atlas, worked out once. */
	static synchronized Map<Location, Bank> of(TravelAtlas atlas)
	{
		if (banksOf != atlas)
		{
			final Map<Location, Bank> found = new EnumMap<>(Location.class);
			for (Location location : Location.values())
			{
				final Bank bank = find(atlas, location);
				if (bank != null)
				{
					found.put(location, bank);
				}
			}
			found.putAll(BY_HAND);
			banks = Collections.unmodifiableMap(found);
			banksOf = atlas;
		}
		return banks;
	}

	/**
	 * The bank nearest the stop's patches on foot, timed from the one furthest from it; null if the atlas walks to none
	 * within {@link #NEAR_TILES}.
	 */
	private static Bank find(TravelAtlas atlas, Location location)
	{
		final List<Integer> patches = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			final TravelAtlas.Place place = patch.getLocation() == location ? atlas.patchPlace(patch.name()) : null;
			if (place != null && !patches.contains(place.getId()))
			{
				patches.add(place.getId());
			}
		}
		TravelAtlas.Place nearest = null;
		double nearestSeconds = Double.MAX_VALUE;
		for (TravelAtlas.Place bank : atlas.getPlaces())
		{
			if (bank.getKind() != TravelAtlas.Kind.BANK)
			{
				continue;
			}
			for (int patch : patches)
			{
				final TravelAtlas.Way way = onFoot(atlas, patch, bank.getId());
				if (way != null && AtlasLegs.seconds(way) < nearestSeconds)
				{
					nearest = bank;
					nearestSeconds = AtlasLegs.seconds(way);
				}
			}
		}
		if (nearest == null)
		{
			return null;
		}
		TravelAtlas.Way furthest = null;
		for (int patch : patches)
		{
			final TravelAtlas.Way way = onFoot(atlas, patch, nearest.getId());
			if (way != null && (furthest == null || AtlasLegs.seconds(way) > AtlasLegs.seconds(furthest)))
			{
				furthest = way;
			}
		}
		return furthest.getTiles() > NEAR_TILES ? null
			: new Bank(nearest.getPoint(), furthest.getTiles(), AtlasLegs.seconds(furthest));
	}

	/** The atlas's quickest way between two places on foot (no boat, portal or guide carrying the player), or null. */
	private static TravelAtlas.Way onFoot(TravelAtlas atlas, int from, int to)
	{
		for (TravelAtlas.Way way : atlas.ways(from, to))
		{
			boolean walked = true;
			for (int step : way.getSteps())
			{
				walked &= !TravelAtlas.carries(atlas.step(step));
			}
			if (walked)
			{
				return way;
			}
		}
		return null;
	}
}
