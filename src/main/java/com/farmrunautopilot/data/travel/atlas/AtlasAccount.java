package com.farmrunautopilot.data.travel.atlas;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.supply.Holdings;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

/**
 * Whether a player meets what an atlas way, teleport or hop needs: their levels and quests (AccessSnapshot), the items
 * they own anywhere (Holdings: the run's bank visit fetches them), and the varbits and varplayers the atlas names, read
 * on the client thread when this is made.
 */
public final class AtlasAccount
{
	private final AccessSnapshot access;
	private final Holdings holdings;
	private final Map<Integer, Integer> varbits;
	private final Map<Integer, Integer> varps;
	/** Transport types the player has turned off (Travel tab families, later). */
	private final Set<String> typesOff;

	public AtlasAccount(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbits,
		Map<Integer, Integer> varps, Set<String> typesOff)
	{
		this.access = access;
		this.holdings = holdings;
		this.varbits = varbits;
		this.varps = varps;
		this.typesOff = typesOff;
	}

	public AtlasAccount(AccessSnapshot access, Holdings holdings, Map<Integer, Integer> varbits, Map<Integer, Integer> varps)
	{
		this(access, holdings, varbits, varps, Collections.emptySet());
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
				return need.passes(varbits.getOrDefault(need.getVar(), 0));
			case VARP:
				return need.passes(varps.getOrDefault(need.getVar(), 0));
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
				return !typesOff.contains(need.getType()) && typeUsable(need.getType());
			case PLANTED:
				return planted(need.getType());
			default:
				return false;
		}
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
