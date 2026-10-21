package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasNeed;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.supply.Holdings;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/** Pretend players for planning on the travel atlas in tests (docs/plans/travel-rework.md, sprint T3). */
final class AtlasPlayers
{
	static final TravelAtlas ATLAS = TravelAtlas.get();

	private AtlasPlayers()
	{
	}

	/**
	 * The atlas for a player with these levels, quests and items, whose game values (quest progress, unlocked pads and
	 * routes) pass every check the atlas names, on the standard spellbook and without a house.
	 */
	static RouteAtlas with(AccessSnapshot access, Holdings holdings)
	{
		return with(access, holdings, Map.of());
	}

	/** As {@link #with(AccessSnapshot, Holdings)}, with some game values set (e.g. a varbit at 0: not unlocked). */
	static RouteAtlas with(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbitsSet)
	{
		return with(access, holdings, varbitsSet, AtlasWays.DEFAULT, Map.of(), 0);
	}

	/** As {@link #with(AccessSnapshot, Holdings)}, with what the Travel tab lets the route use. */
	static RouteAtlas with(AccessSnapshot access, Holdings holdings, AtlasWays ways)
	{
		return with(access, holdings, Map.of(), ways, Map.of(), 0);
	}

	/**
	 * As {@link #with(AccessSnapshot, Holdings)}, with game values set, the Travel tab's choices, varplayers set (a
	 * cooldown teleport's last use) and the time in minutes since 1970 (0: every cooldown over).
	 */
	static RouteAtlas with(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbitsSet, AtlasWays ways,
		Map<Integer, Integer> varpsSet, long minutes)
	{
		final Map<Integer, Integer> varbits = new HashMap<>();
		final Map<Integer, Integer> varps = new HashMap<>();
		for (AtlasNeed need : ATLAS.getNeeds())
		{
			if (need.getKind() == AtlasNeed.Kind.VARBIT)
			{
				pass(varbits, need);
			}
			else if (need.getKind() == AtlasNeed.Kind.VARP)
			{
				pass(varps, need);
			}
		}
		varbits.put(VarbitID.SPELLBOOK, 0);
		varbits.put(VarbitID.POH_HOUSE_LOCATION, 0);
		varbits.putAll(varbitsSet);
		varps.putAll(varpsSet);
		return new RouteAtlas(ATLAS, new AtlasAccount(access, holdings, varbits, varps, ways, minutes));
	}

	/** A value for this var that passes this check and the ones before it where it can (bits add up). */
	private static void pass(Map<Integer, Integer> vars, AtlasNeed need)
	{
		switch (need.getCheck())
		{
			case "&":
				vars.merge(need.getVar(), need.getValue(), (a, b) -> a | b);
				break;
			case ">":
				vars.merge(need.getVar(), need.getValue() + 1, Math::max);
				break;
			case "<":
				vars.putIfAbsent(need.getVar(), need.getValue() - 1);
				break;
			default:
				vars.putIfAbsent(need.getVar(), need.getValue());
				break;
		}
	}
}
