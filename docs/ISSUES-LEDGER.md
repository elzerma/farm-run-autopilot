# Issue ledger

What was said in each issue, how it was judged, and what elzerma decided. The process is in [ISSUES.md](ISSUES.md). Look up an issue by searching for `#N`.

Each entry has: **Kind**, **Status**, **Reviewed through** (date and author of the last comment read), the comment table, and the decisions.

Issues #1 to #5 were answered and closed on 2026-10-15, before this ledger began.

## #5 preparation tab from last update gone (closed)

**Kind:** Question · **Status:** closed 2026-10-15 · **Reviewed through:** 2026-10-16 elzerma

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| frazzorr | The bank's farming icon is not very obvious | `bank/FarmBankTabButton.java` | It already flashes while the run needs the bank (201a725). A stronger cue may still help new players. | **Explore**: a coloured outline on the button, or an opt-in setting that opens the Farm run tab when the bank opens (needs a Plugin Hub rules check). No issue yet. |
| elwakka | Seeds are in the seed vault; a farm tab there too? | | Moved to #8 by elzerma | **Split** (done, #8) |

## #6 Seed stabber not needed

**Kind:** Bug · **Status:** open, fixed in ebe46f2, passed in game 2026-10-16 (value 1 once learned); reply posted, left open until release · **Reviewed through:** 2026-10-16 elzerma (fix reply)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| elwakka | With Strong barbarian fingers no seed dibber is needed, but the plugin asks for one | `SupplyCalculator.java:329`, `PatchGuide.java:455` | True: the unlock isn't handled anywhere. `VarbitID.BRUT_FARMING_PLANTING` exists; its values aren't checked in game. | **Fix**: an unlock read from that varbit, with a manual tick box until confirmed; skip the dibber line and reminder when met; a guided test. |
| elzerma | Will be addressed next release; the dibber counts against the leprechaun's stock meanwhile | `supply/LeprechaunItem.java:20` | Correct | Chatter |

## #7 Equiping magical secateurs not needed

**Kind:** Bug + ideas · **Status:** closed as completed on the 62f3070 release; fixed in 96c706a and confirmed in game by elzerma 2026-10-16 (lava staff suggested; the fairy-ring stops came after the mid-run bank, where the dramen staff goes on); fix reply posted 2026-10-16 (draft B, approved); triage reply posted, ideas split to #16 (house) and #17 (report a problem); reply on the earth rune line posted 2026-10-23 (review of 2026-10-21, draft N) · **Reviewed through:** 2026-10-23 elzerma (draft N)

**2026-10-21 review:** elwakka commented after the close (2026-10-17 20:19) with a screenshot: the earth rune line showed "/ 1" with a green tick under a lava staff. The staff was counted, but the ticked line read as something to bring. b84eadc (the same day) drops rune lines a staff or tome covers. **Already done (next update)**; draft N posted, elzerma's OK.

