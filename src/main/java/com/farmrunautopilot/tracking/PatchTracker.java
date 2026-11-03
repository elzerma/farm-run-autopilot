package com.farmrunautopilot.tracking;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.BinState;
import com.farmrunautopilot.data.CompostBin;
import com.farmrunautopilot.data.DecodedPatch;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchStateDecoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WidgetNode;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.ConfigManager;

/**
 * Records patch varbits while the player is near a patch and predicts every patch's state (SPEC 10).
 * Observations are stored per account under {@code patch.<regionId>.<varbitId>}.
 */
@Slf4j
@Singleton
public class PatchTracker
{
	private static final String PATCH_PREFIX = "patch.";
	private static final String PLANTED_SUFFIX = ".planted";
	private static final String AUTOWEED_KEY = "autoweed";
	private static final String TICK_OFFSET_KEY = "farmTickOffset";
	private static final String TICK_OFFSET_PRECISION_KEY = "farmTickOffsetPrecision";
	/** RuneLite core Time Tracking, read only as a fallback for patches we have never seen (SPEC 10.5). */
	private static final String TIME_TRACKING_GROUP = "timetracking";
	/** Re-save an unchanged observation at most this often, like core Time Tracking. */
	private static final long RESAVE_SECONDS = 5 * 60;
	private static final String BIN_PREFIX = "bin.";
	/** A closed compost bin has rotted this long after it was seen closed (OSRS Wiki: 35 to 70 minutes). */
	static final long ROT_SECONDS = 70 * 60;
	/** Each bin's value as last read this session, to log it as it changes. */
	private final Map<CompostBin, Integer> binValues = new java.util.EnumMap<>(CompostBin.class);
	/**
	 * Each patch's value as last read this session, to log it as it changes, with the tick: a reading that changes just
	 * after arriving, with nothing done to the patch, was the last area's patch on the same varbit (GitHub #35).
	 */
	private final Map<Patch, Integer> patchValues = new java.util.EnumMap<>(Patch.class);

	private static final Map<Integer, List<Patch>> PATCHES_BY_REGION = new HashMap<>();

	static
	{
		for (Patch patch : Patch.values())
		{
			PATCHES_BY_REGION.computeIfAbsent(patch.getRegionId(), k -> new ArrayList<>()).add(patch);
			for (int region : patch.getExtraRegionIds())
			{
				PATCHES_BY_REGION.computeIfAbsent(region, k -> new ArrayList<>()).add(patch);
			}
		}
	}

	private final Client client;
	/** A Leagues world (patches grow faster), read on the client thread. */
	private volatile boolean leaguesWorld;
	private final ConfigManager configManager;

	private Set<Patch> lastPatches = Collections.emptySet();
	/** When each patch was last seen unchanged this session; saved config only refreshes every few minutes.
	 * Written on the client thread, read on the Swing thread. */
	private final Map<Patch, Long> lastSeen = new ConcurrentHashMap<>();

