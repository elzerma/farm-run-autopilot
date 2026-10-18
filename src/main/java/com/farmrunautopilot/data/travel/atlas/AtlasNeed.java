package com.farmrunautopilot.data.travel.atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * One thing an atlas way, teleport or hop needs (requirements.tsv): a level, a quest, a varbit or varplayer condition,
 * items, or a transport type being usable.
 */
@Getter
public final class AtlasNeed
{
	public enum Kind
	{
		SKILL, QUEST, VARBIT, VARP, ITEMS, TYPE,
		/** A spirit tree the player planted (the patch's id in {@link #type}, e.g. FARMING_GUILD_SPIRIT_TREE). */
		PLANTED,
		/** Something this RuneLite version doesn't know (a quest it lacks): never met. */
		UNKNOWN
	}

	/** One way to meet an item need: enough of any of these items, any of the staves or off-hands that cover them. */
	@Getter
	public static final class Branch
	{
		private final int[] items;
		private final int quantity;
		private final int[] covers;
		/** One of Shortest Path's declared unlocks instead of items (e.g. KARAMJA_ELITE), or null. */
		private final String unlock;

		Branch(int[] items, int quantity, int[] covers, String unlock)
		{
			this.items = items;
			this.quantity = quantity;
			this.covers = covers;
			this.unlock = unlock;
		}
	}

	private final Kind kind;
	/** What a person reads, e.g. "45 Agility", "TREEQUEST > 0". */
	private final String name;
	private final Skill skill;
	private final Quest quest;
	private final int level;
	/** The var's id, its check ("=", ">", "<", "&", "@") and the value. */
	private final int var;
	private final String check;
	private final int value;
	/** Any one of these meets an item need. */
	private final List<Branch> branches;
	private final String type;

	private AtlasNeed(Kind kind, String name, Skill skill, Quest quest, int level, int var, String check, int value,
		List<Branch> branches, String type)
	{
		this.kind = kind;
		this.name = name;
		this.skill = skill;
		this.quest = quest;
		this.level = level;
		this.var = var;
		this.check = check;
		this.value = value;
		this.branches = branches;
		this.type = type;
	}

	/**
	 * A row of requirements.tsv: its kind, key and name. Keys: "AGILITY 45", "CABIN_FEVER", "111 > 0", "FAIRY_RING",
	 * and for items branches split by ";", each "id|id=quantity", with "/id|id" for staves or off-hands that cover it,
	 * or "unlock:NAME".
	 */
	static AtlasNeed parse(String kind, String key, String name)
	{
		try
		{
			switch (Kind.valueOf(kind))
			{
				case SKILL:
				{
					final String[] parts = key.split(" ");
					return new AtlasNeed(Kind.SKILL, name, Skill.valueOf(parts[0]), null, Integer.parseInt(parts[1]), 0, null,
						0, Collections.emptyList(), null);
				}
				case QUEST:
					return new AtlasNeed(Kind.QUEST, name, null, Quest.valueOf(key), 0, 0, null, 0, Collections.emptyList(),
						null);
				case VARBIT:
				case VARP:
				{
					final String[] parts = key.split(" ");
					return new AtlasNeed(Kind.valueOf(kind), name, null, null, 0, Integer.parseInt(parts[0]), parts[1],
						Integer.parseInt(parts[2]), Collections.emptyList(), null);
				}
				case ITEMS:
				{
					final List<Branch> branches = new ArrayList<>();
					for (String branch : key.split(";"))
					{
						if (branch.startsWith("unlock:"))
						{
							branches.add(new Branch(new int[0], 1, new int[0], branch.substring("unlock:".length())));
							continue;
						}
						final String[] coverSplit = branch.split("/");
						final String[] amount = coverSplit[0].split("=");
						branches.add(new Branch(ids(amount[0]), Integer.parseInt(amount[1]),
							coverSplit.length > 1 ? ids(coverSplit[1]) : new int[0], null));
					}
					return new AtlasNeed(Kind.ITEMS, name, null, null, 0, 0, null, 0, branches, null);
				}
				case TYPE:
				case PLANTED:
					return new AtlasNeed(Kind.valueOf(kind), name, null, null, 0, 0, null, 0, Collections.emptyList(), key);
				default:
					break;
			}
		}
		catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e)
		{
			// Falls through to unknown
		}
		return new AtlasNeed(Kind.UNKNOWN, name, null, null, 0, 0, null, 0, Collections.emptyList(), null);
	}

	/** Whether a var's value passes this check. A cooldown ("@") counts as passed: cooldown teleports aren't planned. */
	public boolean passes(int current)
	{
		switch (check)
		{
			case "=":
				return current == value;
			case ">":
				return current > value;
			case "<":
				return current < value;
			case "&":
				return (current & value) != 0;
			default:
				return true;
		}
	}

	private static int[] ids(String list)
	{
		if (list.isEmpty())
		{
			return new int[0];
		}
		final String[] parts = list.split("\\|");
		final int[] ids = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			ids[i] = Integer.parseInt(parts[i]);
		}
		return ids;
	}
}
