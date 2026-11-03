package com.farmrunautopilot.run;

import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.BinState;
import com.farmrunautopilot.data.CompostBin;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.FarmingContract;
import com.farmrunautopilot.data.GrimyHerb;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.route.ContractPlanner;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.EmptyBuckets;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.FlowerProtection;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.PatchCare;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.tracking.ContractTracker;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;

/**
 * The work at a stop's patches (docs/REBUILD.md, Sprint 4): the next step at each patch from its live state
 * ({@link StepAdvisor}), what to highlight for it, and reminders (an item to take from the leprechaun, too
 * little space for the harvest). Remembers what was done at each patch this run. Client thread.
 */
@Slf4j
@Singleton
class PatchGuide
{
	/** Compost messages, as in RuneLite core CompostTracker (BSD-2). */
	private static final Pattern COMPOST_USED = Pattern.compile(
		"You treat the .+ with (ultra|super|)compost\\.|^The .+ has been treated with (ultra|super|)compost.*");
	private static final Pattern ALREADY_COMPOSTED = Pattern.compile(
		"This .+ has already been (treated|fertilised) with (ultra|super|)compost.*");
	/** Inspecting a treated patch: "This is an allotment. The soil has been treated with ultracompost. ..." */
	static final Pattern INSPECTED_COMPOSTED = Pattern.compile(
		"This is an? .+?\\. The soil has been treated with (ultra|super|)compost.*");
	/** "You plant an irit seed in the herb patch." (seen 2026-10-29; other patches' wording UNVERIFIED) */
	static final Pattern PLANTED = Pattern.compile("You plant .+ in the .+");
	/**
	 * Game ticks a patch counts as planted on the evidence alone (the seed leaving the inventory, the chat message),
	 * before its reading catches up: the seed goes a tick or so before the patch changes (elzerma, 2026-10-29: the
	 * guide stayed on Plant, saying the irit seed wasn't carried, until then). If the reading still says empty after
	 * this, the seed went in another patch, and the reading decides again.
	 */
	static final int PLANTED_GRACE_TICKS = 5;
	private static final int INVENTORY_SLOTS = 28;
	/** Gained at a patch but never produce to note: weeds, what's left from composting and planting, his own stock. */
	private static final Set<Integer> NOT_PRODUCE = notProduce();

