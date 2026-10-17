package com.farmrunautopilot.bank;

import com.farmrunautopilot.bank.SeedVaultLayout.Entry;
import com.farmrunautopilot.bank.SeedVaultLayout.Placement;
import com.farmrunautopilot.supply.SupplyLine;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * The "Farm Run Autopilot" view of the Farming Guild's seed vault (issue #8), while a run is being built or done: a
 * section at the top with every seed and sapling the rest of the run plants, under a header like the vault's own
 * categories', with the bank's Farm run tab's "/ N" label under each. A button by the vault's close button switches it
 * on and off, as the bank's Farm run button does its tab (elzerma), and flashes, with the same outline round it, while
 * it's off and the vault holds seeds the run still needs. Those the vault holds are its own item widgets moved up, so
 * withdrawing works as usual: nothing is sent to the server and no menu option is changed. Those it doesn't hold (or
 * no longer does) are shown faded, so the section stays put, ticked, once everything's taken. The vault's categories
 * close up below it, and while the view is on the category list on the left has an entry that scrolls to it.
 *
 * <p>Dragging works as in the game's own vault (elzerma). Each item is still the widget of its own vault slot, so a
 * drag reorders exactly the slots the player meant; the server then sends the vault again and it's laid out afresh.
 * The game's drop script puts the dragged item back where the game laid it out, so after each drop everything goes
 * back where this section put it.
 *
 * <p>The game lays the vault out in its seed_vault_build proc. After each run of it this lays the vault out again
 * ({@link SeedVaultLayout}), and again whenever the section's contents change while it's open. It leaves the vault
 * alone while it's being searched, and when it isn't laid out the way the layout expects, so a game update that
 * changes the vault leaves it as the game made it. Client thread.
 *
 * <p>UNVERIFIED: the scripts' IDs and the vault's layout are read from the game's scripts (osrs-dumps). The layout,
 * the counts, dragging, the scroll and the button passed in game (guided test SEED_VAULT_SECTION revisions 2 and 3,
 * and elzerma's own checks); the category list entry that shows with the view is untried.
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
	/**
	 * The script that scrolls to the category picked last when the server says so, as the vault opens
	 * (seed_vault_catvar). Picking one in the list runs another (seed_vault_catop), which is left alone.
	 */
	static final int CATEGORY_VAR_SCRIPT = 2816;
	/** The script run when a drag of a vault item ends (seed_vault_drop). */
	static final int DROP_SCRIPT = 2827;
	/** The script that makes the category list on the left (seed_vault_build_category_list), when the vault opens. */
	static final int CATEGORY_LIST_SCRIPT = 2814;
	/** The section's header, the button's name, and the section's entry in the category list. */
	static final String TITLE = "Farm Run Autopilot";
	/** The search box is open (the game's seed_vault_searching proc). */
	private static final int SEARCH_MODE = 11;
	/** As the vault's own headers: 5 in from the left, 20 high, in its orange; the line 2 in from each side. */
	private static final int TITLE_X = 5;
	private static final int TITLE_HEIGHT = 20;
	private static final int LINE_X = 2;
	/** The vault's own icons and names. */
	private static final int ICON_WIDTH = 36;
	private static final int ICON_HEIGHT = 32;
	private static final int NAME_WIDTH = 50;
	private static final int VAULT_ORANGE = 0xff981f;
	/**
	 * The button is the bank's Farm run button, smaller to sit in the title bar (elzerma: 22 or so; the bank's is 25),
	 * with the farming icon 3 in. It's centred on the line of the vault's close button (16 square, 10 down and 10 in
	 * from the right of the title bar), 3 to its left.
	 */
	private static final int BUTTON_SIZE = 22;
	private static final int BUTTON_RIGHT = 29;
	private static final int BUTTON_TOP = 7;
	private static final int ICON_INSET = 3;
	private static final String SHOW = "Show";
	private static final String HIDE = "Hide";
	/** The category list turns an entry white under the mouse, and a clear background is white. */
	private static final int WHITE = 0xffffff;
	/** The category list's entries are on backgrounds that alternate between clear and dark. */
	private static final int CLEAR = 255;
	private static final int DARK = 225;
	private static final String SELECT = "Select";

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
	/** The section has shown during this visit to the vault, so it's no longer scrolled to by itself. */
	private boolean shownThisVisit;
	/** The game tick this visit to the vault began, or -1 while it's closed. */
	private int visitTick = -1;
	/** The view is switched on: it stays on through the run, and starts off for the next, like the bank's tab. */
	private boolean viewOn;
	/** The button and its icon, made once per visit; null until then. */
	private Widget button;
	private Widget buttonIcon;
	/** The button is asking to be clicked: the view is off and the vault holds something the run still needs. */
	private boolean calling;
	private int pulseTicks;
	/** The section's entry in the category list and its background, made once per list; null until then. */
	private Widget tab;
	private Widget tabBackground;
	/** The entry shows, and the game's entries from {@link #tabY} down have moved down {@link #tabHeight} for it. */
	private boolean tabShown;
	private int tabY;
	private int tabHeight;

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
			showTab(false);
			vault = null;
			updateButton(Collections.emptyList(), false);
			wanted = Collections::emptyList;
			viewOn = false;
			reset();
		});
	}

	/** A run is being built or done, or not: with none, the view starts off again next time. */
	public void onRunState(boolean running)
	{
		if (!running)
		{
			viewOn = false;
		}
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
		if (event.getScriptId() == CATEGORY_LIST_SCRIPT)
		{
			// The game has just made the category list afresh, without the section's entry; it's usually followed by the
			// vault being laid out, which adds it, but if the section's already showing, add it now
			tab = null;
			tabBackground = null;
			tabShown = false;
			showTab(shownHeight > 0);
			return;
		}
		if (event.getScriptId() == DROP_SCRIPT)
		{
			// The game's drop script has put the dragged item back where the game laid it out (and swapped it with the
			// one it was dropped on, as a preview): back to this layout. A reorder comes back as a new layout
			putBack();
			return;
		}
		if (event.getScriptId() == CATEGORY_VAR_SCRIPT)
		{
			// Opening the vault scrolls to the category picked last, past the section: the top instead, so it's seen
			// (elzerma's first test opened scrolled past it). Picking Favourite Slots in the list still goes there
			final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
			if (list != null && shownHeight > 0 && client.getTickCount() - visitTick <= 1)
			{
				scrollTo(list, 0);
			}
			return;
		}
		if (event.getScriptId() != BUILD_PROC)
		{
			return;
		}
		// The game has just made every header, line and item again, and emptied the layer this section draws in
		final int viewHeight = shownHeight;
		shownSeeds = Collections.emptyList();
		shown = Collections.emptyList();
		shownHeight = 0;
		placed = null;
		laidOutFor = Collections.emptyList();
		vault = null;
		unrecognised = null;
		final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (list == null || searching())
		{
			showTab(false);
			updateButton(Collections.emptyList(), false);
			return;
		}
		vault = read(list);
		final List<Seed> all = wanted.get();
		if (vault == null)
		{
			showTab(false);
			updateButton(Collections.emptyList(), false);
			if (!all.isEmpty())
			{
				log.debug("Seed vault not laid out as expected, so no Farm Run Autopilot view: {}", unrecognised);
			}
			return;
		}
		if (visitTick < 0)
		{
			visitTick = client.getTickCount();
		}
		layOut(viewOn ? all : Collections.emptyList(), list, scrollBeforeBuild, viewHeight, true);
		updateButton(all, false);
	}

	/** Each game tick: follow the run while the vault is open, and flash the button while it's asking to be clicked. */
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
		final List<Seed> all = wanted.get();
		final List<Seed> asked = viewOn ? all : Collections.<Seed>emptyList();
		if (!asked.equals(laidOutFor))
		{
			layOut(asked, list, list.getScrollY(), shownHeight, false);
		}
		updateButton(all, true);
	}

	/** Where the button is on screen while it's asking to be clicked, else null (see {@link FuseOverlay}). */
	Rectangle callingBounds()
	{
		return calling && button != null && !button.isHidden() ? button.getBounds() : null;
	}

	/** The button is asking to be clicked, so the guide says to. */
	public boolean isCalling()
	{
		return calling && button != null && !button.isHidden();
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
		final boolean buttonShown = button != null && !button.isHidden();
		return (items.isEmpty() ? "no section" : "section: " + String.join(", ", items)
			+ (held ? "" : ", since moved by something else")) + "; " + SeedVaultLayout.perRow(vault.width) + " per row, "
			+ vault.categories.size() + " categories, section " + shownHeight + " high, scrolled to " + list.getScrollY()
			+ " of " + list.getScrollHeight() + ", view " + (viewOn ? "on" : "off") + ", button "
			+ (!buttonShown ? "not shown" : calling ? "calling" : "shown") + ", category list entry "
			+ (tabShown ? "at " + tabY : "not shown");
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
		shownThisVisit = false;
		visitTick = -1;
		button = null;
		buttonIcon = null;
		calling = false;
		tab = null;
		tabBackground = null;
		tabShown = false;
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
		final Set<Integer> stocked = new HashSet<>();
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
				final int itemId = itemManager.canonicalize(item.getId());
				indexByItem.putIfAbsent(itemId, i);
				if (item.getQuantity() > 0)
				{
					stocked.add(itemId);
				}
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
		return new Vault(width, categories, indexByItem, stocked, list.getScrollHeight(), main.getOriginalHeight(),
			game.bottom);
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
			showTab(false);
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
		// Its entry in the category list shows with it, so only while the view is on (elzerma)
		showTab(shownHeight > 0);
		log.debug("Seed vault: {} in the Farm Run Autopilot section, {} high", section, shownHeight);
	}

	/**
	 * The section's entry in the category list on the left, like the vault's own: it scrolls to the section. It goes
	 * at the top, or under the Farming Contract's entry when that section is above this one, and the game's entries
	 * below it move down to make room, and back up when it goes.
	 */
	private void showTab(boolean show)
	{
		final Widget names = client.getWidget(InterfaceID.SeedVault.CATEGORY_LIST);
		final Widget backgrounds = client.getWidget(InterfaceID.SeedVault.CATEGORY_BACKGROUNDS);
		if (show == tabShown || names == null || backgrounds == null)
		{
			return;
		}
		if (!show)
		{
			tab.setHidden(true);
			tabBackground.setHidden(true);
			moveEntries(names, backgrounds, tabY + tabHeight, -tabHeight);
			tabShown = false;
			fitList(names);
			return;
		}
		final Widget first = names.getChild(0);
		if (first == null)
		{
			return;
		}
		tabHeight = first.getOriginalHeight();
		tabY = vault != null && vault.top > 0 ? first.getOriginalY() + tabHeight : first.getOriginalY();
		// Its background is the other of the one it pushes down, so they still alternate
		final Widget under = backgroundAt(backgrounds, tabY);
		final boolean dark = under == null || under.getOpacity() != DARK;
		moveEntries(names, backgrounds, tabY, tabHeight);
		if (tab == null)
		{
			tabBackground = backgrounds.createChild(-1, WidgetType.RECTANGLE);
			tabBackground.setFilled(true);
			tabBackground.setWidthMode(WidgetSizeMode.MINUS);
			tabBackground.setOriginalWidth(0);

			final Widget text = names.createChild(-1, WidgetType.TEXT);
			text.setText(TITLE);
			text.setFontId(FontID.PLAIN_12);
			text.setTextColor(VAULT_ORANGE);
			text.setTextShadowed(true);
			text.setYTextAlignment(WidgetTextAlignment.CENTER);
			text.setWidthMode(first.getWidthMode());
			text.setOriginalWidth(first.getOriginalWidth());
			text.setOriginalX(first.getOriginalX());
			// Clicked and hovered as the game's own entries are, here in the client
			text.setHasListener(true);
			text.setAction(0, SELECT);
			text.setOnOpListener((JavaScriptCallback) this::onTabClicked);
			text.setOnMouseOverListener((JavaScriptCallback) event -> text.setTextColor(WHITE));
			text.setOnMouseLeaveListener((JavaScriptCallback) event -> text.setTextColor(VAULT_ORANGE));
			tab = text;
		}
		tabBackground.setTextColor(dark ? 0 : WHITE);
		tabBackground.setOpacity(dark ? DARK : CLEAR);
		for (Widget widget : new Widget[]{tab, tabBackground})
		{
			widget.setOriginalHeight(tabHeight);
			widget.setOriginalY(tabY);
			widget.setHidden(false);
			widget.revalidate();
		}
		tabShown = true;
		fitList(names);
	}

	/** The section's entry in the category list was picked: scroll to the section, as the game's own entries do. */
	private void onTabClicked(ScriptEvent event)
	{
		final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (event.getOp() == 1 && list != null && vault != null)
		{
			scrollTo(list, Math.max(0, Math.min(vault.top, list.getScrollHeight() - list.getHeight())));
		}
	}

	/** The game's category list entries from {@code fromY} down, and their backgrounds, moved down {@code dy}. */
	private void moveEntries(Widget names, Widget backgrounds, int fromY, int dy)
	{
		for (Widget layer : new Widget[]{names, backgrounds})
		{
			final Widget[] children = layer.getChildren();
			for (int i = 0; children != null && i < children.length; i++)
			{
				final Widget child = children[i];
				if (child != null && child != tab && child != tabBackground && child.getOriginalY() >= fromY)
				{
					moveTo(child, child.getOriginalX(), child.getOriginalY() + dy);
				}
			}
		}
	}

	private Widget backgroundAt(Widget backgrounds, int y)
	{
		final Widget[] children = backgrounds.getChildren();
		for (int i = 0; children != null && i < children.length; i++)
		{
			if (children[i] != null && children[i] != tabBackground && children[i].getOriginalY() == y)
			{
				return children[i];
			}
		}
		return null;
	}

	/** The category list scrolls only once its entries don't fit, as the game has it. */
	private void fitList(Widget names)
	{
		final Widget leftList = client.getWidget(InterfaceID.SeedVault.LEFT_LIST);
		final Widget[] children = names.getChildren();
		if (leftList == null || children == null)
		{
			return;
		}
		int bottom = 0;
		for (Widget child : children)
		{
			if (child != null && !child.isSelfHidden())
			{
				bottom = Math.max(bottom, child.getOriginalY() + child.getOriginalHeight());
			}
		}
		final int scrollHeight = bottom > leftList.getHeight() ? bottom : 0;
		if (scrollHeight == leftList.getScrollHeight())
		{
			return;
		}
		leftList.setScrollHeight(scrollHeight);
		leftList.revalidateScroll();
		final int y = Math.max(0, Math.min(leftList.getScrollY(), scrollHeight - leftList.getHeight()));
		leftList.setScrollY(y);
		clientThread.invokeLater(() ->
		{
			if (client.getWidget(InterfaceID.SeedVault.LEFT_LIST) != null)
			{
				client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.SeedVault.LEFT_SCROLL,
					InterfaceID.SeedVault.LEFT_LIST, y);
			}
		});
	}

	/**
	 * The button by the vault's close button: there while the run has seeds to show, pressed while the view is on, and
	 * flashing (with the fuse round it) while it's off and the vault holds something the run still needs, as the
	 * bank's Farm run button does.
	 *
	 * @param tick a game tick has passed, so the flash moves on
	 */
	private void updateButton(List<Seed> all, boolean tick)
	{
		final boolean show = vault != null && !all.isEmpty();
		calling = show && !viewOn && anyToTake(all);
		final Widget universe = client.getWidget(InterfaceID.SeedVault.UNIVERSE);
		if (universe == null || (button == null && !show))
		{
			return;
		}
		if (button == null)
		{
			button = buttonPart(universe, SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL, BUTTON_SIZE, BUTTON_RIGHT,
				BUTTON_TOP);
			buttonIcon = buttonPart(universe, SpriteID.Staticons2.FARMING, BUTTON_SIZE - 2 * ICON_INSET,
				BUTTON_RIGHT + ICON_INSET, BUTTON_TOP + ICON_INSET);
		}
		if (tick && calling)
		{
			pulseTicks++;
		}
		final boolean lit = viewOn || (calling && pulseTicks % 2 == 0);
		final int sprite = lit ? SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED
			: SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL;
		if (button.getSpriteId() != sprite)
		{
			button.setSpriteId(sprite);
			button.revalidate();
		}
		for (Widget part : new Widget[]{button, buttonIcon})
		{
			part.setAction(0, viewOn ? HIDE : SHOW);
			part.setHidden(!show);
		}
	}

	/** Something the run still needs isn't carried yet, and the vault holds it. */
	private boolean anyToTake(List<Seed> all)
	{
		for (Seed seed : all)
		{
			if (seed.getStatus() != SupplyLine.Status.CARRIED && vault.stocked.contains(seed.getItemId()))
			{
				return true;
			}
		}
		return false;
	}

	/** Half of the button; both take the click, so it works wherever it's pressed, as on the bank's. */
	private Widget buttonPart(Widget universe, int spriteId, int size, int right, int top)
	{
		final Widget part = universe.createChild(-1, WidgetType.GRAPHIC);
		part.setSpriteId(spriteId);
		part.setOriginalWidth(size);
		part.setOriginalHeight(size);
		part.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		part.setOriginalX(right);
		part.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		part.setOriginalY(top);
		part.setName("<col=ff9040>" + TITLE + "</col>");
		part.setHasListener(true);
		part.setOnOpListener((JavaScriptCallback) this::onButtonClicked);
		part.revalidate();
		return part;
	}

	/** The button was clicked: switch the view, and go to the top to show the section when it comes on. */
	private void onButtonClicked(ScriptEvent event)
	{
		final Widget list = client.getWidget(InterfaceID.SeedVault.LIST);
		if (event.getOp() != 1 || list == null || vault == null)
		{
			return;
		}
		viewOn = !viewOn;
		client.playSoundEffect(SoundEffectID.UI_BOOP);
		final List<Seed> all = wanted.get();
		layOut(viewOn ? all : Collections.emptyList(), list, list.getScrollY(), shownHeight, false);
		if (viewOn && shownHeight > 0)
		{
			scrollTo(list, 0);
		}
		updateButton(all, false);
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
		moveItems(placement, icons, names);
		if (placement.sectionY >= 0)
		{
			header(faves, placement.sectionY);
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

	/** Each of the vault's items, icon and name, where the placement says. */
	private static void moveItems(Placement placement, Widget icons, Widget names)
	{
		for (Entry entry : placement.entries.values())
		{
			final Widget icon = icons.getChild(entry.index);
			final Widget name = names.getChild(entry.index);
			if (icon != null && name != null)
			{
				moveTo(icon, entry.iconX, entry.iconY);
				moveTo(name, entry.nameX, entry.nameY);
			}
		}
	}

	/** After a drag: the vault's items back where this laid them out, if it has since the game last did. */
	private void putBack()
	{
		final Widget icons = client.getWidget(InterfaceID.SeedVault.OBJ_LIST);
		final Widget names = client.getWidget(InterfaceID.SeedVault.TEXT_LIST);
		if (placed != null && icons != null && names != null)
		{
			moveItems(placed, icons, names);
		}
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

	/** The section's header and the line under it, like the vault's own categories' (elzerma). */
	private static void header(Widget faves, int y)
	{
		final Widget title = faves.createChild(-1, WidgetType.TEXT);
		title.setText(TITLE);
		title.setFontId(FontID.BOLD_12);
		title.setTextColor(VAULT_ORANGE);
		title.setTextShadowed(true);
		title.setWidthMode(WidgetSizeMode.MINUS);
		title.setOriginalWidth(TITLE_X);
		title.setOriginalHeight(TITLE_HEIGHT);
		title.setOriginalX(TITLE_X);
		title.setOriginalY(y);
		title.revalidate();

		final Widget line = faves.createChild(-1, WidgetType.GRAPHIC);
		line.setSpriteId(SpriteID.TRADEBACKING_DARK);
		line.setSpriteTiling(true);
		line.setWidthMode(WidgetSizeMode.MINUS);
		line.setOriginalWidth(2 * LINE_X);
		line.setOriginalHeight(FarmBankTab.LINE_HEIGHT);
		line.setOriginalX(LINE_X);
		line.setOriginalY(y + SeedVaultLayout.HEADER_TO_LINE);
		line.revalidate();
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
		name.setTextColor(VAULT_ORANGE);
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
		/** The seeds it holds some of. */
		final Set<Integer> stocked;
		/** The scroll height the game set, 0 if everything fitted. */
		final int scrollHeight;
		/** Where the main list starts: below the Farming Contract section, if there is one. */
		final int top;
		final int bottom;

		Vault(int width, List<List<Integer>> categories, Map<Integer, Integer> indexByItem, Set<Integer> stocked,
			int scrollHeight, int top, int bottom)
		{
			this.width = width;
			this.categories = categories;
			this.indexByItem = indexByItem;
			this.stocked = stocked;
			this.scrollHeight = scrollHeight;
			this.top = top;
			this.bottom = bottom;
		}
	}
}
