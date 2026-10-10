package com.farmrunautopilot.supply;

import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.SettingsStore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Keeps the uses left in the bottomless compost bucket (GitHub #3). The game only shows them in chat (Check,
 * filling) and, for a bucket stored at the tool leprechaun, in its varbits; each patch treated uses one. Saved
 * per account so they survive logging out. Message wording is from the wiki's bucket transcript.
 *
 * <p>Client thread, except that settings are written on the Swing thread.
 */
@Slf4j
@Singleton
public class BottomlessBucketTracker
{
	private static final String TYPES = "(ultracompost|supercompost|compost)";
	static final Pattern CHECK = Pattern.compile(
		"Your bottomless compost bucket is currently holding ([\\d,]+) uses? of " + TYPES + "\\.");
	static final Pattern EMPTY = Pattern.compile("Your (?:bottomless )?compost bucket is currently empty\\.");
	static final Pattern FILL_TYPE = Pattern.compile(
		"You fill your bottomless compost bucket with (?:a single bucket|[\\d,]+ buckets) of " + TYPES + "\\.");
	static final Pattern FILL_TOTAL = Pattern.compile(
		"Your bottomless compost bucket now contains a total of ([\\d,]+) uses\\.");
	/** As in RuneLite core CompostTracker (BSD-2). */
	static final Pattern TREATED = Pattern.compile("You treat the .+ with (ultra|super|)compost\\.");

	private final Client client;
	private final SettingsStore settings;

	/** Uses left, or -1 if not known. */
	private volatile int uses = -1;
	/**
	 * What the player last used on an object: true for the bottomless bucket, false for something else, null
	 * if not seen. Decides whose compost the next "You treat..." message was.
	 */
	private Boolean lastUseWasBucket;
	/** What it holds, or null if not known. */
	private volatile Compost compost;

	@Inject
	BottomlessBucketTracker(Client client, SettingsStore settings)
	{
		this.client = client;
		this.settings = settings;
	}

	/** Read the saved values for the current account. Call after settings load. */
	public void reload()
	{
		final AccountSettings account = settings.getAccount();
		uses = account.getBottomlessUses() != null ? account.getBottomlessUses() : -1;
		compost = account.getBottomlessCompost();
	}

	public int getUses()
	{
		return uses;
	}

	public Compost getCompost()
	{
		return compost;
	}

	/**
	 * @return whether the uses or type changed
	 */
	public boolean onChatMessage(String message)
	{
		Matcher m = CHECK.matcher(message);
		if (m.matches())
		{
			return record(number(m.group(1)), Compost.fromName(m.group(2)));
		}
		if (EMPTY.matcher(message).matches())
		{
			return record(0, null);
		}
		m = FILL_TYPE.matcher(message);
		if (m.matches())
		{
			return record(uses, Compost.fromName(m.group(1)));
		}
		m = FILL_TOTAL.matcher(message);
		if (m.matches())
		{
			return record(number(m.group(1)), compost);
		}
		m = TREATED.matcher(message);
		if (m.matches())
		{
			final Boolean clicked = lastUseWasBucket;
			lastUseWasBucket = null;
			final boolean bucketUsed = clicked != null ? clicked : usedTheBucket(Compost.fromName(m.group(1) + "compost"));
			if (bucketUsed && uses > 0)
			{
				return record(uses - 1, compost);
			}
		}
		return false;
	}

	/** Remember which item was used on an object, so the compost message can be put down to the right one. */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			return;
		}
		final Widget selected = client.getSelectedWidget();
		lastUseWasBucket = selected != null && selected.getItemId() == ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
	}

	/**
	 * The bucket turned into the empty one (used up or emptied): no uses left.
	 *
	 * @return whether the uses changed
	 */
	public boolean onInventoryChanged(ItemContainer inventory)
	{
		if (uses != 0 && inventory.contains(ItemID.BOTTOMLESS_COMPOST_BUCKET)
			&& !inventory.contains(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED))
		{
			return record(0, null);
		}
		return false;
	}

	/**
	 * A bucket stored at the tool leprechaun shows its uses in varbits; keep them for when it's taken out.
	 *
	 * @return whether the uses or type changed
	 */
	public boolean onVarbitChanged(int varbitId)
	{
		if (varbitId != VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE
			&& varbitId != VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY)
		{
			return false;
		}
		final int type = client.getVarbitValue(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
		final int quantity = client.getVarbitValue(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY);
		if (type <= 0 || quantity <= 0)
		{
			// Taken out (or never stored): the bucket keeps its uses
			return false;
		}
		final Compost stored = Compost.fromBucketType(type);
		return record(quantity, stored != null ? stored : compost);
	}

	/**
	 * Treated with the bucket rather than an ordinary bucket of compost: a filled bucket is carried and no
	 * ordinary bucket of that compost is. A guess when both are carried; the next Check corrects it.
	 */
	private boolean usedTheBucket(Compost used)
	{
		final ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null || !inventory.contains(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED))
		{
			return false;
		}
		return used == null || !inventory.contains(used.getItemId());
	}

	private boolean record(int newUses, Compost newCompost)
	{
		if (newUses == uses && newCompost == compost)
		{
			return false;
		}
		uses = newUses;
		compost = newCompost;
		log.debug("Bottomless compost bucket: {} uses of {}", newUses, newCompost);
		final Integer savedUses = newUses >= 0 ? newUses : null;
		SwingUtilities.invokeLater(() ->
		{
			settings.getAccount().setBottomlessUses(savedUses);
			settings.getAccount().setBottomlessCompost(newCompost);
			settings.saveAccount(false);
		});
		return true;
	}

	static int number(String text)
	{
		return Integer.parseInt(text.replace(",", ""));
	}
}
