package com.farmrunautopilot.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.poh.PohAltar;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

/** Sean asked not to be sent across spellbooks unless a house altar makes it practical. */
public class SpellbookRuleTest
{
	private static AccessSnapshot onBook(Spellbook book, Set<Spellbook> altar)
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		quests.put(Quest.PRIEST_IN_PERIL, QuestState.FINISHED);
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.MAGIC, 99);
		levels.put(Skill.FARMING, 99);
		return new AccessSnapshot(true, quests, levels, Collections.emptySet(), Collections.emptySet(), null, book,
			altar);
	}

	private static RouteStop portPhasmatys(AccessSnapshot access)
	{
		final RunConfig config = new RunConfig().sanitise();
		config.setStartLocation(Location.PORT_PHASMATYS);
		return RoutePlanner.plan(Collections.singletonList(Patch.PORT_PHASMATYS_HERB), config, access, Holdings.EMPTY,
			new PohSetup()).getStops().get(0);
	}

	@Test
	public void spellsFromAnotherBookNeedAnAltar()
	{
		final AccessSnapshot standard = onBook(Spellbook.STANDARD, Collections.emptySet());
		assertTrue(standard.canCast(Spell.CAMELOT_TELEPORT));
		assertFalse(standard.canCast(Spell.FENKENSTRAINS_CASTLE_TELEPORT));

		final AccessSnapshot withAltar = onBook(Spellbook.STANDARD, PohAltar.DARK.getSpellbooks());
		assertTrue(withAltar.canCast(Spell.FENKENSTRAINS_CASTLE_TELEPORT));
		assertFalse(withAltar.isOnSpellbook(Spell.FENKENSTRAINS_CASTLE_TELEPORT));
	}

	@Test
	public void routeOnlyCastsFromTheCurrentBook()
	{
		// On the standard book with no altar: it can't be cast, so it's only planned as a tablet to bring.
		final RouteStop standard = portPhasmatys(onBook(Spellbook.STANDARD, Collections.emptySet()));
		assertTrue(standard.getMethod() != TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT || standard.isNeedsSupplies());
		// On Arceuus it's cast from runes.
		final RouteStop arceuus = portPhasmatys(onBook(Spellbook.ARCEUUS, Collections.emptySet()));
		assertEquals(TravelMethod.FENKENSTRAINS_CASTLE_TELEPORT, arceuus.getMethod());
		assertFalse(arceuus.isNeedsSupplies());
	}
}
