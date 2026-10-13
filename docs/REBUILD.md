# Farm Run Autopilot: rebuild plan

Written 2026-10-11 after a full code review (every file in `src/main`, about 19,000 lines). The plugin grew by
patching: each fix to the route added another special case, and nobody (human or Claude) can predict what it
will do any more. This document says what the plugin is for, what we learned, what the review found, what to
keep, the new design, and the sprints to get there. **Every feature built so far stays;** the core is rebuilt
so the features work the way Sean intends.

Sean: read "1. What the plugin is for" and "8. Decisions" first. Correct anything that's wrong there before
Sprint 1 starts; everything else follows from those two sections.

---

## 1. What the plugin is for

Speed up farm runs and take the thinking out of them.

- **One continuous run** of whatever is due: herbs, trees and fruit trees together, in any mix. A typical
  schedule: sometimes all three, often herbs + fruit trees or herbs + trees, sometimes only herbs. Each run
  can be different, and the plugin plans each one from scratch.
- **Find what a player wouldn't think of.** Most players do a herb run, then a separate tree run. Combining
  them saves the reset in between, and the plugin should find overlaps and chains: the Gnome Stronghold, then
  the spirit tree to Tree Gnome Village; Falador Park, walk to Taverley, then the Explorer's ring to the
  Falador farm; Renu between Varlamore stops.
- **Bank when it's convenient.** When everything doesn't fit in one inventory, bank at a bank the route passes
  anyway. If there isn't one, use a teleport to a bank and carry on. As many bank breaks as the run needs.
- **Start where the player is.** Standing at or near a stop means that stop comes first.
- **Every step laid out.** The overlay says exactly what to do next and highlights it. "Run to" steps draw a
  Shortest Path walking line that never suggests teleports, jewellery or banks.
- **Banking is guided like Quest Helper.** The bank tab shows what to withdraw. At a mid-run bank it shows
  what to deposit (no longer needed) and what to withdraw for the next part.

What it is **not**: an automation tool. Nothing is clicked or typed for the player (Jagex rules, AGENTS.md).

## 2. What we learned (and must not repeat)

1. **Costs in two units.** Legs carry both "seconds" (shown to the player) and "cost" (seconds plus
   preference penalties), and they got mixed: walk seconds compared with teleport cost, fake 60/300 s fallback
   legs shown as real times. *Rule: one unit (seconds). Preferences are a separate tie-break, never added in.*
2. **Distances in several places.** `TravelTimes`, `Walk` buckets, `WALKS`, `SPIRIT_TREE_WALK`,
   `RENU_PAD_WALK`, `ringWalkTiles`, `Location.bankWalk` and a Port Phasmatys override all disagree (Taverley has
   three different values). *Rule: one travel graph, one place for every distance.*
3. **Banking bolted on after the route.** The route was planned for one inventory, then cut in two by patch
   type or by position. It could only bank where the cut landed, once, and costed the bank walk twice in one
   path and never costed the walk back. *Rule: a bank visit is a stop the search places, as many as needed.*
4. **The start decided in three places.** `RoutePlanner`, `RunService.fixedStart` and `firstHalfStops` each
   had their own rule. *Rule: one start resolver, looking only at stops in the run.*
5. **The plan kept changing mid-run.** The live plan is rebuilt from patch states every tick. Halfway through,
   finished patches are growing, so a patch type drops out and the mid-run bank tells the player to deposit
   seeds they still need. *Rule: the plan is frozen when the run starts; only the current inventory is
   re-checked against what the player holds.*
6. **Fixes without a model.** Each in-game report got a one-off constant (CIQ 60 -> 100 tiles, Phasmatys 95,
   prefer walking 8 s). *Rule: measured data in one table, scenario tests for every chain Sean expects, and a
   cost breakdown in the log so a bad route can be explained from the log alone.*
7. **Big classes doing everything.** `RunSession` (1,300 lines) is state machine, navigation, patch guidance,
   banking, highlights, reminders and timing. `SetupPanel` (1,545) is four pages in one class. `RunPanel`
   rebuilds the whole tab several times per tick.

## 3. Code review: what's wrong today

Most severe first. File references are to the code as of `ff310c0`.

