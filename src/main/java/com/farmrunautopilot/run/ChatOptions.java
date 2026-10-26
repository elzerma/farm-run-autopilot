package com.farmrunautopilot.run;

import com.farmrunautopilot.FarmRunAutopilotConfig;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * The option a step needs in an open chatbox list ("Select an option", "Your hands are full."): coloured in the item
 * colour, with its number to press shown under the step (elzerma, 2026-10-25: "any dialogue options to be highlighted
 * and number to be listed to press"). A ride's line (a rowboat's "Row out to sea, north of the island") or the place in
 * a teleport item's list. Colour only: the game's text is left as it is. Client thread.
 */
@Singleton
public class ChatOptions
{
	private final Client client;
	private final FarmRunAutopilotConfig config;
	/** The number to press for the option in the open list, or 0 when none is showing. */
	private int press;

	@Inject
	ChatOptions(Client client, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** Each client tick, as the list is built by a script after it opens. */
	public void onPostClientTick(RunSession session)
	{
		press = 0;
		final Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		if (options == null || options.isHidden() || !config.highlightItems())
		{
			return;
		}
		final String wanted = session.chatOption();
		if (wanted == null)
		{
			return;
		}
		final String text = wanted.toLowerCase(Locale.ROOT);
		final Widget[] children = options.getDynamicChildren();
		// The first line with text is the list's title; the options after it are numbered from 1
		int number = -1;
		for (Widget child : children)
		{
			if (child == null || child.isHidden() || child.getText() == null
				|| Text.removeTags(child.getText()).trim().isEmpty())
			{
				continue;
			}
			number++;
			if (number > 0 && Text.removeTags(child.getText()).toLowerCase(Locale.ROOT).contains(text))
			{
				child.setTextColor(config.itemColour().getRGB() & 0xFFFFFF);
				press = number;
				return;
			}
		}
	}

	/** "Press 4" while the option is showing in an open list, else null. */
	String reminder()
	{
		return press > 0 ? "Press " + press : null;
	}
}
