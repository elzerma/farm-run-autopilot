package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.data.travel.JourneyStep;
import com.farmrunautopilot.data.travel.Spell;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Colours the option a leg needs in its teleport item's right-click menu (elzerma, 2026-10-18: "Jewelery and item
 * right click menus should also highlight option that is needed"), e.g. "Crafting Guild" on a worn skills necklace,
 * or in its spell's in the spellbook: a spell's other place ("Seers'" on Camelot Teleport) and Teleport to House's
 * Inside or Outside (elzerma, 2026-10-23).
 * When the item's places are behind "Rub" (or "Teleport", "Reminisce"...), that option instead; TravelMenuOverlay then
 * rings the place in the list. Colour only, no frame round the option (elzerma, 2026-10-20: "just change the text
 * color"). Only colours options the menu has: adds none, removes none. Client thread.
 */
@Slf4j
@Singleton
public class TeleportMenuHighlighter
{
	/** Options that open an item's list of places. */
	private static final Set<String> OPENERS = Set.of("rub", "teleport", "teleports", "reminisce", "commune", "travel");
	/** Stands for the spell's own menu entries, which have no item: those whose target is the spell's name. */
	static final int SPELL_ENTRY = -2;

	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final ItemManager itemManager;
	/**
	 * The teleport items' names, for the items they were worked out for, as the item highlight asks every frame while
	 * the mouse is over the teleport.
	 */
	private Set<Integer> namedIds;
	private Set<String> names;
	/** The teleport items with {@link #SPELL_ENTRY}, for the items they were made for. */
	private Set<Integer> spellIdsFor;
	private Set<Integer> spellIds;

	/** Where the option a leg needs is in a menu: its entry, and the entry opening the sub-menu it's in (or -1). */
	static final class Found
	{
		static final Found NONE = new Found(-1, -1);

		final int index;
		final int parent;

		Found(int index, int parent)
		{
			this.index = index;
			this.parent = parent;
		}

		boolean isFound()
		{
			return index >= 0;
		}
	}

	@Inject
	TeleportMenuHighlighter(RunSession session, FarmRunAutopilotConfig config, ItemManager itemManager)
	{
		this.session = session;
		this.config = config;
		this.itemManager = itemManager;
	}

	public void onMenuOpened(MenuOpened event)
	{
		if (!config.highlightItems())
		{
			return;
		}
		final MenuEntry[] entries = event.getMenuEntries();
		colourRide(entries);
		final String option = session.getView().getHighlights().getTeleportOption();
		if (option == null)
		{
			colourOpener(entries);
			return;
		}
		final Found found = find(entries);
		if (!found.isFound())
		{
			if (teleportItem(entries))
			{
				log.debug("Menu option for '{}' not found in: {}", option, optionList(entries));
			}
			return;
		}
		if (found.parent >= 0)
		{
			// In a sub-menu: the entry opening it as well, as that's the one to hover first
			colour(entries[found.parent]);
			colour(entries[found.parent].getSubMenu().getMenuEntries()[found.index]);
			return;
		}
		colour(entries[found.index]);
	}

	/**
	 * The ride in hand's option on its boat, NPC or object, e.g. "Dive" on the rowboat or "Quick-Travel" on the barge
	 * guard (elzerma, 2026-10-24), matched by option and, where the atlas names one, target.
	 */
	private void colourRide(MenuEntry[] entries)
	{
		final JourneyStep ride = session.currentRide();
		if (ride == null || ride.getOption() == null || entries == null)
		{
			return;
		}
		for (MenuEntry entry : entries)
		{
			final String option = Text.removeTags(entry.getOption());
			final String target = entry.getTarget() == null ? "" : Text.removeTags(entry.getTarget());
			if (option.equalsIgnoreCase(ride.getOption())
				&& (ride.getNpc() == null || target.equalsIgnoreCase(ride.getNpc())))
			{
				colour(entry);
			}
		}
	}

	/**
	 * A teleport item with one teleport and no place to pick (the Skull sceptre's "Teleport", elzerma, 2026-10-24):
	 * the option that teleports, on the step's teleport item, while the step is to teleport.
	 */
	private void colourOpener(MenuEntry[] entries)
	{
		final Highlights highlights = session.getView().getHighlights();
		if (entries == null || highlights.getItemIds().isEmpty() || highlights.getSpell() != null
			|| highlights.getTravelNpc() != null)
		{
			return;
		}
		for (MenuEntry entry : entries)
		{
			// A worn item (the Chronicle in the shield slot, elzerma, 2026-10-25) by its name, as its options may not carry
			// its item id
			final boolean item = highlights.getItemIds().contains(entry.getItemId()) || entry.getTarget() != null
				&& names(highlights.getItemIds()).contains(Text.removeTags(entry.getTarget()).toLowerCase(Locale.ROOT));
			if (item && OPENERS.contains(Text.removeTags(entry.getOption()).toLowerCase(Locale.ROOT)))
			{
				colour(entry);
			}
		}
	}

	private void colour(MenuEntry entry)
	{
		final String text = Text.removeTags(entry.getOption());
		entry.setOption(ColorUtil.wrapWithColorTag(text, config.itemColour()));
		log.debug("Menu option coloured: {}", text);
	}

	/**
	 * The option the leg needs in these menu entries (a menu's top level), looking in their sub-menus too: an option
	 * naming the place anywhere, or else the one opening the item's list of places. NONE when the step has no teleport
	 * option or the menu isn't the teleport item's or spell's (a spell's other place, e.g. "Seers'" on Camelot Teleport,
	 * or Teleport to House's Outside, is on its right-click in the spellbook).
	 */
	Found find(MenuEntry[] entries)
	{
		final Highlights highlights = session.getView().getHighlights();
		final String option = highlights.getTeleportOption();
		if (option == null || entries == null)
		{
			return Found.NONE;
		}
		final int top = pick(entries, highlights, option);
		if (top >= 0 && names(entries[top].getOption(), option))
		{
			return new Found(top, -1);
		}
		// A sub-menu's places: an inventory ring of dueling's are under Rub, there as the menu opens (elzerma, 2026-10-21)
		for (int i = 0; i < entries.length; i++)
		{
			final Menu sub = entries[i].getSubMenu();
			final MenuEntry[] subEntries = sub != null ? sub.getMenuEntries() : null;
			if (subEntries == null || subEntries.length == 0)
			{
				continue;
			}
			final int pick = pick(subEntries, highlights, option);
			if (pick >= 0 && names(subEntries[pick].getOption(), option))
			{
				return new Found(pick, i);
			}
		}
		return top >= 0 ? new Found(top, -1) : Found.NONE;
	}

	/**
	 * {@link #pick(String[], int[], Set, String)} on menu entries: the step's teleport items' entries, knowing a worn
	 * item by its name, and the spell's own entries ({@link #SPELL_ENTRY}) by theirs.
	 */
	private int pick(MenuEntry[] entries, Highlights highlights, String option)
	{
		final Set<Integer> itemIds = highlights.getItemIds();
		final Spell spell = highlights.getSpell();
		final String[] options = new String[entries.length];
		final int[] items = new int[entries.length];
		for (int i = 0; i < entries.length; i++)
		{
			options[i] = entries[i].getOption();
			items[i] = entries[i].getItemId();
			if (!itemIds.contains(items[i]) && entries[i].getTarget() != null)
			{
				final String target = Text.removeTags(entries[i].getTarget()).toLowerCase(Locale.ROOT);
				if (spell != null && target.equals(spell.getDisplayName().toLowerCase(Locale.ROOT)))
				{
					// In the spellbook: "Seers'" on Camelot Teleport, "Outside" on Teleport to House
					items[i] = SPELL_ENTRY;
				}
				else if (!itemIds.isEmpty() && names(itemIds).contains(target))
				{
					// A worn item's options may not carry its item id; its name, in the target, does
					items[i] = itemIds.iterator().next();
				}
			}
		}
		return pick(options, items, spell != null ? withSpell(itemIds) : itemIds, option);
	}

	/** The teleport items and {@link #SPELL_ENTRY}, kept while the items stay the same. */
	private Set<Integer> withSpell(Set<Integer> itemIds)
	{
		if (itemIds != spellIdsFor)
		{
			final Set<Integer> ids = new HashSet<>(itemIds);
			ids.add(SPELL_ENTRY);
			spellIds = ids;
			spellIdsFor = itemIds;
		}
		return spellIds;
	}

	private boolean teleportItem(MenuEntry[] entries)
	{
		final Set<Integer> itemIds = session.getView().getHighlights().getItemIds();
		for (MenuEntry entry : entries)
		{
			if (itemIds.contains(entry.getItemId()))
			{
				return true;
			}
		}
		return false;
	}

	private static String optionList(MenuEntry[] entries)
	{
		final StringBuilder s = new StringBuilder();
		for (MenuEntry entry : entries)
		{
			s.append(s.length() > 0 ? ", " : "").append(Text.removeTags(entry.getOption()));
		}
		return s.toString();
	}

	/** The items' names, lower case, as a menu's target shows them; kept while the items stay the same. */
	private Set<String> names(Set<Integer> itemIds)
	{
		if (itemIds != namedIds)
		{
			final Set<String> found = new HashSet<>();
			for (int id : itemIds)
			{
				found.add(itemManager.getItemComposition(id).getName().toLowerCase(Locale.ROOT));
			}
			names = found;
			namedIds = itemIds;
		}
		return names;
	}

	/** Whether a menu option names the place, e.g. "Ardougne Farm" for "farm". */
	static boolean names(String menuOption, String place)
	{
		return menuOption != null
			&& Text.removeTags(menuOption).toLowerCase(Locale.ROOT).contains(place.toLowerCase(Locale.ROOT));
	}

	/**
	 * Which of a menu's options to colour (its index), or -1: the teleport item's option naming the place (e.g.
	 * "Ardougne Farm" for "farm"), or else the one that opens its list of places.
	 *
	 * @param options each option's text
	 * @param items each option's item id
	 */
	static int pick(String[] options, int[] items, Set<Integer> teleportIds, String option)
	{
		final String place = option.toLowerCase(Locale.ROOT);
		// The place's last word with "Teleport", as the Ardougne cloak words its places: "Monastery Teleport" for Kandarin
		// Monastery, "Farm Teleport" for Ardougne Farm (elzerma, 2026-10-25)
		final String teleport = place.substring(place.lastIndexOf(' ') + 1) + " teleport";
		int opener = -1;
		int worded = -1;
		for (int i = 0; i < options.length; i++)
		{
			if (!teleportIds.contains(items[i]) || options[i] == null)
			{
				continue;
			}
			final String text = Text.removeTags(options[i]).toLowerCase(Locale.ROOT);
			if (text.contains(place))
			{
				return i;
			}
			if (worded < 0 && text.equals(teleport))
			{
				worded = i;
			}
			if (opener < 0 && OPENERS.contains(text))
			{
				opener = i;
			}
		}
		return worded >= 0 ? worded : opener;
	}

	/**
	 * Whether the leg's option needs a right-click in these menu entries, those of the item under the mouse: true when
	 * it isn't their left click (the top one, after any other plugin's swaps), false when it is, null when they don't
	 * have it.
	 */
	Boolean needsRightClick(MenuEntry[] entries)
	{
		final Found found = find(entries);
		if (!found.isFound())
		{
			return null;
		}
		return found.parent >= 0 || found.index != entries.length - 1;
	}

	/**
	 * Whether the item's option the leg needs isn't its left-click one, going by the item's own options (not another
	 * plugin's swaps): always on a worn item, whose left click is Remove; in the inventory when the option, or the one
	 * opening the list of places, comes after the first the item has. False when the item has neither.
	 *
	 * @param inventoryActions the item's options in the inventory, as {@code ItemComposition} gives them
	 */
	static boolean needsRightClick(String[] inventoryActions, boolean worn, String option)
	{
		if (worn)
		{
			return true;
		}
		if (inventoryActions == null)
		{
			return false;
		}
		final int[] items = new int[inventoryActions.length];
		final int pick = pick(inventoryActions, items, Set.of(0), option);
		if (pick < 0)
		{
			return false;
		}
		// The left click is the item's first of its first three options, else "Use", which comes before the other two
		if (pick >= 3)
		{
			return true;
		}
		for (int i = 0; i < pick; i++)
		{
			if (inventoryActions[i] != null)
			{
				return true;
			}
		}
		return false;
	}
}
