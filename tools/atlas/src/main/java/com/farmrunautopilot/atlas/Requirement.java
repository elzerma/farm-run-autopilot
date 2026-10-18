package com.farmrunautopilot.atlas;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
import shortestpath.transport.requirement.ItemRequirement;
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
		SKILL, QUEST, VARBIT, VARP, ITEMS, TYPE,
		/** A spirit tree the player planted themselves (its patch id): Shortest Path checks these outside its data. */
		PLANTED
	}

	final Kind kind;
	/** What a person reads, e.g. "45 Agility", "TREEQUEST > 0", "COINS x30". Also what makes two the same. */
	final String label;
	/** What the plugin reads, e.g. "AGILITY 45", "111 > 0", "995=30". */
	final String key;
	final Skill skill;
	final int level;
	final Quest quest;
	final int var;
	final int[] items;
	final TransportType type;

	private Requirement(Kind kind, String label, String key, Skill skill, int level, Quest quest, int var, int[] items,
		TransportType type)
	{
		this.kind = kind;
		this.label = label;
		this.key = key;
		this.skill = skill;
		this.level = level;
		this.quest = quest;
		this.var = var;
		this.items = items;
		this.type = type;
	}

	static Requirement skill(Skill skill, int level)
	{
		return new Requirement(Kind.SKILL, level + " " + skill.getName(), skill.name() + " " + level, skill, level, null, 0,
			null, null);
	}

	static Requirement quest(Quest quest)
	{
		return new Requirement(Kind.QUEST, quest.getName(), quest.name(), null, 0, quest, 0, null, null);
	}

	/** Only for taking a var away by number ("-var=1234"). */
	static Requirement var(int var)
	{
		return new Requirement(Kind.VARBIT, "var " + var, var + "", null, 0, null, var, null, null);
	}

	static Requirement var(VarRequirement v)
	{
		final String condition = " " + v.getCheckType().getCode() + " " + v.getValue();
		final boolean varbit = v.isVarbit();
		return new Requirement(varbit ? Kind.VARBIT : Kind.VARP,
			(varbit ? Names.varbit(v.getId()) : Names.varp(v.getId())) + condition, v.getId() + condition,
			null, 0, null, v.getId(), null, null);
	}

	/**
	 * One item requirement: any one of its branches, each "items=quantity" with any staves or off-hands that cover it
	 * after a "/" (runes), or "unlock:NAME" for one of Shortest Path's declared unlocks; branches are split by ";".
	 * Taking it away removes every item it names.
	 */
	static Requirement items(ItemRequirement requirement)
	{
		final List<String> branches = new ArrayList<>();
		final Set<Integer> all = new LinkedHashSet<>();
		int first = -1;
		int firstQuantity = 1;
		for (ItemRequirement.Branch b : requirement.getBranches())
		{
			if (b.isUnlockOnly())
			{
				branches.add("unlock:" + b.getUnlock().name());
				continue;
			}
			final int[] ids = b.getItemIds() == null ? new int[0] : b.getItemIds();
			final List<Integer> covers = new ArrayList<>();
			for (int[] part : new int[][]{b.getStaffIds(), b.getOffhandIds()})
			{
				if (part != null)
				{
					for (int id : part)
					{
						covers.add(id);
					}
				}
			}
			final StringBuilder branch = new StringBuilder();
			for (int id : ids)
			{
				branch.append(branch.length() == 0 ? "" : "|").append(id);
				all.add(id);
				if (first < 0)
				{
					first = id;
					firstQuantity = Math.max(1, b.getQuantity());
				}
			}
			branch.append('=').append(Math.max(1, b.getQuantity()));
			if (!covers.isEmpty())
			{
				branch.append('/');
				for (int i = 0; i < covers.size(); i++)
				{
					branch.append(i == 0 ? "" : "|").append(covers.get(i));
					all.add(covers.get(i));
				}
			}
			branches.add(branch.toString());
		}
		final int others = all.size() - 1;
		final String label = (first < 0 ? String.join(" or ", branches) : Names.item(first))
			+ (others > 0 ? " (or " + others + " more)" : "") + (firstQuantity > 1 ? " x" + firstQuantity : "");
		return new Requirement(Kind.ITEMS, label, String.join(";", branches), null, 0, null, 0,
			all.stream().mapToInt(Integer::intValue).toArray(), null);
	}

	/** The spirit tree growing in this patch (e.g. FARMING_GUILD_SPIRIT_TREE), planted by the player. */
	static Requirement planted(String patch)
	{
		return new Requirement(Kind.PLANTED, "spirit tree planted at " + patch, patch, null, 0, null, 0, null, null);
	}

	static Requirement type(TransportType type)
	{
		return new Requirement(Kind.TYPE, "any " + type.name().toLowerCase().replace('_', ' '), type.name(), null, 0,
			null, 0, null, type);
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
			for (VarRequirement v : t.getVarRequirements())
			{
				add(found, var(v));
			}
			final TransportItems items = t.getItemRequirements();
			if (items != null)
			{
				for (ItemRequirement requirement : items.getRequirements())
				{
					add(found, items(requirement));
				}
			}
			if (t.getType() != TransportType.TRANSPORT && t.getType() != TransportType.AGILITY_SHORTCUT)
			{
				add(found, type(t.getType()));
			}
		}
		return new ArrayList<>(found.values());
	}

	/** Every item, staff and off-hand any of these item requirements names. */
	static Set<Integer> allItems(TransportItems items)
	{
		final Set<Integer> all = new LinkedHashSet<>();
		for (ItemRequirement requirement : items.getRequirements())
		{
			for (int id : items(requirement).items)
			{
				all.add(id);
			}
		}
		return all;
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
