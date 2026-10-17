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
		"On the Lunar spellbook (96 Magic, Dream Mentor done) with no house altar for the other spellbook, "
			+ "plan a stop whose teleport is from another spellbook and that you have no tablet for. The Run "
			+ "tab should warn that you need Spellbook Swap, its runes should be on the bank's Farm run tab, and the "
			+ "spell should be outlined in your spellbook on that step."),
	SEED_VAULT_SECTION(Tab.RUN, "Farm run section in the seed vault",
		"Build a run and open the Farming Guild's seed vault: a \"Farm run\" section at the top should show every seed "
			+ "and sapling the run plants, with a \"/ N\" count under each like the bank's Farm run tab, and stay there, "
			+ "ticked, once you've taken them. Withdrawing from it works as usual, dragging a seed and letting go "
			+ "somewhere else does nothing, and the rest of the vault scrolls and looks as usual.", 2),
	FORTIS_CHAMPION(Tab.FARM, "Civitas herbs and the Colosseum",
		"With \"Champion rank at the Fortis Colosseum\" ticked in Account > Unlocks, the Civitas herb patch "
			+ "should count as disease-free: no plant cure or protection asked for it.", 2),
	ATES_CHARGES(Tab.TRAVEL, "Pendant of Ates charges",
		"Check the pendant. Settings > Debug > Detected should show the same number. Teleport once and it should "
			+ "drop by one, then match a second Check.", 2),
	HOUSE_BY_TREE(Tab.TRAVEL, "Spirit tree into your house",
		"With a spirit tree planted in your house, plan a run where a stop is just after one with a spirit tree "
			+ "nearby (e.g. the Farming Guild, then Hosidius with your house portal there). The stop should say "
			+ "\"Spirit tree to your house, then...\", and the guide should move on once you're in the house and again "
			+ "once you've left it."),
	BUCKET_COMPOST(Tab.FARM, "Bottomless bucket at the tool leprechaun",
		"With a bottomless compost bucket that has compost in it, store it with any tool leprechaun. Settings > "
			+ "Debug > Detected should then show the bucket with the right compost and uses."),
	SPIRIT_TREES(Tab.ACCOUNT, "Planted spirit trees",
		"Untick your planted spirit tree in Account > Unlocks, then go to it (Port Sarim, Brimhaven or the "
			+ "Farming Guild). It should tick itself once the grown tree is in view."),
	HOUSE_SCAN(Tab.ACCOUNT, "House scanning (beta)",
		"Tick \"Detect furniture when I enter my house\" in Account > My house, then enter your house: your "
			+ "jewellery box, pool, altar, fairy ring and spirit tree should fill in (the portal nexus isn't scanned). "
			+ "Then visit someone else's house: nothing of yours should change.", 3);

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
