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
	CIVITAS_QUETZAL(Tab.RUN, "Quetzal after the Civitas teleport",
		"Set Travel > Civitas illa Fortis (Ortus Farm) to Civitas illa Fortis Teleport and start a run with the "
			+ "Civitas herb patch. Once you land in the city, the step should say \"Take the quetzal to the "
			+ "Hunter Guild\" and the quetzal nearby should be outlined. After the ride, the step should switch "
			+ "to walking to the patch."),
	SPELLBOOK_SWAP(Tab.RUN, "Spellbook Swap",
		"On the Lunar spellbook (96 Magic, Dream Mentor done) with no house altar for the other spellbook, "
			+ "plan a stop whose teleport is from another spellbook and that you have no tablet for. The Run "
			+ "tab should warn that you need Spellbook Swap, its runes should be on the supply list, and the "
			+ "spell should be outlined in your spellbook on that step."),
	WEISS_FIRE(Tab.FARM, "Fire of Nourishment at Weiss",
		"If you've built the Fire of Nourishment, untick it in Account > Unlocks, then go to Weiss. It should "
			+ "tick itself once the fire is in view, and the Weiss herb patch should join your herb runs."),
	FORTIS_CHAMPION(Tab.FARM, "Civitas herbs and the Colosseum",
		"With \"Champion rank at the Fortis Colosseum\" ticked in Account > Unlocks, the Civitas herb patch "
			+ "should count as disease-free: no plant cure or protection asked for it."),
	ATES_CHARGES(Tab.TRAVEL, "Pendant of Ates charges",
		"With the pendant on you, Account > Detected should show the same charges as its Check. Teleport once "
			+ "and it should drop by one. Start a teleport and cancel it: it shouldn't change."),
	CHAT_CHARGES(Tab.TRAVEL, "Xeric's talisman and quetzal whistle charges",
		"Check the talisman or whistle. Account > Detected should show the same number. Teleport once and it "
			+ "should drop by one. At 0 charges, Auto (best) should stop picking it."),
	DAILY_TELEPORTS(Tab.TRAVEL, "Teleports left today",
		"With an Ardougne cloak 2 or 3, or Explorer's ring 2 or 3: Account > Detected has a \"Daily teleports "
			+ "used (checking)\" line. Use a farm or cabbage teleport and note how its numbers change. Once "
			+ "the day's uses are gone, Auto (best) should stop picking it."),
	KHARYRLL(Tab.TRAVEL, "Kharyrll Teleport highlight",
		"On the Ancient spellbook with Kharyrll Teleport planned for Port Phasmatys (and no tablet), the "
			+ "spell should be outlined in your spellbook on that step."),
	VARBIT_UNLOCKS(Tab.ACCOUNT, "Kastori quetzal and statues of Ates",
		"If you've built the Kastori quetzal landing site or activated a statue of Ates (Nemus Retreat, north "
			+ "of Kastori), Account > Unlocks should show it ticked with \"(detected)\". An unticked box means "
			+ "it wasn't detected."),
	SPIRIT_TREES(Tab.ACCOUNT, "Planted spirit trees",
		"Untick your planted spirit tree in Account > Unlocks, then go to it (Port Sarim, Brimhaven or the "
			+ "Farming Guild). It should tick itself once the grown tree is in view."),
	HOUSE_SCAN(Tab.ACCOUNT, "House scanning (beta)",
		"Tick \"Detect furniture when I enter my house\" in Account > My house, then enter your house: your "
			+ "portal nexus, jewellery box, pool and altar should fill in. Then visit someone else's house: "
			+ "nothing of yours should change.");

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

	TestItem(Tab tab, String title, String howToTry)
	{
		this.tab = tab;
		this.title = title;
		this.howToTry = howToTry;
	}

	/** Where the developer's result is saved. */
	public String key()
	{
		return "help." + name();
	}
}