	@Inject
	PatchTracker(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	/** Patches whose varbits the game is sending right now, i.e. the player is next to them. Client thread. */
	public Set<Patch> getPatchesInRange()
	{
		return lastPatches;
	}

	/** Forget per-session state, e.g. after logging in to another account. */
	public void reset()
	{
		lastPatches = Collections.emptySet();
		lastSeen.clear();
		patchValues.clear();
	}

	/**
	 * Saves the varbits of the patches around {@code location}. Call once per game tick on the client
	 * thread, with the player's location from the previous tick.
	 *
	 * @param ticksSinceModalClose game ticks since a modal interface was closed
	 * @return whether any saved data changed
	 */
	public boolean update(WorldPoint location, int ticksSinceModalClose)
	{
		leaguesWorld = isLeagues(client.getWorldType());
		// Varbits don't get sent when a modal widget is open
		for (WidgetNode node : client.getComponentTable())
		{
			if (node.getModalMode() != WidgetModalMode.NON_MODAL)
			{
				return false;
			}
		}

		boolean changed = false;

		// Saved for the supply calculator: no rake needed when Auto-weed is on (SPEC 11.4)
		final String autoweed = Integer.toString(client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS));
		if (!autoweed.equals(getConfig(AUTOWEED_KEY)))
		{
			setConfig(AUTOWEED_KEY, autoweed);
			changed = true;
		}

		final Set<Patch> patches = patchesAt(location);
		final boolean newRegionLoaded = !patches.equals(lastPatches);
		lastPatches = patches;

		final long now = Instant.now().getEpochSecond();
		changed |= updateBins(patches, now);
		for (Patch patch : patches)
		{
			final int value = client.getVarbitValue(patch.getVarbitId());
			final DecodedPatch current = PatchStateDecoder.decode(patch.getType(), value);
			final Integer before = patchValues.put(patch, value);
			if (before == null || before != value)
			{
				log.debug("Patch {}: {} {} stage {} (value {}) at tick {}", patch, current.getState(), current.getCrop(),
					current.getStage(), value, client.getTickCount());
			}
			if (current.getState() == PatchState.UNKNOWN)
			{
				continue;
			}

			lastSeen.put(patch, now);
			final PatchRecord stored = PatchRecord.parse(getConfig(key(patch)));
			if (stored != null && stored.getValue() == value)
			{
				// Unchanged: only refresh the timestamp every few minutes
				if (stored.getObservedAt() + RESAVE_SECONDS > now && now + 30 > stored.getObservedAt())
				{
					continue;
				}
			}
			else if (stored != null)
			{
				final DecodedPatch previous = PatchStateDecoder.decode(patch.getType(), stored.getValue());
				if (PatchPredictor.isPlanting(previous, current))
				{
					setConfig(key(patch) + PLANTED_SUFFIX, Long.toString(now));
					log.debug("Saw {} planted with {}", patch, current.getCrop());
				}
				// Right after arriving or closing an interface, a change may be old news, not a live tick
				if (!newRegionLoaded && ticksSinceModalClose > 1
					&& PatchPredictor.isObservedGrowthTick(patch.getType(), previous, current))
				{
					recordTickOffset(previous.getCrop().getTickMinutes(), now);
				}
			}

			setConfig(key(patch), new PatchRecord(value, now).format());
			changed = true;
		}
		return changed;
	}

	/**
	 * @return the predicted state of this patch now, or null if it has never been seen
	 */
	public PatchPrediction predict(Patch patch)
	{
		PatchPrediction.Source source = PatchPrediction.Source.THIS_PLUGIN;
		PatchRecord record = PatchRecord.parse(getConfig(key(patch)));
		if (record == null)
		{
			record = PatchRecord.parse(configManager.getRSProfileConfiguration(TIME_TRACKING_GROUP,
				patch.getRegionId() + "." + patch.getVarbitId()));
			source = PatchPrediction.Source.TIME_TRACKING;
		}
		if (record == null)
		{
			return null;
		}

		final Long seen = lastSeen.get(patch);
		if (seen != null && seen > record.getObservedAt() && source == PatchPrediction.Source.THIS_PLUGIN)
		{
			record = new PatchRecord(record.getValue(), seen);
		}
		return PatchPredictor.predict(patch, record, plantedAt(patch), source, Instant.now().getEpochSecond(),
			farmingTick(), isLeaguesWorld());
	}

	/**
	 * The compost bins at the farming areas in range (P3): each one's value, logged as it changes and saved only when it
	 * does, so a closed bin's saved time is when it started rotting (or before).
	 */
	private boolean updateBins(Set<Patch> inRange, long now)
	{
		boolean changed = false;
		for (CompostBin bin : CompostBin.values())
		{
			if (inRange.stream().noneMatch(patch -> patch.getLocation() == bin.getLocation()))
			{
				continue;
			}
			final int value = client.getVarbitValue(bin.getVarbitId());
			final Integer before = binValues.put(bin, value);
			if (before == null || before != value)
			{
				log.debug("Compost bin at {}: {} (value {})", bin.getLocation().getDisplayName(),
					BinState.decode(value, bin.isBig()), value);
			}
			final PatchRecord stored = PatchRecord.parse(getConfig(binKey(bin)));
			if (stored == null || stored.getValue() != value)
			{
				setConfig(binKey(bin), new PatchRecord(value, now).format());
				changed = true;
			}
		}
		return changed;
	}

