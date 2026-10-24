package com.farmrunautopilot.ui;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchStatusText;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Patch details list (Settings > Debug): every patch with its predicted state.
 */
class PatchDebugPanel extends JPanel
{
	private final PatchTracker tracker;
	private final Map<Patch, JLabel> statusLabels = new EnumMap<>(Patch.class);
	private final Map<Patch, JPanel> rows = new EnumMap<>(Patch.class);

	PatchDebugPanel(PatchTracker tracker)
	{
		this.tracker = tracker;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Under the section's own "Patch details" heading, so no title of its own; one small heading per type, on the
		// left like the rows (elzerma: two headings, and these floating in the middle, read as confusing)
		for (PatchType type : PatchType.values())
		{
			final JLabel header = new JLabel(type.getDisplayName() + " patches");
			header.setForeground(Color.WHITE);
			header.setFont(FontManager.getRunescapeSmallFont());
			header.setBorder(new EmptyBorder(8, 0, 3, 0));
			header.setAlignmentX(LEFT_ALIGNMENT);
			add(header);

			for (Patch patch : Patch.values())
			{
				if (patch.getType() == type)
				{
					add(createRow(patch));
				}
			}
		}
		refresh();
	}

	private JPanel createRow(Patch patch)
	{
		final JPanel row = new JPanel(new GridLayout(2, 1));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));

		final JLabel name = new JLabel(patch.getLocation().getDisplayName());
		name.setForeground(ColorScheme.TEXT_COLOR);
		name.setFont(FontManager.getRunescapeSmallFont());

		final JLabel status = new JLabel();
		status.setFont(FontManager.getRunescapeSmallFont());

		row.add(name);
		row.add(status);
		statusLabels.put(patch, status);
		rows.put(patch, row);

		final JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
		wrapper.add(row, BorderLayout.CENTER);
		wrapper.setAlignmentX(LEFT_ALIGNMENT);
		return wrapper;
	}

	/** Re-reads every prediction. Call on the Swing thread. */
	void refresh()
	{
		final long now = Instant.now().getEpochSecond();
		for (Patch patch : Patch.values())
		{
			final PatchPrediction prediction = tracker.predict(patch);
			final JLabel status = statusLabels.get(patch);
			status.setText(PatchStatusText.describe(prediction, now));
			status.setForeground(prediction == null ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
			rows.get(patch).setToolTipText(tooltip(patch, prediction, now));
		}
	}

	private static String tooltip(Patch patch, PatchPrediction p, long now)
	{
		if (p == null)
		{
			return patch.getDisplayName() + ": no data yet. Visit the patch to record it.";
		}
		final StringBuilder sb = new StringBuilder("<html>")
			.append(patch.getDisplayName())
			.append("<br>").append(PatchStatusText.age(p.getObservedAt(), now));
		if (p.getPlantedAt() > 0)
		{
			sb.append("<br>planted ").append(PatchStatusText.duration(now - p.getPlantedAt())).append(" ago");
		}
		if (p.getSource() == PatchPrediction.Source.TIME_TRACKING)
		{
			sb.append("<br>from RuneLite Time Tracking");
		}
		return sb.append("</html>").toString();
	}
}
