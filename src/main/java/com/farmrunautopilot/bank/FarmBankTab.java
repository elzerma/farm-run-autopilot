package com.farmrunautopilot.bank;

import static net.runelite.client.plugins.banktags.BankTagsPlugin.BANK_ITEM_HEIGHT;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.BANK_ITEM_WIDTH;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.BANK_ITEM_X_PADDING;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.BANK_ITEM_Y_PADDING;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.SupplyLine;
import com.farmrunautopilot.supply.SupplyPlan;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.ParamID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * Draws the "Farm run" bank view (SPEC 13.3): the normal items are hidden and the plan's items are laid
 * out in sections (Travel, Runes, Seeds & saplings, ...) with "/ N" needed text. Items the bank doesn't hold
 * are shown faded, with a "Details" option. Everything else in the bank follows at the end so it stays
 * usable.
 *
 * <p>Adapted from Quest Helper's {@code QuestBankTab} (BSD-2, see THIRD_PARTY_NOTICES). The bank is put in
 * its "searching" state so every item widget exists, then those widgets are reused and moved. Withdrawing
 * works because menu clicks are re-pointed at the item's real bank slot. All methods run on the client
 * thread.
 */
@Singleton
public class FarmBankTab
{
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_VERTICAL_SPACING = 36;
	private static final int ITEM_HORIZONTAL_SPACING = 48;
	private static final int ITEM_ROW_START = 51;
	private static final int LINE_VERTICAL_SPACING = 5;
	private static final int LINE_HEIGHT = 2;
	private static final int TEXT_HEIGHT = 15;
	private static final int ICON_SIZE = 10;
	/** Planned items get taller rows, with room for the "/ N" label under each item. */
	private static final int PLANNED_ROW_SPACING = 50;
	private static final int ITEM_HEIGHT = 32;
	private static final int LABEL_WIDTH = 46;
	private static final int LABEL_HEIGHT = 14;
	private static final int FADED = 120;
	private static final Color HEADER = new Color(228, 216, 162);
	private static final Color CARRIED = new Color(0x4C, 0xC1, 0x52);
	private static final Color IN_STORAGE = new Color(0xE8, 0xC5, 0x3A);
	private static final Color MISSING = new Color(0xE0, 0x55, 0x55);
	private static final String REST_OF_BANK = "Everything else";

	private final Client client;
	private final ClientThread clientThread;
	private final ChatMessageManager chatMessageManager;
	private final FarmBankTabButton button;

	/** Widgets this tab created, removed before every rebuild. */
	private final List<Widget> addedWidgets = new ArrayList<>();
	/** The checklist line shown in each reused item widget, for "Details". */
	private final Map<Widget, SupplyLine> lineByWidget = new HashMap<>();
	private int originalContainerChildren = -1;
	private Supplier<SupplyPlan> plan = () -> SupplyPlan.EMPTY;

