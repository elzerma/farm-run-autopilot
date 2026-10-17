package com.farmrunautopilot.bank;

import com.farmrunautopilot.bank.SeedVaultLayout.Entry;
import com.farmrunautopilot.bank.SeedVaultLayout.Placement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** Issue #8: the seed vault's layout read back from the game's, and the Farm run section put on top of it. */
public class SeedVaultLayoutTest
{
	/** The vault's list on desktop: 345 wide less its 16 wide scroll bar, so 5 items to a row. */
	private static final int WIDTH = 329;
	private static final int CATEGORIES = 13;
	private static final int ADD_FAVOURITES = 104;

	/** What the game's seed_vault_build proc makes when not searching. */
	private static final class GameVault
	{
		final int[] headerY;
		final int[] lineY;
		final List<Entry> entries;
		/** The proc's scroll height, less the Farming Contract section above the main list. */
		final int content;

		GameVault(int[] headerY, int[] lineY, List<Entry> entries, int content)
		{
			this.headerY = headerY;
			this.lineY = lineY;
			this.entries = entries;
			this.content = content;
		}
	}

	/**
	 * The proc's loops written out as they are in the game's script (desktop sizes): each category's header, line
	 * and rows, then each slot's icon and name at its category's next place, then "Add Favourites" (child 104) at
	 * the end of the favourites.
	 *
	 * @param slotCategory each vault slot's category, -1 for an empty one (favourites are category 0)
	 */
	private static GameVault game(int width, int categories, int[] slotCategory, int cellGap, int rowGap)
	{
		final int cell = 50;
		final int nameHeight = 30;
		final int[] count = new int[categories];
		for (int category : slotCategory)
		{
			if (category >= 0)
			{
				count[category]++;
			}
		}
		count[0]++;
		final int perRow = width / (cell + cellGap);
		final int[] headerY = new int[categories];
		final int[] lineY = new int[categories];
		final int[] rowY = new int[categories];
		int y = 5;
		for (int i = 0; i < categories; i++)
		{
			headerY[i] = y;
			y = y + 20 + 2;
			lineY[i] = y;
			rowY[i] = y + 15;
			y = y + (1 + (count[i] - 1) / perRow) * (rowGap + 32 + nameHeight);
		}
		final int[] x = new int[categories];
		final List<Entry> entries = new ArrayList<>();
		for (int slot = 0; slot < slotCategory.length; slot++)
		{
			final int category = slotCategory[slot];
			if (category < 0)
			{
				continue;
			}
			int at = x[category];
			final int top = rowY[category];
			entries.add(new Entry(slot, at + cellGap / 2 + (cell - 36) / 2, top, at + cellGap / 2, top + 32));
			at = at + cellGap + cell;
			if (at + cellGap + cell > width)
			{
				at = 0;
				rowY[category] = top + rowGap + 32 + nameHeight;
			}
			x[category] = at;
		}
		entries.add(new Entry(ADD_FAVOURITES, x[0] + cellGap / 2 + (cell - 36) / 2, rowY[0], x[0] + cellGap / 2,
			rowY[0] + 32));
		final int content = rowY[categories - 1] + rowGap + 32 + nameHeight;
		return new GameVault(headerY, lineY, entries, content);
	}

	private static GameVault game(int[] slotCategory)
	{
		return game(WIDTH, CATEGORIES, slotCategory, 15, 20);
	}

	/** A vault of 104 slots with these categories in the first slots, the rest empty. */
	private static int[] slots(int... categories)
	{
		final int[] slots = new int[104];
		Arrays.fill(slots, -1);
		System.arraycopy(categories, 0, slots, 0, categories.length);
		return slots;
	}

	private static int[] randomSlots(Random random)
	{
		final int[] slots = new int[104];
		for (int i = 0; i < slots.length; i++)
		{
			slots[i] = random.nextInt(3) == 0 ? -1 : random.nextInt(CATEGORIES);
		}
		return slots;
	}

	private static List<List<Integer>> recognised(GameVault vault)
	{
		final List<List<Integer>> categories = SeedVaultLayout.categories(vault.headerY, vault.entries);
		assertNull(SeedVaultLayout.mismatch(WIDTH, vault.headerY, vault.lineY, vault.entries, categories));
		return categories;
	}

	@Test
	public void fiveItemsToARow()
	{
		assertEquals(5, SeedVaultLayout.perRow(WIDTH));
		assertEquals(1, SeedVaultLayout.perRow(40));
		assertEquals(1, SeedVaultLayout.rows(0, 5));
		assertEquals(1, SeedVaultLayout.rows(5, 5));
		assertEquals(2, SeedVaultLayout.rows(6, 5));
	}

