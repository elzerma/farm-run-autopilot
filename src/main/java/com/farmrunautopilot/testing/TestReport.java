package com.farmrunautopilot.testing;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/** A finished guided test as a pre-filled GitHub issue. */
public final class TestReport
{
	static final String NEW_ISSUE_URL = "https://github.com/elzerma/farm-run-autopilot/issues/new";
	/** Long links are refused by browsers and GitHub; the body is cut to stay well under. */
	private static final int MAX_BODY = 6000;

	private TestReport()
	{
	}

	/** One line for the log and the developer's clipboard, e.g. "Guided test finished: X = Good to go". */
	public static String summary(TestItem item, boolean passed, List<String> problems)
	{
		return "Guided test finished: " + item.name() + " = " + (passed ? TestRunner.GOOD : TestRunner.ATTENTION)
			+ (problems.isEmpty() ? "" : " " + problems) + " (full report in the client log)";
	}

	public static String title(TestItem item, boolean passed)
	{
		return "Test: " + item.getTitle() + " - " + (passed ? "works" : "needs attention");
	}

	/**
	 * @param steps each step's text and how it went, e.g. "Detected" or "Answered No"
	 */
	public static String body(TestItem item, boolean passed, String version, List<String[]> steps,
		Map<String, String> captured, List<String> problems)
	{
		final StringBuilder b = new StringBuilder();
		b.append("**Guided test:** ").append(item.getTitle()).append(" (`").append(item.name()).append("`)\n");
		b.append("**Result:** ").append(passed ? "Works" : "Needs attention").append('\n');
		b.append("**RuneLite:** ").append(version).append("\n\n");
		b.append("### Steps\n");
		for (String[] step : steps)
		{
			b.append("- [").append(step[1].startsWith("Answered No") ? ' ' : 'x').append("] ")
				.append(step[0]).append(" (").append(step[1]).append(")\n");
		}
		if (!problems.isEmpty())
		{
			b.append("\n### Problems\n");
			for (String problem : problems)
			{
				b.append("- ").append(problem).append('\n');
			}
		}
		if (!captured.isEmpty())
		{
			b.append("\n### What the plugin saw\n");
			for (Map.Entry<String, String> e : captured.entrySet())
			{
				b.append("- **").append(e.getKey()).append(":** `").append(e.getValue().replace('`', '\'')).append("`\n");
			}
		}
		b.append("\n### Anything else?\n\n");
		return b.length() <= MAX_BODY ? b.toString() : b.substring(0, MAX_BODY) + "\n\n(cut short)\n";
	}

	public static String url(String title, String body)
	{
		return NEW_ISSUE_URL + "?title=" + encode(title) + "&body=" + encode(body);
	}

	private static String encode(String text)
	{
		try
		{
			// URLEncoder makes spaces "+", which GitHub reads as spaces in a query
			return URLEncoder.encode(text, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
