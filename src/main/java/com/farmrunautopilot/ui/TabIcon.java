package com.farmrunautopilot.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * The sidebar tabs' icons (Sean: icons with tooltips, as five names don't fit), drawn as simple line icons in the
 * tab's colour so they need no image files: a play arrow, a sprout, a map pin, a person and a cog.
 */
enum TabIcon
{
	RUN, FARM, TRAVEL, ACCOUNT, SETTINGS;

	private static final int SIZE = 18;

	Icon icon(Color color)
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y)
			{
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g2.translate(x, y);
				g2.setColor(color);
				g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				paint(g2);
				g2.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return SIZE;
			}

			@Override
			public int getIconHeight()
			{
				return SIZE;
			}
		};
	}

	private void paint(Graphics2D g)
	{
		switch (this)
		{
			case RUN:
			{
				// A rounded play arrow
				final Path2D p = new Path2D.Float();
				p.moveTo(5, 3.5);
				p.lineTo(15, 9);
				p.lineTo(5, 14.5);
				p.closePath();
				g.fill(p);
				g.draw(p);
				break;
			}
			case FARM:
			{
				// A sprout: a stem and two leaves
				g.drawLine(9, 16, 9, 8);
				final Path2D left = new Path2D.Float();
				left.moveTo(9, 10);
				left.curveTo(4, 10, 3, 6, 3, 4);
				left.curveTo(6, 4, 9, 6, 9, 10);
				g.fill(left);
				final Path2D right = new Path2D.Float();
				right.moveTo(9, 8);
				right.curveTo(9, 4, 12, 2, 15, 2);
				right.curveTo(15, 5, 13, 8, 9, 8);
				g.fill(right);
				break;
			}
			case TRAVEL:
			{
				// A map pin
				final Path2D pin = new Path2D.Float();
				pin.moveTo(9, 16.5);
				pin.curveTo(5, 12, 3.5, 9.5, 3.5, 7);
				pin.curveTo(3.5, 3.5, 6, 1.5, 9, 1.5);
				pin.curveTo(12, 1.5, 14.5, 3.5, 14.5, 7);
				pin.curveTo(14.5, 9.5, 13, 12, 9, 16.5);
				pin.closePath();
				g.draw(pin);
				g.fill(new Ellipse2D.Float(7, 5, 4, 4));
				break;
			}
			case ACCOUNT:
			{
				// A person: head and shoulders
				g.draw(new Ellipse2D.Float(6, 2, 6, 6));
				final Path2D body = new Path2D.Float();
				body.moveTo(3, 16);
				body.curveTo(3, 11.5, 6, 10, 9, 10);
				body.curveTo(12, 10, 15, 11.5, 15, 16);
				g.draw(body);
				break;
			}
			default:
			{
				// A cog: a ring with eight teeth
				g.draw(new Ellipse2D.Float(5.5f, 5.5f, 7, 7));
				for (int i = 0; i < 8; i++)
				{
					final double a = Math.PI / 4 * i;
					g.draw(new Line2D.Double(9 + Math.cos(a) * 5, 9 + Math.sin(a) * 5, 9 + Math.cos(a) * 7.5,
						9 + Math.sin(a) * 7.5));
				}
				break;
			}
		}
	}
}
