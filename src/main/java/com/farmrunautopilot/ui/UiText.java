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

	/** Item and place names can contain characters HTML treats specially. */
	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
