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
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.route.Route;
import com.farmrunautopilot.route.RoutePlanner;
import com.farmrunautopilot.route.RunSelection;
import com.farmrunautopilot.route.RunSelector;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.PohSetup;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.tracking.PatchPrediction;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
		final Map<net.runelite.api.Skill, Integer> levels = new EnumMap<>(net.runelite.api.Skill.class);
		levels.put(net.runelite.api.Skill.FARMING, 99);
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
		final SupplyLine fire = byName.get("rune " + Rune.FIRE.getItemId());
		assertTrue(fire.isMet());
		assertEquals(0, fire.getSlots());
		assertTrue(result.summary.contains("staff covers Fire"));

		// Everything is in the bank, so nothing is carried: air is short, earth is in storage, fire is carried.
		assertEquals(SupplyLine.Status.MISSING, byName.get("rune " + Rune.AIR.getItemId()).getStatus());
		assertEquals(SupplyLine.Status.IN_STORAGE, byName.get("rune " + Rune.EARTH.getItemId()).getStatus());
		assertEquals(SupplyLine.Status.CARRIED, fire.getStatus());
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
	public void leprechaunCountsCombine()
	{
		assertEquals(5, LeprechaunItem.combine(5, 0, 8));
		assertEquals(256 + 5, LeprechaunItem.combine(5, 1, 8));
	}
}
