package com.farmrunautopilot.atlas;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Readable names for item, varbit and varplayer ids, from RuneLite's gameval constants (dev tool only). */
final class Names
{
	private static final Map<Integer, String> ITEMS = constants(ItemID.class);
	private static final Map<Integer, String> VARBITS = constants(VarbitID.class);
	private static final Map<Integer, String> VARPS = constants(VarPlayerID.class);

	private Names()
	{
	}

	static String item(int id)
	{
		return ITEMS.getOrDefault(id, "item " + id);
	}

	/** An item id by its gameval name, e.g. "RING_OF_WEALTH_5". */
	static int itemId(String name)
	{
		try
		{
			return ItemID.class.getField(name).getInt(null);
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalArgumentException("No ItemID." + name, e);
		}
	}

	static String varbit(int id)
	{
		return VARBITS.getOrDefault(id, "varbit " + id);
	}

	static String varp(int id)
	{
		return VARPS.getOrDefault(id, "varp " + id);
	}

	private static Map<Integer, String> constants(Class<?> type)
	{
		final Map<Integer, String> names = new HashMap<>();
		for (Field field : type.getFields())
		{
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class)
			{
				try
				{
					names.putIfAbsent(field.getInt(null), field.getName());
				}
				catch (IllegalAccessException ignored)
				{
				}
			}
		}
		return names;
	}
}