	private static Set<Integer> notProduce()
	{
		final Set<Integer> ids = new java.util.HashSet<>(java.util.Arrays.asList(ItemID.WEEDS, ItemID.BUCKET_EMPTY,
			ItemID.PLANTPOT_EMPTY, ItemID.BOTTOMLESS_COMPOST_BUCKET, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
		for (com.farmrunautopilot.supply.LeprechaunItem item : com.farmrunautopilot.supply.LeprechaunItem.values())
		{
			ids.add(item.getItemId());
		}
		return java.util.Collections.unmodifiableSet(ids);
	}

	/** The NPC who notes produce and stores tools and compost at every farming area. */
	static final String TOOL_LEPRECHAUN = "Tool Leprechaun";
	/** A tool leprechaun this close to the stop's patch is the stop's. */
	private static final int LEPRECHAUN_NEAR_TILES = 40;
	/** How much nearer the player another of the patch's tiles must be before the walking line and its mark move there. */
	static final int RETARGET_TILES = 3;

	private final Client client;
	private final RunService runService;
	private final PatchTracker patchTracker;
	private final HoldingsTracker holdingsTracker;
	private final AccessChecker accessChecker;
	private final SettingsStore settings;
	private final Navigator navigator;
	private final SceneTracker scene;
	/** The Farming Guild contract, for runs with contracts on (docs/plans/farming-contracts.md). */
	private final ContractTracker contractTracker;
	/** The contract as last seen at the guild stop: when it changes, the patches left for one are looked at again. */
	private ContractTracker.Contract contractSeen;

	private RunPlan plan;
	private Patch currentPatch;
	/** What to do at {@link #currentPatch}, or null while travelling. */
	private StepAdvisor.Action currentAction;
	private final Map<Patch, Progress> progress = new EnumMap<>(Patch.class);
	/** Patches finished this run, in the order they were finished, for the checklist's order. */
	private final Set<Patch> finishedOrder = new LinkedHashSet<>();
	/**
	 * The stop being worked at, and what came into the inventory there while picking: its produce (P1). Only gains
	 * while picking count (elzerma, 2026-10-28: a staff taken off at the stop was asked to be noted).
	 */
	private RouteStop atStop;
	/** The stop's visit to the tool leprechaun before planting is done, or the player went their own way. */
	private boolean visited;
	private final Set<Integer> picked = new LinkedHashSet<>();
	/** The inventory as last seen, to tell what picking added. */
	private Map<Integer, Integer> lastSeen = Collections.emptyMap();
	/** The stop's patches, for what's still to be composted there when the inventory fills. */
	private List<Patch> atStopPatches = Collections.emptyList();
	/** The produce the Note step outlines. */
	private Set<Integer> produce = Collections.emptySet();
	/** The grimy herbs the clean step outlines. */
	private Set<Integer> toClean = Collections.emptySet();
	/** Buckets of compost taken out of this stop's bin, by item (P3): stored after, but for what this stop needs. */
	private final Map<Integer, Integer> binTaken = new HashMap<>();
	/** The compost bin the step in hand is on, and the items it outlines; null and none otherwise. */
	private CompostBin binInHand;
	private Set<Integer> binItems = Collections.emptySet();
	/** The stop's bin has compost to take out. */
	private boolean emptyingBin;
	/** The tool leprechaun is being visited for the bin's empty buckets: it stays the step while his window is open. */
	private boolean bucketVisit;
	/** Guild patches outside the plan a farming contract brought into the guild stop: kept to its end. */
	private final Set<Patch> contractExtras = EnumSet.noneOf(Patch.class);
	/** The stop's bin's fill count as last read in range, -1 when not read or not filling (P3). */
	private int binCountSeen = -1;
	/** The player has begun putting the crops for it in the stop's bin, and still has some (P3). */
	private boolean fillingBin;
	/** The stop's tool leprechaun for the walking line, and where he was when picked out: he wanders. */
	private NPC leprechaun;
	private WorldPoint leprechaunAt;
	/** The current patch's tile for the walking line, hint arrow and its mark ({@link #patchTile}), and its patch. */
	private WorldPoint patchTile;
	private Patch patchTileOf;
	/** A visit to the tool leprechaun here has said to store compost or empty buckets: it stays until they're stored. */
	private boolean storeDue;
	/** Something has been picked at this stop. */
	private boolean pickedHere;
	/** What the visit to the tool leprechaun in hand takes from and stores with him, by item, for his window. */
	private Map<Integer, Integer> visitTake = Collections.emptyMap();
	private Map<Integer, Integer> visitStore = Collections.emptyMap();
	/** The stops after this one, by their patches, up to a bank visit or the run's end. */
	private List<List<Patch>> laterStops = Collections.emptyList();

	/** What has happened at a patch during this run. */
	private static final class Progress
	{
		boolean composted;
		boolean paid;
		boolean skipped;
		/** Finished (or skipped) this run, for the current stop's checklist. */
		boolean done;
		/** Seen in range in a state other than growing, so a crop growing later was planted this run. */
		boolean sawUnplanted;
		/** Payment items in the inventory when paying became the next step; -1 until then. */
		int paymentBaseline = -1;
		/** The game tick it was seen planted, before its reading showed it; -1 if not. */
		int plantedTick = -1;
		/** Seen with nothing growing (empty, weeds, dead or a stump) this run: it's this run's to plant (contracts). */
		boolean sawEmpty;
		/** Passed over as a crop waiting for a farming contract: looked at again when the contract changes. */
		boolean leftForContract;
	}

	/** Seen planted within the last few ticks, while its reading may still say empty. */
	private boolean justPlanted(Progress p)
	{
		return plantedRecently(p.plantedTick, client.getTickCount());
	}

	static boolean plantedRecently(int plantedTick, int now)
	{
		return plantedTick >= 0 && now - plantedTick <= PLANTED_GRACE_TICKS;
	}

	/** Fewer of the seed (or sapling) than the step began with: it went into the patch. */
	static boolean plantUsed(int plantItemId, Map<Integer, Integer> before, Map<Integer, Integer> now)
	{
		return now.getOrDefault(plantItemId, 0) < before.getOrDefault(plantItemId, 0);
	}

	/** An empty patch just planted is read as growing, so its compost is asked for at once. */
	static PatchPrediction asPlanted(PatchPrediction live)
	{
		return new PatchPrediction(live.getPatch(), live.getCrop(), PatchState.GROWING, 0, live.getStages(), 0,
			live.getObservedAt(), live.getPlantedAt(), live.getSource());
	}

	private void markPlanted(Patch patch, String evidence)
	{
		final Progress p = progress(patch);
		if (!justPlanted(p))
		{
			p.plantedTick = client.getTickCount();
			log.debug("Planted {}: {}", patch, evidence);
		}
	}

	@Inject
	PatchGuide(Client client, RunService runService, PatchTracker patchTracker, HoldingsTracker holdingsTracker,
		AccessChecker accessChecker, SettingsStore settings, Navigator navigator, SceneTracker scene,
		ContractTracker contractTracker)
	{
		this.client = client;
		this.runService = runService;
		this.patchTracker = patchTracker;
		this.holdingsTracker = holdingsTracker;
		this.accessChecker = accessChecker;
		this.settings = settings;
		this.navigator = navigator;
		this.scene = scene;
		this.contractTracker = contractTracker;
	}

	/** A new run with this (frozen) plan, or null when there's none. */
	void reset(RunPlan plan)
	{
		this.plan = plan;
		progress.clear();
		finishedOrder.clear();
		atStop = null;
		clearCurrent();
	}

	/** Travelling, banking or between stops: no patch is being worked on. */
	void clearCurrent()
	{
		currentPatch = null;
		currentAction = null;
	}

	Patch getCurrentPatch()
	{
		return currentPatch;
	}

	/** Finished or skipped this run, so far as the guide has got (later patches aren't looked at yet). */
	boolean isDone(Patch patch)
	{
		final Progress p = progress.get(patch);
		return p != null && p.done;
	}

	/**
	 * Finished, skipped, or planted already this run, so what it needed is used or no longer needed: for the run's
	 * missing-item check (elzerma, 2026-10-24: the snapdragon and nasturtium just planted at Hosidius were asked for).
	 */
	boolean suppliesUsed(Patch patch)
	{
		final Progress p = progress.get(patch);
		if (p == null)
		{
			return false;
		}
		if (p.done)
		{
			return true;
		}
		final PatchPrediction live = patchTracker.predict(patch);
		return p.sawUnplanted && (justPlanted(p) || live != null && live.getState() == PatchState.GROWING);
	}

	/** Ticked by hand rather than seen done, so it can be unticked. */
	boolean isSkipped(Patch patch)
	{
		final Progress p = progress.get(patch);
		return p != null && p.skipped;
	}

	/**
	 * Tick patches by hand: the ones not already seen done count as skipped.
	 *
	 * @return the patches newly skipped, to bring back if the stop is unticked
	 */
	List<Patch> skipAll(List<Patch> here)
	{
		final List<Patch> skipped = new ArrayList<>();
		for (Patch patch : here)
		{
			final Progress p = progress(patch);
			if (!p.done)
			{
				p.skipped = true;
				skipped.add(patch);
			}
		}
		clearCurrent();
		return skipped;
	}

	/**
	 * The step in hand needs an item the player chose to do without (elzerma, 2026-10-18): composting or paying the
	 * gardener is left out; anything else (planting, curing, chopping) leaves the patch for this run.
	 */
	void skipCurrentStep()
	{
		if (currentPatch == null || currentAction == null)
		{
			return;
		}
		final Progress p = progress(currentPatch);
		if (currentAction == StepAdvisor.Action.COMPOST)
		{
			p.composted = true;
			clearCurrent();
		}
		else if (currentAction == StepAdvisor.Action.PAY)
		{
			p.paid = true;
			clearCurrent();
		}
		else
		{
			skipAll(Collections.singletonList(currentPatch));
		}
	}

	/** Untick a patch ticked by hand: the guide looks at it again. */
	void unskip(Patch patch)
	{
		final Progress p = progress.get(patch);
		if (p != null && p.skipped)
		{
			p.skipped = false;
			p.done = false;
		}
		clearCurrent();
	}

	/**
	 * A compost message: credit the patch it names ("You treat the herb patch...") among this stop's patches, so
	 * at a stop with several patches it lands on the right one; otherwise the patch being worked on.
	 */
	void onChatMessage(String message, List<Patch> stopPatches)
	{
		// Only the stop's patches the player is next to: planting and composting at the stop before, after the guide had
		// moved on, was credited to this stop's herb patch, which then wasn't asked for its compost (elzerma, 2026-10-30:
		// irit planted and treated at Civitas illa Fortis while the guide was on Troll Stronghold)
		final List<Patch> here = atHand(stopPatches, patchTracker.getPatchesInRange());
		if (here.isEmpty())
		{
			return;
		}
		if (PLANTED.matcher(message).matches())
		{
			// The patch it names, else the one the Plant step is on
			final Patch named = patchNamed(message, here);
			final Patch patch = named != null ? named
				: currentAction == StepAdvisor.Action.PLANT && here.contains(currentPatch) ? currentPatch : null;
			if (patch != null)
			{
				markPlanted(patch, message);
			}
			return;
		}
		if (!COMPOST_USED.matcher(message).matches() && !ALREADY_COMPOSTED.matcher(message).matches()
			&& !INSPECTED_COMPOSTED.matcher(message).matches())
		{
			return;
		}
		Patch patch = patchNamed(message, here);
		if (patch == null)
		{
			// Two of the type it names (the two allotments): the nearer one not yet credited, which is the one just used.
			// Crediting the patch the guide was on instead left an allotment asking for compost it had (elzerma,
			// 2026-10-27: composted out of order at the Farming Guild)
			final PatchType type = typeNamed(message);
			final List<Patch> candidates = new ArrayList<>();
			for (Patch p : here)
			{
				if (p.getType() == type && !progress(p).composted)
				{
					candidates.add(p);
				}
			}
			final List<Patch> nearest = navigator.nearestFirst(candidates, null);
			patch = !nearest.isEmpty() ? nearest.get(0) : type == null && here.contains(currentPatch) ? currentPatch : null;
		}
		if (patch != null)
		{
			progress(patch).composted = true;
		}
	}

	/** The stop's patches the game is sending readings for, i.e. the player is next to them. */
	static List<Patch> atHand(List<Patch> stopPatches, Set<Patch> inRange)
	{
		final List<Patch> at = new ArrayList<>();
		for (Patch patch : stopPatches)
		{
			if (inRange.contains(patch))
			{
				at.add(patch);
			}
		}
		return at;
	}

	/** The one patch of the type a message names ("fruit tree patch", "herb patch", "allotment", ...), or null. */
	static Patch patchNamed(String message, List<Patch> here)
	{
		final PatchType type = typeNamed(message);
		Patch found = null;
		for (Patch patch : here)
		{
			if (patch.getType() == type)
			{
				if (found != null)
				{
					// Two of that type here: can't tell which
					return null;
				}
				found = patch;
			}
		}
		return found;
	}

	/** The patch type a message names ("fruit tree patch", "herb patch", "allotment", ...), or null. */
	static PatchType typeNamed(String message)
	{
		final String text = message.toLowerCase();
		return text.contains("fruit tree") ? PatchType.FRUIT_TREE
			: text.contains("herb") ? PatchType.HERB
			: text.contains("allotment") ? PatchType.ALLOTMENT
			: text.contains("flower") ? PatchType.FLOWER
			: text.contains("hops") ? PatchType.HOPS
			// Sprint 1b, as the game names them in "You treat the ... patch" (UNVERIFIED for these patches)
			: text.contains("bush") ? PatchType.BUSH
			: text.contains("cactus") ? PatchType.CACTUS
			: text.contains("mushroom") ? PatchType.MUSHROOM
			: text.contains("belladonna") ? PatchType.BELLADONNA
			: text.contains("seaweed") ? PatchType.SEAWEED
			// Sprint 1c (UNVERIFIED wording for these patches), before plain "tree"
			: text.contains("hardwood") ? PatchType.HARDWOOD
			: text.contains("calquat") ? PatchType.CALQUAT
			: text.contains("celastrus") ? PatchType.CELASTRUS
			: text.contains("redwood") ? PatchType.REDWOOD
			: text.contains("spirit tree") ? PatchType.SPIRIT_TREE
			: text.contains("crystal tree") ? PatchType.CRYSTAL_TREE
			: text.contains("tree") ? PatchType.TREE : null;
	}

	/**
	 * The first unfinished step at this stop, or null when every patch here is done. While still far from the patch,
	 * the stop's walking directions (rocks, gates, stairs) come first.
	 *
	 * @param later the stops after this one, by their patches, up to a bank visit or the run's end: for compost for them,
	 *              and whether to note before leaving
	 */
	String nextStep(RouteStop stop, List<Patch> here, List<List<Patch>> later)
	{
		laterStops = later;
		here = contractStop(stop, here);
		final List<Patch> next = later.isEmpty() ? Collections.emptyList() : later.get(0);
		final Map<Integer, Integer> held = inventoryAsHeld();
		if (stop != atStop)
		{
			atStop = stop;
			// Produce not noted at the stop before comes along, to note on a visit here
			picked.removeIf(id -> !stillHeld(id, held));
			visited = false;
			binTaken.clear();
			binCountSeen = -1;
			fillingBin = false;
			bucketVisit = false;
			contractExtras.clear();
			leprechaun = null;
			leprechaunAt = null;
			storeDue = false;
			pickedHere = false;
		}
		else if (emptyingBin)
		{
			// What came out of the bin (P3): stored with the leprechaun after, but for what this stop's patches need. Taken
			// whichever of its steps showed, as the player can be ahead of the guide (elzerma's run of 2026-10-31: 5 taken
			// out while it said to fetch buckets went uncounted, so it said to store 21 of 26 with 2 needed)
			for (Compost compost : Compost.values())
			{
				final int id = compost.getItemId();
				final int gained = compost != Compost.NONE ? held.getOrDefault(id, 0) - lastSeen.getOrDefault(id, 0) : 0;
				if (gained > 0)
				{
					binTaken.merge(id, gained, Integer::sum);
				}
			}
		}
		else if (harvests(currentAction))
		{
			// The step as last shown: what its picking added is produce, to note (P1)
			final Set<Integer> gained = produceGained(lastSeen, held, runService::notable);
			if (!gained.isEmpty())
			{
				picked.addAll(gained);
				pickedHere = true;
				log.debug("Picked at {}: {}; produce so far {}", currentPatch, produceNames(gained), produceNames(picked));
			}
		}
		else if (currentAction == StepAdvisor.Action.PLANT && currentPatch != null)
		{
			// The seed leaving the inventory at the patch is planting, a tick or so before the patch reads as planted
			final Crop crop = plantingFor(currentPatch);
			if (crop != null && plantUsed(crop.getPlantItemId(), lastSeen, held) && !navigator.farFrom(currentPatch))
			{
				markPlanted(currentPatch, runService.itemName(crop.getPlantItemId()) + " left the inventory");
			}
		}
		// The buckets a visit to the tool leprechaun said to store stay in it, but only while it's the step in hand
		if (currentAction != StepAdvisor.Action.NOTE && currentAction != StepAdvisor.Action.TAKE_COMPOST)
		{
			storeDue = false;
		}
		lastSeen = held;
		atStopPatches = here;
		binInHand = null;
		emptyingBin = false;
		final CompostBin bin = binAt(here);
		if (bin != null)
		{
			final Patch at = currentPatch != null && here.contains(currentPatch) ? currentPatch : here.get(0);
			watchBinFilling(bin, here);
			// A full bin is closed, and one being filled is kept at until it's full or the crops for it run out, before
			// picking on (elzerma, 2026-10-30: "when we've begun adding stuff to bin it should keep you at the bin til the
			// bin is full or you're out of eligible items to add", then "guide you to close the bin. finish picking and note
			// produce")
			final String fill = fillBin(bin, at, true);
			if (fill != null)
			{
				return fill;
			}
			// A ready bin is emptied first, from arriving, as its buckets need the room (P3, elzerma 2026-10-30)
			final String empty = emptyBin(bin, at);
			if (empty != null)
			{
				return empty;
			}
		}
		for (Patch patch : inOrder(here))
		{
			final Progress p = progress(patch);
			if (p.skipped)
			{
				p.done = true;
				continue;
			}
			// Seen done stays done (its tick is locked), so a stop passed again after going back needs nothing
			if (p.done)
			{
				continue;
			}
			PatchPrediction live = patchTracker.predict(patch);
			// Only trust a reading taken since arriving: an estimate from the last visit can say a tree is
			// still growing when it's ready, which would skip the patch
			if (live == null || live.getObservedAt() < navigator.getArrivedAtSeconds())
			{
				currentPatch = patch;
				currentAction = StepAdvisor.Action.INSPECT;
				return navigator.farFrom(patch) ? navigator.walkTo(stop, patch, "Walk", null)
					: "Walk to the " + patch.getDisplayName() + " patch";
			}
			if (live.getState() != PatchState.GROWING && patchTracker.getPatchesInRange().contains(patch))
			{
				p.sawUnplanted = true;
			}
			if (live.getCrop() == null || live.getState() == PatchState.DEAD || live.getState() == PatchState.STUMP)
			{
				p.sawEmpty = true;
			}
			if (live.getState() == PatchState.EMPTY && justPlanted(p))
			{
				live = asPlanted(live);
			}
			// Farming contracts (docs/plans/farming-contracts.md): a guild patch's part as it stands, unless this run is
			// planting it (then its compost and payment follow as usual)
			final ContractPlanner.Role role = p.plantedTick < 0 && !p.sawEmpty ? contractRole(patch) : null;
			if (role == ContractPlanner.Role.LEAVE)
			{
				// A pre-plant waiting for its contract, the contract's own crop still growing, or a crop off the plan to pick
				// once grown
				p.done = true;
				p.leftForContract = true;
				continue;
			}
			// A crop still growing is dug up to make way; a grown one is checked or picked first, like any harvest
			if (role == ContractPlanner.Role.CLEAR_FOR_CONTRACT && !ContractPlanner.ready(patch, live))
			{
				currentPatch = patch;
				currentAction = StepAdvisor.Action.CLEAR_DEAD;
				final Crop wanted = contractTracker.getContract().getCrop();
				final String dig = "Dig up the " + live.getCrop().getDisplayName().toLowerCase() + " with your spade, for the "
					+ "contract's " + wanted.getDisplayName().toLowerCase();
				return navigator.farFrom(patch) ? navigator.walkTo(stop, patch, "Walk", dig) : dig;
			}
			// A flower planted this run still gets its compost, if any. The guild's is the contracts' with them on, as planned
			if (!contractPatches().contains(patch) && keepsProtectingFlower(patch, live)
				&& (live.getState() != PatchState.GROWING || !p.sawUnplanted))
			{
				// Picking it would end the allotments' protection
				p.done = true;
				continue;
			}
			final StepAdvisor.Advice advice = StepAdvisor.advise(goal(patch, p), live);
			if (advice.getAction() == StepAdvisor.Action.PAY)
			{
				watchPayment(patch, p);
				if (p.paid)
				{
					p.done = true;
					continue;
				}
			}
			if (advice.getAction() == StepAdvisor.Action.DONE)
			{
				p.done = true;
				continue;
			}
			// The crops for the compost bin go in it once there are none left to pick here, the patches before planted and
			// composted as they're picked, or when the inventory's full (below), and it's closed once full (elzerma,
			// 2026-10-31, at Port Phasmatys: "it should have told me to plant and compost before putting in bin, then proceed
			// to pick other patch and once the patch is empty or inv is full, dump into bin")
			if (bin != null && !binCropsToPick(here))
			{
				final String fill = fillBin(bin, patch, false);
				if (fill != null)
				{
					return fill;
				}
			}
			// Picked and about to plant: the stop's one visit to the tool leprechaun comes first, to note what was picked,
			// store the empty buckets and take the stop's compost all at once (elzerma, 2026-10-30: "it should tell you to
			// pick stuff first then when you're already going to note produce on the leprechaun it should then tell you to
			// dump buckets/grab ultracompost"), when it's needed. Not once planting or composting has begun here: the player
			// went their own way.
			if (!visited && advice.getAction() == StepAdvisor.Action.PLANT)
			{
				final String visit = plantingBegun(here) || !visitNeeded(patch, here) ? null : cleanThenVisit(patch, Visit.BEFORE_PLANTING);
				if (visit != null)
				{
					return visit;
				}
				visited = true;
			}
			// The inventory full while picking: making room is the step, with what to use outlined, rather than a reminder
			// under "Pick" with the patch outlined (elzerma, 2026-10-31, at Civitas illa Fortis): the crops for the compost
			// bin go in it while it has room, else the produce is cleaned and noted with the tool leprechaun
			if (freeInventorySlots() == 0 && expectedHarvest(patch, advice.getAction(), live) > 0)
			{
				final String fill = bin != null ? fillBin(bin, patch, false) : null;
				if (fill != null)
				{
					return fill;
				}
				final String note = cleanThenVisit(patch, Visit.FOR_ROOM);
				if (note != null)
				{
					return note;
				}
			}
			return step(stop, patch, advice);
		}
		// Every patch done: the bin filled and closed, and the herbs cleaned. What was picked is noted before leaving only when
		// the room's needed for what the next stop brings in; otherwise it comes along, to note on a visit there made anyway
		// (elzerma, 2026-10-31: "we want to save trips to the leprechaun because it can be a time waste to run back and forth
		// several times if you don't NEED to run back and forth")
		if (navigator.nearAny(here))
		{
			final String fill = bin != null ? fillBin(bin, here.get(0), false) : null;
			if (fill != null)
			{
				return fill;
			}
			final String clean = cleanStep(here.get(0));
			if (clean != null)
			{
				return clean;
			}
			final String note = storeDue || freeInventorySlots() < harvestToCome(null, next)
				|| bucketsForNextBin(inventoryAsHeld()) > 0 ? leprechaunVisit(here.get(0), Visit.BEFORE_LEAVING) : null;
			if (note != null)
			{
				return note;
			}
		}
		// Last at the guild: Guildmaster Jane, to hand in a contract, take another or ask for an easier one
		final String jane = janeStep(stop, here);
		if (jane != null)
		{
			return jane;
		}
		clearCurrent();
		return null;
	}

	/** The guild's contract patches the run looks after, with contracts on; none with them off. */
	private List<Patch> contractPatches()
	{
		return RunSelector.contractPatches(settings.getRunConfig(), accessChecker.getSnapshot());
	}

	/**
	 * The crop a patch is planted with: a guild contract patch's as the contract stands now (a new one mid-stop can want
	 * another), else the plan's.
	 */
	private Crop plantingFor(Patch patch)
	{
		final List<Patch> guild = contractPatches();
		if (!guild.contains(patch))
		{
			return plan.getSupplies().getPlantings().get(patch);
		}
		final int farming = farming();
		final RunConfig config = settings.getRunConfig();
		final Holdings holdings = holdingsTracker.getHoldings();
		return ContractPlanner.crop(patch, contractTracker.getContract(), farming, guild, patchTracker::predict,
			c -> holdings.count(c.getPlantItemId()), t -> config.cropFor(t, farming));
	}

	/** The Farming level, for the contract's tier and the pre-planting plan; 99 until it's known, as the planner has it. */
	private int farming()
	{
		final AccessSnapshot access = accessChecker.getSnapshot();
		return access.isKnown() ? access.level(Skill.FARMING) : 99;
	}

	/** A guild contract patch's part as it reads now (ContractPlanner), or null for another patch or contracts off. */
	private ContractPlanner.Role contractRole(Patch patch)
	{
		final List<Patch> guild = contractPatches();
		return guild.contains(patch) ? ContractPlanner.role(patch, contractTracker.getContract(), farming(), guild,
			patchTracker::predict, stock()) : null;
	}

	/** How many of a crop's seed or sapling the player has, wherever it's kept: for the guild's pre-plants. */
	private ToIntFunction<Crop> stock()
	{
		final Holdings holdings = holdingsTracker.getHoldings();
		return crop -> holdings.count(crop.getPlantItemId());
	}

	/**
	 * With contracts on, a guild contract patch outside the plan joins the guild stop for the contract in hand, read since
	 * arriving: one holding its crop to take, or the patch its crop is to be planted in, cleared first if need be. Once in,
	 * it stays to the stop's end, so a crop taken is replanted with its pre-plant and a contract's crop planted, their
	 * seeds from the seed vault there, and the stop goes on from contract to contract until there's nothing to do for one
	 * (elzerma, 2026-11-03: "contract helper should guide you to clear/plant next without having to gen a new run ... and
	 * if you're chaining contracts bc you have preplanted it should keep going until you're in idle again"; that morning
	 * the snapdragon contract took a second run). A pre-plant waiting for its contract never joins: on elzerma's run of
	 * 2026-11-02 every guild patch did, and the stop began at the north allotment until the patches were read. When the
	 * contract changes, the planned patches passed over as waiting for one are looked at again.
	 */
	private List<Patch> contractStop(RouteStop stop, List<Patch> here)
	{
		final List<Patch> guild = contractPatches();
		if (guild.isEmpty() || stop.getLocation() != Location.FARMING_GUILD)
		{
			return here;
		}
		final ContractTracker.Contract contract = contractTracker.getContract();
		if (!contract.equals(contractSeen))
		{
			contractSeen = contract;
			for (Patch patch : guild)
			{
				final Progress p = progress.get(patch);
				if (p != null && p.leftForContract)
				{
					p.done = false;
					p.leftForContract = false;
				}
			}
		}
		final int farming = farming();
		final ToIntFunction<Crop> stock = stock();
		final Patch forContract = contract.held() && contract.getCrop() != null
			? ContractPlanner.contractPatch(contract, farming, guild, patchTracker::predict) : null;
		final List<Patch> all = new ArrayList<>(here);
		for (Patch patch : guild)
		{
			if (all.contains(patch))
			{
				continue;
			}
			final PatchPrediction live = patchTracker.predict(patch);
			if (!contractExtras.contains(patch) && contract.held() && live != null
				&& live.getObservedAt() >= navigator.getArrivedAtSeconds())
			{
				final ContractPlanner.Role role = ContractPlanner.role(patch, contract, farming, guild,
					patchTracker::predict, stock);
				if (role == ContractPlanner.Role.TAKE && live.getCrop() == contract.getCrop()
					|| patch == forContract && role != ContractPlanner.Role.LEAVE)
				{
					contractExtras.add(patch);
					log.debug("Farming contract: {} joins the guild stop for the {} contract ({})", patch.getDisplayName(),
						contract.getCrop().getDisplayName().toLowerCase(), role);
				}
			}
			if (contractExtras.contains(patch))
			{
				all.add(patch);
			}
		}
		return all;
	}

	/**
	 * Guildmaster Jane at the end of the guild stop (contracts on): hand in a grown contract and take another, ask for one
	 * when there's none, or for an easier one when a slow crop's patches are all taken; the highest tier the level allows
	 * (elzerma, 2026-11-02). Null when there's nothing to see her about, which the contract's varbits show once she's
	 * been seen.
	 */
	private String janeStep(RouteStop stop, List<Patch> here)
	{
		final List<Patch> guild = contractPatches();
		if (guild.isEmpty() || stop.getLocation() != Location.FARMING_GUILD)
		{
			return null;
		}
		final ContractTracker.Contract contract = contractTracker.getContract();
		final ContractPlanner.Jane jane = ContractPlanner.jane(contract, farming(), guild, patchTracker::predict);
		if (jane == ContractPlanner.Jane.NONE)
		{
			return null;
		}
		final FarmingContract.Tier ask = FarmingContract.Tier.highest(farming());
		final String tier = ask != null ? ask.getDisplayName() + " " : "";
		currentPatch = here.get(0);
		currentAction = StepAdvisor.Action.JANE;
		switch (jane)
		{
			case HAND_IN:
				// She keeps the seed pack until there's room for it (wiki: Guildmaster Jane)
				return "Talk to Guildmaster Jane: hand in your contract, then take another " + tier + "contract"
					+ (freeInventorySlots() == 0 ? " (make room for the seed pack first)" : "");
			case NEW:
				return "Talk to Guildmaster Jane: ask for a " + tier + "contract";
			default:
				final Crop crop = contract.getCrop();
				return "Talk to Guildmaster Jane: ask for an easier contract (" + crop.getDisplayName().toLowerCase()
					+ " takes " + ContractPlanner.clock(crop.getGrowthMinutes()) + " to grow)";
		}
	}

	/**
	 * The visit to the tool leprechaun before planting is needed: there's compost, or the seed dibber or spade, to take from
	 * him for the stop's planting, or too little room left for what its other patches will bring in. Otherwise noting
	 * waits (elzerma, 2026-10-31, at Hosidius: sent from the compost bin across the farm to note 2 watermelons before
	 * planting, with the stop's ultracompost carried from the bin and 22 slots free).
	 */
	private boolean visitNeeded(Patch picked, List<Patch> here)
	{
		return !toTake().isEmpty() || freeInventorySlots() < harvestToCome(picked, here);
	}

	/** Inventory slots the stop's other patches ready to pick will fill, as the inventory-full reminder reckons them. */
	private int harvestToCome(Patch picked, List<Patch> here)
	{
		int slots = 0;
		for (Patch patch : here)
		{
			final Progress p = progress(patch);
			final PatchPrediction live = patchTracker.predict(patch);
			if (patch == picked || p.done || p.skipped || live == null)
			{
				continue;
			}
			if (live.getState() == PatchState.HARVESTABLE)
			{
				slots += expectedHarvest(patch, StepAdvisor.Action.PICK, live);
			}
			else if (live.getState() == PatchState.CHECK_HEALTH)
			{
				slots += expectedHarvest(patch, StepAdvisor.Action.CHECK_HEALTH, live);
			}
		}
		return slots;
	}

	/** The stop's compost bin while Make compost is on (P3), or null. */
	private CompostBin binAt(List<Patch> here)
	{
		return settings.getRunConfig().isMakeCompost() && !here.isEmpty() ? CompostBin.at(here.get(0).getLocation()) : null;
	}

	/** What emptying a bin of compost comes to next (P3). */
	enum BinNext
	{
		/** The empty buckets from the tool leprechaun, before opening it. */
		TAKE_BUCKETS,
		OPEN,
		/** The volcanic ash carried, before any compost is taken out. */
		ADD_ASH,
		TAKE_OUT,
		/** Nothing to take it out with, carried or with the leprechaun: it's left as it is. */
		NONE
	}

	/**
	 * The next thing to do to empty a bin with compost in it (elzerma, 2026-10-30: "go to leprechaun to remove buckets and
	 * then open and either a) withdraw or b) ... add it to the bin before removing it into buckets"): the empty buckets
	 * from the tool leprechaun first when none are carried. With some carried, what they hold is taken out first and the
	 * rest fetched while storing it: a trip to top them up first is never fewer trips, and one more when they don't all fit
	 * (elzerma, 2026-10-31: "we want to save trips to the leprechaun"; at the Farming Guild, sent for 5 with room for 1 and
	 * 25 in hand). Then open it once rotted, add the ash for ultracompost when carried and worth it, and take it out.
	 *
	 * @param buckets empty buckets carried
	 * @param stored  empty buckets the tool leprechaun holds
	 * @param room    inventory slots for buckets: free, and freed by storing what's been taken out
	 * @param ash     volcanic ash carried, or 0 while ultracompost isn't wanted
	 */
	static BinNext binNext(BinState state, CompostBin bin, int buckets, boolean bottomless, int stored, int room, int ash)
	{
		if (!bottomless && buckets == 0 && stored > 0)
		{
			return BinNext.TAKE_BUCKETS;
		}
		if (!bottomless && buckets == 0)
		{
			return BinNext.NONE;
		}
		if (state.getStage() == BinState.Stage.ROTTED)
		{
			return BinNext.OPEN;
		}
		return state.takesAsh(bin.ashForUltracompost()) && ash >= bin.ashForUltracompost() ? BinNext.ADD_ASH : BinNext.TAKE_OUT;
	}

	/** Buckets of compost in a bin with some: all it holds while it's closed. */
	static int compostIn(BinState state, CompostBin bin)
	{
		return state.getStage() == BinState.Stage.ROTTED ? bin.capacity() : state.getCount();
	}

	/**
	 * The stop's ready bin emptied first, from arriving (P3, elzerma 2026-10-30: "empty it first", as their 2026-10-24 run
	 * did), as {@link #binNext} has it. Null once there's nothing more to do.
	 */
	private String emptyBin(CompostBin bin, Patch at)
	{
		final BinState state = patchTracker.binState(bin);
		final Map<Integer, Integer> held = inventoryAsHeld();
		final int buckets = held.getOrDefault(ItemID.BUCKET_EMPTY, 0);
		if (state != null && state.hasCompost())
		{
			final int bottomless = held.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 0) > 0
				? ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED
				: held.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET, 0) > 0 ? ItemID.BOTTOMLESS_COMPOST_BUCKET : -1;
			final int stored = holdingsTracker.getHoldings().in(Holdings.Source.LEPRECHAUN)
				.getOrDefault(ItemID.BUCKET_EMPTY, 0);
			final int ash = settings.getRunConfig().isMakeUltracompost()
				? held.getOrDefault(ItemID.FOSSIL_VOLCANIC_ASH, 0) : 0;
			// What's been taken out is stored to make room for more
			final Map<Compost, Integer> spare = binCompostSpare(held);
			final int room = freeInventorySlots() + spare.values().stream().mapToInt(Integer::intValue).sum();
			final int wanted = Math.min(compostIn(state, bin) - buckets, stored);
			final int take = room > 0 ? Math.min(wanted, room) : wanted;
			// A visit for empty buckets stays the step while his window's open, until all it says is taken and stored, rather
			// than sending the player back to the bin with the first bucket (elzerma's run of 2026-11-03 at the Farming Guild:
			// back to the bin with 1 of 6, 20 ultracompost still to store)
			bucketVisit = bucketVisit && leprechaunWindowOpen() && (take > 0 || !spare.isEmpty());
			final BinNext next = bucketVisit ? BinNext.TAKE_BUCKETS
				: binNext(state, bin, buckets, bottomless >= 0, stored, room, ash);
			emptyingBin = next != BinNext.NONE;
			currentPatch = at;
			binInHand = bin;
			binItems = Collections.emptySet();
			switch (next)
			{
				case TAKE_BUCKETS:
				{
					// No more than fit (elzerma's run of 2026-10-31: "take 30" with room for 25), unless there's no room
					// at all yet; what's left of it while his window stays open
					final List<String> store = counted(spare);
					bucketVisit = true;
					visitTake = take > 0 ? Collections.singletonMap(ItemID.BUCKET_EMPTY, take) : Collections.emptyMap();
					visitStore = byItem(spare);
					currentAction = StepAdvisor.Action.TAKE_COMPOST;
					return "At the tool leprechaun: " + (store.isEmpty() ? "" : "store " + join(store))
						+ (store.isEmpty() || take <= 0 ? "" : " and ")
						+ (take > 0 ? "take " + take + (take == 1 ? " empty bucket" : " empty buckets") : "");
				}
				case OPEN:
					currentAction = StepAdvisor.Action.OPEN_BIN;
					return "Open the compost bin";
				case ADD_ASH:
					currentAction = StepAdvisor.Action.ADD_ASH;
					binItems = Collections.singleton(ItemID.FOSSIL_VOLCANIC_ASH);
					return "Add " + bin.ashForUltracompost() + " volcanic ash to the compost bin";
				case TAKE_OUT:
					currentAction = StepAdvisor.Action.EMPTY_BIN;
					binItems = Collections.singleton(buckets > 0 ? ItemID.BUCKET_EMPTY : bottomless);
					return "Take the " + state.getCompost().getDisplayName().toLowerCase() + " out of the compost bin";
				default:
					binInHand = null;
					break;
			}
		}
		// Emptied here: its compost past what this stop and the stops ahead in reach will use is stored before picking, with
		// him by the bin, so the harvest and the crops for the bin have the room (elzerma, 2026-10-31: "we should do a trip to
		// leprechaun to store the ultracompost before harvesting watermelons and filling the bin"). Once picking's begun here,
		// it waits for a visit made anyway, rather than breaking into it
		bucketVisit = false;
		return binTaken.isEmpty() || pickedHere ? null : leprechaunVisit(at, Visit.AFTER_BIN);
	}

