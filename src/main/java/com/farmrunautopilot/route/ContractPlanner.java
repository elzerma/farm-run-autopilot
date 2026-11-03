package com.farmrunautopilot.route;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.FarmingContract;
import com.farmrunautopilot.data.FarmingContract.Tier;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.tracking.ContractTracker.Contract;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import lombok.Value;

/**
 * What a run does at the Farming Guild with contracts on (docs/plans/farming-contracts.md, option (c); elzerma,
 * 2026-11-02): every contract patch holds a pre-plant from the wiki's table and is left alone until a contract asks for
 * its crop, while a crop off the table is picked once grown and the pre-plant planted in its place; the contract's crop is
 * checked or picked once grown, and the patch replanted; a contract whose crop isn't
 * growing gets it planted, in a patch cleared for it if it's quick (or Easy, which can't be swapped); and the guild stop
 * ends at Guildmaster Jane to hand one in, take the next (the highest tier the level allows) or ask for an easier one.
 * Pure: the planner and the guide both ask it, from the patches as they're read.
 */
public final class ContractPlanner
{
	/** What happens to a guild contract patch on this run. */
	public enum Role
	{
		/** A crop no contract asks for yet (a pre-plant waiting, or the contract's still growing): not touched. */
		LEAVE,
		/**
		 * The contract's crop, grown: check its health or pick it, then clear and replant. Or a diseased crop to cure, or a
		 * grown crop off the pre-planting plan, picked like any harvest and the patch planted to plan.
		 */
		TAKE,
		/** Nothing waiting there (empty, weeds, dead, a stump or a tree already checked): clear if need be, and plant. */
		PLANT,
		/** Another crop where an Easy or quick contract's has to go: dig it up and plant the contract's. */
		CLEAR_FOR_CONTRACT
	}

	/** Guildmaster Jane's part at the end of the guild stop. */
	public enum Jane
	{
		/** Nothing to see her about. */
		NONE,
		/** The contract's grown: hand it in, then take another. */
		HAND_IN,
		/** No contract: ask for one. */
		NEW,
		/** Its crop is slow and every patch for it holds another: ask for an easier one. */
		EASIER
	}

	/** The run's contract work at the guild. */
	@Value
	public static class Plan
	{
		public static final Plan OFF = new Plan(false, Contract.NONE, Collections.emptyMap(), Jane.NONE, null, null);

		/** Contracts are on and the guild's patches are run for them. */
		boolean on;
		Contract contract;
		/** Each usable guild contract patch's role. */
		Map<Patch, Role> roles;
		Jane jane;
		/** The tier to ask Jane for: the highest the Farming level allows (elzerma, 2026-11-02). */
		Tier ask;
		/** One line for the Run tab, or null. */
		String line;

		public Role role(Patch patch)
		{
			return roles.get(patch);
		}
	}

	private ContractPlanner()
	{
	}

	/**
	 * The guild's contract work: each usable patch's role, Jane's part and the Run tab's line.
	 *
	 * @param usable the guild contract patches the player can use and has ticked
	 * @param stock  how many of a crop's seed or sapling the player has, wherever it's kept
	 */
	public static Plan plan(Contract contract, int farming, List<Patch> usable,
		Function<Patch, PatchPrediction> predictions, ToIntFunction<Crop> stock)
	{
		final Map<Patch, Role> roles = new EnumMap<>(Patch.class);
		for (Patch patch : usable)
		{
			roles.put(patch, role(patch, contract, farming, usable, predictions, stock));
		}
		final Jane jane = jane(contract, farming, usable, predictions);
		final Tier ask = Tier.highest(farming);
		return new Plan(true, contract, Collections.unmodifiableMap(roles), jane, ask,
			line(contract, farming, jane, ask, roles, usable, predictions));
	}

	/** A guild contract patch's role, from what it holds now. */
	public static Role role(Patch patch, Contract contract, int farming, List<Patch> usable,
		Function<Patch, PatchPrediction> predictions, ToIntFunction<Crop> stock)
	{
		final PatchPrediction live = predictions.apply(patch);
		// Never seen: looked at, and planted if there's nothing there
		if (live == null || live.getCrop() == null || spent(patch, live))
		{
			return Role.PLANT;
		}
		if (live.getState() == PatchState.DISEASED)
		{
			return Role.TAKE;
		}
		final Crop wanted = contract.held() ? contract.getCrop() : null;
		if (live.getCrop() == wanted)
		{
			return ready(patch, live) ? Role.TAKE : Role.LEAVE;
		}
		final Patch forContract = wanted != null ? contractPatch(contract, farming, usable, predictions) : null;
		if (patch == forContract && blocked(contract, farming, usable, predictions) && !askEasier(contract))
		{
			return Role.CLEAR_FOR_CONTRACT;
		}
		// A crop off the pre-planting plan is picked once grown, and the patch planted to plan (elzerma, 2026-11-02: "We
		// just want to pick anything that isn't in alignment with the pre-planting plan"): with the contract's crop when
		// it goes there, else once there's a pre-plant to put in (elzerma's run that day had no white lily seed for the
		// flower patch, and the step to pick its nasturtiums was ticked off)
		return clearing(patch, live, farming) && (patch == forContract || hasPrePlant(patch, farming, stock))
			? Role.TAKE : Role.LEAVE;
	}

