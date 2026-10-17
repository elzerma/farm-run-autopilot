package com.farmrunautopilot.supply;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchState;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RoutePlanner;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Outfit;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SupplyCalculatorTest
{
	private static final long NOW = 1_700_000_000L;
	private static final Function<Patch, PatchPrediction> NEVER_SEEN = p -> null;

	private static Holdings holdings(Map<Integer, Integer> bank, Set<Rune> infinite)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.BANK, bank);
		return new Holdings(items, Collections.emptyMap(), infinite, true, false);
	}

	private static RunConfig onlyType(PatchType type)
	{
		final RunConfig config = new RunConfig();
		config.setEnabledTypes(EnumSet.of(type));
		return config.sanitise();
	}

	private static SupplyPlan plan(RunConfig config, AccessSnapshot access, Holdings holdings,
		Function<Patch, PatchPrediction> predictions, boolean fullRun)
	{
		final RunSelection selection = RunSelector.select(config, access, predictions, NOW, fullRun,
			Collections.emptyMap());
		final Route route = RoutePlanner.plan(selection.getPatches(), config, access, holdings, new PohSetup());
		return SupplyCalculator.calculate(config, access, holdings, selection, route, predictions, id -> "item " + id,
			id -> 0);
	}

	private static SupplyLine find(SupplyPlan plan, String name)
	{
		for (SupplyLine line : plan.getLines())
		{
			if (line.getName().equals(name))
			{
				return line;
			}
		}
		return null;
	}

	private static PatchPrediction prediction(Patch patch, PatchState state)
	{
		return new PatchPrediction(patch, null, state, 0, 1, 0, NOW, 0, PatchPrediction.Source.THIS_PLUGIN);
	}

	@Test
	public void noPlantCureWhenEveryPatchIsDiseaseFree()
	{
		final RunConfig config = onlyType(PatchType.HERB);
		config.setPlantCureDoses(1);
		for (Patch patch : Patch.values())
		{
			if (patch != Patch.TROLL_STRONGHOLD_HERB)
			{
				config.getDisabledPatches().add(patch);
			}
		}
		final SupplyPlan safe = plan(config, AccessSnapshot.UNKNOWN, holdings(new HashMap<>(), Collections.emptySet()),
			NEVER_SEEN, true);
		assertEquals(1, safe.patchTotal());
		assertNull(find(safe, "Plant cure"));

		config.getDisabledPatches().remove(Patch.ARDOUGNE_HERB);
		final SupplyPlan atRisk = plan(config, AccessSnapshot.UNKNOWN, holdings(new HashMap<>(), Collections.emptySet()),
			NEVER_SEEN, true);
		assertNotNull(find(atRisk, "Plant cure"));
	}

	@Test
	public void barbarianPlantingNeedsNoDibber()
	{
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.FARMING, 99);
		final AccessSnapshot access = new AccessSnapshot(true, Collections.emptyMap(), levels, Collections.emptySet(),
			EnumSet.of(Unlock.BARBARIAN_PLANTING), null);
		final SupplyPlan plan = plan(onlyType(PatchType.HERB), access, Holdings.EMPTY, NEVER_SEEN, true);
		assertNull(find(plan, "Seed dibber"));
	}

	@Test
	public void herbRun()
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.TORSTOL_SEED, 4);
		bank.put(ItemID.BUCKET_ULTRACOMPOST, 12);
		bank.put(ItemID.SPADE, 1);
		final SupplyPlan plan = plan(onlyType(PatchType.HERB), AccessSnapshot.UNKNOWN,
			holdings(bank, Collections.emptySet()), NEVER_SEEN, false);

		assertEquals(10, plan.patchTotal());
		// Unknown Farming level: highest herb.
		final SupplyLine seeds = find(plan, "item " + ItemID.TORSTOL_SEED);
		assertNotNull(seeds);
		assertEquals(10, seeds.getNeed());
		assertEquals(4, seeds.getHave());
		assertFalse(seeds.isMet());
		assertEquals(1, seeds.getSlots());

		final SupplyLine compost = find(plan, Compost.ULTRACOMPOST.getDisplayName());
		assertEquals(10, compost.getNeed());
		assertTrue(compost.isMet());

		assertTrue(find(plan, "Spade").isMet());
		assertNotNull(find(plan, "Seed dibber"));
		assertNotNull("rake needed without Auto-weed", find(plan, "Rake"));
		// Herbs have no gardener payments.
		assertNull(find(plan, "Coins"));
	}

	@Test
	public void growingPatchesAreNotDue()
	{
		final Function<Patch, PatchPrediction> predictions = p ->
			p == Patch.CATHERBY_HERB || p == Patch.ARDOUGNE_HERB ? prediction(p, PatchState.GROWING)
				: prediction(p, PatchState.HARVESTABLE);
		// At the default 100% threshold the herb run waits until every patch is due.
		assertEquals(0, plan(onlyType(PatchType.HERB), AccessSnapshot.UNKNOWN, Holdings.EMPTY, predictions, false)
			.patchTotal());

		final RunConfig half = onlyType(PatchType.HERB);
		half.setDueThresholdPercent(50);
		final SupplyPlan due = plan(half, AccessSnapshot.UNKNOWN, Holdings.EMPTY, predictions, false);
		assertEquals(8, due.patchTotal());
		assertEquals(2, due.getNotDue().size());

		final SupplyPlan full = plan(onlyType(PatchType.HERB), AccessSnapshot.UNKNOWN, Holdings.EMPTY, predictions, true);
		assertEquals(10, full.patchTotal());
		assertTrue(full.getNotDue().isEmpty());
	}

	@Test
	public void treeRunPaymentsAndClearing()
	{
		final RunConfig config = onlyType(PatchType.TREE);
		final SupplyPlan plan = plan(config, AccessSnapshot.UNKNOWN, Holdings.EMPTY, NEVER_SEEN, true);

		assertEquals(7, plan.patchTotal());
		// Magic trees: 25 coconuts each, noted, one slot.
		final SupplyLine coconuts = find(plan, "item " + ItemID.COCONUT + " (noted)");
		assertEquals(7 * Crop.MAGIC.getPaymentQuantity(), coconuts.getNeed());
		assertEquals(1, coconuts.getSlots());
		// Never-seen trees are assumed grown, so each is cleared for 200 coins.
		assertEquals(7 * Crop.CLEAR_PATCH_COINS, find(plan, "Coins").getNeed());
		assertNull("no axe when the gardener clears", find(plan, "Axe"));
		// Saplings don't stack.
		assertEquals(7, find(plan, "item " + ItemID.PLANTPOT_MAGIC_TREE_SAPLING).getSlots());
	}

	@Test
	public void choppingNeedsAnAxeAndNoClearingCoins()
	{
		final RunConfig config = onlyType(PatchType.TREE);
		config.getPayToClear().clear();
		final SupplyPlan plan = plan(config, AccessSnapshot.UNKNOWN, Holdings.EMPTY, NEVER_SEEN, true);
		assertNotNull(find(plan, "Axe"));
		assertNull(find(plan, "Coins"));
	}

	@Test
	public void eliteFaladorDiaryMakesFaladorProtectionFree()
	{
		final Set<String> diaries = new HashSet<>();
		diaries.add(AccessSnapshot.diaryKey(AchievementDiary.FALADOR, AchievementDiary.Tier.ELITE));
		final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.FARMING, 99);
		final AccessSnapshot access = new AccessSnapshot(true, Collections.emptyMap(), levels, diaries,
			Collections.emptySet(), null);

		final RunConfig config = onlyType(PatchType.TREE);
		// Only patches with no requirements are unlocked here: Farming Guild needs 65 (met), Nemus needs a quest.
		final SupplyPlan plan = plan(config, access, Holdings.EMPTY, NEVER_SEEN, true);
		final int trees = plan.patchTotal();
		final SupplyLine coconuts = find(plan, "item " + ItemID.COCONUT + " (noted)");
		assertEquals((trees - 1) * Crop.MAGIC.getPaymentQuantity(), coconuts.getNeed());
	}

	@Test
	public void runesUseStavesThenCombinationRunes()
	{
		final Map<Rune, Integer> need = new EnumMap<>(Rune.class);
		need.put(Rune.AIR, 6);
		need.put(Rune.EARTH, 1);
		need.put(Rune.FIRE, 2);
		final Map<Integer, Integer> bank = new HashMap<>();
		bank.put(Rune.AIR.getItemId(), 2);
		bank.put(ItemID.DUSTRUNE, 3);

		final Holdings held = holdings(bank, EnumSet.of(Rune.FIRE));
		final SupplyCalculator.RuneResult result = SupplyCalculator.runes(need, held, held.carriedOnly(),
			id -> "rune " + id);
		final Map<String, SupplyLine> byName = new HashMap<>();
		for (SupplyLine line : result.lines)
		{
			byName.put(line.getName(), line);
		}

		// 2 air + 3 dust = 5 of 6 air; dust also covers the 1 earth; the staff covers fire.
		assertEquals(5, byName.get("rune " + Rune.AIR.getItemId()).getHave());
		assertEquals(1, byName.get("rune " + Rune.EARTH.getItemId()).getHave());
		// Fire, which the staff covers, gets no line of its own: only the summary says so
		assertNull(byName.get("rune " + Rune.FIRE.getItemId()));
		assertTrue(result.summary.contains("staff covers Fire"));

		// Everything is in the bank, so nothing is carried: air is short, earth is in storage.
		assertEquals(SupplyLine.Status.MISSING, byName.get("rune " + Rune.AIR.getItemId()).getStatus());
		assertEquals(SupplyLine.Status.IN_STORAGE, byName.get("rune " + Rune.EARTH.getItemId()).getStatus());
	}

	@Test
	public	void dueAndClearingRules()
	{
		assertTrue(RunSelector.isDue(null));
		assertFalse(RunSelector.isDue(prediction(Patch.CATHERBY_HERB, PatchState.GROWING)));
		assertTrue(RunSelector.isDue(prediction(Patch.CATHERBY_HERB, PatchState.DEAD)));
		assertTrue(SupplyCalculator.needsClearing(PatchState.CHECK_HEALTH));
		assertTrue(SupplyCalculator.needsClearing(PatchState.STUMP));
		assertFalse(SupplyCalculator.needsClearing(PatchState.EMPTY));
		assertFalse(SupplyCalculator.needsClearing(PatchState.DEAD));
	}

	@Test
	public void diseaseFreeHerbsAreRankedByPriceAndLevel()
	{
		final RunConfig config = new RunConfig();
		config.getDiseaseFreeHerbs().add(Crop.RANARR);
		config.getDiseaseFreeHerbs().add(Crop.SNAPDRAGON);
		config.getDiseaseFreeHerbs().add(Crop.TORSTOL);
		final Map<Integer, Integer> prices = new HashMap<>();
		prices.put(ItemID.RANARR_SEED, 40_000);
		prices.put(ItemID.SNAPDRAGON_SEED, 60_000);
		prices.put(ItemID.TORSTOL_SEED, 50_000);

		// Switched off: the list is ignored.
		assertTrue(SupplyCalculator.diseaseFreeHerbs(config, 99, id -> prices.getOrDefault(id, 0)).isEmpty());

		config.setPrioritiseDiseaseFreeHerbs(true);
		// Farming 70: torstol (85) can't be planted yet.
		assertEquals(java.util.Arrays.asList(Crop.SNAPDRAGON, Crop.RANARR),
			SupplyCalculator.diseaseFreeHerbs(config, 70, id -> prices.getOrDefault(id, 0)));
	}

	@Test
	public void cropRolesShowChoiceAndDiseaseFree()
	{
		final RunConfig config = new RunConfig();
		config.getCrops().put(PatchType.HERB, Crop.RANARR);
		config.setUseBackupCrops(true);
		config.getBackupCrops().put(PatchType.HERB, java.util.Arrays.asList(Crop.AVANTOE));
		final java.util.List<Crop> reserved = java.util.Arrays.asList(Crop.RANARR, Crop.KWUARM);

		assertEquals("1st choice, disease-free only", SupplyCalculator.cropRole(Crop.RANARR, config, 99, reserved));
		assertEquals("2nd choice", SupplyCalculator.cropRole(Crop.AVANTOE, config, 99, reserved));
		assertEquals("disease-free only", SupplyCalculator.cropRole(Crop.KWUARM, config, 99, reserved));

		// Single choice and nothing reserved: no label needed.
		config.setUseBackupCrops(false);
		assertEquals("", SupplyCalculator.cropRole(Crop.RANARR, config, 99, java.util.Collections.emptyList()));
	}

	@Test
	public void outfitCountsPiecesOfAnyColour()
	{
		final Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.GRACEFUL_HOOD, 1);
		bank.put(ItemID.ZEAH_GRACEFUL_TOP_ARCEUUS, 1);
		bank.put(ItemID.GRACEFUL_BOOTS_WYRM, 1);
		final SupplyLine line = SupplyCalculator.outfitLine(com.farmrunautopilot.settings.Outfit.GRACEFUL,
			holdings(bank, Collections.emptySet()), Holdings.EMPTY);
		assertEquals(6, line.getNeed());
		assertEquals(3, line.getHave());
		assertEquals(0, line.getCarried());
	}

	@Test
	public void leprechaunCountsCombineAsTheGameDoes()
	{
		// The game's farming_tools_getstored script: compost base + 256 * extra, tools base + 2 * extra
		assertEquals(5, LeprechaunItem.COMPOST.total(5, 0));
		assertEquals(256 + 5, LeprechaunItem.COMPOST.total(5, 1));
		assertEquals(1 + 2 * 3, LeprechaunItem.RAKE.total(1, 3));
		assertEquals(40, LeprechaunItem.ULTRACOMPOST.total(40, 0));
	}

	@Test
	public void anEmptyStoredBucketHoldsNoCompost()
	{
		assertEquals(null, Compost.fromBucketType(Compost.EMPTY_BUCKET_TYPE));
		assertEquals(null, Compost.fromBucketType(0));
	}

	private static Map<Compost, Integer> ultracompostFor(int patches)
	{
		final Map<Compost, Integer> compost = new EnumMap<>(Compost.class);
		compost.put(Compost.ULTRACOMPOST, patches);
		return compost;
	}

	private static Holdings in(Holdings.Source source, int itemId, int uses, Compost holds)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(source, Collections.singletonMap(itemId, 1));
		return new Holdings(items, Collections.emptyMap(), Collections.emptySet(), true, false, uses, holds);
	}

	private static java.util.List<SupplyLine> compost(Map<Compost, Integer> compost, Holdings holdings,
		java.util.List<String> warnings)
	{
		return SupplyCalculator.compostLines(compost, holdings, holdings.carriedOnly(), warnings);
	}

	@Test
	public void filledBottomlessBucketReplacesCompostBuckets()
	{
		// GitHub #3: one line for the bucket, coloured by where it is, instead of a vague "ok"
		final java.util.List<String> warnings = new java.util.ArrayList<>();
		final java.util.List<SupplyLine> inBank = compost(ultracompostFor(6),
			in(Holdings.Source.BANK, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 120, Compost.ULTRACOMPOST), warnings);
		assertEquals(1, inBank.size());
		final SupplyLine line = inBank.get(0);
		assertEquals("Bottomless compost bucket (120 uses)", line.getName());
		assertEquals(1, line.getNeed());
		assertEquals(SupplyLine.Status.IN_STORAGE, line.getStatus());
		assertEquals(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, line.getItemIds()[0]);
		assertTrue(line.getNote(), line.getNote().contains("this run needs 6 uses"));
		assertTrue(line.getChangeIn().contains("compost type"));
		assertTrue(warnings.toString(), warnings.isEmpty());

		final java.util.List<SupplyLine> carried = compost(ultracompostFor(6),
			in(Holdings.Source.INVENTORY, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 120, Compost.ULTRACOMPOST), warnings);
		assertEquals(SupplyLine.Status.CARRIED, carried.get(0).getStatus());
	}

	@Test
	public void bottomlessBucketUsesUnknownOrShort()
	{
		// Not checked yet: ask for a Check
		final java.util.List<String> unknown = new java.util.ArrayList<>();
		final java.util.List<SupplyLine> lines = compost(ultracompostFor(6),
			in(Holdings.Source.INVENTORY, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, -1, null), unknown);
		assertEquals("Bottomless compost bucket", lines.get(0).getName());
		assertEquals(1, unknown.size());
		assertTrue(unknown.get(0), unknown.get(0).contains("choose Check"));

		// Too few uses for the run
		final java.util.List<String> shortWarnings = new java.util.ArrayList<>();
		compost(ultracompostFor(6),
			in(Holdings.Source.INVENTORY, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 4, Compost.ULTRACOMPOST), shortWarnings);
		assertEquals(Collections.singletonList("Your bottomless compost bucket has 4 uses left; this run needs 6"),
			shortWarnings);
	}

	@Test
	public void bottomlessBucketOnlyReplacesItsOwnCompost()
	{
		// Holds supercompost; the run wants ultracompost for herbs and supercompost for trees
		final Map<Compost, Integer> both = ultracompostFor(6);
		both.put(Compost.SUPERCOMPOST, 3);
		final java.util.List<SupplyLine> lines = compost(both,
			in(Holdings.Source.INVENTORY, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 50, Compost.SUPERCOMPOST),
			new java.util.ArrayList<>());
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).getNote(), lines.get(0).getNote().contains("supercompost; this run needs 3 uses"));
		assertEquals("Ultracompost", lines.get(1).getName());
		assertEquals(6, lines.get(1).getNeed());

		// Holds a type the run doesn't use at all: buckets as normal, plus a warning
		final java.util.List<String> warnings = new java.util.ArrayList<>();
		final java.util.List<SupplyLine> other = compost(ultracompostFor(6),
			in(Holdings.Source.INVENTORY, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 50, Compost.COMPOST), warnings);
		assertEquals("Ultracompost", other.get(0).getName());
		assertTrue(warnings.toString(), warnings.get(0).contains("holds compost"));
	}

	@Test
	public void emptyBottomlessBucketKeepsTheBucketsWithAHint()
	{
		final java.util.List<SupplyLine> lines = compost(ultracompostFor(6),
			in(Holdings.Source.BANK, ItemID.BOTTOMLESS_COMPOST_BUCKET, -1, null), new java.util.ArrayList<>());
		assertEquals(1, lines.size());
		assertEquals("Ultracompost", lines.get(0).getName());
		assertEquals(6, lines.get(0).getNeed());
		assertTrue(lines.get(0).getNote().startsWith("Fill your bottomless compost bucket"));

		// No bucket at all: plain buckets, no note
		assertNull(compost(ultracompostFor(6), Holdings.EMPTY, new java.util.ArrayList<>()).get(0).getNote());
	}

	private static Holdings wornAndBank(Map<Integer, Integer> worn, Map<Integer, Integer> bank)
	{
		final Map<Holdings.Source, Map<Integer, Integer>> items = new EnumMap<>(Holdings.Source.class);
		items.put(Holdings.Source.WORN, worn);
		items.put(Holdings.Source.BANK, bank);
		final Set<Rune> infinite = EnumSet.noneOf(Rune.class);
		worn.keySet().forEach(id -> infinite.addAll(SupplyItems.INFINITE_RUNE_ITEMS.getOrDefault(id,
			Collections.emptySet())));
		return new Holdings(items, Collections.emptyMap(), infinite, true, false);
	}

	@Test
	public void aStaffCoveringTheRunesBeatsMagicSecateurs()
	{
		// Issue #7: wearing the secateurs knocked the lava battlestaff off, so the plan asked for earth runes
		final Map<Integer, Integer> worn = new HashMap<>();
		worn.put(ItemID.LAVA_BATTLESTAFF, 1);
		final Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.FAIRY_ENCHANTED_SECATEURS, 1);
		final RunConfig config = onlyType(PatchType.HERB);
		config.setUseCurePlant(true);
		final SupplyPlan plan = plan(config, AccessSnapshot.UNKNOWN, wornAndBank(worn, bank), NEVER_SEEN, true);

		final SupplyLine staff = find(plan, "item " + ItemID.LAVA_BATTLESTAFF);
		assertNotNull(staff);
		assertTrue(staff.isWear());
		assertEquals(0, staff.getSlots());
		// The staff covers the earth runes, so there's no earth rune line to bring
		assertNull(find(plan, "item " + Rune.EARTH.getItemId()));
		final SupplyLine secateurs = find(plan, "Magic secateurs");
		assertFalse(secateurs.isWear());
		assertEquals(1, secateurs.getSlots());
		assertTrue(secateurs.getNote().contains("item " + ItemID.LAVA_BATTLESTAFF));

		// A staff in the bank counts too, as the plan says to wield it
		final SupplyPlan fromBank = plan(config, AccessSnapshot.UNKNOWN, wornAndBank(bank, worn), NEVER_SEEN, true);
		assertNull(find(fromBank, "item " + Rune.EARTH.getItemId()));
		assertTrue(find(fromBank, "item " + ItemID.LAVA_BATTLESTAFF).isWear());

		// No runes to save: the staff isn't a candidate, and the secateurs are worn
		final Holdings held = wornAndBank(worn, bank);
		assertTrue(SupplyCalculator.runeItems(Collections.emptyMap(), held).isEmpty());
		final Map<TravelItem.WearSlot, Object> wear = SupplyCalculator.toWear(Collections.emptySet(),
			Collections.singletonList(new SupplyCalculator.Wearable(SupplyCalculator.Wearable.MAGIC_SECATEURS,
				TravelItem.WearSlot.WEAPON, 1, 1, false)), Outfit.NONE, held);
		assertEquals(SupplyCalculator.Wearable.MAGIC_SECATEURS, wear.get(TravelItem.WearSlot.WEAPON));
		assertFalse(SupplyCalculator.infiniteRunes(wear, held).contains(Rune.FIRE));
	}

	@Test
	public void theFairyRingStaffBeatsAStaffSavingRunes()
	{
		final Map<Integer, Integer> worn = new HashMap<>();
		worn.put(ItemID.LAVA_BATTLESTAFF, 1);
		final Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.DRAMEN_STAFF, 1);
		final Holdings held = wornAndBank(worn, bank);
		final Map<Rune, Integer> need = new EnumMap<>(Rune.class);
		need.put(Rune.EARTH, 8);

		final List<SupplyCalculator.Wearable> extras = new ArrayList<>();
		extras.add(new SupplyCalculator.Wearable(SupplyCalculator.Wearable.FAIRY_RING_STAFF, TravelItem.WearSlot.WEAPON,
			SupplyCalculator.Wearable.NEEDED, 0, false));
		extras.addAll(SupplyCalculator.runeItems(need, held));
		extras.add(new SupplyCalculator.Wearable(SupplyCalculator.Wearable.MAGIC_SECATEURS, TravelItem.WearSlot.WEAPON,
			1, 1, false));
		final Map<TravelItem.WearSlot, Object> wear = SupplyCalculator.toWear(Collections.emptySet(), extras,
			Outfit.NONE, held);
		assertEquals(SupplyCalculator.Wearable.FAIRY_RING_STAFF, wear.get(TravelItem.WearSlot.WEAPON));
		// The lava staff comes off for the dramen staff, so its runes no longer count
		final Set<Rune> infinite = SupplyCalculator.infiniteRunes(wear, held);
		assertFalse(infinite.contains(Rune.EARTH));
		final SupplyCalculator.RuneResult runes = SupplyCalculator.runes(need, infinite, held, held.carriedOnly(),
			id -> "rune " + id);
		assertFalse(runes.lines.get(0).isMet());

		// Without fairy rings the lava staff stays on, ahead of the secateurs
		final Map<TravelItem.WearSlot, Object> noRings = SupplyCalculator.toWear(Collections.emptySet(),
			extras.subList(1, extras.size()), Outfit.NONE, held);
		assertEquals(ItemID.LAVA_BATTLESTAFF, noRings.get(TravelItem.WearSlot.WEAPON));
		assertTrue(SupplyCalculator.infiniteRunes(noRings, held).contains(Rune.EARTH));
	}

	@Test
	public void theArdougneCloakGivesWayToTheCapeAlreadyWorn()
	{
		final Set<TravelItem> used = EnumSet.of(TravelItem.ARDOUGNE_CLOAK, TravelItem.QUEST_POINT_CAPE);
		final Map<Integer, Integer> bank = new HashMap<>();
		bank.put(ItemID.ARDY_CAPE_ELITE, 1);
		final Map<Integer, Integer> worn = new HashMap<>();
		worn.put(ItemID.SKILLCAPE_QP, 1);
		// The cloak teleports from the inventory too, so wearing it is only to save a slot
		assertEquals(EnumSet.of(TravelItem.QUEST_POINT_CAPE),
			SupplyCalculator.toWear(used, Outfit.NONE, wornAndBank(worn, bank)));
		// Neither worn: the first used gets the cape slot, the other is carried
		bank.putAll(worn);
		assertEquals(EnumSet.of(TravelItem.ARDOUGNE_CLOAK),
			SupplyCalculator.toWear(used, Outfit.NONE, wornAndBank(new HashMap<>(), bank)));
	}
}
