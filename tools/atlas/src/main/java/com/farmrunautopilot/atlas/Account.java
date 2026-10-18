package com.farmrunautopilot.atlas;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.ItemVariations;
import shortestpath.ShortestPathConfig;
import shortestpath.TeleportationItem;
import shortestpath.transport.Transport;
import shortestpath.transport.TransportType;
import shortestpath.transport.requirement.TransportItems;

/**
 * A pretend account for the bubble: its levels, quests and items, and the requirements taken away from it. It answers
 * Shortest Path's questions through a stand-in for RuneLite's client, the way Shortest Path's own tests do; nothing
 * here talks to the game.
 */
final class Account
{
	private static final int MAXED = 99;

	final String name;
	final Map<Skill, Integer> levels;
	final Set<Quest> questsNotDone;
	/** Varbits and varplayers whose transports count as locked. */
	final Set<Integer> lockedVars;
	/** Every transport that needs any var counts as locked (the "needs nothing" account). */
	final boolean lockAllVars;
	/** Vars checked against these values; any other var check passes unless locked. */
	final Map<Integer, Integer> varValues;
	final Map<Integer, Integer> items;
	final Set<TransportType> typesOff;

	private Account(String name, Map<Skill, Integer> levels, Set<Quest> questsNotDone, Set<Integer> lockedVars,
		boolean lockAllVars, Map<Integer, Integer> varValues, Map<Integer, Integer> items, Set<TransportType> typesOff)
	{
		this.name = name;
		this.levels = levels;
		this.questsNotDone = questsNotDone;
		this.lockedVars = lockedVars;
		this.lockAllVars = lockAllVars;
		this.varValues = varValues;
		this.items = items;
		this.typesOff = typesOff;
	}

