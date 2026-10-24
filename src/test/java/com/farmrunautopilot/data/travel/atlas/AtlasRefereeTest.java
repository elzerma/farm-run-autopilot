package com.farmrunautopilot.data.travel.atlas;

import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.supply.Holdings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

/**
 * The referee (docs/plans/travel-rework.md, section 3 step 10): legs the plugin builds from the atlas against Shortest
 * Path's own fastest answers with teleports (tools/atlas/referee.tsv), for the pretend player like elzerma, between
 * every two stops the plugin runs. Shortest Path searches everything at once; the atlas allows one teleport and one
 * hop, and leaves the house out like the referee does. The two should mostly agree; the summary prints the rest.
 */
public class AtlasRefereeTest
{
	private static final TravelAtlas ATLAS = TravelAtlas.get();
	/** Within this, the two count as agreeing (start tiles and rounding differ). */
	private static final double AGREE_SECONDS = 3;

	@Test
	public void atlasLegsAgreeWithShortestPath() throws IOException
	{
		final Map<String, Integer> starts = new LinkedHashMap<>();
		final Map<String, List<Integer>> targets = new LinkedHashMap<>();
		for (String line : Files.readAllLines(Paths.get("tools/atlas/patches.tsv")))
		{
			final String[] r = line.split("\t", -1);
			if (line.startsWith("#") || r[0].equals("patch") || !r[6].equals("plugin"))
			{
				continue;
			}
			final int place = ATLAS.patchPlace(r[0]).getId();
			starts.putIfAbsent(r[2], place);
			final List<Integer> list = targets.computeIfAbsent(r[2], k -> new ArrayList<>());
			if (!list.contains(place))
			{
				list.add(place);
			}
		}

		final AtlasLegs legs = new AtlasLegs(ATLAS, likeElzerma(), null, null, true);
		int compared = 0;
		int agree = 0;
		int missing = 0;
		final List<String> slower = new ArrayList<>();
		final List<String> quicker = new ArrayList<>();
		for (String line : Files.readAllLines(Paths.get("tools/atlas/referee.tsv")))
		{
			final String[] r = line.split("\t", -1);
			if (line.startsWith("#") || !r[0].equals("like elzerma") || r[3].isEmpty())
			{
				continue;
			}
			final double referee = Double.parseDouble(r[3]);
			final AtlasLegs.Leg leg = legs.quickest(starts.get(r[1]), targets.get(r[2]));
			compared++;
			if (leg == null)
			{
				missing++;
				slower.add(String.format(Locale.ROOT, "%s > %s: atlas no way, Shortest Path %.0f s (%s)", r[1], r[2],
					referee, r[7]));
				continue;
			}
			final double gap = leg.getSeconds() - referee;
			if (Math.abs(gap) <= AGREE_SECONDS)
			{
				agree++;
			}
			else
			{
				(gap > 0 ? slower : quicker).add(String.format(Locale.ROOT, "%s > %s: atlas %.0f s (%s), Shortest Path %.0f s (%s)",
					r[1], r[2], leg.getSeconds(), leg.getHow(), referee, r[7]));
			}
		}
		System.out.printf(Locale.ROOT, "Atlas against the referee: %d legs, %d agree within %.0f s, %d slower, %d quicker, "
			+ "%d with no atlas way%n", compared, agree, AGREE_SECONDS, slower.size() - missing, quicker.size(), missing);
		slower.stream().limit(25).forEach(s -> System.out.println("  slower: " + s));
		quicker.stream().limit(25).forEach(s -> System.out.println("  quicker: " + s));

		// 2026-10-18: 577 of 600 agree; the rest are the Lovakengj minecart into the Farming Guild, free in Shortest
		// Path's answer because it passes both "A Kingdom Divided done" and "not done", which no real player is
		assertTrue("No legs compared", compared > 100);
		assertTrue(missing + " legs have no atlas way", missing == 0);
		assertTrue("Only " + agree + " of " + compared + " agree", agree >= compared * 0.9);
	}

	/** As Referee.likeElzerma in tools/atlas: every quest, 90 everywhere, the standard spellbook, its items and runes. */
	private static AtlasAccount likeElzerma()
	{
		final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			quests.put(quest, QuestState.FINISHED);
		}
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			levels.put(skill, 90);
		}
		final AccessSnapshot access = new AccessSnapshot(true, quests, levels, Collections.emptySet(),
			EnumSet.of(Unlock.FAIRY_RINGS, Unlock.SPIRIT_TREES), null);
		final Map<Integer, Integer> bank = new HashMap<>();
		for (int id : new int[]{ItemID.SLAYER_RING_8, ItemID.DRAMEN_STAFF, ItemID.RING_OF_WEALTH_5,
			ItemID.LUMBRIDGE_RING_MEDIUM, ItemID.ARDY_CAPE_MEDIUM, ItemID.ECTOPHIAL, ItemID.VEOS_KHAREDSTS_MEMOIRS,
			ItemID.MOURNING_TELEPORT_CRYSTAL_5, ItemID.HG_QUETZALWHISTLE_ENHANCED, ItemID.RING_OF_DUELING_8})
		{
			bank.put(id, 1);
		}
		for (int rune : new int[]{ItemID.AIRRUNE, ItemID.WATERRUNE, ItemID.EARTHRUNE, ItemID.FIRERUNE, ItemID.MINDRUNE,
			ItemID.CHAOSRUNE, ItemID.DEATHRUNE, ItemID.BLOODRUNE, ItemID.LAWRUNE, ItemID.NATURERUNE, ItemID.COSMICRUNE,
			ItemID.BODYRUNE, ItemID.SOULRUNE, ItemID.ASTRALRUNE})
		{
			bank.put(rune, 10_000);
		}
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		final Holdings holdings = new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false);

		// The referee passes every var check but the spellbook's and the house's: give each var a value its checks pass
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
		return new AtlasAccount(access, holdings, varbits, varps);
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
