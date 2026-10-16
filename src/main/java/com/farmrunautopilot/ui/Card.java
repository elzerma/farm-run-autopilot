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
 * The sidebar's building block (Sprint 7 facelift, elzerma: RuneLite's colours, a modern look): a rounded panel in
 * RuneLite's darker grey, optionally with a coloured bar down its left edge to mark the current or important one.
 */
class Card extends JPanel
{
	/** Corner rounding, in pixels: the one radius everything shares. */
	static final int ARC = Style.ARC;
	/** Width of the accent bar down the left edge. */
	private static final int ACCENT_WIDTH = 3;

	private Color fill = ColorScheme.DARKER_GRAY_COLOR;
	private Color accent;

	Card(LayoutManager layout)
	{
		super(layout);
		setOpaque(false);
		setBorder(new EmptyBorder(4, 7, 4, 7));
	}

	/** The card's background; RuneLite's darker grey unless changed. */
	Card fill(Color fill)
	{
		this.fill = fill;
		repaint();
		return this;
	}

	/** Clickable: one step lighter while the mouse is over it. */
	Card hoverable()
	{
		final Color normal = fill;
		addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				fill(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				fill(normal);
			}
		});
		return this;
	}

	/** A bar down the left edge in this colour (and room for it), or null for none. */
	Card accent(Color accent)
	{
		this.accent = accent;
		setBorder(new EmptyBorder(4, accent != null ? 7 + ACCENT_WIDTH : 7, 4, 7));
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

	/**
	 * A group's heading that opens and closes it: a card with a chevron, the title, and a count on the right
	 * (e.g. "3 of 7 due"). Used for the patch timers and the guided tests.
	 */
	static javax.swing.JComponent groupHeader(String title, String summary, boolean open, Runnable toggle)
	{
		final Card head = new Card(new java.awt.BorderLayout(8, 0)).hoverable();
		final javax.swing.JLabel name = new javax.swing.JLabel(title, chevron(open, Color.WHITE),
			javax.swing.JLabel.LEFT);
		name.setIconTextGap(8);
		name.setForeground(Color.WHITE);
		name.setFont(net.runelite.client.ui.FontManager.getRunescapeFont());
		final javax.swing.JLabel count = new javax.swing.JLabel(summary);
		count.setForeground(Color.WHITE);
		count.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		head.add(name, java.awt.BorderLayout.CENTER);
		head.add(count, java.awt.BorderLayout.EAST);
		head.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		head.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				toggle.run();
			}
		});
		// As wide as its parent, as tall as its text at each layout
		final JPanel wrapper = new JPanel(new java.awt.BorderLayout())
		{
			@Override
			public java.awt.Dimension getMaximumSize()
			{
				return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
		wrapper.add(head, java.awt.BorderLayout.CENTER);
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		return wrapper;
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
