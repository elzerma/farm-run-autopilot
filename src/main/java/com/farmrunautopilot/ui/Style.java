package com.farmrunautopilot.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The sidebar's look (Sprint 7 facelift, Sean: "an updated RuneLite look. No gradients, but slightly softer
 * radius, really mature and polished"): RuneLite's own colours and fonts, flat fills, one soft radius. Controls
 * are styled through FlatLaf's style property, which RuneLite's look and feel reads, so they keep their normal
 * behaviour (keyboard, focus, disabled state).
 */
final class Style
{
	/** Corner radius for cards, buttons, drop-downs and number boxes: square (Sean: no radius). */
	static final int ARC = 0;
	/** Done, due, carried. */
	static final Color GREEN = new Color(0x4C, 0xC1, 0x52);
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
	 * A tick box in the same family (Sean: the old ones were hard to see): flat dark fill and a grey outline that
	 * turns orange on hover; ticked, filled orange with a dark tick. Ticked but disabled means the plugin saw it
	 * done (a run step) or read it from the game (a detected unlock): filled green, and it can't be changed.
	 * Disabled and empty (not yet possible): a faint outline.
	 */
	static JCheckBox checkBox(JCheckBox box)
	{
		box.setFocusPainted(false);
		box.putClientProperty(STYLE, "icon.arc: 0; icon.focusWidth: 0; icon.borderWidth: 1"
			+ "; icon.background: " + hex(ColorScheme.DARKER_GRAY_COLOR)
			+ "; icon.borderColor: " + hex(ColorScheme.LIGHT_GRAY_COLOR.darker())
			+ "; icon.hoverBorderColor: " + hex(ColorScheme.BRAND_ORANGE)
			+ "; icon.hoverBackground: " + hex(ColorScheme.DARKER_GRAY_COLOR)
			+ "; icon.focusedBorderColor: " + hex(ColorScheme.LIGHT_GRAY_COLOR.darker())
			+ "; icon.selectedBackground: " + hex(ColorScheme.BRAND_ORANGE)
			+ "; icon.selectedBorderColor: " + hex(ColorScheme.BRAND_ORANGE)
			+ "; icon.hoverSelectedBackground: " + hex(ColorScheme.BRAND_ORANGE.brighter())
			+ "; icon.focusedSelectedBackground: " + hex(ColorScheme.BRAND_ORANGE)
			+ "; icon.checkmarkColor: " + hex(ColorScheme.DARKER_GRAY_COLOR)
			+ "; icon.disabledBackground: " + hex(ColorScheme.DARK_GRAY_COLOR)
			+ "; icon.disabledBorderColor: " + hex(ColorScheme.MEDIUM_GRAY_COLOR)
			+ "; icon.disabledSelectedBackground: " + hex(GREEN)
			+ "; icon.disabledSelectedBorderColor: " + hex(GREEN)
			+ "; icon.disabledCheckmarkColor: " + hex(ColorScheme.DARKER_GRAY_COLOR));
		return box;
	}

	/** Titles and headings: RuneLite's normal font, not bold (Sean: back to how it was). */
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
