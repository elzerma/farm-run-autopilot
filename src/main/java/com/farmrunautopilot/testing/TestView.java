package com.farmrunautopilot.testing;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * What the Run tab shows for a guided test in progress, or once it has finished: the GitHub report to send,
 * or why it couldn't be done. Immutable.
 */
@Value
public class TestView
{
	public static final TestView NONE = new TestView(null, null, 0, 0, null, false, false, Collections.emptyList(),
		false, null);

	/** Null when no test is running. */
	TestItem item;
	String title;
	int step;
	int steps;
	String text;
	/** Waiting for Yes or No. */
	boolean question;
	boolean optional;
	/** What to bring, e.g. "Pendant of Ates: in your bank". */
	List<String> bring;
	/** The test is over; only a message (and maybe the report) is left, with a Close button. */
	boolean finished;
	/** The pre-filled GitHub issue to open, for a finished test that has a report; otherwise null. */
	String reportUrl;

	public boolean isActive()
	{
		return item != null;
	}

	/** The test is done and only the GitHub report is left. */
	public boolean isReporting()
	{
		return reportUrl != null;
	}

	static TestView reporting(TestItem item, boolean passed, String reportUrl)
	{
		return finished(item, (passed
			? "Done, and it looks like it works. "
			: "Done, and something needs attention. ")
			+ "Last step: open the GitHub issue form. Your results are already filled in. Sign in to GitHub if it "
			+ "asks, add anything else you noticed at the bottom, then press \"Create\" (or \"Submit new issue\") "
			+ "to send it. Nothing is sent until you do. Your settings are already back to how they were.",
			reportUrl);
	}

	static TestView finished(TestItem item, String text, String reportUrl)
	{
		return new TestView(item, item.getTitle(), 0, 0, text, false, false, Collections.emptyList(), true,
			reportUrl);
	}
}