	@Inject
	FarmBankTab(Client client, ClientThread clientThread, ChatMessageManager chatMessageManager,
		FarmBankTabButton button)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.chatMessageManager = chatMessageManager;
		this.button = button;
	}

	public void startUp(Supplier<SupplyPlan> plan)
	{
		this.plan = plan;
		clientThread.invokeLater(button::init);
	}

	public void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			button.destroy();
			removeAddedWidgets();
		});
	}

	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			button.init();
		}
	}

	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		// Only ever say "yes"; leave the answer alone otherwise so the Bank Tags plugin's own tabs still work.
		if ("getSearchingTagTab".equals(event.getEventName()) && button.isActive())
		{
			client.getIntStack()[client.getIntStackSize() - 1] = 1;
		}
	}

	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			resetItemWidgetSizes();
			if (button.isActive())
			{
				final Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
				if (title != null)
				{
					title.setText("Tab <col=ff0000>Farm run</col>");
				}
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			button.onSearchToggled();
		}
	}

	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			// Make bankmain_searching return true so the bank builds every item for us to lay out.
			if (button.isActive())
			{
				client.getIntStack()[client.getIntStackSize() - 1] = 1;
			}
			return;
		}
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}

		removeAddedWidgets();
		if (!button.isActive())
		{
			return;
		}
		final Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (itemContainer == null)
		{
			return;
		}
		final Widget[] children = itemContainer.getChildren();
		if (children != null && originalContainerChildren == -1)
		{
			originalContainerChildren = children.length;
		}
		final Widget[] itemWidgets = itemContainer.getDynamicChildren();
		clientThread.invokeAtTickEnd(() -> layOut(itemContainer, itemWidgets, plan.get()));
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		button.onMenuOptionClicked(event);
		if (event.getParam1() != InterfaceID.Bankmain.ITEMS || !button.isActive())
		{
			return;
		}

		final MenuEntry menu = event.getMenuEntry();
		if ("Details".equals(menu.getOption()))
		{
			event.consume();
			final SupplyLine line = lineByWidget.get(event.getWidget());
			if (line != null)
			{
				showDetails(line);
			}
			return;
		}

		// Items were moved, so point the click at the item's real bank slot.
		final Widget widget = menu.getWidget();
		final ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (widget != null && widget.getItemId() > -1 && bank != null)
		{
			final int slot = bank.find(widget.getItemId());
			if (slot > -1 && menu.getParam0() != slot)
			{
				menu.setParam0(slot);
			}
		}
	}

	/**
	 * Redraw if the farm run view is showing, e.g. after the plan changed. Re-lays out the widgets already
	 * there instead of rebuilding the bank, so the scroll position stays put. Call on the client thread.
	 */
	public void refresh()
	{
		if (!button.isActive())
		{
			return;
		}
		final Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (itemContainer == null || itemContainer.isHidden())
		{
			return;
		}
		removeAddedWidgets();
		layOut(itemContainer, itemContainer.getDynamicChildren(), plan.get());
	}

	private void layOut(Widget itemContainer, Widget[] itemWidgets, SupplyPlan plan)
	{
		final ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return;
		}
		lineByWidget.clear();
		hideBankWidgets(itemContainer, itemWidgets);

		// Group the plan's lines, then add the rest of the bank so it stays usable
		final Map<SupplyLine.Group, List<SupplyLine>> sections = new EnumMap<>(SupplyLine.Group.class);
		final Set<Integer> planned = new HashSet<>();
		for (SupplyLine line : plan.getLines())
		{
			sections.computeIfAbsent(line.getGroup(), k -> new ArrayList<>()).add(line);
			for (int id : line.getItemIds())
			{
				planned.add(id);
			}
		}
		final Set<Integer> rest = new LinkedHashSet<>();
		for (Item item : bank.getItems())
		{
			if (item.getId() > -1 && item.getId() != ItemID.BLANKOBJECT && !planned.contains(item.getId()))
			{
				rest.add(item.getId());
			}
		}

		final List<PendingText> texts = new ArrayList<>();
		int height = 0;
		int nextWidget = 0;
		for (Map.Entry<SupplyLine.Group, List<SupplyLine>> section : sections.entrySet())
		{
			height = addHeader(itemContainer, section.getKey().getDisplayName(), height);
			int added = 0;
			for (SupplyLine line : section.getValue())
			{
				final Widget widget = itemContainer.getChild(nextWidget++);
				if (widget == null)
				{
					break;
				}
				drawItem(widget, displayItem(line, bank), bank, line);
				placeItem(widget, added++, height, PLANNED_ROW_SPACING);
				texts.add(neededText(widget, line));
			}
			height = rowsEnd(height, added, PLANNED_ROW_SPACING);
		}

		if (!rest.isEmpty())
		{
			height = addHeader(itemContainer, REST_OF_BANK, height);
			int added = 0;
			for (int id : rest)
			{
				final Widget widget = itemContainer.getChild(nextWidget++);
				if (widget == null)
				{
					break;
				}
				drawItem(widget, id, bank, null);
				placeItem(widget, added++, height, ITEM_VERTICAL_SPACING);
			}
			height = rowsEnd(height, added, ITEM_VERTICAL_SPACING);
		}

		// Labels go on last so they draw over everything: text, then tick or cross
		for (PendingText text : texts)
		{
			final Widget label = createText(itemContainer, text.text, text.colour, LABEL_WIDTH - ICON_SIZE - 2,
				LABEL_HEIGHT, text.x + 2, text.y);
			label.setFontId(FontID.BOLD_12);
			label.setYTextAlignment(WidgetTextAlignment.CENTER);
			label.revalidate();
			addedWidgets.add(label);
			if (text.spriteId != -1)
			{
				addedWidgets.add(createIcon(itemContainer, text.spriteId, text.x + LABEL_WIDTH - ICON_SIZE - 1,
					text.y + (LABEL_HEIGHT - ICON_SIZE) / 2));
			}
		}

		itemContainer.setScrollHeight(Math.max(height, itemContainer.getHeight()));
		final int scroll = itemContainer.getScrollY();
		clientThread.invokeLater(() -> client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Bankmain.SCROLLBAR,
			InterfaceID.Bankmain.ITEMS, scroll));
	}

	/** The variant the bank holds most of (e.g. which charge of a ring), or the first one if none. */
	private static int displayItem(SupplyLine line, ItemContainer bank)
	{
		int best = line.getItemIds()[0];
		int bestCount = 0;
		for (int id : line.getItemIds())
		{
			final int n = bank.count(id);
			if (n > bestCount)
			{
				best = id;
				bestCount = n;
			}
		}
		return best;
	}

	private void drawItem(Widget widget, int itemId, ItemContainer bank, SupplyLine line)
	{
		final int qty = bank.count(itemId);
		final ItemComposition def = client.getItemDefinition(itemId);
		widget.setItemId(itemId);
		widget.setItemQuantity(qty);
		widget.setItemQuantityMode(ItemQuantityMode.ALWAYS);
		// Effectively stops dragging, which would move the wrong slot
		widget.setDragDeadTime(1000);
		widget.setName("<col=ff9040>" + def.getName() + "</col>");
		widget.clearActions();

		if (def.getPlaceholderTemplateId() >= 0 && def.getPlaceholderId() >= 0)
		{
			// The bank's own placeholder
			widget.setOpacity(FADED);
			widget.setAction(7, "Release");
			widget.setAction(9, "Examine");
		}
		else if (qty == 0)
		{
			// Not in the bank: a faded stand-in that explains where it is
			widget.setOpacity(FADED);
			widget.setItemQuantity(0);
			widget.setItemQuantityMode(ItemQuantityMode.NEVER);
			widget.setAction(1, "Details");
		}
		else
		{
			setWithdrawActions(widget, def);
			widget.setOpacity(0);
		}

		widget.setOnDragListener(ScriptID.BANKMAIN_DRAGSCROLL, ScriptEvent.WIDGET_ID, ScriptEvent.WIDGET_INDEX,
			ScriptEvent.MOUSE_X, ScriptEvent.MOUSE_Y, InterfaceID.Bankmain.SCROLLBAR, 0);
		widget.setOnDragCompleteListener((JavaScriptCallback) ev ->
		{
		});
		if (line != null)
		{
			lineByWidget.put(widget, line);
		}
		widget.setHidden(false);
		widget.revalidate();
	}

	/** The same withdraw options the bank shows (~script669), following the bank's quantity setting. */
	private void setWithdrawActions(Widget widget, ItemComposition def)
	{
		final int quantityType = client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE);
		final int requested = client.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY);
		final String suffix;
		switch (quantityType)
		{
			case 1:
				suffix = "5";
				break;
			case 2:
				suffix = "10";
				break;
			case 3:
				suffix = Integer.toString(Math.max(1, requested));
				break;
			case 4:
				suffix = "All";
				break;
			default:
				suffix = "1";
				break;
		}
		widget.setAction(0, "Withdraw-" + suffix);
		if (quantityType != 0)
		{
			widget.setAction(1, "Withdraw-1");
		}
		widget.setAction(2, "Withdraw-5");
		widget.setAction(3, "Withdraw-10");
		if (requested > 0)
		{
			widget.setAction(4, "Withdraw-" + requested);
		}
		widget.setAction(5, "Withdraw-X");
		widget.setAction(6, "Withdraw-All");
		widget.setAction(7, "Withdraw-All-but-1");
		if (client.getVarbitValue(VarbitID.BANK_BANKOPS_TOGGLE_ON) == 1 && def.getIntValue(ParamID.BANK_AUTOCHARGE) != -1)
		{
			widget.setAction(8, "Configure-Charges");
		}
		if (client.getVarbitValue(VarbitID.BANK_LEAVEPLACEHOLDERS) == 0)
		{
			widget.setAction(9, "Placeholder");
		}
		widget.setAction(10, "Examine");
	}

	/** "/ N" under the item, in bold, coloured like the sidebar, ticked when carried, crossed when missing. */
	private static PendingText neededText(Widget widget, SupplyLine line)
	{
		final String needed = "/ " + QuantityFormatter.quantityToStackSize(line.getNeed());
		// The item is 36 wide in a 48 wide slot; centre the label under it.
		final int x = widget.getOriginalX() - (LABEL_WIDTH - BANK_ITEM_WIDTH) / 2;
		final int y = widget.getOriginalY() + ITEM_HEIGHT + 1;

		final Color colour;
		final int sprite;
		switch (line.getStatus())
		{
			case CARRIED:
				colour = CARRIED;
				sprite = SpriteID.Checkbox.CHECKED;
				break;
			case IN_STORAGE:
				colour = IN_STORAGE;
				sprite = -1;
				break;
			default:
				colour = MISSING;
				sprite = SpriteID.Checkbox.CROSSED;
				break;
		}
		return new PendingText(needed, colour.getRGB(), x, y, sprite);
	}
	private void showDetails(SupplyLine line)
	{
		final List<String> where = new ArrayList<>();
		for (Map.Entry<Holdings.Source, Integer> e : line.getWhere().entrySet())
		{
			where.add(QuantityFormatter.formatNumber(e.getValue()) + " in your " + e.getKey().getLabel());
		}
		final String message = "You need " + QuantityFormatter.formatNumber(line.getNeed()) + " x "
			+ Text.removeTags(line.getName()) + ". "
			+ (where.isEmpty() ? "You don't have any." : "You have " + String.join(", ", where) + ".");
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.ITEM_EXAMINE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	private void hideBankWidgets(Widget itemContainer, Widget[] itemWidgets)
	{
		for (int i = 0; i < itemWidgets.length; i++)
		{
			final Widget widget = itemContainer.getChild(i);
			if (widget == null)
			{
				continue;
			}
			final boolean item = !widget.isSelfHidden() && widget.getItemId() > -1
				&& widget.getItemId() != ItemID.BLANKOBJECT;
			final boolean tabDivider = widget.getSpriteId() == SpriteID.TRADEBACKING_DARK
				|| widget.getText().contains("Tab");
			if (item || tabDivider)
			{
				widget.setHidden(true);
			}
		}
	}

	/**
	 * Layouts change item widget sizes, which the bank only sets when it opens, so put them back before
	 * every build.
	 */
	private void resetItemWidgetSizes()
	{
		final Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.getChildren() == null)
		{
			return;
		}
		for (Widget child : container.getChildren())
		{
			if (child.getOriginalHeight() < BANK_ITEM_HEIGHT)
			{
				break;
			}
			if (child.getOriginalWidth() != BANK_ITEM_WIDTH || child.getOriginalHeight() != BANK_ITEM_HEIGHT)
			{
				child.setOriginalWidth(BANK_ITEM_WIDTH);
				child.setOriginalHeight(BANK_ITEM_HEIGHT);
				child.revalidate();
			}
		}
	}

	private void removeAddedWidgets()
	{
		if (originalContainerChildren == -1 || addedWidgets.isEmpty())
		{
			return;
		}
		final Widget parent = addedWidgets.get(0).getParent();
		if (parent != null && parent.getChildren() != null)
		{
			parent.setChildren(Arrays.copyOf(parent.getChildren(), originalContainerChildren));
			parent.revalidate();
		}
		addedWidgets.clear();
	}

	private int addHeader(Widget container, String title, int height)
	{
		addedWidgets.add(createLine(container, height));
		addedWidgets.add(createText(container, title, HEADER.getRGB(), ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING + ITEM_ROW_START,
			TEXT_HEIGHT, ITEM_ROW_START, height + LINE_VERTICAL_SPACING));
		return height + LINE_VERTICAL_SPACING + TEXT_HEIGHT;
	}

	private static int rowsEnd(int height, int items, int rowSpacing)
	{
		final int rows = (items + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
		return height + rows * rowSpacing;
	}

	private static void placeItem(Widget widget, int index, int sectionTop, int rowSpacing)
	{
		final int y = sectionTop + (index / ITEMS_PER_ROW) * rowSpacing;
		final int x = (index % ITEMS_PER_ROW) * ITEM_HORIZONTAL_SPACING + ITEM_ROW_START;
		if (widget.getOriginalY() != y || widget.getOriginalX() != x)
		{
			widget.setOriginalY(y);
			widget.setOriginalX(x);
			widget.revalidate();
		}
	}

	private static Widget createLine(Widget container, int y)
	{
		final Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING);
		widget.setOriginalHeight(LINE_HEIGHT);
		widget.setOriginalX(ITEM_ROW_START);
		widget.setOriginalY(y);
		widget.setSpriteId(SpriteID.TRADEBACKING_DARK);
		widget.revalidate();
		return widget;
	}

	private static Widget createText(Widget container, String text, int colour, int width, int height, int x, int y)
	{
		final Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setText(text);
		widget.setFontId(FontID.PLAIN_11);
		widget.setTextColor(colour);
		widget.setTextShadowed(true);
		widget.revalidate();
		return widget;
	}

	private static Widget createIcon(Widget container, int spriteId, int x, int y)
	{
		final Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(ICON_SIZE);
		widget.setOriginalHeight(ICON_SIZE);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	/** A "/ N" label to add once every item is placed, so it draws on top. */
	private static final class PendingText
	{
		final String text;
		final int colour;
		final int x;
		final int y;
		final int spriteId;

		PendingText(String text, int colour, int x, int y, int spriteId)
		{
			this.text = text;
			this.colour = colour;
			this.x = x;
			this.y = y;
			this.spriteId = spriteId;
		}
	}
}
