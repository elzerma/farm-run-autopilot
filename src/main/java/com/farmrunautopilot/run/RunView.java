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
	/**
	 * Off until Build run is pressed; armed once everything is gathered; running from the first teleport or
	 * patch click.
	 */
	public enum State
	{
		OFF,
		BUILDING,
		ARMED,
		RUNNING
	}

	public enum StopStatus
	{
		DONE,
		CURRENT,
		PENDING
	}

	/** One step of the current stop, e.g. "Camelot Teleport" or "Herb: harvest ranarr and plant ranarr". */
	@Value
	public static class Step
	{
		String text;
		StopStatus status;
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
		/** The current stop's steps in order (travel, journey steps, each patch), with how far along; empty otherwise. */
		List<Step> steps;
	}

	public static final RunView OFF_VIEW = new RunView(State.OFF, 0, Collections.emptyList(), null, false, null,
		null, null, null, Highlights.NONE);

	State state;
	/** When the run timer started (epoch millis), 0 until it has. */
	long startedAtMillis;
	List<Stop> stops;
	/** The next thing to do, shown under the player, or null. */
	String instruction;
	/** The player is already at the first stop, so there's no travelling to it. */
	boolean atFirstStop;
	/** e.g. "Run finished in 12:30", or null. */
	String lastRun;
	/** e.g. "Best for 6 herbs: 8:12, 8:40, 9:03", or null when there are none yet. */
	String bestTimes;
	/** Reminders under the instruction, one per line (e.g. "Drop 4 weeds"), or null. */
	String reminder;
	/** What to put on, e.g. "Wear: Graceful outfit, Ring of wealth", in its own colour; or null. */
	String wear;
	Highlights highlights;
}
