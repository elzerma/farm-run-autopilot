package com.farmrunautopilot.route;

import com.farmrunautopilot.data.PatchType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Singleton;

/**
 * The player's "include anyway" / "skip this run" choices (SPEC 10). Kept for the session only, since they
 * are about the next run, not a lasting setting. Safe to use from any thread.
 */
@Singleton
public class RunOverrides
{
	private final Map<PatchType, TypeOverride> overrides = new EnumMap<>(PatchType.class);

	public synchronized Map<PatchType, TypeOverride> get()
	{
		return Collections.unmodifiableMap(new EnumMap<>(overrides));
	}

	/**
	 * @param override null to go back to following the due threshold
	 */
	public synchronized void set(PatchType type, TypeOverride override)
	{
		if (override == null)
		{
			overrides.remove(type);
		}
		else
		{
			overrides.put(type, override);
		}
	}

	public synchronized void clear()
	{
		overrides.clear();
	}
}
