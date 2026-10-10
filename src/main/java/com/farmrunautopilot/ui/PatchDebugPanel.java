package com.farmrunautopilot.ui;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchStatusText;
import com.farmrunautopilot.tracking.PatchTracker;
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
 * Patch details list (Account > Testing & debug): every patch with its predicted state (milestone M2).
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

		final JLabel title = new JLabel("Patch states (debug)");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		title.setBorder(new EmptyBorder(0, 0, 6, 0));
		add(title);

		for (PatchType type : PatchType.values())
		{
			final JLabel header = new JLabel(type.getDisplayName() + " patches");
			header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			header.setBorder(new EmptyBorder(8, 0, 4, 0));
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
		wrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
		wrapper.add(row, BorderLayout.CENTER);
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
			status.setForeground(prediction == null ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
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
