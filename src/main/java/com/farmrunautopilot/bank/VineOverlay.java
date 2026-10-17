package com.farmrunautopilot.bank;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
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
 * round the seed vault's Farm Run Autopilot button the same way (issue #8): it sprouts from the middle of the bottom
 * edge, grows both ways and up the sides to meet at the top, blooms, drops a few petals, then fades and grows again.
 * Drawing only; the button itself is untouched.
 */
public class VineOverlay extends Overlay
{
	private static final long GROW_MILLIS = 900;
	private static final long BLOOM_MILLIS = 450;
	private static final long HOLD_MILLIS = 450;
	private static final long FADE_MILLIS = 300;
	private static final long CYCLE_MILLIS = GROW_MILLIS + BLOOM_MILLIS + HOLD_MILLIS + FADE_MILLIS;
	/** Room between the button and the vine. */
	private static final int GAP = 2;
	private static final int LEAF_SPACING = 4;
	private static final int FLOWER_SPACING = 9;
	private static final long PETAL_EMIT_MILLIS = 90;
	private static final long PETAL_LIFE_MILLIS = 900;
	private static final int MAX_PETALS = 16;

	private static final Color STEM = new Color(0x2E, 0x7D, 0x32);
	private static final Color LEAF = new Color(0x7C, 0xCB, 0x4A);
	private static final Color TIP = new Color(0xC5, 0xF0, 0x8A);
	private static final Color CENTRE = new Color(0xFF, 0xD5, 0x4F);
	private static final Color[] PETALS = {new Color(0xF0, 0x6E, 0xAA), new Color(0xB3, 0x88, 0xFF),
		new Color(0xFF, 0xF4, 0xF8)};

	private final FarmBankTabButton button;
	private final SeedVaultSection seedVault;
	private final Random random = new Random();
	private final List<Petal> petals = new ArrayList<>();
	private long lastFrame;
	private long lastEmit;

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
		final long fadeStart = GROW_MILLIS + BLOOM_MILLIS + HOLD_MILLIS;
		final float alpha = t < fadeStart ? 1f : 1f - (t - fadeStart) / (float) FADE_MILLIS;

		final Composite before = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
		for (int side = -1; side <= 1; side += 2)
		{
			drawHalf(graphics, frame, side, grown, bloom);
		}
		graphics.setComposite(before);

		if (bloom > 0 && alpha >= 1 && now - lastEmit >= PETAL_EMIT_MILLIS && petals.size() < MAX_PETALS)
		{
			lastEmit = now;
			petals.add(dropPetal(frame, half, now));
		}
		drawPetals(graphics, now);
		lastFrame = now;
		return null;
	}

	/** One half of the vine, mirrored for the right ({@code side} 1) or left (-1). */
	private void drawHalf(Graphics2D graphics, Rectangle frame, int side, double grown, double bloom)
	{
		graphics.setColor(STEM);
		for (int d = 0; d <= grown; d++)
		{
			final int[] p = pointAt(frame, side, d);
			graphics.fillRect(p[0], p[1], 1, 1);
		}
		// Leaves alternate in and out along the stem
		graphics.setColor(LEAF);
		for (int d = LEAF_SPACING; d <= grown; d += LEAF_SPACING)
		{
			final int[] p = pointAt(frame, side, d);
			final int[] out = outward(frame, side, d);
			final int flip = (d / LEAF_SPACING) % 2 == 0 ? 1 : -1;
			graphics.fillRect(p[0] + out[0] * flip, p[1] + out[1] * flip, 1, 1);
		}
		if (bloom == 0)
		{
			final int[] tip = pointAt(frame, side, grown);
			graphics.setColor(TIP);
			graphics.fillRect(tip[0] - 1, tip[1] - 1, 2, 2);
			return;
		}
		// Flowers open along the vine, the last one where the two halves meet at the top
		final int radius = (int) Math.round(bloom * 2);
		int n = 0;
		for (int d = FLOWER_SPACING / 2; d <= grown; d += FLOWER_SPACING, n++)
		{
			drawFlower(graphics, pointAt(frame, side, Math.min(d, (int) grown)), radius, PETALS[n % PETALS.length]);
		}
	}

	private static void drawFlower(Graphics2D graphics, int[] at, int radius, Color colour)
	{
		if (radius > 0)
		{
			graphics.setColor(colour);
			graphics.fillRect(at[0] - radius, at[1], 1, 1);
			graphics.fillRect(at[0] + radius, at[1], 1, 1);
			graphics.fillRect(at[0], at[1] - radius, 1, 1);
			graphics.fillRect(at[0], at[1] + radius, 1, 1);
		}
		graphics.setColor(CENTRE);
		graphics.fillRect(at[0], at[1], 1, 1);
	}

	/** A petal falling from somewhere along the vine. */
	private Petal dropPetal(Rectangle frame, double half, long now)
	{
		final int[] at = pointAt(frame, random.nextBoolean() ? 1 : -1, random.nextDouble() * half);
		final Petal p = new Petal();
		p.x = at[0];
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
			p.y += 14 * dt;
			p.x += Math.sin(p.sway + age / 160.0) * 12 * dt;
			final double life = age / (double) PETAL_LIFE_MILLIS;
			graphics.setColor(new Color(p.colour.getRed(), p.colour.getGreen(), p.colour.getBlue(), (int) (255 * (1 - life))));
			graphics.fillRect((int) Math.round(p.x), (int) Math.round(p.y), 1, 1);
		}
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
}