	/** The tool leprechaun's window is open. Client thread. */
	private boolean leprechaunWindowOpen()
	{
		final Widget window = client.getWidget(InterfaceID.FarmingTools.UNIVERSE);
		return window != null && !window.isHidden();
	}

	/** The compost taken out of this stop's bin that its patches won't use, by type; empty for none. */
	private Map<Compost, Integer> binCompostSpare(Map<Integer, Integer> held)
	{
		final Map<Compost, Integer> need = compostStillNeeded(holdingsTracker.getHoldings().in(Holdings.Source.INVENTORY));
		final Map<Compost, Integer> spare = new EnumMap<>(Compost.class);
		for (Compost compost : Compost.values())
		{
			final int count = compost == Compost.NONE ? 0 : Math.min(binTaken.getOrDefault(compost.getItemId(), 0),
				held.getOrDefault(compost.getItemId(), 0) - need.getOrDefault(compost, 0));
			if (count > 0)
			{
				spare.put(compost, count);
			}
		}
		return spare;
	}

	/** e.g. "12 ultracompost" for each kind. */
	private static List<String> counted(Map<Compost, Integer> compost)
	{
		final List<String> parts = new ArrayList<>();
		compost.forEach((kind, count) -> parts.add(count + " " + kind.getDisplayName().toLowerCase()));
		return parts;
	}

