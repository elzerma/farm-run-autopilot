package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.settings.SettingsStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Text;

/**
 * Charges of teleport items the player charges themselves, which the game only shows in chat: the quetzal
 * whistle, Xeric's talisman and the pendant of Ates. Set from their Check and charging messages (wiki
 * transcripts), and counted down by one when a teleport click is followed by the player actually being moved
 * (picking a destination can take a while; a cancelled teleport or a closed menu uses nothing). Saved per account. Kharedst's memoirs / Book of the dead have no known messages, so aren't
 * tracked.
 *
 * <p>Client thread, except that settings are written on the Swing thread.
 */
@Slf4j
@Singleton
public class ItemChargeTracker
{
	/** Items whose charges are kept here. */
	public static final Set<TravelItem> TRACKED = Collections.unmodifiableSet(EnumSet.of(
		TravelItem.QUETZAL_WHISTLE, TravelItem.XERICS_TALISMAN, TravelItem.PENDANT_OF_ATES));
	private static final String COUNT = "(no|one|[\\d,]+)";
	/** Confirmed in game. */
	public static final Pattern WHISTLE_CHECK = Pattern.compile("Your quetzal whistle has " + COUNT + " charges? remaining\\.");
	public static final Pattern TALISMAN_CHECK = Pattern.compile("The talisman has " + COUNT + " charges?\\.");
	static final Pattern TALISMAN_CHARGED = Pattern.compile("Your talisman now has ([\\d,]+) charges?\\.");
	public static final Pattern PENDANT_CHECK = Pattern.compile("The pendant has " + COUNT + " charges?\\.");
	static final Pattern PENDANT_CHARGED = Pattern.compile(".*It now has ([\\d,]+) charges?\\.");
	private static final int FULL = 1000;
	/** A teleport click counts only if the player is moved within this many ticks (about 30 seconds). */
	private static final int PENDING_TICKS = 50;
	/** Moving further than this in one tick can only be a teleport. */
	private static final int TELEPORT_TILES = 30;
	/** Menu options on these items that aren't a teleport. */
	private static final Set<String> NOT_TELEPORTS = new HashSet<>(Arrays.asList("Check", "Wear", "Wield", "Remove",
		"Drop", "Examine", "Use", "Destroy", "Uncharge", "Charge", "Cancel"));

	private final SettingsStore settings;
	private volatile Map<TravelItem, Integer> charges = Collections.emptyMap();
	/** The item a teleport was just chosen from, waiting for the player to be moved; null if none. */
	private TravelItem pending;
	private int pendingTicks;
	private WorldPoint lastLocation;

	@Inject
	ItemChargeTracker(SettingsStore settings)
	{
		this.settings = settings;
	}

	/** Read the saved charges for the current account. Call after settings load. */
	public void reload()
	{
		charges = Collections.unmodifiableMap(new EnumMap<>(withDefaults(settings.getAccount().getItemCharges())));
	}

	private static Map<TravelItem, Integer> withDefaults(Map<TravelItem, Integer> saved)
	{
		final Map<TravelItem, Integer> map = new EnumMap<>(TravelItem.class);
		if (saved != null)
		{
			map.putAll(saved);
		}
		return map;
	}

	/** Known charges per item; items not here haven't been checked yet. */
	public Map<TravelItem, Integer> getCharges()
	{
		return charges;
	}