	/** The player has a seed or sapling of one of the patch's pre-plants, to plant in place of a crop off the plan. */
	static boolean hasPrePlant(Patch patch, int farming, ToIntFunction<Crop> stock)
	{
		for (Crop crop : FarmingContract.prePlants(patch, farming))
		{
			if (stock.applyAsInt(crop) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Jane's part: hand in a grown contract, ask for one when there's none (or the last was handed in with none taken
	 * since, as elzerma's dev check saw it), or for an easier one when it's blocked.
	 */
	public static Jane jane(Contract contract, int farming, List<Patch> usable,
		Function<Patch, PatchPrediction> predictions)
	{
		if (contract.grown())
		{
			return Jane.HAND_IN;
		}
		if (!contract.held())
		{
			return Jane.NEW;
		}
		return contract.getCrop() != null && blocked(contract, farming, usable, predictions) && askEasier(contract)
			? Jane.EASIER : Jane.NONE;
	}

	/**
	 * The crop a guild contract patch gets planted with: the contract's in the patch it goes to, else the patch's
	 * pre-plant, the first of the wiki's choices the player has (or its first), else their own choice for the type.
	 */
	public static Crop crop(Patch patch, Contract contract, int farming, List<Patch> usable,
		Function<Patch, PatchPrediction> predictions, ToIntFunction<Crop> stock, Function<PatchType, Crop> own)
	{
		if (contract.held() && contract.getCrop() != null && patch == contractPatch(contract, farming, usable, predictions)
			&& !growing(contract.getCrop(), usable, predictions))
		{
			return contract.getCrop();
		}
		final List<Crop> choices = FarmingContract.prePlants(patch, farming);
		for (Crop choice : choices)
		{
			if (stock.applyAsInt(choice) > 0)
			{
				return choice;
			}
		}
		return !choices.isEmpty() ? choices.get(0) : own.apply(patch.getType());
	}

	/**
	 * Grown: its health to check, or (crops without a health check) ready to pick. For the contract's crop, grown enough to
	 * count (wiki: Farming contracts).
	 */
	public static boolean ready(Patch patch, PatchPrediction live)
	{
		return patch.getType().isHealthChecked() ? live.getState() == PatchState.CHECK_HEALTH
			: live.getState() == PatchState.HARVESTABLE;
	}

	/**
	 * Nothing there can count for a contract any more: dead, a stump, or a tree or bush whose health has been checked
	 * already (a contract counts only a crop not yet checked).
	 */
	static boolean spent(Patch patch, PatchPrediction live)
	{
		final PatchState state = live.getState();
		return state == PatchState.EMPTY || state == PatchState.WEEDS || state == PatchState.DEAD
			|| state == PatchState.STUMP || patch.getType().isHealthChecked() && state == PatchState.HARVESTABLE;
	}

	/**
	 * Off the pre-planting plan: a crop that isn't one of the patch's pre-plants at this Farming level. Never where the
	 * table has none, as the player's own choice for the type goes there.
	 */
	public static boolean offPlan(Patch patch, Crop crop, int farming)
	{
		final List<Crop> plan = FarmingContract.prePlants(patch, farming);
		return crop != null && !plan.isEmpty() && !plan.contains(crop);
	}

	/** Emptied this run: a grown crop off the pre-planting plan, picked or checked like any harvest. */
	static boolean clearing(Patch patch, PatchPrediction live, int farming)
	{
		return offPlan(patch, live.getCrop(), farming) && ready(patch, live);
	}

	/**
	 * The patch the contract's crop grows in: one holding it, else an empty one (or one emptied this run), else (blocked)
	 * one holding a crop off the plan, else the first of its type.
	 */
	public static Patch contractPatch(Contract contract, int farming, List<Patch> usable,
		Function<Patch, PatchPrediction> predictions)
	{
		Patch first = null;
		Patch free = null;
		Patch spare = null;
		for (Patch patch : usable)
		{
			if (!FarmingContract.patchTakes(patch, contract.getCrop()))
			{
				continue;
			}
			final PatchPrediction live = predictions.apply(patch);
			if (live != null && live.getCrop() == contract.getCrop() && !spent(patch, live))
			{
				return patch;
			}
			if (free == null && (live == null || live.getCrop() == null || spent(patch, live)
				|| clearing(patch, live, farming)))
			{
				free = patch;
			}
			if (spare == null && live != null && offPlan(patch, live.getCrop(), farming))
			{
				spare = patch;
			}
			if (first == null)
			{
				first = patch;
			}
		}
		return free != null ? free : spare != null ? spare : first;
	}

	/** The contract's crop is growing or grown (not yet counted) in one of the guild's patches for it. */
	static boolean growing(Crop crop, List<Patch> usable, Function<Patch, PatchPrediction> predictions)
	{
		for (Patch patch : usable)
		{
			final PatchPrediction live = predictions.apply(patch);
			if (live != null && live.getCrop() == crop && !spent(patch, live))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Every guild patch for the contract's crop holds another crop, waiting for its own contract (or still growing, off the
	 * plan): none is empty or emptied this run.
	 */
	static boolean blocked(Contract contract, int farming, List<Patch> usable,
		Function<Patch, PatchPrediction> predictions)
	{
		if (!contract.held() || contract.getCrop() == null || growing(contract.getCrop(), usable, predictions))
		{
			return false;
		}
		boolean any = false;
		for (Patch patch : usable)
		{
			if (!FarmingContract.patchTakes(patch, contract.getCrop()))
			{
				continue;
			}
			any = true;
			final PatchPrediction live = predictions.apply(patch);
			if (live == null || live.getCrop() == null || spent(patch, live) || clearing(patch, live, farming))
			{
				return false;
			}
		}
		return any;
	}

	/**
	 * A blocked contract is swapped for an easier one rather than a pre-plant cleared for it: its crop is slower than a
	 * bush and it isn't Easy, which can't be swapped (wiki: Farming contracts; elzerma, 2026-11-02).
	 */
	static boolean askEasier(Contract contract)
	{
		return !FarmingContract.quick(contract.getCrop()) && tier(contract) != Tier.EASY;
	}

	/** The contract's tier: from its LEVEL varbit, else Easy for a crop only Easy contracts ask for. */
	static Tier tier(Contract contract)
	{
		final Tier read = Tier.fromVarbit(contract.getLevel());
		if (read != null)
		{
			return read;
		}
		return FarmingContract.tiers(contract.getCrop()).equals(Collections.singleton(Tier.EASY)) ? Tier.EASY : null;
	}

	private static String line(Contract contract, int farming, Jane jane, Tier ask, Map<Patch, Role> roles,
		List<Patch> usable, Function<Patch, PatchPrediction> predictions)
	{
		final String asked = ask != null ? "a " + ask.getDisplayName() + " one" : "one";
		final Crop crop = contract.getCrop();
		final String name = crop != null ? crop.getDisplayName() : "an unknown crop";
		switch (jane)
		{
			case HAND_IN:
				return "Contract: " + name + " is grown. Hand it in to Guildmaster Jane, and take another";
			case NEW:
				return "No farming contract: ask Guildmaster Jane for " + asked;
			case EASIER:
				return "Contract: " + name + " (" + clock(crop.getGrowthMinutes()) + "). Its patch holds another crop, so ask"
					+ " Guildmaster Jane for an easier one";
			default:
				break;
		}
		if (crop == null)
		{
			return null;
		}
		final Patch where = contractPatch(contract, farming, usable, predictions);
		final Role role = where != null ? roles.get(where) : null;
		if (role == Role.TAKE)
		{
			return "Contract: " + name + " is grown: " + (where.getType().isHealthChecked() ? "checked" : "picked")
				+ " at the guild this run";
		}
		if (role == Role.PLANT)
		{
			return "Contract: " + name + ", planted at the guild this run";
		}
		if (role == Role.CLEAR_FOR_CONTRACT)
		{
			return "Contract: " + name + ", planted at the guild this run in place of the crop there";
		}
		return "Contract: " + name + ", growing at the guild";
	}

	/** "6 h 40 min", "1 h 20 min", "40 min". */
	public static String clock(int minutes)
	{
		final int hours = minutes / 60;
		final int rest = minutes % 60;
		if (hours == 0)
		{
			return rest + " min";
		}
		return hours + " h" + (rest > 0 ? " " + rest + " min" : "");
	}
}
