package com.farmrunautopilot.bank;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.plugins.bank.BankSearch;

/**
 * The "Farm run" button in the bank title bar (SPEC 13.3). Clicking it switches the bank into the farm
 * run view and back. Client-side only: it changes how the bank is drawn, never sends anything to the
 * server.
 *
 * <p>Follows Quest Helper's {@code QuestBankTabInterface} (BSD-2, see THIRD_PARTY_NOTICES), except that it
 * never closes the potion store for the player (that would be a scripted click); it asks them to instead.
 * All methods run on the client thread.
 */
@Singleton
class FarmBankTabButton
{
	static final String NAME = "farm-run-autopilot";
	private static final String VIEW_TAB = "View tab ";
	private static final int BANKTAB_POTIONSTORE = 15;
	private static final int BUTTON_SIZE = 25;
	/** Between Quest Helper's button (x 408, 25 wide) and the bank's close button. */
	private static final int BUTTON_X = 434;
	private static final int BUTTON_Y = 5;

	private final Client client;
	private final ClientThread clientThread;
	private final BankSearch bankSearch;
	private final ChatMessageManager chatMessageManager;

	@Getter
	private boolean active;
	private Widget background;
	private Widget icon;

	@Inject
	FarmBankTabButton(Client client, ClientThread clientThread, BankSearch bankSearch,
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.bankSearch = bankSearch;
		this.chatMessageManager = chatMessageManager;
	}

	/** Adds the button when the bank opens. */
	void init()
	{
		if (isBankHidden())
		{
			return;
		}
		final Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		background = createGraphic(parent, NAME, SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL, BUTTON_SIZE,
			BUTTON_X, BUTTON_Y);
		icon = createGraphic(parent, NAME, SpriteID.Staticons2.FARMING, BUTTON_SIZE - 6, BUTTON_X + 3, BUTTON_Y + 3);
		// Both halves take the click, so it works wherever the button is pressed
		for (Widget part : new Widget[]{background, icon})
		{
			part.setAction(1, VIEW_TAB);
			part.setOnOpListener((JavaScriptCallback) this::onClick);
		}

		// Reopened the bank with the tab still selected: show it again.
		if (active)
		{
			active = false;
			clientThread.invokeLater(this::activate);
		}
	}

	/** Removes the button and goes back to the normal bank. */
	void destroy()
	{
		if (active)
		{
			close();
			bankSearch.reset(true);
		}
		if (icon != null)
		{
			icon.setHidden(true);
		}
		if (background != null)
		{
			background.setHidden(true);
		}
		active = false;
	}

	/** Leaves the farm run view when the player picks another tab. */
	void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!active || isBankHidden())
		{
			return;
		}
		final String option = event.getMenuOption();
		final boolean otherTab = option.startsWith("View tab") && !event.getMenuTarget().equals(NAME);
		if (otherTab || option.equals("View all items") || option.startsWith("View tag tab")
			|| option.startsWith("Potion store"))
		{
			close();
		}
	}

	/** Leaves the farm run view when the player starts a bank search. */
	void onSearchToggled()
	{
		if (active)
		{
			close();
			// Open the search box rather than the client trying to close it first
			client.setVarcStrValue(VarClientID.MESLAYERINPUT, "");
			client.setVarcIntValue(VarClientID.MESLAYERMODE, 0);
		}
	}

	private void onClick(ScriptEvent event)
	{
		if (event.getOp() != 2)
		{
			return;
		}
		if (client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == BANKTAB_POTIONSTORE)
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage("Close the potion store first, then open the farm run tab.")
				.build());
			return;
		}

		client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
		if (active)
		{
			close();
			bankSearch.reset(true);
		}
		else
		{
			activate();
		}
		client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	private void activate()
	{
		if (active || background == null)
		{
			return;
		}
		background.setSpriteId(SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED);
		background.revalidate();
		active = true;
		// Rebuild the bank in "searching" mode so every item is available to lay out
		bankSearch.reset(true);
		clearSearchButton();
	}

	private void close()
	{
		active = false;
		if (background != null)
		{
			background.setSpriteId(SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
			background.revalidate();
		}
	}

	/**
	 * Going from a real search to our "searching" view would leave the search button's timer running and
	 * its background highlighted, so clear both (as bankmain_search_setbutton does).
	 */
	private void clearSearchButton()
	{
		final Widget search = client.getWidget(InterfaceID.Bankmain.SEARCH);
		if (search != null)
		{
			search.setOnTimerListener((Object[]) null);
			search.setSpriteId(SpriteID.Miscgraphics.EQUIPMENT_SLOT_TILE);
		}
	}

	private boolean isBankHidden()
	{
		final Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank == null || bank.isHidden();
	}

	private static Widget createGraphic(Widget parent, String name, int spriteId, int size, int x, int y)
	{
		final Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(size);
		widget.setOriginalHeight(size);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.setHasListener(true);
		widget.setName(name);
		widget.revalidate();
		return widget;
	}
}
