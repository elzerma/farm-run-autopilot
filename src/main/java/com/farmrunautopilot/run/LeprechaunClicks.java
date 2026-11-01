package com.farmrunautopilot.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

/**
 * The clicks in the tool leprechaun's window for a visit's exact amounts, with his quantity buttons rather than typing
 * an amount (elzerma, 2026-11-01: "13 would change to 5, 2x click bucket remove, change to 1, 3x click bucket remove.
 * change back to 5 ... it says 'Click 3x' so someone can quickly click 3x and as they click the number goes down same
 * tick as when clicked"): fives for every item first, then ones, then back to 5, his default. Each click is counted at
 * once, before the game takes the items a tick later. Client thread.
 */
@Singleton
public class LeprechaunClicks
{
	/** A click not seen applied after this many game ticks didn't happen (refused, or the window closed). */
	static final int PENDING_TICKS = 3;

	/** His quantity buttons, as varbit FARMING_TOOLS_SELECTEDQUANTITY has them (the game's storage scripts). */
	enum Quantity
	{
		ONE, FIVE, ALL, X;

		static Quantity fromVarbit(int value)
		{
			switch (value)
			{
				case 1:
					return FIVE;
				case 2:
					return ALL;
				case 3:
					return X;
				default:
					return ONE;
			}
		}
	}

	/** A side of his window: his store, where things are taken, or the inventory side, where they're stored. */
	enum Side
	{
		TAKE, STORE
	}

	/** What to click next in his window. */
	static final class Next
	{
		/** The quantity the clicks go in: 5 while anything has five or more left, then 1, then 5 again to finish. */
		final Quantity target;
		/** His quantity isn't {@link #target}: its button first. */
		final boolean switchFirst;
		/** Clicks left by item at {@link #target}, on each side. */
		final Map<Integer, Integer> take;
		final Map<Integer, Integer> store;

		Next(Quantity target, boolean switchFirst, Map<Integer, Integer> take, Map<Integer, Integer> store)
		{
			this.target = target;
			this.switchFirst = switchFirst;
			this.take = take;
			this.store = store;
		}
	}

	/**
	 * What to click next for these amounts left to take and store, at this quantity: the quantity button, or each item
	 * with its clicks. Fives for every item before any ones, so the button changes twice a visit at most.
	 */
	static Next next(Map<Integer, Integer> take, Map<Integer, Integer> store, Quantity selected)
	{
		final boolean fives = any(take, 5) || any(store, 5);
		final Quantity target = fives || !any(take, 1) && !any(store, 1) ? Quantity.FIVE : Quantity.ONE;
		return new Next(target, selected != target, clicks(take, target), clicks(store, target));
	}

	private static boolean any(Map<Integer, Integer> amounts, int atLeast)
	{
		return amounts.values().stream().anyMatch(amount -> amount >= atLeast);
	}

	private static Map<Integer, Integer> clicks(Map<Integer, Integer> amounts, Quantity at)
	{
		final Map<Integer, Integer> clicks = new HashMap<>();
		amounts.forEach((item, amount) ->
		{
			final int n = at == Quantity.FIVE ? amount / 5 : amount;
			if (n > 0)
			{
				clicks.put(item, n);
			}
		});
		return clicks;
	}

	/** A click on an item in his window not yet seen applied. */
	static final class Click
	{
		final Side side;
		final int item;
		final int moves;
		/** What was left to move when clicked, so it's known applied once less is left. */
		final int leftThen;
		final int tick;

		Click(Side side, int item, int moves, int leftThen, int tick)
		{
			this.side = side;
			this.item = item;
			this.moves = moves;
			this.leftThen = leftThen;
			this.tick = tick;
		}
	}

	private final Client client;
	private final RunSession session;
	private final List<Click> pending = new ArrayList<>();
	/** {@link #now()} as worked out this client cycle, as every outlined item asks for it each frame. */
	private Next cached;
	private int cachedCycle = -1;

	@Inject
	LeprechaunClicks(Client client, RunSession session)
	{
		this.client = client;
		this.session = session;
	}

	/** The side of his window a widget is on, or null for another. */
	static Side sideOf(Widget widget)
	{
		final int group = widget != null ? WidgetUtil.componentToInterface(widget.getId()) : -1;
		return group == InterfaceID.FARMING_TOOLS ? Side.TAKE : group == InterfaceID.FARMING_TOOLS_SIDE ? Side.STORE : null;
	}

	/** "Click 3x", or "Click" for one. */
	static String clickLabel(int clicks)
	{
		return clicks > 1 ? "Click " + clicks + "x" : "Click";
	}

	/** How many items an option moves: "Remove-5" or "Store-5" 5, the "-1"s 1, others 0 (not counted). */
	static int moves(String option)
	{
		final String text = Text.removeTags(option).toLowerCase();
		return text.endsWith("-5") ? 5 : text.endsWith("-1") ? 1 : 0;
	}

	/** A click on one of the visit's items in his window: counted at once. Client thread. */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		final Widget widget = event.getMenuEntry().getWidget();
		final Side side = sideOf(widget);
		final int moves = moves(event.getMenuOption());
		if (side == null || moves == 0)
		{
			return;
		}
		final Integer amount = amounts(side).get(widget.getItemId());
		if (amount != null)
		{
			pending.add(new Click(side, widget.getItemId(), moves, left(side, widget.getItemId(), amount),
				client.getTickCount()));
			cachedCycle = -1;
		}
	}

	/** His window closed: what was clicked there is either applied or won't be. */
	public void clear()
	{
		pending.clear();
		cachedCycle = -1;
	}

	/** What to click next in his window, as of this frame: the visit's amounts less the clicks not yet applied. */
	Next now()
	{
		if (cached == null || cachedCycle != client.getGameCycle())
		{
			final Map<Integer, Integer> take = new HashMap<>();
			amounts(Side.TAKE).forEach((item, amount) -> take.put(item, left(Side.TAKE, item, amount)));
			final Map<Integer, Integer> store = new HashMap<>();
			amounts(Side.STORE).forEach((item, amount) -> store.put(item, left(Side.STORE, item, amount)));
			cached = next(take, store, Quantity.fromVarbit(client.getVarbitValue(VarbitID.FARMING_TOOLS_SELECTEDQUANTITY)));
			cachedCycle = client.getGameCycle();
		}
		return cached;
	}

	private Map<Integer, Integer> amounts(Side side)
	{
		final Highlights highlights = session.getView().getHighlights();
		final Map<Integer, Integer> amounts = side == Side.TAKE ? highlights.getLeprechaunTake()
			: highlights.getLeprechaunStore();
		return amounts != null ? amounts : Collections.emptyMap();
	}

	private int left(Side side, int item, int amount)
	{
		return left(pending, side, item, amount, client.getTickCount());
	}

	/**
	 * The amount the guide has left for this item, less its clicks not yet applied: a click is applied once the amount is
	 * below what was left when it was clicked, and dropped as not happening after {@link #PENDING_TICKS}. Removes those.
	 */
	static int left(List<Click> pending, Side side, int item, int amount, int tick)
	{
		int clicked = 0;
		for (Iterator<Click> it = pending.iterator(); it.hasNext(); )
		{
			final Click click = it.next();
			final boolean mine = click.side == side && click.item == item;
			if (tick - click.tick > PENDING_TICKS || mine && amount < click.leftThen)
			{
				it.remove();
			}
			else if (mine)
			{
				clicked += click.moves;
			}
		}
		return Math.max(0, amount - clicked);
	}
}
