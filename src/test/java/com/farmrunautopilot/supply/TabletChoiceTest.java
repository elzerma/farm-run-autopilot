package com.farmrunautopilot.supply;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelFamily;
import com.farmrunautopilot.settings.RunConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** A spell's tablet or its runes, as the Travel tab's Spells and Tablets and scrolls have it (sprint T7). */
public class TabletChoiceTest
{
	private static final AccessSnapshot CASTER = caster();
	private static final Holdings TABLETS = bank(ItemID.POH_TABLET_CAMELOTTELEPORT, ItemID.POH_TABLET_TELEPORTTOHOUSE);

	@Test
	public void aTabletHeldIsUsedFirst()
	{
		assertTrue(SupplyCalculator.usesTablet(Spell.CAMELOT_TELEPORT, Location.CATHERBY, config(), CASTER, TABLETS));
	}

	@Test
	public void tabletsOffCastsFromRunes()
	{
		final RunConfig config = config();
		config.setOn(TravelFamily.TABLETS, false);
		assertFalse(SupplyCalculator.usesTablet(Spell.CAMELOT_TELEPORT, Location.CATHERBY, config, CASTER, TABLETS));
	}

	@Test
	public void spellsOffTakesTheTabletEvenWithRunesPreferred()
	{
		final RunConfig config = config();
		config.setUseRunesNotTabs(true);
		assertFalse(SupplyCalculator.usesTablet(Spell.CAMELOT_TELEPORT, Location.CATHERBY, config, CASTER, TABLETS));
		config.setOn(TravelFamily.SPELLS, false);
		assertTrue(SupplyCalculator.usesTablet(Spell.CAMELOT_TELEPORT, Location.CATHERBY, config, CASTER, TABLETS));
	}

	@Test
	public void teleportToHouseGoesWithTheHouseNotSpellsOrTablets()
	{
		final RunConfig config = config();
		config.setUseRunesNotTabs(true);
		config.setOn(TravelFamily.SPELLS, false);
		config.setOn(TravelFamily.TABLETS, false);
		// Runes, as chosen: the house takes the spell or its tablet alike
		assertFalse(SupplyCalculator.usesTablet(Spell.TELEPORT_TO_HOUSE, Location.CATHERBY, config, CASTER, TABLETS));
		config.setUseRunesNotTabs(false);
		assertTrue(SupplyCalculator.usesTablet(Spell.TELEPORT_TO_HOUSE, Location.CATHERBY, config, CASTER, TABLETS));
	}

	@Test
	public void switchingOffTakesTheTabletOfASpellOnAnotherBook()
	{
		// On Lunar, Spellbook Swap reaches Camelot Teleport: cast from runes, as chosen
		final AccessSnapshot lunar = caster(Spellbook.LUNAR);
		final RunConfig config = config();
		config.setUseRunesNotTabs(true);
		config.setSwitchSpellbooks(true);
		assertFalse(SupplyCalculator.usesTablet(Spell.CAMELOT_TELEPORT, Location.CATHERBY, config, lunar, TABLETS));
		config.setSwitchSpellbooks(false);
		assertTrue(SupplyCalculator.usesTablet(Spell.CAMELOT_TELEPORT, Location.CATHERBY, config, lunar, TABLETS));
	}

	private static RunConfig config()
	{
		return new RunConfig().sanitise();
	}

	/** 99 everywhere, every quest done, on the standard spellbook. */
	private static AccessSnapshot caster()
	{
		return caster(Spellbook.STANDARD);
	}

	/** 99 everywhere, every quest done, on this spellbook with no house altar. */
	private static AccessSnapshot caster(Spellbook spellbook)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : AccessSnapshot.REAL_SKILLS)
		{
			levels.put(skill, 99);
		}
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(), EnumSet.noneOf(Unlock.class), null,
			spellbook, Collections.emptySet());
	}

	/** One of each of these in the bank. */
	private static Holdings bank(int... ids)
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		for (int id : ids)
		{
			bank.put(id, 1);
		}
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);
	}
}