	/**
	 * Notes the player putting crops in the stop's bin: its count going up while it's in range, read at each step (P3).
	 */
	private void watchBinFilling(CompostBin bin, List<Patch> here)
	{
		if (!navigator.inRange(here))
		{
			return;
		}
		final int count = fillCount(patchTracker.binState(bin));
		if (binCountSeen >= 0 && count > binCountSeen)
		{
			fillingBin = true;
		}
		binCountSeen = count;
	}

	/** How many crops are in a bin they can go in, or -1 when they can't (it has compost, is rotting, or is unknown). */
	static int fillCount(BinState state)
	{
		return state == null || !state.fills() ? -1 : state.getStage() == BinState.Stage.FILLING ? state.getCount() : 0;
	}

	/**
	 * "Close the compost bin" once it's full, and "Put your watermelon in the compost bin (4/15)" while it has room and the
	 * crops for it picked here are carried (P3); null otherwise, as while it's rotting or holds the player's own tomatoes.
	 *
	 * @param whileFilling only once the player has begun putting them in: the step then stays on the bin until it's full or
	 *                     they run out, rather than going back to picking after the first
	 */
	private String fillBin(CompostBin bin, Patch at, boolean whileFilling)
	{
		final BinState state = patchTracker.binState(bin);
		final int count = fillCount(state);
		if (count < 0)
		{
			return null;
		}
		if (count >= bin.capacity())
		{
			currentPatch = at;
			binInHand = bin;
			binItems = Collections.emptySet();
			currentAction = StepAdvisor.Action.CLOSE_BIN;
			return "Close the compost bin";
		}
		final Set<Integer> crops = binCropsHeld();
		if (crops.isEmpty())
		{
			fillingBin = false;
			return null;
		}
		if (whileFilling && !fillingBin)
		{
			return null;
		}
		currentPatch = at;
		binInHand = bin;
		binItems = crops;
		currentAction = StepAdvisor.Action.FILL_BIN;
		return "Put your " + produceNames(crops) + " in the compost bin (" + count + "/" + bin.capacity() + ")";
	}

	/** The crops for the compost bins picked at this stop, still carried unnoted. */
	private Set<Integer> binCropsHeld()
	{
		final Map<Integer, Integer> held = inventoryAsHeld();
		final Set<Integer> crops = new LinkedHashSet<>();
		for (Crop crop : settings.getRunConfig().getBinCrops())
		{
			final int item = CompostBin.cropItem(crop);
			if (item > 0 && picked.contains(item) && held.getOrDefault(item, 0) > 0)
			{
				crops.add(item);
			}
		}
		return crops;
	}

	/** The stop's bin can take more of the crops picked here (P3): they aren't noted while it can. */
	private boolean binHasRoom()
	{
		final CompostBin bin = binAt(atStopPatches);
		final int count = bin != null ? fillCount(patchTracker.binState(bin)) : -1;
		return count >= 0 && count < bin.capacity();
	}

	/** The stop's bin is being emptied, so the empty buckets for it aren't to be dropped or stored (P3). */
	boolean isEmptyingBin()
	{
		return emptyingBin;
	}

