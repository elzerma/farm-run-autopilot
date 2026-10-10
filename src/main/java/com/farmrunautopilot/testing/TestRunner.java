package com.farmrunautopilot.testing;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.run.RunSession;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.run.SceneTracker;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.ItemChargeTracker;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.LinkBrowser;

/**
 * Runs one guided test at a time: backs up the settings and applies the test's, walks the player through its
 * steps on the Run tab, then puts the settings back and opens a pre-filled GitHub issue with the result.
 *
 * <p>{@link #start} and {@link #cancel} are called on the Swing thread; everything else on the client thread.
 */
@Slf4j
@Singleton
public class TestRunner
{
	/** Result names shared with the "I NEED YOUR HELP!" list. */
	public static final String GOOD = "Good to go";
	public static final String ATTENTION = "Needs attention";

	private final Client client;
	private final ClientThread clientThread;
	private final SettingsStore settings;
	private final AccessChecker accessChecker;
	private final HoldingsTracker holdingsTracker;
	private final ItemChargeTracker itemCharges;
	private final RunSession runSession;
	private final RunService runService;
	private final SceneTracker scene;
	private final PohDetector pohDetector;
	/** The dev client: the whole report also goes to the log, to be read back without submitting it. */
	private final boolean developerMode;

	// Client thread
	private TestItem item;
	private GuidedTest test;
	private List<GuidedTest.Step> steps;
	private int stepIndex;
	private int stepTicks;
	private boolean asking;
	private TestContext context;
	/** Each finished step's text and how it went. */
	private final List<String[]> results = new ArrayList<>();

	private volatile TestView view = TestView.NONE;
	/** Set on the Swing thread as soon as a test is started, so a second can't start before the first begins. */
	private volatile boolean busy;

