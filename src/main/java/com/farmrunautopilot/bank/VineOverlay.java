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
 * A vine round the bank's Farm run button while the guide is waiting for it to be clicked (issue #5, elzerma), or
 * round the seed vault's Farm Run Autopilot button the same way (issue #8): a thick stem with leaves and curling
 * tendrils sprouts from the middle of the bottom edge, grows both ways and up the sides to meet at the top, opens
 * into flowers, then wilts (browning, drooping, dropping its petals) and grows again. Drawing only; the button itself
 * is untouched.
 */
public class VineOverlay extends Overlay
{
	private static final long GROW_MILLIS = 900;
	private static final long BLOOM_MILLIS = 400;
	private static final long HOLD_MILLIS = 600;
	private static final long WILT_MILLIS = 700;
	private static final long CYCLE_MILLIS = GROW_MILLIS + BLOOM_MILLIS + HOLD_MILLIS + WILT_MILLIS;
	/** Room between the button and the vine. */
	private static final int GAP = 3;
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

	private final FarmBankTabButton button;
	private final SeedVaultSection seedVault;
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

	@Inject
	VineOverlay(FarmBankTabButton button, SeedVaultSection seedVault)
	{
		this.button = button;
		this.seedVault = seedVault;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// The bank and the seed vault are never open together, so it's one or the other
		final Rectangle bank = button.callingBounds();
		final Rectangle bounds = bank != null ? bank : seedVault.callingBounds();
		final long now = System.currentTimeMillis();
		if (bounds == null)
		{
			petals.clear();
			lastFrame = now;
			return null;
		}
		final Rectangle frame = new Rectangle(bounds.x - GAP, bounds.y - GAP, bounds.width + 2 * GAP - 1,
			bounds.height + 2 * GAP - 1);
		// Each half of the vine: along the bottom from the middle, up a side, along the top to the middle
		final double half = frame.width + frame.height;
		final long t = now % CYCLE_MILLIS;
		final double grown = Math.min(1, t / (double) GROW_MILLIS) * half;
		final double bloom = t < GROW_MILLIS ? 0 : Math.min(1, (t - GROW_MILLIS) / (double) BLOOM_MILLIS);
		final long wiltStart = GROW_MILLIS + BLOOM_MILLIS + HOLD_MILLIS;
		final double wilt = t < wiltStart ? 0 : (t - wiltStart) / (double) WILT_MILLIS;

		for (int side = -1; side <= 1; side += 2)
		{
			drawHalf(graphics, frame, side, grown, bloom, wilt);
		}

		// A few petals fall while it's in flower; the rest all drop as it starts to wilt
		final long cycle = now / CYCLE_MILLIS;
		if (wilt > 0 && shedCycle != cycle)
		{
			shedCycle = cycle;
			shedFlowers(frame, half, now);
		}
		else if (bloom > 0 && wilt == 0 && now - lastEmit >= PETAL_EMIT_MILLIS && petals.size() < MAX_PETALS)
		{
			lastEmit = now;
			petals.add(petal(pointAt(frame, random.nextBoolean() ? 1 : -1, random.nextDouble() * half), now));
		}
		drawPetals(graphics, now);
		lastFrame = now;
		return null;
	}

