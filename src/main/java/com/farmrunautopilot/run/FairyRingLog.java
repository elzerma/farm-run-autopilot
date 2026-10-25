package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
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
			scrolled = false;
			return;
		}
		final String code = config.highlightItems() ? session.fairyRingCode() : null;
		if (code == null)
		{
			return;
		}
		final Widget fave = find(client.getWidget(InterfaceID.FairyringsLog.FAVES), code, false);
		final Widget listed = find(contents, code, true);
		final int colour = config.itemColour().getRGB() & 0xFFFFFF;
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
