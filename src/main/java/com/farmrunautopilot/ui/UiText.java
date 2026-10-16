package com.farmrunautopilot.ui;

/**
 * Text helpers shared by the sidebar tabs.
 */
final class UiText
{
	private UiText()
	{
	}

	/**
	 * HTML that wraps to {@code width} screen pixels, since plain Swing text never wraps. Swing's HTML
	 * renderer scales CSS "px" by 1.3 (javax.swing.text.html.CSS), so divide that back out.
	 */
	static String wrap(String text, int width)
	{
		return "<html><body style='width:" + (width * 10 / 13) + "px'>" + escape(text) + "</body></html>";
	}

	/**
	 * Hover text that's only as wide as it needs to be, up to {@code width} screen pixels (elzerma: short hovers took
	 * the full width). Each line is measured in the tooltip font; only if one is longer does the text wrap.
	 */
	static String tooltip(String text, int width)
	{
		return tooltip(java.util.Collections.singletonList(text), width);
	}

	/** The same for several lines, each starting on its own line. */
	static String tooltip(java.util.List<String> lines, int width)
	{
		java.awt.Font font = javax.swing.UIManager.getFont("ToolTip.font");
		if (font == null)
		{
			font = net.runelite.client.ui.FontManager.getRunescapeFont();
		}
		final java.awt.FontMetrics metrics = new javax.swing.JLabel().getFontMetrics(font);
		boolean fits = true;
		final StringBuilder body = new StringBuilder();
		for (String line : lines)
		{
			fits &= metrics.stringWidth(line) <= width;
			body.append(body.length() == 0 ? "" : "<br>").append(escape(line));
		}
		return fits ? "<html>" + body + "</html>"
			: "<html><body style='width:" + (width * 10 / 13) + "px'>" + body + "</body></html>";
	}

	/** Item and place names can contain characters HTML treats specially. */
	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