### Route and banking
- Type splits count the bank walk twice (`planHalf` adds it as `endCost`, `splitAt` adds it again); the way
  back from a bank is never costed. (RunService `splitAt`, `splitInOrder`)
- Banking by teleport replaces the next leg but keeps the old supplies and the old route total, adds cost
  units to seconds, and skips the charge budget. (RunService `splitInOrder`)
- Charges are spent after the order is fixed, so an order planned around charges you don't have is never
  revisited. (RoutePlanner `spendCharges`)
- Learned times ignore where a leg starts, so a 14-tile spirit tree walk and a 60-tile one are averaged.
  (LearnedTimes key)
- The nearby-fairy-ring walk uses the old `Walk` bucket (CIR 28 tiles) instead of `TravelTimes` (90), and a
  test locks the wrong value in. (RoutePlanner `ringWalkTiles`, RoutePlannerTest)
- Going via the house never adds the spellbook swap for Teleport to House on Lunar/Arceuus.
- `nearby()` can pick a stop that isn't in the run and then drop the start; Own order ignores the start.
- Both halves of a split are costed against the full stock (5 ranarr seeds counted in each half).
- Dead or misleading: `TravelMethod.directions` (never set), `Location.internalWalk`, `hasToolLeprechaun`,
  `primaryFor`, `PohAmenity`, `chainsFrom` (builds a planner it never uses), orphan javadoc.

### Run guide, bank tab, supplies
- **Mid-run bank reads the live plan, not the frozen one** (RunSession `isReady`, `toDeposit`, `fromSeedVault`;
  bank tab `runService::getSupplies`). Can tell the player to deposit seeds still needed, skip the bank
  silently, or never finish banking. `BankStop.supplies` (the right list) only feeds the Run tab text.
- The run record and best times store only the first half of a split run.
- Noted items are never outlined for deposit (HoldingsTracker un-notes IDs, the overlay matches raw IDs).
- Seed vault counted for readiness only when the Farming Guild is the first stop, but used at any stop.
- Bank withdraw/deposit clicks on a whistle or talisman count as teleports (ItemChargeTracker).
- Diseased trees and fruit trees are told to use plant cure; they need pruning with secateurs. Planting
  highlights the seed but not the dibber/spade.
- Compost can be credited to the wrong patch at stops with several patches.
- Patch states are read one tick late (session runs before the tracker updates).
- Worn travel items still count an inventory slot, so splits happen when they needn't.
- No Shortest Path line to the bank; the Run tab shows no withdraw list while banking.

### UI, settings, threading
- Settings objects are edited on the Swing thread while the planner reads them on the client thread.
  `PatchDebugPanel`/`RunPanel` call the client from Swing.
- Guided tests: the Fortis Champion test can't pass (plant cure always on the list when doses > 0); the house
  scan test promises nexus detection that doesn't exist; `shutDown` doesn't end a running test.
- Shortest Path is always on with no setting, and overrides about 25 of the player's Shortest Path settings
  per step (key names unverified).
- Presets include account-wide and debug switches, so ticking a debug box shows "Custom (changed)".
- Resurrect Crops only adds runes; nothing tells the player to cast it. "Suggest yield boosts" says Farming
  cape/outfit but only adds secateurs. Several "change this in" hints name sections that don't exist.
- The Run tab duplicates the bank tab (full supply list, legend, bank-stop item list, coins/runes) and the
  overlay (instruction and clock while running).
- Stale docs: `docs/plans/sidebar-ux.md` and `teleport-preferences.md` status lines, README presets location,
  milestone tags (M4, M8...) in code comments.

No Plugin Hub hard-rule violations were found (no reflection, Desktop, file IO, HTTP, per-frame `log.info`).

## 4. Keep, rewrite, delete

