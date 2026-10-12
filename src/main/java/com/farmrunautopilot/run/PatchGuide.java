package com.farmrunautopilot.run;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.SupplyCalculator;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchTracker;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
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
@Singleton
class PatchGuide
{
	/** Compost messages, as in RuneLite core CompostTracker (BSD-2). */
	private static final Pattern COMPOST_USED = Pattern.compile(
		"You treat the .+ with (ultra|super|)compost\\.|^The .+ has been treated with (ultra|super|)compost.*");
	private static final Pattern ALREADY_COMPOSTED = Pattern.compile(
		"This .+ has already been (treated|fertilised) with (ultra|super|)compost.*");
	private static final int INVENTORY_SLOTS = 28;

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

	/** Skip the patch being worked on. */
	void skipCurrent()
	{
		if (currentPatch != null)
		{
			progress(currentPatch).skipped = true;
		}
	}

	/** Finished or skipped this run, so far as the guide has got (later patches aren't looked at yet). */
	boolean isDone(Patch patch)
	{
		final Progress p = progress.get(patch);
		return p != null && p.done;
	}

	/** Skip every patch at a stop. */
	void skipAll(List<Patch> here)
	{
		for (Patch patch : here)
		{
			progress(patch).skipped = true;
		}
		clearCurrent();
	}

	/**
	 * A compost message: credit the patch it names ("You treat the herb patch...") among this stop's patches, so
	 * at a stop with several patches it lands on the right one; otherwise the patch being worked on.
	 */
	void onChatMessage(String message, List<Patch> here)
	{
		if (!COMPOST_USED.matcher(message).matches() && !ALREADY_COMPOSTED.matcher(message).matches())
		{
			return;
		}
		final Patch named = patchNamed(message, here);
		final Patch patch = named != null ? named : currentPatch;
		if (patch != null)
		{
			progress(patch).composted = true;
		}
	}