	/**
	 * Where the step in hand is done, for the walking line: the tool leprechaun for his steps and the compost bin for its
	 * (elzerma, 2026-10-30: "Highlight should go to leprechaun to remove buckets"), else the patch. The leprechaun where
	 * he was first seen at the stop, as he wanders; until he's loaded (NPCs show from nearer than objects), the stop's bin
	 * when the step is for it, else the patch. Null with no step in hand.
	 */
	WorldPoint stepPoint()
	{
		if (currentPatch == null || currentAction == null)
		{
			return null;
		}
		// Guildmaster Jane, where she's seen, else where she stands (contracts)
		if (currentAction == StepAdvisor.Action.JANE)
		{
			final NPC jane = scene.transferNpc(FarmingContract.JANE);
			return jane != null ? jane.getWorldLocation() : FarmingContract.JANE_POINT;
		}
		final GameObject object = binInHand != null ? scene.binObject(binInHand) : null;
		final WorldPoint workAt = object != null ? object.getWorldLocation() : scene.locationOf(currentPatch);
		if ((currentAction == StepAdvisor.Action.NOTE || currentAction == StepAdvisor.Action.TAKE_COMPOST) && workAt != null)
		{
			// The one nearest the bin or patch (the Farming Guild has several), not one still loaded from the stop before
			final NPC nearest = scene.nearestLeprechaun(workAt);
			if (nearest != null && nearest != leprechaun
				&& nearest.getWorldLocation().distanceTo(workAt) <= LEPRECHAUN_NEAR_TILES)
			{
				leprechaun = nearest;
				leprechaunAt = nearest.getWorldLocation();
			}
			if (leprechaunAt != null)
			{
				return leprechaunAt;
			}
		}
		// The patch's tile nearest the player, not its middle (elzerma, 2026-11-01)
		return object != null ? workAt : patchTile();
	}

	/**
	 * The current patch's tile nearest the player, following them: it moves to another of the patch's tiles once that one
	 * is {@link #RETARGET_TILES} nearer, so the walking line isn't asked for again at every step taken (elzerma's run of
	 * 2026-11-01: worked out as the step began at the north allotment, it stayed at the south one's far tip after they
	 * filled the compost bin on its other side). Its map point until its object loads; null with no patch in hand.
	 */
	WorldPoint patchTile()
	{
		if (currentPatch == null)
		{
			return null;
		}
		final Player player = client.getLocalPlayer();
		final WorldPoint at = player != null ? player.getWorldLocation() : null;
		final WorldPoint nearest = scene.nearestTileOf(currentPatch, at);
		if (nearest != null && (patchTileOf != currentPatch || retarget(patchTile, nearest, at)))
		{
			log.debug("Patch tile for {}: {}, {} tiles from {}", currentPatch, nearest, nearest.distanceTo2D(at), at);
			patchTile = nearest;
			patchTileOf = currentPatch;
		}
		return patchTileOf == currentPatch && patchTile != null ? patchTile : scene.locationOf(currentPatch);
	}

	/** Move the patch's tile from the one shown to the nearest: none shown, or the nearest is enough nearer the player. */
	static boolean retarget(WorldPoint shown, WorldPoint nearest, WorldPoint at)
	{
		return shown == null || nearest.distanceTo2D(at) + RETARGET_TILES <= shown.distanceTo2D(at);
	}

	/** Planted or treated here this run: the player has started on the stop's planting their own way. */
	private boolean plantingBegun(List<Patch> here)
	{
		for (Patch patch : here)
		{
			final Progress p = progress(patch);
			final PatchPrediction live = patchTracker.predict(patch);
			if (p.composted || p.paid || p.sawUnplanted && (justPlanted(p)
				|| live != null && live.getState() == PatchState.GROWING))
			{
				return true;
			}
		}
		return false;
	}

	/** The step at a patch, with the walk to it first while it's still far off. */
	private String step(RouteStop stop, Patch patch, StepAdvisor.Advice advice)
	{
		currentPatch = patch;
		currentAction = advice.getAction();
		// The area loads well before the patch is reached, and some places have several patches
		if (advice.getAction() != StepAdvisor.Action.INSPECT && navigator.farFrom(patch))
		{
			return navigator.walkTo(stop, patch, "Run", advice.getText());
		}
		return advice.getText();
	}

	/**
	 * The stop's visit to the tool leprechaun as one step, for whichever of these are needed: noting what was picked here
	 * (noting on, as by default), storing the compost bin's spare compost and the empty buckets (Empty buckets: Store), and
	 * taking the compost the stop's patches still need, e.g. "At the tool leprechaun: note your watermelon, store 13
	 * ultracompost, 2 empty buckets and take 1 supercompost"; null when none is. Storing is never a visit's only reason,
	 * but for room (elzerma, 2026-10-31: "Don't need to store compost til we have a second reason to go to leprechaun").
	 *
	 * @param why what the visit is made for
	 */
	private String leprechaunVisit(Patch at, Visit why)
	{
		final List<String> parts = new ArrayList<>();
		produce = settings.getRunConfig().isNoteProduce() ? unnotedProduce() : Collections.emptySet();
		// The crops for the compost bin go in it while it has room, not to the leprechaun (P3)
		if (binHasRoom())
		{
			produce.removeAll(binCropsHeld());
		}
		if (!produce.isEmpty())
		{
			parts.add("note your " + produceNames(produce));
		}
		final boolean beforePlanting = why == Visit.BEFORE_PLANTING;
		final Map<Integer, Integer> held = inventoryAsHeld();
		final int forNextBin = why == Visit.BEFORE_LEAVING ? bucketsForNextBin(held) : 0;
		// Still to store once the noting's done, as storing is a step of its own with him (elzerma's run of 2026-10-31
		// finished at the noting, the buckets carried off)
		if (why == Visit.BEFORE_LEAVING && parts.isEmpty() && forNextBin == 0 && !storeDue)
		{
			return null;
		}
		// Not the empty buckets the next stop's compost bin will take out its compost with
		final int buckets = settings.getRunConfig().getEmptyBuckets() == EmptyBuckets.STORE && !nextBinReady()
			? held.getOrDefault(ItemID.BUCKET_EMPTY, 0) : 0;
		final Set<Integer> tools = beforePlanting ? plantingToolsToTake() : Collections.emptySet();
		final int room = freeInventorySlots() + freedByNoting(held) + buckets - tools.size();
		final CompostTrade compost = compostTradeHere(at, beforePlanting, room);
		// Only for the bin's compost, with nothing else to store: then there's no reason to go
		if (why == Visit.AFTER_BIN && compost.store.isEmpty() && !storeDue)
		{
			return null;
		}
		final List<String> store = counted(compost.store);
		if (buckets > 0)
		{
			store.add(buckets + (buckets == 1 ? " empty bucket" : " empty buckets"));
		}
		storeDue = !store.isEmpty();
		for (int i = 0; i < store.size(); i++)
		{
			parts.add(i == 0 ? "store " + store.get(i) : store.get(i));
		}
		final List<String> take = counted(compost.take);
		for (int tool : tools)
		{
			take.add(withArticle(runService.itemName(tool).toLowerCase()));
		}
		// As many as fit, once the rest is noted and stored
		final int emptyBuckets = Math.min(forNextBin, room - compost.slots());
		if (emptyBuckets > 0)
		{
			take.add(emptyBuckets + (emptyBuckets == 1 ? " empty bucket" : " empty buckets"));
		}
		for (int i = 0; i < take.size(); i++)
		{
			parts.add(i == 0 ? "take " + take.get(i) : take.get(i));
		}
		if (parts.isEmpty())
		{
			return null;
		}
		// For his window's outlines
		final Map<Integer, Integer> taking = byItem(compost.take);
		tools.forEach(tool -> taking.put(tool, 1));
		if (emptyBuckets > 0)
		{
			taking.put(ItemID.BUCKET_EMPTY, emptyBuckets);
		}
		final Map<Integer, Integer> storing = byItem(compost.store);
		if (buckets > 0)
		{
			storing.put(ItemID.BUCKET_EMPTY, buckets);
		}
		visitTake = taking;
		visitStore = storing;
		currentPatch = at;
		currentAction = !produce.isEmpty() ? StepAdvisor.Action.NOTE : StepAdvisor.Action.TAKE_COMPOST;
		return "At the tool leprechaun: " + join(parts);
	}

	/** Compost counts by item, for the outlines in the tool leprechaun's window. */
	private static Map<Integer, Integer> byItem(Map<Compost, Integer> compost)
	{
		final Map<Integer, Integer> items = new HashMap<>();
		compost.forEach((kind, count) -> items.put(kind.getItemId(), count));
		return items;
	}

	/** What a visit to the tool leprechaun is made for (docs/plans/patch-actions.md 8.1). */
	enum Visit
	{
		/** Before the stop's planting: compost or a tool to take for it, or room for the stop's other patches. */
		BEFORE_PLANTING,
		/** The inventory is full while picking: storing makes room, so it's reason enough. */
		FOR_ROOM,
		/** The stop's compost bin was just emptied: its compost is stored before picking, with him by the bin. */
		AFTER_BIN,
		/** Every patch done: noting when the room's needed for the next stop, or buckets for its compost bin. */
		BEFORE_LEAVING
	}

	/** The next stop's compost bin is expected ready, with Make compost on: empty buckets are for it. */
	private boolean nextBinReady()
	{
		final CompostBin bin = laterStops.isEmpty() ? null : binAt(laterStops.get(0));
		final BinState state = bin != null ? patchTracker.predictBin(bin) : null;
		return state != null && state.hasCompost();
	}

