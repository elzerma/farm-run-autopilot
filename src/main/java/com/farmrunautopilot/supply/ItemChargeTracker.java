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
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;

/**
 * Charges of teleport items the player charges themselves, which the game only shows in chat: the quetzal
 * whistle, Xeric's talisman and the pendant of Ates. Set from their Check and charging messages (wiki
 * transcripts), and counted down by one on each teleport click; a cancelled teleport makes it drift until the
 * next Check. Saved per account. Kharedst's memoirs / Book of the dead have no known messages, so aren't
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
	static final Pattern WHISTLE_CHECK = Pattern.compile("Your quetzal whistle has " + COUNT + " charges? remaining\\.");
	static final Pattern TALISMAN_CHECK = Pattern.compile("The talisman has " + COUNT + " charges?\\.");
	static final Pattern TALISMAN_CHARGED = Pattern.compile("Your talisman now has ([\\d,]+) charges?\\.");
	static final Pattern PENDANT_CHECK = Pattern.compile("The pendant has " + COUNT + " charges?\\.");
	static final Pattern PENDANT_CHARGED = Pattern.compile(".*It now has ([\\d,]+) charges?\\.");
	private static final int FULL = 1000;
	/** Menu options on these items that aren't a teleport. */
	private static final Set<String> NOT_TELEPORTS = new HashSet<>(Arrays.asList("Check", "Wear", "Wield", "Remove",
		"Drop", "Examine", "Use", "Destroy", "Uncharge", "Charge", "Cancel"));

	private final SettingsStore settings;
	private volatile Map<TravelItem, Integer> charges = Collections.emptyMap();

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
	 * A teleport chosen from one of these items uses a charge.
	 *
	 * @return whether a charge count changed
	 */
	public boolean onMenuOptionClicked(MenuOptionClicked event)
	{
		final int itemId = event.getItemId();
		if (itemId <= 0 || NOT_TELEPORTS.contains(Text.removeTags(event.getMenuOption())))
		{
			return false;
		}
		for (TravelItem item : TRACKED)
		{
			final Integer left = charges.get(item);
			if (left != null && left > 0 && Arrays.stream(item.getItemIds()).anyMatch(id -> id == itemId))
			{
				return record(item, left - 1);
			}
		}
		return false;
	}

	static int count(String text)
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
