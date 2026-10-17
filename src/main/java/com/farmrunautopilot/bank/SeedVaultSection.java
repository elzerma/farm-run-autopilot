package com.farmrunautopilot.bank;

import com.farmrunautopilot.bank.SeedVaultLayout.Entry;
import com.farmrunautopilot.bank.SeedVaultLayout.Placement;
import com.farmrunautopilot.supply.SupplyLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetDrag;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * The "Farm run" section at the top of the Farming Guild's seed vault (issue #8), whenever the vault is open while a
 * run is being built or done: every seed and sapling the rest of the run plants, laid out like a section of the
 * bank's Farm run tab, with the same "/ N" label under each. Those the vault holds are its own item widgets moved up,
 * so withdrawing works as usual: nothing is sent to the server and no menu option is changed. Those it doesn't hold
 * (or no longer does) are shown faded, so the section stays put, ticked, once everything's taken. The vault's
 * categories close up below it.
 *
 * <p>While the section shows, dragging an item does nothing. The game's drag scripts would put items back where it
 * laid them out, so they're taken off, and the drop is cancelled, as Bank Tags does in a tag tab, so the vault isn't
 * reordered. Dragging itself is left on, so letting go elsewhere isn't a click (elzerma: with dragging turned off,
 * it withdrew).
 *
 * <p>The game lays the vault out in its seed_vault_build proc. After each run of it this lays the vault out again
 * ({@link SeedVaultLayout}), and again whenever the section's contents change while it's open. It leaves the vault
 * alone while it's being searched, and when it isn't laid out the way the layout expects, so a game update that
 * changes the vault leaves it as the game made it. Client thread.
 *
 * <p>UNVERIFIED: the procs' IDs and the vault's layout are read from the game's scripts (osrs-dumps), not checked in
 * game (guided test SEED_VAULT_SECTION).
 */
@Slf4j
@Singleton
public class SeedVaultSection
{
	/**
	 * The proc that lays the vault out (seed_vault_build). {@link ScriptID#SEED_VAULT_BUILD} is only the client script
	 * that runs it when the vault's contents or settings change; opening the vault and searching run it directly.
	 */
	static final int BUILD_PROC = 2820;
	/** The proc that scrolls to a category (seed_vault_catselect), when one is picked or the vault opens. */
	static final int CATEGORY_PROC = 2817;
	static final String TITLE = "Farm run";
	/** The search box is open (the game's seed_vault_searching proc). */
	private static final int SEARCH_MODE = 11;
	/** The title starts 5 in, as the vault's own headers do; the line 2 in from each side. */
	private static final int TITLE_X = 5;
	private static final int LINE_X = 2;
	/** The vault's own icons and names. */
	private static final int ICON_WIDTH = 36;
	private static final int ICON_HEIGHT = 32;
	private static final int NAME_WIDTH = 50;
	private static final int NAME_COLOUR = 0xff981f;

	/** A seed or sapling the run needs: how many, and whether they're carried yet, as the bank tab colours it. */
	@Value
	public static class Seed
	{
		int itemId;
		int need;
		SupplyLine.Status status;
	}

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	/** What the section shows; empty when there's no run. */
	private Supplier<List<Seed>> wanted = Collections::emptyList;

	/** The vault as the game last laid it out; null while it's closed, being searched or not as expected. */
	private Vault vault;
	/** Why the vault wasn't as expected when it was last laid out, or null. */
	private String unrecognised;
	/** Where the list was scrolled to just before the game laid it out again. */
	private int scrollBeforeBuild;
	/** What the section was last laid out for. */
	private List<Seed> laidOutFor = Collections.emptyList();
	/** The section as shown: each seed, and its vault child index or -1 (shown faded); empty for none. */
	private List<Seed> shownSeeds = Collections.emptyList();
	private List<Integer> shown = Collections.emptyList();
	private int shownHeight;
	private Placement placed;
	/** The vault's drag scripts have been taken off since the game last laid it out, so drops are cancelled. */
	private boolean noDragScripts;
	/** The section has shown during this visit to the vault, so it's no longer scrolled to by itself. */
	private boolean shownThisVisit;

