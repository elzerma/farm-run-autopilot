package com.farmrunautopilot.run;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * A snapshot of the run session for the sidebar and overlay. Immutable; safe to pass between threads.
 */
@Value
public class RunView
{
	public enum State
	{
		IDLE,
		RUNNING
	}

	public enum StopStatus
	{
		DONE,
		CURRENT,
		PENDING
	}

	@Value
	public static class Stop
	{
		String location;
		String travel;
		StopStatus status;
		/** What to do there right now (current stop only), else null. */
		String instruction;
		/** The plan for each patch there, for hovering. */
		List<String> objectives;
	}

	public static final RunView IDLE_VIEW = new RunView(State.IDLE, 0, Collections.emptyList(), null, false, null,
		null, null);

	State state;
	/** When the run started (epoch millis), 0 when idle. */
	long startedAtMillis;
	List<Stop> stops;
	/** The next thing to do, shown under the player, or null. */
	String instruction;
	/** Everything needed is in the inventory, so the run can start. */
	boolean ready;
	/** e.g. "Run finished in 12:30", or null. */
	String lastRun;
	/** e.g. "Best for 6 herbs: 8:12, 8:40, 9:03", or null when there are none yet. */
	String bestTimes;
	/** e.g. "Drop 4 weeds and 2 empty plant pots", shown under the instruction, or null. */
	String reminder;
}
