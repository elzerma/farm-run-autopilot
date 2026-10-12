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
	/** What's set inside, shown after the title so a closed section still says it, e.g. "18 of 24"; or null. */
	private final String summary;
	private boolean expanded;
	/** What the section is for, shown on hovering its title (Sprint 7: explanations out of the way); or null. */
	private String help;

	/**
	 * @param onToggle told the new expanded state, so it can be remembered across rebuilds
	 */
	CollapsibleSection(String title, boolean expanded, Consumer<Boolean> onToggle)
	{
		this(title, null, expanded, onToggle);
	}

	CollapsibleSection(String title, String summary, boolean expanded, Consumer<Boolean> onToggle)
	{
		this.title = title;
		this.summary = summary;
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

	/** Explain the section on hovering its title, marked with a faint "(?)", instead of a note inside it. */
	CollapsibleSection help(String text)
	{
		help = text;
		header.setToolTipText(UiText.wrap(text, 250));
		setExpanded(expanded);
		return this;
	}

	void addContent(JComponent component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		content.add(component);
	}

	private void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		final String text = (expanded ? "- " : "+ ") + UiText.escape(title)
			+ (help != null ? " <font color='#6f6f6f'>(?)</font>" : "");
		header.setText(summary == null ? "<html>" + text + "</html>"
			: "<html>" + text + " <font color='#9a9a9a'>" + UiText.escape(summary) + "</font></html>");
		content.setVisible(expanded);
		revalidate();
	}
}
