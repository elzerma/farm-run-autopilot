package com.farmrunautopilot.route;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.atlas.AtlasAccount;
import com.farmrunautopilot.data.travel.atlas.AtlasNeed;
import com.farmrunautopilot.data.travel.atlas.AtlasWays;
import com.farmrunautopilot.data.travel.atlas.TravelAtlas;
import com.farmrunautopilot.supply.Holdings;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.VarbitID;

/**
 * Pretend players for planning on the travel atlas in tests (docs/plans/travel-rework.md, sprint T3). Their game values
 * match their account where the plugin reads them too: the achievement diaries and the unlocks read from varbits are
 * as the account has them (every one done when it isn't known), and so is the house (where it is, none without one,
 * and how Teleport to House lands). Every other check the atlas names (quest progress, landing sites, balloon routes)
 * passes. On the standard spellbook.
 */
public final class AtlasPlayers
{
	public static final TravelAtlas ATLAS = TravelAtlas.get();

	private AtlasPlayers()
	{
	}

	/** The atlas for a player with these levels, quests, diaries, unlocks and items. */
	public static RouteAtlas with(AccessSnapshot access, Holdings holdings)
	{
		return with(access, holdings, Map.of());
	}

	/** As {@link #with(AccessSnapshot, Holdings)}, with some game values set (e.g. a varbit at 0: not unlocked). */
	public static RouteAtlas with(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbitsSet)
	{
		return with(access, holdings, varbitsSet, AtlasWays.DEFAULT, Map.of(), 0);
	}

	/** As {@link #with(AccessSnapshot, Holdings)}, with what the Travel tab lets the route use. */
	public static RouteAtlas with(AccessSnapshot access, Holdings holdings, AtlasWays ways)
	{
		return with(access, holdings, Map.of(), ways, Map.of(), 0);
	}

	/**
	 * As {@link #with(AccessSnapshot, Holdings)}, with game values set, the Travel tab's choices, varplayers set (a
	 * cooldown teleport's last use) and the time in minutes since 1970 (0: every cooldown over).
	 */
	public static RouteAtlas with(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbitsSet,
		AtlasWays ways, Map<Integer, Integer> varpsSet, long minutes)
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
		if (access.isKnown())
		{
			for (AchievementDiary diary : AchievementDiary.values())
			{
				for (AchievementDiary.Tier tier : AchievementDiary.Tier.values())
				{
					// Karamja's easy, medium and hard read 2 once done (AchievementDiary.isComplete)
					final int done = diary == AchievementDiary.KARAMJA && tier != AchievementDiary.Tier.ELITE ? 2 : 1;
					varbits.put(diary.getCompleteVarbit(tier), access.isMet(Requirement.diary(diary, tier)) ? done : 0);
				}
			}
			for (Unlock unlock : Unlock.values())
			{
				if (unlock.hasVarbit() && !access.isMet(Requirement.unlock(unlock)))
				{
					varbits.put(unlock.getVarbit(), 0);
				}
			}
		}
		varbits.put(VarbitID.SPELLBOOK, 0);
		varbits.put(VarbitID.POH_HOUSE_LOCATION, access.getHouseLocation());
		varbits.put(VarbitID.POH_TELE_TOGGLE, access.isHouseTeleportOutside() ? 0 : 1);
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
