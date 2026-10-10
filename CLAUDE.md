@AGENTS.md

# This plugin

## "I NEED YOUR HELP!" list

`src/main/java/com/farmrunautopilot/testing/TestItem.java` (shown by `ui/HelpWanted.java`) lists the features that couldn't be tried in development and need feedback, grouped by the sidebar tab they belong to. It is shown in Account > Testing & debug; every tab has an "I NEED YOUR HELP!" button that opens it. Each item has a title, full "how to try it" steps, and a guided test in `testing/GuidedTests.java`: settings it applies (the player's are backed up and put back), items to bring, steps it detects or asks Yes/No about, and values it captures for the pre-filled GitHub issue it opens at the end. In the dev client (RuneLite developer mode) each item also has a result to pick (Not tried yet / Good to go / Needs attention, plus a note for Needs attention); other players get a GitHub issues button instead. The user uses this list as their test checklist. Keep it current without being asked:

- **Add** an item (and its guided test) when you build or change a feature that the user can't test on their account (an item, unlock, quest or spellbook they don't have) or that relies on an UNVERIFIED game value: varbit, NPC/object ID, chat message wording, widget. Write full steps a player can follow: where to set it up, what to do, what they should see.
- **Remove** the item once the user confirms it works in game ("Good to go"), and drop the matching UNVERIFIED comment in the code at the same time.
- **Reword** an item when the feature it describes changes. Never rename an `Item` constant that has been released: its name keys the saved result.
- Put each item on the tab where the player would see the feature.
- Mention any change to the list when reporting back.

**Reading the results:** every change is logged at INFO as `Help list: <ITEM> = <result> (note: ...)`, and guided tests log `Guided test finished: <ITEM> = <result> [problems]`, followed in the dev client by `Guided test report: <ITEM>` with the full report (steps, problems, captured values). At the start of a session, and whenever the user says they've been testing, search the client log (`~/.runelite/logs/client.log`, and the dated `client_*.log` files) for `Help list:` and `Guided test`. The latest line per item wins. Then:

- **Needs attention:** ask the user about it, starting from their note, and fix it.
- **Good to go:** offer to remove the item and its UNVERIFIED comment.