	/**
	 * Empty buckets to take before leaving for the next stop's compost bin, when it's expected ready: what it holds, less
	 * those carried, as many as he has (elzerma, 2026-10-31, leaving Hosidius for Port Phasmatys: "2x ultracompost were used
	 * so we should pull 13 out for the next stop"). None with a bottomless bucket.
	 */
	private int bucketsForNextBin(Map<Integer, Integer> held)
	{
		if (!nextBinReady() || held.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET, 0) > 0
			|| held.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 0) > 0)
		{
			return 0;
		}
		final CompostBin bin = binAt(laterStops.get(0));
		final int stock = holdingsTracker.getHoldings().in(Holdings.Source.LEPRECHAUN).getOrDefault(ItemID.BUCKET_EMPTY, 0);
		return Math.max(0, Math.min(compostIn(patchTracker.predictBin(bin), bin)
			- held.getOrDefault(ItemID.BUCKET_EMPTY, 0), stock));
	}

	/** The empty buckets carried are for a compost bin, this stop's or the next's, so aren't to be dropped (P3). */
	boolean keepsBuckets()
	{
		return emptyingBin || nextBinReady();
	}

	/**
	 * The step log's last decision line of each kind (the text before its colon), so each is logged once when it changes
	 * (debug: the developer client only). By kind, as "Compost for the stop" and "Compost for the visit" alternated and
	 * were each logged every tick (elzerma's run of 2026-11-01).
	 */
	private final Map<String, String> loggedDecisions = new HashMap<>();

	private void logDecision(String decision)
	{
		final int colon = decision.indexOf(':');
		if (!decision.equals(loggedDecisions.put(colon > 0 ? decision.substring(0, colon) : decision, decision)))
		{
			log.debug(decision);
		}
	}

	private static Map<Compost, Integer> countsOf(Set<Compost> composts, Map<Integer, Integer> held)
	{
		final Map<Compost, Integer> counts = new EnumMap<>(Compost.class);
		for (Compost compost : composts)
		{
			counts.put(compost, held.getOrDefault(compost.getItemId(), 0));
		}
		return counts;
	}

	/** Steps whose gains are the patch's produce: picking, and checking a tree's health (its first fruit). */
	static boolean harvests(StepAdvisor.Action action)
	{
		return action == StepAdvisor.Action.PICK || action == StepAdvisor.Action.CHECK_HEALTH;
	}

	/** The produce picked at this stop still unnoted in the inventory, grimy herbs cleaned since included. */
	private Set<Integer> unnotedProduce()
	{
		final Map<Integer, Integer> held = inventoryAsHeld();
		final Set<Integer> gained = new LinkedHashSet<>();
		for (int id : withCleanForms(picked))
		{
			if (held.getOrDefault(id, 0) > 0)
			{
				gained.add(id);
			}
		}
		// Not produce: what the run brought (seeds, payments), and logs from chopping a tree
		for (com.farmrunautopilot.supply.SupplyLine line : plan.getSupplies().getLines())
		{
			for (int id : line.getItemIds())
			{
				gained.remove(id);
			}
		}
		gained.removeIf(id -> runService.itemName(id).toLowerCase().endsWith("logs"));
		return gained;
	}

	/** Picked produce still in the inventory unnoted, or cleaned, for a grimy herb. */
	static boolean stillHeld(int id, Map<Integer, Integer> held)
	{
		final GrimyHerb herb = GrimyHerb.of(id);
		return held.getOrDefault(id, 0) > 0 || herb != null && held.getOrDefault(herb.getCleanItemId(), 0) > 0;
	}

	/**
	 * The produce picked here with the clean form of each grimy herb: herbs cleaned after picking are still the stop's to
	 * note (elzerma, 2026-10-30: cleaned at Catherby, the visit didn't say to note them).
	 */
	static Set<Integer> withCleanForms(Set<Integer> picked)
	{
		final Set<Integer> ids = new LinkedHashSet<>(picked);
		for (int id : picked)
		{
			final GrimyHerb herb = GrimyHerb.of(id);
			if (herb != null)
			{
				ids.add(herb.getCleanItemId());
			}
		}
		return ids;
	}

	/**
	 * "Clean your grimy irit leaf": the grimy herbs picked here still in the inventory that this Herblore level cleans, with
	 * Clean herbs on (P2); null otherwise.
	 */
	private String cleanStep(Patch at)
	{
		if (!settings.getRunConfig().isCleanHerbs())
		{
			return null;
		}
		toClean = cleanable(picked, inventoryAsHeld(), client.getRealSkillLevel(Skill.HERBLORE));
		if (toClean.isEmpty())
		{
			return null;
		}
		currentPatch = at;
		currentAction = StepAdvisor.Action.CLEAN;
		return "Clean your " + produceNames(toClean);
	}

	/** The grimy herbs picked here and held, that this Herblore level can clean. */
	static Set<Integer> cleanable(Set<Integer> picked, Map<Integer, Integer> held, int herblore)
	{
		final Set<Integer> grimy = new LinkedHashSet<>();
		for (int id : picked)
		{
			final GrimyHerb herb = GrimyHerb.of(id);
			if (herb != null && herb.getHerbloreLevel() <= herblore && held.getOrDefault(id, 0) > 0)
			{
				grimy.add(id);
			}
		}
		return grimy;
	}

	/** Herbs are cleaned at the patch first, then the stop's visit to the tool leprechaun. */
	private String cleanThenVisit(Patch at, Visit why)
	{
		final String clean = cleanStep(at);
		return clean != null ? clean : leprechaunVisit(at, why);
	}

	private String produceNames(Set<Integer> ids)
	{
		final List<String> names = new ArrayList<>();
		for (int id : ids)
		{
			names.add(runService.itemName(id).toLowerCase());
		}
		return join(names);
	}

	/**
	 * What's gained since arriving that the leprechaun can note: items with a noted form, more of them than were carried
	 * in; not weeds or empty buckets and pots (elzerma, 2026-10-27: "it said to note my bucket"), nor what he stores
	 * (compost, tools, plant cure) taken from him, nor the notes he gives (elzerma, 2026-10-28: the step stayed after
	 * noting, as the noted irit and watermelons counted as gained).
	 */
	static Set<Integer> produceGained(Map<Integer, Integer> before, Map<Integer, Integer> now,
		java.util.function.IntPredicate notable)
	{
		final Set<Integer> gained = new LinkedHashSet<>();
		now.forEach((id, count) ->
		{
			if (!NOT_PRODUCE.contains(id) && count > before.getOrDefault(id, 0) && notable.test(id))
			{
				gained.add(id);
			}
		});
		return gained;
	}

	/**
	 * What to take from the tool leprechaun for the stop's planting and compost, e.g. "3 ultracompost" and "a seed dibber";
	 * empty when nothing.
	 */
	private List<String> toTake()
	{
		final List<String> take = counted(compostToTake());
		for (int tool : plantingToolsToTake())
		{
			take.add(withArticle(runService.itemName(tool).toLowerCase()));
		}
		return take;
	}

	private static String withArticle(String name)
	{
		return ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}

	/** Inventory slots noting the produce frees: all but one of a kind, or all of it where its notes are carried. */
	private int freedByNoting(Map<Integer, Integer> held)
	{
		int freed = 0;
		for (int id : produce)
		{
			freed += held.getOrDefault(id, 0) - (held.getOrDefault(runService.notedId(id), 0) > 0 ? 0 : 1);
		}
		return Math.max(0, freed);
	}

	/** What a visit to the tool leprechaun takes and stores of compost (P1, P3). */
	static final class CompostTrade
	{
		final Map<Compost, Integer> take = new EnumMap<>(Compost.class);
		final Map<Compost, Integer> store = new EnumMap<>(Compost.class);

		/** Inventory slots it uses: taken, less stored. */
		int slots()
		{
			return take.values().stream().mapToInt(Integer::intValue).sum()
				- store.values().stream().mapToInt(Integer::intValue).sum();
		}
	}

	/**
	 * The compost a visit to the tool leprechaun leaves carried: what this stop's patches still need, and the next stops'
	 * too, stop by stop, while the room left still holds what this stop and those stops will bring in (elzerma,
	 * 2026-10-31, asked whether to take compost for later stops on a visit made anyway: "Yes, when there's room"); up to
	 * a stop whose compost bin will supply it. What's missing is taken, when he has it; what came out of this stop's bin
	 * past that is stored, but never what was brought, which is for later.
	 *
	 * @param carried  compost carried, by type
	 * @param fromBin  of it, what came out of this stop's compost bin
	 * @param stock    what the tool leprechaun holds
	 * @param needHere what this stop's patches still need
	 * @param later    each later stop's need in route order, up to a bank visit or the run's end; null for one whose
	 *                 compost bin will supply it
	 * @param harvests each later stop's expected harvest, in inventory slots
	 * @param room     free slots once the visit's noting and storing of buckets are done
	 * @param toCome   slots this stop's patches still to pick will fill
	 */
	static CompostTrade compostTrade(Map<Compost, Integer> carried, Map<Compost, Integer> fromBin,
		Map<Compost, Integer> stock, Map<Compost, Integer> needHere, List<Map<Compost, Integer>> later,
		List<Integer> harvests, int room, int toCome)
	{
		// This stop's whatever the room, as its patches need it
		Map<Compost, Integer> target = new EnumMap<>(Compost.class);
		target.putAll(needHere);
		CompostTrade trade = trade(target, carried, fromBin, stock, false);
		int coming = toCome;
		for (int i = 0; i < later.size() && i < harvests.size(); i++)
		{
			final Map<Compost, Integer> need = later.get(i);
			if (need == null)
			{
				break;
			}
			final Map<Compost, Integer> more = new EnumMap<>(Compost.class);
			more.putAll(target);
			need.forEach((compost, count) -> more.merge(compost, count, Integer::sum));
			final CompostTrade next = trade(more, carried, fromBin, stock, true);
			coming += harvests.get(i);
			if (next == null || room - next.slots() < coming)
			{
				break;
			}
			target = more;
			trade = next;
		}
		return trade;
	}

	/** What to take and store to carry this much compost; null when he's short of some and {@code strict}. */
	private static CompostTrade trade(Map<Compost, Integer> target, Map<Compost, Integer> carried,
		Map<Compost, Integer> fromBin, Map<Compost, Integer> stock, boolean strict)
	{
		final CompostTrade trade = new CompostTrade();
		for (Compost compost : Compost.values())
		{
			final int want = target.getOrDefault(compost, 0);
			final int have = carried.getOrDefault(compost, 0);
			if (compost == Compost.NONE)
			{
				continue;
			}
			if (want > have)
			{
				if (stock.getOrDefault(compost, 0) >= want - have)
				{
					trade.take.put(compost, want - have);
				}
				else if (strict)
				{
					return null;
				}
			}
			else if (Math.min(have - want, fromBin.getOrDefault(compost, 0)) > 0)
			{
				trade.store.put(compost, Math.min(have - want, fromBin.getOrDefault(compost, 0)));
			}
		}
		return trade;
	}

	/** {@link #compostTrade} for a visit here, with the stops ahead as RunSession gave them. */
	private CompostTrade compostTradeHere(Patch at, boolean beforePlanting, int room)
	{
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> leprechaun = holdings.in(Holdings.Source.LEPRECHAUN);
		final Map<Compost, Integer> carried = new EnumMap<>(Compost.class);
		final Map<Compost, Integer> fromBin = new EnumMap<>(Compost.class);
		final Map<Compost, Integer> stock = new EnumMap<>(Compost.class);
		for (Compost compost : Compost.values())
		{
			if (compost != Compost.NONE)
			{
				final int id = compost.getItemId();
				carried.put(compost, inventory.getOrDefault(id, 0));
				fromBin.put(compost, Math.min(binTaken.getOrDefault(id, 0), inventory.getOrDefault(id, 0)));
				stock.put(compost, leprechaun.getOrDefault(id, 0));
			}
		}
		final Map<Compost, Integer> needHere = compostStillNeeded(inventory);
		final List<Map<Compost, Integer>> later = new ArrayList<>();
		final List<Integer> harvests = new ArrayList<>();
		// A filled bottomless bucket does for every patch
		if (inventory.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 0) == 0)
		{
			for (List<Patch> stop : laterStops)
			{
				later.add(compostAt(stop));
				harvests.add(harvestToCome(null, stop));
			}
		}
		final CompostTrade trade = compostTrade(carried, fromBin, stock, needHere, later, harvests, room,
			harvestToCome(beforePlanting ? at : null, atStopPatches));
		logDecision("Compost for the visit: need here " + needHere + ", carried " + carried + ", stops ahead " + later
			+ "; take " + trade.take + ", store " + trade.store);
		return trade;
	}

	/** A later stop's compost by type, as the supplies were worked out; null when its compost bin will supply it. */
	private Map<Compost, Integer> compostAt(List<Patch> stop)
	{
		final CompostBin bin = binAt(stop);
		final BinState state = bin != null ? patchTracker.predictBin(bin) : null;
		if (state != null && state.hasCompost())
		{
			return null;
		}
		final Map<Compost, Integer> need = new EnumMap<>(Compost.class);
		for (Patch patch : stop)
		{
			final PatchCare care = plan.getSupplies().getCare().get(patch);
			if (care != null && care.getCompost() != null && care.getCompost() != Compost.NONE)
			{
				need.merge(care.getCompost(), 1, Integer::sum);
			}
		}
		return need;
	}

	/**
	 * The seed dibber or spade the stop's planting needs, when it isn't carried and the tool leprechaun has it: taken on the
	 * visit before planting rather than a trip of its own as planting starts.
	 */
	private Set<Integer> plantingToolsToTake()
	{
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> stored = holdings.in(Holdings.Source.LEPRECHAUN);
		final AccessSnapshot access = accessChecker.getSnapshot();
		final boolean barbarian = access.isKnown() && access.isMet(Requirement.unlock(Unlock.BARBARIAN_PLANTING));
		final Set<Integer> tools = new LinkedHashSet<>();
		for (Patch patch : atStopPatches)
		{
			final Progress p = progress(patch);
			final Crop crop = plantingFor(patch);
			if (p.done || p.skipped || crop == null || crop == Crop.SCARECROW || crop.isSeed() && barbarian)
			{
				continue;
			}
			// Seeds go in with a dibber, saplings with a spade
			final int tool = crop.isSeed() ? ItemID.DIBBER : ItemID.SPADE;
			if (inventory.getOrDefault(tool, 0) == 0 && stored.getOrDefault(tool, 0) > 0)
			{
				tools.add(tool);
			}
		}
		return tools;
	}

	/**
	 * The compost this stop's patches still need, e.g. 3 ultracompost, when there's too little in the inventory and enough
	 * stored with the tool leprechaun (P1: at each stop, for that stop); none otherwise, or with a filled bottomless bucket.
	 * Whether it makes the visit before planting needed; the visit itself may take more, for later stops.
	 */
	private Map<Compost, Integer> compostToTake()
	{
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> stored = holdings.in(Holdings.Source.LEPRECHAUN);
		final Map<Compost, Integer> need = compostStillNeeded(inventory);
		logDecision("Compost for the stop: need " + need + ", carried " + countsOf(need.keySet(), inventory) + ", stored "
			+ countsOf(need.keySet(), stored));
		final Map<Compost, Integer> take = new EnumMap<>(Compost.class);
		for (Map.Entry<Compost, Integer> e : need.entrySet())
		{
			final int missing = e.getValue() - inventory.getOrDefault(e.getKey().getItemId(), 0);
			if (missing > 0 && stored.getOrDefault(e.getKey().getItemId(), 0) >= missing)
			{
				take.put(e.getKey(), missing);
			}
		}
		return take;
	}

	private static String join(List<String> parts)
	{
		String text = parts.get(0);
		for (int i = 1; i < parts.size(); i++)
		{
			text += (i == parts.size() - 1 ? " and " : ", ") + parts.get(i);
		}
		return text;
	}

	/** Paid once the payment items leave the inventory after paying became the next step. */
	private void watchPayment(Patch patch, Progress p)
	{
		final Crop crop = plantingFor(patch);
		if (crop == null || !crop.hasPayment())
		{
			return;
		}
		final int held = holdingsTracker.getHoldings().in(Holdings.Source.INVENTORY)
			.getOrDefault(crop.getPaymentItemId(), 0);
		if (p.paymentBaseline < 0)
		{
			p.paymentBaseline = held;
		}
		else if (held < p.paymentBaseline)
		{
			p.paid = true;
		}
	}

	/**
	 * A stop's patches in the order the guide takes them, for the stop's checklist (elzerma, 2026-10-18: the sidebar
	 * listed them in its own order while the guide went nearest first): the finished ones in the order they were
	 * finished, then the one in hand, then the rest as the guide would go to them from here.
	 */
	List<Patch> order(List<Patch> here)
	{
		finishedOrder.removeIf(patch -> !isDone(patch));
		final List<Patch> rest = new ArrayList<>();
		for (Patch patch : here)
		{
			if (isDone(patch))
			{
				finishedOrder.add(patch);
			}
			else
			{
				rest.add(patch);
			}
		}
		final List<Patch> ordered = new ArrayList<>();
		for (Patch patch : finishedOrder)
		{
			if (here.contains(patch))
			{
				ordered.add(patch);
			}
		}
		ordered.addAll(inOrder(rest));
		return ordered;
	}

	/**
	 * The stop's patches in the order the guide takes them: nearest the player first, the one being worked on staying first
	 * so the step doesn't flip between patches while walking. While the compost bin is to be filled, the patches whose crops
	 * go in it come first, nearest the tool leprechaun first (elzerma, 2026-10-31, at Port Phasmatys: "we should harvest
	 * the allotment that is closest to the bin so then we have shorter trips", then "no it should be patch that is closest
	 * to leprechaun"). A protecting flower comes before all.
	 */
	private List<Patch> inOrder(List<Patch> here)
	{
		// The patch a bin or leprechaun step names isn't one being worked on: it's only the stop's first
		final Patch working = currentAction == null || atBinOrLeprechaun(currentAction) ? null : currentPatch;
		final List<Patch> ordered = navigator.nearestFirst(here, null);
		final CompostBin bin = binAt(here);
		final GameObject object = bin != null && binToFill(bin) ? scene.binObject(bin) : null;
		if (object != null)
		{
			// The stop's leprechaun, the one by the bin, or the bin while he isn't loaded
			final NPC nearest = scene.nearestLeprechaun(object.getWorldLocation());
			final WorldPoint from = leprechaunAt != null ? leprechaunAt
				: nearest != null ? nearest.getWorldLocation() : object.getWorldLocation();
			ordered.sort(Comparator.comparing((Patch patch) -> !cropsForBin(patch)).thenComparingInt(patch ->
			{
				final WorldPoint point = scene.locationOf(patch);
				return cropsForBin(patch) && point != null ? point.distanceTo2D(from) : 0;
			}));
		}
		if (working != null && ordered.remove(working))
		{
			ordered.add(0, working);
		}
		return protectingFlowersFirst(ordered);
	}

	/** Steps done at the compost bin or the tool leprechaun, or with the inventory, rather than at a patch. */
	private static boolean atBinOrLeprechaun(StepAdvisor.Action action)
	{
		switch (action)
		{
			case OPEN_BIN:
			case ADD_ASH:
			case EMPTY_BIN:
			case FILL_BIN:
			case CLOSE_BIN:
			case NOTE:
			case TAKE_COMPOST:
			case CLEAN:
				return true;
			default:
				return false;
		}
	}

	/** The stop's bin will take crops this stop: it has room, or is to be emptied first. */
	private boolean binToFill(CompostBin bin)
	{
		final BinState state = patchTracker.binState(bin);
		return state != null && (state.hasCompost() || fillCount(state) >= 0 && fillCount(state) < bin.capacity());
	}

	/** A patch here still has crops for the compost bin to pick. */
	private boolean binCropsToPick(List<Patch> here)
	{
		for (Patch patch : here)
		{
			final Progress p = progress(patch);
			final PatchPrediction live = patchTracker.predict(patch);
			if (!p.done && !p.skipped && live != null && live.getState() == PatchState.HARVESTABLE && cropsForBin(patch))
			{
				return true;
			}
		}
		return false;
	}

	/** The crop growing in the patch is one of those for the compost bin. */
	private boolean cropsForBin(Patch patch)
	{
		final PatchPrediction live = patchTracker.predict(patch);
		final Crop crop = live != null ? live.getCrop() : null;
		return crop != null && settings.getRunConfig().getBinCrops().contains(crop);
	}

	/**
	 * A flower kept for protecting its allotments goes first at its stop: it only protects them once fully grown, so
	 * the sooner it's in, the shorter the gap (docs/plans/crop-types.md).
	 */
	private List<Patch> protectingFlowersFirst(List<Patch> patches)
	{
		final List<Patch> ordered = new ArrayList<>();
		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		for (Patch patch : patches)
		{
			if (FlowerProtection.isProtecting(patch, config, access))
			{
				ordered.add(patch);
			}
		}
		for (Patch patch : patches)
		{
			if (!ordered.contains(patch))
			{
				ordered.add(patch);
			}
		}
		return ordered;
	}

	/** A protecting flower that's growing or grown and suits the crop this run plants next to it: leave it be. */
	private boolean keepsProtectingFlower(Patch patch, PatchPrediction live)
	{
		if (!FlowerProtection.isProtecting(patch, settings.getRunConfig(), accessChecker.getSnapshot()))
		{
			return false;
		}
		final Crop allotmentCrop = patch.getSetMates().stream()
			.map(mate -> plan.getSupplies().getPlantings().get(mate))
			.filter(crop -> crop != null && crop.getType() == PatchType.ALLOTMENT)
			.findFirst().orElse(null);
		return !FlowerProtection.isDue(live, allotmentCrop);
	}

	private StepAdvisor.PatchGoal goal(Patch patch, Progress p)
	{
		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final Crop crop = plantingFor(patch);
		final boolean faladorElite = patch == Patch.FALADOR_TREE && access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE));
		// As the supplies were worked out: paid, or protected with compost when the payment couldn't be had
		final PatchCare care = plan.getSupplies().getCare().get(patch);
		final boolean protectionNeeded = care != null ? care.isPaid() : patch.getType().isProtectable() && crop != null
			&& crop.hasPayment() && FlowerProtection.effective(patch, config, access, plan.getSupplies().getPlantings(),
			patchTracker::predict) == Protection.PAY_GARDENER && !faladorElite;
		final String plantName = crop != null ? runService.itemName(crop.getPlantItemId()) : "your seed";
		final String payment = protectionNeeded
			? crop.getPaymentQuantity() + " x " + runService.itemName(crop.getPaymentItemId()) + extras(crop)
			+ (config.isPayWithNotes() ? " (noted is fine)" : "")
			: null;
		// A guild patch run for contracts is replanted, as planned: a crop checked or picked can't count for one
		return new StepAdvisor.PatchGoal(patch, crop, plantName, compostFor(patch),
			protectionNeeded, payment, config.paysToClear(patch.getType()), p.composted, p.paid,
			p.sawUnplanted, config.keepsGrowing(patch.getType()) && !contractPatches().contains(patch));
	}

	/** The rest of a payment after its main item, e.g. ", 1 x Monkey bar, 1 x Ground tooth" for a spirit tree. */
	private String extras(Crop crop)
	{
		final StringBuilder s = new StringBuilder();
		crop.getExtraPayments().forEach((id, qty) -> s.append(", ").append(qty).append(" x ").append(runService.itemName(id)));
		return s.toString();
	}

	/** The compost a patch gets: as the supplies were worked out (falling back to what the player has), else its type's. */
	private Compost compostFor(Patch patch)
	{
		final PatchCare care = plan.getSupplies().getCare().get(patch);
		return care != null ? care.getCompost() : settings.getRunConfig().getCompost().get(patch.getType());
	}

	private Progress progress(Patch patch)
	{
		return progress.computeIfAbsent(patch, k -> new Progress());
	}

	/** What the current patch step points at: the patch, its gardener, and the items and spell to use. */
	Highlights highlights()
	{
		if (currentPatch == null || currentAction == null)
		{
			return null;
		}
		// The grimy herbs to clean outlined, every one, as each is clicked (P2)
		if (currentAction == StepAdvisor.Action.CLEAN)
		{
			return new Highlights(null, false, null, toClean, null, Collections.emptySet(), Collections.emptyList(), null,
				Collections.emptySet(), toClean);
		}
		// The compost bin outlined, with what's used on it: the ash, the buckets or the crops (P3)
		if (binInHand != null && currentAction != StepAdvisor.Action.TAKE_COMPOST)
		{
			return new Highlights(null, false, null, binItems, null, Collections.emptySet(), Collections.emptyList(), null,
				Collections.emptySet(), Collections.emptySet(), binInHand);
		}
		// Guildmaster Jane outlined for her step (contracts)
		if (currentAction == StepAdvisor.Action.JANE)
		{
			return new Highlights(null, false, FarmingContract.JANE, Collections.emptySet(), null);
		}
		// The tool leprechaun outlined, with the produce to use on him (P1), and in his window what to take and store
		if (currentAction == StepAdvisor.Action.NOTE || currentAction == StepAdvisor.Action.TAKE_COMPOST)
		{
			return new Highlights(null, false, TOOL_LEPRECHAUN, currentAction == StepAdvisor.Action.NOTE ? produce
				: Collections.emptySet(), null, Collections.emptySet(), Collections.emptyList(), null,
				Collections.emptySet()).withLeprechaun(visitTake, visitStore);
		}
		// Main item first: reminders name it. Only what gets clicked is outlined (elzerma, 2026-10-19: the rake and
		// spades were outlined, though raking and clearing click the patch); the rest is still checked as carried.
		final Set<Integer> items = new LinkedHashSet<>();
		final Set<Integer> needed = new LinkedHashSet<>();
		boolean gardener = false;
		Spell spell = null;
		final Crop crop = plantingFor(currentPatch);
		switch (currentAction)
		{
			case PLANT:
				if (crop != null)
				{
					items.add(crop.getPlantItemId());
				}
				break;
			case COMPOST:
				final Compost compost = compostFor(currentPatch);
				if (compost != null && compost != Compost.NONE)
				{
					items.add(compost.getItemId());
				}
				items.add(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
				break;
			case RAKE:
				needed.add(ItemID.RAKE);
				break;
			case PICK:
				// Allotments are picked with a spade ("You need a spade to do that.", elzerma's run of 2026-11-02 at the
				// guild with the spade left with the tool leprechaun)
				if (currentPatch.getType() == PatchType.ALLOTMENT)
				{
					needed.add(ItemID.SPADE);
				}
				break;
			case CLEAR_DEAD:
			case DIG_STUMP:
				needed.add(ItemID.SPADE);
				break;
			case CHOP:
				for (int axe : SupplyItems.AXES)
				{
					needed.add(axe);
				}
				break;
			case CURE:
				items.add(ItemID.PLANT_CURE);
				spell = settings.getRunConfig().isUseCurePlant() ? castableNow(Spell.CURE_PLANT) : null;
				break;
			case PRUNE:
				for (int secateurs : SupplyItems.SECATEURS)
				{
					needed.add(secateurs);
				}
				break;
			case PAY:
				// Paid through the gardener's Pay option
				gardener = true;
				if (crop != null && crop.hasPayment())
				{
					needed.add(crop.getPaymentItemId());
					needed.add(runService.notedId(crop.getPaymentItemId()));
					for (int extra : crop.getExtraPayments().keySet())
					{
						needed.add(extra);
						needed.add(runService.notedId(extra));
					}
				}
				break;
			case PAY_TO_CLEAR:
				gardener = true;
				needed.add(ItemID.COINS);
				break;
			default:
				break;
		}
		return new Highlights(currentPatch, gardener && currentPatch.hasGardener(), null, items, spell,
			Collections.emptySet(), Collections.emptyList(), null, needed).withPatchTile(patchTile());
	}

	/** The spell if it can be cast from the spellbook the player is on right now, otherwise null. */
	Spell castableNow(Spell spell)
	{
		final AccessSnapshot access = accessChecker.getSnapshot();
		// On Lunar, a spell from another book starts with Spellbook Swap
		if (spell != null && access.needsLunarSwap(spell))
		{
			return Spell.SPELLBOOK_SWAP;
		}
		return spell != null && access.canCast(spell) && access.isOnSpellbook(spell) ? spell : null;
	}

	/**
	 * The item the current patch step needs isn't carried: take it from the tool leprechaun if it's stored
	 * there, otherwise say it's missing.
	 */
	/**
	 * The current patch step's item is neither carried nor at the tool leprechaun, so only a bank can help (elzerma:
	 * the task then becomes a bank visit).
	 */
	boolean currentItemMissing(Highlights highlights)
	{
		final String reminder = missingItemReminder(highlights);
		// "Take ... from the tool leprechaun" can be done right here
		return reminder != null && !reminder.startsWith("Take ");
	}

	String missingItemReminder(Highlights highlights)
	{
		if (currentPatch == null || currentAction == null || highlights == null)
		{
			return null;
		}
		final Set<Integer> needed = highlights.getStepItemIds();
		if (needed.isEmpty())
		{
			return null;
		}
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> worn = holdings.in(Holdings.Source.WORN);
		final Map<Integer, Integer> leprechaun = holdings.in(Holdings.Source.LEPRECHAUN);
		final Map<Integer, Integer> vault = holdings.in(Holdings.Source.SEED_VAULT);
		Integer stored = null;
		Integer inVault = null;
		for (int id : needed)
		{
			if (inventory.getOrDefault(id, 0) > 0 || worn.getOrDefault(id, 0) > 0)
			{
				return null;
			}
			if (stored == null && leprechaun.getOrDefault(id, 0) > 0)
			{
				stored = id;
			}
			if (inVault == null && vault.getOrDefault(id, 0) > 0)
			{
				inVault = id;
			}
		}
		// At the Farming Guild, a seed or sapling only in its seed vault: one a farming contract taken there plants, or a
		// pre-plant to put back (elzerma, 2026-11-03), so it's taken there rather than banked for
		if (stored == null && inVault != null && atStop != null && atStop.getLocation() == Location.FARMING_GUILD)
		{
			final Crop crop = plantingFor(currentPatch);
			final int count = crop != null && crop.getPlantItemId() == inVault ? crop.getSeedsPerPatch() : 1;
			final String name = runService.itemName(inVault).toLowerCase();
			return "Take " + (count > 1 ? count + " " + name + "s" : ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ")
				+ name) + " from the seed vault";
		}
		if (stored != null)
		{
			final String name = runService.itemName(stored).toLowerCase();
			// "Take ultracompost", but "Take a bottomless compost bucket"
			final String article = currentAction == StepAdvisor.Action.COMPOST
				&& stored != ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED ? ""
				: "aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ";
			return "Take " + article + name + " from the tool leprechaun";
		}
		if (currentAction == StepAdvisor.Action.CHOP)
		{
			return "You aren't carrying an axe";
		}
		// The step's main item is listed first
		return "You aren't carrying: " + runService.itemName(needed.iterator().next());
	}

	/**
	 * Planting uses the seed or sapling itself, but only works with a seed dibber (herbs) or spade (saplings) in the
	 * inventory: say so if it isn't there. The tool isn't outlined, since it isn't what gets clicked (elzerma).
	 */
	String plantingToolReminder()
	{
		if (currentPatch == null || currentAction != StepAdvisor.Action.PLANT)
		{
			return null;
		}
		final Crop crop = plantingFor(currentPatch);
		if (crop == Crop.SCARECROW)
		{
			return null;
		}
		// Seeds go in with a dibber, saplings with a spade
		final boolean herb = crop != null ? crop.isSeed() : !currentPatch.getType().isHealthChecked();
		final AccessSnapshot access = accessChecker.getSnapshot();
		if (herb && access.isKnown() && access.isMet(Requirement.unlock(Unlock.BARBARIAN_PLANTING)))
		{
			return null;
		}
		final Map<Integer, Integer> inventory = holdingsTracker.getHoldings().in(Holdings.Source.INVENTORY);
		if (inventory.getOrDefault(herb ? ItemID.DIBBER : ItemID.SPADE, 0) > 0)
		{
			return null;
		}
		final boolean atLeprechaun = holdingsTracker.getHoldings().in(Holdings.Source.LEPRECHAUN)
			.getOrDefault(herb ? ItemID.DIBBER : ItemID.SPADE, 0) > 0;
		return (herb ? "Planting needs a seed dibber" : "Planting needs a spade") + " in your inventory"
			+ (atLeprechaun ? ": take it from the tool leprechaun" : "");
	}

	/**
	 * The inventory is full and something is about to be picked here. Only when full, not when it merely might
	 * not fit (elzerma): herbs and fruit can be noted by the tool leprechaun at every patch. Says what makes the
	 * room: the produce still unnoted, else the compost this stop won't use, to store with the leprechaun (elzerma,
	 * 2026-10-28: full of buckets from the compost bin, with no produce to note, it still said to note produce).
	 */
	String spaceReminder()
	{
		if (currentPatch == null || currentAction == null)
		{
			return null;
		}
		final int expected = expectedHarvest(currentPatch, currentAction, patchTracker.predict(currentPatch));
		if (expected == 0 || freeInventorySlots() > 0)
		{
			return null;
		}
		// Putting the crops in the compost bin, or noting with the tool leprechaun, is the step itself (nextStep): this is
		// for the rest, as with noting off
		final Set<Integer> unnoted = unnotedProduce();
		logDecision("Inventory full: unnoted produce " + (unnoted.isEmpty() ? "none" : produceNames(unnoted)));
		if (!unnoted.isEmpty())
		{
			return "Inventory full: note your " + produceNames(unnoted) + " on the tool leprechaun";
		}
		final Map<Integer, Integer> inventory = holdingsTracker.getHoldings().in(Holdings.Source.INVENTORY);
		final Map<Compost, Integer> spare = spareCompost(inventory, compostStillNeeded(inventory));
		if (!spare.isEmpty())
		{
			final List<String> parts = new ArrayList<>();
			spare.forEach((compost, count) -> parts.add(count + " " + compost.getDisplayName().toLowerCase()));
			return "Inventory full: store " + join(parts) + " with the tool leprechaun";
		}
		return "Inventory full: make room for the harvest";
	}

	/** Buckets of each compost this stop's patches still need: none with a filled bottomless bucket. */
	private Map<Compost, Integer> compostStillNeeded(Map<Integer, Integer> inventory)
	{
		final Map<Compost, Integer> need = new EnumMap<>(Compost.class);
		if (inventory.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 0) > 0)
		{
			return need;
		}
		for (Patch patch : atStopPatches)
		{
			final Progress p = progress(patch);
			final Compost compost = compostFor(patch);
			if (!p.done && !p.skipped && !p.composted && compost != null && compost != Compost.NONE)
			{
				need.merge(compost, 1, Integer::sum);
			}
		}
		return need;
	}

	/** Buckets of compost carried past what's still needed here, e.g. supercompost taken from a bin. */
	static Map<Compost, Integer> spareCompost(Map<Integer, Integer> inventory, Map<Compost, Integer> need)
	{
		final Map<Compost, Integer> spare = new EnumMap<>(Compost.class);
		for (Compost compost : Compost.values())
		{
			if (compost == Compost.NONE)
			{
				continue;
			}
			final int extra = inventory.getOrDefault(compost.getItemId(), 0) - need.getOrDefault(compost, 0);
			if (extra > 0)
			{
				spare.put(compost, extra);
			}
		}
		return spare;
	}

	/** Inventory slots the next harvest here will take (herbs, allotments and fruit), 0 if none. */
	static int expectedHarvest(Patch patch, StepAdvisor.Action action, PatchPrediction live)
	{
		if (!patch.getType().isHealthChecked())
		{
			// Herbs and hops: a handful each (flowers are a few); an allotment many more
			if (action != StepAdvisor.Action.PICK)
			{
				return 0;
			}
			return patch.getType() == PatchType.ALLOTMENT ? SupplyCalculator.ALLOTMENT_HARVEST
				: SupplyCalculator.HERBS_PER_PATCH;
		}
		if (patch.getType().regrows())
		{
			// Fruit, berries or spines: what's on it, or a full crop once its health is checked
			if (action == StepAdvisor.Action.PICK && live != null)
			{
				return live.getStage();
			}
			final int full = live != null && live.getCrop() != null ? live.getCrop().getHarvestStages() - 1
				: SupplyCalculator.FRUIT_PER_TREE;
			return action == StepAdvisor.Action.CHECK_HEALTH ? full : 0;
		}
		return 0;
	}

	/**
	 * The inventory with notes as notes: the holdings count a note as its item, so noting at the leprechaun changed
	 * nothing the Note step could see (elzerma, 2026-10-28: it stayed after noting at Civitas illa Fortis).
	 */
	private Map<Integer, Integer> inventoryAsHeld()
	{
		final Map<Integer, Integer> counts = new HashMap<>();
		final ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}

	private int freeInventorySlots()
	{
		final ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return INVENTORY_SLOTS;
		}
		int used = 0;
		for (Item item : inventory.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				used++;
			}
		}
		return INVENTORY_SLOTS - used;
	}
}
