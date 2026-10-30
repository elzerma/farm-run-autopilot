package com.farmrunautopilot.run;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Times for the step log to the millisecond, as RuneLite's log lines only show whole seconds: enough to tell how long
 * after a teleport's landing the guide's step and walking line came (elzerma, 2026-10-30).
 */
final class LogClock
{
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private LogClock()
	{
	}

	/** e.g. "at 20:53:52.094". */
	static String now()
	{
		return "at " + TIME.format(LocalTime.now());
	}
}