	@Inject
	SeedVaultSection(Client client, ClientThread clientThread, ItemManager itemManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
	}

	public void startUp(Supplier<List<Seed>> wanted)
	{
		this.wanted = wanted;
	}

	/** Put the vault back as the game laid it out. */
	public void shutDown()
	{
		clientThread.invoke(() ->
		{
			final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
			if (list != null && vault != null && !shown.isEmpty())
			{
				layOut(Collections.emptyList(), list, list.getScrollY(), shownHeight, false);
			}
			wanted = Collections::emptyList;
			reset();
		});
	}

	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == BUILD_PROC)
		{
			final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
			scrollBeforeBuild = list != null ? list.getScrollY() : 0;
		}
	}

	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == CATEGORY_PROC)
		{
			// The first category is at the top of the vault, so show the section above it too: opening the vault scrolls
			// to the category picked last (elzerma's test opened scrolled past the section)
			final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
			if (list != null && shownHeight > 0 && client.getVarbitValue(VarbitID.SEED_VAULT_CATEGORY) == 0)
			{
				scrollTo(list, 0);
			}
			return;
		}
		if (event.getScriptId() != BUILD_PROC)
		{
			return;
		}
		// The game has just made every header, line and item again, with its drag scripts, and emptied the layer
		// this section draws in
		final int viewHeight = shownHeight;
		shownSeeds = Collections.emptyList();
		shown = Collections.emptyList();
		shownHeight = 0;
		placed = null;
		noDragScripts = false;
		laidOutFor = Collections.emptyList();
		vault = null;
		unrecognised = null;
		final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (list == null || searching())
		{
			return;
		}
		vault = read(list);
		final List<Seed> asked = wanted.get();
		if (vault == null)
		{
			if (!asked.isEmpty())
			{
				log.debug("Seed vault not laid out as expected, so no Farm run section: {}", unrecognised);
			}
			return;
		}
		layOut(asked, list, scrollBeforeBuild, viewHeight, true);
	}

	/** Each game tick: follow the run while the vault is open. */
	public void onGameTick()
	{
		final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (list == null)
		{
			reset();
			return;
		}
		if (vault == null)
		{
			return;
		}
		final List<Seed> asked = wanted.get();
		if (!asked.equals(laidOutFor))
		{
			layOut(asked, list, list.getScrollY(), shownHeight, false);
		}
	}

	/**
	 * Each client tick while something is dragged: an item of the vault this has laid out drops on nothing, so the
	 * vault isn't reordered, as Bank Tags does in a tag tab.
	 */
	public void onWidgetDrag(WidgetDrag event)
	{
		if (!noDragScripts)
		{
			return;
		}
		final Widget dragged = client.getDraggedWidget();
		if (dragged != null && (dragged.getId() == InterfaceID.SeedVault.OBJ_LIST
			|| dragged.getId() == InterfaceID.SeedVault.TEXT_LIST))
		{
			client.setDraggedOnWidget(null);
		}
	}

	/** The seed vault is open. */
	public boolean isOpen()
	{
		return client.getWidget(InterfaceID.SeedVault.LIST) != null;
	}

	/** The vault is open and has been looked at since the game laid it out. */
	public boolean isLaidOut()
	{
		return isOpen() && (vault != null || unrecognised != null);
	}

	/** How many seeds and saplings the section shows. */
	public int shownCount()
	{
		return shown.size();
	}

	/** For the guided test's report: what the section shows and where, or why there's none. */
	public String describe()
	{
		final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (list == null)
		{
			return "vault closed";
		}
		if (vault == null)
		{
			return unrecognised != null ? "not laid out as expected: " + unrecognised
				: searching() ? "searching" : "not laid out yet";
		}
		final Widget icons = client.getWidget(InterfaceID.SeedVault.OBJ_LIST);
		final List<String> items = new ArrayList<>();
		boolean held = true;
		for (int i = 0; i < shown.size(); i++)
		{
			final Seed seed = shownSeeds.get(i);
			final int index = shown.get(i);
			final String name = client.getItemDefinition(seed.getItemId()).getName();
			items.add(name + " " + FarmBankTab.neededLabel(seed.getNeed()) + " " + seed.getStatus().name().toLowerCase()
				+ (index >= 0 ? " (slot " + index + ")" : " (faded)"));
			final Widget icon = index >= 0 && icons != null ? icons.getChild(index) : null;
			final Entry at = index >= 0 ? placed.entries.get(index) : null;
			held &= icon == null || at != null && icon.getOriginalX() == at.iconX && icon.getOriginalY() == at.iconY;
		}
		return (items.isEmpty() ? "no section" : "section: " + String.join(", ", items)
			+ (held ? "" : ", since moved by something else")) + "; " + SeedVaultLayout.perRow(vault.width) + " per row, "
			+ vault.categories.size() + " categories, section " + shownHeight + " high, scrolled to " + list.getScrollY()
			+ " of " + list.getScrollHeight();
	}

	private void reset()
	{
		vault = null;
		unrecognised = null;
		laidOutFor = Collections.emptyList();
		shownSeeds = Collections.emptyList();
		shown = Collections.emptyList();
		shownHeight = 0;
		placed = null;
		noDragScripts = false;
		shownThisVisit = false;
	}

	/** The vault is showing search results, as the game's seed_vault_searching proc decides. */
	private boolean searching()
	{
		final int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		final String input = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
		return mode == SEARCH_MODE || (mode <= 0 && input != null && !input.isEmpty());
	}

	/** The vault as the game has just laid it out, or null (with why in {@link #unrecognised}) if not as expected. */
	private Vault read(Widget list)
	{
		final Widget main = client.getWidget(InterfaceID.SeedVault.MAIN_VAULT);
		final Widget faves = client.getWidget(InterfaceID.SeedVault.FAVES);
		final Widget headers = client.getWidget(InterfaceID.SeedVault.CATEGORY_HEADERS);
		final Widget lines = client.getWidget(InterfaceID.SeedVault.CATEGORY_LINES);
		final Widget icons = client.getWidget(InterfaceID.SeedVault.OBJ_LIST);
		final Widget names = client.getWidget(InterfaceID.SeedVault.TEXT_LIST);
		if (main == null || faves == null || headers == null || lines == null || icons == null || names == null
			|| icons.getChildren() == null)
		{
			unrecognised = "parts of the vault are missing";
			return null;
		}
		final ItemContainer contents = client.getItemContainer(InventoryID.SEED_VAULT);
		final int[] headerY = ys(headers);
		final int[] lineY = ys(lines);
		final List<Entry> entries = new ArrayList<>();
		final Map<Integer, Integer> indexByItem = new HashMap<>();
		final Widget[] children = icons.getChildren();
		for (int i = 0; i < children.length; i++)
		{
			final Widget icon = children[i];
			if (icon == null || icon.isSelfHidden())
			{
				continue;
			}
			final Widget name = names.getChild(i);
			if (name == null)
			{
				unrecognised = "item " + i + " has no name";
				return null;
			}
			entries.add(new Entry(i, icon.getOriginalX(), icon.getOriginalY(), name.getOriginalX(), name.getOriginalY()));
			// Only that vault slot's own stack, or its placeholder once it's empty (shown faded by the game); not the
			// "Add Favourites" button after the slots
			final Item item = contents != null ? contents.getItem(i) : null;
			if (item != null && item.getId() == icon.getItemId())
			{
				indexByItem.putIfAbsent(itemManager.canonicalize(item.getId()), i);
			}
		}
		final int width = icons.getWidth();
		final List<List<Integer>> categories = SeedVaultLayout.categories(headerY, entries);
		unrecognised = SeedVaultLayout.mismatch(width, headerY, lineY, entries, categories);
		if (unrecognised != null)
		{
			return null;
		}
		final Placement game = SeedVaultLayout.place(width, categories, Collections.emptyList());
		// The main list sits at the bottom of the scrolling list, as tall as it less the Farming Contract section
		// above it (the game sets that straight from the contract section's height)
		return new Vault(width, categories, indexByItem, list.getScrollHeight(), main.getOriginalHeight(), game.bottom);
	}

	/** Each child's y, in order, up to the first gap. */
	private static int[] ys(Widget parent)
	{
		final Widget[] children = parent.getChildren();
		if (children == null)
		{
			return new int[0];
		}
		int n = 0;
		while (n < children.length && children[n] != null)
		{
			n++;
		}
		final int[] ys = new int[n];
		for (int i = 0; i < n; i++)
		{
			ys[i] = children[i].getOriginalY();
		}
		return ys;
	}

	/**
	 * Lay the vault out for these seeds, from the game's layout, keeping the same part of it in view.
	 *
	 * @param viewScroll where the list was scrolled to, with a section {@code viewHeight} high (0 for none) on top
	 * @param rebuilt the game has just laid the vault out again, so everything is where it put it
	 */
	private void layOut(List<Seed> asked, Widget list, int viewScroll, int viewHeight, boolean rebuilt)
	{
		laidOutFor = asked;
		final List<Integer> section = new ArrayList<>();
		for (Seed seed : asked)
		{
			final Integer index = vault.indexByItem.get(seed.getItemId());
			section.add(index != null ? index : -1);
		}
		if (section.isEmpty() && shown.isEmpty())
		{
			// The game's own layout as it is; after a rebuild, the game's scroll needs the section taken off
			if (rebuilt && viewHeight > 0)
			{
				scrollTo(list, SeedVaultLayout.scrollY(viewScroll, viewHeight, 0, false, list.getScrollHeight(),
					list.getHeight()));
			}
			return;
		}
		final Placement placement = SeedVaultLayout.place(vault.width, vault.categories, section);
		if (!draw(placement, asked))
		{
			return;
		}
		list.setScrollHeight(SeedVaultLayout.scrollHeight(vault.scrollHeight, vault.top, vault.bottom, placement.bottom,
			list.getHeight()));
		// The main list is sized from the scroll height, so it grows to hold everything
		list.revalidateScroll();
		shownSeeds = asked;
		shown = section;
		shownHeight = placement.sectionHeight;
		placed = placement;
		final boolean first = shownHeight > 0 && !shownThisVisit;
		shownThisVisit |= shownHeight > 0;
		scrollTo(list, SeedVaultLayout.scrollY(viewScroll, viewHeight, shownHeight, first, list.getScrollHeight(),
			list.getHeight()));
		log.debug("Seed vault: {} in the Farm run section, {} high", section, shownHeight);
	}

	/** Put everything where the placement says and draw the section; false if the vault's widgets have gone. */
	private boolean draw(Placement placement, List<Seed> seeds)
	{
		final Widget faves = client.getWidget(InterfaceID.SeedVault.FAVES);
		final Widget headers = client.getWidget(InterfaceID.SeedVault.CATEGORY_HEADERS);
		final Widget lines = client.getWidget(InterfaceID.SeedVault.CATEGORY_LINES);
		final Widget icons = client.getWidget(InterfaceID.SeedVault.OBJ_LIST);
		final Widget names = client.getWidget(InterfaceID.SeedVault.TEXT_LIST);
		if (faves == null || headers == null || lines == null || icons == null || names == null)
		{
			return false;
		}
		// The game leaves this layer empty when it lays the vault out, so everything in it is this section's
		faves.deleteAllChildren();
		for (int i = 0; i < placement.headerY.length; i++)
		{
			final Widget header = headers.getChild(i);
			final Widget line = lines.getChild(i);
			if (header != null)
			{
				moveTo(header, header.getOriginalX(), placement.headerY[i]);
			}
			if (line != null)
			{
				moveTo(line, line.getOriginalX(), placement.headerY[i] + SeedVaultLayout.HEADER_TO_LINE);
			}
		}
		final boolean section = placement.sectionY >= 0;
		for (Entry entry : placement.entries.values())
		{
			final Widget icon = icons.getChild(entry.index);
			final Widget name = names.getChild(entry.index);
			if (icon == null || name == null)
			{
				continue;
			}
			moveTo(icon, entry.iconX, entry.iconY);
			moveTo(name, entry.nameX, entry.nameY);
			if (section)
			{
				noDragScripts(icon);
				noDragScripts(name);
			}
		}
		if (section)
		{
			noDragScripts = true;
			title(faves, placement.sectionY);
			for (int i = 0; i < placement.cells.size(); i++)
			{
				final Entry cell = placement.cells.get(i);
				if (cell.index < 0)
				{
					standIn(faves, cell, seeds.get(i).getItemId());
				}
				counter(faves, cell, seeds.get(i));
			}
		}
		return true;
	}

	private static void moveTo(Widget widget, int x, int y)
	{
		if (widget.getOriginalX() != x || widget.getOriginalY() != y)
		{
			widget.setOriginalX(x);
			widget.setOriginalY(y);
			widget.revalidate();
		}
	}

	/**
	 * The game's drag scripts would put a moved item back where the game laid it out. It sets them again whenever it
	 * lays the vault out; until then a drag drops on nothing ({@link #onWidgetDrag}).
	 */
	private static void noDragScripts(Widget widget)
	{
		widget.setOnDragListener((Object[]) null);
		widget.setOnDragCompleteListener((Object[]) null);
	}

	/** The section's line and title, as on the bank's Farm run tab. */
	private static void title(Widget faves, int y)
	{
		final Widget line = faves.createChild(-1, WidgetType.GRAPHIC);
		line.setSpriteId(SpriteID.TRADEBACKING_DARK);
		line.setSpriteTiling(true);
		line.setWidthMode(WidgetSizeMode.MINUS);
		line.setOriginalWidth(2 * LINE_X);
		line.setOriginalHeight(FarmBankTab.LINE_HEIGHT);
		line.setOriginalX(LINE_X);
		line.setOriginalY(y);
		line.revalidate();

		final Widget title = faves.createChild(-1, WidgetType.TEXT);
		title.setText(TITLE);
		title.setFontId(FontID.PLAIN_11);
		title.setTextColor(FarmBankTab.HEADER.getRGB());
		title.setTextShadowed(true);
		title.setWidthMode(WidgetSizeMode.MINUS);
		title.setOriginalWidth(TITLE_X);
		title.setOriginalHeight(FarmBankTab.TEXT_HEIGHT);
		title.setOriginalX(TITLE_X);
		title.setOriginalY(y + SeedVaultLayout.SECTION_TITLE_Y);
		title.revalidate();
	}

	/** A seed the vault doesn't hold (or no longer does): its icon and name, faded, as the bank tab shows one. */
	private void standIn(Widget faves, Entry cell, int itemId)
	{
		final Widget icon = faves.createChild(-1, WidgetType.GRAPHIC);
		icon.setItemId(itemId);
		icon.setItemQuantity(0);
		icon.setItemQuantityMode(ItemQuantityMode.NEVER);
		icon.setBorderType(1);
		icon.setOpacity(FarmBankTab.FADED);
		icon.setOriginalWidth(ICON_WIDTH);
		icon.setOriginalHeight(ICON_HEIGHT);
		icon.setOriginalX(cell.iconX);
		icon.setOriginalY(cell.iconY);
		icon.revalidate();

		final Widget name = faves.createChild(-1, WidgetType.TEXT);
		name.setText(client.getItemDefinition(itemId).getName());
		name.setFontId(FontID.PLAIN_11);
		name.setTextColor(NAME_COLOUR);
		name.setTextShadowed(true);
		name.setXTextAlignment(WidgetTextAlignment.CENTER);
		name.setYTextAlignment(WidgetTextAlignment.TOP);
		name.setOpacity(FarmBankTab.FADED);
		name.setOriginalWidth(NAME_WIDTH);
		name.setOriginalHeight(SeedVaultLayout.NAME_HEIGHT);
		name.setOriginalX(cell.nameX);
		name.setOriginalY(cell.nameY);
		name.revalidate();
	}

	/** "/ N" under the icon, coloured, with its tick or cross, as on the bank's Farm run tab. */
	private static void counter(Widget faves, Entry cell, Seed seed)
	{
		final int x = cell.iconX - (FarmBankTab.LABEL_WIDTH - ICON_WIDTH) / 2;
		final int y = cell.iconY + SeedVaultLayout.COUNTER_BELOW;
		final Widget label = faves.createChild(-1, WidgetType.TEXT);
		label.setText(FarmBankTab.neededLabel(seed.getNeed()));
		label.setFontId(FontID.BOLD_12);
		label.setTextColor(FarmBankTab.neededColour(seed.getStatus()));
		label.setTextShadowed(true);
		label.setYTextAlignment(WidgetTextAlignment.CENTER);
		label.setOriginalWidth(FarmBankTab.LABEL_WIDTH - FarmBankTab.ICON_SIZE - 2);
		label.setOriginalHeight(FarmBankTab.LABEL_HEIGHT);
		label.setOriginalX(x + 2);
		label.setOriginalY(y);
		label.revalidate();

		final int sprite = FarmBankTab.neededSprite(seed.getStatus());
		if (sprite != -1)
		{
			final Widget mark = faves.createChild(-1, WidgetType.GRAPHIC);
			mark.setSpriteId(sprite);
			mark.setOriginalWidth(FarmBankTab.ICON_SIZE);
			mark.setOriginalHeight(FarmBankTab.ICON_SIZE);
			mark.setOriginalX(x + FarmBankTab.LABEL_WIDTH - FarmBankTab.ICON_SIZE - 1);
			mark.setOriginalY(y + (FarmBankTab.LABEL_HEIGHT - FarmBankTab.ICON_SIZE) / 2);
			mark.revalidate();
		}
	}

	/** Scroll the list there, then have the game's own script redraw its scroll bar to match (as the bank tab does). */
	private void scrollTo(Widget list, int y)
	{
		list.setScrollY(y);
		clientThread.invokeLater(() ->
		{
			if (client.getWidget(InterfaceID.SeedVault.LIST) != null)
			{
				client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.SeedVault.RIGHT_SCROLL, InterfaceID.SeedVault.LIST,
					y);
			}
		});
	}

	/** The vault as the game laid it out. */
	private static final class Vault
	{
		final int width;
		final List<List<Integer>> categories;
		/** Each seed's child index (its vault slot), by item ID: its stack, or its placeholder once that's empty. */
		final Map<Integer, Integer> indexByItem;
		/** The scroll height the game set, 0 if everything fitted. */
		final int scrollHeight;
		/** Where the main list starts: below the Farming Contract section, if there is one. */
		final int top;
		final int bottom;

		Vault(int width, List<List<Integer>> categories, Map<Integer, Integer> indexByItem, int scrollHeight, int top,
			int bottom)
		{
			this.width = width;
			this.categories = categories;
			this.indexByItem = indexByItem;
			this.scrollHeight = scrollHeight;
			this.top = top;
			this.bottom = bottom;
		}
	}
}