	/** Every level 99, every quest done, every var unlocked, carrying every item a transport asks for. */
	static Account maxed(Map<Integer, Integer> items)
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			levels.put(skill, MAXED);
		}
		return new Account("maxed", levels, EnumSet.noneOf(Quest.class), new LinkedHashSet<>(), false, Map.of(), items,
			EnumSet.noneOf(TransportType.class));
	}

	/** Every item any transport asks for, plenty of each, plus a dramen staff for the fairy rings. */
	static Map<Integer, Integer> everyItem(BubbleConfig.World world)
	{
		final Map<Integer, Integer> items = new HashMap<>();
		for (Set<Transport> set : world.transports.values())
		{
			for (Transport t : set)
			{
				final TransportItems required = t.getItemRequirements();
				if (required != null)
				{
					for (int id : Requirement.allItems(required))
					{
						items.put(id, 1_000_000);
					}
				}
			}
		}
		for (int id : ItemVariations.DRAMEN_STAFF.getIds())
		{
			items.put(id, 1);
		}
		items.put(ItemID.COINS, 1_000_000_000);
		return items;
	}

	/** This account with the hop networks off ({@link Places#HUB_TYPES}): the atlas's ways leave hops to the plugin. */
	Account withoutHops()
	{
		final Set<TransportType> types = EnumSet.noneOf(TransportType.class);
		types.addAll(typesOff);
		types.addAll(Places.HUB_TYPES);
		return new Account(name, levels, questsNotDone, lockedVars, lockAllVars, varValues, items, types);
	}

	/**
	 * Needs nothing: level 1 everywhere, no quests, no items, every var-gated transport locked, and only walking,
	 * doors, stairs and the like (agility shortcuts stay, gated by the level-1 skills).
	 */
	static Account bare()
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			levels.put(skill, 1);
		}
		final Set<TransportType> off = EnumSet.allOf(TransportType.class);
		off.remove(TransportType.TRANSPORT);
		off.remove(TransportType.AGILITY_SHORTCUT);
		return new Account("needs nothing", levels, EnumSet.allOf(Quest.class), new LinkedHashSet<>(), true, Map.of(),
			Map.of(), off);
	}

	/** This account with these levels, var values and items instead (for the referee's pretend players). */
	Account as(String newName, Map<Skill, Integer> newLevels, Set<Quest> notDone, Map<Integer, Integer> vars,
		Map<Integer, Integer> newItems)
	{
		return new Account(newName, newLevels, notDone, lockedVars, false, vars, newItems, typesOff);
	}

	/**
	 * This account with changes, e.g. "agility=44 -quest=CABIN_FEVER -type=FAIRY_RING -var=1234": a level, a quest
	 * not done, a transport type off, a var locked.
	 */
	Account with(String spec)
	{
		Account account = this;
		for (String token : spec.trim().split("\\s+"))
		{
			if (token.isEmpty() || token.equals("maxed"))
			{
				continue;
			}
			final String[] kv = token.replaceFirst("^-", "").split("=", 2);
			switch (kv[0])
			{
				case "quest":
					account = account.without(Requirement.quest(Quest.valueOf(kv[1])));
					break;
				case "type":
					account = account.without(Requirement.type(TransportType.valueOf(kv[1])));
					break;
				case "var":
					account = account.without(Requirement.var(Integer.parseInt(kv[1])));
					break;
				default:
					final Skill skill = Skill.valueOf(kv[0].toUpperCase());
					account = account.withLevel(skill, Integer.parseInt(kv[1]));
					break;
			}
		}
		return account;
	}

	private Account withLevel(Skill skill, int level)
	{
		final Map<Skill, Integer> changed = new EnumMap<>(levels);
		changed.put(skill, level);
		return new Account(name + ", " + level + " " + skill.getName(), changed, questsNotDone, lockedVars, lockAllVars,
			varValues, items, typesOff);
	}

	/** This account without what the requirement asks for: one level short, the quest not done, and so on. */
	Account without(Requirement requirement)
	{
		final String changedName = name + ", no " + requirement.label;
		switch (requirement.kind)
		{
			case SKILL:
				return withLevel(requirement.skill, requirement.level - 1);
			case QUEST:
				final Set<Quest> quests = EnumSet.noneOf(Quest.class);
				quests.addAll(questsNotDone);
				quests.add(requirement.quest);
				return new Account(changedName, levels, quests, lockedVars, lockAllVars, varValues, items, typesOff);
			case VARBIT:
			case VARP:
				final Set<Integer> vars = new LinkedHashSet<>(lockedVars);
				vars.add(requirement.var);
				return new Account(changedName, levels, questsNotDone, vars, lockAllVars, varValues, items, typesOff);
			case PLANTED:
				// Only on hops, which the bubble never asks about
				return this;
			case ITEMS:
				final Map<Integer, Integer> fewer = new HashMap<>(items);
				for (int id : requirement.items)
				{
					fewer.remove(id);
				}
				return new Account(changedName, levels, questsNotDone, lockedVars, lockAllVars, varValues, fewer, typesOff);
			default:
				final Set<TransportType> types = EnumSet.noneOf(TransportType.class);
				types.addAll(typesOff);
				types.add(requirement.type);
				return new Account(changedName, levels, questsNotDone, lockedVars, lockAllVars, varValues, items, types);
		}
	}

	/** Stands in for RuneLite's client; anything the bubble doesn't need answers 0, false or nothing. */
	Client client(Thread clientThread)
	{
		final Item[] carried = new Item[items.size()];
		int i = 0;
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			carried[i++] = new Item(e.getKey(), e.getValue());
		}
		final ItemContainer inventory = (ItemContainer) Proxy.newProxyInstance(Account.class.getClassLoader(),
			new Class<?>[]{ItemContainer.class},
			(proxy, method, args) -> method.getName().equals("getItems") ? carried : nothing(method));
		final int total = levels.values().stream().mapToInt(Integer::intValue).sum();
		return (Client) Proxy.newProxyInstance(Account.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getGameState":
						return GameState.LOGGED_IN;
					case "getBoostedSkillLevel":
					case "getRealSkillLevel":
						return levels.getOrDefault((Skill) args[0], 1);
					case "getTotalLevel":
						return total;
					case "getVarpValue":
						return (int) args[0] == VarPlayer.QUEST_POINTS ? 300 : varValues.getOrDefault((int) args[0], 0);
					case "getVarbitValue":
						// Fairy rings need Fairytale II far enough along; turned off by type when peeled
						return (int) args[0] == VarbitID.FAIRY2_QUEENCURE_QUEST && !typesOff.contains(TransportType.FAIRY_RING)
							? 100 : varValues.getOrDefault((int) args[0], 0);
					case "getClientThread":
						return clientThread;
					case "isClientThread":
						return true;
					case "getWorldType":
						return EnumSet.noneOf(WorldType.class);
					case "getItemContainer":
						return args[0] instanceof Integer && (int) args[0] == InventoryID.INV ? inventory : null;
					default:
						return nothing(method);
				}
			});
	}

	/**
	 * Shortest Path's settings: its defaults, then teleports off (each answer is "from here, with no further teleport")
	 * unless asked, the house off, no bank detours, and the transport types this account has turned off.
	 */
	ShortestPathConfig config(boolean teleports)
	{
		final Map<String, Object> overrides = new HashMap<>();
		overrides.put("useTeleportationSpells", teleports);
		overrides.put("useTeleportationSpellsHome", false);
		overrides.put("useTeleportationMinigames", false);
		overrides.put("useTeleportationItems", teleports ? TeleportationItem.INVENTORY : TeleportationItem.NONE);
		overrides.put("useSeasonalTransports", false);
		overrides.put("usePoh", false);
		overrides.put("includeBankPath", false);
		overrides.put("calculationCutoff", 200);
		overrides.put("unreachableTargetDistance", 0);
		overrides.put("currencyThreshold", Integer.MAX_VALUE);
		for (TransportType type : typesOff)
		{
			if (type.getEnabledKey() != null)
			{
				overrides.put(type.getEnabledKey(), false);
			}
		}
		final InvocationHandler handler = (proxy, method, args) ->
		{
			if (overrides.containsKey(method.getName()) && (args == null || args.length == 0))
			{
				return overrides.get(method.getName());
			}
			if (method.isDefault())
			{
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			return nothing(method);
		};
		return (ShortestPathConfig) Proxy.newProxyInstance(Account.class.getClassLoader(),
			new Class<?>[]{ShortestPathConfig.class}, handler);
	}

	private static Object nothing(Method method)
	{
		final Class<?> type = method.getReturnType();
		if (type == boolean.class)
		{
			return false;
		}
		if (type == int.class || type == short.class || type == byte.class || type == char.class)
		{
			return type == char.class ? (Object) (char) 0 : (Object) 0;
		}
		if (type == long.class)
		{
			return 0L;
		}
		if (type == double.class || type == float.class)
		{
			return type == float.class ? (Object) 0f : (Object) 0d;
		}
		if (method.getName().equals("toString"))
		{
			return "pretend " + method.getDeclaringClass().getSimpleName();
		}
		if (List.class.isAssignableFrom(type))
		{
			return new ArrayList<>();
		}
		if (Set.class.isAssignableFrom(type))
		{
			return Collections.emptySet();
		}
		if (Map.class.isAssignableFrom(type))
		{
			return Collections.emptyMap();
		}
		return null;
	}
}
