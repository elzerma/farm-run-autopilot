package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * The leg's fairy ring code in the fairy ring's travel log, as Shortest Path does it (elzerma, 2026-10-25): the code
 * coloured in the item colour, in the favourites or the list, and the list scrolled to it once as the log opens.
 * Clicking the code sets the dials. Colour only: the game's text is left as it is. Client thread.
 */
@Singleton
public class FairyRingLog
{
	private final Client client;
	private final RunSession session;
	private final FarmRunAutopilotConfig config;
	/** The list already scrolled to the code since the log opened, so the player can scroll it themselves. */
	private boolean scrolled;
	/** Where the code is in the open log, clipped to the list it's in, for the vine round it; null when not showing. */
	private Rectangle entry;

	@Inject
	FairyRingLog(Client client, RunSession session, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.session = session;
		this.config = config;
	}

	/** Each client tick: the log is built by a script after it loads, so its entries are looked for while it's open. */
	public void onPostClientTick()
	{
		final Widget contents = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
		if (contents == null || contents.isHidden())
		{
			entry = null;
			scrolled = false;
			return;
		}
		final String code = config.highlightItems() ? session.fairyRingCode() : null;
		if (code == null)
		{
			entry = null;
			return;
		}
		final Widget fave = find(client.getWidget(InterfaceID.FairyringsLog.FAVES), code, false);
		final Widget listed = find(contents, code, true);
		final int colour = config.itemColour().getRGB() & 0xFFFFFF;
		entry = fave != null ? visible(fave, client.getWidget(InterfaceID.FairyringsLog.FAVES))
			: listed != null ? visible(listed, contents) : null;
		if (fave != null)
		{
			fave.setTextColor(colour);
		}
		if (listed == null)
		{
			return;
		}
		listed.setTextColor(colour);
		if (scrolled || fave != null)
		{
			return;
		}
		scrolled = true;
		final int y = Math.max(0, Math.min(listed.getRelativeY(), contents.getScrollHeight() - contents.getHeight()));
		contents.setScrollY(y);
		contents.revalidateScroll();
		client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.FairyringsLog.SCROLLBAR,
			InterfaceID.FairyringsLog.CONTENTS, y);
	}

	/** Where the leg's code is in the open travel log, for the vine round it; null if it isn't showing. */
	Rectangle entryBounds()
	{
		return entry;
	}

	/** The entry's bounds within its list's, or null when it's scrolled out of sight. */
	private static Rectangle visible(Widget widget, Widget list)
	{
		if (widget.isHidden() || list == null || list.isHidden())
		{
			return null;
		}
		final Rectangle shown = widget.getBounds().intersection(list.getBounds());
		return shown.isEmpty() ? null : shown;
	}

	/** The entry whose text is the code ("C I P" or "CIP"), or null. */
	private static Widget find(Widget parent, String code, boolean dynamic)
	{
		if (parent == null)
		{
			return null;
		}
		final Widget[] children = dynamic ? parent.getDynamicChildren() : parent.getStaticChildren();
		for (Widget child : children)
		{
			if (child != null && child.getText() != null
				&& Text.removeTags(child.getText()).replace(" ", "").equalsIgnoreCase(code))
			{
				return child;
			}
		}
		return null;
	}
}
