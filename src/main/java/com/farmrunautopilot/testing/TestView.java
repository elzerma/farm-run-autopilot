package com.farmrunautopilot.testing;

import java.util.Collections;
import java.util.List;
import lombok.Value;

/** What the Run tab shows for a guided test in progress. Immutable. */
@Value
public class TestView
{
	public static final TestView NONE = new TestView(null, null, 0, 0, null, false, false, Collections.emptyList());

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

	public boolean isActive()
	{
		return item != null;
	}
}