	/** The one patch of the type a message names ("fruit tree patch", "herb patch", "tree patch"), or null. */
	static Patch patchNamed(String message, List<Patch> here)
	{
		final String text = message.toLowerCase();
		final PatchType type = text.contains("fruit tree") ? PatchType.FRUIT_TREE
			: text.contains("herb") ? PatchType.HERB
			: text.contains("tree") ? PatchType.TREE : null;
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

	/** The first unfinished step at this stop, or null when every patch here is done. */
	String nextStep(List<Patch> here)
	{
		for (Patch patch : navigator.nearestFirst(here, currentPatch))
		{
			final Progress p = progress(patch);
			if (p.skipped)
			{
				p.done = true;
				continue;
			}
			final PatchPrediction live = patchTracker.predict(patch);
			// Only trust a reading taken since arriving: an estimate from the last visit can say a tree is
			// still growing when it's ready, which would skip the patch
			if (live == null || live.getObservedAt() < navigator.getArrivedAtSeconds())
			{
				currentPatch = patch;
				currentAction = StepAdvisor.Action.INSPECT;
				return "Walk to the " + patch.getDisplayName() + " patch";
			}
			if (live.getState() != PatchState.GROWING && patchTracker.getPatchesInRange().contains(patch))
			{
				p.sawUnplanted = true;
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
			if (advice.getAction() != StepAdvisor.Action.DONE)
			{
				currentPatch = patch;
				currentAction = advice.getAction();
				// The area loads well before the patch is reached, and some places have several patches
				if (advice.getAction() != StepAdvisor.Action.INSPECT && navigator.farFrom(patch))
				{
					final String text = advice.getText();
					return "Run to the " + patch.getDisplayName() + " patch, then "
						+ Character.toLowerCase(text.charAt(0)) + text.substring(1);
				}
				return advice.getText();
			}
			p.done = true;
		}
		clearCurrent();
		return null;
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

	private StepAdvisor.PatchGoal goal(Patch patch, Progress p)
	{
		final RunConfig config = settings.getRunConfig();
		final AccessSnapshot access = accessChecker.getSnapshot();
		final Crop crop = plan.getSupplies().getPlantings().get(patch);
		final boolean faladorElite = patch == Patch.FALADOR_TREE && access.isKnown()
			&& access.isMet(Requirement.diary(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE));
		final boolean protectionNeeded = patch.getType().isProtectable() && crop != null && crop.hasPayment()
			&& config.protectionFor(patch) == Protection.PAY_GARDENER && !faladorElite;
		final String plantName = crop != null ? runService.itemName(crop.getPlantItemId()) : "your seed";
		final String payment = protectionNeeded
			? crop.getPaymentQuantity() + " x " + runService.itemName(crop.getPaymentItemId())
			+ (config.isPayWithNotes() ? " (noted is fine)" : "")
			: null;
		return new StepAdvisor.PatchGoal(patch, crop, plantName, config.getCompost().get(patch.getType()),
			protectionNeeded, payment, config.getPayToClear().contains(patch.getType()), p.composted, p.paid,
			p.sawUnplanted);
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
		// Main item first: reminders name it
		final Set<Integer> items = new LinkedHashSet<>();
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
				final Compost compost = settings.getRunConfig().getCompost().get(currentPatch.getType());
				if (compost != null && compost != Compost.NONE)
				{
					items.add(compost.getItemId());
				}
				items.add(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
				break;
			case RAKE:
				items.add(ItemID.RAKE);
				break;
			case CLEAR_DEAD:
			case DIG_STUMP:
				items.add(ItemID.SPADE);
				break;
			case CHOP:
				for (int axe : SupplyItems.AXES)
				{
					items.add(axe);
				}
				break;
			case CURE:
				items.add(ItemID.PLANT_CURE);
				spell = settings.getRunConfig().isUseCurePlant() ? castableNow(Spell.CURE_PLANT) : null;
				break;
			case PRUNE:
				for (int secateurs : SupplyItems.SECATEURS)
				{
					items.add(secateurs);
				}
				break;
			case PAY:
				gardener = true;
				if (crop != null && crop.hasPayment())
				{
					items.add(crop.getPaymentItemId());
					items.add(runService.notedId(crop.getPaymentItemId()));
				}
				break;
			case PAY_TO_CLEAR:
				gardener = true;
				items.add(ItemID.COINS);
				break;
			default:
				break;
		}
		return new Highlights(currentPatch, gardener && currentPatch.hasGardener(), null, items, spell);
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
	String missingItemReminder(Highlights highlights)
	{
		if (currentPatch == null || currentAction == null || highlights == null)
		{
			return null;
		}
		final Set<Integer> needed = highlights.getItemIds();
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
	 * inventory: say so if it isn't there. The tool isn't outlined, since it isn't what gets clicked (Sean).
	 */
	String plantingToolReminder()
	{
		if (currentPatch == null || currentAction != StepAdvisor.Action.PLANT)
		{
			return null;
		}
		final boolean herb = currentPatch.getType() == PatchType.HERB;
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
	 * Not enough free inventory space for what's about to be picked here (SPEC 11.5). Herbs and fruit can be
	 * noted by the tool leprechaun at every patch.
	 */
	String spaceReminder()
	{
		if (currentPatch == null || currentAction == null)
		{
			return null;
		}
		final int expected = expectedHarvest(currentPatch, currentAction, patchTracker.predict(currentPatch));
		if (expected == 0)
		{
			return null;
		}
		final int free = freeInventorySlots();
		if (free >= expected)
		{
			return null;
		}
		return "Only " + free + " free slot" + (free == 1 ? "" : "s") + ": note produce on the tool leprechaun";
	}

	/** Inventory slots the next harvest here will take (herbs and fruit), 0 if none. */
	static int expectedHarvest(Patch patch, StepAdvisor.Action action, PatchPrediction live)
	{
		if (patch.getType() == PatchType.HERB)
		{
			return action == StepAdvisor.Action.PICK ? SupplyCalculator.HERBS_PER_PATCH : 0;
		}
		if (patch.getType() == PatchType.FRUIT_TREE)
		{
			if (action == StepAdvisor.Action.PICK && live != null)
			{
				return live.getStage();
			}
			return action == StepAdvisor.Action.CHECK_HEALTH ? SupplyCalculator.FRUIT_PER_TREE : 0;
		}
		return 0;
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