	@Test
	public void theGamesLayoutIsRecognisedAndRebuiltExactly()
	{
		final Random random = new Random(8);
		for (int run = 0; run < 200; run++)
		{
			final GameVault vault = game(randomSlots(random));
			final List<List<Integer>> categories = recognised(vault);
			final Placement same = SeedVaultLayout.place(WIDTH, categories, Collections.emptyList());
			assertArrayEquals(vault.headerY, same.headerY);
			for (Entry entry : vault.entries)
			{
				assertEquals(entry, same.entries.get(entry.index));
			}
			assertEquals(vault.entries.size(), same.entries.size());
			assertEquals(-1, same.sectionY);
			assertEquals(0, same.sectionHeight);
			// Measured as the game measures its scroll height, full last row and all
			assertEquals(vault.content, same.bottom);
		}
	}

	@Test
	public void itemsAreReadInTheOrderShown()
	{
		// Category 2 holds slots 3, 0 and 7, shown in slot order
		final GameVault vault = game(slots(2, 1, -1, 2, 1, -1, -1, 2));
		final List<List<Integer>> categories = recognised(vault);
		assertEquals(Collections.singletonList(ADD_FAVOURITES), categories.get(0));
		assertEquals(Arrays.asList(1, 4), categories.get(1));
		assertEquals(Arrays.asList(0, 3, 7), categories.get(2));
		assertEquals(Collections.emptyList(), categories.get(3));
	}

	@Test
	public void theSectionGoesOnTopAndTheRestIsTheGamesLayoutWithoutThem()
	{
		final Random random = new Random(16);
		for (int run = 0; run < 200; run++)
		{
			final int[] slots = randomSlots(random);
			final GameVault vault = game(slots);
			final List<List<Integer>> categories = recognised(vault);

			// Some of the vault's items, in the order the plan lists them
			final List<Integer> section = new ArrayList<>();
			for (int slot = 0; slot < slots.length && section.size() < 1 + run % 8; slot += 1 + random.nextInt(9))
			{
				if (slots[slot] >= 0)
				{
					section.add(0, slot);
				}
			}
			if (section.isEmpty())
			{
				continue;
			}
			final Placement placed = SeedVaultLayout.place(WIDTH, categories, section);

			// The section: a header at the top and the items in rows below it, in order
			assertEquals(SeedVaultLayout.FIRST_HEADER_Y, placed.sectionY);
			final int rows = SeedVaultLayout.rows(section.size(), 5);
			assertEquals(SeedVaultLayout.HEADER_TO_LINE + rows * SeedVaultLayout.ROW_HEIGHT, placed.sectionHeight);
			for (int i = 0; i < section.size(); i++)
			{
				final int x = (i % 5) * SeedVaultLayout.CELL_WIDTH;
				final int y = 42 + (i / 5) * SeedVaultLayout.ROW_HEIGHT;
				assertEquals(new Entry(section.get(i), x + 14, y, x + 7, y + 32), placed.entries.get(section.get(i)));
			}

			// Below it, the game's own layout of the vault without those items, pushed down by the section
			final int[] without = slots.clone();
			for (int slot : section)
			{
				without[slot] = -1;
			}
			final GameVault rest = game(without);
			for (int i = 0; i < CATEGORIES; i++)
			{
				assertEquals(rest.headerY[i] + placed.sectionHeight, placed.headerY[i]);
			}
			for (Entry entry : rest.entries)
			{
				final Entry expected = new Entry(entry.index, entry.iconX, entry.iconY + placed.sectionHeight,
					entry.nameX, entry.nameY + placed.sectionHeight);
				assertEquals(expected, placed.entries.get(entry.index));
			}
			assertEquals(rest.entries.size() + section.size(), placed.entries.size());
			assertEquals(rest.content + placed.sectionHeight, placed.bottom);
		}
	}

	@Test
	public void theSectionIsAsTallAsACategory()
	{
		final GameVault vault = game(slots(1, 1, 1, 1, 1, 1, 1));
		final List<List<Integer>> categories = recognised(vault);
		// One row: header, line and a row, like the Farming Contract section (104)
		assertEquals(104, SeedVaultLayout.place(WIDTH, categories, Collections.singletonList(3)).sectionHeight);
		assertEquals(104, SeedVaultLayout.place(WIDTH, categories, Arrays.asList(0, 1, 2, 3, 4)).sectionHeight);
		assertEquals(186, SeedVaultLayout.place(WIDTH, categories, Arrays.asList(0, 1, 2, 3, 4, 5)).sectionHeight);
		// Taking 2 of category 1's 7 leaves 5: one row, so the next header moves up a row less the section
		final Placement placed = SeedVaultLayout.place(WIDTH, categories, Arrays.asList(5, 6));
		assertEquals(vault.headerY[2] + 104 - SeedVaultLayout.ROW_HEIGHT, placed.headerY[2]);
	}

