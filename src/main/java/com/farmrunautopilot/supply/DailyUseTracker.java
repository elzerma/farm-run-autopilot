package com.farmrunautopilot.supply;

import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.SettingsStore;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.util.Text;

/**
 * Today's uses of the teleports limited per day, from the game's own messages, both seen in elzerma's runs:
 * "You have used 3 of your 3 Ardougne Farm teleports for today." after each one, and "You have already used all of
 * your available teleports for today. Try again tomorrow when the cape has recharged." when none are left. The game
 * value read for the Ardougne cloak ({@link com.farmrunautopilot.data.travel.DailyLimits}) left a used-up cloak
 * planned (2026-10-18), so the higher of the two counts. Saved per account with the day it was for: the game's day
 * starts at midnight UTC.
 *
 * <p>Client thread, except that settings are written on the Swing thread.
 */
@Slf4j
@Singleton
public class DailyUseTracker
{
	static final Pattern USED = Pattern.compile("You have used (\\d+) of your (\\d+) (.+) teleports for today\\.");
	static final Pattern ALL_USED = Pattern.compile("You have already used all of your available teleports for today\\..*");
	/** Counted when the game says none are left: more than any item allows. */
	static final int ALL = 99;
	/** The place each message names, as in "Ardougne Farm teleports". */
	private static final Map<String, TravelMethod> BY_PLACE = Map.of(
		"Ardougne Farm", TravelMethod.ARDOUGNE_CLOAK_FARM,
		"Cabbage", TravelMethod.EXPLORERS_RING_CABBAGE_PATCH);

	private final Client client;
	private final SettingsStore settings;
	private volatile Map<TravelMethod, Long> uses = Collections.emptyMap();
	/** A teleport the game just refused for having no uses left today, until it's taken; null if none. */
	private TravelMethod refused;
	/**
	 * The game variables as they were when an Ardougne cloak's farm teleport was clicked, to log which change with the
	 * use (where the cloak's count keeps its high bits isn't known); null otherwise.
	 */
	private int[] varpsBefore;
	/** Ticks until those are compared with the game's now, once the use is confirmed; 0 when not waiting. */
	private int compareIn;

	@Inject
	DailyUseTracker(Client client, SettingsStore settings)
	{
		this.client = client;
		this.settings = settings;
	}

	/** An Ardougne cloak's farm teleport clicked: keep the game variables as they are, to compare after. */
	public void onMenuOptionClicked(String option, String target)
	{
		if (option != null && target != null && option.contains("Farm") && Text.removeTags(target).contains("Ardougne"))
		{
			varpsBefore = client.getVarps().clone();
		}
	}

	/** A tick after the use is confirmed: the dev log lists the game variables that changed with it. */
	public void onGameTick()
	{
		if (compareIn <= 0 || --compareIn > 0)
		{
			return;
		}
		final int[] before = varpsBefore;
		varpsBefore = null;
		if (before == null)
		{
			return;
		}
		final int[] now = client.getVarps();
		final StringBuilder changed = new StringBuilder();
		for (int i = 0; i < Math.min(before.length, now.length); i++)
		{
			if (before[i] != now[i])
			{
				changed.append(changed.length() == 0 ? "" : ", ").append("varp ").append(i).append(": ").append(before[i])
					.append(" -> ").append(now[i]).append(" (bits ").append(Integer.toBinaryString(before[i] ^ now[i]))
					.append(')');
			}
		}
		log.debug("Ardougne Farm teleport, game variables changed: {}", changed.length() == 0 ? "none" : changed);
	}

	/** Read the saved counts for the current account. Call after settings load. */
	public void reload()
	{
		final Map<TravelMethod, Long> saved = settings.getAccount().getDailyUses();
		uses = Collections.unmodifiableMap(saved == null ? new EnumMap<>(TravelMethod.class) : new EnumMap<>(saved));
	}

	/** Today's uses of each, as the game last said; teleports it hasn't mentioned today aren't here. */
	public Map<TravelMethod, Integer> usedToday()
	{
		final long today = today();
		final Map<TravelMethod, Integer> used = new EnumMap<>(TravelMethod.class);
		uses.forEach((method, value) ->
		{
			if (value / 1000 == today)
			{
				used.put(method, (int) (value % 1000));
			}
		});
		return used;
	}

	/**
	 * @return whether a count changed
	 */
	public boolean onChatMessage(String message)
	{
		final Matcher m = USED.matcher(message);
		if (m.matches())
		{
			final TravelMethod method = BY_PLACE.get(m.group(3));
			if (method == TravelMethod.ARDOUGNE_CLOAK_FARM && varpsBefore != null)
			{
				log.debug("Ardougne Farm teleport {} of {}", m.group(1), m.group(2));
				compareIn = 2;
			}
			return method != null && record(method, Integer.parseInt(m.group(1)));
		}
		final TravelMethod method = refusedBy(message);
		if (method != null)
		{
			refused = method;
			record(method, ALL);
			return true;
		}
		return false;
	}

	/** The teleport the game just refused for having no uses left today, once; null if none. */
	public TravelMethod takeRefused()
	{
		final TravelMethod method = refused;
		refused = null;
		return method;
	}

	/**
	 * The teleport a "no uses left today" message is about: the Ardougne cloak's ("...when the cape has recharged").
	 * The Explorer's ring's wording isn't known; its "You have used 3 of your 3" message counts it all the same.
	 */
	static TravelMethod refusedBy(String message)
	{
		return ALL_USED.matcher(message).matches() && message.contains("cape") ? TravelMethod.ARDOUGNE_CLOAK_FARM : null;
	}

	/** The game's day now: days since 1970 in UTC, as its daily reset is at midnight UTC. */
	static long today()
	{
		return LocalDate.now(ZoneOffset.UTC).toEpochDay();
	}

	private boolean record(TravelMethod method, int used)
	{
		final long value = today() * 1000 + used;
		if (Long.valueOf(value).equals(uses.get(method)))
		{
			return false;
		}
		final Map<TravelMethod, Long> next = new EnumMap<>(TravelMethod.class);
		next.putAll(uses);
		next.put(method, value);
		uses = Collections.unmodifiableMap(next);
		log.debug("{} used today: {}", method, used == ALL ? "all" : used);
		final Map<TravelMethod, Long> saved = new EnumMap<>(next);
		SwingUtilities.invokeLater(() ->
		{
			settings.editAccount().setDailyUses(saved);
			settings.saveAccount(false);
		});
		return true;
	}
}
