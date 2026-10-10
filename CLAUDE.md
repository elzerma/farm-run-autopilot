@AGENTS.md

# This plugin

## "I NEED YOUR HELP!" list

`src/main/java/com/farmrunautopilot/testing/TestItem.java` (shown by `ui/HelpWanted.java`) lists the features that couldn't be tried in development and need feedback, grouped by the sidebar tab they belong to. It is shown in Account > Testing & debug; every tab has an "I NEED YOUR HELP!" button that opens it. Each item has a title, full "how to try it" steps, and a guided test in `testing/GuidedTests.java`: settings it applies (the player's are backed up and put back), items to bring, steps it detects or asks Yes/No about, and values it captures for the report. When a test finishes:

- **Dev client (RuneLite developer mode, i.e. the user):** the full report is logged as `Guided test report: <ITEM>`, a one-line summary (`Guided test finished: <ITEM> = <result> [problems] (full report in the client log)`) is copied to the clipboard, and a game chat message says so. The user pastes that summary to you.
- **Everyone else:** the Run tab box ends with an "Open GitHub issues with prefilled test results" button (never opened automatically) and how to submit it; the help list explains the whole process to players.

The user uses this list as their test checklist. Keep it current without being asked:

- **Add** an item (and its guided test) when you build or change a feature that the user can't test on their account (an item, unlock, quest or spellbook they don't have) or that relies on an UNVERIFIED game value: varbit, NPC/object ID, chat message wording, widget. Write full steps a player can follow: where to set it up, what to do, what they should see.
- **Remove** the item, its guided test and the matching UNVERIFIED comment in the code once its test passes your own reading of the report (the steps and captured values, not only the user's Yes answers). Don't ask first; just say it was removed.
- **Reword** an item when the feature it describes changes. Never rename a `TestItem` constant that has been released: its name keys the saved result. A test someone has run (pass or not) is greyed out for them and not asked again; when a fix means earlier results no longer count, bump the item's `revision` so players are asked again.
- Put each item on the tab where the player would see the feature.
- Mention any change to the list when reporting back.

**Reading the results:** when the user pastes a `Guided test finished:` line or says they've been testing, find the matching `Guided test report:` in the dev client's log: the output file of the background `./gradlew run` task, or `~/.runelite/logs/client.log` and the dated `client_*.log` files. Then:

- **Passed:** remove the item as above.
- **Needs attention:** work out the cause from the captured values, fix it, and ask the user to run the test again.
