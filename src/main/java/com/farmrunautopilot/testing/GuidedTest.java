package com.farmrunautopilot.testing;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Value;

/**
 * A test the plugin walks the player through on the Run tab: settings to use, items to bring, and steps it
 * either detects or asks about. What it sees along the way goes into the GitHub report.
 */
public abstract class GuidedTest
{
	/** Something to bring: any one of these items. */
	@Value
	public static class Need
	{
		String label;
		int[] itemIds;
	}

	/**
	 * One step. It's done when {@code check} passes (checked every tick and after each chat message). Without
	 * a check, or once {@code askAfterTicks} pass without it, the player is asked {@code question} instead.
	 */
	public static final class Step
	{
		final String text;
		Predicate<TestContext> check;
		Consumer<TestContext> onStart;
		Consumer<TestContext> onDone;
		String question;
		int askAfterTicks = -1;
		/** Told the answer; by default a "No" is a problem. */
		BiConsumer<TestContext, Boolean> onAnswer;
		boolean optional;

		private Step(String text)
		{
			this.text = text;
		}

		/** A step the plugin detects. */
		public static Step doThis(String text, Predicate<TestContext> check)
		{
			final Step step = new Step(text);
			step.check = check;
			return step;
		}

		/** A Yes/No question: only the player can tell. */
		public static Step ask(String question)
		{
			final Step step = new Step(question);
			step.question = question;
			return step;
		}

		/** Ask this if the step isn't detected within this many ticks. */
		public Step orAskAfter(int ticks, String question)
		{
			this.askAfterTicks = ticks;
			this.question = question;
			return this;
		}

		public Step onStart(Consumer<TestContext> action)
		{
			this.onStart = action;
			return this;
		}

		public Step onDone(Consumer<TestContext> action)
		{
			this.onDone = action;
			return this;
		}

		public Step onAnswer(BiConsumer<TestContext, Boolean> action)
		{
			this.onAnswer = action;
			return this;
		}

		/** Can be skipped. */
		public Step optional()
		{
			this.optional = true;
			return this;
		}
	}

	/** What's missing to run this test, as far as the plugin can tell; empty if it can be run. */
	public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
	{
		return Collections.emptyList();
	}

	/** Change these copies of the settings for the test; the originals are put back afterwards. */
	public void setUp(RunConfig config, AccountSettings account)
	{
	}

	/** Items to bring. */
	public List<Need> bring()
	{
		return Collections.emptyList();
	}

	public abstract List<Step> steps();

	/** The test plans a run: it's stopped when the test ends, since the settings change back. */
	public boolean usesRun()
	{
		return false;
	}

	/**
	 * After the settings are put back: keep anything the test found, e.g. an unlock it saw.
	 *
	 * @param kept the player's own settings, about to be saved
	 * @param during the settings as they were at the end of the test
	 */
	public void keep(AccountSettings kept, AccountSettings during)
	{
	}
}
