package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.supply.Holdings;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarbitID;

/**
 * Whether a player meets what an atlas way, teleport or hop needs: their levels and quests (AccessSnapshot), the items
 * they own anywhere (Holdings: the run's bank visit fetches them), and the varbits and varplayers the atlas names, read
 * on the client thread when this is made; and which ways they let the route use (the Travel tab, AtlasWays).
 */
public final class AtlasAccount
{
	/** The spellbooks by the game's SPELLBOOK varbit value. */
	private static final String[] SPELLBOOKS = {"standard", "Ancient", "Lunar", "Arceuus"};

	private final AccessSnapshot access;
	private final Holdings holdings;
	private final Map<Integer, Integer> varbits;
	private final Map<Integer, Integer> varps;
	/** The families and single ways the player turned off on the Travel tab, and the Wilderness. */
	private final AtlasWays ways;
	/** Minutes since 1970 (UTC) when this was made, for home and minigame teleports' cooldowns; 0 if unknown. */
	private final long minutes;

	/**
	 * @param ways    what the player lets the route use (the Travel tab)
	 * @param minutes minutes since 1970 (UTC) now, for cooldowns; 0 counts every cooldown as over
	 */
	public AtlasAccount(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbits,
		Map<Integer, Integer> varps, AtlasWays ways, long minutes)
	{
		this.access = access;
		this.holdings = holdings;
		this.varbits = varbits;
		this.varps = varps;
		this.ways = ways;
		this.minutes = minutes;
	}

	/** A new player's choices: every family but home and minigame teleports, the Wilderness avoided. */
	public AtlasAccount(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbits, Map<Integer, Integer> varps)
	{
		this(access, holdings, varbits, varps, AtlasWays.DEFAULT, 0);
	}

	/** The same player with other choices, e.g. everything on to see what a way would need. */
	public AtlasAccount withWays(AtlasWays other)
	{
		return new AtlasAccount(access, holdings, varbits, varps, other, minutes);
	}

	public AtlasWays getWays()
	{
		return ways;
	}

	/** Every one of these needs (ids into the atlas's requirements) is met. */
	public boolean meetsAll(TravelAtlas atlas, int[] needs)
	{
		for (int id : needs)
		{
			if (!meets(atlas.need(id)))
			{
				return false;
			}
		}
		return true;
	}

	public boolean meets(AtlasNeed need)
	{
		switch (need.getKind())
		{
			case SKILL:
				return !access.isKnown() || access.level(need.getSkill()) >= need.getLevel();
			case QUEST:
				return !access.isKnown() || access.getQuests().get(need.getQuest()) == QuestState.FINISHED;
			case VARBIT:
				return need.isCooldown() ? cooldownOver(need, varbits) : need.passes(varbits.getOrDefault(need.getVar(), 0));
			case VARP:
				return need.isCooldown() ? cooldownOver(need, varps) : need.passes(varps.getOrDefault(need.getVar(), 0));
			case ITEMS:
				for (AtlasNeed.Branch branch : need.getBranches())
				{
					if (branch.getUnlock() != null ? unlocked(branch.getUnlock())
						: holdings.countAny(branch.getItems()) >= branch.getQuantity() || holdings.countAny(branch.getCovers()) > 0)
					{
						return true;
					}
				}
				return false;
			case TYPE:
				return !ways.typeOff(need.getType()) && typeUsable(need.getType());
			case PLANTED:
				return planted(need.getType());
			default:
				return false;
		}
	}

	/**
	 * What's missing for a need the player doesn't meet, in the Travel tab's words ("45 Agility", "Cabin Fever", "a
	 * dramen or lunar staff"), or null if it's met. Items are named by the caller, who can look their names up; a
	 * cooldown isn't something missing (see {@link AtlasNeed#isCooldown()}).
	 */
	public String missing(AtlasNeed need)
	{
		if (meets(need))
		{
			return null;
		}
		switch (need.getKind())
		{
			case SKILL:
			case QUEST:
				return need.getName();
			case TYPE:
				return missingType(need.getType());
			case PLANTED:
				return "a spirit tree planted there";
			case VARBIT:
				if (need.getVar() == VarbitID.SPELLBOOK && "=".equals(need.getCheck()) && need.getValue() >= 0
					&& need.getValue() < SPELLBOOKS.length)
				{
					return "the " + SPELLBOOKS[need.getValue()] + " spellbook";
				}
				return "an unlock";
			default:
				// A var Shortest Path checks (a quest's progress, a diary) or something this RuneLite doesn't know
				return "an unlock";
		}
	}

	/** What a whole transport network needs that the player lacks, as {@link #typeUsable} checks it. */
	private String missingType(String type)
	{
		switch (type)
		{
			case "FAIRY_RING":
				return !access.isMet(Requirement.unlock(Unlock.FAIRY_RINGS)) ? "Fairytale II started"
					: "a dramen or lunar staff";
			case "SPIRIT_TREE":
				return "Tree Gnome Village";
			case "QUETZAL":
				return "Twilight's Promise";
			case "GNOME_GLIDER":
				return "The Grand Tree";
			case "MAGIC_MUSHTREE":
				return "Bone Voyage";
			default:
				return "an unlock";
		}
	}

	/** A home or minigame teleport's cooldown is over: the var holds the minute it was last used. */
	private boolean cooldownOver(AtlasNeed need, Map<Integer, Integer> vars)
	{
		return minutes <= 0 || minutes - vars.getOrDefault(need.getVar(), 0) >= need.getValue();
	}

	/** The player planted the spirit tree in this patch; the trees the plugin can't tell yet count as not planted. */
	private boolean planted(String patch)
	{
		switch (patch)
		{
			case "PORT_SARIM_SPIRIT_TREE":
				return access.isMet(Requirement.unlock(Unlock.SPIRIT_TREE_PORT_SARIM));
			case "BRIMHAVEN_SPIRIT_TREE":
				return access.isMet(Requirement.unlock(Unlock.SPIRIT_TREE_BRIMHAVEN));
			case "FARMING_GUILD_SPIRIT_TREE":
				return access.isMet(Requirement.unlock(Unlock.SPIRIT_TREE_FARMING_GUILD));
			default:
				return false;
		}
	}

	/** What a whole transport network needs beyond each hop's own requirements. */
	private boolean typeUsable(String type)
	{
		switch (type)
		{
			case "FAIRY_RING":
				return access.isMet(Requirement.unlock(Unlock.FAIRY_RINGS))
					&& (access.isMet(Requirement.diary(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.ELITE))
					|| holdings.countAny(SupplyItems.FAIRY_RING_STAFFS) > 0);
			case "SPIRIT_TREE":
				return access.isMet(Requirement.unlock(Unlock.SPIRIT_TREES));
			case "QUETZAL":
				return finished(Quest.TWILIGHTS_PROMISE);
			case "GNOME_GLIDER":
				return finished(Quest.THE_GRAND_TREE);
			case "MAGIC_MUSHTREE":
				return finished(Quest.BONE_VOYAGE);
			default:
				return true;
		}
	}

	/** Shortest Path's declared unlocks the plugin can tell; the others (a stored canoe axe...) count as not unlocked. */
	private boolean unlocked(String unlock)
	{
		switch (unlock)
		{
			case "KARAMJA_ELITE":
				return access.isMet(Requirement.diary(AchievementDiary.KARAMJA, AchievementDiary.Tier.ELITE));
			default:
				return false;
		}
	}

	private boolean finished(Quest quest)
	{
		return !access.isKnown() || access.getQuests().get(quest) == QuestState.FINISHED;
	}
}
