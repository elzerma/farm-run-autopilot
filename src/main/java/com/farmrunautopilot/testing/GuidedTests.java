package com.farmrunautopilot.testing;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.data.AchievementDiary;
import com.farmrunautopilot.data.Crop;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.PatchPoints;
import com.farmrunautopilot.data.PatchType;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.SupplyItems;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Rune;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.BankStop;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.run.RunSession;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.Protection;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.settings.VaultBanking;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.ItemChargeTracker;
import com.farmrunautopilot.supply.SupplyLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import static com.farmrunautopilot.testing.GuidedTest.Step.ask;
import static com.farmrunautopilot.testing.GuidedTest.Step.doThis;

/** The guided test for each {@link TestItem}. */
final class GuidedTests
{
	/** Ticks to wait for something the plugin should spot before asking (about 30 seconds). */
	private static final int SPOT_TICKS = 50;
	/** Ticks to wait for the player to do something, e.g. fetch an item and teleport (about 6 minutes). */
	private static final int DO_TICKS = 600;

	private static final Map<TestItem, GuidedTest> TESTS = new EnumMap<>(TestItem.class);

	static
	{
		TESTS.put(TestItem.SPELLBOOK_SWAP, new SpellbookSwap());
		TESTS.put(TestItem.SEED_VAULT_SECTION, new SeedVaultTop());
		TESTS.put(TestItem.VAULT_SEEDS_FOR_LATER, new VaultSeedsForLater());
		TESTS.put(TestItem.VAULT_BANK_AT_GUILD, new VaultBankAtGuild());
		TESTS.put(TestItem.ALLOTMENT_PAYMENT, new AllotmentPayment());
		TESTS.put(TestItem.NEW_PATCH_POINTS, new NewPatchPoints());
		TESTS.put(TestItem.ALLOTMENT_FLOWER_PROTECTION, new AllotmentFlowerProtection());
		TESTS.put(TestItem.FORTIS_CHAMPION, new FortisChampion());
		TESTS.put(TestItem.ATES_CHARGES, new AtesCharges());
		TESTS.put(TestItem.SPIRIT_TREES, new SpiritTrees());
		TESTS.put(TestItem.BUCKET_COMPOST, new BucketCompost());
		TESTS.put(TestItem.HOUSE_BY_TREE, new HouseByTree());
		TESTS.put(TestItem.HOUSE_SCAN, new HouseScan());
		TESTS.put(TestItem.STAFF_SWAPS, new StaffSwapGuide());
	}

	private GuidedTests()
	{
	}

	static GuidedTest of(TestItem item)
	{
		return TESTS.get(item);
	}

	// Shared helpers

	/** Plan a run of just this location's patches, every one counted whether due or not. */
	private static void onlyPatchesAt(RunConfig config, Location location)
	{
		config.getEnabledTypes().clear();
		config.getDisabledPatches().clear();
		for (Patch patch : Patch.values())
		{
			if (patch.getLocation() == location)
			{
				config.getEnabledTypes().add(patch.getType());
			}
			else
			{
				config.getDisabledPatches().add(patch);
			}
		}
		config.setSupplyFullRun(true);
	}

	/** Travel to this location this way, cast from runes so a tablet doesn't stand in for the spell. */
	private static void travelBy(RunConfig config, TravelMethod method)
	{
		config.getTravel().put(method.getDestination(), method);
		config.getTravelHow().put(method.getDestination(), Departure.DIRECT);
		config.getRunesNotTabsAt().add(method.getDestination());
	}

	/** Why a location's patches can't be used, or nothing. */
	private static List<String> patchesUsable(AccessSnapshot access, Location location)
	{
		for (Patch patch : Patch.values())
		{
			if (patch.getLocation() == location && access.missingFor(patch).isEmpty())
			{
				return Collections.emptyList();
			}
		}
		return Collections.singletonList("Unlock a patch at " + location.getDisplayName());
	}

	private static List<String> owns(Holdings holdings, String label, TravelItem... items)
	{
		for (TravelItem item : items)
		{
			if (holdings.countAny(item.getItemIds()) > 0)
			{
				return Collections.emptyList();
			}
		}
		return Collections.singletonList("Have " + label + " in your bank or on you");
	}

	private static GuidedTest.Step startRun()
	{
		return doThis("On the Run tab, press Build run, take what the bank's Farm run tab lists, then start the run",
			ctx -> ctx.runView().getState() == RunView.State.RUNNING);
	}

	private static String warnings(TestContext ctx)
	{
		final List<String> warnings = ctx.plan().getSupplies().getWarnings();
		return warnings.isEmpty() ? "none" : String.join(" | ", warnings);
	}

