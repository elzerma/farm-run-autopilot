package com.farmrunautopilot.ui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar's tab strip: one boxed button per tab sharing the width evenly, an icon each with its name on hover
 * (elzerma: five names don't fit across the sidebar), whichever window is active. The selected tab's icon is orange
 * with a line under it.
 */
class TabBar extends JPanel
{
	// Boxed buttons sharing the width evenly (elzerma); the selected one has an orange line along its bottom
	private static final Border SELECTED = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE), new EmptyBorder(5, 0, 3, 0));
	private static final Border UNSELECTED = new EmptyBorder(5, 0, 5, 0);

	private final JPanel display;
	private final CardLayout cards = new VisibleCardLayout();
	private final List<JLabel> labels = new ArrayList<>();
	private final NameTip nameTip = new NameTip();

	/**
	 * @param display where the selected tab's content is shown
	 */
	TabBar(JPanel display)
	{
		this.display = display;
		display.setLayout(cards);
		setLayout(new GridLayout(1, 0, 3, 0));
		setOpaque(false);
		// Not left on screen when the sidebar closes under the mouse
		addHierarchyListener(e ->
		{
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing())
			{
				nameTip.hide();
			}
		});
	}

	void addTab(String name, TabIcon icon, JComponent content)
	{
		final JLabel label = new JLabel();
		label.setName(name);
		label.setHorizontalAlignment(JLabel.CENTER);
		label.setOpaque(true);
		label.putClientProperty(TabIcon.class, icon);
		label.addMouseListener(nameTip);
		label.addMouseMotionListener(nameTip);
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
	 * A tab's name on hover, looking and timed as RuneLite's tooltips. Not a tooltip, as RuneLite's look and feel shows
	 * those only while its window is the active one: with another window active (elzerma, reading hand checks), a tab
	 * lit up under the mouse but never showed its name.
	 */
	private static final class NameTip extends MouseAdapter
	{
		/** How far below the mouse, as Swing puts a tooltip. */
		private static final int BELOW = 20;

		private final Timer showTimer = new Timer(0, e -> show());
		private final Timer hideTimer = new Timer(0, e -> hide());
		/** The tab under the mouse. */
		private JLabel over;
		/** The mouse, on screen. */
		private Point mouse;
		private Popup popup;
		/** When the mouse last left a tab showing its name: the next tab's shows straight away, as tooltips do. */
		private long leftAt;

		NameTip()
		{
			showTimer.setRepeats(false);
			hideTimer.setRepeats(false);
		}

		@Override
		public void mouseEntered(MouseEvent e)
		{
			over = (JLabel) e.getComponent();
			mouse = e.getLocationOnScreen();
			final ToolTipManager tips = ToolTipManager.sharedInstance();
			showTimer.setInitialDelay(System.currentTimeMillis() - leftAt < tips.getReshowDelay()
				? 0 : tips.getInitialDelay());
			showTimer.restart();
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			mouse = e.getLocationOnScreen();
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			if (popup != null)
			{
				leftAt = System.currentTimeMillis();
			}
			hide();
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			// Gone on a click until the mouse comes back, as a tooltip is
			hide();
		}

		private void show()
		{
			if (over == null || !over.isShowing())
			{
				return;
			}
			final JToolTip tip = over.createToolTip();
			tip.setTipText(over.getName());
			// A window of its own (FlatLaf's option), as RuneLite's tooltips are, so the game never draws over it
			tip.putClientProperty("Popup.forceHeavyWeight", true);
			final Dimension size = tip.getPreferredSize();
			final Rectangle screen = over.getGraphicsConfiguration().getBounds();
			final int x = Math.max(screen.x, Math.min(mouse.x, screen.x + screen.width - size.width));
			// Above the mouse at the bottom of the screen, never under it: it would take the mouse off the tab
			final int y = mouse.y + BELOW + size.height <= screen.y + screen.height ? mouse.y + BELOW
				: mouse.y - size.height - 4;
			popup = PopupFactory.getSharedInstance().getPopup(over, tip, x, y);
			popup.show();
			hideTimer.setInitialDelay(ToolTipManager.sharedInstance().getDismissDelay());
			hideTimer.restart();
		}

		/** Takes the name away, and one about to show. */
		private void hide()
		{
			showTimer.stop();
			hideTimer.stop();
			if (popup != null)
			{
				popup.hide();
				popup = null;
			}
		}
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
