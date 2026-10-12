package com.farmrunautopilot.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar's building block (Sprint 7 facelift, Sean: RuneLite's colours, a modern look): a rounded panel in
 * RuneLite's darker grey, optionally with a coloured bar down its left edge to mark the current or important one.
 */
class Card extends JPanel
{
	/** Corner rounding, in pixels. */
	static final int ARC = 8;
	/** Width of the accent bar down the left edge. */
	private static final int ACCENT_WIDTH = 3;

	private Color fill = ColorScheme.DARKER_GRAY_COLOR;
	private Color accent;

	Card(LayoutManager layout)
	{
		super(layout);
		setOpaque(false);
		setBorder(new EmptyBorder(6, 8, 6, 8));
	}

	/** The card's background; RuneLite's darker grey unless changed. */
	Card fill(Color fill)
	{
		this.fill = fill;
		repaint();
		return this;
	}

	/** A bar down the left edge in this colour (and room for it), or null for none. */
	Card accent(Color accent)
	{
		this.accent = accent;
		setBorder(new EmptyBorder(6, accent != null ? 8 + ACCENT_WIDTH : 8, 6, 8));
		repaint();
		return this;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(fill);
		g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
		if (accent != null)
		{
			// Clipped to the card's rounded shape, so the bar follows its corners
			g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC));
			g2.setColor(accent);
			g2.fillRect(0, 0, ACCENT_WIDTH, getHeight());
		}
		g2.dispose();
		super.paintComponent(g);
	}

	/** Side of a tick box, in pixels. */
	static final int TICK_SIZE = 14;

	/**
	 * A tick box drawn to stand out on a dark card (Sean: RuneLite's own was hard to see): an outline when empty,
	 * filled with a white tick when ticked.
	 *
	 * @param fill the fill when ticked, or null for an empty box
	 * @param outline the empty box's outline
	 */
	static Icon tick(Color fill, Color outline)
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y)
			{
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				final int s = TICK_SIZE - 1;
				if (fill == null)
				{
					g2.setColor(outline);
					g2.setStroke(new BasicStroke(1.5f));
					g2.drawRoundRect(x, y, s, s, 4, 4);
				}
				else
				{
					g2.setColor(fill);
					g2.fillRoundRect(x, y, s + 1, s + 1, 4, 4);
					g2.setColor(Color.WHITE);
					g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g2.drawPolyline(new int[]{x + 3, x + 6, x + 11}, new int[]{y + 7, y + 10, y + 4}, 3);
				}
				g2.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return TICK_SIZE;
			}

			@Override
			public int getIconHeight()
			{
				return TICK_SIZE;
			}
		};
	}

	/** A small chevron for a collapsible header: pointing right when closed, down when open. */
	static Icon chevron(boolean open, Color color)
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y)
			{
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(color);
				g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				final int cx = x + 5;
				final int cy = y + 5;
				if (open)
				{
					g2.drawPolyline(new int[]{cx - 4, cx, cx + 4}, new int[]{cy - 2, cy + 2, cy - 2}, 3);
				}
				else
				{
					g2.drawPolyline(new int[]{cx - 2, cx + 2, cx - 2}, new int[]{cy - 4, cy, cy + 4}, 3);
				}
				g2.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return 10;
			}

			@Override
			public int getIconHeight()
			{
				return 10;
			}
		};
	}
}