	private static String supplyNames(TestContext ctx)
	{
		final List<String> names = new ArrayList<>();
		for (SupplyLine line : ctx.plan().getSupplies().getLines())
		{
			names.add(line.getNeed() + " x " + line.getName());
		}
		return names.isEmpty() ? "none" : String.join(", ", names);
	}

	private static Spell highlightedSpell(TestContext ctx)
	{
		return ctx.runView().getHighlights().getSpell();
	}

	/** The count in the first chat message matching, or null. */
	private static Integer chatCount(TestContext ctx, Pattern... patterns)
	{
		for (String message : ctx.chat())
		{
			for (Pattern pattern : patterns)
			{
				final Matcher m = pattern.matcher(message);
				if (m.matches())
				{
					ctx.capture("Chat message", message);
					return ItemChargeTracker.count(m.group(1));
				}
			}
		}
		return null;
	}

	/**
	 * Just the allotments (and their flower patch) at the farm south of Falador, planting potatoes with no compost, so
	 * all the test needs is seeds and the tools.
	 */
	private static void faladorAllotments(RunConfig config, Protection protection)
	{
		onlyPatchesAt(config, Location.FALADOR_FARM);
		config.getEnabledTypes().remove(PatchType.HERB);
		config.getDisabledPatches().add(Patch.FALADOR_HERB);
		config.getCrops().put(PatchType.ALLOTMENT, Crop.POTATO);
		config.getProtection().put(PatchType.ALLOTMENT, protection);
		config.getProtectionOverrides().remove(Patch.FALADOR_ALLOTMENT_NW);
		config.getProtectionOverrides().remove(Patch.FALADOR_ALLOTMENT_SE);
		config.getCompost().put(PatchType.ALLOTMENT, Compost.NONE);
		config.getCompost().put(PatchType.FLOWER, Compost.NONE);
	}

	/** Each allotment, flower and hops patch loaded nearby: where its object is, against its map point. */
	private static String newPatchObjects(TestContext ctx)
	{
		final List<String> found = new ArrayList<>();
		for (Patch patch : Patch.values())
		{
			if (patch.getType() != PatchType.ALLOTMENT && patch.getType() != PatchType.FLOWER
				&& patch.getType() != PatchType.HOPS)
			{
				continue;
			}
			final List<GameObject> objects = ctx.getScene().objectsFor(patch);
			if (!objects.isEmpty())
			{
				final WorldPoint at = objects.get(0).getWorldLocation();
				found.add(patch + " at " + at + " (map point " + PatchPoints.of(patch) + ", "
					+ at.distanceTo2D(PatchPoints.of(patch)) + " tiles off)");
			}
		}
		return found.isEmpty() ? "none" : String.join("; ", found);
	}

	// The tests

