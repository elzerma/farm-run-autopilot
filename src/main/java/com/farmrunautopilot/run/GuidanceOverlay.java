package com.farmrunautopilot.run;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Text under the player: what to do next, and the run timer (SPEC 13.4). Only reads the session's
 * precomputed view, so rendering stays cheap.
 */
public class GuidanceOverlay extends Overlay
{
	private static final Color INSTRUCTION = new Color(0xFF, 0xD7, 0x00);
	private static final Color TIMER = Color.WHITE;
	private static final Color REMINDER = new Color(0xFF, 0x98, 0x1F);
	/** Long instructions wrap at this width (pixels). */
	private static final int MAX_LINE_WIDTH = 360;
	/** Pixels below the player's feet for the first line, and between lines. */
	private static final int FIRST_LINE_OFFSET = 18;
	private static final int LINE_HEIGHT = 15;

	private final Client client;
	private final RunSession session;

	@Inject
	GuidanceOverlay(Client client, RunSession session)
	{
		this.client = client;
		this.session = session;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final RunView view = session.getView();
		final String instruction = view.getInstruction();
		final Player player = client.getLocalPlayer();
		if (instruction == null || player == null)
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeBoldFont());
		final int maxWidth = Math.min(MAX_LINE_WIDTH, client.getCanvasWidth() - 40);
		int line = 0;
		if (view.getState() == RunView.State.RUNNING)
		{
			final long seconds = (System.currentTimeMillis() - view.getStartedAtMillis()) / 1000;
			draw(graphics, player, clock(seconds), TIMER, line++);
		}
		for (String text : wrap(graphics.getFontMetrics(), instruction, maxWidth))
		{
			draw(graphics, player, text, INSTRUCTION, line++);
		}
		if (view.getReminder() != null)
		{
			draw(graphics, player, view.getReminder(), REMINDER, line);
		}
		return null;
	}

	/** Splits text into lines no wider than maxWidth pixels, breaking between words. */
	static List<String> wrap(FontMetrics metrics, String text, int maxWidth)
	{
		final List<String> lines = new ArrayList<>();
		final StringBuilder current = new StringBuilder();
		for (String word : text.split(" "))
		{
			if (current.length() > 0 && metrics.stringWidth(current + " " + word) > maxWidth)
			{
				lines.add(current.toString());
				current.setLength(0);
			}
			if (current.length() > 0)
			{
				current.append(' ');
			}
			current.append(word);
		}
		if (current.length() > 0)
		{
			lines.add(current.toString());
		}
		return lines;
	}

	private static void draw(Graphics2D graphics, Player player, String text, Color colour, int line)
	{
		final Point feet = player.getCanvasTextLocation(graphics, text, 0);
		if (feet != null)
		{
			OverlayUtil.renderTextLocation(graphics,
				new Point(feet.getX(), feet.getY() + FIRST_LINE_OFFSET + line * LINE_HEIGHT), text, colour);
		}
	}

	/** e.g. "4:07" or "1:02:30". */
	public static String clock(long seconds)
	{
		final long h = seconds / 3600;
		final long m = (seconds % 3600) / 60;
		final long s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}
}
