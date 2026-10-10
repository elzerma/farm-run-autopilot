package com.farmrunautopilot;

import com.farmrunautopilot.access.AccessChecker;
import com.farmrunautopilot.bank.FarmBankTab;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.supply.BottomlessBucketTracker;
import com.farmrunautopilot.supply.HoldingsTracker;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import com.farmrunautopilot.route.RunOverrides;
import com.farmrunautopilot.route.RunPlan;
import com.farmrunautopilot.route.RunService;
import com.farmrunautopilot.run.GuidanceOverlay;
import com.farmrunautopilot.run.HighlightOverlay;
import com.farmrunautopilot.run.HintArrowController;
import com.farmrunautopilot.run.ItemHighlightOverlay;
import com.farmrunautopilot.run.RunSession;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.run.SceneTracker;
import com.farmrunautopilot.run.SpellHighlightOverlay;
import com.farmrunautopilot.tracking.PatchTracker;
import com.farmrunautopilot.ui.FarmRunAutopilotPanel;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Farm Run Autopilot",
	description = "Plans and guides combined tree, fruit tree and herb farming runs",
	tags = {"farming", "farm", "run", "herb", "tree", "fruit", "route", "planner"}
)
public class FarmRunAutopilotPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PatchTracker patchTracker;

	@Inject
	private SettingsStore settings;

	@Inject
	private AccessChecker accessChecker;

	@Inject
	private PohDetector pohDetector;

	@Inject
	private HoldingsTracker holdingsTracker;

	@Inject
	private BottomlessBucketTracker bottomlessBucket;

	@Inject
	private RunService runService;

	@Inject
	private RunOverrides runOverrides;

	@Inject
	private ClientThread clientThread;

	@Inject
	private FarmBankTab farmBankTab;

	@Inject
	private RunSession runSession;

	@Inject
	private GuidanceOverlay guidanceOverlay;

	@Inject
	private HighlightOverlay highlightOverlay;

	@Inject
	private ItemHighlightOverlay itemHighlightOverlay;

	@Inject
	private SpellHighlightOverlay spellHighlightOverlay;

	@Inject
	private SceneTracker sceneTracker;

	@Inject
	private HintArrowController hintArrow;

	@Inject
	private OverlayManager overlayManager;

	/** The session view last shown in the sidebar. */
	private RunView shownRunView;

	/** What the bank tab last showed: each line, amount needed and colour (not exact counts held). */
	private String bankTabContents = "";

	private final Runnable onSettingsReloaded = this::rebuildSetupLater;
	private final Runnable onSettingsSaved = () -> runService.markDirty();

	private FarmRunAutopilotPanel panel;
	private NavigationButton navButton;

	private WorldPoint lastTickLocation;
	private boolean lastTickPostLogin;
	private int lastModalCloseTick;

	@Override
	protected void startUp() throws Exception
	{
		settings.load();
		bottomlessBucket.reload();
		accessChecker.requestRefresh();
		panel = injector.getInstance(FarmRunAutopilotPanel.class);
		settings.addListener(onSettingsReloaded);
		settings.addSaveListener(onSettingsSaved);
		clientThread.invoke(holdingsTracker::loadCaches);
		farmBankTab.startUp(runService::getSupplies);
		overlayManager.add(guidanceOverlay);
		overlayManager.add(highlightOverlay);
		overlayManager.add(itemHighlightOverlay);
		overlayManager.add(spellHighlightOverlay);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Farm Run Autopilot")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		log.debug("Farm Run Autopilot started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		settings.removeListener(onSettingsReloaded);
		settings.removeSaveListener(onSettingsSaved);
		farmBankTab.shutDown();
		overlayManager.remove(guidanceOverlay);
		overlayManager.remove(highlightOverlay);
		overlayManager.remove(itemHighlightOverlay);
		overlayManager.remove(spellHighlightOverlay);
		itemHighlightOverlay.clearCache();
		clientThread.invoke(() ->
		{
			runSession.stop(false);
			hintArrow.clear();
			sceneTracker.clear();
			pohDetector.reset();
		});
		shownRunView = null;
		clientToolbar.removeNavigation(navButton);
		panel.shutDown();
		navButton = null;
		panel = null;
		lastTickLocation = null;
		lastTickPostLogin = false;
		patchTracker.reset();
		accessChecker.reset();
		runService.reset();
		runOverrides.clear();
		log.debug("Farm Run Autopilot stopped");
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		pohDetector.onGameTick();
		if (accessChecker.onGameTick())
		{
			rebuildSetupLater();
			runService.markDirty();
		}
		if (runService.onGameTick())
		{
			final RunPlan plan = runService.getPlan();
			showPlanLater(plan, true);
			// Redraw the bank tab when a line or its colour changes. The bank redraws itself on every withdrawal,
			// but a tick before the plan catches up, so without this it would show the previous step.
			final String contents = bankTabContents(plan.getSupplies());
			if (!contents.equals(bankTabContents))
			{
				bankTabContents = contents;
				farmBankTab.refresh();
			}
		}

		runSession.onGameTick();
		hintArrow.update();
		final RunView runView = runSession.getView();
		if (!runView.equals(shownRunView))
		{
			if (shownRunView == null || shownRunView.getState() != runView.getState())
			{
				farmBankTab.setEnabled(runView.getState() != RunView.State.OFF);
			}
			shownRunView = runView;
			showPlanLater(runService.getPlan(), client.getGameState() == GameState.LOGGED_IN);
		}

		// Patch varbits are only sent after leaving the post-login welcome screen
		final Widget motw = client.getWidget(InterfaceID.WelcomeScreen.MOTW);
		if (motw != null && !motw.isHidden())
		{
			lastTickPostLogin = true;
			return;
		}
		if (lastTickPostLogin)
		{
			lastTickPostLogin = false;
			return;
		}

		final WorldPoint location = lastTickLocation;
		final Player player = client.getLocalPlayer();
		lastTickLocation = player == null ? null : player.getWorldLocation();

		// Skip ticks where the player crossed a region boundary
		if (location == null || lastTickLocation == null || location.getRegionID() != lastTickLocation.getRegionID())
		{
			return;
		}

		if (patchTracker.update(location, client.getTickCount() - lastModalCloseTick))
		{
			runService.markDirty();
			final FarmRunAutopilotPanel p = panel;
			SwingUtilities.invokeLater(p::refreshPatches);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getModalMode() != WidgetModalMode.NON_MODAL)
		{
			lastModalCloseTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		patchTracker.reset();
		accessChecker.reset();
		holdingsTracker.loadCaches();
		runService.reset();
		// Reloading notifies onSettingsReloaded, which rebuilds the settings tabs
		settings.load();
		bottomlessBucket.reload();
		holdingsTracker.markDirty();
		final FarmRunAutopilotPanel p = panel;
		SwingUtilities.invokeLater(p::refreshPatches);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			accessChecker.requestRefresh();
			holdingsTracker.markDirty();
			runService.reset();
		}
		else if (event.getGameState() == GameState.LOADING)
		{
			// Objects are spawned again once the new scene loads; NPCs nearby aren't, so keep those
			sceneTracker.clearObjects();
			pohDetector.onSceneLoading(client.getTopLevelWorldView().isInstance());
		}
		else if (event.getGameState() == GameState.HOPPING)
		{
			sceneTracker.clear();
			pohDetector.reset();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// A run being built or waiting to leave is dropped; one already timing carries on after relogging
			runSession.cancelIfNotRunning();
			sceneTracker.clear();
			pohDetector.reset();
			showPlanLater(RunPlan.EMPTY, false);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		accessChecker.onStatChanged(event.getSkill(), event.getLevel());
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		holdingsTracker.onItemContainerChanged(event.getContainerId(), event.getItemContainer());
		if (event.getContainerId() == InventoryID.INV && bottomlessBucket.onInventoryChanged(event.getItemContainer()))
		{
			holdingsTracker.markDirty();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		holdingsTracker.onVarbitChanged(event.getVarbitId());
		accessChecker.onVarbitChanged(event.getVarbitId());
		if (bottomlessBucket.onVarbitChanged(event.getVarbitId()))
		{
			holdingsTracker.markDirty();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
		{
			final String message = Text.removeTags(event.getMessage());
			runSession.onChatMessage(message);
			if (bottomlessBucket.onChatMessage(message))
			{
				holdingsTracker.markDirty();
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.POH_LOADING)
		{
			pohDetector.onHouseLoading();
		}
		farmBankTab.onWidgetLoaded(event);
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		farmBankTab.onScriptCallbackEvent(event);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		farmBankTab.onScriptPreFired(event);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		farmBankTab.onScriptPostFired(event);
	}

	// After other plugins, so withdraw clicks are re-pointed last
	@Subscribe(priority = -1)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		runSession.onMenuOptionClicked(event);
		bottomlessBucket.onMenuOptionClicked(event);
		pohDetector.onMenuOptionClicked(event);
		farmBankTab.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		pohDetector.onObjectSpawned(event.getGameObject().getId());
		sceneTracker.onObjectSpawned(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		sceneTracker.onObjectDespawned(event.getGameObject());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		sceneTracker.onNpcSpawned(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		sceneTracker.onNpcDespawned(event.getNpc());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!FarmRunAutopilotConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("itemColour".equals(event.getKey()))
		{
			itemHighlightOverlay.clearCache();
		}
		else if (FarmRunAutopilotConfig.OPEN_SIDEBAR_KEY.equals(event.getKey()))
		{
			// The config panel has no buttons and doesn't redraw a box we untick, so any click on it opens the sidebar
			SwingUtilities.invokeLater(() ->
			{
				if (navButton != null)
				{
					clientToolbar.openPanel(navButton);
				}
			});
		}
	}

	private static String bankTabContents(SupplyPlan plan)
	{
		final StringBuilder sb = new StringBuilder();
		for (SupplyLine line : plan.getLines())
		{
			sb.append(line.getName()).append('=').append(line.getNeed()).append(':').append(line.getStatus()).append(';');
		}
		return sb.toString();
	}

	private void showPlanLater(RunPlan plan, boolean loggedIn)
	{
		final FarmRunAutopilotPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.updateRun(plan, loggedIn));
		}
	}

	private void rebuildSetupLater()
	{
		final FarmRunAutopilotPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::rebuildSetup);
		}
	}

	@Provides
	FarmRunAutopilotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmRunAutopilotConfig.class);
	}
}
