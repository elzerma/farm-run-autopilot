package com.farmrunautopilot.access;

import com.farmrunautopilot.data.poh.JewelleryBoxTier;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.poh.PoolTier;
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
 * spawn events, never by scanning the scene.
 *
 * <p>Detection only ever adds or upgrades, because objects in another player's house look the same
 * and a partly loaded house would otherwise wipe settings. Removed furniture is picked up with Rescan
 * house in My house, which clears what was detected so the next house visit fills it in again. The house
 * portal location and nexus destinations can't be read this way and stay manual.
 *
 * <p>Only objects in the player's own house are looked at: from the "Loading house" screen (shown a tick
 * before the house's objects spawn) until a scene outside an instance loads. The game doesn't say whose house
 * it is, so it's worked out from how the player got in: a friend's house or the advertisement board means
 * someone else's, and the house is skipped until the player goes home again. Solo ironmen can't visit other
 * houses, so theirs is always scanned. Furniture found is collected and saved once, on the next game tick.
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
	// Found since the last tick (client thread)
	private boolean found;
	private JewelleryBoxTier foundBox;
	private PoolTier foundPool;
	private PohAltar foundAltar;
	private boolean foundFairyRing;
	private boolean foundSpiritTree;

	@Inject
	PohDetector(Client client, SettingsStore settings, AccessChecker accessChecker)
	{
		this.client = client;
		this.settings = settings;
		this.accessChecker = accessChecker;
	}

	/** The "Loading house" screen opened: the house's objects spawn next. Scan only the player's own. */
	public void onHouseLoading()
	{
		// Beta, ticked in Account > My house
		inHouse = settings.getAccount().isAutoDetectHouse() && (!visiting || soloIronman());
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
		}
	}

	/** Call for every spawned game object. Does nothing outside a house. */
	public void onObjectSpawned(int objectId)
	{
		if (!inHouse)
		{
			return;
		}
		final JewelleryBoxTier box = jewelleryBoxFor(objectId);
		final PoolTier pool = poolFor(objectId);
		final boolean fairyRing = objectId == ObjectID.POH_FAIRY_RING || objectId == ObjectID.POH_SPIRIT_RING;
		final boolean spiritTree = objectId == ObjectID.POH_SPIRIT_TREE || objectId == ObjectID.POH_SPIRIT_RING;
		final PohAltar altar = altarFor(objectId);
		if (box == null && pool == null && altar == null && !fairyRing && !spiritTree)
		{
			return;
		}

		found = true;
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
	}

	/** Save what the house that just loaded had. Call every game tick. */
	public void onGameTick()
	{
		checkNamePrompt();
		if (!found)
		{
			return;
		}
		final JewelleryBoxTier box = foundBox;
		final PoolTier pool = foundPool;
		final PohAltar altar = foundAltar;
		final boolean fairyRing = foundFairyRing;
		final boolean spiritTree = foundSpiritTree;
		clearFound();
		// Settings are only changed on the Swing thread.
		SwingUtilities.invokeLater(() -> apply(box, pool, altar, fairyRing, spiritTree));
	}

	/** Forget anything found but not yet saved, and that the player was in a house. */
	public void reset()
	{
		inHouse = false;
		clearFound();
	}

	/** Furniture was found and not yet saved (for tests). */
	boolean hasFound()
	{
		return found;
	}

	private void clearFound()
	{
		found = false;
		foundBox = null;
		foundPool = null;
		foundAltar = null;
		foundFairyRing = false;
		foundSpiritTree = false;
	}

	/**
	 * Clear the detected furniture so the next house visit records exactly what's there, removals included.
	 * The portal location, teleport-outside and nexus destinations are kept. Call on the Swing thread.
	 */
	public static void clearDetected(PohSetup poh)
	{
		poh.setJewelleryBox(null);
		poh.setPool(null);
		poh.setAltar(null);
		poh.setFairyRing(false);
		poh.setSpiritTree(false);
		poh.setLastDetected(0);
	}

	private void apply(JewelleryBoxTier box, PoolTier pool, PohAltar altar, boolean fairyRing, boolean spiritTree)
	{
		final PohSetup poh = settings.getAccount().getPoh();
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

		poh.setLastDetected(Instant.now().getEpochSecond());
		if (changed)
		{
			log.debug("Detected POH furniture: box={} pool={} altar={} fairyRing={} spiritTree={}", box, pool, altar, fairyRing,
				spiritTree);
		}
		// Always save so "last detected" updates; refresh the tab only when something new was found.
		settings.saveAccount(changed);
		if (changed)
		{
			accessChecker.requestRefresh();
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
			// UNVERIFIED: "recovery" and "regeneration" are assumed to be the fancy and ornate pools.
			case ObjectID.POH_POOL_RECOVERY:
				return PoolTier.FANCY_REJUVENATION;
			case ObjectID.POH_POOL_REGENERATION:
				return PoolTier.ORNATE_REJUVENATION;
			default:
				return null;
		}
	}
}
