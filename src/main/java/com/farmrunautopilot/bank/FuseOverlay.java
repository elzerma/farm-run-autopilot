package com.farmrunautopilot.bank;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A lit fuse round the bank's Farm run button while the guide is waiting for it to be clicked (issue #5,
 * elzerma): a spark runs round the button like marquee lights, the fuse behind it glows, and little pixel
 * figures leap off where the spark passes. Drawing only; the button itself is untouched.
 */
public class FuseOverlay extends Overlay
{
	/** One lap of the spark. */
	private static final long LAP_MILLIS = 1400;
	/** How much of the lap glows behind the spark. */
	private static final double TRAIL = 0.45;
	/** Room between the button and the fuse. */
	private static final int GAP = 2;
	/** Marquee bulbs every few pixels. */
	private static final int BULB_SPACING = 4;
	private static final long EMIT_MILLIS = 80;
	private static final long FIGURE_LIFE_MILLIS = 750;
	private static final int MAX_FIGURES = 30;
	/** Pixels per second squared. */
	private static final double GRAVITY = 220;
	/** A tiny figure, 3 x 4 pixels: head, arms out, body, legs apart. */
	private static final String[] FIGURE = {".X.", "XXX", ".X.", "X.X"};

	private static final Color FUSE = new Color(0xFF, 0x98, 0x1F, 70);
	private static final Color GLOW = new Color(0xFF, 0x98, 0x1F);
	private static final Color SPARK_HALO = new Color(0xFF, 0xB0, 0x30, 120);
	private static final Color SPARK = new Color(0xFF, 0xF4, 0xC0);

	private final FarmBankTabButton button;
	private final Random random = new Random();
	private final List<Figure> figures = new ArrayList<>();
	private long lastFrame;
	private long lastEmit;

	private static final class Figure
	{
		double x;
		double y;
		double vx;
		double vy;
		long born;
	}

	@Inject
	FuseOverlay(FarmBankTabButton button)
	{
		this.button = button;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Rectangle bounds = button.callingBounds();
		final long now = System.currentTimeMillis();
		if (bounds == null)
		{
			figures.clear();
			lastFrame = now;
			return null;
		}
		final Rectangle fuse = new Rectangle(bounds.x - GAP, bounds.y - GAP, bounds.width + 2 * GAP - 1,
			bounds.height + 2 * GAP - 1);
		final double perimeter = 2.0 * (fuse.width + fuse.height);
		final double spark = (now % LAP_MILLIS) / (double) LAP_MILLIS * perimeter;

		for (int d = 0; d < perimeter; d += BULB_SPACING)
		{
			// How far the spark has gone past this bulb, as part of a lap
			final double behind = ((spark - d) + perimeter) % perimeter / perimeter;
			final int[] p = pointAt(fuse, d);
			if (behind < TRAIL)
			{
				final int alpha = (int) (255 * (1 - behind / TRAIL));
				graphics.setColor(new Color(GLOW.getRed(), GLOW.getGreen(), GLOW.getBlue(), Math.max(alpha, FUSE.getAlpha())));
			}
			else
			{
				graphics.setColor(FUSE);
			}
			graphics.fillRect(p[0], p[1], 2, 2);
		}

		final int[] s = pointAt(fuse, spark);
		graphics.setColor(SPARK_HALO);
		graphics.fillRect(s[0] - 3, s[1] - 3, 7, 7);
		graphics.setColor(SPARK);
		graphics.fillRect(s[0] - 1, s[1] - 1, 3, 3);

		if (now - lastEmit >= EMIT_MILLIS && figures.size() < MAX_FIGURES)
		{
			lastEmit = now;
			figures.add(leapFrom(fuse, s, now));
		}
		drawFigures(graphics, now);
		lastFrame = now;
		return null;
	}

	/** A figure jumping outwards from the spark's edge of the fuse. */
	private Figure leapFrom(Rectangle fuse, int[] at, long now)
	{
		final Figure f = new Figure();
		f.x = at[0];
		f.y = at[1];
		final double cx = fuse.getCenterX();
		final double cy = fuse.getCenterY();
		final double dx = at[0] - cx;
		final double dy = at[1] - cy;
		final double length = Math.max(1, Math.hypot(dx, dy));
		final double speed = 30 + random.nextDouble() * 30;
		f.vx = dx / length * speed + (random.nextDouble() - 0.5) * 20;
		f.vy = dy / length * speed - 40 - random.nextDouble() * 30;
		f.born = now;
		return f;
	}

	private void drawFigures(Graphics2D graphics, long now)
	{
		// Seconds since the last frame, kept small so a pause doesn't fling them off screen
		final double dt = lastFrame == 0 ? 0 : Math.min(0.05, (now - lastFrame) / 1000.0);
		for (Iterator<Figure> it = figures.iterator(); it.hasNext(); )
		{
			final Figure f = it.next();
			final long age = now - f.born;
			if (age > FIGURE_LIFE_MILLIS)
			{
				it.remove();
				continue;
			}
			f.vy += GRAVITY * dt;
			f.x += f.vx * dt;
			f.y += f.vy * dt;
			// Yellow when they leave the spark, cooling to red as they fade
			final double life = age / (double) FIGURE_LIFE_MILLIS;
			graphics.setColor(new Color(0xFF, (int) (0xD0 * (1 - life)), 0x20, (int) (255 * (1 - life))));
			final int x = (int) Math.round(f.x) - 1;
			final int y = (int) Math.round(f.y) - 2;
			for (int row = 0; row < FIGURE.length; row++)
			{
				for (int col = 0; col < FIGURE[row].length(); col++)
				{
					if (FIGURE[row].charAt(col) == 'X')
					{
						graphics.fillRect(x + col, y + row, 1, 1);
					}
				}
			}
		}
	}

	/** The point a distance along the fuse, clockwise from its top-left corner. */
	private static int[] pointAt(Rectangle r, double distance)
	{
		double d = distance % (2.0 * (r.width + r.height));
		if (d < r.width)
		{
			return new int[]{r.x + (int) d, r.y};
		}
		d -= r.width;
		if (d < r.height)
		{
			return new int[]{r.x + r.width, r.y + (int) d};
		}
		d -= r.height;
		if (d < r.width)
		{
			return new int[]{r.x + r.width - (int) d, r.y + r.height};
		}
		d -= r.width;
		return new int[]{r.x, r.y + r.height - (int) d};
	}
}
