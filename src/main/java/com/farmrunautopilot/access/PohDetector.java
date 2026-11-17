package com.farmrunautopilot.access;

import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
import com.farmrunautopilot.data.travel.atlas.AtlasHouse;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.SettingsStore;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Fills in My house from house furniture as it loads (SPEC 2 "auto-detect on visit"). Driven by object
 * spawn events (game objects and wall decorations), never by scanning the scene.
 *
 * <p>Detection only ever adds or upgrades, because objects in another player's house look the same
 * and a partly loaded house would otherwise wipe settings. Removed furniture is picked up with Rescan
 * house in My house, which clears what was detected so the next house visit fills it in again. The nexus
 * destinations can't be read this way and stay manual; where the house is comes from the game (AccessChecker).
 *
 * <p>Only objects in the player's own house are looked at: from the "Loading house" screen (shown a tick
 * before the house's objects spawn) until a scene outside an instance loads. The game doesn't say whose house
 * it is, so it's worked out from how the player got in: a friend's house or the advertisement board means
 * someone else's, and the house is skipped until the player goes home again. Solo ironmen can't visit other
 * houses, so theirs is always scanned. Once the house's scene loads, what it has is collected and saved on the
 * next game tick, even when none of the furniture is there, so a house without any still counts as scanned.
 */
@Slf4j
@Singleton
public class PohDetector
{
	/** The friend's-name prompt within this many ticks of using a house portal means visiting a friend. */
	private static final int NAME_PROMPT_TICKS = 50;

	private final Client client;
	private final SettingsStore settings;
	private final AccessChecker accessChecker;

	/** In the player's own house (client thread). */
	private boolean inHouse;
	/** The last way into a house was someone else's (client thread). */
	private boolean visiting;
	/** When a house portal was last used, for spotting the friend's-name prompt. */
	private int portalUsedTick = -NAME_PROMPT_TICKS;
	/** The player's own house is loading and its scene hasn't loaded yet (client thread). */
	private boolean awaitingScene;
	/** How many times the player's own house has been scanned since the plugin started (client thread). */
	private int scans;
	// Found since the house's scene loaded, saved on the next tick (client thread)
	private boolean scanDue;
	private JewelleryBoxTier foundBox;
	private PoolTier foundPool;
	private PohAltar foundAltar;
	private boolean foundFairyRing;
	private boolean foundSpiritTree;
	private boolean foundXericsTalisman;

	@Inject
	PohDetector(Client client, SettingsStore settings, AccessChecker accessChecker)
	{
		this.client = client;
		this.settings = settings;
		this.accessChecker = accessChecker;
	}

	/** In the player's own house, with scanning on. Client thread. */
	public boolean isScanningHouse()
	{
		return inHouse;
	}

	/** The last house entered was someone else's. Client thread. */
	public boolean isVisiting()
	{
		return visiting;
	}

	/** How many times the player's own house has been scanned, found or not, since the plugin started. Client thread. */
	public int getScans()
	{
		return scans;
	}

	/** The "Loading house" screen opened: the house's objects spawn next. Scan only the player's own. */
	public void onHouseLoading()
	{
		// Beta, ticked in Account > My house
		inHouse = settings.getAccount().isAutoDetectHouse() && (!visiting || soloIronman());
		awaitingScene = inHouse;
		log.debug("Entering a house: {}", inHouse ? "own, scanning" : "someone else's, not scanning");
	}

