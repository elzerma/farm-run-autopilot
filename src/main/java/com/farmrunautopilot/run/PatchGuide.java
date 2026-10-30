package com.farmrunautopilot.run;

import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.FlowerProtection;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.PatchCare;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

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

	private final Client client;
	private final RunService runService;
	private final PatchTracker patchTracker;
	private final HoldingsTracker holdingsTracker;
	private final AccessChecker accessChecker;
	private final SettingsStore settings;
	private final Navigator navigator;

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
	private final Set<Integer> picked = new LinkedHashSet<>();
	/** The inventory as last seen, to tell what picking added. */
	private Map<Integer, Integer> lastSeen = Collections.emptyMap();
	/** The stop's patches, for what's still to be composted there when the inventory fills. */
	private List<Patch> atStopPatches = Collections.emptyList();
	/** The produce the Note step outlines. */
	private Set<Integer> produce = Collections.emptySet();

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
		AccessChecker accessChecker, SettingsStore settings, Navigator navigator)
	{
		this.client = client;
		this.runService = runService;
		this.patchTracker = patchTracker;
		this.holdingsTracker = holdingsTracker;
		this.accessChecker = accessChecker;
		this.settings = settings;
		this.navigator = navigator;
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
	 */
	String nextStep(RouteStop stop, List<Patch> here)
	{
		final Map<Integer, Integer> held = inventoryAsHeld();
		if (stop != atStop)
		{
			atStop = stop;
			picked.clear();
		}
		else if (harvests(currentAction))
		{
			// The step as last shown: what its picking added is produce, to note (P1)
			final Set<Integer> gained = produceGained(lastSeen, held, runService::notable);
			if (!gained.isEmpty())
			{
				picked.addAll(gained);
				log.debug("Picked at {}: {}; produce so far {}", currentPatch, produceNames(gained), produceNames(picked));
			}
		}
		else if (currentAction == StepAdvisor.Action.PLANT && currentPatch != null)
		{
			// The seed leaving the inventory at the patch is planting, a tick or so before the patch reads as planted
			final Crop crop = plan.getSupplies().getPlantings().get(currentPatch);
			if (crop != null && plantUsed(crop.getPlantItemId(), lastSeen, held) && !navigator.farFrom(currentPatch))
			{
				markPlanted(currentPatch, runService.itemName(crop.getPlantItemId()) + " left the inventory");
			}
		}
		lastSeen = held;
		atStopPatches = here;
		// On arriving, the compost for the whole stop from the tool leprechaun, then each patch picked, planted and
		// composted in turn (elzerma, 2026-10-28: "Take N ultracompost" at the start, not everything picked first). It
		// stands aside once picking or composting has begun here.
		if (picked.isEmpty() && navigator.nearAny(here) && here.stream().noneMatch(patch -> progress(patch).composted))
		{
			final String compost = compostStep(here);
			if (compost != null)
			{
				return compost;
			}
		}
		for (Patch patch : protectingFlowersFirst(navigator.nearestFirst(here, currentPatch)))
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
			if (live.getState() == PatchState.EMPTY && justPlanted(p))
			{
				live = asPlanted(live);
			}
			// A flower planted this run still gets its compost, if any
			if (keepsProtectingFlower(patch, live) && (live.getState() != PatchState.GROWING || !p.sawUnplanted))
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
			return step(stop, patch, advice);
		}
		// Every patch done: the produce still unnoted is noted before leaving, while still here
		final String note = navigator.nearAny(here) ? noteStep(here.get(0)) : null;
		if (note != null)
		{
			return note;
		}
		clearCurrent();
		return null;
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
	 * "Note your produce on the tool leprechaun", while anything notable picked at this stop is still unnoted in the
	 * inventory and noting is on (P1, on by default); null otherwise.
	 */
	private String noteStep(Patch at)
	{
		if (!settings.getRunConfig().isNoteProduce())
		{
			return null;
		}
		produce = unnotedProduce();
		if (produce.isEmpty())
		{
			return null;
		}
		currentPatch = at;
		currentAction = StepAdvisor.Action.NOTE;
		return "Note your " + produceNames(produce) + " on the tool leprechaun (use them on him)";
	}

	/** The step log's last decision line, so each is logged once when it changes (debug: the developer client only). */
	private String loggedDecision;

	private void logDecision(String decision)
	{
		if (!decision.equals(loggedDecision))
		{
			loggedDecision = decision;
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

	/** The produce picked at this stop still unnoted in the inventory. */
	private Set<Integer> unnotedProduce()
	{
		final Map<Integer, Integer> held = inventoryAsHeld();
		final Set<Integer> gained = new LinkedHashSet<>();
		for (int id : picked)
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
	 * "Take 3 ultracompost from the tool leprechaun": the compost this stop's patches still need, when there's too little
	 * in the inventory and enough stored with him (P1: on arriving at each stop, for that stop); null otherwise, or with
	 * a filled bottomless bucket.
	 */
	private String compostStep(List<Patch> here)
	{
		final Holdings holdings = holdingsTracker.getHoldings();
		final Map<Integer, Integer> inventory = holdings.in(Holdings.Source.INVENTORY);
		final Map<Integer, Integer> stored = holdings.in(Holdings.Source.LEPRECHAUN);
		final Map<Compost, Integer> need = compostStillNeeded(inventory);
		logDecision("Compost for the stop: need " + need + ", carried " + countsOf(need.keySet(), inventory) + ", stored "
			+ countsOf(need.keySet(), stored));
		for (Map.Entry<Compost, Integer> e : need.entrySet())
		{
			final int id = e.getKey().getItemId();
			final int missing = e.getValue() - inventory.getOrDefault(id, 0);
			if (missing > 0 && stored.getOrDefault(id, 0) >= missing)
			{
				currentPatch = here.get(0);
				currentAction = StepAdvisor.Action.TAKE_COMPOST;
				return "Take " + missing + " " + e.getKey().getDisplayName().toLowerCase() + " from the tool leprechaun";
			}
		}
		return null;
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
		final Crop crop = plan.getSupplies().getPlantings().get(patch);
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
		ordered.addAll(protectingFlowersFirst(navigator.nearestFirst(rest, currentPatch)));
		return ordered;
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
		final Crop crop = plan.getSupplies().getPlantings().get(patch);
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
		return new StepAdvisor.PatchGoal(patch, crop, plantName, compostFor(patch),
			protectionNeeded, payment, config.paysToClear(patch.getType()), p.composted, p.paid,
			p.sawUnplanted, config.keepsGrowing(patch.getType()));
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
		// The tool leprechaun outlined, with the produce to use on him (P1)
		if (currentAction == StepAdvisor.Action.NOTE || currentAction == StepAdvisor.Action.TAKE_COMPOST)
		{
			return new Highlights(null, false, TOOL_LEPRECHAUN, currentAction == StepAdvisor.Action.NOTE ? produce
				: Collections.emptySet(), null, Collections.emptySet(), Collections.emptyList(), null,
				Collections.emptySet());
		}
		// Main item first: reminders name it. Only what gets clicked is outlined (elzerma, 2026-10-19: the rake and
		// spades were outlined, though raking and clearing click the patch); the rest is still checked as carried.
		final Set<Integer> items = new LinkedHashSet<>();
		final Set<Integer> needed = new LinkedHashSet<>();
		boolean gardener = false;
		Spell spell = null;
		final Crop crop = plan.getSupplies().getPlantings().get(currentPatch);
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
			Collections.emptySet(), Collections.emptyList(), null, needed);
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
		Integer stored = null;
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
		final Crop crop = plan.getSupplies().getPlantings().get(currentPatch);
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

	/** Inventory slots the next harvest here will take (herbs and fruit), 0 if none. */
	static int expectedHarvest(Patch patch, StepAdvisor.Action action, PatchPrediction live)
	{
		if (!patch.getType().isHealthChecked())
		{
			// Herbs, allotments and hops: a handful each (flowers are a few)
			return action == StepAdvisor.Action.PICK ? SupplyCalculator.HERBS_PER_PATCH : 0;
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
