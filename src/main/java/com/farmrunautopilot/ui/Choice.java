package com.farmrunautopilot.ui;

import lombok.Value;

/**
 * One dropdown entry. Disabled entries are shown greyed with a tooltip and can't be picked.
 */
@Value
class Choice<T>
{
	/** May be null, e.g. for "Auto" or "None". */
	T value;
	String label;
	boolean enabled;
	String tooltip;

	static <T> Choice<T> of(T value, String label)
	{
		return new Choice<>(value, label, true, null);
	}

	@Override
	public String toString()
	{
		return label;
	}
}
