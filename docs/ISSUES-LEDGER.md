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

**Kind:** Bug + ideas · **Status:** open, fixed in 96c706a and confirmed in game by elzerma 2026-10-16 (lava staff suggested; the fairy-ring stops came after the mid-run bank, where the dramen staff goes on); fix reply posted 2026-10-16 (draft B, approved); left open until the release by elzerma's choice; triage reply posted, ideas split to #16 (house) and #17 (report a problem) · **Reviewed through:** 2026-10-16 elzerma (fix reply)

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

**Kind:** Idea · **Status:** open. The section is built (option 2). Tests 1 and 2 passed, and elzerma asked for changes after each. The latest changes are built and wait on revision 3 of SEED_VAULT_SECTION. Two write-ups below wait on elzerma: the banking setting, and routing to the guild. · **Reviewed through:** 2026-10-16 elzerma (body)

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

### #8 write-up: how a run with seeds in the vault banks partway (not built; elzerma to choose the wording)

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

### #8 write-up: routing through the Farming Guild for seeds only in the vault (Explore; not built)

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

**Kind:** Test report · **Status:** open, replied; fixed in b2f2ad7, waiting on a re-run (ATES_CHARGES revision 2) · **Reviewed through:** 2026-10-16 elzerma (triage reply)

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
