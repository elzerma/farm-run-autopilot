package com.farmrunautopilot.testing;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.access.PohDetector;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.settings.AccountSettings;
import com.farmrunautopilot.settings.Compost;
import com.farmrunautopilot.settings.RunConfig;
import com.farmrunautopilot.supply.Holdings;
import com.farmrunautopilot.supply.ItemChargeTracker;
import com.farmrunautopilot.supply.SupplyLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Skill;
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
		TESTS.put(TestItem.FORTIS_CHAMPION, new FortisChampion());
		TESTS.put(TestItem.ATES_CHARGES, new AtesCharges());
		TESTS.put(TestItem.SPIRIT_TREES, new SpiritTrees());
		TESTS.put(TestItem.BUCKET_COMPOST, new BucketCompost());
		TESTS.put(TestItem.HOUSE_BY_TREE, new HouseByTree());
		TESTS.put(TestItem.HOUSE_SCAN, new HouseScan());
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

	// The tests

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
	 * The Farm run section at the top of the seed vault (issue #8): where the game puts everything in the vault was
	 * read from its scripts, and moving the vault's widgets hasn't been tried.
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
				// Without them in the vault there's no section to show, which proves nothing
				ask("Are the seeds or saplings the bank's Farm run tab lists in the Farming Guild's seed vault, and not in "
					+ "your inventory?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Supply list", supplyNames(ctx));
						if (!yes)
						{
							ctx.cannotTest("the seeds or saplings for the Farming Guild's patches in its seed vault");
						}
					}),
				doThis("Take everything else the bank's Farm run tab lists, leaving those seeds in the vault, then go to the "
					+ "Farming Guild until the guide says to take them from the seed vault",
					ctx -> !ctx.seedVaultItems().isEmpty())
					.onDone(ctx -> ctx.capture("Step", ctx.runView().getInstruction()))
					.orAskAfter(DO_TICKS, "The guide hasn't said to take anything from the seed vault. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Step", ctx.runView().getInstruction());
						ctx.problem("The guide didn't say to take anything from the seed vault");
					}),
				doThis("Open the seed vault", ctx -> ctx.seedVault().isLaidOut())
					.onDone(ctx ->
					{
						captureVault(ctx, "Seed vault when opened");
						if (ctx.seedVault().shownCount() == 0)
						{
							ctx.problem("No Farm run section in the seed vault");
						}
					})
					.orAskAfter(DO_TICKS, "The plugin hasn't seen the seed vault open. Is it open?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault when asked");
						ctx.problem("The plugin didn't see the seed vault laid out");
					}),
				ask("Is there a \"Farm run\" section at the top of the vault, with just the seeds the guide asked for?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault when answered");
						if (!yes)
						{
							ctx.problem("Answered No: the Farm run section wasn't at the top with just those seeds");
						}
					}),
				ask("Try to drag one of the vault's seeds to another place. Does it stay where it was?"),
				ask("Withdraw one of the seeds in the Farm run section. Did it go into your inventory?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault after withdrawing");
						if (!yes)
						{
							ctx.problem("Answered No: withdrawing from the Farm run section didn't work");
						}
					}),
				ask("Scroll down to the bottom of the vault, then click a few categories on the left. Is everything else "
					+ "there, with nothing cut off or overlapping?"),
				ask("Take the rest of what the guide asked for. Did the section go, leaving the vault as usual?")
					.onAnswer((ctx, yes) ->
					{
						captureVault(ctx, "Seed vault at the end");
						if (!yes)
						{
							ctx.problem("Answered No: the vault didn't go back to normal");
						}
					})
					.optional());
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
