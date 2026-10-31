package com.farmrunautopilot.bank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Where everything in the seed vault goes with a Farm run section at the top (issue #8), as plain numbers so it can
 * be tested. It's the grid the game's own seed_vault_build proc (2820) lays the vault out on, at desktop sizes: one
 * scrolling list of categories, each a header, a line and rows of items. The section comes first, with a header and
 * line like the categories' and taller rows that fit the bank tab's "/ N" label under each icon, and the categories
 * follow it without the items moved into it. Positions are within the vault's main list
 * ({@code InterfaceID.SeedVault.MAIN_VAULT}).
 */
final class SeedVaultLayout
{
	/** Each item takes 50 across, plus a 15 gap. */
	static final int CELL_WIDTH = 65;
	/** Each row of items: 20, the 32 high icon and its 30 high name. */
	static final int ROW_HEIGHT = 82;
	static final int FIRST_HEADER_Y = 5;
	/** A header is 20 high, with its line 2 below it. */
	static final int HEADER_TO_LINE = 22;
	static final int LINE_TO_ITEMS = 15;
	/** The 36 wide icon is centred in its 50 wide cell, after half the gap. */
	static final int ICON_X = 14;
	/** The name is as wide as the cell, after half the gap. */
	static final int NAME_X = 7;
	/** The name sits under the 32 high icon. */
	static final int NAME_BELOW = 32;
	static final int NAME_HEIGHT = 30;

	/** The section's header and line are the vault's own, so its items start as far below the header as theirs. */
	static final int SECTION_ITEMS_Y = HEADER_TO_LINE + LINE_TO_ITEMS;
	/** The "/ N" label under each of the section's icons, as on the bank tab. */
	static final int COUNTER_BELOW = 33;
	/** The name goes under the label. */
	static final int SECTION_NAME_BELOW = 48;
	/** A row of the section: the icon, its label and its name, then the vault's 20 gap. */
	static final int SECTION_ROW_HEIGHT = SECTION_NAME_BELOW + NAME_HEIGHT + 20;
	/** The vault's first category starts this far below the section's last names, as its categories do. */
	static final int AFTER_SECTION = 5;

	private SeedVaultLayout()
	{
	}

	/** One item: its icon and its name share a child index (the vault slot). */
	static final class Entry
	{
		final int index;
		final int iconX;
		final int iconY;
		final int nameX;
		final int nameY;

		Entry(int index, int iconX, int iconY, int nameX, int nameY)
		{
			this.index = index;
			this.iconX = iconX;
			this.iconY = iconY;
			this.nameX = nameX;
			this.nameY = nameY;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Entry))
			{
				return false;
			}
			final Entry e = (Entry) o;
			return index == e.index && iconX == e.iconX && iconY == e.iconY && nameX == e.nameX && nameY == e.nameY;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(index, iconX, iconY, nameX, nameY);
		}

		@Override
		public String toString()
		{
			return "item " + index + " at " + iconX + "," + iconY + " (name " + nameX + "," + nameY + ")";
		}
	}

	/** Where the section, the headers and the items go. */
	static final class Placement
	{
		/** The section's header (its line is {@link #HEADER_TO_LINE} below), or -1 without a section. */
		final int sectionY;
		/** How far the section pushes the categories down; 0 without one. */
		final int sectionHeight;
		/**
		 * The section's items in order: where each icon and name goes, with the vault's child index, or -1 for one
		 * the vault doesn't hold. The "/ N" label is {@link #COUNTER_BELOW} under the icon.
		 */
		final List<Entry> cells;
		/** Each category's header (its line is {@link #HEADER_TO_LINE} below). */
		final int[] headerY;
		/** Every vault item's place, by child index. */
		final Map<Integer, Entry> entries;
		/** Where the game measures the end of the list from: the row after the last category's last full row. */
		final int bottom;

		Placement(int sectionY, int sectionHeight, List<Entry> cells, int[] headerY, Map<Integer, Entry> entries,
			int bottom)
		{
			this.sectionY = sectionY;
			this.sectionHeight = sectionHeight;
			this.cells = cells;
			this.headerY = headerY;
			this.entries = entries;
			this.bottom = bottom;
		}
	}

	/** Items per row in a list this wide. */
	static int perRow(int width)
	{
		return Math.max(1, width / CELL_WIDTH);
	}

	/** Rows for this many items; an empty category keeps one, as in the game. */
	static int rows(int items, int perRow)
	{
		return Math.max(1, (items + perRow - 1) / perRow);
	}

	/**
	 * Each category's items (child indices) in the order shown, going by where the game put them: an item belongs
	 * to the last header above it. Null if one is above the first header.
	 */
	static List<List<Integer>> categories(int[] headerY, List<Entry> entries)
	{
		final List<List<Entry>> found = new ArrayList<>();
		for (int i = 0; i < headerY.length; i++)
		{
			found.add(new ArrayList<>());
		}
		for (Entry entry : entries)
		{
			int category = -1;
			for (int i = 0; i < headerY.length; i++)
			{
				if (headerY[i] <= entry.iconY)
				{
					category = i;
				}
			}
			if (category < 0)
			{
				return null;
			}
			found.get(category).add(entry);
		}
		final List<List<Integer>> categories = new ArrayList<>();
		for (List<Entry> items : found)
		{
			items.sort(Comparator.comparingInt((Entry e) -> e.iconY).thenComparingInt(e -> e.iconX));
			final List<Integer> indices = new ArrayList<>();
			for (Entry entry : items)
			{
				indices.add(entry.index);
			}
			categories.add(indices);
		}
		return categories;
	}

	/**
	 * What isn't where the game's own layout puts it, e.g. "header 2 at 417, not 412", or null when everything is.
	 * Nothing is moved unless this is null, so a game update that changes the vault leaves it as the game made it.
	 */
	static String mismatch(int width, int[] headerY, int[] lineY, List<Entry> entries, List<List<Integer>> categories)
	{
		if (headerY.length == 0)
		{
			return "no category headers";
		}
		if (lineY.length != headerY.length)
		{
			return headerY.length + " headers but " + lineY.length + " lines";
		}
		if (categories == null)
		{
			return "an item above the first header";
		}
		final Placement game = place(width, categories, Collections.emptyList());
		for (int i = 0; i < headerY.length; i++)
		{
			if (headerY[i] != game.headerY[i])
			{
				return "header " + i + " at " + headerY[i] + ", not " + game.headerY[i];
			}
			if (lineY[i] != headerY[i] + HEADER_TO_LINE)
			{
				return "line " + i + " at " + lineY[i] + ", not " + (headerY[i] + HEADER_TO_LINE);
			}
		}
		for (Entry entry : entries)
		{
			final Entry expected = game.entries.get(entry.index);
			if (!entry.equals(expected))
			{
				return entry + ", not " + (expected == null ? "anywhere" : expected.iconX + "," + expected.iconY
					+ " (name " + expected.nameX + "," + expected.nameY + ")");
			}
		}
		return null;
	}

	/**
	 * The layout with a section at the top holding these items in order: the vault's child index of each, or -1 for
	 * one the vault doesn't hold (shown faded in its place). Each category closes up where its items were. With no
	 * section it's the game's own layout.
	 */
	static Placement place(int width, List<List<Integer>> categories, List<Integer> section)
	{
		return place(width, categories, section, 0);
	}

	/**
	 * As above, with a gap {@code gap} high after the section, for the Farming Contract's own section to sit in when this
	 * one goes above it; none without a section. The bottom includes it.
	 */
	static Placement place(int width, List<List<Integer>> categories, List<Integer> section, int gap)
	{
		final int perRow = perRow(width);
		final Map<Integer, Entry> entries = new HashMap<>();
		final List<Entry> cells = new ArrayList<>();
		final Set<Integer> moved = new HashSet<>();
		int y = FIRST_HEADER_Y;
		int sectionY = -1;
		int sectionHeight = 0;
		if (!section.isEmpty())
		{
			sectionY = y;
			final int top = y + SECTION_ITEMS_Y;
			for (int i = 0; i < section.size(); i++)
			{
				final int x = (i % perRow) * CELL_WIDTH;
				final int iconY = top + (i / perRow) * SECTION_ROW_HEIGHT;
				final Entry cell = new Entry(section.get(i), x + ICON_X, iconY, x + NAME_X, iconY + SECTION_NAME_BELOW);
				cells.add(cell);
				if (cell.index >= 0)
				{
					entries.put(cell.index, cell);
					moved.add(cell.index);
				}
			}
			final int lastRow = (rows(section.size(), perRow) - 1) * SECTION_ROW_HEIGHT;
			sectionHeight = SECTION_ITEMS_Y + lastRow + SECTION_NAME_BELOW + NAME_HEIGHT + AFTER_SECTION;
			y += sectionHeight + gap;
		}
		final int[] headerY = new int[categories.size()];
		int bottom = y;
		for (int i = 0; i < categories.size(); i++)
		{
			final List<Integer> items = new ArrayList<>();
			for (int index : categories.get(i))
			{
				if (!moved.contains(index))
				{
					items.add(index);
				}
			}
			headerY[i] = y;
			placeRows(items, y, perRow, entries);
			// The game starts a new row after every full one, so a full last row leaves an empty one below it
			bottom = itemsTop(y) + (items.size() / perRow + 1) * ROW_HEIGHT;
			y += HEADER_TO_LINE + rows(items.size(), perRow) * ROW_HEIGHT;
		}
		return new Placement(sectionY, sectionHeight, cells, headerY, entries, bottom);
	}

	private static int itemsTop(int headerY)
	{
		return headerY + HEADER_TO_LINE + LINE_TO_ITEMS;
	}

	/** Items in rows under the header at {@code headerY}. */
	private static void placeRows(List<Integer> items, int headerY, int perRow, Map<Integer, Entry> into)
	{
		final int top = itemsTop(headerY);
		for (int i = 0; i < items.size(); i++)
		{
			final int x = (i % perRow) * CELL_WIDTH;
			final int y = top + (i / perRow) * ROW_HEIGHT;
			into.put(items.get(i), new Entry(items.get(i), x + ICON_X, y, x + NAME_X, y + NAME_BELOW));
		}
	}

	/**
	 * The list's scroll height once the bottom of the layout moves from where the game had it: the game's own, grown
	 * by as much, or 0 (no scrolling, as the game does) when everything fits.
	 *
	 * @param gameScroll the scroll height the game set, 0 if everything fitted
	 * @param top where the main list starts in the scrolling list: below the Farming Contract section, if any
	 * @param visible the scrolling list's height on screen
	 */
	static int scrollHeight(int gameScroll, int top, int gameBottom, int bottom, int visible)
	{
		final int content = (gameScroll > 0 ? gameScroll : top + gameBottom) + bottom - gameBottom;
		return content > visible ? content : 0;
	}

	/**
	 * Where to scroll to once the section's height changes, so the same part of the vault stays in view; the top
	 * when the section first shows during a visit to the vault.
	 */
	static int scrollY(int scrollY, int oldSectionHeight, int sectionHeight, boolean firstShown, int scrollHeight,
		int visible)
	{
		final int y = firstShown ? 0 : scrollY + sectionHeight - oldSectionHeight;
		return Math.max(0, Math.min(y, scrollHeight - visible));
	}
}