	@Test
	public void aLayoutTheGameDidntMakeIsLeftAlone()
	{
		final int[] slots = slots(1, 1, 2, 3, 3, 3, 3, 3, 3, 0);
		final GameVault vault = game(slots);
		recognised(vault);

		// An item a pixel out
		final List<Entry> moved = new ArrayList<>(vault.entries);
		final Entry first = moved.get(0);
		moved.set(0, new Entry(first.index, first.iconX, first.iconY + 1, first.nameX, first.nameY + 1));
		assertNotNull(SeedVaultLayout.mismatch(WIDTH, vault.headerY, vault.lineY, moved,
			SeedVaultLayout.categories(vault.headerY, moved)));

		// A header out
		final int[] headers = vault.headerY.clone();
		headers[3] += 2;
		assertNotNull(SeedVaultLayout.mismatch(WIDTH, headers, vault.lineY, vault.entries,
			SeedVaultLayout.categories(headers, vault.entries)));

		// A missing line
		assertNotNull(SeedVaultLayout.mismatch(WIDTH, vault.headerY, Arrays.copyOf(vault.lineY, CATEGORIES - 1),
			vault.entries, SeedVaultLayout.categories(vault.headerY, vault.entries)));

		// No headers at all, as while searching
		assertNotNull(SeedVaultLayout.mismatch(WIDTH, new int[0], new int[0], vault.entries,
			SeedVaultLayout.categories(new int[0], vault.entries)));

		// The game's wider gaps on mobile
		final GameVault mobile = game(WIDTH, CATEGORIES, slots, 20, 25);
		assertNotNull(SeedVaultLayout.mismatch(WIDTH, mobile.headerY, mobile.lineY, mobile.entries,
			SeedVaultLayout.categories(mobile.headerY, mobile.entries)));
	}

	@Test
	public void theScrollHeightGrowsWithTheSection()
	{
		// Scrolling already: grows by as much as the bottom moved
		assertEquals(1104, SeedVaultLayout.scrollHeight(1000, 0, 1000, 1104, 500));
		// Below a Farming Contract section 104 high
		assertEquals(1290, SeedVaultLayout.scrollHeight(1104, 104, 1000, 1186, 500));
		// It all fitted and still does: no scrolling, as the game does
		assertEquals(0, SeedVaultLayout.scrollHeight(0, 0, 300, 404, 500));
		assertEquals(0, SeedVaultLayout.scrollHeight(0, 104, 300, 404, 600));
		// It fitted, but not with the section
		assertEquals(604, SeedVaultLayout.scrollHeight(0, 0, 300, 604, 500));
		// Back to the game's own
		assertEquals(1000, SeedVaultLayout.scrollHeight(1000, 0, 1000, 1000, 500));
		assertEquals(0, SeedVaultLayout.scrollHeight(0, 0, 300, 300, 500));
	}

	@Test
	public void theSamePartOfTheVaultStaysInView()
	{
		// The section grows by a row: everything below it moves down as much
		assertEquals(382, SeedVaultLayout.scrollY(300, 104, 186, false, 2000, 500));
		// It goes: back up by its height, never past the top
		assertEquals(196, SeedVaultLayout.scrollY(300, 104, 0, false, 2000, 500));
		assertEquals(0, SeedVaultLayout.scrollY(50, 186, 0, false, 2000, 500));
		// First shown on this visit: the top, so it's seen
		assertEquals(0, SeedVaultLayout.scrollY(1200, 0, 104, true, 2104, 500));
		// Never past the end, nor anywhere when nothing scrolls
		assertEquals(1604, SeedVaultLayout.scrollY(1900, 0, 104, false, 2104, 500));
		assertEquals(0, SeedVaultLayout.scrollY(40, 0, 104, false, 0, 500));
	}

	@Test
	public void sectionItemsLeaveTheirCategories()
	{
		final GameVault vault = game(slots(1, 1, 1));
		final List<List<Integer>> categories = recognised(vault);
		final Placement placed = SeedVaultLayout.place(WIDTH, categories, Arrays.asList(2, 0));
		// Slot 2 first, as asked, then slot 0
		assertEquals(14, placed.entries.get(2).iconX);
		assertEquals(14 + 65, placed.entries.get(0).iconX);
		// Slot 1 is category 1's only item now, first in its row
		final Entry left = placed.entries.get(1);
		assertEquals(14, left.iconX);
		assertEquals(placed.headerY[1] + 37, left.iconY);
		final Set<Integer> all = new HashSet<>(placed.entries.keySet());
		assertEquals(new HashSet<>(Arrays.asList(0, 1, 2, ADD_FAVOURITES)), all);
	}
}
