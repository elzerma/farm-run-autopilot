package com.farmrunautopilot.ui;

import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The sidebar's tab strip. RuneLite's material tabs pad each label by 10px and space them 8px apart, which
 * only fits three tabs; this one pads by a few pixels so Run, Farm, Travel and Account fit on one line.
 */
class TabBar extends JPanel
{
	private static final int SIDE_PADDING = 4;
	private static final Border SELECTED = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE),
		new EmptyBorder(4, SIDE_PADDING, 3, SIDE_PADDING));
	private static final Border UNSELECTED = new EmptyBorder(4, SIDE_PADDING, 5, SIDE_PADDING);

	private final JPanel display;
	private final CardLayout cards = new CardLayout();
	private final List<JLabel> labels = new ArrayList<>();

	/**
	 * @param display where the selected tab's content is shown
	 */
	TabBar(JPanel display)
	{
		this.display = display;
		display.setLayout(cards);
		setLayout(new FlowLayout(FlowLayout.CENTER, 2, 0));
		setOpaque(false);
	}

	void addTab(String name, JComponent content)
	{
		final JLabel label = new JLabel(name);
		label.setFont(FontManager.getRunescapeFont());
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				select(name);
			}
		});
		labels.add(label);
		add(label);
		display.add(content, name);
		if (labels.size() == 1)
		{
			select(name);
		}
		else
		{
			style(label, false);
		}
	}

	void select(String name)
	{
		for (JLabel label : labels)
		{
			style(label, label.getText().equals(name));
		}
		cards.show(display, name);
	}

	private static void style(JLabel label, boolean selected)
	{
		label.setBorder(selected ? SELECTED : UNSELECTED);
		label.setForeground(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
	}
}
