package com.farmrunautopilot.atlas;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import shortestpath.Destination;
import shortestpath.DestinationRequirements;
import shortestpath.transport.TransportLoader;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.SplitFlagMap;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
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

	/**
	 * A closed door or gate costs the pathfinder its real time, so a way round wins when it's as quick. In Shortest
	 * Path's units a walked tile is one; the plugin runs, two tiles a tick.
	 */
	@Override
	public int getAdditionalTransportCost(Transport transport)
	{
		return super.getAdditionalTransportCost(transport) + (Way.isDoor(transport) ? 2 * Way.DOOR_EXTRA_TICKS : 0);
	}

	@Override
	public boolean varbitChecks(Transport transport, long evaluationTimeMinutes)
	{
		return locked(transport.getVarbits(), evaluationTimeMinutes);
	}

	@Override
	public boolean varPlayerChecks(Transport transport, long evaluationTimeMinutes)
	{
		return locked(transport.getVarPlayers(), evaluationTimeMinutes);
	}

	/**
	 * True when the transport needs a var this account has locked, or one it gives a value for that doesn't pass.
	 * Every other var check passes.
	 */
	private boolean locked(Set<VarRequirement> requirements, long evaluationTimeMinutes)
	{
		for (VarRequirement requirement : requirements)
		{
			final Integer value = account.varValues.get(requirement.getId());
			if (account.lockAllVars || account.lockedVars.contains(requirement.getId())
				|| value != null && !requirement.checkValue(value, evaluationTimeMinutes))
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

		/** Shortest Path's collision map, transports, destinations and bank gates from its resources. */
		static World load()
		{
			final Map<Integer, Set<Transport>> transports = TransportLoader.loadAllFromResources();
			addExtras(transports);
			return new World(SplitFlagMap.fromResources(), transports, Destination.loadAllFromResources(),
				Destination.loadBankRequirementsFromResources());
		}

		/**
		 * Ways Shortest Path has none for, from {@code extra-boats.tsv} next to this build (the Fossil Island dive to the
		 * underwater seaweed patches, sprint 1b), read as its boats are. Shortest Path's own files stay unchanged.
		 */
		private static void addExtras(Map<Integer, Set<Transport>> transports)
		{
			final Path extras = Paths.get("extra-boats.tsv");
			if (!Files.exists(extras))
			{
				throw new IllegalStateException("extra-boats.tsv not found: run from tools/atlas");
			}
			try
			{
				TransportLoader.addTransportsFromContents(transports,
					new String(Files.readAllBytes(extras), StandardCharsets.UTF_8), TransportType.BOAT, 0);
			}
			catch (IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
	}
}
