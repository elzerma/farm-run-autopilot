package com.farmrunautopilot.bank;

import com.farmrunautopilot.bank.SeedVaultLayout.Entry;
import com.farmrunautopilot.bank.SeedVaultLayout.Placement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * The "Farm run" section at the top of the Farming Guild's seed vault (issue #8): the seeds and saplings the guide
 * says to take from the vault are moved above its categories, under a header like theirs. The vault's own item
 * widgets are moved, so withdrawing works as usual: nothing is sent to the server and no menu option is changed.
 * While the section shows, nothing in the vault can be dragged, as while searching, since the game's drag scripts
 * would put the items back where it laid them out, and a drag reorders the vault.
 *
 * <p>The game lays the vault out in its seed_vault_build proc. After each run of it this lays the vault out again
 * ({@link SeedVaultLayout}), and again whenever what the guide asks for changes while it's open. It leaves the vault
 * alone while it's being searched, and when it isn't laid out the way the layout expects, so a game update that
 * changes the vault leaves it as the game made it. Client thread.
 *
 * <p>UNVERIFIED: the proc's ID, the vault's layout and its searching drag settings are read from the game's
 * scripts (osrs-dumps), not checked in game, and moving the vault's widgets hasn't been tried (guided test
 * SEED_VAULT_SECTION).
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
	static final String TITLE = "Farm run";
	/** The search box is open (the game's seed_vault_searching proc). */
	private static final int SEARCH_MODE = 11;
	/** As while searching: too long and too far for a drag to ever start. */
	private static final int NO_DRAG = 10000;
	/** The vault's own category headers: 5 in from the left, as wide as the list less 5, 20 high. */
	private static final int HEADER_X = 5;
	private static final int HEADER_HEIGHT = 20;
	private static final int HEADER_COLOUR = 0xff981f;
	/** The line under each header: 2 in from each side, 2 high. */
	private static final int LINE_X = 2;
	private static final int LINE_HEIGHT = 2;

	private final Client client;
	private final ClientThread clientThread;
	/** What the guide asks to take from the vault, by item ID in order; empty when nothing. */
	private Supplier<List<Integer>> wanted = Collections::emptyList;

	/** The vault as the game last laid it out; null while it's closed, being searched or not as expected. */
	private Vault vault;
	/** Why the vault wasn't as expected when it was last laid out, or null. */
	private String unrecognised;
	/** Where the list was scrolled to just before the game laid it out again. */
	private int scrollBeforeBuild;
	/** What was asked for when the vault was last laid out here. */
	private List<Integer> laidOutFor = Collections.emptyList();
	/** The section's items (child indices) as shown, and its height; empty and 0 for none. */
	private List<Integer> shown = Collections.emptyList();
	private int shownHeight;
	private Placement placed;
	/** The section has shown during this visit to the vault, so it's no longer scrolled to by itself. */
	private boolean shownThisVisit;
	/** The section's header and line; the game deletes them when it lays the vault out again. */
	private Widget sectionHeader;
	private Widget sectionLine;

	@Inject
	SeedVaultSection(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	public void startUp(Supplier<List<Integer>> wanted)
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
		if (event.getScriptId() != BUILD_PROC)
		{
			return;
		}
		// The game has just made every header, line and item again, and deleted ours
		sectionHeader = null;
		sectionLine = null;
		final int viewHeight = shownHeight;
		shown = Collections.emptyList();
		shownHeight = 0;
		placed = null;
		laidOutFor = Collections.emptyList();
		vault = null;
		unrecognised = null;
		final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (list == null || searching())
		{
			return;
		}
		vault = read(list);
		final List<Integer> asked = wanted.get();
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

	/** Each game tick: follow what the guide asks for while the vault is open. */
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
		final List<Integer> asked = wanted.get();
		if (!asked.equals(laidOutFor))
		{
			layOut(asked, list, list.getScrollY(), shownHeight, false);
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

	/** How many items the section shows. */
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
		for (int index : shown)
		{
			final Widget icon = icons != null ? icons.getChild(index) : null;
			if (icon == null)
			{
				continue;
			}
			items.add(Text.removeTags(icon.getName()) + " (slot " + index + ")");
			final Entry at = placed.entries.get(index);
			held &= at != null && icon.getOriginalX() == at.iconX && icon.getOriginalY() == at.iconY;
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
		shown = Collections.emptyList();
		shownHeight = 0;
		placed = null;
		shownThisVisit = false;
		sectionHeader = null;
		sectionLine = null;
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
		final Widget headers = client.getWidget(InterfaceID.SeedVault.CATEGORY_HEADERS);
		final Widget lines = client.getWidget(InterfaceID.SeedVault.CATEGORY_LINES);
		final Widget icons = client.getWidget(InterfaceID.SeedVault.OBJ_LIST);
		final Widget names = client.getWidget(InterfaceID.SeedVault.TEXT_LIST);
		if (main == null || headers == null || lines == null || icons == null || names == null
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
			// Only a real stack in that vault slot: not a placeholder, nor the "Add Favourites" button after the slots
			final Item item = contents != null ? contents.getItem(i) : null;
			if (item != null && item.getId() == icon.getItemId() && item.getQuantity() > 0)
			{
				indexByItem.putIfAbsent(item.getId(), i);
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
	 * Lay the vault out for these items, from the game's layout, keeping the same part of it in view.
	 *
	 * @param viewScroll where the list was scrolled to, with a section {@code viewHeight} high (0 for none) on top
	 * @param rebuilt the game has just laid the vault out again, so everything is where it put it
	 */
	private void layOut(List<Integer> asked, Widget list, int viewScroll, int viewHeight, boolean rebuilt)
	{
		laidOutFor = asked;
		final List<Integer> section = new ArrayList<>();
		for (int itemId : asked)
		{
			final Integer index = vault.indexByItem.get(itemId);
			if (index != null && !section.contains(index))
			{
				section.add(index);
			}
		}
		final boolean move = !section.equals(shown);
		// Nothing to move, and the view is as it was (after a rebuild, the game's scroll needs the section taken off)
		if (!move && !(rebuilt && viewHeight > 0))
		{
			return;
		}
		if (move)
		{
			final Placement placement = SeedVaultLayout.place(vault.width, vault.categories, section);
			if (!moveWidgets(placement, !section.isEmpty()))
			{
				return;
			}
			list.setScrollHeight(SeedVaultLayout.scrollHeight(vault.scrollHeight, vault.top, vault.bottom,
				placement.bottom, list.getHeight()));
			// The main list is sized from the scroll height, so it grows to hold everything
			list.revalidateScroll();
			shown = section;
			shownHeight = placement.sectionHeight;
			placed = placement;
			log.debug("Seed vault: {} at the top, section {} high", section, shownHeight);
		}
		final boolean first = shownHeight > 0 && !shownThisVisit;
		shownThisVisit |= shownHeight > 0;
		scrollTo(list, SeedVaultLayout.scrollY(viewScroll, viewHeight, shownHeight, first, list.getScrollHeight(),
			list.getHeight()));
	}

	/** Put the headers, lines and items where the placement says; false if the vault's widgets have gone. */
	private boolean moveWidgets(Placement placement, boolean noDrag)
	{
		final Widget headers = client.getWidget(InterfaceID.SeedVault.CATEGORY_HEADERS);
		final Widget lines = client.getWidget(InterfaceID.SeedVault.CATEGORY_LINES);
		final Widget icons = client.getWidget(InterfaceID.SeedVault.OBJ_LIST);
		final Widget names = client.getWidget(InterfaceID.SeedVault.TEXT_LIST);
		if (headers == null || lines == null || icons == null || names == null)
		{
			return false;
		}
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
			if (noDrag)
			{
				noDrag(icon);
				noDrag(name);
			}
		}
		showHeader(headers, lines, placement.sectionY);
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
	 * What the game sets while searching. It sets the drag scripts again whenever it lays the vault out, so an item
	 * put back without that stays undraggable until then.
	 */
	private static void noDrag(Widget widget)
	{
		widget.setDragDeadTime(NO_DRAG);
		widget.setDragDeadZone(NO_DRAG);
		widget.setOnDragListener((Object[]) null);
		widget.setOnDragCompleteListener((Object[]) null);
	}

	/** The section's header and line, in the vault's own style, with the header at {@code y}; hidden at -1. */
	private void showHeader(Widget headers, Widget lines, int y)
	{
		if (y < 0)
		{
			if (sectionHeader != null)
			{
				sectionHeader.setHidden(true);
			}
			if (sectionLine != null)
			{
				sectionLine.setHidden(true);
			}
			return;
		}
		if (sectionHeader == null)
		{
			sectionHeader = headers.createChild(-1, WidgetType.TEXT);
			sectionHeader.setText(TITLE);
			sectionHeader.setFontId(FontID.BOLD_12);
			sectionHeader.setTextColor(HEADER_COLOUR);
			sectionHeader.setTextShadowed(true);
			sectionHeader.setWidthMode(WidgetSizeMode.MINUS);
			sectionHeader.setOriginalWidth(HEADER_X);
			sectionHeader.setOriginalHeight(HEADER_HEIGHT);
			sectionHeader.setOriginalX(HEADER_X);
		}
		if (sectionLine == null)
		{
			sectionLine = lines.createChild(-1, WidgetType.GRAPHIC);
			sectionLine.setSpriteId(SpriteID.TRADEBACKING_DARK);
			sectionLine.setSpriteTiling(true);
			sectionLine.setWidthMode(WidgetSizeMode.MINUS);
			sectionLine.setOriginalWidth(2 * LINE_X);
			sectionLine.setOriginalHeight(LINE_HEIGHT);
			sectionLine.setOriginalX(LINE_X);
		}
		sectionHeader.setOriginalY(y);
		sectionHeader.setHidden(false);
		sectionHeader.revalidate();
		sectionLine.setOriginalY(y + SeedVaultLayout.HEADER_TO_LINE);
		sectionLine.setHidden(false);
		sectionLine.revalidate();
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
		/** Each item's child index (its vault slot), by item ID. */
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