**Keep (move, rename or trim only):**
- Game knowledge: `Patch`, `PatchPoints`, `PatchStateDecoder`, `Crop`, `Requirement`, `Unlock`,
  `AchievementDiary`, `Spell`, `TravelItem`, `ItemCharges`, `DailyLimits`, `JourneyStep`, POH enums,
  `SupplyItems`, `MetaOrder`, `TravelTimes` (becomes the travel graph's data).
- Reading the game: `AccessChecker`/`AccessSnapshot`, `UnlockSpotter`, `PohDetector`, `HoldingsTracker`/
  `Holdings`, `ItemChargeTracker` (fix the bank-click bug), `BottomlessBucketTracker`, `PatchTracker`,
  `PatchPredictor`, `FarmingTick`, `SceneTracker`.
- `RunSelector`, `RunOverrides`, `TypeOverride` (what's due and included).
- `StepAdvisor` (state-driven patch steps; fix diseased trees and tools).
- `FarmBankTab` + `FarmBankTabButton` (the Quest Helper bank tab) as the only supply view.
- `ShortestPathBridge` (behind a setting; verify the keys against Shortest Path's source).
- Overlays (`GuidanceOverlay`, highlight overlays, `HintArrowController`), small UI parts (`TabBar`, `Choice`,
  `CollapsibleSection`, `ExpandableRow`, `UiText`), `SettingsStore` and the `sanitise()` migrations.
- The search code in `RoutePlanner` (`exactOrder` Held-Karp, `improve` 2-opt/or-opt): correct, reused.
- Tests: `RouteChainTest` scenarios (Sean's expected chains, the acceptance suite), `RunSelectorTest`,
  `LearnedTimesTest`, `SpellbookRuleTest`, supply and decoder tests.

**Rewrite:**
- The cost model and leg generation (RoutePlanner) as a travel graph in seconds.
- Banking: replace `split*`, `Split`, `BankStop`, `fixedStart`, `firstHalfStops` with bank stops the search
  places.
- `RunPlan`: a frozen list of steps and per-inventory supplies.
- `RunSession`: split into a state machine, navigator, patch guide and bank guide.
- `RunPanel`: a slim Run tab. `SetupPanel`: one class per page.

**Delete:** `Walk` buckets (once every method is measured), `WALKS`/`SPIRIT_TREE_WALK`/`RENU_PAD_WALK`/
`ringWalkTiles` (folded into the graph), Port Phasmatys override, dead fields listed in section 3,
`SupplyLine.coveredOtherwise`, Resurrect Crops (or finish it, see decisions).

## 5. The new design

### 5.1 Travel graph (one source of every distance)
- **Places:** each stop's patch spots (`PatchPoints`), each bank (booth/chest coordinates), each teleport
  landing, spirit trees, fairy rings, quetzal pads, Renu, boats.
- **Ways between places:** teleports (from anywhere), spirit trees, fairy rings, quetzal/Renu, boats, and
  walks with measured tiles (agility shortcuts as variants with their requirements).
- **One leg cost in seconds** = cast/rub/animation + measured walk tiles x seconds per tile (0.3 s running
  today; checked once against Sean's recorded legs in Sprint 1) + fixed per-step extras (boat, Elkoy, gate). The
  player's own learned times replace the estimate once there are 3 clean samples, keyed by
  `from -> to -> way`.
- **Preferences are tie-breaks:** "prefer walking", travel style (fastest / save charges / fewest clicks) only
  choose between ways within a few seconds of each other; they never change a leg's seconds.
- Every leg can print its breakdown to the log ("Catherby via Camelot Teleport: 3 s cast + 56 tiles 17 s").

### 5.2 Planning a run
1. **Select** what's due (`RunSelector`, unchanged).
2. **Resolve the start:** standing at a stop in the run -> that stop; else the chosen Start at; else the
   nearest stop in the run within walking range (if any); else anywhere.
3. **Order the stops** by total seconds (Held-Karp up to ~14 stops, 2-opt/or-opt above), with charges
   budgeted inside the search (plan, spend charges, re-plan the order if a charge ran out).
4. **Fit the inventory:** walk the order, filling an inventory with each stop's supplies (stock subtracted
   as it's used, worn items take no slot). When the next stop won't fit, insert a bank visit in the cheapest
   place before it: a bank near a stop already in that stretch (detour there and back), or a bank-teleport
   item (crafting cape, ring of dueling, max cape, ring of wealth...) used between two stops. Dynamic
   programming over the gaps finds the cheapest set of bank visits; any number is allowed.
5. **Improve once more** with bank visits in place (or-opt can move a stop across a bank), then freeze.
6. **Explain:** log the order, each leg's breakdown and each bank choice with the alternatives it beat.

### 5.3 The plan the run follows
`RunPlan` = an ordered list of steps (`TravelTo stop`, `WorkPatches stop`, `Bank at place`) plus one
`Inventory` per stretch between banks: what to carry, what to deposit at the bank that starts it, what to
withdraw. Built once when the player presses Build (re-built while they're still gathering supplies), frozen
when the run starts. The bank tab, the deposit outline, readiness, the Run tab and the overlay all read this
one object.

### 5.4 Guiding the run
- **State machine:** Off -> Gathering (bank tab shows inventory 1) -> Ready -> Running -> Banking (bank tab
  shows deposit + withdraw for the next inventory) -> Running ... -> Done.
- **Navigator:** travel steps and journey steps (boat, Elkoy, Renu...), arrival detection, Shortest Path
  walking line to the patch, the bank, or the next journey step.
- **Patch guide:** `StepAdvisor` per patch (harvest, clear, cure/prune, compost, plant with the right tool,
  protect), with highlights.
- **Bank guide:** while gathering and at every mid-run bank, three steps: open a bank (booth or chest
  outlined, walking line), click the Farm run tab (icon outlined and pulsing, or opened by itself), then
  deposit (outlined, noted IDs too) and withdraw (highlighted in the tab); done when the next inventory is
  carried. See decision 8.7.

### 5.5 Sidebar
- **Run tab:** run types (with today's Include/Skip), Build / Start / Stop, one status line with the same next
  step as the overlay ("6 items to get: open a bank, then the Farm run tab"), the route list with bank breaks shown in place, total time and slots. Nothing that the
  bank tab or overlay already shows.
- **Farm / Travel / Account tabs:** as now, one class each; due % next to the run types; fixes from section 3.

### 5.6 Threading
Settings are immutable snapshots: the sidebar edits a copy and swaps it in; the client thread only reads
snapshots. Planning runs on the client thread only when inputs change, never per tick during a run.

## 6. Sprints

Each sprint ends with `./gradlew test` green, a commit on the `rebuild` branch, the dev client relaunched, and a numbered
in-game checklist for Sean. A sprint isn't done until Sean has run its checks.

**Sprint 0: agree the target (no code).** Sean reads sections 1 and 8, corrects them, and lists anything
missing from his vision. Turn every chain or behaviour he names into a scenario test (failing is fine).

**Sprint 1: travel graph and costs.**
- Build the graph from `TravelTimes`, `PatchPoints`, bank coordinates (measure the missing ones), journeys.
- One seconds-only leg cost; preferences as tie-break; learned times keyed by from/to/way.
- Calibrate seconds per tile and per-step extras against Sean's recorded legs (`RunTimings`).
- Delete `Walk` buckets and the scattered walk maps. Fix the CIR test.
- Checks: leg times in the Run tab look right for 5 legs Sean knows (e.g. CIQ is slow, Stronghold spirit tree).

**Sprint 2: route search with bank visits.**
- Start resolver; ordering with charges inside the search; inventory fitting with bank visits (stop banks
  and bank teleports), any number of them; explain log.
- Delete the old split code.
- Checks: near Falador Park the run starts there; Stronghold -> spirit tree -> Tree Gnome Village; a big run
  banks somewhere Sean agrees with; herbs-only and herbs+trees runs look sensible.

**Sprint 3: frozen plan and per-inventory supplies.**
- `RunPlan` with steps and inventories; stock subtracted between inventories; worn items take no slot; run
  record covers the whole run.
- Checks: the supply counts for each part of a big run add up to what Sean has.

**Sprint 4: run guide rewrite.**
- State machine, navigator, patch guide, bank guide replacing `RunSession`.
- Fix: diseased trees prune, tools highlighted, compost credited to the right patch, patch states read after
  the tracker updates, Shortest Path to the bank, noted items outlined, whistle bank clicks not charged.
- Wear and swap steps (decision 8.8): before the run and at each bank, what to put on, item outlined.
- Checks: a full run start to finish, including one mid-run bank.

**Sprint 5: bank tab and Run tab.**
- Bank tab: deposit section at a mid-run bank, withdraw list per inventory.
- Guidance to the bank tab (decision 8.7): "Open a bank", the icon outlined and pulsing, the optional
  auto-open setting. Add a guided test if any of it relies on an unverified widget.
- Slim Run tab (section 5.5).
- Checks: with the bank closed the overlay says to open one; on opening a bank the Farm run icon is
  impossible to miss (or the tab opens by itself); the highlight stops once it's open; gathering and mid-run
  banking only need the bank tab and overlay.

**Sprint 6: settings and pages.**
- Immutable settings snapshots; split `SetupPanel`; presets hold only Farm and Travel; Shortest Path setting;
  fix the mislabelled options and hints.
- Checks: every setting does what its label says (a short list).

**Sprint 7: UI facelift** (Sean, 2026-10-12: "a LOT to look at"). Before the release, so its screenshots show it.
- **Say each thing in one place** (Sean, 2026-10-12, a rule for the whole UI): if the overlay, a tooltip or another tab
  already says it, the panel doesn't repeat it. E.g. the next step is under the player only, not also on the Run tab.
- Explanations move into hover tooltips: no note lines under checkboxes and dropdowns; a control's label says what
  it is, its tooltip says why and what it does. Only warnings that need action stay visible.
- Shorter labels and summaries, the same spacing and fonts everywhere, fewer headings; sections closed by
  default except the one or two changed most.
- Run checklist as in Quest Helper (Sean, 2026-10-12): tick boxes on stops, bank visits and the current row's
  steps; the plugin ticks what it sees done (locked), the player ticks what it missed and can untick those to go
  back. Mark done and Skip stop go; the clock and Stop share one line. Bank visits are rows of their own.
- A calculated item missing mid-run turns the current task into a bank visit (Sean, 2026-10-12): straight away
  if the current step needs it, otherwise after the current stop; none if a planned bank visit comes first.
- Second pass (Sean, 2026-10-12): five icon tabs (Run, Farm, Travel, Account, Settings). Account is what you
  have (house, unlocks, storage); Settings holds Presets, Display, Debug (Detected inside), Reset and the help list
  last. One soft radius and flat controls (FlatLaf style strings, ui/Style.java); white bold headings. Patch
  timers one line each by type; Farm patches in groups two to a row; crops one line per choice; per-patch
  protection behind a tick box; Travel locations one line each.
- Run tab: the status line, route and buttons only; Account > Detected as a compact two-column list.
- Measure it: visible lines per tab before and after, and screenshots of each tab for Sean to compare.
- Checks: every control still has its explanation on hover; nothing a player needs is only in a tooltip.

**Sprint 8: Shortest Path data** (after Sprint 7; plan in docs/plans/shortest-path-research.md). Reuse its BSD-2
licensed data with credit: bank coordinates, teleport landing points and durations checked against ours, a
dev-only "Measure walks" tool using its query message to measure every landing-to-patch walk once and save
the numbers in TravelTimes, and research on the Trinket of fairies. Also hot air balloons (Taverley, Grand Tree, Castle
Wars), bank distance from the last patch done at a stop, measured walks for every landing and bank, and start points chosen by walking distance (Castle Wars must not suggest Lletya).

**Sprint 9: sourced data and crowdsourcing** (Sean, 2026-10-12; plan in docs/plans/sourced-data.md). Most
guessed game values come from the game's own client scripts, Shortest Path's data and RuneLite's code instead of
players: Karamja diary done at 2 (bug), bottomless bucket 1 = empty (bug), leprechaun counts by the script's
multipliers, statues of Ates confirmed, Trollheim tablet, eternal crystal, Barnaby's spot, the nexus Troll
Stronghold landing. Players then only test behaviour (the help list, plus a bottomless bucket test) and can
share their run times through an opt-in GitHub report.

**Sprint 10: tidy and release** (last, and only once everything works: Sean, 2026-10-12).
- Journey step text that fits the player (e.g. leave out "without 33 Agility, walk round" when they have it).
- Guided tests: fix the two broken tests and bring every test in line with the rebuilt run, docs (README, delete stale plans),
  screenshots, Plugin Hub PR update (with Sean's OK).
- Starts only when Sprints 1-9 all pass in game; then merge `rebuild` into master and release.

## 7. Features that must survive the rebuild

Due tracking and timers; run types with per-run Include/Skip; presets; crop choices with backups and
disease-free herbs; protection (pay gardener, notes, clear for 200); compost and plant cure; outfit and
secateurs; per-location teleport choice and how (direct, house nexus, jewellery box, fairy ring way);
tablets vs runes; keep last charge; travel styles; charges and daily teleports read from the game; unlocks read
from varbits and spotted in the world; house detection (beta); quetzal/Renu, journeys (boat, Elkoy, roof,
stones...); Start at; finish near a bank; meta and own order; drag to reorder; storage sources (seed vault,
group storage, leprechaun, bottomless bucket); bank tab; overlay steps and highlights (objects, NPCs,
inventory, spellbook, hint arrow); drop reminders; Shortest Path lines; leg timing and learned times; best
times; patch details/debug; the help list and guided tests (see decisions).

## 8. Decisions (my defaults; Sean, change any you disagree with)

1. **Rebuild on the `rebuild` branch, sprint by sprint**, keeping the parts in section 4. Not a fresh project.
   master stays the working, released plugin (urgent fixes go there and are merged into `rebuild`); `rebuild`
   merges into master in Sprint 10, the last sprint, once everything works in game (Sean, 2026-10-12).
2. **Any number of bank breaks** per run, placed by the search.
3. **Bank teleports** considered: crafting cape (Crafting Guild), ring of dueling (Castle Wars), max cape,
   ring of wealth (Grand Exchange), plus regular teleports that land by a bank (Camelot -> Seers' with the
   diary, Varrock, Falador, Ardougne). The plugin brings one only when it's the cheapest way to bank.
4. **Shortest Path lines:** a Display setting, on by default (only does anything if it's installed).
5. **Guided tests:** kept for everyone, as now (Sean, 2026-10-11): players run them and open a prefilled GitHub
   issue with the results; the dev client logs and copies the summary. Each sprint adds or rewords tests for
   anything Sean can't try on his own account.
6. **Resurrect Crops:** removed until it can be guided properly.
7. **Run tab:** no supply list; the bank tab is the only place items are listed. Because the bank tab's icon
   is small and easy to miss, the player is walked to it (Sean, 2026-10-11), at the start and at every
   mid-run bank:
   1. **Bank closed:** the overlay says "Open a bank". At a mid-run bank, that bank's booth or chest is
      outlined and Shortest Path draws the walk to it.
   2. **Bank open, Farm run tab not selected:** the overlay says "Click the Farm run tab" and the icon in the
      bank interface is outlined and pulses until it's clicked.
   3. **Farm run tab open:** at a mid-run bank, the items to deposit are outlined in the inventory first; then
      the items to withdraw are highlighted in the tab.
   - Option (a setting, on by default): open the Farm run tab by itself when the bank opens during gathering
     or a mid-run bank, so the highlight is a fallback rather than the only cue. This only changes our own
     view of the bank, sends nothing to the server, and must be checked against Plugin Hub rules first.
   - The Run tab's status line shows the same next step ("Open a bank, then the Farm run tab").
8. **Wear teleport items, and swap them at banks** (Sean, 2026-10-11): rings, capes, necklaces, bracelets and
   Rada's blessing can be worn, one per equipment slot, instead of taking an inventory slot (an outfit such as
   Graceful takes its slots first). The slot count already assumes this (Sprint 3). The guide then says what to
   put on before the run ("Wear your ring of wealth and Ardougne cloak") and at each bank what to swap ("Swap:
   wear your slayer ring"), with the item outlined (Sprints 4-5).
   - The wear step is its own message under the character ("Wear: Graceful outfit, ring of wealth, Ardougne
     cloak"), including the outfit chosen in Farm > Extras, so it's obvious without hovering anything.
   - Items to wear get their own highlight colour and a "Wear" label, never the colour used for "use this to
     teleport", so nobody mistakes an item to put on for the next teleport. Once it's worn the highlight goes.
   - The Run tab's status line says the same, not only a tooltip. (Sean, 2026-10-11)
