package com.farmrunautopilot.atlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.requirement.TransportItems;

/**
 * One thing a transport on a path needs: a level, a quest, a var, items, or its type being allowed. Taking each away
 * from the pretend account in turn and asking again gives the next-best way (the "peeling" in
 * docs/plans/travel-rework.md, section 3).
 */
final class Requirement
{
	enum Kind
	{
		SKILL, QUEST, VAR, ITEMS, TYPE
	}

	final Kind kind;
	final String label;
	final Skill skill;
	final int level;
	final Quest quest;
	final int var;
	final int[] items;
	final TransportType type;

	private Requirement(Kind kind, String label, Skill skill, int level, Quest quest, int var, int[] items,
		TransportType type)
	{
		this.kind = kind;
		this.label = label;
		this.skill = skill;
		this.level = level;
		this.quest = quest;
		this.var = var;
		this.items = items;
		this.type = type;
	}

	static Requirement skill(Skill skill, int level)
	{
		return new Requirement(Kind.SKILL, level + " " + skill.getName(), skill, level, null, 0, null, null);
	}

	static Requirement quest(Quest quest)
	{
		return new Requirement(Kind.QUEST, quest.getName(), null, 0, quest, 0, null, null);
	}

	static Requirement var(int var)
	{
		return new Requirement(Kind.VAR, "var " + var, null, 0, null, var, null, null);
	}

	static Requirement items(int[] ids)
	{
		final StringBuilder label = new StringBuilder("item");
		for (int i = 0; i < ids.length && i < 3; i++)
		{
			label.append(i == 0 ? " " : "/").append(ids[i]);
		}
		return new Requirement(Kind.ITEMS, label + (ids.length > 3 ? "/..." : ""), null, 0, null, 0, ids, null);
	}

	static Requirement type(TransportType type)
	{
		return new Requirement(Kind.TYPE, "any " + type.name().toLowerCase().replace('_', ' '), null, 0, null, 0,
			null, type);
	}

	/** Everything these transports need, once each, in path order. */
	static List<Requirement> of(List<Transport> transports)
	{
		final Map<String, Requirement> found = new LinkedHashMap<>();
		for (Transport t : transports)
		{
			final int[] levels = t.getSkillLevels();
			for (int i = 0; i < Skill.values().length && i < levels.length; i++)
			{
				if (levels[i] > 1)
				{
					add(found, skill(Skill.values()[i], levels[i]));
				}
			}
			for (Quest quest : t.getQuests())
			{
				add(found, quest(quest));
			}
			final Set<VarRequirement> vars = t.getVarRequirements();
			for (VarRequirement v : vars)
			{
				add(found, var(v.getId()));
			}
			final TransportItems items = t.getItemRequirements();
			if (items != null)
			{
				for (int[] group : groups(items))
				{
					add(found, items(group));
				}
			}
			if (t.getType() != TransportType.TRANSPORT && t.getType() != TransportType.AGILITY_SHORTCUT)
			{
				add(found, type(t.getType()));
			}
		}
		return new ArrayList<>(found.values());
	}

	/** Each "any one of these" group of a transport's items, staves and off-hands included. */
	static List<int[]> groups(TransportItems items)
	{
		final List<int[]> groups = new ArrayList<>();
		final int[][] ids = items.getItems();
		final int[][] staves = items.getStaves();
		final int[][] offhands = items.getOffhands();
		for (int i = 0; i < ids.length; i++)
		{
			final List<Integer> group = new ArrayList<>();
			for (int[] part : new int[][]{ids[i], staves == null ? null : staves[i], offhands == null ? null : offhands[i]})
			{
				if (part != null)
				{
					for (int id : part)
					{
						group.add(id);
					}
				}
			}
			if (!group.isEmpty())
			{
				groups.add(group.stream().mapToInt(Integer::intValue).toArray());
			}
		}
		return groups;
	}

	private static void add(Map<String, Requirement> found, Requirement requirement)
	{
		found.putIfAbsent(requirement.label, requirement);
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Requirement && ((Requirement) o).label.equals(label);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(label);
	}
}
