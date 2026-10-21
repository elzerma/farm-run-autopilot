package com.farmrunautopilot.run;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * A growing vine round something the player should click (elzerma, issue #5): the bank's Farm run button, the seed
 * vault's button, the spell to cast and the place to pick on a travel map. A thick stem with leaves and curling tendrils
 * sprouts from the middle of the bottom edge, grows both ways and up the sides to meet at the top, opens into flowers,
 * then wilts (browning, drooping, dropping its petals) and grows again. Lit from the top left, with a soft shadow under
 * it, so it stands off the button (elzerma, 2026-10-18: "more depth, should appear more 3d"); it can be drawn thicker
 * (a travel map's places). Each highlight keeps its own, since the falling petals are state. Drawing only; client thread.
 */
public final class VineFrame
{
	private static final long GROW_MILLIS = 900;
	private static final long BLOOM_MILLIS = 400;
	private static final long HOLD_MILLIS = 600;
	private static final long WILT_MILLIS = 700;
	private static final long CYCLE_MILLIS = GROW_MILLIS + BLOOM_MILLIS + HOLD_MILLIS + WILT_MILLIS;
	/** Room between the button and the vine, in cells. */
	private static final int GAP = 3;
	/** Along the vine, in cells. */
	private static final int LEAF_SPACING = 5;
	private static final int TENDRIL_SPACING = 11;
	private static final int FLOWER_SPACING = 14;
	private static final long PETAL_EMIT_MILLIS = 70;
	private static final long PETAL_LIFE_MILLIS = 1000;
	private static final int MAX_PETALS = 30;

	private static final Color STEM = new Color(0x2E, 0x6B, 0x26);
	private static final Color LEAF = new Color(0x6D, 0xC0, 0x3E);
	private static final Color TENDRIL = new Color(0x9C, 0xD6, 0x5A);
	private static final Color TIP = new Color(0xC5, 0xF0, 0x8A);
	private static final Color CENTRE = new Color(0xFF, 0xD5, 0x4F);
	private static final Color DEAD_STEM = new Color(0x5B, 0x43, 0x25);
	private static final Color DEAD_LEAF = new Color(0xA0, 0x7A, 0x3C);
	private static final Color DEAD_FLOWER = new Color(0x7A, 0x52, 0x3A);
	private static final Color[] PETALS = {new Color(0xF0, 0x6E, 0xAA), new Color(0xB3, 0x88, 0xFF),
		new Color(0xFF, 0xF4, 0xF8), new Color(0xFF, 0x8A, 0x5C)};
	/** The shadow the vine casts on what's under it, down and to the right. */
	private static final Color SHADOW = new Color(0, 0, 0, 110);
	private static final Color GLINT = new Color(0xFF, 0xFF, 0xFF, 200);

	private final Random random = new Random();
	private final List<Petal> petals = new ArrayList<>();
	private long lastFrame;
	private long lastEmit;
	/** The cycle whose wilt has already shed its petals, so each wilt sheds them once. */
	private long shedCycle = -1;

	private static final class Petal
	{
		double x;
		double y;
		double sway;
		Color colour;
		long born;
	}

	/** Nothing to frame this frame: drop any falling petals, so the next vine starts clean. */
	public void clear()
	{
		petals.clear();
		lastFrame = 0;
	}

	/** Draws the vine round {@code bounds} as it is right now. */
	public void draw(Graphics2D graphics, Rectangle bounds)
	{
		draw(graphics, bounds, 1);
	}

	/**
	 * Draws the vine round {@code bounds} as it is right now, each cell of it {@code cell} pixels across: 1 round a
	 * button, 2 round a place on a travel map (elzerma, 2026-10-18: thicker there).
	 */
	public void draw(Graphics2D graphics, Rectangle bounds, int cell)
	{
		final long now = System.currentTimeMillis();
		final int gap = GAP * cell;
		final Rectangle frame = new Rectangle(bounds.x - gap, bounds.y - gap, bounds.width + 2 * gap - 1,
			bounds.height + 2 * gap - 1);
		// Each half of the vine: along the bottom from the middle, up a side, along the top to the middle
		final double half = frame.width + frame.height;
		final long t = now % CYCLE_MILLIS;
		final double grown = Math.min(1, t / (double) GROW_MILLIS) * half;
		final double bloom = t < GROW_MILLIS ? 0 : Math.min(1, (t - GROW_MILLIS) / (double) BLOOM_MILLIS);
		final long wiltStart = GROW_MILLIS + BLOOM_MILLIS + HOLD_MILLIS;
		final double wilt = t < wiltStart ? 0 : (t - wiltStart) / (double) WILT_MILLIS;

		// The shadow first, under everything, then the vine itself
		for (int side = -1; side <= 1; side += 2)
		{
			drawHalf(graphics, frame, side, grown, bloom, wilt, cell, true);
		}
		for (int side = -1; side <= 1; side += 2)
		{
			drawHalf(graphics, frame, side, grown, bloom, wilt, cell, false);
		}

		// A few petals fall while it's in flower; the rest all drop as it starts to wilt
		final long cycle = now / CYCLE_MILLIS;
		if (wilt > 0 && shedCycle != cycle)
		{
			shedCycle = cycle;
			shedFlowers(frame, half, now, cell);
		}
		else if (bloom > 0 && wilt == 0 && now - lastEmit >= PETAL_EMIT_MILLIS && petals.size() < MAX_PETALS)
		{
			lastEmit = now;
			petals.add(petal(pointAt(frame, random.nextBoolean() ? 1 : -1, random.nextDouble() * half), now));
		}
		drawPetals(graphics, now, cell);
		lastFrame = now;
	}

	/**
	 * One half of the vine, mirrored for the right ({@code side} 1) or left (-1): its shadow, or the vine shaded as if
	 * lit from the top left.
	 */
	private void drawHalf(Graphics2D graphics, Rectangle frame, int side, double grown, double bloom, double wilt,
		int cell, boolean shadow)
	{
		// Wilting: the green goes brown and everything sags towards the bottom
		final Color stem = blend(STEM, DEAD_STEM, wilt);
		final Color leaf = blend(LEAF, DEAD_LEAF, wilt);
		final int sag = (int) Math.round(wilt * 2) * cell;
		final int drop = shadow ? Math.max(1, cell) : 0;

		// The stem, two cells thick: its edge towards the light lighter, the other darker
		final int width = 2 * cell;
		for (int d = 0; d <= grown; d++)
		{
			final int[] p = pointAt(frame, side, d);
			final int[] out = outward(frame, side, d);
			// The edge facing up or left catches the light
			final boolean outLit = out[0] < 0 || out[1] < 0;
			for (int k = 0; k < width; k++)
			{
				final Color colour;
				if (shadow)
				{
					colour = SHADOW;
				}
				else if (k == width - 1)
				{
					colour = outLit ? lighter(stem, 0.35) : darker(stem, 0.35);
				}
				else if (k == 0)
				{
					colour = outLit ? darker(stem, 0.25) : lighter(stem, 0.2);
				}
				else
				{
					colour = stem;
				}
				graphics.setColor(colour);
				graphics.fillRect(p[0] + out[0] * k + drop, p[1] + out[1] * k + drop, 1, 1);
			}
		}

		// Leaves, two cells long, alternating out and in; when wilting they hang lower. A lit tip and a shaded base
		final int leafSpacing = LEAF_SPACING * cell;
		for (int d = leafSpacing; d <= grown; d += leafSpacing)
		{
			final int[] p = pointAt(frame, side, d);
			final int[] out = outward(frame, side, d);
			final int[] along = along(frame, side, d);
			final int reach = (d / leafSpacing) % 2 == 0 ? width : -cell;
			final int x = p[0] + out[0] * reach + drop;
			final int y = p[1] + out[1] * reach + sag + drop;
			graphics.setColor(shadow ? SHADOW : darker(leaf, 0.2));
			graphics.fillRect(x, y, cell, cell);
			graphics.setColor(shadow ? SHADOW : lighter(leaf, 0.3));
			graphics.fillRect(x + along[0] * cell, y + along[1] * cell, cell, cell);
			if (!shadow && cell > 1)
			{
				// A glint on the leaf's lit corner
				graphics.setColor(lighter(leaf, 0.6));
				graphics.fillRect(x + along[0] * cell, y + along[1] * cell, 1, 1);
			}
		}

		// Tendrils: a little hook curling away from the stem
		if (wilt < 0.6)
		{
			graphics.setColor(shadow ? SHADOW : blend(TENDRIL, DEAD_LEAF, wilt));
			final int tendrilSpacing = TENDRIL_SPACING * cell;
			for (int d = tendrilSpacing; d <= grown; d += tendrilSpacing)
			{
				final int[] p = pointAt(frame, side, d);
				final int[] out = outward(frame, side, d);
				final int[] along = along(frame, side, d);
				final int[][] hook = {{0, 2}, {0, 3}, {1, 4}, {2, 3}};
				for (int[] h : hook)
				{
					graphics.fillRect(p[0] + (out[0] * h[1] + along[0] * h[0]) * cell + drop,
						p[1] + (out[1] * h[1] + along[1] * h[0]) * cell + drop, cell, cell);
				}
			}
		}

		if (bloom == 0)
		{
			final int[] tip = pointAt(frame, side, grown);
			graphics.setColor(shadow ? SHADOW : TIP);
			graphics.fillRect(tip[0] - cell + drop, tip[1] - cell + drop, 3 * cell, 3 * cell);
			if (!shadow)
			{
				graphics.setColor(lighter(TIP, 0.5));
				graphics.fillRect(tip[0] - cell, tip[1] - cell, cell, cell);
			}
			return;
		}
		// Flowers open along the vine; wilting, they shrink, droop and brown
		final double open = bloom * (1 - wilt * 0.6);
		int n = 0;
		for (int d = FLOWER_SPACING * cell / 2; d <= grown; d += FLOWER_SPACING * cell, n++)
		{
			final int[] at = pointAt(frame, side, d);
			at[0] += drop;
			at[1] += sag + (int) Math.round(wilt * 2) * cell + drop;
			drawFlower(graphics, at, open, shadow ? SHADOW : blend(PETALS[n % PETALS.length], DEAD_FLOWER, wilt), wilt,
				cell, shadow);
		}
	}

	/**
	 * Five-ish petals round a yellow centre, up to seven cells across once fully open: the petals towards the light
	 * lighter, the others shaded, and a glint on the centre.
	 */
	private static void drawFlower(Graphics2D graphics, int[] at, double open, Color colour, double wilt, int cell,
		boolean shadow)
	{
		final int r = (int) Math.round(open * 3);
		final int x = at[0];
		final int y = at[1];
		final int c = cell;
		final Color lit = shadow ? colour : lighter(colour, 0.35);
		final Color shaded = shadow ? colour : darker(colour, 0.3);
		if (r >= 1)
		{
			graphics.setColor(colour);
			graphics.fillRect(x - c, y - c, 3 * c, 3 * c);
		}
		if (r >= 2)
		{
			// Top and left petals face the light; right and bottom ones are in shade
			graphics.setColor(lit);
			graphics.fillRect(x - c, y - r * c, 3 * c, 2 * c);
			graphics.fillRect(x - r * c, y - c, 2 * c, 3 * c);
			graphics.setColor(shaded);
			graphics.fillRect(x + (r - 1) * c, y - c, 2 * c, 3 * c);
			graphics.fillRect(x + (1 - r) * c, y + (r - 1) * c, 2 * c, 2 * c);
			graphics.fillRect(x + (r - 2) * c, y + (r - 1) * c, 2 * c, 2 * c);
		}
		if (shadow)
		{
			return;
		}
		graphics.setColor(blend(CENTRE, DEAD_FLOWER, wilt));
		graphics.fillRect(x, y, c, c);
		if (wilt < 0.5)
		{
			graphics.setColor(GLINT);
			graphics.fillRect(x, y, 1, 1);
		}
	}

	/** As the wilt begins, every flower's petals let go. */
	private void shedFlowers(Rectangle frame, double half, long now, int cell)
	{
		for (int side = -1; side <= 1; side += 2)
		{
			int n = 0;
			for (int d = FLOWER_SPACING * cell / 2; d <= half && petals.size() < MAX_PETALS; d += FLOWER_SPACING * cell, n++)
			{
				for (int i = 0; i < 2 && petals.size() < MAX_PETALS; i++)
				{
					final Petal p = petal(pointAt(frame, side, d), now);
					p.colour = PETALS[n % PETALS.length];
					petals.add(p);
				}
			}
		}
	}

	private Petal petal(int[] at, long now)
	{
		final Petal p = new Petal();
		p.x = at[0] + random.nextInt(3) - 1;
		p.y = at[1];
		p.sway = random.nextDouble() * Math.PI * 2;
		p.colour = PETALS[random.nextInt(PETALS.length)];
		p.born = now;
		return p;
	}

	private void drawPetals(Graphics2D graphics, long now, int cell)
	{
		// Seconds since the last frame, kept small so a pause doesn't drop them off screen
		final double dt = lastFrame == 0 ? 0 : Math.min(0.05, (now - lastFrame) / 1000.0);
		for (Iterator<Petal> it = petals.iterator(); it.hasNext(); )
		{
			final Petal p = it.next();
			final long age = now - p.born;
			if (age > PETAL_LIFE_MILLIS)
			{
				it.remove();
				continue;
			}
			// Drifting down, swaying side to side
			p.y += 16 * dt;
			p.x += Math.sin(p.sway + age / 160.0) * 14 * dt;
			final double life = age / (double) PETAL_LIFE_MILLIS;
			final int alpha = (int) (255 * (1 - life));
			final int x = (int) Math.round(p.x);
			final int y = (int) Math.round(p.y);
			// Its shadow, then the petal, its upper edge lit
			graphics.setColor(new Color(0, 0, 0, alpha * SHADOW.getAlpha() / 255));
			graphics.fillRect(x + cell, y + cell, 2 * cell, cell);
			graphics.setColor(new Color(p.colour.getRed(), p.colour.getGreen(), p.colour.getBlue(), alpha));
			graphics.fillRect(x, y, 2 * cell, cell);
			final Color lit = lighter(p.colour, 0.4);
			graphics.setColor(new Color(lit.getRed(), lit.getGreen(), lit.getBlue(), alpha));
			graphics.fillRect(x, y, 2 * cell, 1);
		}
	}

	private static Color blend(Color from, Color to, double amount)
	{
		if (amount <= 0)
		{
			return from;
		}
		final double a = Math.min(1, amount);
		return new Color((int) (from.getRed() + (to.getRed() - from.getRed()) * a),
			(int) (from.getGreen() + (to.getGreen() - from.getGreen()) * a),
			(int) (from.getBlue() + (to.getBlue() - from.getBlue()) * a), from.getAlpha());
	}

	/** Towards white, by {@code amount} (0 to 1). */
	private static Color lighter(Color colour, double amount)
	{
		return blend(colour, new Color(255, 255, 255, colour.getAlpha()), amount);
	}

	/** Towards black, by {@code amount} (0 to 1). */
	private static Color darker(Color colour, double amount)
	{
		return blend(colour, new Color(0, 0, 0, colour.getAlpha()), amount);
	}

	/**
	 * The point a distance along one half of the vine: from the middle of the bottom edge towards a corner, up that
	 * side, then along the top towards its middle.
	 */
	private static int[] pointAt(Rectangle r, int side, double distance)
	{
		final double halfWidth = r.width / 2.0;
		final double centre = r.x + halfWidth;
		final double x;
		final double y;
		if (distance < halfWidth)
		{
			x = centre + side * distance;
			y = r.y + r.height;
		}
		else if (distance < halfWidth + r.height)
		{
			x = centre + side * halfWidth;
			y = r.y + r.height - (distance - halfWidth);
		}
		else
		{
			x = centre + side * (halfWidth - Math.min(halfWidth, distance - halfWidth - r.height));
			y = r.y;
		}
		return new int[]{(int) Math.round(x), (int) Math.round(y)};
	}

	/** One pixel away from the button, at this point of the vine. */
	private static int[] outward(Rectangle r, int side, int distance)
	{
		final double halfWidth = r.width / 2.0;
		if (distance < halfWidth)
		{
			return new int[]{0, 1};
		}
		if (distance < halfWidth + r.height)
		{
			return new int[]{side, 0};
		}
		return new int[]{0, -1};
	}

	/** One pixel further along the vine, the way it grows, at this point. */
	private static int[] along(Rectangle r, int side, int distance)
	{
		final double halfWidth = r.width / 2.0;
		if (distance < halfWidth)
		{
			return new int[]{side, 0};
		}
		if (distance < halfWidth + r.height)
		{
			return new int[]{0, -1};
		}
		return new int[]{-side, 0};
	}
}
