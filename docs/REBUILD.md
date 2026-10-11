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
- **Bank guide:** at a mid-run bank, outline items to deposit (noted IDs too), then the bank tab's withdraw
  list; done when the next inventory is carried.

### 5.5 Sidebar
- **Run tab:** run types (with today's Include/Skip), Build / Start / Stop, one status line ("6 items to get:
  open the bank tab"), the route list with bank breaks shown in place, total time and slots. Nothing that the
  bank tab or overlay already shows.
- **Farm / Travel / Account tabs:** as now, one class each; due % next to the run types; fixes from section 3.

### 5.6 Threading
Settings are immutable snapshots: the sidebar edits a copy and swaps it in; the client thread only reads
snapshots. Planning runs on the client thread only when inputs change, never per tick during a run.

## 6. Sprints

Each sprint ends with `./gradlew test` green, a commit on master, the dev client relaunched, and a numbered
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
- Checks: a full run start to finish, including one mid-run bank.

**Sprint 5: bank tab and Run tab.**
- Bank tab: deposit section at a mid-run bank, withdraw list per inventory.
- Slim Run tab (section 5.5).
- Checks: Gathering and mid-run banking only need the bank tab and overlay.

**Sprint 6: settings and pages.**
- Immutable settings snapshots; split `SetupPanel`; presets hold only Farm and Travel; Shortest Path setting;
  fix the mislabelled options and hints.
- Checks: every setting does what its label says (a short list).

**Sprint 7: tidy and release.**
- Guided tests per the decision below (and fix the two broken tests), docs (README, delete stale plans),
  screenshots, Plugin Hub PR update (with Sean's OK).

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

1. **Rebuild in place on master, sprint by sprint**, keeping the parts in section 4. Not a fresh project.
2. **Any number of bank breaks** per run, placed by the search.
3. **Bank teleports** considered: crafting cape (Crafting Guild), ring of dueling (Castle Wars), max cape,
   ring of wealth (Grand Exchange), plus regular teleports that land by a bank (Camelot -> Seers' with the
   diary, Varrock, Falador, Ardougne). The plugin brings one only when it's the cheapest way to bank.
4. **Shortest Path lines:** a Display setting, on by default (only does anything if it's installed).
5. **Guided tests:** kept for the dev client only; players don't see the help list or test box.
6. **Resurrect Crops:** removed until it can be guided properly.
7. **Run tab:** no supply list; the bank tab is the only place items are listed.