**Fix (96c706a):** `SupplyCalculator.toWear` ranks every equipment slot in one pass, now with a weapon slot: needed-worn first (fairy-ring staff without the Elite Lumbridge diary), then whatever saves most (a staff or tome per rune type of the run it covers, a teleport item one slot), magic secateurs last. A staff the plan wields counts for its runes even while it's still in the bank; one that gives up its slot stops counting. The wiki says every Ardougne cloak tier teleports from the inventory ("When worn or in the player's inventory"), so the cloak stays an optional wear and gives way to a cape already worn. Equipping stays at the bank (`BankGuide.wear`). No help-list item: no new game values.

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| elwakka | Wearing magic secateurs pushed off the lava battlestaff, so the plan asked for fire and earth runes | `SupplyCalculator.java:341-350`, `HoldingsTracker.java:210-217` | True. The weapon slot is handled case by case, and staff runes only count once the staff is worn. | **Fix** (with the next row) |
| elzerma | Equip at the bank, never mid-run; secateurs lowest of the weapon-slot items; comb all wearables | `BankGuide.java:70-118`, REBUILD decision 8 | Good, and it matches decision 8 | **Fix**: rank every wearable slot at the bank (weapon: fairy-ring staff when needed, then a staff covering the plan's runes, then secateurs). A staff's runes count when the plan says to wield it. |
| elwakka | The Ardougne cloak doesn't need to be worn either | `SupplyCalculator.toWear` | True; the plugin only suggests wearing it to save a slot | **Fix**, as part of the wearables pass: the cape slot is ranked too (check on the wiki that each cloak tier teleports from the inventory) |
| elwakka | Use the house as a way through (Farming Guild spirit tree → house → exit portal) | `RoutePlanner.java:945` | Good idea. Arriving at the house by spirit tree, or on foot at the portal, isn't modelled. | **Split** into an idea issue |
| elzerma | Issues from the plugin could carry anonymised log data | `TestReport.java` | The Bug form now has an optional log field. A "report a problem" button could reuse `TestReport.url`. | **Split** into an idea issue |
| elzerma | More crop types; farming contracts | | Roadmap | **Explore**, in this ledger only |
| elwakka | Testing beta builds feels risky; will batch tests | | | Chatter |

## #8 explore/add bank tab support for seed vault

**Kind:** Idea · **Status:** open. The section is built (option 2). Tests 1 to 3 passed, and elzerma asked for changes after each. The list entry (revision 4) passed elzerma's hand checks; its guided test is still to run. The banking setting and routing to the guild are being built (decisions below). · **Reviewed through:** 2026-10-16 elzerma (body)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| elwakka (via #5) | Seeds in the seed vault are hard to pick out; a farm tab there too? | `RunSession.fromSeedVault()`, `HoldingsTracker.java:151` | A text reminder exists (6583e4d). There's no highlighting in the vault interface. | **Explore** |
| elzerma | Options: (1) seed vault tabs like the bank tag plugin, built into this plugin with credit; (2) highlight each tab in turn, as Easy Blast Furnace does | | Both possible. A third way, outlining the needed seeds in the vault (~100 lines), was offered and not picked. | **Explore**, keeping elzerma's two options |

**Built (2026-10-16):** `bank/SeedVaultSection.java`, with the layout maths in `bank/SeedVaultLayout.java` (unit-tested against the game's own layout loop). While the guide's step is "Take ... from the seed vault", the vault's own widgets for those seeds move into a "Farm run" section at the top, with a header like the vault's; the categories below close up without them. The real widgets move, so withdrawing works with no menu changes and nothing is sent to the server. It runs after every vault build: the proc `seed_vault_build` (2820), not only the 2819 script, which just calls it when the vault's contents change; opening the vault and searching call the proc directly. It also follows each tick when the step changes. While the section shows, no item in the vault can be dragged, as while searching: the game's drag scripts would put a moved item back where the game laid it out, and a drag reorders the vault on the server. It does nothing while the vault is searched, or when the vault isn't laid out exactly as the game's script would lay it out (checked on every build), so after a game update that changes the vault it shows the vault as the game made it. New help-list test SEED_VAULT_SECTION.

**Tested (2026-10-16, elzerma, dev client):** passed. Avantoe seed moved to the top, was withdrawn, and the section then went. elzerma asked for changes:
- Letting go of a drag somewhere else withdrew the seed.
- Show the section for the whole banking stage, from Build run, not only at the guild's own step, and keep it once everything's taken.
- Add the bank tab's counts: "There is no x/y counter like the bank shows."
- Make it look like the bank's Farm run tab, without hiding the rest of the vault.
- Cover the whole run, not only the guild's patches.

The report also showed the vault opening scrolled to 109, past the section. The vault scrolls to the last category picked when it opens, and that category's header had moved down by the section.

**Decisions (2026-10-16, elzerma, picker):**
1. Show the section for the whole banking and gathering stage (before the run and at a bank stop), whenever the vault is opened. It stays once everything's been taken, with done items shown as done.
2. Show the seeds for every patch in the run. Whether that's the whole run or only the current part depends on the banking setting below; it shows the whole run until that's decided.
3. Routing through the guild for seeds only in the vault: explore first (write-up below).

**Changed (2026-10-17):**
- **When it shows:** whenever the vault is open while a run is being built or done (the same rule as the bank's Farm run button).
- **What it shows:** every seed and sapling the rest of the run plants (the whole run, bank visits included), in the bank tab's order. It's laid out like a bank tab section, with the line and title in its colours.
- **Counts:** each item has the bank tab's "/ N" label, tick and cross, in the same colours (shared with `FarmBankTab`).
- **Seeds the vault doesn't hold** (or no longer does) show faded, as on the bank tab, so the section stays, ticked, once everything's taken. An empty slot's placeholder is moved up in its place.
- **Dragging:** the game's own drag settings are kept, so letting go elsewhere ends a drag instead of counting as a click. The game's drag scripts come off the items that moved, and each tick a vault item is dragged its drop is cancelled (`client.setDraggedOnWidget(null)` on `WidgetDrag`, as RuneLite's Bank Tags does for "Prevent tag tab item dragging"). Nothing is withdrawn or reordered.
- **Scroll:** when the vault scrolls to its first category, as it does on opening, it scrolls to the top so the section shows.

The guided test is now revision 2. It asks about each of these.

**Tested again (2026-10-17, elzerma, revision 2):** passed. The report showed:
- The section at the top, with a "/ 1" count under each seed. Both seeds were already carried, so the change from yellow to green wasn't seen.
- The section staying once everything was taken.
- The vault opening scrolled to 0.

After the test, a seed the vault didn't hold showed faded. elzerma then asked for:
- Dragging back: "I don't believe that causes issues".
- The name "Farm Run Autopilot".
- Its own entry in the list on the left.
- The title in the same font as the vault's other headers.

**Changed (2026-10-17, revision 3):**
- **Dragging works as in the game's vault.** The game's drag scripts and drag settings are left alone, and drops are no longer cancelled. Each item is still the widget of its own vault slot, so a drag moves the slots the player meant. After each drop (`seed_vault_drop`, 2827) every item goes back where the section put it, since the game's drop script returns the dragged item to the place the game first gave it.
- **Name and font.** The header reads "Farm Run Autopilot", in the vault's own header style (bold 12, orange, the line under it).
- **Left list.** A "Farm Run Autopilot" entry like the game's own scrolls to the section. It sits under the Farming Contract's entry when there is one, and the game's entries below it move down to make room. It's made after the list's script (`seed_vault_build_category_list`, 2814) and hidden while searching.

SEED_VAULT_SECTION is now revision 3, asking about the name, the list entry and dragging.

**Checked by hand (2026-10-17, elzerma):** the name and font, dragging (a swap, letting go in an empty space, dragging out of the section), withdrawing by click, and the bottom of the vault were all good. One problem: Favourite Slots in the left list also went to the very top. The fix for opening at the top ran whenever the vault scrolled to its first category, clicks included. elzerma then asked for:
- A button just left of the vault's close button that switches the view with the section on and off, instead of the list entry.
- On that button, the same attention look as the bank's Farm run button.

**Changed (2026-10-17):**
- **The button:** the bank's Farm run button again (same square button, farming icon and click sound), just left of the close button. It switches the Farm Run Autopilot view on and off, and is pressed while it's on.
- **On or off:** like the bank's tab, the view starts off, stays as left through the run, and is off again for the next run.
- **Attention:** while the view is off and the vault holds a seed the run still needs, the button flashes and has the bank button's outline round it (`VineOverlay`, which another session changed from the fuse to a vine the same day). The gathering step says "Farm run: click the flashing button at the top of the seed vault".
- **Left list:** the list entry is gone.
- **Scroll:** opening the vault goes to the top only for the scroll the game does as the vault opens (`seed_vault_catvar`, 2816, within a tick of opening). Clicking Favourite Slots (`seed_vault_catop`) goes to its own place, below the section.

Revision 3 now asks about the button, Favourite Slots, reopening and hiding. No result had been recorded for its earlier steps.

**Later the same day:** the button came down to 22 pixels, centred on the close button's line, to sit in the title bar (elzerma: "might need to come down to like 22-23 pixels").

**Tested (2026-10-17, elzerma, revision 3):** passed. The report shows:
- The vault opening with the view off and the button calling.
- With the button clicked, the section at the top: 120 high, both seeds yellow (in storage), scrolled to 0.
- With the button clicked again, the section gone and the button calling again.

The capture after Favourite Slots ("scrolled to 0") was taken too early to count; elzerma's hand check covers it. elzerma's 11 hand checks all passed:
- Favourite Slots lands just below the section.
- The counts go up on withdrawing and down on depositing.
- Seeds missing everywhere show red, and seeds only in the bank show yellow with 0.
- Dragging in or onto the section does nothing ("can only swap within categories").
- Drags elsewhere still swap.
- Searching works.
- There's no button without a run built.
- Herbs for other patches are included.
- With the vine switched off, the button only flashes.
- Fixed and resizable layouts both line up.

elzerma then asked: "previously indicated that farm run autopilot shouldn't create a tab in the list on the left. I want that feature back, but only show that tab when the button has been toggled on".

**Changed (2026-10-17, revision 4):** the "Farm Run Autopilot" entry is back in the list on the left (the same code as before). It now shows only while the view is on, so it comes and goes with the button.
- It goes at the top, or under "Farming contract" when that section is above.
- It scrolls to the section.
- The game's entries move down a row for it and back up when it goes.
- Favourite Slots and the other entries still go to their own places: only the scroll as the vault opens is sent to the top now.

Revision 4 asks about the entry with the view on and off.

**Checked by hand (2026-10-17, elzerma, revision 4 change):** checks 1 to 6 passed: the entry comes and goes with the view, in the same font, with the others moved down a row and still alternating; it scrolls back to the section; Favourite Slots and Trees go to their own headings; it lights up under the mouse; the list is back as the game has it with the view off; and reopening the vault with the view on shows both. The log agrees (the section 120 high with the view on, gone with it off, no errors). Not tried: a farming contract in progress (none active), where "Farming contract" should stay first. The revision 4 guided test hasn't been run yet.

**Decisions (2026-10-17, elzerma, picker):**
1. Build the banking setting as drafted below. It's "When a run banks partway", under the Seed vault tick box and shown only while that's ticked, with both choices; "Take all seeds at the start" is the default.
2. Seeds only in the vault, on a run that doesn't start at the guild: option 4 now, then option 1 (see the routing write-up). The open questions in option 1 (which way there, whether to ask first) are left to Claude.

**Built (2026-10-17), one commit each:**
- **Option 4:** when the bank has nothing left to give before the run, the gathering step names the seeds that only the seed vault can make up: "Farm run: take 6 x Ranarr seed from the seed vault in the Farming Guild" (`SupplyPlan.onlyInVault`). It takes all that's still to get from the vault if the vault holds that many, so it's one trip. Not when the run starts at the guild, where the guild's own vault step covers them. That step now names items without the "- 3rd choice".
- **The setting:** "When a run banks partway" in Account > Storage sources, under the Seed vault tick box and only while it's ticked, with the label, choices and tooltips as drafted (`settings/VaultBanking.java`). "Take all seeds at the start" is the default.
- **Take all seeds at the start:** on a run that banks partway, the gathering step adds the later parts' seeds that only the vault has: "Farm run: take 12 x Ranarr seed from the seed vault in the Farming Guild, and put the ones for later in your bank". Once they're taken it says "Farm run: put 6 x Ranarr seed in your bank for later in the run", with them outlined while the bank is open. The run waits for both before it's ready; Start now skips them.
  - Only seeds seen in the vault for the later parts are asked about, so seeds the player keeps in their inventory anyway are left alone.
  - When the run starts at the guild, the first part's vault seeds come out in the same trip.
  - Before the run starts, the vault section counts the later parts' seeds in the bank as done, so it doesn't ask for them again.
  - At the bank visit they're in the bank, so it works as for anything else.
- **Option 1, the run starts at the Farming Guild:** when a seed the run plants is only in the seed vault, the run is planned from the guild (`RunService.plansFromGuild`, the planner's `startFrom`).
  - If the guild is a stop, the run starts there, by the way Auto picks, and the guild's own vault step says what to take, as before.
  - If it isn't, the first leg is planned from the guild (its spirit tree, fairy ring CIR, or the best teleport) and the gathering step says so: "Farm run: take 6 x Ranarr seed from the seed vault in the Farming Guild, where the run starts".
  - While the player is in the guild and it isn't a stop, the run sets off from there too, so the route stays the same once the seeds are taken.
  - The open questions, decided by Claude: it plans from the guild without asking, since the seeds have to come from there anyway. A start picked in the Run options wins. With the Seed vault tick box off nothing changes.
- **Bank at the Farming Guild:** a run that banks partway banks at the guild before any part that plants seeds only the seed vault has (`BankPlanner`, `RunService.guild`).
  - That's right after the guild's own stop when the run can split there. Otherwise it's a trip to the guild from the stop before, by the way Auto picks, then on from the guild. Its row on the Run tab says "Bank at the Farming Guild", and the trip's item (skills necklace, farming cape) is added to the part before.
  - The planner weighs this against banking elsewhere and keeps the route's order. If no plan can bank at the guild (an item that doesn't fit), it banks wherever is quickest, as before.
  - The first part's vault seeds come out before the run (options 4 and 1), so they don't move the bank visits. With this choice, the run is planned from the guild only when the first part needs the vault.
  - At the guild's bank, once the Farm run tab is done, the step says "Take 6 x Ranarr seed from the seed vault (next to the bank)", or to click the vault's flashing button, and the visit waits for them. The bank's Farm run tab stops flashing for that step.
  - The vault section shows only the part being gathered or banked for.
  - Scenario test on elzerma's full run: with the last stop needing the vault, it banks right after the guild's own stop, and keeps the start and the Stronghold chain.
- **Help list:** VAULT_SEEDS_FOR_LATER and VAULT_BANK_AT_GUILD, on the Run tab, each with a guided test that sets the choice and captures the steps, the bank visits and the vault section.

### #8 write-up: how a run with seeds in the vault banks partway (wording chosen 2026-10-17; see Built above)

Only for a run too big for one inventory, so it banks partway, with some of its seeds kept in the seed vault. elzerma: "the decision I can't make is whether it should guide users to bank again at the farming guild or if it should have them withdraw all for a farm run from vault, deposit to main bank, then withdraw only what is needed from bank for remainder of run. That is the decision to be left with users, but I need to be sure it's described really well."

**The setting (draft wording).** It goes in Account > Storage sources, under the "Seed vault" tick box, and only shows while that's ticked.

| | Words |
|---|---|
| Label | When a run banks partway |
| Label tooltip | For seeds you keep in the seed vault, on a run too big for one inventory. |
| Choice 1 | Bank at the Farming Guild |
| Choice 1 tooltip | Take only the seeds this part of the run needs from the seed vault. When the run banks partway, it banks at the Farming Guild, so the rest come from the vault then. |
| Choice 2 | Take all seeds at the start |
| Choice 2 tooltip | Take every seed the run needs from the seed vault before you set off, and put the ones for later in your bank. When the run banks partway, take them from your bank. |

Another label, if the one above reads oddly: "Seed vault on longer runs".

**What each choice needs from the planner:**
- **Bank at the Farming Guild:**
  - The bank visit before a part that needs vault seeds must be at the guild. That's right after the guild's stop if the run has one and splitting there fits. Otherwise it's a trip there (skills necklace, farming cape, spirit tree, fairy ring CIR) and on from the guild. `BankPlanner.place` has to weigh that trip against banking elsewhere, so the route can change shape and be slower.
  - A bank visit at the guild has to count the vault. Today no bank visit does; RunSession checks them with the vault off.
  - The vault section would show only the part being banked for.
  - Size: L.
- **Take all seeds at the start:**
  - The run has to start at the Farming Guild, where the vault and a bank are side by side, or the gathering step has to send the player there first (see the routing write-up). Today the vault only counts when the run starts there.
  - Two new guide steps at the start: take the whole run's vault seeds (the section already shows the whole run), then put the later parts' seeds in the bank (the bank tab's "Deposit first" list can show them).
  - The first part's slot count must leave those seeds out.
  - Later parts need nothing new: their seeds are in the bank by then, which the plugin reads when the bank opens.
  - Size: M.

**Recommended default:** Take all seeds at the start. The bank visit partway happens either way, so this adds no trip, and it needs less from the planner.

### #8 write-up: routing through the Farming Guild for seeds only in the vault (options 4 and 1 built 2026-10-17; see Built above)

**Today:**
- The plugin picks crops from everything you hold, the vault included (`SupplyCalculator`, `holdings.count`).
- The vault only counts as usable when the run starts at the Farming Guild (`RunSession.missingCount`), or mid-run when the rest of the run passes the guild.
- Otherwise vault-only seeds are yellow (in storage) on the bank tab, and "take the N items marked in red or yellow" includes them, so the player fetches them by hand. From 2026-10-17 the vault section shows them while the run is being built.

| Option | Size | Notes |
|--------|------|-------|
| 1. Start at the guild: when a seed the run plants is only in the vault, plan the run from the Farming Guild and say so in the gathering step ("Go to the Farming Guild: Ranarr seed is in the seed vault") | M | Works with either banking choice. Open questions: which way there (the guild teleports you have), and whether to ask first. |
| 2. Pick another crop: count vault seeds only when the run passes the guild, otherwise take the next crop choice you hold elsewhere, with a note | S | Overrides your crop order, so it has to be said where it happens |
| 3. A trip to the guild partway: the bank planner adds a vault visit before the part that plants them | L | Needed anyway for "Bank at the Farming Guild" above |
| 4. Say where they are: keep the plan, and have the gathering step name the vault ("Take 6 Ranarr seed from the seed vault in the Farming Guild") | XS | Clear, and changes no routes |

**Recommended:** 4 now, then 1 if the plugin should route there itself. 3 only goes with the first banking choice.

## #9 Test: Fire of Nourishment at Weiss - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: arrived in region 11325, and the fire was spotted on that tile (`UnlockSpotter.java:49`). "a q p" under Anything else is noise. **Decision:** close as passed and remove WEISS_FIRE. Narrow the UNVERIFIED note in `UnlockSpotter`, which also covers the spirit trees.

## #10 Test: House scanning (beta) - works

**Kind:** Test report · **Status:** closed 2026-10-16; test fixed in b2f2ad7 (HOUSE_SCAN revision 3) · **Reviewed through:** 2026-10-16 elwakka (body)

Weak pass. "Before" is captured before the player enters, and a scan only adds, so the report doesn't show the scan filling anything in. The Kharyrll nexus change was a hand tick in Account > My house, not a scan. The other-house half was skipped (ironman). **Decision:** keep HOUSE_SCAN; fix the test so the house is cleared first and the scan has to fill it in; bump its revision; close #10 with thanks.

## #11 Test: Pendant of Ates test

**Kind:** Test feedback · **Status:** closed as duplicate of #12 · **Reviewed through:** 2026-10-16 elwakka (body)

"Doesn't go to the next step after teleporting" is true, and caused by #12's bug. **Decision:** close as a duplicate of #12.

## #12 Test: Pendant of Ates charges - needs attention

**Kind:** Test report · **Status:** closed as completed 2026-10-23: the re-run passed, `ATES_CHARGES` removed from the help list (f674066, merged 64ebd4a) · **Reviewed through:** 2026-10-23 elzerma (draft A)

**2026-10-21 review:** elwakka's re-run (2026-10-18) went 20 to 19, with the plugin's count matching the second Check and the teleport step **Detected** (not the fallback question), so it's a real pass (`GuidedTests.AtesCharges`). With #28 that's two. **Close as passed, remove the test** (elzerma, picker); draft A posted.

The report is right, though the player thought it was bugged. `CHARGES_PENDANT_OF_ATES_QUANTITY` reads 0 with 4 charges (`ItemChargeTracker.java:140`, UNVERIFIED), so the plugin can't count pendant teleports. Its "advance" after the spirit tree was the 6-minute fallback question. **Decision:** count teleports by click-then-move, as for the talisman; drop the varbit; bump ATES_CHARGES; reply and keep #12 open until a re-run.

## #13 Test: Xeric's talisman charges - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: 13 = 13, then a detected teleport, then 12 = 12. **Decision:** close as passed and remove TALISMAN_CHARGES.

## #14 Test: Kharyrll Teleport highlight - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: the player confirmed the outline on `ZAROSTELEPORT3`. **Decision:** close as passed; remove KHARYRLL and its UNVERIFIED note in `SpellHighlightOverlay`.

## #15 Test: Hot air balloon from the Grand Tree - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: `ZEP_MULTI_GNO` = 1 with the route unlocked. Taverley's value of 2 hints that values above 1 mean something, but "above 0 = unlocked" holds. **Decision:** close as passed, remove HOT_AIR_BALLOONS, and mark BALLOON_GRAND_TREE confirmed.

## #16 Use the house as a way through (arrive by spirit tree)

**Kind:** Idea · **Status:** open · **Reviewed through:** 2026-10-16 elzerma (body)

Split from #7 (elwakka). **Explore**: arriving at the house through a spirit tree, or on foot at the portal, isn't modelled (`RoutePlanner.java:945`). Measure it against today's route first, e.g. Farming Guild → house spirit tree → Hosidius portal.

## #17 Report a problem from the plugin, with anonymised run data

**Kind:** Idea · **Status:** open · **Reviewed through:** 2026-10-16 elzerma (body)

Split from #7 (elzerma). **Explore**: could reuse `TestReport.url`, capped at 6000 characters.

## Explore write-ups (2026-10-16)

Options for the ideas marked **Explore** above. None of these is being built until elzerma picks one.

### #8 seed vault

The vault already has its own category tabs, eight favourites and a search (`InterfaceID.SeedVault`: CATEGORY_LIST, FAVES, SEARCH, OBJ_LIST; `VarbitID.SEED_VAULT_CATEGORY`). The plugin already knows the needed seeds and what's in the vault (`HoldingsTracker`, `RunSession.fromSeedVault()`).

| Option | Suggested by | Size | Notes |
|--------|--------------|------|-------|
| 1. Step-by-step highlight: outline the category tab with the next seed, then the seed, then move on once it's in the inventory (Easy Blast Furnace style) | elzerma | S–M, ~150–250 lines | Allowed (highlight only). Unknown: mapping each seed to its category. **Recommended.** |
| 2. Outline the needed seeds wherever they show | Claude | S, ~100 lines | A subset of option 1 |
| 3. A "Farm run" view inside the vault, ported from the bank tab | elzerma | L, ~600–900 lines | The bank tab relies on hooks only the bank has (`getSearchingTagTab`, BANKMAIN_*); the vault's build script (2819) is undocumented. Likely to break on game updates. |

### #5 bank icon cue

The button flashes while the run needs the bank (`FarmBankTabButton.onGameTick`). REBUILD decision 7 also planned an outline and a setting to open the tab by itself; neither was built.

| Option | Size | Notes |
|--------|------|-------|
| 1. Outline around the button, pulsing with the flash | XS, ~30–40 lines | Finishes decision 7 (i) |
| 2. Open the Farm run tab by itself when the bank opens and the guide is waiting for it, once per visit | S, ~30–50 lines + a setting | The same local redraw a click does; needs a Plugin Hub rules check (and a look at how Quest Helper does it) |
| 3. One-line hint by the button the first time | S, ~50–70 lines | Only if players still miss it |

Recommended: 1, then 2 after the rules check.

### #16 house as a way through

Today the only way into the house is Teleport to House (`RoutePlanner.canTeleportHome`, ~945). Spirit trees are only destinations, and the house portal has no location to walk to.

| Option | Size | Notes |
|--------|------|-------|
| 1. Spirit tree into the house, feeding the existing ways out (exit portal, nexus, jewellery box, fairy ring) | M, ~120–200 lines in 4–6 files | Must not ask for a house tab when the way in is a tree (`SupplyCalculator` ~217, `RunSession` ~1382). Needs in-game timings: arrival point and walk to the portal or nexus. **Recommended.** |
| 2. Option 1, plus walking in at the portal from nearby stops | +60–100 lines + walk measurements | |
| 3. One hard-coded chain, e.g. tree → house → Hosidius portal | S, ~30–50 lines | Quick to compare against today's route; doesn't generalise |

To check on the wiki: "Your house" is on spirit trees only with a spirit tree or spirit ring built in the house (Superior garden), and the arrival point.

### #17 report a problem from the plugin

It would follow the "Share your run times" pattern: a button in Settings > Help improve this plugin opens a prefilled issue in the browser (`LinkBrowser`), and nothing is sent unless the player submits it. The plugin makes no request, so the third-party server rule doesn't apply. Leave out the name, position, bank, seed-vault and group-storage counts, and the full levels, quests and diaries list (close to a hiscores fingerprint).

| Option | Size | Notes |
|--------|------|-------|
| 1. A button that prefills the Bug form (`template=bug.yml`, `version` and `log` fields) with a coarse plan: stops and ways, supply need/carried/status, worn items, the unlocks used, settings, warnings | S, ~80 lines | **Recommended** |
| 2. Option 1, plus a preview before opening and a pointer from the Run tab | M | |
| 3. Make it the expected way to report bugs: a README link, and the last few plugin warnings and errors kept for the report | L | |

Open questions: bank and vault counts left out, or shown only as present/missing? Is a preview wanted, or is the GitHub page enough (as for run times)?

### Decisions on the write-ups (2026-10-16, elzerma)

- **#8:** explore a Bank Tags-style view inside the seed vault, if the rules allow (Other: "Explore options like bank tab plugin if rules allow"). No code yet.
- **#5:** build the outline now, as an animated "fuse": a bright spark looping around the Farm run button like marquee lights, with little pixel figures shedding off where the spark passes. Auto-open comes later, only after checking how Quest Helper does it and that it fits the Plugin Hub rules.
- **#16:** build option 1 (a spirit tree into the house).
- **#17:** build option 1 (the Report a problem button).

### Built and researched (2026-10-16)

- **#5:** the fuse is built (659b0a8). **Auto-open, researched:** Quest Helper has no auto-open setting; it only reopens its tab if the player left it open, which this plugin already does. RuneLite's own Bank Tags reopens the last tag tab by default ("Remember last tag tab"), and the Inventory Setups hub plugin filters the bank on every open (with a "Manual Bank Filter" setting to turn that off). Nothing in the rejected-features list is against it. **Likely allowed** as a setting players can turn off, done the same way as a click (`bankSearch.reset(true)`), never with `client.menuAction`. Waiting on elzerma.
- **#17:** the Report a problem button is built (93ac387).
- **#16:** spirit tree into the house is built. New departures TREE_HOUSE_PORTAL, _NEXUS, _JEWELLERY_BOX and _FAIRY_RING need no house tab, rune or cape and cost nothing. Timings are estimates (tree walk + tree + door/walk), so there's a new help-list test, HOUSE_BY_TREE.
- **#8, researched:** no hub plugin re-lays out the seed vault, and RuneLite has no seed vault hook like the bank's. Its build script (2819, `seed_vault_build`) makes a widget for every vault slot in one scrolling list with category headers, and hides the empty ones. Nothing in the rules forbids moving those widgets, but it hasn't been done before, so expect a closer review. Options, from least to most risk:
  1. Highlight the needed seeds after 2819 runs (as the chance-man hub plugin dims items). S.
  2. A "Farm run" section at the top: move the real item widgets into it after 2819 runs, so withdrawing still works; push the vanilla headers down; enlarge the scroll; turn off dragging on the moved items (a drag could reorder the vault on the server); skip while searching. M–L, and may need adjusting after game updates.
  3. Never use `menuAction` or a server-side vault script.

  Waiting on elzerma.

### Decisions (2026-10-16, elzerma)

- **#5:** no auto-open; the fuse is enough. Done.
- **#8:** build option 2, a "Farm run" section at the top of the seed vault.
- **#5:** the fuse was checked in game on 2026-10-16 after it was sped up (0.7 s a lap): "it looks fine to me". The Report a problem button and its Bug form (#17) were checked the same day.
- **#5, follow-up (2026-10-17):** at elzerma's request the fuse became a growing vine. It grows from the middle of the bottom edge, flowers, then wilts. It's also used for the spell to cast, with a "Growing vine highlights" setting that switches back to plain outlines (a109fe6). elzerma checked it in game: "Looks good".

### Release 2026-10-17

runelite/plugin-hub#18748 merged at 12:04 UTC, so 62f3070 is live. Replies posted as approved:
- **#6, #7, #17:** closed as completed.
- **#12:** told the fix is live and the test is asked again. Stays open until it passes.
- **#16:** told it's live, with the HOUSE_BY_TREE test. Stays open until someone with a house spirit tree tries it.

Still open: #8 (seed vault, waiting on edge-case checks and elzerma's choices on the banking setting and the routing), #12 and #16. The vine (bank, vault and spellbook) and the seed vault section are on master after 62f3070, and go out in the next update.

## #18 Help wanted: 6 quick in-game tests

**Kind:** Help wanted (opened and pinned by elzerma's OK, 2026-10-17) · **Status:** open, pinned · **Reviewed through:** 2026-10-23 elzerma (body edit)

- **2026-10-23 edit (review of 2026-10-21, draft O, elzerma's OK):** retitled from 7 to 6 tests, the "Pendant of Ates charges" row removed (passed in #12 and #28), and the bucket row now asks for "A bottomless compost bucket with compost or supercompost in it (ultracompost is done)".

Lists the help-list tests elzerma's account can't run: SPELLBOOK_SWAP, FORTIS_CHAMPION, BUCKET_COMPOST, SPIRIT_TREES, HOUSE_BY_TREE, ATES_CHARGES and HOUSE_SCAN. Update the table when a test is added, passes or is removed. Close it when the list is empty.
- **HOUSE_TURNED_OFF added (sprint T7, 2026-10-20):** draft A, a row "Turning your house off | Your own house, set up in Account > My house, on a run that goes through it" after "Spirit tree into your house", and the title "Help wanted: 8 quick in-game tests". elzerma (picker, 2026-10-20): post it once the test is in a Plugin Hub release, not before. Not posted. Withdrawn 2026-10-23 (elzerma, picker): they have a house after all (Hosidius), so the test is theirs to run and #18 needs no row for it.
- **ICY_BASALT added (the route snapshots' Weiss leg, 2026-10-21):** draft A, a row "Icy basalt to Weiss | Making Friends with My Arm, the Fire of Nourishment built at Weiss, and an icy basalt (it's used up)" after "Pendant of Ates charges", with the title's count one higher (9 with HOUSE_TURNED_OFF's row; 8 now that row is withdrawn). elzerma (picker, 2026-10-22): post it once the test is in a Plugin Hub release, with HOUSE_TURNED_OFF's row (withdrawn 2026-10-23, so on its own). Not posted yet.
- **HOUSE_LOCATION added (2026-10-21, the house's place read from the game):** draft B, a row "Where your house is | Your own house, and a way to teleport to it (the spell, a tablet, or a construction or max cape)" after "House scanning (beta)", with the title's count one higher. Shown to elzerma, never asked about or posted. Withdrawn 2026-10-22: the test passed on elzerma's own house (Hosidius) and was removed, so #18 needs no row for it.
- **#7, follow-up (2026-10-17, elzerma in game):** with a lava staff suggested, the list still showed an earth rune line with a green tick, which read as something to bring. Runes covered by a staff or tome no longer get a line of their own; the staff's line ("Covers the earth and fire runes") and the rune summary say it once.
- **#7, follow-up (2026-10-17, elzerma: "bring multiple staves if more space efficient than runes"):** built (38920cb, f96c34d, ea00c6c), not yet checked in game. elzerma's choices (picker): guided swaps during the run are fine, but only when they free at least one inventory slot overall; the rune pouch is one slot shared by its rune types (1/3 each when full, 1/2 with two, the whole slot with one; the divine pouch holds 4, 1/4 each), and types that don't fit take a slot each; the dramen staff joins the swapping (a rune staff wielded by default, the dramen staff for fairy ring legs and back), and the Elite Lumbridge diary still means no dramen staff. `supply/StaffPlanner.java` tries every set of up to three staves against keeping one on, and swaps only just before setting off for a stop, so the staff at a herb patch (Cure plant) is the one wielded on the way there. The first staff is put on at the bank; the others are carried (a slot each), and the step before a stop that needs another says "Wield your mist battlestaff", outlined in the wear colour, moving on once it's wielded. Also fixed in passing: the house fairy ring reached by spirit tree didn't ask for the dramen staff. New help-list item STAFF_SWAPS. Then (elzerma asked how a teleport needing fire and water is handled with a fire staff and a water staff): one staff is wielded per cast, so that cast keeps one staff and carries the other rune; a staff is now worth the slots its rune types take (a rune pouch share counts), then the runes it saves, so 20 fire beat 2 water. No reply posted.

## Review of 2026-10-21 (#19 to #31)

Worked through in the working notes `docs/ISSUES-REVIEW-2026-10-21.md` on branch `claude/issues-review-2026-10-21`, which this section replaces. Every report below was made on the released `62f3070`; line numbers were for master `019880c`. elzerma picked each verdict in the picker on 2026-10-22 and said to post every draft ("good to post them all"); the replies, closes and labels went up on 2026-10-23 (GitHub time). Each fix promised in a reply was built in its own session and merged to master: the pendant test removed (64ebd4a), the bucket test (3a4576c), the house scan (6c6e828), crystal trees in Sprint 1c (e734870) and the spirit tree test (241244f). None of them has been checked in game by a player yet.

## #19 Test: Planted spirit trees - works

**Kind:** Test report · **Status:** open, `test-report`; reply posted (draft C); closes once elwakka answers · **Reviewed through:** 2026-10-23 elzerma (draft C)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| elwakka | Works: arrived at `WorldPoint(1249, 3741, 0)`, spotted `SPIRIT_TREE_PORT_SARIM` | `GuidedTests.SpiritTrees.spotted` (`:1120`), `TestContext.hasUnlock` (`:180`), `TestRunner.start`, `SettingsStore.startTest`, `AccessChecker.REFRESH_TICKS` = 50 | Weak pass. 1249, 3741 is region 4922, the Farming Guild, but `spotted` returns the first of Port Sarim, Brimhaven, Farming Guild that `hasUnlock` says is ticked, not the one where the player is, and no test refreshed the access snapshot at its start, so a tick from before `setUp` could still count | **Fix**: only the tree in the player's region counts, every guided test starts with a fresh access snapshot, `SPIRIT_TREES` revision 2 (241244f). Ask whether they have a Port Sarim tree too |

## #20 Test: Bottomless bucket at the tool leprechaun - needs attention

**Kind:** Test report · **Status:** closed as completed 2026-10-23, `test-report` · **Reviewed through:** 2026-10-23 elzerma (draft D)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| darkshadow0308 | Needs attention: "Type value 4 was read as Ultracompost, not Compost" | `GuidedTests.BucketCompost` (`:968`), `Compost.fromBucketType` (`:47`) | The reading was right: they answered Yes to compost **and** to ultracompost, and 4 matches the ultracompost Yes (as in #29). The question "Is the bucket filled with compost?" reads as "any compost". The uses value (38) was never compared with anything | **Fix the test**: ask about plain compost, add a uses check, mark 4 confirmed and keep 2 and 3 UNVERIFIED (3a4576c). Close; the test stays for compost and supercompost |

## #21 Test: House scanning (beta) - needs attention

**Kind:** Test report · **Status:** open, `test-report`, `needs-info`; question posted (draft F) · **Reviewed through:** 2026-10-23 elzerma (draft F)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| darkshadow0308 | "The house wasn't scanned"; everything empty before and after, `lastDetected=0`; answered Yes that it matched | `PohDetector.onGameTick` (`:198`), `apply` (`:252`, `:283`), `GuidedTests.HouseScan` (`:1168`), `TestContext.inOwnHouse` (`:185`) | Two possible causes: a house with none of the five scanned things (jewellery box, pool, ancient/lunar/dark/occult altar, fairy ring, spirit tree) was never marked scanned, which fits the Yes; or they were already inside when the test started, so nothing loaded after `setUp` cleared it | **Needs info** and **Fix** either way: a loaded house counts as scanned with nothing found, step 1 waits for a new house load, `HOUSE_SCAN` revision 4 (6c6e828) |

## #22 Add support for Calquat trees

**Kind:** Idea · **Status:** open, `enhancement` (was `idea`); reply posted (draft H) · **Reviewed through:** 2026-10-23 elzerma (draft H)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| Zerkia | No calquat options; two of three are near other fruit trees | `docs/plans/crop-types.md` section 4 | Already planned in Sprint 1c (Tai Bwo Wannai, Kastori, Great Conch), after the travel rework and 1b | **Planned**: keep open until calquats are in |

## #23 Fruit Tree in Gnome Stronghold not triggering

**Kind:** Bug · **Status:** open, `needs-info` (`bug` taken off until the skip is traced); questions posted (draft G) · **Reviewed through:** 2026-10-23 elzerma (draft G)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| Zerkia | On a herb + tree + fruit tree run the guide did the Stronghold tree, then sent them to Tree Gnome Village, skipping the fruit tree | `Patch.java:53` (fruit tree `FARMING_TRANSMIT_B` in 9781, as RuneLite's `FarmingWorld`), `RunSelector.java:135`, `StepAdvisor.java:83`, `PatchGuide.nextStep` (`:245`) | The varbits are right. A patch at a stop is only passed over when it isn't in the plan (not due, unticked), reads as growing since arriving, or the stop was ticked by hand. Most likely it was planned as due but still growing; can't settle without the reporter. elzerma asked whether the next release fixes it: no, master passes over a patch the same way as `62f3070` | **Needs info**: ask whether the fruit tree was in the stop's hover list and whether it was ready |

## #24 Support for item customization for setup

**Kind:** Idea · **Status:** open, `idea`, `needs-info` (the tome question); reply posted (draft J) · **Reviewed through:** 2026-10-23 elzerma (draft J)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| Zerkia | Can't say they don't have, or don't want, a tome of fire | `SupplyCalculator.runeItems` (`:928`) | Only staves and tomes the player owns are offered; there's no opt-out. If they don't own one, it's a bug | **Needs info**: ask whether they own one; no opt-out planned (elzerma: ask only) |
| Zerkia | The rune pouch isn't suggested, though its runes are seen | `HoldingsTracker.readRunePouch` (`:259`), `StaffPlanner.pouchTypes` (`:102`), `SupplyCalculator.java:1299` | On `62f3070` the pouch is never named. On master (38920cb) an owned pouch, bank included, is named on the rune lines; its runes only count while it's carried | **Already done (next update)**, plus **Explore**: count a banked pouch's runes |
| Zerkia | Without an icy basalt there's no other way to Weiss | `atlas/places.tsv` (place 21 `WEISS_HERB`), `ways.tsv` | On master the atlas reaches Weiss by fairy ring DKS, the Fremennik sea boots, the Rellekka house portal or tablet and more | **Already done (next update)** |
| ConnorBampton | Magic secateurs on herb runs | `SupplyCalculator.secateursWanted`, `RunConfig.java:70` | Already suggested by Suggest yield boosts (on by default), for herbs, allotments and hops | **Already done** |

## #25 Run times

**Kind:** Idea (shared run times) · **Status:** closed as not planned 2026-10-23, `wontfix`; reply posted (draft M, with the Hosidius line) · **Reviewed through:** 2026-10-23 elzerma (draft M)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| collinphood-svg | Shared their learned legs from Settings > Help improve this plugin > Share your run times | 7d1f244, 92d4624 | Master removed the button (no crowdsourcing: times are the player's own), and learned times no longer choose routes. Their Hosidius leg by Kharedst's memoirs (119.4 s, 2 samples) is far over the estimate; elzerma's own leg there (Book of the Dead, 59 s) was already slow (a3af137) | **Close as not planned**, mention the slow Hosidius leg |

## #26 Herb Seeds in Seed box should be usable

**Kind:** Idea · **Status:** open, `idea`; reply posted (draft L) · **Reviewed through:** 2026-10-23 elzerma (draft L)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| ConnorBampton | Track seeds in the seed box and point to the box at the patch, not "No cadantine seeds in inventory" | OSRS wiki (Seed box; the 3 June 2026 update plants the first suitable seed when the box is used on a patch), `InventoryID.SEED_BOX` | Not supported on either build. Unknown when the server sends the box's container | **Explore**: read the box's contents and point to it when its first suitable seed is the planned crop. Asked which seeds they keep in it |

## #27 Idea: Ability to whitelist or block validation on some bank items e.g. house tabs

**Kind:** Idea · **Status:** open, `idea`; reply posted (draft K); closes once the update is out · **Reviewed through:** 2026-10-23 elzerma (draft K)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| ConnorBampton | Asked for house tabs with a dust staff and law runes held; any way to mark an item as covered | `62f3070` `SupplyCalculator.usesTablet` and the Travel tab box (`Departure.DIRECT` only); master `TravelPage.java:165`, `SupplyCalculator.java:1164` (862b7d4, 0bfed55) | True on the release: house stops couldn't switch to runes. Master's "Cast from runes, not tablets" covers Teleport to House | **Already done (next update)**: keep open until it's released |

## #28 Test: Pendant of Ates charges - works

**Kind:** Test report · **Status:** closed as completed 2026-10-23, `test-report`; reply posted (draft B) · **Reviewed through:** 2026-10-23 elzerma (draft B)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| ConnorBampton | Works: 89 to 88 | `GuidedTests.AtesCharges` (`:906`) | Real pass, the second (with #12) | **Close as passed**; `ATES_CHARGES` removed (f674066) and its #18 row dropped |

## #29 Test: Bottomless bucket at the tool leprechaun - works

**Kind:** Test report · **Status:** closed as completed 2026-10-23, `test-report`; reply posted (draft E) · **Reviewed through:** 2026-10-23 elzerma (draft E)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| ConnorBampton | Works: ultracompost, type 4, uses 203 | `Compost.java:42-47` | 4 = ultracompost confirmed by a second player; compost and supercompost still unseen | **Close**; the test stays for compost and supercompost (see #20) |

## #30 Offer to help with Sprint 1c (special trees)

**Kind:** Idea (offer of help) · **Status:** closed as completed by elzerma 2026-10-23 · **Reviewed through:** 2026-10-23 elzerma (closing comment)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| MangoTheFlamingo | Offers to help with 1c (building parts to the plan, or sharing data); their Tree Run Planner ([TreeRuns](https://github.com/MangoTheFlamingo/TreeRuns), BSD-2) covers every special tree, crystal trees too | `docs/plans/crop-types.md` section 4 | A generous offer; the plan's 1c unknowns are exactly what their testing covers. Crystal trees were left out of the plan with no reason | elzerma (picker): ask for their data in the thread, **no pull requests**; **add crystal trees to Sprint 1c** (e734870). Draft I posted |
| MangoTheFlamingo (2026-10-23) | Great Conch: the step where you give the bandage; hardwood stump fee 200 coins; Anglers' Retreat needs 51 Sailing, a rowboat (50 Construction) from Corsair Cove recommended; patch data in TreeRuns' `data/Patch.java` | | Reports from a month of testing, not readings from this plugin | Written into the 1c plan's UNVERIFIED list, credited to them (06f16b9); the README credits them and their plugin and thanks them |
| elzerma, MangoTheFlamingo | Thanks, the release timing and the changes in it | | | Chatter; elzerma closed the issue |

## #31 House teleport options

**Kind:** Idea · **Status:** open, `idea`; reply posted (draft P); closes once the update is out · **Reviewed through:** 2026-10-23 elzerma (draft P)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| iieu8827roj | Ask for the rune pouch instead of single runes; runes, not tabs, to the house (an ironman without a construction cape) | as #24 and #27 | Master names an owned pouch on the rune lines and has "Cast from runes, not tablets"; a banked pouch's runes don't count (#24) | **Already done (next update)**, linked to #24 |

**Nothing new** on #8 (reviewed through 2026-10-16 elzerma) or #16 (2026-10-17 elzerma, release reply).