	/**
	 * Swapping staves on the way (issue #7). Untried in game: that the "Wield your ..." step comes before setting off,
	 * outlines the staff, and moves on once the game shows it wielded.
	 */
	private static final class StaffSwapGuide extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			// Two staves that cover different runes, or the fairy ring staff (while rings need it) and a rune staff
			final Set<Set<Rune>> kinds = new HashSet<>();
			SupplyItems.INFINITE_RUNE_ITEMS.forEach((id, runes) ->
			{
				if (!SupplyItems.RUNE_TOMES.contains(id) && holdings.count(id) > 0)
				{
					kinds.add(runes);
				}
			});
			if (holdings.countAny(SupplyItems.FAIRY_RING_STAFFS) > 0 && !(access.isKnown() && access.isMet(
				Requirement.diary(AchievementDiary.LUMBRIDGE_DRAYNOR, AchievementDiary.Tier.ELITE))))
			{
				kinds.add(Collections.emptySet());
			}
			return kinds.size() >= 2 ? Collections.emptyList() : Collections.singletonList(
				"Have two staves, e.g. a dramen staff and a lava battlestaff, in your bank or on you");
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			// Teleports from runes, so the staves have runes to save
			config.getRunesNotTabsAt().addAll(EnumSet.allOf(Location.class));
		}

		@Override
		public boolean usesRun()
		{
			return true;
		}

		private static String swaps(TestContext ctx)
		{
			final List<String> parts = new ArrayList<>();
			ctx.plan().getSupplies().getStaffSwaps().getWield().forEach((location, swap) ->
				parts.add(location.getDisplayName() + ": " + swap.getName()));
			return parts.isEmpty() ? "none" : String.join(", ", parts);
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("On the Run tab, press Build run", ctx -> ctx.runView().getState() != RunView.State.OFF)
					.onDone(ctx ->
					{
						ctx.capture("Supply list", supplyNames(ctx));
						ctx.capture("Staves wielded before", swaps(ctx));
						if (ctx.plan().getSupplies().getStaffSwaps().isEmpty())
						{
							ctx.cannotTest("a run where carrying a second staff frees an inventory slot (try more stops, "
								+ "or leave your rune pouch in the bank)");
						}
					}),
				ask("Does the Run tab's list have one staff to wield at the bank and another to carry, \"wield it when "
					+ "the guide says\"?"),
				startRun(),
				doThis("Follow the guide until it says to wield a staff",
					ctx -> instruction(ctx).startsWith("Wield your"))
					.onDone(ctx ->
					{
						ctx.capture("Wield step", instruction(ctx));
						ctx.capture("Outlined to wear", ctx.runView().getHighlights().getWearItemIds());
					})
					.orAskAfter(DO_TICKS, "The guide hasn't said to wield a staff. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step instead", instruction(ctx));
						ctx.problem("No step to wield a staff");
					}),
				ask("Is that staff outlined in your inventory, with a blue \"Wear\" label?"),
				doThis("Wield it", ctx -> !instruction(ctx).startsWith("Wield your"))
					.onDone(ctx -> ctx.capture("Step after wielding", instruction(ctx)))
					.orAskAfter(SPOT_TICKS, "The step didn't move on. Is the staff wielded?")
					.onAnswer((ctx, yes) -> ctx.problem(yes ? "Wielded, but the step didn't move on" : "Couldn't wield it")),
				ask("Does the step now say to set off for that stop (the teleport or fairy ring)?"));
		}
	}

	/**
	 * Flower protection for allotments (docs/plans/crop-types.md, Sprint 1a). UNVERIFIED: that a marigold planted on the
	 * same visit is enough (the wiki only says it protects once fully grown), and how the game says a patch is protected.
	 */
	private static final class AllotmentFlowerProtection extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return patchesUsable(access, Location.FALADOR_FARM);
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			faladorAllotments(config, Protection.FLOWER);
		}

		@Override
		public List<Need> bring()
		{
			return Arrays.asList(new Need("6 potato seeds", new int[]{ItemID.POTATO_SEED}),
				new Need("A marigold seed", new int[]{ItemID.MARIGOLD_SEED}));
		}

		@Override
		public boolean usesRun()
		{
			return true;
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				ask("Does the Run tab's list have a Marigold seed \"protects allotments\", and no compost for paying Elstan?")
					.onStart(ctx -> ctx.capture("Supply list", supplyNames(ctx))),
				startRun(),
				doThis("Go to the farm south of Falador: the first patch step should be the flower patch",
					ctx -> ctx.runView().getHighlights().getPatch() == Patch.FALADOR_FLOWER)
					.onDone(ctx ->
					{
						ctx.capture("First step", instruction(ctx));
						ctx.capture("Patch objects", newPatchObjects(ctx));
					})
					.orAskAfter(DO_TICKS, "The plugin didn't start at the flower patch. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step instead", instruction(ctx));
						ctx.problem("The flower patch wasn't first");
					}),
				ask("Follow the steps until both allotments are planted. Did the steps ever ask you to pay Elstan?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Last step", instruction(ctx));
						if (yes)
						{
							ctx.problem("Asked to pay Elstan with the marigold protecting the allotments");
						}
					}),
				ask("Right-click an allotment and pick Inspect. Does the message say it's protected?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Inspect message", String.join(" | ", ctx.chat()));
						ctx.capture("Says protected", yes);
					}));
		}
	}

	/** Paying an allotment gardener for one of their two patches. UNVERIFIED: how the game asks which one. */
	private static final class AllotmentPayment extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return patchesUsable(access, Location.FALADOR_FARM);
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			faladorAllotments(config, Protection.PAY_GARDENER);
		}

		@Override
		public List<Need> bring()
		{
			return Arrays.asList(new Need("6 potato seeds", new int[]{ItemID.POTATO_SEED}),
				new Need("4 buckets of compost", new int[]{ItemID.BUCKET_COMPOST}));
		}

		@Override
		public boolean usesRun()
		{
			return true;
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				startRun(),
				doThis("Go to the farm south of Falador and plant the first allotment",
					ctx -> instruction(ctx).startsWith("Pay Elstan for the"))
					.onDone(ctx -> ctx.remember("pay step", instruction(ctx)))
					.orAskAfter(DO_TICKS, "The plugin hasn't asked you to pay Elstan for one allotment. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step instead", instruction(ctx));
						ctx.problem("No \"Pay Elstan for the ... allotment\" step");
					}),
				doThis("Pay Elstan for the allotment the step names (right-click him, or talk to him)",
					ctx -> ctx.recall("pay step") != null && !instruction(ctx).equals(ctx.recall("pay step")))
					.onStart(ctx -> ctx.capture("Pay step", ctx.recall("pay step")))
					.onDone(ctx -> ctx.capture("Next step", instruction(ctx)))
					.orAskAfter(DO_TICKS, "The step didn't move on. Did you pay Elstan?")
					.onAnswer((ctx, yes) -> ctx.problem(yes ? "Paid, but the step didn't move on" : "Couldn't pay")),
				ask("Could you choose which allotment you paid for, and was it the one the step named?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Messages", String.join(" | ", ctx.chat()));
						if (!yes)
						{
							ctx.problem("Paying didn't let them pick the allotment the step named");
						}
					}));
		}
	}

	/** The wiki's map points for the new patches. UNVERIFIED until the objects' real places are logged. */
	private static final class NewPatchPoints extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			for (Patch patch : Patch.values())
			{
				if ((patch.getType() == PatchType.ALLOTMENT || patch.getType() == PatchType.HOPS)
					&& access.missingFor(patch).isEmpty())
				{
					return Collections.emptyList();
				}
			}
			return Collections.singletonList("Unlock an allotment or hops patch");
		}

		private static Step visit(String text)
		{
			return doThis(text, ctx -> !"none".equals(newPatchObjects(ctx)))
				.onDone(ctx -> ctx.capture("Patches at " + ctx.location(), newPatchObjects(ctx)))
				.orAskAfter(DO_TICKS, "The plugin hasn't seen an allotment, flower or hops patch yet. Are you by one?")
				.onAnswer((ctx, yes) ->
				{
					if (yes)
					{
						ctx.problem("By a patch at " + ctx.location() + " but its object wasn't matched");
					}
				});
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				visit("Go to any allotment set or hops patch and stand by it"),
				visit("Now another one (a different place)").optional(),
				visit("And one more, if you can").optional());
		}
	}

	private static final class SpellbookSwap extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			final List<String> missing = new ArrayList<>(patchesUsable(access, Location.CATHERBY));
			if (!access.needsLunarSwap(Spell.CAMELOT_TELEPORT))
			{
				missing.add("Be on the Lunar spellbook with 96 Magic and Dream Mentor done, and no house altar for "
					+ "the standard spellbook");
			}
			return missing;
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			onlyPatchesAt(config, Location.CATHERBY);
			travelBy(config, TravelMethod.CAMELOT_TELEPORT);
		}

		@Override
		public boolean usesRun()
		{
			return true;
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				ask("Does the Run tab warn that you need Spellbook Swap for Catherby?")
					.onStart(ctx ->
					{
						ctx.capture("Run tab warnings", warnings(ctx));
						ctx.capture("Supply list", supplyNames(ctx));
					}),
				ask("Press Build run and open a bank: are Spellbook Swap's runes (3 astral, 2 cosmic, 1 law) on the "
					+ "bank's Farm run tab, as well as Camelot Teleport's?"),
				startRun(),
				doThis("Wait for the first step", ctx -> highlightedSpell(ctx) == Spell.SPELLBOOK_SWAP)
					.orAskAfter(SPOT_TICKS, "The plugin didn't pick Spellbook Swap to highlight. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Highlighted spell", highlightedSpell(ctx));
						ctx.problem("Spellbook Swap wasn't the spell to highlight");
					}),
				ask("Open your spellbook. Is Spellbook Swap outlined?"),
				ask("Cast Spellbook Swap and pick the standard spellbook. Is Camelot Teleport outlined now?")
					.onStart(ctx -> ctx.capture("Spellbook before the swap", ctx.access().getSpellbook()))
					.optional());
		}
	}

	/**
	 * The Farm Run Autopilot view of the seed vault (issue #8): where the game puts everything in the vault was read
	 * from its scripts. Revisions 2 and 3 passed, with elzerma's own checks: the counts, staying once everything's
	 * taken, opening at the top, dragging, the button by the close button and Favourite Slots. Revision 4 asks about the
	 * view's entry in the list on the left, which shows only while the view is on (elzerma).
	 */
	private static final class SeedVaultTop extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return patchesUsable(access, Location.FARMING_GUILD);
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			onlyPatchesAt(config, Location.FARMING_GUILD);
			config.setUseSeedVault(true);
		}

		@Override
		public boolean usesRun()
		{
			return true;
		}

		/** What the section shows (and where), for the report under this name. */
		private static void captureVault(TestContext ctx, String label)
		{
			ctx.capture(label, ctx.seedVault().describe());
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("On the Run tab, press Build run", ctx -> ctx.runView().getState() != RunView.State.OFF),
				// Without them in the vault the button has nothing to call for and the section nothing to show
				ask("Are some of the seeds or saplings the bank's Farm run tab lists in the Farming Guild's seed vault, "
					+ "and not in your bank or inventory?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Supply list", supplyNames(ctx));
						if (!yes)
						{
							ctx.cannotTest("some of the seeds or saplings for the Farming Guild's patches in its seed vault, not "
								+ "your bank");
						}
					}),
				doThis("Go to the Farming Guild and open its seed vault", ctx -> ctx.seedVault().isLaidOut())
					.onDone(ctx ->
					{
						captureVault(ctx, "Seed vault when opened");
						if (!ctx.seedVault().isCalling())
						{
							ctx.problem("The button by the close button wasn't asking to be clicked");
						}
					})
					.orAskAfter(DO_TICKS, "The plugin hasn't seen the seed vault open. Is it open?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault when asked");
						ctx.problem("The plugin didn't see the seed vault laid out");
					}),
				ask("Click the button by the vault's close button. Is there a \"Farm Run Autopilot\" section at the top, "
					+ "and a \"Farm Run Autopilot\" entry at the top of the list on the left?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault with the view on");
						if (ctx.seedVault().shownCount() == 0)
						{
							ctx.problem("No Farm Run Autopilot section once the button was clicked");
						}
						if (!yes)
						{
							ctx.problem("Answered No: the section or its entry in the list wasn't there");
						}
					}),
				ask("Scroll down, then click Farm Run Autopilot in the list on the left. Does it take you back to the "
					+ "section?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault after the list entry");
						if (!yes)
						{
							ctx.problem("Answered No: the list entry didn't go to the section");
						}
					}),
				ask("Click Favourite Slots in the list. Does it go to Favourite Slots, just below the section?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault after Favourite Slots");
						if (!yes)
						{
							ctx.problem("Answered No: Favourite Slots didn't go to its own place");
						}
					}),
				ask("Click the button again. Do the section and its entry in the list both go, leaving the vault and the "
					+ "list as usual?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault with the view off");
						if (ctx.seedVault().shownCount() > 0)
						{
							ctx.problem("The section was still there once the button was clicked again");
						}
						if (!yes)
						{
							ctx.problem("Answered No: the vault or the list didn't go back to usual");
						}
					}));
		}
	}

	/** The step under the player, or "" when there's none. */
	private static String instruction(TestContext ctx)
	{
		final String text = ctx.runView().getInstruction();
		return text == null ? "" : text;
	}

	/** The run's bank visits, e.g. "after 4 Catherby: Bank at the Farming Guild (Farming cape to the guild's bank)". */
	private static String bankVisits(TestContext ctx)
	{
		final List<String> visits = new ArrayList<>();
		for (BankStop bank : ctx.plan().getBankStops())
		{
			visits.add("after " + (bank.getAfterStop() + 1) + " " + bank.getLocation().getDisplayName() + ": "
				+ bank.title() + " (" + bank.how() + ")" + (bank.isAtGuild() ? ", at the guild" : ""));
		}
		return visits.isEmpty() ? "none" : String.join(" | ", visits);
	}

	/**
	 * A run that banks partway, with seeds for after the visit only in the seed vault: asked first, then checked.
	 *
	 * @param onYes what else to check once the player says they have that
	 */
	private static GuidedTest.Step banksWithVaultSeedsForLater(Consumer<TestContext> onYes)
	{
		return ask("Does the Run tab show a bank visit partway through the run, with seeds for after it only in the "
			+ "Farming Guild's seed vault? (Open the vault once first, so the plugin has seen what's in it.)")
			.onAnswer((ctx, yes) ->
			{
				ctx.capture("Bank visits", bankVisits(ctx));
				if (!yes)
				{
					ctx.cannotTest("a run that banks partway, with seeds for after the bank visit only in the seed vault");
					return;
				}
				if (ctx.plan().getBankStops().isEmpty())
				{
					ctx.problem("The plugin didn't plan a bank visit");
				}
				onYes.accept(ctx);
			});
	}

	/**
	 * "Take all seeds at the start" (issue #8): the gathering steps for the seed vault's seeds for after the bank
	 * visit, and the run waiting for them.
	 */
	private static final class VaultSeedsForLater extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return Collections.emptyList();
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			config.setUseSeedVault(true);
			config.setVaultBanking(VaultBanking.TAKE_ALL_AT_START);
		}

		@Override
		public boolean usesRun()
		{
			return true;
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("On the Run tab, press Build run", ctx -> ctx.runView().getState() != RunView.State.OFF),
				banksWithVaultSeedsForLater(ctx ->
				{
					final Map<Integer, Integer> later = RunSession.laterFromVault(ctx.plan());
					ctx.capture("Seeds for later only in the vault", later);
					if (later.isEmpty())
					{
						ctx.problem("The plugin didn't see seeds for after the bank visit only in the seed vault");
					}
				}),
				doThis("Read the step under your character: it should start at the seed vault",
					ctx -> instruction(ctx).contains("from the seed vault"))
					.onDone(ctx -> ctx.capture("Vault step", instruction(ctx)))
					.orAskAfter(DO_TICKS, "The step hasn't said to take seeds from the seed vault first. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step when asked", instruction(ctx));
						ctx.problem("The step never said to take the seeds for later from the vault first");
					}),
				doThis("Take them from the seed vault, then open the bank beside it",
					ctx -> instruction(ctx).contains("Deposit inventory"))
					.onDone(ctx -> ctx.capture("Bank step", instruction(ctx)))
					.orAskAfter(DO_TICKS, "The step hasn't said to deposit everything with Deposit inventory. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step when asked", instruction(ctx));
						ctx.problem("The step never said to deposit everything at the bank");
					}),
				doThis("Press Deposit inventory, then take what the Farm run tab marks",
					ctx -> ctx.runView().getState() == RunView.State.ARMED
					|| ctx.runView().getState() == RunView.State.RUNNING)
					.onDone(ctx -> ctx.capture("Seed vault section", ctx.seedVault().describe()))
					.orAskAfter(DO_TICKS, "The run isn't ready yet. Are the seeds for later in your bank?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step when asked", instruction(ctx));
						ctx.problem(yes ? "The run didn't get ready with the seeds for later in the bank"
							: "Answered No: the seeds for later weren't put in the bank");
					}),
				ask("Do the run to its bank visit. Does the bank's Farm run tab show the seeds for later, to take from "
					+ "your bank?")
					.optional());
		}
	}

	/**
	 * "Bank at the Farming Guild" (issue #8): the bank visit before seeds only the seed vault has is at the guild, and
	 * there the vault comes last, showing only the rest of the run.
	 */
	private static final class VaultBankAtGuild extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return Collections.emptyList();
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			config.setUseSeedVault(true);
			config.setVaultBanking(VaultBanking.BANK_AT_GUILD);
		}

		@Override
		public boolean usesRun()
		{
			return true;
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("On the Run tab, press Build run", ctx -> ctx.runView().getState() != RunView.State.OFF),
				banksWithVaultSeedsForLater(ctx ->
				{
					if (ctx.plan().getBankStops().stream().noneMatch(BankStop::isAtGuild))
					{
						ctx.problem("No bank visit at the Farming Guild");
					}
				}),
				startRun(),
				doThis("Do the run up to the bank visit at the Farming Guild, then take what the bank's Farm run tab "
					+ "lists", ctx -> instruction(ctx).contains("from the seed vault (next to the bank)")
					|| instruction(ctx).contains("flashing button at the top of the seed vault"))
					.onDone(ctx -> ctx.capture("Vault step", instruction(ctx)))
					.orAskAfter(3 * DO_TICKS, "The step hasn't said to take seeds from the seed vault. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step when asked", instruction(ctx));
						ctx.problem("The bank visit never said to take seeds from the seed vault");
					}),
				ask("Open the seed vault and click the button by its close button. Does the Farm Run Autopilot section "
					+ "show only the seeds for the rest of the run?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Seed vault section", ctx.seedVault().describe());
						if (!yes)
						{
							ctx.problem("Answered No: the section didn't show only the rest of the run");
						}
					}),
				doThis("Take them", ctx -> !instruction(ctx).contains("seed vault"))
					.onDone(ctx -> ctx.capture("Next step", instruction(ctx)))
					.orAskAfter(DO_TICKS, "The step still says to take seeds from the seed vault. Have you taken them?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step when asked", instruction(ctx));
						ctx.problem(yes ? "The guide didn't move on once the seeds were taken"
							: "Answered No: the seeds weren't taken");
					}));
		}
	}

	private static final class FortisChampion extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return patchesUsable(access, Location.CIVITAS_ILLA_FORTIS);
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			onlyPatchesAt(config, Location.CIVITAS_ILLA_FORTIS);
			config.setPlantCureDoses(1);
			account.getManualUnlocks().add(Unlock.FORTIS_CHAMPION);
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				ask("Do you have the Champion rank at the Fortis Colosseum?")
					.onAnswer((ctx, yes) ->
					{
						if (!yes)
						{
							ctx.cannotTest("the Champion rank at the Fortis Colosseum");
						}
					}),
				// Read from the plan, not asked: the supply list is on the bank's Farm run tab since Sprint 5
				doThis("Checking the supplies for the Civitas herb patch", ctx -> true)
					.onStart(ctx ->
					{
						final String supplies = supplyNames(ctx);
						ctx.capture("Supply list", supplies);
						if (supplies.contains("Plant cure"))
						{
							ctx.problem("Plant cure was asked for the disease-free Civitas herb patch");
						}
					}),
				ask("Has your Civitas herb patch ever caught disease since you got the rank?")
					.onAnswer((ctx, yes) ->
					{
						if (yes)
						{
							ctx.problem("The Civitas herb patch caught disease with the rank");
						}
					})
					.optional());
		}
	}

	private static final class AtesCharges extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return owns(holdings, "a pendant of Ates", TravelItem.PENDANT_OF_ATES);
		}

		@Override
		public List<Need> bring()
		{
			return Collections.singletonList(new Need("Pendant of Ates", TravelItem.PENDANT_OF_ATES.getItemIds()));
		}

		/** The pendant's Check message, compared with the plugin's count. */
		private static boolean checked(TestContext ctx, String label)
		{
			final Integer said = chatCount(ctx, ItemChargeTracker.PENDANT_CHECK);
			if (said == null)
			{
				return false;
			}
			final Integer plugin = ctx.charges(TravelItem.PENDANT_OF_ATES);
			ctx.capture(label + ": Check said", said);
			ctx.capture(label + ": plugin count", plugin);
			if (plugin == null || plugin.intValue() != said)
			{
				ctx.problem(label + ": the plugin counted " + plugin + " but the Check said " + said);
			}
			return true;
		}

		@Override
		public List<Step> steps()
		{
			// Counted like Xeric's talisman (a teleport click, then being moved), which passed in game (issue #13)
			return Arrays.asList(
				doThis("Take the pendant of Ates out of the bank", ctx -> ctx.carries(TravelItem.PENDANT_OF_ATES.getItemIds())),
				doThis("Right-click the pendant and pick Check", ctx -> checked(ctx, "First check")),
				doThis("Teleport with the pendant once", ctx ->
				{
					final Integer now = ctx.charges(TravelItem.PENDANT_OF_ATES);
					final Integer before = ctx.recall("before");
					return now != null && before != null && now < before;
				})
					.onStart(ctx -> ctx.remember("before", ctx.charges(TravelItem.PENDANT_OF_ATES)))
					.orAskAfter(DO_TICKS, "Have you teleported with the pendant?")
					.onAnswer((ctx, yes) ->
					{
						if (yes)
						{
							ctx.problem("The plugin's count didn't go down after a teleport");
						}
					}),
				doThis("Check it again", ctx -> checked(ctx, "After teleporting")));
		}
	}

	/**
	 * Which stored-bucket type value means which compost (the game's script only says 1 is empty, higher filled):
	 * the player says which compost is in theirs, the test captures the value.
	 */
	private static final class BucketCompost extends GuidedTest
	{
		private static Step isIt(Compost compost)
		{
			return ask("Is the bucket filled with " + compost.getDisplayName().toLowerCase() + "?").onAnswer((ctx, yes) ->
			{
				if (!yes)
				{
					return;
				}
				final int type = ctx.varbit(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE);
				ctx.capture("Compost in it / type value", compost.getDisplayName() + " / " + type);
				ctx.capture("Uses value", ctx.varbit(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY));
				if (Compost.fromBucketType(type) != compost)
				{
					ctx.problem("Type value " + type + " was read as " + Compost.fromBucketType(type) + ", not "
						+ compost.getDisplayName());
				}
			});
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				// An empty or missing bucket reads 0 or 1 whichever compost values are right, which proves nothing
				ask("Do you have a bottomless compost bucket with compost in it?").onAnswer((ctx, yes) ->
				{
					if (!yes)
					{
						ctx.cannotTest("a bottomless compost bucket with compost in it");
					}
				}),
				doThis("Store the bucket with a tool leprechaun",
					ctx -> ctx.varbit(VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE) > Compost.EMPTY_BUCKET_TYPE),
				isIt(Compost.COMPOST),
				isIt(Compost.SUPERCOMPOST),
				isIt(Compost.ULTRACOMPOST));
		}
	}

	/** A spirit tree as the way into the house (issue #16): the timings are estimates, and the guide's steps untried. */
	private static final class HouseByTree extends GuidedTest
	{
		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				// Without one, the plan never offers it, which proves nothing
				ask("Is a spirit tree (or spirit ring) planted in your house?")
					.onAnswer((ctx, yes) ->
					{
						if (!yes)
						{
							ctx.cannotTest("a spirit tree planted in your house");
						}
					}),
				ask("Build a run where a stop follows one with a spirit tree nearby, e.g. the Farming Guild then a "
					+ "patch by your house portal. Does that stop say \"Spirit tree to your house, then...\"?"),
				ask("Start the run and follow it. Did the guide move on once you were in your house, and again once you'd "
					+ "left it?"),
				ask("Was that way quicker than how you'd normally get there?").optional());
		}
	}

	private static final class SpiritTrees extends GuidedTest
	{
		private static final Set<Unlock> PLANTED = Collections.unmodifiableSet(EnumSet.of(
			Unlock.SPIRIT_TREE_PORT_SARIM, Unlock.SPIRIT_TREE_BRIMHAVEN, Unlock.SPIRIT_TREE_FARMING_GUILD));

		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return access.level(Skill.FARMING) >= 83 ? Collections.emptyList()
				: Collections.singletonList("Have a planted spirit tree (83 Farming)");
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			account.getManualUnlocks().removeAll(PLANTED);
		}

		private static boolean spotted(TestContext ctx)
		{
			for (Unlock unlock : PLANTED)
			{
				if (ctx.hasUnlock(unlock))
				{
					ctx.capture("Spotted", unlock);
					return true;
				}
			}
			return false;
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("Go to a spirit tree you've planted: Port Sarim, Brimhaven or the Farming Guild",
					ctx -> ctx.inRegion(12082, 11058, 11057, 4922))
					.onDone(ctx -> ctx.capture("Arrived at", ctx.location())),
				doThis("Walk up to the spirit tree", GuidedTests.SpiritTrees::spotted)
					.orAskAfter(SPOT_TICKS, "The plugin hasn't spotted it. Is your spirit tree here fully grown?")
					.onAnswer((ctx, yes) ->
					{
						if (!yes)
						{
							ctx.cannotTest("a fully grown spirit tree you've planted");
							return;
						}
						ctx.capture("Spirit trees loaded", ctx.objectsNamed("Spirit tree"));
						ctx.capture("Standing at", ctx.location());
						ctx.problem("The grown spirit tree wasn't spotted");
					}));
		}

		@Override
		public void keep(AccountSettings kept, AccountSettings during)
		{
			for (Unlock unlock : PLANTED)
			{
				if (during.getManualUnlocks().contains(unlock))
				{
					kept.getManualUnlocks().add(unlock);
				}
			}
		}
	}

	private static final class HouseScan extends GuidedTest
	{
		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			account.setAutoDetectHouse(true);
			// Start empty, so only the scan can fill the furniture in (issue #10: it was already filled in, which
			// proved nothing). The player's own is put back unless the scan ran (see keep)
			PohDetector.clearDetected(account.getPoh());
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("Enter your own house", TestContext::inOwnHouse)
					.onStart(ctx -> ctx.capture("My house before (cleared)", ctx.houseSummary())),
				ask("Look at Account > My house. Do the jewellery box, pool, altar, fairy ring and spirit tree match your "
					+ "house? (The portal nexus isn't scanned: it stays as you set it.)")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("My house after", ctx.houseSummary());
						if (!ctx.houseScanned())
						{
							ctx.problem("The house wasn't scanned");
						}
						if (!yes)
						{
							ctx.problem("My house didn't match the house after scanning");
						}
					}),
				doThis("Now visit someone else's house", TestContext::visitingHouse)
					.onStart(ctx -> ctx.remember("house", ctx.houseSummary()))
					.optional(),
				ask("Did Account > My house stay the same while you were in their house?")
					.onAnswer((ctx, yes) ->
					{
						final String before = ctx.recall("house");
						if (before == null)
						{
							ctx.capture("Visited someone else's house", "no");
							return;
						}
						final boolean same = ctx.houseSummary().equals(before);
						ctx.capture("My house unchanged while visiting", same);
						if (!yes || !same)
						{
							ctx.problem("My house changed while visiting someone else's house");
						}
					})
					.optional());
		}

		@Override
		public void keep(AccountSettings kept, AccountSettings during)
		{
			// What the scan found is the point of the test; without a scan the cleared house would lose the player's
			if (during.getPoh().getLastDetected() > 0)
			{
				kept.setPoh(during.getPoh());
			}
		}
	}
}
