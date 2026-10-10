package com.farmrunautopilot.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A one-line summary (e.g. "Catherby: Auto") that opens to show its controls when clicked. An orange
 * "Override" tag marks a row that differs from the defaults.
 */
class ExpandableRow extends JPanel
{
	private static final Color OVERRIDE = new Color(0xE8, 0xC5, 0x3A);

	private final JPanel content = new JPanel();
	private final JLabel header = new JLabel();
	private final String name;
	private final String summary;
	private final boolean overridden;
	private final int width;
	private boolean expanded;

	ExpandableRow(String name, String summary, boolean overridden, int width)
	{
		this.name = name;
		this.summary = summary;
		this.overridden = overridden;
		this.width = width;
		setLayout(new BorderLayout());
		setAlignmentX(LEFT_ALIGNMENT);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(0, 0, 2, 0));

		header.setOpaque(true);
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(3, 6, 3, 6));
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.TEXT_COLOR);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!expanded);
			}
		});

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(4, 6, 4, 0));

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
		setExpanded(false);
	}

	void addContent(JComponent component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		content.add(component);
	}

	private void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		final String tag = overridden
			? " <font color='#" + Integer.toHexString(OVERRIDE.getRGB() & 0xFFFFFF) + "'>Override</font>" : "";
		header.setText(UiText.wrap((expanded ? "- " : "+ ") + name + ": " + summary, width - 70)
			.replace("</body>", tag + "</body>"));
		content.setVisible(expanded);
		revalidate();
	}
}
