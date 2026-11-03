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
 * screen). As elzerma's dev check saw them (2026-11-02), the same away from the guild as at it: TYPE names the crop (0 for
 * none) and keeps it until the next contract; COMPLETE goes to 1 the tick the crop is checked or fully picked, to 2 once
 * Jane has given the seed pack, and back to 0 with the next contract; LEVEL is its tier; COUNT goes up as each one is
 * completed. Client thread; the contract can be read from any thread.
 */
@Slf4j
@Singleton
public class ContractTracker
{
	/** A contract as the varbits have it. */
	@Value
	public static class Contract
	{
		public static final Contract NONE = new Contract(0, null, 0, 0, 0);

		/** FARMGUILD_CONTRACT_TYPE: 0 for none. */
		int value;
		/** The crop it asks for, or null for none or a value the table doesn't know. */
		Crop crop;
		/** FARMGUILD_CONTRACT_COMPLETE: 0 growing, 1 grown and waiting to be handed in, 2 handed in, none taken since. */
		int complete;
		/** FARMGUILD_CONTRACT_LEVEL: its tier (FarmingContract.Tier.fromVarbit). */
		int level;
		/** FARMGUILD_CONTRACT_COUNT: how many have been completed. */
		int count;

		/** A contract to grow: one held and not yet grown. */
		public boolean held()
		{
			return value > 0 && complete == 0;
		}

		/** Grown, and waiting to be handed in to Guildmaster Jane. */
		public boolean grown()
		{
			return value > 0 && complete == 1;
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
			client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COMPLETE),
			client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_LEVEL), client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COUNT));
		if (now.equals(contract))
		{
			return false;
		}
		log.debug("Farming contract: {} (value {}), complete {}, level {}, count {} at tick {}",
			now.getCrop() != null ? now.getCrop().getDisplayName() : now.getValue() == 0 ? "none" : "unknown",
			now.getValue(), now.getComplete(), now.getLevel(), now.getCount(), client.getTickCount());
		contract = now;
		return true;
	}

	/** Logged out or another account: nothing known until the next read. */
	public void reset()
	{
		contract = Contract.NONE;
	}
}
