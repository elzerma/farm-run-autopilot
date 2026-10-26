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
 * colour, with its number to press in front of it (elzerma, 2026-10-25: "any dialogue options to be highlighted and
 * number to be listed to press", then "add the number to select in the dialogue box"). A ride's line (a rowboat's
 * "Row out to sea, north of the island") or the place in a teleport item's list. Client thread.
 */
@Singleton
public class ChatOptions
{
	private final Client client;
	private final FarmRunAutopilotConfig config;

	@Inject
	ChatOptions(Client client, FarmRunAutopilotConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** Each client tick, as the list is built by a script after it opens. */
	public void onPostClientTick(RunSession session)
	{
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
				// Its number in front, once (elzerma, 2026-10-25: "add the number to select in the dialogue box")
				if (!Text.removeTags(child.getText()).startsWith(number + ": "))
				{
					child.setText(number + ": " + child.getText());
				}
				return;
			}
		}
	}
}
