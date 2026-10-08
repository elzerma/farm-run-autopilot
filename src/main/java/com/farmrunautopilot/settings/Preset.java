package com.farmrunautopilot.settings;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A named copy of the run settings (SPEC 13.5), e.g. "Full tree + fruit" or "Quick herbs". No-argument
 * constructor so Gson can build these normally.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Preset
{
	private String name;
	private RunConfig config;
}
