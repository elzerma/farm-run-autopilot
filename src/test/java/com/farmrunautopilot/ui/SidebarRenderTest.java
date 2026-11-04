package com.farmrunautopilot.ui;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.RenderAccess;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.route.BankStop;
import com.farmrunautopilot.route.RenderData;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RouteStop;
import com.farmrunautopilot.route.RunOverrides;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.run.Highlights;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.testing.RenderTests;
import com.farmrunautopilot.testing.TestItem;
import com.farmrunautopilot.testing.TestRunner;
import com.farmrunautopilot.testing.TestView;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchTracker;
import com.farmrunautopilot.tracking.RenderTracker;
import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.Assume;
import org.junit.Test;

/**
 * Paints every sidebar tab offscreen at the sidebar's width, with RuneLite's look and feel, from an account like
 * elzerma's and made-up patch states, and writes what's on each (labels, hovers and heights) to a text file: for
 * layout and wording reviews without a game client (docs/plans/fresh-eyes-audit.md). Not part of the plugin, and
 * skipped unless asked for:
 *
 * <pre>
 * $env:SIDEBAR_RENDERS = "build/sidebar-renders"
 * ./gradlew test --tests com.farmrunautopilot.ui.SidebarRenderTest --rerun
 * </pre>
 *
 * (in bash, {@code SIDEBAR_RENDERS=build/sidebar-renders ./gradlew ...}). Each tab is painted as first opened and with
 * every section open, the Run tab also built, under way, logged out and during a guided test, and the Farm and Account
 * tabs with the options that open more settings ticked.
 */
public class SidebarRenderTest
{
	/** The plugin panel's width with the scroll bar showing (docs/BRANDING.md: "the 225 px sidebar"). */
	private static final int WIDTH = PluginPanel.PANEL_WIDTH;
	/** As SettingsStore opens them the first time. */
	private static final List<String> DEFAULT_OPEN = Arrays.asList("Crops", "Ways to travel");

	@Test
	public void renderEveryTab() throws Exception
	{
		final String folder = System.getenv("SIDEBAR_RENDERS");
		Assume.assumeTrue("Set SIDEBAR_RENDERS to a folder to paint the sidebar", folder != null && !folder.isEmpty());
		final File out = new File(folder);
		final String tag = "sidebar";
		out.mkdirs();
		SwingUtilities.invokeAndWait(RuneLiteLAF::setup);
		final Map<String, Integer> heights = new LinkedHashMap<>();
		try (PrintWriter text = new PrintWriter(new File(out, tag + "-inventory.txt"), "UTF-8"))
		{
			for (boolean open : new boolean[]{false, true})
			{
				final String mode = open ? "open" : "default";
				final Sidebar sidebar = onEdt(() -> new Sidebar(open, false));
				for (String tab : new String[]{"Run", "Farm", "Travel", "Account", "Settings"})
				{
					if (open && tab.equals("Travel"))
					{
						onEdt(() -> sidebar.openEveryFamily());
					}
					final int h = onEdt(() -> sidebar.render(tab, new File(out, tag + "-" + tab.toLowerCase() + "-"
						+ mode + ".png"), text, mode));
					heights.put(tab + " (" + mode + ")", h);
				}
			}
			// The Run tab in its other states
			for (RunView.State state : new RunView.State[]{RunView.State.BUILDING, RunView.State.RUNNING})
			{
				final Sidebar sidebar = onEdt(() -> new Sidebar(false, false));
				onEdt(() -> sidebar.showState(state));
				final String name = state.name().toLowerCase();
				heights.put("Run (" + name + ")", onEdt(() -> sidebar.render("Run",
					new File(out, tag + "-run-" + name + ".png"), text, name)));
			}
			final Sidebar testing = onEdt(() -> new Sidebar(false, false));
			onEdt(() -> testing.showTest());
			heights.put("Run (guided test)", onEdt(() -> testing.render("Run",
				new File(out, tag + "-run-test.png"), text, "guided test")));
			final Sidebar loggedOut = onEdt(() -> new Sidebar(false, true));
			heights.put("Run (logged out)", onEdt(() -> loggedOut.render("Run",
				new File(out, tag + "-run-loggedout.png"), text, "logged out")));
			heights.put("Farm (logged out)", onEdt(() -> loggedOut.render("Farm",
				new File(out, tag + "-farm-loggedout.png"), text, "logged out")));
			// What shows only once something is ticked: backups, saved herbs, compost bins, single patches, house scans
			final Sidebar options = onEdt(() -> new Sidebar(true, false));
			onEdt(() -> options.tickOptions());
			for (String tab : new String[]{"Farm", "Account"})
			{
				heights.put(tab + " (options on)", onEdt(() -> options.render(tab,
					new File(out, tag + "-" + tab.toLowerCase() + "-options.png"), text, "options on")));
			}
			text.println();
			text.println("== Heights (px, at " + WIDTH + " wide)");
			heights.forEach((k, v) -> text.println(k + ": " + v));
		}
	}