	/**
	 * @return whether a charge count changed
	 */
	public boolean onChatMessage(String message)
	{
		if (message.equals("The talisman is full."))
		{
			return record(TravelItem.XERICS_TALISMAN, FULL);
		}
		if (message.equals("The pendant is already fully charged."))
		{
			return record(TravelItem.PENDANT_OF_ATES, FULL);
		}
		Matcher m = WHISTLE_CHECK.matcher(message);
		if (m.matches())
		{
			return record(TravelItem.QUETZAL_WHISTLE, count(m.group(1)));
		}
		m = TALISMAN_CHECK.matcher(message);
		if (m.matches())
		{
			return record(TravelItem.XERICS_TALISMAN, count(m.group(1)));
		}
		m = TALISMAN_CHARGED.matcher(message);
		if (m.matches())
		{
			return record(TravelItem.XERICS_TALISMAN, count(m.group(1)));
		}
		m = PENDANT_CHECK.matcher(message);
		if (m.matches())
		{
			return record(TravelItem.PENDANT_OF_ATES, count(m.group(1)));
		}
		m = PENDANT_CHARGED.matcher(message);
		if (m.matches() && message.contains("pendant"))
		{
			return record(TravelItem.PENDANT_OF_ATES, count(m.group(1)));
		}
		return false;
	}

	/**
	 * The pendant of Ates keeps its charges in a varbit (UNVERIFIED: picked by its name), which is exact, so it
	 * replaces the chat and click counting for the pendant whenever it changes.
	 *
	 * @return whether a charge count changed
	 */
	public boolean onVarbitChanged(int varbitId, int value)
	{
		return varbitId == VarbitID.CHARGES_PENDANT_OF_ATES_QUANTITY && record(TravelItem.PENDANT_OF_ATES, value);
	}

	/**
	 * A teleport chosen from one of these items: it uses a charge once the player is moved (see
	 * {@link #onGameTick}).
	 *
	 * @return whether a charge count changed (never, now; kept for callers)
	 */
	public boolean onMenuOptionClicked(MenuOptionClicked event)
	{
		final int itemId = event.getItemId();
		final String option = Text.removeTags(event.getMenuOption());
		// Moving it in or out of the bank (Withdraw-1, Deposit-All...) isn't a teleport
		if (itemId <= 0 || NOT_TELEPORTS.contains(option) || option.startsWith("Withdraw")
			|| option.startsWith("Deposit"))
		{
			return false;
		}
		for (TravelItem item : TRACKED)
		{
			if (item == TravelItem.PENDANT_OF_ATES)
			{
				// Its varbit says when the charge is really used (a cancelled teleport uses none)
				continue;
			}
			final Integer left = charges.get(item);
			if (left != null && left > 0 && Arrays.stream(item.getItemIds()).anyMatch(id -> id == itemId))
			{
				pending = item;
				pendingTicks = 0;
				return false;
			}
		}
		return false;
	}

	/**
	 * Each game tick: a chosen teleport uses its charge when the player is moved, and is forgotten if they aren't
	 * within about 30 seconds.
	 *
	 * @return whether a charge count changed
	 */
	public boolean onGameTick(WorldPoint location)
	{
		final WorldPoint previous = lastLocation;
		lastLocation = location;
		if (pending == null)
		{
			return false;
		}
		final boolean moved = previous != null && location != null
			&& (previous.getPlane() != location.getPlane() || previous.distanceTo2D(location) > TELEPORT_TILES);
		if (moved)
		{
			final TravelItem used = pending;
			pending = null;
			final Integer left = charges.get(used);
			return left != null && left > 0 && record(used, left - 1);
		}
		if (++pendingTicks > PENDING_TICKS)
		{
			pending = null;
		}
		return false;
	}

	public static int count(String text)
	{
		if (text.equals("no"))
		{
			return 0;
		}
		return text.equals("one") ? 1 : Integer.parseInt(text.replace(",", ""));
	}

	private boolean record(TravelItem item, int value)
	{
		if (Integer.valueOf(value).equals(charges.get(item)))
		{
			return false;
		}
		final Map<TravelItem, Integer> next = new EnumMap<>(TravelItem.class);
		next.putAll(charges);
		next.put(item, value);
		charges = Collections.unmodifiableMap(next);
		log.debug("{} charges: {}", item, value);
		final Map<TravelItem, Integer> saved = new EnumMap<>(next);
		SwingUtilities.invokeLater(() ->
		{
			settings.getAccount().setItemCharges(saved);
			settings.saveAccount(false);
		});
		return true;
	}
}
