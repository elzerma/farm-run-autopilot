package com.farmrunautopilot.testing;

import com.farmrunautopilot.settings.SettingsStore;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** A test runner that starts nothing, for the sidebar renders. */
public final class RenderTests
{
	private RenderTests()
	{
	}

	public static TestRunner runner(SettingsStore settings, boolean developer, Function<TestItem, List<String>> missing,
		Supplier<TestView> view)
	{
		return new TestRunner(null, null, settings, null, null, null, null, null, null, null, developer)
		{
			@Override
			public List<String> missing(TestItem item)
			{
				return missing.apply(item);
			}

			@Override
			public TestView getView()
			{
				return view.get();
			}
		};
	}

	/** A guided test part way through, as the Run tab shows it. */
	public static TestView inProgress(TestItem item, String text, boolean question, List<String> bring)
	{
		return new TestView(item, item.getTitle(), 2, 5, text, question, false, bring, false, null);
	}
}
