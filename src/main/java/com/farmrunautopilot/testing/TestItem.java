package com.farmrunautopilot.testing;

import lombok.Getter;

/**
 * A feature that couldn't be tried in development and needs feedback (the "I NEED YOUR HELP!" list), with
 * steps a player can follow by hand. Each also has a guided test ({@link GuidedTests}). The constant's name
 * keys its saved result and appears in logs and reports, so don't rename one once released. Remove an item
 * once it's confirmed (see CLAUDE.md).
 */
@Getter
public enum TestItem
{
	SPELLBOOK_SWAP(Tab.RUN, "Spellbook Swap",
		"On the Lunar spellbook (96 Magic, Dream Mentor done) with no house altar for the standard spellbook, start "
			+ "this test: it plans Catherby by Camelot Teleport, cast from runes. The Run tab should warn that you need "
			+ "Spellbook Swap, its runes should be on the bank's Farm run tab, and the spell should be outlined in your "
			+ "spellbook on that step."),
	SPELL_PLACES(Tab.RUN, "Spells' right-click places",
		"With the hard Kandarin diary (Camelot Teleport to Seers'), the hard Ardougne diary (Watchtower Teleport to Yanille) "
			+ "or the medium Varrock diary (Varrock Teleport to the Grand Exchange), build a run with the Seers' Village or "
			+ "Yanille hops patch. A stop should go by the spell's place, e.g. \"Camelot Teleport: Seers'\". In the spellbook "
			+ "the spell is outlined with \"Right\" over \"click\" under it, right-clicking it shows that place coloured, and "
			+ "picking it lands you where the route expects."),
	SEED_VAULT_SECTION(Tab.RUN, "Farm run section in the seed vault",
		"Build a run needing seeds your bank doesn't have but the Farming Guild's seed vault does, and open the vault: "
			+ "the button by its close button should flash, with the same outline round it as the bank's Farm run "
			+ "button. Clicking it shows a \"Farm Run Autopilot\" section at the top, with a \"/ N\" count under each "
			+ "seed, and a \"Farm Run Autopilot\" entry at the top of the list on the left that goes back to it. "
			+ "Clicking the button again hides both.", 4),
	VAULT_SEEDS_FOR_LATER(Tab.RUN, "Seed vault seeds for later in the run",
		"Pick \"Take all seeds at the start\" under Account > Storage sources, with a run too big for one inventory "
			+ "and seeds for after its bank visit only in the Farming Guild's seed vault. After Build run, Getting "
			+ "ready should start at the seed vault: take what it says, then at the bank press Deposit inventory (it's "
			+ "outlined) and take what the Farm run tab marks. Only then is the run ready, and at the bank visit the "
			+ "seeds for later come out of your bank.", 2),
	VAULT_BANK_AT_GUILD(Tab.RUN, "Banking at the Farming Guild",
		"Pick \"Bank at the Farming Guild\" under Account > Storage sources, with a run too big for one inventory and "
			+ "seeds for after its bank visit only in the seed vault. The run should bank at the Farming Guild: after "
			+ "the guild's own stop, or \"Bank at the Farming Guild\" with the way there. At its bank, once the Farm run "
			+ "tab is done, the step should say \"Take ... from the seed vault (next to the bank)\", and the vault's "
			+ "Farm Run Autopilot section should show only the seeds for the rest of the run."),
	STAFF_SWAPS(Tab.RUN, "Swapping staves on the way",
		"With two staves in your bank (e.g. a dramen staff and a lava battlestaff) and a run that casts teleports from "
			+ "runes, press Build run: when a second staff saves an inventory slot, the list has one staff to wield at the "
			+ "bank and another to carry. During the run, before the stop that needs the other staff, the step should say "
			+ "\"Wield your ...\" with that staff outlined in blue, and move on once it's wielded."),
	ALLOTMENT_PAYMENT(Tab.RUN, "Paying for one allotment",
		"At the farm south of Falador, with potato seeds and 4 buckets of compost, set Farm > Protection > Allotment "
			+ "patches to Pay gardener and run just that farm's allotments. After planting each allotment the step should "
			+ "say \"Pay Elstan for the north-west allotment: 2 x Compost\" (then the south-east one), and move on once "
			+ "you've paid for it."),
	NEW_PATCH_POINTS(Tab.RUN, "Where the new patches are",
		"Tick Allotments, Flowers or Hops on the Run tab and stand by any allotment set or hops patch you can use. "
			+ "The patch should be outlined when it's the next step, and the hint arrow should point at it, not a few "
			+ "tiles away."),
	ALLOTMENT_FLOWER_PROTECTION(Tab.FARM, "Flowers protecting allotments",
		"At the farm south of Falador, with a marigold seed and potato seeds, leave Farm > Protection > Allotment "
			+ "patches on Flower and run just that farm's allotments. The steps should start at the flower patch with "
			+ "the marigold, never ask you to pay Elstan, and leave the grown marigold alone on your next run."),
	FORTIS_CHAMPION(Tab.FARM, "Civitas herbs and the Colosseum",
		"With \"Champion rank at the Fortis Colosseum\" ticked in Account > Unlocks, the Civitas herb patch "
			+ "should count as disease-free: no plant cure or protection asked for it.", 2),
	ICY_BASALT(Tab.TRAVEL, "Icy basalt to Weiss",
		"With Making Friends with My Arm done, the Fire of Nourishment built at Weiss and an icy basalt in your bank, "
			+ "build a run with Weiss's herb patch. Its stop should say \"Icy basalt\". Break the basalt when the step "
			+ "outlines it (it's used up): you should land by the herb patch, outlined as the next step."),
	HOUSE_BY_TREE(Tab.TRAVEL, "Spirit tree into your house",
		"With a spirit tree planted in your house, plan a run where a stop is just after one with a spirit tree "
			+ "nearby (e.g. the Farming Guild, then Hosidius with your house portal there). The stop should say "
			+ "\"Spirit tree to your house, then...\", and the guide should move on once you're in the house and again "
			+ "once you've left it."),
	HOUSE_TURNED_OFF(Tab.TRAVEL, "Turning your house off",
		"With your house set up in Account > My house, build a run whose route goes through it (a stop says "
			+ "\"House ...\" or \"Spirit tree to your house\"). Untick House under Travel > Ways to travel: every stop "
			+ "should get another way, none of them through your house. Tick it again and the stops through your house "
			+ "should come back."),
	BUCKET_COMPOST(Tab.FARM, "Bottomless bucket at the tool leprechaun",
		"Most wanted: a bottomless compost bucket filled with plain compost or supercompost (ultracompost is "
			+ "confirmed). Right-click the bucket and pick Check, then store it with any tool leprechaun. Settings > "
			+ "Debug > Detected should then show the bucket with the same compost and uses as the Check said.", 2),
	SPIRIT_TREES(Tab.ACCOUNT, "Planted spirit trees",
		"Untick your planted spirit tree in Account > Unlocks, then go to it (Port Sarim, Brimhaven or the "
			+ "Farming Guild). It should tick itself once the grown tree is in view.", 2),
	HOUSE_SCAN(Tab.ACCOUNT, "House scanning (beta)",
		"Tick \"Detect furniture when I enter my house\" in Account > My house, then enter your house: your "
			+ "jewellery box, pool, altar, fairy ring and spirit tree should fill in (the portal nexus isn't scanned), and My house should say when it was last "
			+ "scanned, even if your house has none of them. "
			+ "Then visit someone else's house: nothing of yours should change.", 4);

	/** The sidebar tab a feature belongs to. */
	@Getter
	public enum Tab
	{
		RUN("Run"), FARM("Farm"), TRAVEL("Travel"), ACCOUNT("Account");

		private final String label;

		Tab(String label)
		{
			this.label = label;
		}
	}

	private final Tab tab;
	private final String title;
	private final String howToTry;
	/**
	 * Bump this when a fix means earlier results no longer count, so players who already ran the test are
	 * asked again.
	 */
	private final int revision;

	TestItem(Tab tab, String title, String howToTry)
	{
		this(tab, title, howToTry, 1);
	}

	TestItem(Tab tab, String title, String howToTry, int revision)
	{
		this.tab = tab;
		this.title = title;
		this.howToTry = howToTry;
		this.revision = revision;
	}

	/** Where this client's result for the current revision is saved. */
	public String key()
	{
		return "help." + name() + (revision > 1 ? ".v" + revision : "");
	}
}