	/** The bin's state now while the player's at its farming area, else {@link #predictBin}. Client thread. */
	public BinState binState(CompostBin bin)
	{
		if (lastPatches.stream().anyMatch(patch -> patch.getLocation() == bin.getLocation()))
		{
			return BinState.decode(client.getVarbitValue(bin.getVarbitId()), bin.isBig());
		}
		return predictBin(bin);
	}

	/**
	 * The bin's state as last seen here, or by RuneLite's Time Tracking, and rotted once {@link #ROT_SECONDS} have passed
	 * since it was seen rotting; null if it's never been seen.
	 */
	public BinState predictBin(CompostBin bin)
	{
		PatchRecord record = PatchRecord.parse(getConfig(binKey(bin)));
		if (record == null)
		{
			record = PatchRecord.parse(configManager.getRSProfileConfiguration(TIME_TRACKING_GROUP,
				bin.getRegionId() + "." + bin.getVarbitId()));
		}
		final BinState seen = record != null ? BinState.decode(record.getValue(), bin.isBig()) : null;
		return seen != null ? rotted(seen, record.getObservedAt(), Instant.now().getEpochSecond()) : null;
	}

	/** A bin seen rotting at {@code seenAt} has rotted by {@code now} once {@link #ROT_SECONDS} have passed. */
	static BinState rotted(BinState seen, long seenAt, long now)
	{
		return seen.getStage() == BinState.Stage.ROTTING && now - seenAt >= ROT_SECONDS
			? new BinState(BinState.Stage.ROTTED, seen.getCompost(), 0) : seen;
	}

	private static String binKey(CompostBin bin)
	{
		return BIN_PREFIX + bin.getRegionId() + "." + bin.getVarbitId();
	}

	private void recordTickOffset(int tickMinutes, long now)
	{
		final int offset = FarmingTick.observedOffsetMinutes(tickMinutes, now);
		final Integer precision = parseInt(getConfig(TICK_OFFSET_PRECISION_KEY));
		log.debug("Observed a growth tick on a {} minute crop, offset {}", tickMinutes, offset);
		if (precision == null || tickMinutes >= precision)
		{
			setConfig(TICK_OFFSET_PRECISION_KEY, Integer.toString(tickMinutes));
			setConfig(TICK_OFFSET_KEY, Integer.toString(offset));
		}
	}

	private FarmingTick farmingTick()
	{
		return new FarmingTick(parseInt(getConfig(TICK_OFFSET_KEY)), parseInt(getConfig(TICK_OFFSET_PRECISION_KEY)));
	}

	private long plantedAt(Patch patch)
	{
		final String text = getConfig(key(patch) + PLANTED_SUFFIX);
		try
		{
			return text == null ? 0 : Long.parseLong(text);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static Set<Patch> patchesAt(WorldPoint location)
	{
		final List<Patch> candidates = PATCHES_BY_REGION.get(location.getRegionID());
		if (candidates == null)
		{
			return Collections.emptySet();
		}
		final Set<Patch> patches = EnumSet.noneOf(Patch.class);
		for (Patch patch : candidates)
		{
			if (patch.isInBounds(location))
			{
				patches.add(patch);
			}
		}
		return patches;
	}

	/** Read on the client thread each update, since predict() is also called from the sidebar (Swing). */
	private boolean isLeaguesWorld()
	{
		return leaguesWorld;
	}

	private static boolean isLeagues(Set<WorldType> worldTypes)
	{
		return worldTypes.contains(WorldType.SEASONAL) && !worldTypes.contains(WorldType.DEADMAN);
	}

	private static String key(Patch patch)
	{
		return PATCH_PREFIX + patch.getRegionId() + "." + patch.getVarbitId();
	}

	private String getConfig(String key)
	{
		return configManager.getRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key);
	}

	private void setConfig(String key, String value)
	{
		configManager.setRSProfileConfiguration(FarmRunAutopilotConfig.GROUP, key, value);
	}

	private static Integer parseInt(String text)
	{
		try
		{
			return text == null ? null : Integer.valueOf(text);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
