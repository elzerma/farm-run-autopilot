package com.farmrunautopilot.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * A titled section whose content can be shown or hidden by clicking the title.
 */
class CollapsibleSection extends JPanel
{
	private final JLabel header = new JLabel();
	private final JPanel content = new JPanel();
	private final String title;
	private boolean expanded;

	/**
	 * @param onToggle told the new expanded state, so it can be remembered across rebuilds
	 */
	CollapsibleSection(String title, boolean expanded, Consumer<Boolean> onToggle)
	{
		this.title = title;
		setLayout(new BorderLayout());
		setAlignmentX(LEFT_ALIGNMENT);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(0, 0, 6, 0));

		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setOpaque(true);
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 6, 6, 6));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!CollapsibleSection.this.expanded);
				onToggle.accept(CollapsibleSection.this.expanded);
			}
		});

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(6, 4, 0, 4));

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
		setExpanded(expanded);
	}

	void addContent(JComponent component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		content.add(component);
	}

	private void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		header.setText((expanded ? "- " : "+ ") + title);
		content.setVisible(expanded);
		revalidate();
	}
}
