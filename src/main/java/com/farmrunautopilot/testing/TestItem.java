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
	WEISS_FIRE(Tab.FARM, "Fire of Nourishment at Weiss",
		"If you've built the Fire of Nourishment, untick it in Account > Unlocks, then go to Weiss. It should "
			+ "tick itself once the fire is in view, and the Weiss herb patch should join your herb runs."),
	FORTIS_CHAMPION(Tab.FARM, "Civitas herbs and the Colosseum",
		"With \"Champion rank at the Fortis Colosseum\" ticked in Account > Unlocks, the Civitas herb patch "
			+ "should count as disease-free: no plant cure or protection asked for it.", 2),
	ATES_CHARGES(Tab.TRAVEL, "Pendant of Ates charges",
		"With the pendant on you, Settings > Debug > Detected should show the same charges as its Check. Teleport once "
			+ "and it should drop by one. Start a teleport and cancel it: it shouldn't change."),
	TALISMAN_CHARGES(Tab.TRAVEL, "Xeric's talisman charges",
		"Check the talisman. Settings > Debug > Detected should show the same number. Teleport once and it should drop "
			+ "by one. At 0 charges, Auto (best) should stop picking it."),
	KHARYRLL(Tab.TRAVEL, "Kharyrll Teleport highlight",
		"On the Ancient spellbook with Kharyrll Teleport planned for Port Phasmatys (and no tablet), the "
			+ "spell should be outlined in your spellbook on that step."),
	BUCKET_COMPOST(Tab.FARM, "Bottomless bucket at the tool leprechaun",
		"With a bottomless compost bucket that has compost in it, store it with any tool leprechaun. Settings > "
			+ "Debug > Detected should then show the bucket with the right compost and uses."),
	SPIRIT_TREES(Tab.ACCOUNT, "Planted spirit trees",
		"Untick your planted spirit tree in Account > Unlocks, then go to it (Port Sarim, Brimhaven or the "
			+ "Farming Guild). It should tick itself once the grown tree is in view."),
	HOT_AIR_BALLOONS(Tab.ACCOUNT, "Hot air balloon from the Grand Tree",
		"If you've done Enlightened Journey and unlocked the Grand Tree balloon route (60 Firemaking, 3 magic logs), Account > "
			+ "Unlocks should show \"Hot air balloon route to the Grand Tree unlocked (detected)\" ticked. A run with "
			+ "Gnome Stronghold and Taverley can then fly from the Grand Tree to Taverley with one normal log."),
	HOUSE_SCAN(Tab.ACCOUNT, "House scanning (beta)",
		"Tick \"Detect furniture when I enter my house\" in Account > My house, then enter your house: your "
			+ "jewellery box, pool, altar, fairy ring and spirit tree should fill in (the portal nexus isn't scanned). "
			+ "Then visit someone else's house: nothing of yours should change.", 2);

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
