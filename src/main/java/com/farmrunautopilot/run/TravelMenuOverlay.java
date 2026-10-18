package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import com.farmrunautopilot.route.MenuPick;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Rings the place to pick while travelling there (elzerma, 2026-10-18): the landing site on Renu's map or the quetzal
 * whistle's, or the destination on a hot air balloon's (under the setting for the spell to cast, twice as thick: the
 * maps are big), and the place in a teleport item's list of places (under the setting for items to use). A growing
 * vine, or the item colour's outline with vines off. Only drawn while that map or list is open.
 */
@Slf4j
public class TravelMenuOverlay extends Overlay
{
	/** The balloon map's button for each place, as the atlas names it. */
	private static final Map<String, Integer> BALLOON = Map.of(
		"Castle Wars", InterfaceID.ZepBalloonMap.BTN_CAST,
		"Grand Tree", InterfaceID.ZepBalloonMap.BTN_GNO,
		"Crafting Guild", InterfaceID.ZepBalloonMap.BTN_CRAFT,
		"Entrana", InterfaceID.ZepBalloonMap.BTN_ENT,
		"Taverley", InterfaceID.ZepBalloonMap.BTN_TAV,
		"Varrock", InterfaceID.ZepBalloonMap.BTN_VARR);
	/** How deep under a quetzal map's icons to look for a site. */
	private static final int MAX_DEPTH = 4;

	private final Client client;
	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	private final VineFrame vine = new VineFrame();
	/** A quetzal map already logged as open without the place in it, so the log says so once while it's open. */
	private Widget loggedMissing;

	@Inject
	TravelMenuOverlay(Client client, RunSession session, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.session = session;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Highlights highlights = session.getView().getHighlights();
		Rectangle bounds = null;
		boolean map = false;
		if (config.highlightSpell())
		{
			for (MenuPick pick : highlights.getMenuPicks())
			{
				bounds = bounds(pick);
				if (bounds != null)
				{
					map = true;
					break;
				}
			}
		}
		// A teleport item's list of places (a jewellery item's "Rub", a cape's "Teleport"): the place this leg needs
		if (bounds == null && highlights.getTeleportOption() != null && config.highlightItems())
		{
			bounds = listEntry(highlights.getTeleportOption());
		}
		if (bounds == null)
		{
			vine.clear();
			return null;
		}
		if (config.vineHighlights())
		{
			// Thicker than round a button on the maps, which are big (elzerma, 2026-10-18)
			vine.draw(graphics, bounds, map ? 2 : 1);
			return null;
		}
		graphics.setColor(config.itemColour());
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
		return null;
	}

	/** The entry naming the place in an open list of options (the chatbox's "Select an option"), or null. */
	private Rectangle listEntry(String place)
	{
		final Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (options == null || options.isHidden())
		{
			return null;
		}
		final Widget found = find(options, place.toLowerCase(Locale.ROOT), 0);
		return found != null && !found.isHidden() ? found.getBounds() : null;
	}

	/** Where the place is on its map, if that map is open. */
	private Rectangle bounds(MenuPick pick)
	{
		switch (pick.getMenu())
		{
			case BALLOON:
			{
				final Integer button = BALLOON.get(pick.getPlace());
				final Widget widget = button != null ? client.getWidget(button) : null;
				return widget != null && !widget.isHidden() ? widget.getBounds() : null;
			}
			case RENU:
				return site(InterfaceID.QuetzalMenu.ICONS, pick.getPlace());
			case WHISTLE:
				return site(InterfaceID.QuetzalwhistleMenu.ICONS, pick.getPlace());
			default:
				return null;
		}
	}

	/** A landing site on a quetzal map: the icon whose name, text or option names the place. */
	private Rectangle site(int icons, String place)
	{
		final Widget root = client.getWidget(icons);
		if (root == null || root.isHidden())
		{
			return null;
		}
		final Widget found = find(root, place.toLowerCase(Locale.ROOT), 0);
		if (found == null && loggedMissing != root)
		{
			// The map's icons as the game names them, to put right what the site is called
			loggedMissing = root;
			final List<String> names = new ArrayList<>();
			collect(root, names, 0);
			log.debug("Travel map open without {}: {}", place, names);
		}
		return found != null && !found.isHidden() ? found.getBounds() : null;
	}

	private static Widget find(Widget widget, String place, int depth)
	{
		if (depth > 0 && names(widget).contains(place))
		{
			return widget;
		}
		if (depth >= MAX_DEPTH)
		{
			return null;
		}
		for (Widget[] children : new Widget[][]{widget.getDynamicChildren(), widget.getStaticChildren(),
			widget.getNestedChildren()})
		{
			for (Widget child : children)
			{
				final Widget found = child != null ? find(child, place, depth + 1) : null;
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	/** A widget's name, text and options in lower case, without colour tags. */
	private static String names(Widget widget)
	{
		final StringBuilder s = new StringBuilder();
		s.append(widget.getName() != null ? Text.removeTags(widget.getName()) : "").append('|')
			.append(widget.getText() != null ? Text.removeTags(widget.getText()) : "");
		final String[] actions = widget.getActions();
		if (actions != null)
		{
			for (String action : actions)
			{
				if (action != null)
				{
					s.append('|').append(Text.removeTags(action));
				}
			}
		}
		return s.toString().toLowerCase(Locale.ROOT);
	}

	private static void collect(Widget widget, List<String> names, int depth)
	{
		if (depth > 0)
		{
			final String name = names(widget);
			if (name.replace("|", "").trim().length() > 0)
			{
				names.add(name);
			}
		}
		if (depth >= MAX_DEPTH)
		{
			return;
		}
		for (Widget[] children : new Widget[][]{widget.getDynamicChildren(), widget.getStaticChildren(),
			widget.getNestedChildren()})
		{
			for (Widget child : children)
			{
				if (child != null)
				{
					collect(child, names, depth + 1);
				}
			}
		}
	}
}