	@Inject
	TestRunner(Client client, ClientThread clientThread, SettingsStore settings, AccessChecker accessChecker,
		HoldingsTracker holdingsTracker, ItemChargeTracker itemCharges, RunSession runSession, RunService runService,
		SceneTracker scene, PohDetector pohDetector, @Named("developerMode") boolean developerMode)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.settings = settings;
		this.accessChecker = accessChecker;
		this.holdingsTracker = holdingsTracker;
		this.itemCharges = itemCharges;
		this.runSession = runSession;
		this.runService = runService;
		this.scene = scene;
		this.pohDetector = pohDetector;
		this.developerMode = developerMode;
	}

	public TestView getView()
	{
		return view;
	}

	public boolean isBusy()
	{
		return busy;
	}

	/** Why this test can't be started now; empty if it can. Swing thread. */
	public List<String> missing(TestItem item)
	{
		final List<String> missing = new ArrayList<>();
		if (busy)
		{
			missing.add("Another test is running");
			return missing;
		}
		if (!accessChecker.getSnapshot().isKnown())
		{
			missing.add("Log in first");
			return missing;
		}
		if (runSession.getView().getState() != RunView.State.OFF)
		{
			missing.add("Stop your run first");
		}
		missing.addAll(GuidedTests.of(item).missing(accessChecker.getSnapshot(), holdingsTracker.getHoldings(),
			settings.getAccount()));
		return missing;
	}

	/** Back up the settings, apply the test's and begin. Swing thread. */
	public void start(TestItem item)
	{
		if (busy)
		{
			return;
		}
		busy = true;
		final GuidedTest test = GuidedTests.of(item);
		settings.startTest(test::setUp);
		log.info("Guided test started: {}", item.name());
		clientThread.invoke(() -> begin(item, test));
	}

	private void begin(TestItem item, GuidedTest test)
	{
		this.item = item;
		this.test = test;
		this.steps = test.steps();
		this.stepIndex = -1;
		this.context = new TestContext(client, accessChecker, holdingsTracker, itemCharges, runSession, runService,
			scene, pohDetector, settings);
		results.clear();
		nextStep();
	}

	/** Stop without reporting. Swing thread. */
	public void cancel()
	{
		clientThread.invoke(() ->
		{
			if (item != null)
			{
				log.info("Guided test cancelled: {}", item.name());
				finish(false, false);
			}
		});
	}

	public void answer(boolean yes)
	{
		clientThread.invoke(() ->
		{
			if (item == null || !asking)
			{
				return;
			}
			final GuidedTest.Step step = steps.get(stepIndex);
			if (step.onAnswer != null)
			{
				step.onAnswer.accept(context, yes);
			}
			else if (!yes)
			{
				context.problem("Answered No: " + step.question);
			}
			results.add(new String[]{step.question, yes ? "Answered Yes" : "Answered No"});
			nextStep();
		});
	}

	public void skip()
	{
		clientThread.invoke(() ->
		{
			if (item != null && steps.get(stepIndex).optional)
			{
				results.add(new String[]{steps.get(stepIndex).text, "Skipped"});
				nextStep();
			}
		});
	}

	public void onChatMessage(String message)
	{
		if (item != null)
		{
			context.addChat(message);
			check();
		}
	}

	public void onGameTick()
	{
		if (item == null)
		{
			return;
		}
		stepTicks++;
		final GuidedTest.Step step = steps.get(stepIndex);
		if (!asking && step.askAfterTicks >= 0 && stepTicks >= step.askAfterTicks)
		{
			asking = true;
			publish();
		}
		check();
	}

	/** Account switched or logged out: give up quietly, so the settings go back to the right account. */
	public void onLoggedOut()
	{
		if (item != null)
		{
			finish(false, false);
		}
	}

	private void check()
	{
		final GuidedTest.Step step = steps.get(stepIndex);
		if (step.check != null && step.check.test(context))
		{
			if (step.onDone != null)
			{
				step.onDone.accept(context);
			}
			results.add(new String[]{step.text, "Detected"});
			context.clearChat();
			nextStep();
			return;
		}
		context.clearChat();
	}

	private void nextStep()
	{
		stepIndex++;
		stepTicks = 0;
		if (stepIndex >= steps.size())
		{
			finish(true, context.getProblems().isEmpty());
			return;
		}
		final GuidedTest.Step step = steps.get(stepIndex);
		asking = step.check == null;
		if (step.onStart != null)
		{
			step.onStart.accept(context);
		}
		publish();
	}

	private void publish()
	{
		final GuidedTest.Step step = steps.get(stepIndex);
		final String text = asking && step.check != null ? step.question : step.text;
		view = new TestView(item, item.getTitle(), stepIndex + 1, steps.size(), text, asking, step.optional,
			bringLines());
	}

	private List<String> bringLines()
	{
		final Holdings holdings = holdingsTracker.getHoldings();
		final List<String> lines = new ArrayList<>();
		for (GuidedTest.Need need : test.bring())
		{
			final String where;
			if (holdings.carriedOnly().countAny(need.getItemIds()) > 0)
			{
				where = "on you";
			}
			else if (holdings.countAny(need.getItemIds()) > 0)
			{
				where = "in your bank or storage";
			}
			else
			{
				where = "not found";
			}
			lines.add(need.getLabel() + ": " + where);
		}
		return lines;
	}

	/**
	 * @param report open the GitHub issue (not when cancelled)
	 */
	private void finish(boolean report, boolean passed)
	{
		final TestItem done = item;
		final GuidedTest finished = test;
		final TestContext seen = context;
		final List<String[]> steps = new ArrayList<>(results);
		final Map<String, String> captured = Collections.unmodifiableMap(seen.getCaptured());
		final List<String> problems = new ArrayList<>(seen.getProblems());
		if (finished.usesRun())
		{
			// The run was planned with the test's settings, which are about to change back
			runSession.cancel();
		}
		item = null;
		test = null;
		this.steps = null;
		context = null;
		view = TestView.NONE;
		final String summary = TestReport.summary(done, passed, problems);
		if (report)
		{
			log.info(summary);
			if (developerMode)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Farm Run Autopilot: test "
					+ (passed ? "passed" : "needs attention") + ". The result is on your clipboard to paste to Claude.",
					null);
			}
		}
		SwingUtilities.invokeLater(() ->
		{
			settings.endTest(finished::keep);
			busy = false;
			if (!report)
			{
				return;
			}
			settings.setClientValue(done.key(), passed ? GOOD : ATTENTION);
			final String body = TestReport.body(done, passed, RuneLiteProperties.getVersion(), steps, captured,
				problems);
			if (developerMode)
			{
				// The developer pastes the summary to Claude, who reads the full report from the log
				log.info("Guided test report: {}\n{}", done.name(), body);
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(summary), null);
			}
			else
			{
				LinkBrowser.browse(TestReport.url(TestReport.title(done, passed), body));
			}
		});
	}
}
