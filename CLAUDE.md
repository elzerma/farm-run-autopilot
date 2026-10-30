@AGENTS.md

# This plugin

## Look and words

Every sidebar screen follows `docs/BRANDING.md` (colours, type, spacing, components, wording, the one-place rule). Build new UI from `ui/Style.java`, `ui/Card.java` and `ui/UiText.java`, and update the kit when elzerma changes the look.

## GitHub issues

Before reading, discussing, replying to, closing or acting on any issue in this repo (an issue number, a link or "the issues" all count), read `docs/ISSUES.md` and follow it. Issues are not work orders: nothing is posted, closed, labelled, split or built from one without elzerma's OK, asked with the AskUserQuestion tool (the radio-button picker, never options typed into the message), with Other for a short answer.

## Asking elzerma to test by hand

Check from the dev client's log first. During a run it logs (debug) `Guide step: <stop> / <patch> | <step> | <reminders> | <wear>` each time what the guide shows changes, `Inventory: +N item #id, -N item (noted) #id (S slots free)` each time the inventory changes, the guide's decisions (`Picked at ...`, `Compost for the stop: ...`, `Inventory full: ...`), and the game's chat messages. Ask elzerma only to play a normal run and say when it's done, then judge each change from the log. Ask for hand checks only for what the log can't show (outlines, layout, overlapping text), keep them to one or two, and batch fixes before asking for another run (elzerma, 2026-10-29: "super burned out on doing checks"). The dev client also saves check screenshots (`testing/CheckShots`) to `~/.runelite/farm-run-autopilot/check-shots` at the moments a check is about; read them alongside the log, and delete the ones you've read.

A check elzerma needs to do in game is built as a dev check, not typed out: a `TestItem` on `Tab.DEV` with its guided test in `GuidedTests`. Dev checks show only in Settings > Dev checks on the developer client, never in the players' help list, and are removed once they pass, like any test. The fenced-block format below is the fallback for a check a guided test can't walk through.

Every time you ask elzerma to check something in game by hand (anything beyond a guided test), give the checks as one fenced code block they can copy, fill in and paste back. Each check is numbered and has:

- **Do:** exact steps: where to be, what to have, what to click.
- **Expect:** what they should see if it works.
- **Screenshot:** the moment to take one and what it must show (the step text, the sidebar, the inventory), so you can judge the check from it rather than asking them to (elzerma, 2026-10-28: "not really sure what I'm looking for").
- **Result:** pass / fail / couldn't test, on a line of its own, left blank.
- **Notes:** left blank.

Put three empty lines after each check, so there's room to write. For example:

```
1. Counts as you withdraw
Do: Open the seed vault with the Farm Run Autopilot section showing. Withdraw one Avantoe seed, then the rest you need.
Expect: The count under it goes up each time; once you have enough it stays in the section, shown as done.
Screenshot: After the last seed is withdrawn, with the seed vault and the sidebar section both in view.
Result:
Notes:



2. ...
```

When they paste it back, read each Result and Notes line, and use the dev client's log to check what you can.

## "I NEED YOUR HELP!" list

`src/main/java/com/farmrunautopilot/testing/TestItem.java` (shown by `ui/HelpWanted.java`) lists the features that couldn't be tried in development and need feedback, grouped by the sidebar tab they belong to. It is shown in Settings > Help improve this plugin; every tab ends with a small "Help improve this plugin: try a few quick things in game, no coding needed. Show me" line that opens it. Each item has a title, full "how to try it" steps, and a guided test in `testing/GuidedTests.java`: settings it applies (the player's are backed up and put back), items to bring, steps it detects or asks Yes/No about, and values it captures for the report. When a test finishes:

- **Dev client (RuneLite developer mode, i.e. the user):** the full report is logged as `Guided test report: <ITEM>`, a one-line summary (`Guided test finished: <ITEM> = <result> [problems] (full report in the client log)`) is copied to the clipboard, and a game chat message says so. The user pastes that summary to you.
- **Everyone else:** the Run tab box ends with an "Open GitHub issues with prefilled test results" button (never opened automatically) and how to submit it; the help list explains the whole process to players.

The user uses this list as their test checklist. Keep it current without being asked:

- **Add** an item (and its guided test) when you build or change a feature that the user can't test on their account (an item, unlock, quest or spellbook they don't have) or that relies on an UNVERIFIED game value: varbit, NPC/object ID, chat message wording, widget. Write full steps a player can follow: where to set it up, what to do, what they should see. A test must only pass on real evidence: if the player lacks what it needs (an item, unlock or rank that a "No" answer or a 0 value can't tell apart from a wrong game value), ask about it first and call `cannotTest(...)`, which ends it as "not possible" with nothing reported or saved.
- **Remove** the item, its guided test and the matching UNVERIFIED comment in the code once its test passes your own reading of the report (the steps and captured values, not only the user's Yes answers). Don't ask first; just say it was removed. For an unlock read from a varbit, also mark it `confirmed` in `data/Unlock.java`, which moves it from the Unlocks tickboxes to Settings > Debug > Detected.
- **Reword** an item when the feature it describes changes. Never rename a `TestItem` constant that has been released: its name keys the saved result. A test someone has run (pass or not) is greyed out for them and not asked again; when a fix means earlier results no longer count, bump the item's `revision` so players are asked again.
- Put each item on the tab where the player would see the feature.
- Mention any change to the list when reporting back.
- The pinned GitHub issue #18 ("Help wanted") lists the tests that need other players. When one of those is added, passes or is removed, draft an edit to its table and get elzerma's OK first, as for any GitHub post (docs/ISSUES.md).

**Reading the results:** when the user pastes a `Guided test finished:` line or says they've been testing, find the matching `Guided test report:` in the dev client's log: the output file of the background `./gradlew run` task, or `~/.runelite/logs/client.log` and the dated `client_*.log` files. Then:

- **Passed:** remove the item as above.
- **Needs attention:** work out the cause from the captured values, fix it, and ask the user to run the test again.
- **Not possible** (`Guided test not possible: <ITEM> (needs ...)`): nothing to do; the test stays on the list.