	/** One half of the vine, mirrored for the right ({@code side} 1) or left (-1). */
	private void drawHalf(Graphics2D graphics, Rectangle frame, int side, double grown, double bloom, double wilt)
	{
		// Wilting: the green goes brown and everything sags towards the bottom
		final Color stem = blend(STEM, DEAD_STEM, wilt);
		final Color leaf = blend(LEAF, DEAD_LEAF, wilt);
		final int sag = (int) Math.round(wilt * 2);

		// A stem two pixels thick: the line itself and one pixel outward
		graphics.setColor(stem);
		for (int d = 0; d <= grown; d++)
		{
			final int[] p = pointAt(frame, side, d);
			final int[] out = outward(frame, side, d);
			graphics.fillRect(p[0], p[1], 1, 1);
			graphics.fillRect(p[0] + out[0], p[1] + out[1], 1, 1);
		}

		// Leaves, two pixels long, alternating out and in; when wilting they hang lower
		graphics.setColor(leaf);
		for (int d = LEAF_SPACING; d <= grown; d += LEAF_SPACING)
		{
			final int[] p = pointAt(frame, side, d);
			final int[] out = outward(frame, side, d);
			final int[] along = along(frame, side, d);
			final int reach = (d / LEAF_SPACING) % 2 == 0 ? 2 : -1;
			final int x = p[0] + out[0] * reach;
			final int y = p[1] + out[1] * reach + sag;
			graphics.fillRect(x, y, 1, 1);
			graphics.fillRect(x + along[0], y + along[1], 1, 1);
		}

		// Tendrils: a little hook curling away from the stem
		if (wilt < 0.6)
		{
			graphics.setColor(blend(TENDRIL, DEAD_LEAF, wilt));
			for (int d = TENDRIL_SPACING; d <= grown; d += TENDRIL_SPACING)
			{
				final int[] p = pointAt(frame, side, d);
				final int[] out = outward(frame, side, d);
				final int[] along = along(frame, side, d);
				graphics.fillRect(p[0] + out[0] * 2, p[1] + out[1] * 2, 1, 1);
				graphics.fillRect(p[0] + out[0] * 3, p[1] + out[1] * 3, 1, 1);
				graphics.fillRect(p[0] + out[0] * 4 + along[0], p[1] + out[1] * 4 + along[1], 1, 1);
				graphics.fillRect(p[0] + out[0] * 3 + along[0] * 2, p[1] + out[1] * 3 + along[1] * 2, 1, 1);
			}
		}

		if (bloom == 0)
		{
			final int[] tip = pointAt(frame, side, grown);
			graphics.setColor(TIP);
			graphics.fillRect(tip[0] - 1, tip[1] - 1, 3, 3);
			return;
		}
		// Flowers open along the vine; wilting, they shrink, droop and brown
		final double open = bloom * (1 - wilt * 0.6);
		int n = 0;
		for (int d = FLOWER_SPACING / 2; d <= grown; d += FLOWER_SPACING, n++)
		{
			final int[] at = pointAt(frame, side, d);
			at[1] += sag + (int) Math.round(wilt * 2);
			drawFlower(graphics, at, open, blend(PETALS[n % PETALS.length], DEAD_FLOWER, wilt), wilt);
		}
	}

	/** Five-ish petals round a yellow centre, up to seven pixels across once fully open. */
	private static void drawFlower(Graphics2D graphics, int[] at, double open, Color colour, double wilt)
	{
		final int r = (int) Math.round(open * 3);
		final int x = at[0];
		final int y = at[1];
		graphics.setColor(colour);
		if (r >= 1)
		{
			graphics.fillRect(x - 1, y - 1, 3, 3);
		}
		if (r >= 2)
		{
			graphics.fillRect(x - 1, y - r, 3, 2);
			graphics.fillRect(x - r, y - 1, 2, 3);
			graphics.fillRect(x + r - 1, y - 1, 2, 3);
			graphics.fillRect(x - r + 1, y + r - 1, 2, 2);
			graphics.fillRect(x + r - 2, y + r - 1, 2, 2);
		}
		graphics.setColor(blend(CENTRE, DEAD_FLOWER, wilt));
		graphics.fillRect(x, y, 1, 1);
	}

	/** As the wilt begins, every flower's petals let go. */
	private void shedFlowers(Rectangle frame, double half, long now)
	{
		for (int side = -1; side <= 1; side += 2)
		{
			int n = 0;
			for (int d = FLOWER_SPACING / 2; d <= half && petals.size() < MAX_PETALS; d += FLOWER_SPACING, n++)
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

	private void drawPetals(Graphics2D graphics, long now)
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
			graphics.setColor(new Color(p.colour.getRed(), p.colour.getGreen(), p.colour.getBlue(), (int) (255 * (1 - life))));
			graphics.fillRect((int) Math.round(p.x), (int) Math.round(p.y), 2, 1);
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
			(int) (from.getBlue() + (to.getBlue() - from.getBlue()) * a));
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
