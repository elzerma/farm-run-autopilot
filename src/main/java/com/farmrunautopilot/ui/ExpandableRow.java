package com.farmrunautopilot.ui;

import java.awt.BorderLayout;
import java.awt.Color;
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
import net.runelite.client.ui.FontManager;

/**
 * One line, e.g. "Taverley" and "Falador Teleport", that opens to show its controls when clicked (Sean: one line
 * per location, the full way on hover and when opened). A yellow bar down its edge marks a row changed from the
 * default.
 */
class ExpandableRow extends JPanel
{
	private static final Color OVERRIDE = new Color(0xE8, 0xC5, 0x3A);

	private final JPanel content = new JPanel();
	private final JLabel name = new JLabel();
	/** The clickable line: a rounded card with a chevron (Sprint 7 facelift). */
	private final Card headCard = new Card(new BorderLayout(8, 0)).hoverable();
	private boolean expanded;

	/**
	 * @param name e.g. "Taverley"
	 * @param shortSummary what it's set to, short enough to share the line, e.g. "Falador Teleport"
	 * @param fullSummary the whole of it, for the tooltip
	 * @param expanded open to start with
	 * @param onToggle told the new state, so it can be kept across redraws
	 */
	ExpandableRow(String name, String shortSummary, String fullSummary, boolean overridden, int width,
		boolean expanded, Consumer<Boolean> onToggle)
	{
		setLayout(new BorderLayout());
		setAlignmentX(LEFT_ALIGNMENT);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(0, 0, 3, 0));

		headCard.accent(overridden ? OVERRIDE : null);
		this.name.setText(UiText.wrap(name, width / 2 - 20));
		this.name.setIconTextGap(6);
		this.name.setFont(FontManager.getRunescapeSmallFont());
		this.name.setForeground(Color.WHITE);
		final JLabel summary = new JLabel(UiText.wrap(shortSummary, width / 2 - 10));
		summary.setFont(FontManager.getRunescapeSmallFont());
		summary.setForeground(Color.WHITE);
		headCard.add(this.name, BorderLayout.CENTER);
		headCard.add(summary, BorderLayout.EAST);
		headCard.setToolTipText(UiText.wrap(name + ": " + fullSummary
			+ (overridden ? " (changed from Auto)" : ""), 250));
		headCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		headCard.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!ExpandableRow.this.expanded);
				onToggle.accept(ExpandableRow.this.expanded);
			}
		});

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(2, 4, 2, 0));

		add(headCard, BorderLayout.NORTH);
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
		name.setIcon(Card.chevron(expanded, Color.WHITE));
		content.setVisible(expanded);
		revalidate();
	}
}