	/** Note how the player is getting into a house. */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		final String option = Text.removeTags(event.getMenuOption());
		final String target = Text.removeTags(event.getMenuTarget());
		final Widget widget = event.getWidget();
		final Boolean someoneElses = someoneElsesHouse(option, target,
			widget != null && widget.getText() != null ? Text.removeTags(widget.getText()) : null);
		if (someoneElses != null)
		{
			visiting = someoneElses;
		}
		if (target.contains("Portal"))
		{
			portalUsedTick = client.getTickCount();
		}
	}

	/**
	 * Whether a click leads into someone else's house (true), the player's own (false), or neither (null).
	 *
	 * @param dialogText the text of a clicked dialogue option or other widget, or null
	 */
	static Boolean someoneElsesHouse(String option, String target, String dialogText)
	{
		final String text = dialogText != null ? dialogText.toLowerCase() : "";
		if (option.equalsIgnoreCase("Friend's house") || target.contains("House Advertisement")
			|| text.contains("friend's house"))
		{
			return true;
		}
		if (option.equalsIgnoreCase("Home") || option.equalsIgnoreCase("Build mode") || option.contains("POH")
			|| target.toLowerCase().contains("teleport to house") || text.contains("your house")
			|| text.contains("build mode"))
		{
			return false;
		}
		return null;
	}

	/**
	 * Chosen "a friend's house" from the portal by keyboard: no click to see, but the game then asks for the
	 * friend's name. UNVERIFIED: the prompt is assumed to contain "Enter name".
	 */
	private void checkNamePrompt()
	{
		if (client.getTickCount() - portalUsedTick > NAME_PROMPT_TICKS)
		{
			return;
		}
		final Widget prompt = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		if (prompt != null && !prompt.isHidden() && prompt.getText() != null
			&& prompt.getText().contains("Enter name"))
		{
			visiting = true;
		}
	}

	/** Normal, hardcore and ultimate ironmen can't enter other players' houses. Group ironmen can. */
	private boolean soloIronman()
	{
		final int type = client.getVarbitValue(VarbitID.IRONMAN);
		return type >= 1 && type <= 3;
	}

	/** A new scene is loading; anything but an instance means the player has left the house. */
	public void onSceneLoading(boolean instance)
	{
		if (!instance)
		{
			inHouse = false;
			awaitingScene = false;
			clearFound();
		}
		else if (awaitingScene)
		{
			// The house's objects spawn with this scene; the next tick saves them, or that there were none
			awaitingScene = false;
			scanDue = true;
		}
	}

	/** Call for every spawned game object and wall decoration. Does nothing outside a house. */
	public void onObjectSpawned(int objectId)
	{
		if (!inHouse)
		{
			return;
		}
		final JewelleryBoxTier box = jewelleryBoxFor(objectId);
		final PoolTier pool = poolFor(objectId);
		final boolean fairyRing = isWayOut(AtlasHouse.ExitKind.FAIRY_RING, objectId);
		final boolean spiritTree = isWayOut(AtlasHouse.ExitKind.SPIRIT_TREE, objectId);
		final PohAltar altar = altarFor(objectId);
		final boolean talisman = isXericsTalisman(objectId);
		if (box == null && pool == null && altar == null && !fairyRing && !spiritTree && !talisman)
		{
			return;
		}
		if (talisman)
		{
			log.debug("Xeric's talisman in the house: object {}", objectId);
		}

		// Also furniture built while in the house, after its scene loaded
		scanDue = true;
		if (box != null && (foundBox == null || box.ordinal() > foundBox.ordinal()))
		{
			foundBox = box;
		}
		if (pool != null && (foundPool == null || pool.ordinal() > foundPool.ordinal()))
		{
			foundPool = pool;
		}
		if (altar != null && foundAltar != PohAltar.OCCULT)
		{
			foundAltar = altar;
		}
		foundFairyRing |= fairyRing;
		foundSpiritTree |= spiritTree;
		foundXericsTalisman |= talisman;
	}

	/** Save what the house that just loaded had. Call every game tick. */
	public void onGameTick()
	{
		checkNamePrompt();
		if (!scanDue)
		{
			return;
		}
		scans++;
		final JewelleryBoxTier box = foundBox;
		final PoolTier pool = foundPool;
		final PohAltar altar = foundAltar;
		final boolean fairyRing = foundFairyRing;
		final boolean spiritTree = foundSpiritTree;
		final boolean talisman = foundXericsTalisman;
		clearFound();
		// Settings are only changed on the Swing thread.
		SwingUtilities.invokeLater(() -> apply(box, pool, altar, fairyRing, spiritTree, talisman));
	}

	/** Forget anything found but not yet saved, and that the player was in a house. */
	public void reset()
	{
		inHouse = false;
		awaitingScene = false;
		clearFound();
	}

	/** The house's scene loaded and what it has is not yet saved (for tests). */
	boolean isScanDue()
	{
		return scanDue;
	}

	/** Furniture was found and not yet saved (for tests). */
	boolean hasFound()
	{
		return foundBox != null || foundPool != null || foundAltar != null || foundFairyRing || foundSpiritTree
			|| foundXericsTalisman;
	}

	/** The mounted Xeric's talisman was found and not yet saved (for tests). */
	boolean hasFoundXericsTalisman()
	{
		return foundXericsTalisman;
	}

	private void clearFound()
	{
		scanDue = false;
		foundBox = null;
		foundPool = null;
		foundAltar = null;
		foundFairyRing = false;
		foundSpiritTree = false;
		foundXericsTalisman = false;
	}

	/**
	 * Clear the detected furniture so the next house visit records exactly what's there, removals included.
	 * The nexus destinations are kept. Call on the Swing thread.
	 */
	public static void clearDetected(PohSetup poh)
	{
		poh.setJewelleryBox(null);
		poh.setPool(null);
		poh.setAltar(null);
		poh.setFairyRing(false);
		poh.setSpiritTree(false);
		poh.setXericsTalisman(false);
		poh.setLastDetected(0);
	}

	private void apply(JewelleryBoxTier box, PoolTier pool, PohAltar altar, boolean fairyRing, boolean spiritTree,
		boolean talisman)
	{
		final PohSetup poh = settings.editAccount().getPoh();
		final boolean firstScan = poh.getLastDetected() == 0;
		boolean changed = false;
		if (box != null && (poh.getJewelleryBox() == null || box.ordinal() > poh.getJewelleryBox().ordinal()))
		{
			poh.setJewelleryBox(box);
			changed = true;
		}
		if (pool != null && (poh.getPool() == null || pool.ordinal() > poh.getPool().ordinal()))
		{
			poh.setPool(pool);
			changed = true;
		}
		// The occult altar covers every spellbook, so it beats the others
		if (altar != null && poh.getAltar() != altar && poh.getAltar() != PohAltar.OCCULT)
		{
			poh.setAltar(altar);
			changed = true;
		}
		if (fairyRing && !poh.isFairyRing())
		{
			poh.setFairyRing(true);
			changed = true;
		}
		if (spiritTree && !poh.isSpiritTree())
		{
			poh.setSpiritTree(true);
			changed = true;
		}
		if (talisman && !poh.isXericsTalisman())
		{
			poh.setXericsTalisman(true);
			changed = true;
		}

		poh.setLastDetected(Instant.now().getEpochSecond());
		log.debug("Scanned the house: box={} pool={} altar={} fairyRing={} spiritTree={} xericsTalisman={} (changed={})",
			box, pool, altar, fairyRing, spiritTree, talisman, changed);
		// Always save so "last scanned" updates; refresh the tab when something new was found, or so My house stops
		// saying the house hasn't been scanned
		settings.saveAccount(changed || firstScan);
		if (changed)
		{
			accessChecker.requestRefresh();
		}
	}

	/**
	 * Whether this object is the house's way out of this kind, for the guide to outline and point at in the house: the
	 * furniture the scan reads (its jewellery box, fairy ring, spirit tree, spirit ring and Xeric's talisman), and the
	 * portal nexus, as RuneLite's gamevals and its POH plugin have it. None for the front door.
	 */
	public static boolean isWayOut(AtlasHouse.ExitKind kind, int objectId)
	{
		switch (kind)
		{
			case NEXUS:
				return objectId == ObjectID.POH_NEXUS_PORTAL_1 || objectId == ObjectID.POH_NEXUS_PORTAL_2
					|| objectId == ObjectID.POH_NEXUS_PORTAL_3;
			case JEWELLERY_BOX:
				return jewelleryBoxFor(objectId) != null;
			case XERICS_TALISMAN:
				return isXericsTalisman(objectId);
			case FAIRY_RING:
				return objectId == ObjectID.POH_FAIRY_RING || objectId == ObjectID.POH_SPIRIT_RING;
			case SPIRIT_TREE:
				return objectId == ObjectID.POH_SPIRIT_TREE || objectId == ObjectID.POH_SPIRIT_RING;
			default:
				return false;
		}
	}

	/** Whether this object is one of the house's ways out of any kind. */
	public static boolean isAnyWayOut(int objectId)
	{
		for (AtlasHouse.ExitKind kind : AtlasHouse.ExitKind.values())
		{
			if (isWayOut(kind, objectId))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The Xeric's talisman mounted in the portal nexus room's amulet space: its object and one for each place its left-click
	 * can be set to (POH_AMULET_XERIC_GLADE...), as RuneLite's gamevals name them, the six RuneLite's own POH plugin draws
	 * the talisman's map icon on, and the wiki's object ids for it. A house shows the one for its left-click, as a wall
	 * decoration (elzerma's dev check, 2026-11-15: #33412, "Glade").
	 */
	static boolean isXericsTalisman(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.POH_AMULET_XERIC:
			case ObjectID.POH_AMULET_XERIC_LOOKOUT:
			case ObjectID.POH_AMULET_XERIC_GLADE:
			case ObjectID.POH_AMULET_XERIC_INFERNO:
			case ObjectID.POH_AMULET_XERIC_HEART:
			case ObjectID.POH_AMULET_XERIC_HONOUR:
				return true;
			default:
				return false;
		}
	}

	static JewelleryBoxTier jewelleryBoxFor(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.POH_JEWELLERY_BOX_1:
				return JewelleryBoxTier.BASIC;
			case ObjectID.POH_JEWELLERY_BOX_2:
				return JewelleryBoxTier.FANCY;
			case ObjectID.POH_JEWELLERY_BOX_3:
				return JewelleryBoxTier.ORNATE;
			default:
				return null;
		}
	}

	static PohAltar altarFor(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.POH_ALTAR_ANCIENT:
				return PohAltar.ANCIENT;
			case ObjectID.POH_ALTAR_LUNAR:
				return PohAltar.LUNAR;
			case ObjectID.POH_ALTAR_DARK:
				return PohAltar.DARK;
			case ObjectID.POH_ALTAR_OCCULT:
			case ObjectID.POH_ALTAR_OCCULT_STANDARD:
			case ObjectID.POH_ALTAR_OCCULT_ANCIENT:
			case ObjectID.POH_ALTAR_OCCULT_LUNAR:
			case ObjectID.POH_ALTAR_OCCULT_ARCEUUS:
				return PohAltar.OCCULT;
			default:
				return null;
		}
	}

	static PoolTier poolFor(int objectId)
	{
		switch (objectId)
		{
			case ObjectID.POH_POOL_RESTORATION:
				return PoolTier.RESTORATION;
			case ObjectID.POH_POOL_REVITALISATION:
				return PoolTier.REVITALISATION;
			case ObjectID.POH_POOL_REJUVENATION:
				return PoolTier.REJUVENATION;
			// "Recovery" (29240) and "regeneration" (29241) are the fancy and ornate pools: the wiki gives the ornate pool
			// 29241, and the five are numbered in tier order
			case ObjectID.POH_POOL_RECOVERY:
				return PoolTier.FANCY_REJUVENATION;
			case ObjectID.POH_POOL_REGENERATION:
				return PoolTier.ORNATE_REJUVENATION;
			default:
				return null;
		}
	}
}
