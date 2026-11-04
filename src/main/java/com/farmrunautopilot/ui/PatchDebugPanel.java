package com.farmrunautopilot.ui;

import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.settings.SettingsStore;
import com.farmrunautopilot.tracking.PatchPrediction;
import com.farmrunautopilot.tracking.PatchStatusText;
import com.farmrunautopilot.tracking.PatchTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Patch details list (Settings > Debug): every patch with its predicted state, in a group per patch type that opens and
 * closes and stays that way, a line per patch, as the Run tab's patch timers (docs/plans/fresh-eyes-audit.md, S2: 80
 * two-line cards made Debug 3,800 px tall).
 */
class PatchDebugPanel extends JPanel
{
	private final PatchTracker tracker;
	private final SettingsStore settings;

	PatchDebugPanel(PatchTracker tracker, SettingsStore settings)
	{
		this.tracker = tracker;
		this.settings = settings;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		refresh();
	}

	/** Re-reads every prediction. Call on the Swing thread. */
	void refresh()
	{
		removeAll();
		final long now = Instant.now().getEpochSecond();
		for (PatchType type : PatchType.values())
		{
			final List<Patch> patches = new ArrayList<>();
			final Map<Patch, PatchPrediction> predictions = new EnumMap<>(Patch.class);
			int seen = 0;
			for (Patch patch : Patch.values())
			{
				if (patch.getType() == type)
				{
					final PatchPrediction prediction = tracker.predict(patch);
					patches.add(patch);
					predictions.put(patch, prediction);
					seen += prediction != null ? 1 : 0;
				}
			}
			final String key = "Patch details: " + type.name();
			final boolean open = settings.isSectionOpen(key);
			add(Card.groupHeader(type.getPluralName(), seen + " of " + patches.size() + " seen", open, () ->
			{
				settings.setSectionOpen(key, !open);
				refresh();
			}));
			if (!open)
			{
				continue;
			}
			final Card rows = new Card(null);
			rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
			for (Patch patch : patches)
			{
				rows.add(row(patch, predictions.get(patch), now));
			}
			final JPanel wrapper = new JPanel(new BorderLayout())
			{
				@Override
				public Dimension getMaximumSize()
				{
					return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
				}
			};
			wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
			wrapper.setBorder(new EmptyBorder(0, 0, 3, 0));
			wrapper.add(rows, BorderLayout.CENTER);
			wrapper.setAlignmentX(LEFT_ALIGNMENT);
			add(wrapper);
		}
		revalidate();
		repaint();
	}

	/** One patch on one line: where on the left, its state on the right (grey when not seen yet); the rest on hover. */
	private static JComponent row(Patch patch, PatchPrediction prediction, long now)
	{
		final String described = PatchStatusText.describe(prediction, now);
		// "Magic - ready in 3h": the state on the line, the crop in the tooltip
		final int dash = described.indexOf(" - ");
		final JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		final String part = patch.getPartInitials();
		final JLabel name = new JLabel(RunPanel.timerName(patch.getLocation()) + (part.isEmpty() ? "" : " " + part));
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		final JLabel status = new JLabel(dash >= 0 ? described.substring(dash + 3) : described);
		status.setForeground(prediction == null ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
		status.setFont(FontManager.getRunescapeSmallFont());
		row.add(name, BorderLayout.CENTER);
		row.add(status, BorderLayout.EAST);
		row.setToolTipText(tooltip(patch, prediction, described, now));
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

	private static String tooltip(Patch patch, PatchPrediction p, String described, long now)
	{
		if (p == null)
		{
			return patch.getDisplayName() + ": no data yet. Visit the patch to record it.";
		}
		final StringBuilder sb = new StringBuilder("<html>")
			.append(UiText.escape(patch.getDisplayName())).append(": ").append(UiText.escape(described))
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
