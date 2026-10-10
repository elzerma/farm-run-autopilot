package com.farmrunautopilot.testing;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/** What the Run tab shows for a guided test in progress, or its last step: reporting it. Immutable. */
@Value
public class TestView
{
	public static final TestView NONE = new TestView(null, null, 0, 0, null, false, false, Collections.emptyList(),
		null);

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
	/** The last step: the pre-filled GitHub issue to open; null until the test's steps are done. */
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
		return new TestView(item, item.getTitle(), 0, 0, (passed
			? "Done, and it looks like it works. "
			: "Done, and something needs attention. ")
			+ "Last step: open the GitHub issue form. Your results are already filled in. Sign in to GitHub if it "
			+ "asks, add anything else you noticed at the bottom, then press \"Create\" (or \"Submit new issue\") "
			+ "to send it. Nothing is sent until you do. Your settings are already back to how they were.",
			false, false, Collections.emptyList(), reportUrl);
	}
}
