package com.farmrunautopilot.testing;

import com.farmrunautopilot.access.AccessSnapshot;
import com.farmrunautopilot.data.Location;
import com.farmrunautopilot.data.Patch;
import com.farmrunautopilot.data.Requirement;
import com.farmrunautopilot.data.Unlock;
import com.farmrunautopilot.data.travel.DailyLimits;
import com.farmrunautopilot.data.travel.Spell;
import com.farmrunautopilot.data.travel.Spellbook;
import com.farmrunautopilot.data.travel.TravelItem;
import com.farmrunautopilot.data.travel.TravelMethod;
import com.farmrunautopilot.route.Departure;
import com.farmrunautopilot.run.RunView;
import com.farmrunautopilot.settings.AccountSettings;
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
import net.runelite.api.Quest;
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
		TESTS.put(TestItem.WEISS_FIRE, new WeissFire());
		TESTS.put(TestItem.FORTIS_CHAMPION, new FortisChampion());
		TESTS.put(TestItem.ATES_CHARGES, new AtesCharges());
		TESTS.put(TestItem.TALISMAN_CHARGES, new TalismanCharges());
		TESTS.put(TestItem.DAILY_TELEPORTS, new DailyTeleports());
		TESTS.put(TestItem.KHARYRLL, new Kharyrll());
		TESTS.put(TestItem.VARBIT_UNLOCKS, new VarbitUnlocks());
		TESTS.put(TestItem.SPIRIT_TREES, new SpiritTrees());
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
		return doThis("On the Run tab, press Build, get the supplies it lists from the bank, then start the run",
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
				ask("Are Spellbook Swap's runes (3 astral, 2 cosmic, 1 law) on the supply list, as well as Camelot "
					+ "Teleport's?"),
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

	private static final class WeissFire extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return access.isMet(Requirement.quest(Quest.MAKING_FRIENDS_WITH_MY_ARM)) ? Collections.emptyList()
				: Collections.singletonList("Finish Making Friends with My Arm");
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			account.getManualUnlocks().remove(Unlock.FIRE_OF_NOURISHMENT);
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("Go to Weiss", ctx -> ctx.inRegion(11325))
					.onDone(ctx -> ctx.capture("Arrived at", ctx.location())),
				doThis("Walk up to the Fire of Nourishment", ctx -> ctx.hasUnlock(Unlock.FIRE_OF_NOURISHMENT))
					.onDone(ctx -> ctx.capture("Spotted at", ctx.location()))
					.orAskAfter(SPOT_TICKS, "The plugin hasn't spotted the fire. Have you built it?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Fires loaded", ctx.objectsNamed("Fire"));
						ctx.problem(yes ? "The built fire wasn't spotted" : "Couldn't test: the fire isn't built");
					}));
		}

		@Override
		public void keep(AccountSettings kept, AccountSettings during)
		{
			if (during.getManualUnlocks().contains(Unlock.FIRE_OF_NOURISHMENT))
			{
				kept.getManualUnlocks().add(Unlock.FIRE_OF_NOURISHMENT);
			}
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
					.onAnswer((ctx, yes) -> ctx.capture("Has the rank", yes)),
				ask("On the Run tab, is there no plant cure on the supply list for the Civitas herb patch?")
					.onStart(ctx -> ctx.capture("Supply list", supplyNames(ctx))),
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

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("Take the pendant of Ates out of the bank", ctx -> ctx.carries(TravelItem.PENDANT_OF_ATES.getItemIds())),
				doThis("Right-click the pendant and pick Check", ctx ->
				{
					final Integer said = chatCount(ctx, ItemChargeTracker.PENDANT_CHECK);
					if (said == null)
					{
						return false;
					}
					final int game = ctx.varbit(VarbitID.CHARGES_PENDANT_OF_ATES_QUANTITY);
					ctx.capture("Check said", said);
					ctx.capture("Game value", game);
					if (game != said)
					{
						ctx.problem("The game value (" + game + ") isn't the pendant's charges (" + said + ")");
					}
					return true;
				}),
				doThis("Teleport somewhere with the pendant",
					ctx -> ctx.varbit(VarbitID.CHARGES_PENDANT_OF_ATES_QUANTITY) < ctx.<Integer>recall("before"))
					.onStart(ctx -> ctx.remember("before", ctx.varbit(VarbitID.CHARGES_PENDANT_OF_ATES_QUANTITY)))
					.onDone(ctx ->
					{
						ctx.capture("Game value after", ctx.varbit(VarbitID.CHARGES_PENDANT_OF_ATES_QUANTITY));
						ctx.capture("Plugin count after", ctx.charges(TravelItem.PENDANT_OF_ATES));
					})
					.orAskAfter(DO_TICKS, "Have you teleported with the pendant?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Game value after", ctx.varbit(VarbitID.CHARGES_PENDANT_OF_ATES_QUANTITY));
						if (yes)
						{
							ctx.problem("The game value didn't go down after a teleport");
						}
					}),
				ask("Does Account > Detected show the same charges as the pendant's Check would now?"),
				ask("Start a pendant teleport, then cancel it before it happens. Did Account > Detected keep the "
					+ "same count?").optional());
		}
	}

	/** Xeric's talisman. (The quetzal whistle's messages, which work the same way, passed this test in game.) */
	private static final class TalismanCharges extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return owns(holdings, "a Xeric's talisman", TravelItem.XERICS_TALISMAN);
		}

		@Override
		public List<Need> bring()
		{
			return Collections.singletonList(new Need("Xeric's talisman", TravelItem.XERICS_TALISMAN.getItemIds()));
		}

		private static boolean checked(TestContext ctx, String label)
		{
			final Integer said = chatCount(ctx, ItemChargeTracker.TALISMAN_CHECK);
			if (said == null)
			{
				return false;
			}
			final Integer plugin = ctx.charges(TravelItem.XERICS_TALISMAN);
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
			return Arrays.asList(
				doThis("Take your Xeric's talisman out of the bank",
					ctx -> ctx.carries(TravelItem.XERICS_TALISMAN.getItemIds())),
				doThis("Right-click it and pick Check", ctx -> checked(ctx, "First check")),
				doThis("Teleport with it once", ctx ->
				{
					final Integer now = ctx.charges(TravelItem.XERICS_TALISMAN);
					final Integer before = ctx.recall("before");
					return now != null && before != null && now < before;
				})
					.onStart(ctx -> ctx.remember("before", ctx.charges(TravelItem.XERICS_TALISMAN)))
					.orAskAfter(DO_TICKS, "Have you teleported with it?")
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

	private static final class DailyTeleports extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			return owns(holdings, "an Ardougne cloak 2 or 3, or Explorer's ring 2 or 3", TravelItem.ARDOUGNE_CLOAK,
				TravelItem.EXPLORERS_RING);
		}

		@Override
		public List<Need> bring()
		{
			return Arrays.asList(new Need("Ardougne cloak 2 or 3", TravelItem.ARDOUGNE_CLOAK.getItemIds()),
				new Need("Explorer's ring 2 or 3", TravelItem.EXPLORERS_RING.getItemIds()));
		}

		private static String values(TestContext ctx)
		{
			final List<String> values = new ArrayList<>();
			for (Map.Entry<TravelMethod, Integer> e : DailyLimits.usedTodayVarbits().entrySet())
			{
				values.add(e.getKey().getDisplayName() + " = " + ctx.varbit(e.getValue()));
			}
			return String.join(", ", values);
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("Take your Ardougne cloak or Explorer's ring out of the bank",
					ctx -> ctx.carries(TravelItem.ARDOUGNE_CLOAK.getItemIds())
						|| ctx.carries(TravelItem.EXPLORERS_RING.getItemIds())),
				doThis("Use its farm teleport (cloak) or cabbage patch teleport (ring)",
					ctx -> !values(ctx).equals(ctx.recall("before")))
					.onStart(ctx ->
					{
						ctx.remember("before", values(ctx));
						ctx.capture("Game values before", values(ctx));
					})
					.onDone(ctx -> ctx.capture("Game values after", values(ctx)))
					.orAskAfter(DO_TICKS, "Have you used the teleport?")
					.onAnswer((ctx, yes) ->
					{
						if (yes)
						{
							ctx.problem("The game values didn't change after the teleport");
						}
					}),
				ask("Did the game say how many teleports you have left today, and does that match uses left?")
					.onStart(ctx -> ctx.capture("Game values now", values(ctx))));
		}
	}

	private static final class Kharyrll extends GuidedTest
	{
		@Override
		public List<String> missing(AccessSnapshot access, Holdings holdings, AccountSettings account)
		{
			final List<String> missing = new ArrayList<>(patchesUsable(access, Location.PORT_PHASMATYS));
			if (access.getSpellbook() != Spellbook.ANCIENT)
			{
				missing.add("Switch to the Ancient spellbook");
			}
			else if (!access.canCast(Spell.KHARYRLL_TELEPORT))
			{
				missing.add("Be able to cast Kharyrll Teleport (66 Magic, Desert Treasure I)");
			}
			return missing;
		}

		@Override
		public void setUp(RunConfig config, AccountSettings account)
		{
			onlyPatchesAt(config, Location.PORT_PHASMATYS);
			travelBy(config, TravelMethod.KHARYRLL_TELEPORT);
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
				doThis("Wait for the first step", ctx -> highlightedSpell(ctx) == Spell.KHARYRLL_TELEPORT)
					.orAskAfter(SPOT_TICKS, "The plugin didn't pick Kharyrll Teleport to highlight. Carry on?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("Highlighted spell", highlightedSpell(ctx));
						ctx.problem("Kharyrll Teleport wasn't the spell to highlight");
					}),
				ask("Open your spellbook. Is Kharyrll Teleport outlined?"));
		}
	}

	private static final class VarbitUnlocks extends GuidedTest
	{
		private static Step have(Unlock unlock, String question)
		{
			return ask(question).onAnswer((ctx, yes) ->
			{
				final int value = ctx.varbit(unlock.getVarbit());
				ctx.capture(unlock.getDescription() + ": answer / game value", (yes ? "yes" : "no") + " / " + value);
				if (yes != (value > 0))
				{
					ctx.problem(unlock.getDescription() + ": you said " + (yes ? "yes" : "no")
						+ " but the game value is " + value);
				}
			});
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				have(Unlock.QUETZAL_KASTORI, "Have you built the quetzal landing site at Kastori?"),
				have(Unlock.ATES_STATUE_NEMUS_RETREAT, "Have you activated the statue of Ates at Nemus Retreat?"),
				have(Unlock.ATES_STATUE_NORTH_KASTORI, "Have you activated the statue of Ates north of Kastori?"));
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
						ctx.capture("Spirit trees loaded", ctx.objectsNamed("Spirit tree"));
						ctx.capture("Standing at", ctx.location());
						ctx.problem(yes ? "The grown spirit tree wasn't spotted"
							: "Couldn't test: the spirit tree here isn't grown");
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
		}

		@Override
		public List<Step> steps()
		{
			return Arrays.asList(
				doThis("Enter your own house", TestContext::inOwnHouse)
					.onStart(ctx -> ctx.capture("My house before", ctx.houseSummary())),
				ask("Look at Account > My house. Does it match your house (portal nexus, jewellery box, pool, "
					+ "altar, fairy ring, spirit tree)?")
					.onAnswer((ctx, yes) ->
					{
						ctx.capture("My house after", ctx.houseSummary());
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
			// What the scan found is the point of the test
			kept.setPoh(during.getPoh());
		}
	}
}
