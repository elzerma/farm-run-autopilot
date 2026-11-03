package com.farmrunautopilot.tracking;

import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.FarmingContract;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * The player's Farming Guild contract (docs/plans/farming-contracts.md), from the game's contract varbits, read each
 * tick as RuneLite's Time Tracking does (FarmingContractManager, BSD-2: no region check, nothing read on the welcome
 * screen). TYPE names the crop (0 for none) and COMPLETE above 0 means it's grown and waiting to be handed in, as the game's
 * own seed vault scripts read them. LEVEL and COUNT are logged to learn what they hold (UNVERIFIED). Client thread; the
 * contract can be read from any thread.
 */
@Slf4j
@Singleton
public class ContractTracker
{
	/** A contract as the varbits have it. */
	@Value
	public static class Contract
	{
		public static final Contract NONE = new Contract(0, null, false, 0, 0);

		/** FARMGUILD_CONTRACT_TYPE: 0 for none. */
		int value;
		/** The crop it asks for, or null for none or a value the table doesn't know. */
		Crop crop;
		/** Grown (checked or picked) and waiting to be handed in to Guildmaster Jane. */
		boolean complete;
		/** FARMGUILD_CONTRACT_LEVEL, UNVERIFIED: probably the tier (FarmingContract.Tier.fromVarbit). */
		int level;
		/** FARMGUILD_CONTRACT_COUNT, UNVERIFIED: probably how many have been done. */
		int count;

		/** A contract to grow: one held and not yet grown. */
		public boolean held()
		{
			return value > 0 && !complete;
		}
	}

	private final Client client;
	private volatile Contract contract = Contract.NONE;

	@Inject
	ContractTracker(Client client)
	{
		this.client = client;
	}

	public Contract getContract()
	{
		return contract;
	}

	/** Read the varbits. Call each game tick once past the welcome screen. @return whether the contract changed */
	public boolean update()
	{
		final int type = client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE);
		final Contract now = new Contract(type, FarmingContract.crop(type),
			client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COMPLETE) > 0,
			client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_LEVEL), client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COUNT));
		if (now.equals(contract))
		{
			return false;
		}
		log.debug("Farming contract: {} (value {}), complete {}, level {}, count {} at tick {}",
			now.getCrop() != null ? now.getCrop().getDisplayName() : now.getValue() == 0 ? "none" : "unknown",
			now.getValue(), now.isComplete() ? 1 : 0, now.getLevel(), now.getCount(), client.getTickCount());
		contract = now;
		return true;
	}

	/** Logged out or another account: nothing known until the next read. */
	public void reset()
	{
		contract = Contract.NONE;
	}
}
