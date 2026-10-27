package com.farmrunautopilot.settings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What to do with empty buckets after composting (docs/plans/patch-actions.md, P1: elzerma's three choices, drop by
 * default).
 */
@Getter
@RequiredArgsConstructor
public enum EmptyBuckets
{
	DROP("Drop them"),
	KEEP("Keep them"),
	STORE("Store them at the tool leprechaun");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
