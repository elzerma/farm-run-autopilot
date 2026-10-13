package com.farmrunautopilot.ui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Cursor;
import java.awt.GridLayout;
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

/**
 * The sidebar's tab strip: one boxed button per tab sharing the width evenly, an icon each with its name on hover
 * (Sean: five names don't fit across the sidebar). The selected tab's icon is orange with a line under it.
 */
class TabBar extends JPanel
{
	// Boxed buttons sharing the width evenly (Sean); the selected one has an orange line along its bottom
	private static final Border SELECTED = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE), new EmptyBorder(5, 0, 3, 0));
	private static final Border UNSELECTED = new EmptyBorder(5, 0, 5, 0);

	private final JPanel display;
	private final CardLayout cards = new VisibleCardLayout();
	private final List<JLabel> labels = new ArrayList<>();

	/**
	 * @param display where the selected tab's content is shown
	 */
	TabBar(JPanel display)
	{
		this.display = display;
		display.setLayout(cards);
		setLayout(new GridLayout(1, 0, 3, 0));
		setOpaque(false);
	}

	void addTab(String name, TabIcon icon, JComponent content)
	{
		final JLabel label = new JLabel();
		label.setName(name);
		label.setHorizontalAlignment(JLabel.CENTER);
		label.setOpaque(true);
		label.putClientProperty(TabIcon.class, icon);
		label.setToolTipText(name);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				select(name);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR.brighter());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
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
			style(label, label.getName().equals(name));
		}
		cards.show(display, name);
		// The new tab may be shorter or longer: let the scroll pane re-measure
		display.revalidate();
	}

	private static void style(JLabel label, boolean selected)
	{
		label.setBorder(selected ? SELECTED : UNSELECTED);
		label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		final TabIcon icon = (TabIcon) label.getClientProperty(TabIcon.class);
		label.setIcon(icon.icon(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR));
	}

	/**
	 * A card layout as tall as the tab being shown. The standard one is as tall as the tallest tab, which left
	 * the shorter tabs with a long scroll of empty space.
	 */
	private static final class VisibleCardLayout extends CardLayout
	{
		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			final Component shown = visible(parent);
			return withInsets(parent, shown != null ? shown.getPreferredSize() : new Dimension());
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			final Component shown = visible(parent);
			return withInsets(parent, shown != null ? shown.getMinimumSize() : new Dimension());
		}

		private static Component visible(Container parent)
		{
			for (Component component : parent.getComponents())
			{
				if (component.isVisible())
				{
					return component;
				}
			}
			return null;
		}

		private static Dimension withInsets(Container parent, Dimension size)
		{
			final Insets insets = parent.getInsets();
			return new Dimension(size.width + insets.left + insets.right, size.height + insets.top + insets.bottom);
		}
	}
}
