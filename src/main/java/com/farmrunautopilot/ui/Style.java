package com.farmrunautopilot.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The sidebar's look (Sprint 7 facelift, elzerma: "an updated RuneLite look. No gradients, but slightly softer
 * radius, really mature and polished"): RuneLite's own colours and fonts, flat fills, one soft radius. Controls
 * are styled through FlatLaf's style property, which RuneLite's look and feel reads, so they keep their normal
 * behaviour (keyboard, focus, disabled state).
 */
final class Style
{
	/** Corner radius for cards, buttons, drop-downs and number boxes: square (elzerma: no radius). */
	static final int ARC = 0;
	/** Done, due, carried. */
	static final Color GREEN = new Color(0x4C, 0xC1, 0x52);
	/** Side of a tick box, in pixels. */
	private static final int TICK_SIZE = 12;
	/** Stop: a muted red, not an alarm. */
	private static final Color DANGER = new Color(0x8A, 0x33, 0x33);
	/** FlatLaf's per-component style property (FlatClientProperties.STYLE). */
	private static final String STYLE = "FlatLaf.style";

	private Style()
	{
	}

	/** A plain button: flat dark grey, lighter on hover, no outline. */
	static <B extends AbstractButton> B button(B button)
	{
		button.setFocusPainted(false);
		button.putClientProperty(STYLE, "arc: " + ARC + "; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0"
			+ "; background: " + hex(ColorScheme.DARKER_GRAY_COLOR)
			+ "; hoverBackground: " + hex(ColorScheme.DARK_GRAY_HOVER_COLOR)
			+ "; pressedBackground: " + hex(ColorScheme.MEDIUM_GRAY_COLOR));
		button.setForeground(ColorScheme.TEXT_COLOR);
		return button;
	}

	/** The main action on screen (Build run, Start now): orange with dark text. */
	static JButton primary(JButton button)
	{
		button.setFocusPainted(false);
		// Slimmer than a standard button (elzerma: a little smaller)
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setMargin(new Insets(1, 8, 1, 8));
		button.putClientProperty(STYLE, "arc: " + ARC + "; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0"
			+ "; background: " + hex(ColorScheme.BRAND_ORANGE)
			+ "; hoverBackground: " + hex(ColorScheme.BRAND_ORANGE.brighter())
			+ "; pressedBackground: " + hex(ColorScheme.BRAND_ORANGE.darker()));
		button.setForeground(ColorScheme.DARKER_GRAY_COLOR);
		return button;
	}

	/** Ending something (Stop): a muted red with white text. */
	static JButton danger(JButton button)
	{
		button.setFocusPainted(false);
		button.putClientProperty(STYLE, "arc: " + ARC + "; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0"
			+ "; background: " + hex(DANGER)
			+ "; hoverBackground: " + hex(DANGER.brighter())
			+ "; pressedBackground: " + hex(DANGER.darker()));
		button.setForeground(Color.WHITE);
		return button;
	}

	/** A drop-down: flat, a soft grey outline that turns orange while it has focus, a plain arrow. */
	static <C extends JComboBox<?>> C combo(C combo)
	{
		combo.putClientProperty(STYLE, "arc: " + ARC + "; focusWidth: 0; buttonStyle: none"
			+ "; buttonBackground: " + hex(ColorScheme.DARKER_GRAY_COLOR)
			+ "; buttonArrowColor: " + hex(ColorScheme.LIGHT_GRAY_COLOR)
			+ "; borderColor: " + hex(ColorScheme.MEDIUM_GRAY_COLOR)
			+ "; focusedBorderColor: " + hex(ColorScheme.BRAND_ORANGE));
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return combo;
	}

	/** A number box, matching the drop-downs. */
	static JSpinner spinner(JSpinner spinner)
	{
		spinner.putClientProperty(STYLE, "arc: " + ARC + "; focusWidth: 0; buttonStyle: none"
			+ "; buttonBackground: " + hex(ColorScheme.DARKER_GRAY_COLOR)
			+ "; buttonArrowColor: " + hex(ColorScheme.LIGHT_GRAY_COLOR)
			+ "; borderColor: " + hex(ColorScheme.MEDIUM_GRAY_COLOR)
			+ "; focusedBorderColor: " + hex(ColorScheme.BRAND_ORANGE));
		spinner.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return spinner;
	}

	/**
	 * A tick box in the same family (elzerma: the old ones were hard to see): flat dark fill and a grey outline that
	 * turns orange on hover; ticked, filled orange with a dark tick. Ticked but disabled means the plugin saw it
	 * done (a run step) or read it from the game (a detected unlock): filled green, and it can't be changed.
	 * Disabled and empty (not yet possible): a faint outline.
	 */
	static JCheckBox checkBox(JCheckBox box)
	{
		box.setFocusPainted(false);
		box.setIconTextGap(5);
		// A little room above and below, so it doesn't touch a drop-down (elzerma)
		box.setMargin(new Insets(3, 1, 3, 1));
		// Drawn here, smaller than the look and feel's (elzerma: smaller tick boxes)
		box.setIcon(tick(ColorScheme.DARKER_GRAY_COLOR, ColorScheme.LIGHT_GRAY_COLOR.darker(), null));
		box.setRolloverIcon(tick(ColorScheme.DARKER_GRAY_COLOR, ColorScheme.BRAND_ORANGE, null));
		box.setPressedIcon(tick(ColorScheme.DARKER_GRAY_COLOR, ColorScheme.BRAND_ORANGE, null));
		box.setSelectedIcon(tick(ColorScheme.BRAND_ORANGE, null, ColorScheme.DARKER_GRAY_COLOR));
		box.setRolloverSelectedIcon(tick(ColorScheme.BRAND_ORANGE.brighter(), null, ColorScheme.DARKER_GRAY_COLOR));
		box.setDisabledIcon(tick(ColorScheme.DARK_GRAY_COLOR, ColorScheme.MEDIUM_GRAY_COLOR, null));
		box.setDisabledSelectedIcon(tick(GREEN, null, ColorScheme.DARKER_GRAY_COLOR));
		return box;
	}

	/** A small square tick box: a fill, an outline (or none), and a tick (or none). */
	private static Icon tick(Color fill, Color outline, Color check)
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y)
			{
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(fill);
				g2.fillRect(x, y, TICK_SIZE, TICK_SIZE);
				if (outline != null)
				{
					g2.setColor(outline);
					g2.drawRect(x, y, TICK_SIZE - 1, TICK_SIZE - 1);
				}
				if (check != null)
				{
					g2.setColor(check);
					g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g2.drawPolyline(new int[]{x + 2, x + 5, x + 9}, new int[]{y + 6, y + 9, y + 3}, 3);
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

	/** Titles and headings: RuneLite's normal font, not bold (elzerma: back to how it was). */
	static Font titleFont()
	{
		return FontManager.getRunescapeFont();
	}

	/** A heading in white, as every title is. */
	static <C extends JComponent> C title(C component)
	{
		component.setFont(titleFont());
		component.setForeground(Color.WHITE);
		return component;
	}

	static String hex(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}
}