	private interface EdtCall<T>
	{
		T call() throws Exception;
	}

	private static <T> T onEdt(EdtCall<T> call) throws Exception
	{
		final Object[] result = new Object[1];
		final Exception[] failed = new Exception[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				result[0] = call.call();
			}
			catch (Exception e)
			{
				failed[0] = e;
			}
		});
		// Let anything the call queued (rebuilds) run
		SwingUtilities.invokeAndWait(() ->
		{
		});
		if (failed[0] != null)
		{
			throw failed[0];
		}
		@SuppressWarnings("unchecked")
		final T t = (T) result[0];
		return t;
	}

	private static void onEdt(Runnable run) throws Exception
	{
		onEdt(() ->
		{
			run.run();
			return null;
		});
	}

	/** The sidebar as FarmRunAutopilotPanel puts it together, from stubs. */
	private static final class Sidebar
	{
		final StubSettings settings;
		final AccessChecker access;
		final PatchTracker tracker;
		final TestRunner runner;
		final RunPanel run;
		final FarmPage farm;
		final TravelPage travel;
		final AccountPage account;
		final SettingsPage settingsPage;
		final PresetsPage presets;
		final TabBar tabs;
		final JPanel root;
		RunView view;
		TestView testView = TestView.NONE;
		RunPlan plan;

		Sidebar(boolean allOpen, boolean loggedOut)
		{
			settings = new StubSettings(allOpen, !loggedOut);
			final RunConfig config = settings.editRunConfig();
			config.getEnabledTypes().addAll(EnumSet.of(PatchType.ALLOTMENT, PatchType.FLOWER));
			final AccessSnapshot snapshot = loggedOut ? AccessSnapshot.UNKNOWN : RenderData.access();
			access = RenderAccess.checker(settings, snapshot);
			final long now = Instant.now().getEpochSecond();
			tracker = RenderTracker.tracker(patch -> predict(patch, now));
			runner = RenderTests.runner(settings, false,
				item -> item.ordinal() % 3 == 0 ? Collections.singletonList("Have an icy basalt in your bank or on you")
					: Collections.emptyList(), () -> testView);
			view = new RunView(RunView.State.OFF, 0, Collections.emptyList(), null, false,
				"Run finished in 14:05 - new best for 9 herbs", "Best: 9 herbs 6:05\nTrees: 5 trees 4:10", null, null,
				Highlights.NONE, null, Collections.emptyList());
			plan = plan(config, snapshot);

			final RunPanel[] runHolder = new RunPanel[1];
			final Runnable rebuild = () -> rebuildSetup(runHolder[0]);
			run = new RunPanel(settings, new RunOverrides(), () ->
			{
			}, rebuild, tracker, access, controls());
			runHolder[0] = run;
			if (!loggedOut)
			{
				run.update(plan, true);
			}
			presets = new PresetsPage(settings, access, rebuild);
			farm = new FarmPage(settings, access, rebuild);
			travel = new TravelPage(settings, access, rebuild);
			if (!loggedOut)
			{
				travel.showWays(RenderData.ways(config));
			}
			final GuidanceSettings guidance = new GuidanceSettings(new FarmRunAutopilotConfig()
			{
			}, null, null);
			account = new AccountPage(settings, access, rebuild);
			settingsPage = new SettingsPage(settings, access, tracker, rebuild, guidance, presets);
			settingsPage.setResetActions(() ->
			{
			}, () ->
			{
			});
			settingsPage.showHelpList(runner, () ->
			{
			});
			settingsPage.showProblemReport(() -> Collections.singletonList("plan"));
			settingsPage.showHoldings(RenderData::holdings);

			final JPanel display = new JPanel(new BorderLayout());
			display.setBackground(ColorScheme.DARK_GRAY_COLOR);
			final JPanel runStack = new JPanel();
			runStack.setLayout(new BoxLayout(runStack, BoxLayout.Y_AXIS));
			runStack.setBackground(ColorScheme.DARK_GRAY_COLOR);
			run.setAlignmentX(Component.LEFT_ALIGNMENT);
			runStack.add(run);
			runStack.add(help());
			final JPanel runTab = new JPanel(new BorderLayout());
			runTab.setBackground(ColorScheme.DARK_GRAY_COLOR);
			runTab.add(runStack, BorderLayout.NORTH);
			tabs = new TabBar(display);
			tabs.setBorder(new EmptyBorder(0, 0, 6, 0));
			tabs.addTab("Run", TabIcon.RUN, runTab);
			tabs.addTab("Farm", TabIcon.FARM, top(farm, help()));
			tabs.addTab("Travel", TabIcon.TRAVEL, top(travel, help()));
			tabs.addTab("Account", TabIcon.ACCOUNT, top(account, help()));
			tabs.addTab("Settings", TabIcon.SETTINGS, top(settingsPage, null));
			root = new JPanel(new BorderLayout());
			root.setBorder(new EmptyBorder(6, 6, 6, 6));
			root.setBackground(ColorScheme.DARK_GRAY_COLOR);
			root.add(tabs, BorderLayout.NORTH);
			root.add(display, BorderLayout.CENTER);
		}

		private void rebuildSetup(RunPanel runPanel)
		{
			if (farm == null)
			{
				return;
			}
			farm.rebuild();
			travel.rebuild();
			account.rebuild();
			settingsPage.rebuild();
			presets.rebuild();
			if (runPanel != null)
			{
				runPanel.refresh();
			}
		}

		private JButton help()
		{
			return HelpWanted.button(settings, false, () ->
			{
			});
		}

		private static JPanel top(JPanel page, JComponent help)
		{
			final JPanel stack = new JPanel();
			stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
			stack.setBackground(ColorScheme.DARK_GRAY_COLOR);
			page.setAlignmentX(Component.LEFT_ALIGNMENT);
			stack.add(page);
			if (help != null)
			{
				help.setAlignmentX(Component.LEFT_ALIGNMENT);
				stack.add(help);
			}
			final JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrapper.add(stack, BorderLayout.NORTH);
			return wrapper;
		}

		/** Herbs ready, trees growing, fruit trees to check, allotments and flowers growing, the rest not seen. */
		private static PatchPrediction predict(Patch patch, long now)
		{
			switch (patch.getType())
			{
				case HERB:
					return new PatchPrediction(patch, Crop.RANARR, PatchState.HARVESTABLE, 4, 4, 0, now - 600, now - 5000,
						PatchPrediction.Source.THIS_PLUGIN);
				case TREE:
					return new PatchPrediction(patch, Crop.MAGIC, PatchState.GROWING, 5, 12, now + 3 * 3600 + 1200,
						now - 600, now - 30000, PatchPrediction.Source.THIS_PLUGIN);
				case FRUIT_TREE:
					return new PatchPrediction(patch, Crop.PALM, PatchState.CHECK_HEALTH, 6, 6, 0, now - 600, now - 60000,
						PatchPrediction.Source.THIS_PLUGIN);
				case ALLOTMENT:
					return new PatchPrediction(patch, Crop.WATERMELON, PatchState.GROWING, 3, 8, now + 2400, now - 600,
						now - 3000, PatchPrediction.Source.THIS_PLUGIN);
				case FLOWER:
					return new PatchPrediction(patch, Crop.WHITE_LILY, PatchState.HARVESTABLE, 4, 4, 0, now - 600,
						now - 5000, PatchPrediction.Source.THIS_PLUGIN);
				default:
					return null;
			}
		}

		/** Herbs and flowers due, trees and fruit trees waiting, allotments included anyway. */
		private static RunPlan plan(RunConfig config, AccessSnapshot snapshot)
		{
			final long now = Instant.now().getEpochSecond();
			final List<Patch> patches = new ArrayList<>();
			for (Patch patch : Patch.values())
			{
				if ((patch.getType() == PatchType.HERB || patch.getType() == PatchType.FLOWER
					|| patch.getType() == PatchType.ALLOTMENT) && snapshot.isKnown() && snapshot.missingFor(patch).isEmpty()
					&& patch.getLocation() != Location.HARMONY_ISLAND)
				{
					patches.add(patch);
				}
			}
			final Map<PatchType, Long> skipped = new EnumMap<>(PatchType.class);
			skipped.put(PatchType.TREE, now + 3 * 3600 + 1200);
			skipped.put(PatchType.FRUIT_TREE, now + 25 * 60);
			final Set<PatchType> included = EnumSet.of(PatchType.HERB, PatchType.FLOWER, PatchType.ALLOTMENT);
			final RunSelection selection = new RunSelection(patches, included, skipped,
				Arrays.asList("Farming Guild herb: Ranarr - ready in 12m"), new EnumMap<>(PatchType.class));
			if (!snapshot.isKnown())
			{
				return RunPlan.EMPTY;
			}
			final Route route = RenderData.route(patches, config);
			final SupplyPlan supplies = new SupplyPlan(Collections.emptyList(), new EnumMap<>(PatchType.class),
				Arrays.asList("Farming Guild herb: Ranarr - ready in 12m"), Collections.emptyList(),
				Arrays.asList("Spellbook Swap needed for Catherby: Camelot Teleport is on the Lunar spellbook"), 0, "",
				24, new EnumMap<>(Patch.class));
			final Map<Location, List<String>> objectives = new EnumMap<>(Location.class);
			for (RouteStop stop : route.getStops())
			{
				objectives.put(stop.getLocation(), Arrays.asList("Herb: harvest ranarr and plant ranarr",
					"Flower: harvest white lily and plant white lily"));
			}
			final int middle = Math.max(0, route.getStops().size() / 2 - 1);
			final List<BankStop> banks = route.getStops().size() > 3
				? Collections.singletonList(new BankStop(middle, route.getStops().get(middle).getLocation(), supplies, null))
				: Collections.emptyList();
			return new RunPlan(selection, route, supplies, objectives, banks);
		}

		private RunPanel.RunControls controls()
		{
			return new RunPanel.RunControls()
			{
				@Override
				public RunView view()
				{
					return view;
				}

				@Override
				public void build()
				{
				}

				@Override
				public void cancel()
				{
				}

				@Override
				public void startNow()
				{
				}

				@Override
				public void stop()
				{
				}

				@Override
				public void setStepTicked(int step, boolean ticked)
				{
				}

				@Override
				public void setRowTicked(int row, boolean ticked)
				{
				}

				@Override
				public void bankForMissing()
				{
				}

				@Override
				public void skipMissing()
				{
				}

				@Override
				public void openUnlocks()
				{
				}

				@Override
				public void openPresets()
				{
				}

				@Override
				public boolean seedVaultSeen()
				{
					return false;
				}

				@Override
				public Double pace()
				{
					return 1.1;
				}

				@Override
				public TestView testView()
				{
					return testView;
				}

				@Override
				public void answerTest(boolean yes)
				{
				}

				@Override
				public void skipTestStep()
				{
				}

				@Override
				public void cancelTest()
				{
				}

				@Override
				public void openTestReport()
				{
				}

				@Override
				public void closeTestReport()
				{
				}
			};
		}

		void showState(RunView.State state)
		{
			final long now = System.currentTimeMillis();
			final List<RunView.Stop> stops = new ArrayList<>();
			final List<RouteStop> route = plan.getRoute().getStops();
			for (int i = 0; i < route.size(); i++)
			{
				final RouteStop stop = route.get(i);
				final RunView.StopStatus status = state == RunView.State.BUILDING ? RunView.StopStatus.PENDING
					: i < 2 ? RunView.StopStatus.DONE : i == 2 ? RunView.StopStatus.CURRENT : RunView.StopStatus.PENDING;
				final List<RunView.Step> steps = status != RunView.StopStatus.CURRENT ? Collections.emptyList()
					: Arrays.asList(new RunView.Step(stop.describeTravel(), RunView.StopStatus.DONE, false, false),
					new RunView.Step("Herb: harvest ranarr", RunView.StopStatus.CURRENT, false, false),
					new RunView.Step("Herb: plant ranarr and use ultracompost", RunView.StopStatus.PENDING, false, false),
					new RunView.Step("Note the produce on the tool leprechaun", RunView.StopStatus.PENDING, false, false));
				stops.add(new RunView.Stop(stop.getLocation().getDisplayName(), stop.describeTravel(), status, null,
					Collections.emptyList(), steps, false, false));
			}
			view = new RunView(state, now - 5 * 60_000, state == RunView.State.BUILDING ? Collections.emptyList() : stops,
				"Open a bank", false, null, null, null, null, Highlights.NONE,
				state == RunView.State.RUNNING ? "4:12 - 0:12 ahead of your best pace" : null, Collections.emptyList());
			run.update(plan, true);
		}

		/** Ticks the options that open more settings, then redraws the tabs. */
		void tickOptions()
		{
			final RunConfig config = settings.editRunConfig();
			config.setUseBackupCrops(true);
			config.getBackupCrops().put(PatchType.HERB, new ArrayList<>(Arrays.asList(Crop.RANARR)));
			config.setPrioritiseDiseaseFreeHerbs(true);
			config.getDiseaseFreeHerbs().addAll(EnumSet.of(Crop.TORSTOL, Crop.SNAPDRAGON));
			config.setMakeCompost(true);
			config.setUseProtectionOverrides(true);
			config.getProtectionOverrides().put(Patch.values()[0], com.farmrunautopilot.settings.Protection.COMPOST_ONLY);
			settings.editAccount().setAutoDetectHouse(true);
			settings.editAccount().getPoh().setLastDetected(Instant.now().getEpochSecond());
			rebuildSetup(run);
		}

		void showTest()
		{
			testView = RenderTests.inProgress(TestItem.SPELLBOOK_SWAP,
				"Press Build run and open a bank: are Spellbook Swap's runes (3 astral, 2 cosmic, 1 law) on the bank's "
					+ "Farm run tab, as well as Camelot Teleport's?", true, Collections.emptyList());
			run.update(plan, true);
		}

		/** Opens every family on the Travel tab, as clicking each one would. */
		void openEveryFamily()
		{
			for (int pass = 0; pass < 20; pass++)
			{
				final ExpandableRow closed = firstClosedRow(travel);
				if (closed == null)
				{
					return;
				}
				clickHead(closed);
			}
		}

		int render(String tab, File png, PrintWriter text, String mode) throws Exception
		{
			tabs.select(tab);
			layout(root);
			final int height = root.getHeight();
			final BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
			final Graphics2D g = image.createGraphics();
			root.printAll(g);
			g.dispose();
			ImageIO.write(image, "png", png);
			text.println();
			text.println("==================== " + tab + " (" + mode + "), " + height + " px");
			dump(text, visibleTab(), 0);
			return height;
		}

		private Component visibleTab()
		{
			for (Component c : ((Container) root.getComponent(1)).getComponents())
			{
				if (c.isVisible())
				{
					return c;
				}
			}
			return root;
		}
	}

	private static ExpandableRow firstClosedRow(Container c)
	{
		for (Component k : c.getComponents())
		{
			if (k instanceof ExpandableRow)
			{
				final Container content = (Container) ((ExpandableRow) k).getComponent(1);
				if (!content.isVisible())
				{
					return (ExpandableRow) k;
				}
			}
			else if (k instanceof Container)
			{
				final ExpandableRow found = firstClosedRow((Container) k);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void clickHead(ExpandableRow row)
	{
		final Component head = row.getComponent(0);
		head.dispatchEvent(new MouseEvent(head, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 5, 5, 1, false,
			MouseEvent.BUTTON1));
	}

	/**
	 * Offscreen, nothing is ever validated, so adding to a container never invalidates it and BoxLayout keeps the sizes
	 * it worked out first: clear every container's before laying out.
	 */
	private static void invalidateTree(Container c)
	{
		for (Component k : c.getComponents())
		{
			if (k instanceof Container)
			{
				invalidateTree((Container) k);
			}
		}
		c.invalidate();
	}

	/** A list cell laid out as it's painted: offscreen, the renderer pane's validate does nothing. */
	private static final class LaidOutCell extends JPanel
	{
		LaidOutCell(Component cell)
		{
			super(new BorderLayout());
			setOpaque(false);
			add(cell, BorderLayout.CENTER);
		}

		@Override
		public void paint(java.awt.Graphics g)
		{
			invalidateTree(this);
			layoutTree(this);
			super.paint(g);
		}
	}

	@SuppressWarnings("unchecked")
	private static void wrapListCells(Container c)
	{
		for (Component k : c.getComponents())
		{
			if (k instanceof JList)
			{
				final JList<Object> list = (JList<Object>) k;
				final javax.swing.ListCellRenderer<Object> renderer = list.getCellRenderer();
				if (!(renderer instanceof LaidOut))
				{
					list.setCellRenderer((LaidOut) (l, value, index, selected, focus) ->
						new LaidOutCell(renderer.getListCellRendererComponent(l, value, index, selected, focus)));
				}
			}
			else if (k instanceof Container)
			{
				wrapListCells((Container) k);
			}
		}
	}

	private interface LaidOut extends javax.swing.ListCellRenderer<Object>
	{
	}

	private static void layout(JComponent root)
	{
		wrapListCells(root);
		invalidateTree(root);
		root.setSize(WIDTH, 100);
		layoutTree(root);
		final Dimension preferred = root.getPreferredSize();
		root.setSize(WIDTH, preferred.height);
		layoutTree(root);
		// A second pass: rows that wrap only know their height once they have their width
		invalidateTree(root);
		root.setSize(WIDTH, root.getPreferredSize().height);
		layoutTree(root);
	}

	private static void layoutTree(Container c)
	{
		c.doLayout();
		for (Component k : c.getComponents())
		{
			if (k instanceof Container)
			{
				layoutTree((Container) k);
			}
		}
	}

	// The inventory text

	private static void dump(PrintWriter out, Component c, int depth)
	{
		if (!c.isVisible() || c.getHeight() == 0 && !(c instanceof Container && ((Container) c).getComponentCount() > 0))
		{
			return;
		}
		final String pad = String.join("", Collections.nCopies(depth, "  "));
		if (c instanceof CollapsibleSection)
		{
			final Container section = (Container) c;
			final Component head = section.getComponent(0);
			out.println(pad + "SECTION " + textOf(head) + "  [head " + head.getHeight() + ", total " + c.getHeight() + "]"
				+ tip(head));
			final Container content = (Container) section.getComponent(1);
			if (content.isVisible())
			{
				for (Component k : content.getComponents())
				{
					dump(out, k, depth + 1);
				}
			}
			return;
		}
		if (c instanceof ExpandableRow)
		{
			final Container row = (Container) c;
			final Component head = row.getComponent(0);
			out.println(pad + "ROW " + textOf(head) + "  [head " + head.getHeight() + ", total " + c.getHeight() + "]"
				+ tip(head));
			final Container content = (Container) row.getComponent(1);
			if (content.isVisible())
			{
				for (Component k : content.getComponents())
				{
					dump(out, k, depth + 1);
				}
			}
			return;
		}
		if (isLeaf(c))
		{
			out.println(pad + kind(c) + textOf(c) + "  [" + c.getHeight() + "]" + tip(c));
			return;
		}
		final Container container = (Container) c;
		boolean allLeaves = container.getComponentCount() > 0;
		for (Component k : container.getComponents())
		{
			allLeaves &= isLeaf(k) || k instanceof JPanel && ((JPanel) k).getComponentCount() == 0;
		}
		if (allLeaves && container.getComponentCount() > 1)
		{
			final List<String> parts = new ArrayList<>();
			String tips = "";
			for (Component k : container.getComponents())
			{
				if (isLeaf(k) && k.isVisible())
				{
					parts.add(kind(k) + textOf(k));
					tips += tip(k);
				}
			}
			out.println(pad + "LINE " + String.join(" | ", parts) + "  [" + c.getHeight() + "]" + tips
				+ (c instanceof JComponent ? tip(c) : ""));
			return;
		}
		if (c instanceof JComponent && ((JComponent) c).getToolTipText() != null && container.getComponentCount() > 0)
		{
			out.println(pad + "GROUP [" + c.getHeight() + "]" + tip(c));
		}
		for (Component k : container.getComponents())
		{
			dump(out, k, depth + (c instanceof Card ? 1 : 0));
		}
	}

	private static boolean isLeaf(Component c)
	{
		return c instanceof JLabel || c instanceof AbstractButton || c instanceof JComboBox || c instanceof JSpinner
			|| c instanceof JList;
	}

	private static String kind(Component c)
	{
		if (c instanceof JCheckBox)
		{
			final JCheckBox box = (JCheckBox) c;
			return (box.isSelected() ? "[x] " : "[ ] ") + (box.isEnabled() ? "" : "(greyed) ");
		}
		if (c instanceof JButton)
		{
			return "BUTTON ";
		}
		if (c instanceof JComboBox)
		{
			return "DROPDOWN ";
		}
		if (c instanceof JSpinner)
		{
			return "NUMBER ";
		}
		if (c instanceof JList)
		{
			return "LIST ";
		}
		return "";
	}

	private static String textOf(Component c)
	{
		if (c instanceof JLabel)
		{
			return plain(((JLabel) c).getText());
		}
		if (c instanceof AbstractButton)
		{
			return plain(((AbstractButton) c).getText());
		}
		if (c instanceof JComboBox)
		{
			final JComboBox<?> combo = (JComboBox<?>) c;
			final List<String> items = new ArrayList<>();
			for (int i = 0; i < combo.getItemCount(); i++)
			{
				items.add(String.valueOf(combo.getItemAt(i)));
			}
			return "\"" + combo.getSelectedItem() + "\" of " + items;
		}
		if (c instanceof JSpinner)
		{
			return String.valueOf(((JSpinner) c).getValue());
		}
		if (c instanceof JList)
		{
			return ((JList<?>) c).getModel().getSize() + " rows";
		}
		if (c instanceof Container)
		{
			final List<String> parts = new ArrayList<>();
			for (Component k : ((Container) c).getComponents())
			{
				final String t = textOf(k);
				if (!t.isEmpty())
				{
					parts.add(kind(k) + t);
				}
			}
			return String.join(" | ", parts);
		}
		return "";
	}

	private static String tip(Component c)
	{
		if (!(c instanceof JComponent))
		{
			return "";
		}
		final String tip = ((JComponent) c).getToolTipText();
		return tip == null ? "" : "\n" + "        tip: " + plain(tip);
	}

	private static String plain(String html)
	{
		if (html == null)
		{
			return "";
		}
		return html.replaceAll("<br>", " / ").replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&lt;", "<")
			.replace("&gt;", ">").replace("&nbsp;", " ").trim();
	}

	/** The settings store without RuneLite's config: open sections and client values in memory. */
	private static final class StubSettings extends SettingsStore
	{
		private final Map<String, String> client = new HashMap<>();
		private final Set<String> open = new HashSet<>(DEFAULT_OPEN);
		private final boolean allOpen;
		private final boolean profile;

		StubSettings(boolean allOpen, boolean profile)
		{
			super(null, new Gson());
			this.allOpen = allOpen;
			this.profile = profile;
		}

		@Override
		public boolean isSectionOpen(String title)
		{
			return allOpen || open.contains(title);
		}

		@Override
		public void setSectionOpen(String title, boolean isOpen)
		{
			if (isOpen)
			{
				open.add(title);
			}
			else
			{
				open.remove(title);
			}
		}

		@Override
		public String getClientValue(String key)
		{
			return client.get(key);
		}

		@Override
		public void setClientValue(String key, String value)
		{
			client.put(key, value);
		}

		@Override
		public boolean hasProfile()
		{
			return profile;
		}

		@Override
		public void saveRunConfig()
		{
		}

		@Override
		public void saveAccount(boolean external)
		{
		}

		@Override
		public List<String> presetNames()
		{
			return Arrays.asList("Full run", "Herbs only");
		}

		@Override
		public String activePreset()
		{
			return "Full run";
		}

		@Override
		public void applyPreset(String name)
		{
		}
	}
}
