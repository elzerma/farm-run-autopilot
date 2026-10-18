package com.farmrunautopilot.atlas;

import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.DestinationRequirements;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.transport.Transport;
import shortestpath.transport.parser.VarRequirement;

/**
 * Shortest Path's pathfinder settings for a pretend account. Quests and vars are answered here rather than through the
 * client (as Shortest Path's own TestPathfinderConfig does), since they're asked in its inner loop.
 */
final class BubbleConfig extends PathfinderConfig
{
	private final Account account;

	BubbleConfig(Account account, boolean teleports, World world)
	{
		super(account.client(Thread.currentThread()), account.config(teleports), world.map, world.transports,
			world.destinations, world.destinations, world.bankRequirements);
		this.account = account;
	}

	@Override
	public QuestState getQuestState(Quest quest)
	{
		return account.questsNotDone.contains(quest) ? QuestState.NOT_STARTED : QuestState.FINISHED;
	}

	@Override
	public boolean varbitChecks(Transport transport, long evaluationTimeMinutes)
	{
		return locked(transport.getVarbits());
	}

	@Override
	public boolean varPlayerChecks(Transport transport, long evaluationTimeMinutes)
	{
		return locked(transport.getVarPlayers());
	}

	/** True when the transport needs a var this account has locked; every other var check passes. */
	private boolean locked(Set<VarRequirement> requirements)
	{
		for (VarRequirement requirement : requirements)
		{
			if (account.lockedVars.contains(requirement.getId()))
			{
				return true;
			}
		}
		return false;
	}

	/** Shortest Path's map and data, loaded once and shared by every question (read only). */
	static final class World
	{
		final SplitFlagMap map;
		final Map<Integer, Set<Transport>> transports;
		final Map<String, Set<Integer>> destinations;
		final Map<Integer, DestinationRequirements> bankRequirements;

		World(SplitFlagMap map, Map<Integer, Set<Transport>> transports, Map<String, Set<Integer>> destinations,
			Map<Integer, DestinationRequirements> bankRequirements)
		{
			this.map = map;
			this.transports = transports;
			this.destinations = destinations;
			this.bankRequirements = bankRequirements;
		}
	}
}
