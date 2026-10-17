package com.farmrunautopilot.testing;

import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GuidedTestsTest
{
	@Test
	public void everyItemHasStepsAndSetsUpCleanly()
	{
		for (TestItem item : TestItem.values())
		{
			final GuidedTest test = GuidedTests.of(item);
			assertNotNull(item.name(), test);
			assertFalse(item.name(), test.steps().isEmpty());
			final RunConfig config = new RunConfig().sanitise();
			final AccountSettings account = new AccountSettings().sanitise();
			test.setUp(config, account);
			// What a test changes must survive being saved and loaded
			config.sanitise();
			account.sanitise();
		}
	}

	@Test
	public void reportIsAPrefilledIssueLink()
	{
		final List<String[]> steps = Arrays.asList(new String[]{"Teleport", "Detected"},
			new String[]{"Is it outlined?", "Answered No"});
		final String body = TestReport.body(TestItem.SPELLBOOK_SWAP, false, "1.11.0", steps,
			Collections.singletonMap("Quetzals loaded", "none"), Collections.singletonList("Answered No: Is it outlined?"));
		assertTrue(body.contains("- Teleport **Detected**"));
		assertTrue(body.contains("- Is it outlined? **Answered No**"));
		assertTrue(body.contains("**Quetzals loaded:** `none`"));
		final String url = TestReport.url(TestReport.title(TestItem.SPELLBOOK_SWAP, false), body);
		assertTrue(url.startsWith("https://github.com/elzerma/farm-run-autopilot/issues/new?title=Test%3A+Spellbook"));
		assertTrue(url.contains("&body="));
		assertFalse(url.contains(" "));
	}

	@Test
	public void problemReportFillsTheBugForm()
	{
		final String url = TestReport.problemUrl("1.13.2", Arrays.asList("Run types: [HERB]", "1. Catherby: walk, 9 s"));
		assertTrue(url.startsWith("https://github.com/elzerma/farm-run-autopilot/issues/new?template=bug.yml&title="));
		assertTrue(url.contains("&version=1.13.2&log="));
		assertTrue(url.contains("Catherby"));
		assertFalse(url.contains(" "));
	}
}
