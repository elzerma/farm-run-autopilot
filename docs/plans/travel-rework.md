# Plan: travel rework (a travel atlas built with Shortest Path in a bubble)

Written 2026-10-17, planning only (nothing built). Answers elzerma's "MAJOR issues with travelling data" and their idea of
using Shortest Path's data and pathfinding to find the fastest way to every patch. Shortest Path was checked at commit
`d98d478` (2026-10-17, `master`); the plugin's earlier research is in `shortest-path-research.md`,
`travel-research.md` and `sourced-data.md`.

**Decided by elzerma (2026-10-17):** the atlas built with Shortest Path in a bubble, "walk through all relevant teleport
methods to determine a true fastest, then develop our own algo for selecting methods for saving inv slots (potentially
reducing expected amounts of bank trips)" (section 4, last part); **this comes first**: crop types wait and will run on
it, planned for the day the plugin handles **all** crop types (section 3, step 2); "Share your run times" is removed;
Auto uses every way except Wilderness and cooldown teleports by default; times are compared per patch type, with a
split at each stop (section 5); a bank stop's time is balanced against the fastest ways (section 4). Only whether the
"Fewest items" style goes is left, for the start of T4. 2026-10-18: the Travel tab's per-location list of the plugin's
own methods goes; every run is planned from every way the atlas knows, from the bank the player gears up at, and
optimised (section 6, T4, T7).

## 0. In short

- **Your idea works, but not inside the game.** The plugin can't move or teleport you, and a script that clicks through
  teleports breaks Jagex's rules and gets accounts banned. It doesn't need the game, though: **Shortest Path's
  pathfinder runs on its own on a computer**, with a pretend account (made-up levels, quests, unlocks and items) on its
  real map and transport data. Its own tests already do this. That is the "bubble".
- **What the bubble does:** a tool run by us (not by players) asks it thousands of "fastest way from here to that
  patch" questions: from every teleport landing, patch and bank to every patch of every type. It writes the answers
  to a data file the plugin ships. Nothing from Shortest Path runs in the plugin, and players don't need it installed.
- **Teleports you don't have:** the pretend account starts with everything. For each answer the tool then takes away
  what it used (45 Agility, a quest, a dramen staff, a planted spirit tree), asks again, and keeps the next-best way.
  Each answer says what it needs, so in game the plugin picks the fastest way **your** account can use. Methods
  nobody on the team owns are still covered.
- **Debug mode:** Shortest Path has none for this, and we don't need one: the bubble asks it directly.
- **Then our own part:** with the true fastest way for every leg known, the plugin chooses ways for the whole run,
  trading a few seconds for inventory slots when that saves a bank trip (one skills necklace for three legs instead
  of three different items, a staff instead of runes).
- **Timing (your rule, 2026-10-17):** no crowdsourcing. Routes come from the map data only. Your own times are only
  used for how long the plugin says a run will take, and for a personal best to beat (with splits per stop).

## 1. Problems found

elzerma's examples (picker, 2026-10-17): Harmony Island has only the teleport, the tablet and the nexus, where
Shortest Path would still find a way; the plugin picks slow teleports; leg times are wrong; ways to travel are missing.
No stop stands out ("just know that we can do better"). Reading the code and the history explains why:

1. **A hand-kept list of 90 ways** (`data/travel/TravelMethod.java`), each "one teleport, then walk to the nearest
   patch". A journey that isn't typed in doesn't exist. Harmony Island (line 223) has only the spell, tablet and nexus;
   the charter ship or Trouble Brewing teleport to Mos Le'Harmless, then Brother Tranquility, are unknown (both are in
   Shortest Path's `charter_ships.tsv`, `teleportation_minigames.tsv` and `transports.tsv`). The same goes for every
   gnome glider, charter ship, canoe, minecart, magic carpet and mushtree, apart from the few typed in by hand (the
   Ardougne boat to Brimhaven, the Grand Tree balloon); the class comment says the rest "are not planned".
2. **Numbers copied by hand** from a tool's output into `TravelTimes.java`, with estimates still in it: royal seed pod
   (line 54), Mount Karuulm (62), Stony basalt entrance (123), the Tree Gnome Village tree (216), the Stronghold bank
   (240), the Port Phasmatys bank (248). Hosidius by skills necklace was a 76-tile guess until today (79 plus the 45
   Agility stepping stone, 121 without it: `236f96a`).
3. **One walk per way**, to the stop's nearest patch, whichever patch is visited first or last. Only three walks between
   stops are known (`TravelTimes` 209-211); no other pair of stops can be walked.
4. **What a walk needs is coded case by case.** Only Falador to Taverley switches on Agility (66 rocks). Hosidius assumes
   the 45 stepping stone and Nemus the 33 tunnel, so players without them get times that are far too short.
5. **The time model has fudge factors:** one 8 s `LEG_OVERHEAD` for everything, flat cast times (3.0 / 3.6 s against
   Shortest Path's 4 ticks = 2.4 s), and extras from single recorded legs (CIQ +47 s, lines 71-74).
6. **Learned times choose the route.** `RoutePlanner.quickest` blends the player's recorded legs into the choice. A way
   that loses is never used, so never timed again: one slow Book of the Dead leg locked it out (`a3af137` added an
   outlier rule to patch that). It also makes routes differ between runs and hard to test.
7. **Special cases pile up:** fairy ring ways, Renu from a pad, the balloon basket, the spirit tree into the house, each
   with its own code in `RoutePlanner.options` and a `Departure` value. `docs/REBUILD.md` already asked for "one travel
   graph, one place for every distance"; Sprint 8 measured walks but kept the hand list.
8. **Every new stop needs hand research.** Crop types 1b and 1c (`crop-types.md`) add Rimmington, Champions' Guild,
   Etceteria, Al Kharid, Canifis, Draynor Manor, Fossil Island (underwater), Tai Bwo Wannai, the Farming Guild's special
   trees and more. With the current design each one is a new research table and more hand-copied numbers.

## 2. The idea, honestly, and the approach

### What the plugin may and may not do in game

- **May not:** move the player, teleport, click, type, or run any "script" in the game. Injecting input is forbidden by
  the Plugin Hub rules and Jagex's guidelines (AGENTS.md). Timing every teleport by hand would also need every teleport.
- **May:** watch where the player is each game tick while they play normally, time runs and legs, and send Shortest
  Path messages (it already draws our walking lines).

### What Shortest Path offers (BSD-2)

- **Data:** the collision map (`collision-map.zip`, 1.6 MB) and 26 transport files (about 10,000 rows): spells, items,
  tablets, minigame and home teleports, POH portals, jewellery box, fairy rings, spirit trees, quetzals, boats, charter
  ships, gliders, balloons, carpets, minecarts, canoes, agility shortcuts, doors, ladders and NPC transports. Each row
  has its landing, duration in ticks and what it needs: skills, quests, items, varbits, varplayers, wilderness level.
  Format: `docs/Transport-TSV-format.md` in their repo.
- **A pathfinder that runs outside the game.** `src/test/.../pathfinder/PathfinderTest.java`, `TestPathfinderConfig.java`
  and `TestWorldData.java` load the real map and transports, mock the RuneLite `Client` (levels, quest states, varbits,
  inventory, worn items, bank) and run `new Pathfinder(config, start, targets).run()`. The result has the path's tiles,
  whether it got there, the cost, and every transport used with its menu text and object or NPC id.
- **An in-game API** (`docs/Cross-plugin-communication.md`): `query` returns a `result` with the path, the cost in
  ticks, the transports used and a reason, so it can do more than draw. But it needs Shortest Path installed and
  running, uses the player's own Shortest Path settings and "bank as last seen", always uses its older pathfinder,
  is cancelled whenever a new path is drawn (ours included), answers later on its own thread, and counts a walked tile
  as one tick, so walking weighs twice what running does next to a teleport.

### Recommended: the travel atlas

A dev-only tool (outside the plugin build, like `tools/walk`) runs Shortest Path's own pathfinder and data, at a pinned
commit, with pretend accounts. It answers "the fastest way from this place to that patch, with no further teleport"
for every place a route can be (teleport landings, patches, banks, house exits) and every patch and bank of every patch
type, including the 1b and 1c types once their patches are listed. Each answer keeps a few alternatives, each with
what it needs. The plugin ships the answers as a data file and adds the teleport choice itself:

> leg = the best usable start (no teleport, or a teleport the player can cast or rub) + the atlas answer from where it
> lands, using the fastest alternative the player meets.

Why this one:
- It fixes the causes, not the cases: missing ways appear on their own (Harmony by charter ship and Brother Tranquility
  is just another answer), guesses and hand copying go, and what each walk needs is checked per player.
- Every patch type and every method is covered, whether or not anyone on the team owns it.
- One source for every distance, rebuilt by one command, with a report of what changed.
- No runtime dependency: planning stays instant, works without Shortest Path, and the plugin stays our own. Only facts
  (tiles, ticks, steps, requirements) ship, with the BSD-2 notice.

### Alternatives rejected

| Alternative | Why not |
|---|---|
| A. An in-game script that times every teleport | Not allowed (input automation, account bans) and impossible without owning everything. |
| B. Plan live through Shortest Path's `query` API | Players would need it installed; a route needs thousands of legs and options; answers are slow, cancellable and use the player's Shortest Path settings; walking cost is doubled; it can't answer "what if I fetch X from the bank". Kept only as a dev-client second opinion (sprint T8). |
| C. Copy Shortest Path's pathfinder into the plugin | Ships their code, which elzerma doesn't want; holds the 1.6 MB map in memory; heavy work while planning; breaks whenever they change it. |
| D. Keep the hand tables and fix case by case | What we have done since 2026-10-10 (sections 1.1-1.8); it can't find ways nobody typed in. |
| E. Our own pathfinder over Shortest Path's data | Works, and is the fallback if the bubble harness turns out fragile (decided in T0), but we'd have to copy their rules for doors, transports and requirements and keep them in step. |

## 3. The data pipeline (dev only)

New folder `tools/atlas/` with its own small Gradle build, never part of the plugin's build. It compiles Shortest Path's
main sources unchanged from a pinned local clone (next to the repo, as `tools/walk/README.md` does), with our harness,
`runelite-client` and Mockito for the pretend client.

1. **Pin.** The Shortest Path commit the Plugin Hub ships, recorded in the output and in `THIRD_PARTY_NOTICES`.
2. **Our inputs.** `patches.tsv`: **every farming patch in the game**, whether or not the plugin runs that type yet:
   the types it runs (from `data/Patch` and `PatchPoints`, so they can't drift), the 1b and 1c types
   (`crop-types.md` sections 3-4: bushes, cactus, mushroom, belladonna, seaweed, hardwoods, calquat, celastrus,
   redwood, spirit trees) and the ones that plan leaves out (anima, Hespori, crystal tree, grapes, coral, the
   Keldagrim hops and Anglers' Retreat flax patches), from RuneLite's `FarmingWorld` where it tracks them and the
   wiki's map points otherwise. Rows for types the plugin doesn't run are marked "atlas only", so a crop-type sprint
   only switches its patches on and never touches travel. Compost bins and the tool leprechauns by them are added as
   places too. `extras.tsv`: steps Shortest
   Path can't model (following Elkoy through the maze, the Troll Stronghold inside, the Grand Tree door), each with a
   source note.
3. **Places to search from.** The landing of every teleport usable from anywhere (spells, items, tablets, minigame and
   home teleports, POH portal, nexus and jewellery box destinations), every patch, every bank by a stop. Seasonal and
   Leagues rows are left out; Wilderness rows are kept and marked.
4. **Places to reach.** Every patch (each one, not "the stop") and every bank by a stop.
5. **Ask the bubble.** For each pair worth asking (a quick walking estimate drops pairs that can't be useful, except
   through a transport), run the pathfinder with teleports off and every other transport on, for a pretend account
   with everything.
6. **Peel for alternatives.** Collect what the answer used (a level, a quest, items, a varbit), take each away in turn
   (the level one below, the quest not started, the items gone, the varbit unset) and ask again; repeat on the new
   answers, keeping at most 6 per pair and always the "needs nothing" way when there is one.
7. **Re-time.** Shortest Path's cost counts a walked tile as a tick, so the tool stores **walking tiles** and
   **transport ticks** separately and the plugin turns them into seconds at running speed (section 5).
8. **Write** `src/main/resources/com/farmrunautopilot/travel-atlas.tsv` (answers: tiles, ticks, requirements and steps,
   each step with its kind, menu text, object or NPC id and tiles), `teleports.tsv` (each teleport: landing, cast
   ticks, items, levels, quests, varbits, charges or cooldown, Wilderness level, name), and rebuild `walk-grid.bin`
   the same way. Target under 1 MB; read with our own parser (no Java serialization).
9. **Report** `tools/atlas/report.md`: ways added and removed since the last build, times that moved more than 10%,
   patches nothing reaches, and pretend-account checks (every patch reachable by a maxed account; which patches a fresh
   account can't reach).
10. **Referee.** The same bubble with teleports on, for three pretend accounts (one like elzerma's, a mid-level one, a
    maxed one), from 20 starting points to every stop. A test then compares the plugin's own leg choice with it and
    lists every leg where Shortest Path is quicker. That list is the evidence elzerma asked for.
11. **Refresh** at each plugin release: rebuild, read the report, commit data and report together.

**As built in T1 (2026-10-18), where it differs from the steps above:** the transport networks (fairy rings, spirit
trees, quetzals, gnome gliders, charter ships, magic carpets, mushtrees, canoes, minecarts, balloons, obelisks) are
**hops the plugin adds itself**, like teleports: ways walk, use doors, stairs, shortcuts and single boats, and go to a
network's **hubs** (a ring, a tree, a pad) as places; `hops.tsv` lists every hop from a hub to where it lands. With the
networks inside the ways, nearly every landing was "near" nearly every patch (62,378 pairs; hours to build). Patches of
one type at one stop within 8 tiles share a place, landings within 2 tiles are one, and starts are asked about only
within 300 of Shortest Path's units (150 for banks and hubs). The files are `places.tsv`, `ways.tsv`, `teleports.tsv`,
`hops.tsv`, `transports.tsv` and `requirements.tsv` in `src/main/resources/com/farmrunautopilot/atlas/`
(`tools/atlas/README.md`). `walk-grid.bin` is left as it is until the plugin's stops change. Mockito wasn't needed: the
pretend client and settings are plain Java proxies.

**Licence and credit:** Shortest Path's code runs only on a developer's machine; the plugin ships derived facts, as
the notice already says for Sprint 8's walks. Update `THIRD_PARTY_NOTICES` (commit, which files, what's derived) and
the README credit. The harness is ours; the way it fakes a client follows their tests, credited in its README.

## 4. Runtime changes

- **New data classes** in `data/travel/`: `TravelAtlas` (loads the atlas, loaded the way `WalkGrid` is), `Teleport`
  (one row of `teleports.tsv`), `AtlasStep`. `TravelMethod` stops being a hand list: generic teleports come from data,
  and a small link table ties a teleport to our `TravelItem` / `Spell` / `ItemCharges` / `DailyLimits` where we track
  charges, runes, daily limits, Spellbook Swap and highlights.
- **Requirements from data:** Shortest Path's skills, quests, varbits, varplayers and items map onto our `Requirement`,
  `AccessSnapshot` and `Holdings` (varbits read on the client thread, as now). Their `UNLOCK_*` tokens map to our
  `Unlock` entries. Varbits we haven't confirmed get guided tests (section 8).
- **Legs:** from where the player is (the last patch done, a bank, or their tile via the walk grid) to the next stop's
  first patch, the cheapest of: the atlas way from here; each usable teleport plus the way from its landing; a hop
  (fairy ring, spirit tree, quetzal pad, balloon...) reached on foot or after a teleport, plus the way from where it
  lands, so those stop being special cases; the house (`PohSetup`: teleport in, then each portal, nexus, jewellery box,
  fairy ring or spirit tree exit, plus the spirit tree into the house from issue #16) plus the way from that exit's
  landing. (`data/travel/atlas/AtlasLegs` does all but the house since T2.)
- **Fairy ring ways** (`FairyRingAccess`: Ardougne cloak to the monastery ring, slayer ring, quest cape) become ordinary
  teleports whose atlas answer walks to the ring and hops. Most `Departure` values go; the house ones stay.
- **Inside a stop:** patch order and patch-to-patch walks from the atlas; the bank walk from the actual last patch.
- **Items a way needs** (coins for a charter ship or boat, logs for a balloon, a dramen staff) go onto the supply list
  through `BankPlanner`, as the balloon's log does now.
- **Choosing stays as it is** (seconds plus the travel style's tie-breaks and charge costs), minus learned times.
- **Deleted:** `TILES`, `EXTRA_SECONDS`, `WALKS`, `SPIRIT_TREE_WALK`, `RENU_PAD_WALK`, `BALLOON_WALK`, `BANK_TILES`,
  `BANK_POINTS` and most hand `JourneyStep`s; `LearnedTimes.adjust` in `RoutePlanner.quickest` and the outlier rules.

### Choosing ways with the inventory in mind (our own algorithm)

The atlas tells us the true fastest way for every leg. The fastest way per leg isn't always the fastest run: each way
needs things carried, and when they and the farming supplies don't fit, the run needs a bank trip. Today that trade is
spread over several rules (worn items take no slot, a staff is worth the slots its rune types take (issue #7), the
rune pouch, pooled charges, the "Fewest items" style). This sprint joins them into one choice for the whole run.

- **Each candidate way has a footprint:** what it needs carried. A teleport item takes a slot unless it is worn in a
  free slot; a spell takes a slot per rune type unless a wielded staff or the rune pouch covers it; tablets take a slot
  per kind; coins one slot shared by every fee; a balloon log; the dramen staff. Footprints combine as a **set**: one
  skills necklace serves the Farming Guild, Hosidius (Woodcutting Guild) and Ardougne (Fishing Guild) legs for one slot,
  and every spell needing law runes shares that rune's slot.
- **The goal is the run's total time**, legs plus bank trips (elzerma: "a bank stop is going to cost some amount of
  time. it needs to balance between making another bank stop and being absolute fastest"). A bank trip costs its real
  detour from the atlas (to the bank and on to the next stop) plus the banking itself (opening, depositing, withdrawing;
  set from elzerma's dev logs), so a slot that saves a bank trip is worth that many seconds, and no more.
- **The search, given the stop order:**
  1. Start from each leg's fastest way and let `BankPlanner` place the bank trips the supplies and footprint need.
  2. Try moves: switch one leg to another way; "use this item wherever it's nearly as quick" (fills the shared slot);
     wield a staff instead of carrying its runes; drop an item no leg needs any more. Each move is scored by the whole
     run's time with bank trips placed again.
  3. Keep the best move, repeat until nothing improves (bounded), then check the order once more with the chosen ways.
  4. Ties fall to the travel style (save charges, prefer free), as now.
- **Explained:** the Run tab's hover and the dev log say why a slower way was chosen, e.g. "skills necklace to Hosidius,
  4 s slower, frees a slot: no bank trip (saves 45 s)".
- **Reused:** `ChargeBudget`, the staff and rune pouch slot rules, `BankPlanner`'s bank-trip search, worn items taking
  no slot. The "Fewest items" style looks unneeded once Fastest balances by time (question 7): confirm with elzerma at
  the start of T4 before removing it, and move a saved "Fewest items" choice to Fastest.

## 5. Timing: an estimate and a personal best

elzerma, 2026-10-17: "I don't want to crowdsource route time path data anything like that. The timing is really
entirely to give user an idea of how long it will take and then also as a 'gamification' to beat the best time
you've set."

- **The estimate is the same for everyone:** set off (finish the patch, open the menu) + cast or animation
  (`teleports.tsv` ticks x 0.6 s) + walking tiles x 0.3 s (running) + transport ticks + a menu or dialogue allowance per
  transport kind. The few constants are set from elzerma's own dev-client logs (each leg is already logged with its
  estimate), as Sprint 8 did, never from other players.
- **Your pace (display only):** after three complete runs, the Run tab's estimate is scaled by your median "actual over
  estimate" from your last five runs. Hover: "Map estimate 8:10; your recent runs are 6% quicker." It never changes
  which way the route takes.
- **Your times by patch type** (elzerma: "like quantities of the same type are compared to each other", with a median
  or mean time per type at the end of each run). Each finished run records, per patch type, how many patches and the
  time spent on them: the leg into each stop plus the time at it, shared between the stop's types by patch count.
  Bank trips are their own line.
- **Like with like:** 9 herbs are compared with your other runs of 9 herbs (best and median). Every type also gets a
  time per patch (median over your last 10 runs, whatever the count), so runs of different sizes still compare.
- **End of the run** on the Run tab (new bests also in the game chat; the numbers are examples):

  ```
  Herbs     9   6:12   41 s each   best 6:05   median 6:30
  Trees     4   2:50   43 s each   best 2:41
  Banking   1   0:48
  Total         9:50
  ```

- **Splits at each stop** (elzerma): after each stop, ahead or behind your best pace, i.e. the time so far against
  your best time per patch for each type done so far.
- A run with a skipped stop doesn't count; "Reset personal bests" in Settings.
- **Removed:** "Share your run times" (Settings > Help improve this plugin), `LearnedTimes.summary()` and its report in
  `TestReport`. `RunTimings` keeps recording runs and legs on the player's computer for splits and pace. Old leg
  samples are left in the saved settings unused (no key renames).

## 6. The Travel tab

Ideas taken from Shortest Path's settings (transport families with their own on/off, item modes, avoid the Wilderness,
house options), rebuilt in our own kit (`docs/BRANDING.md`: `Card`, `ExpandableRow`, `Style`, white text, square, one
orange accent, explanations on hover, one line where possible).

```
Travel
  Ways to travel
    [x] Spells                          9 usable   >
    [x] Teleport items                 14 usable   >
    [x] Tablets                         6 usable   >
    [x] House                    set in Account > My house
    [x] Fairy rings                    11 usable   >
    [x] Spirit trees                    5 usable   >
    [x] Quetzals                        4 usable   >
    [x] Boats and ships                 7 usable   >   (some cost coins)
    [x] Gliders, balloons, carts        3 usable   >
    [ ] Minigame teleports (cooldown)   2 usable   >
    [x] Agility shortcuts           by your level  >
    [x] Avoid the Wilderness
    Travel style                        Fastest  v
  Locations
    Hosidius        Book of the Dead, 34 s     >
      (hover: every step; open: other ways with their times and what they need,
       e.g. "Skills necklace, 41 s"; "Xeric's talisman, needs the talisman"; pick one to override)
```

- The counts and times above are examples, not measured.
- Opening a family lists each way with its state on the right: "ready", "needs 45 Agility", "no charges", "off".
- Turning a family off is a preference the route follows (as Shortest Path's toggles do).
- **Per-location overrides go** (elzerma, 2026-10-18: "I don't want to stick to our list of travel methods in the tab,
  I think we should ditch that and then essentially use shortest path's algo/methods for each run, but it would need to
  simulate at the bank and optimize"). The Locations list shows the way each run would take, read-only, with the other
  ways on hover; no pick from the plugin's own list, and the planner stops honouring old picks. Every run is planned from
  every way the atlas knows (as T3 does), from the bank the player gears up at, with its ways and bank trips chosen
  together (T4).
- The fairy ring way drop-down goes (those ways are ordinary teleports now).
- A location's hover may say what would help most ("with 45 Agility: 13 s quicker"); said there only.
- The Run tab keeps leg times on hover (say it once) and adds the best time and splits from section 5.

## 7. Tests

- **Tool:** the parser reads every pinned transport file; every patch is reachable by the maxed account; peeling ends
  with a "needs nothing" answer wherever one exists; the report lists changes.
- **Plugin unit tests:** the atlas and teleport files load; requirement mapping (a level, a quest, a varbit, items
  carried or banked); the fastest alternative the account meets is picked; no learned time changes a choice.
- **Times by type:** a mixed stop's leg and patch time are shared by patch count; bank trips stay separate; bests are
  per type and count; the per-patch median uses the last 10 runs; a run with a skipped stop records nothing; splits
  compare with the best pace per type.
- **Scenario tests (expected routes),** added to `RouteChainTest` / `ScenarioTest` with `ElzermaAccount` and two new
  accounts (fresh mid-level, maxed):

| Scenario | Account | Expected |
|---|---|---|
| Harmony Island herb, no Harmony teleport, tablet or nexus | Cabin Fever done | Charter ship or Trouble Brewing teleport to Mos Le'Harmless, then Brother Tranquility; never "no way there" |
| Harmony Island herb with a Harmony tablet | any | The tablet |
| Hosidius, Book of the Dead and skills necklace, 50 Agility | no Xeric's | Book of the Dead unless the necklace plus stepping stone is quicker on the atlas; with 30 Agility the necklace's walk is the long way round |
| Falador Park to Taverley | 66 / 60 Agility | Over the rocks / round by the gate |
| Gnome Stronghold to Tree Gnome Village | spirit trees | Spirit tree, then Elkoy |
| Civitas illa Fortis to Kastori | Twilight's Promise | Renu from the pad, no whistle charge |
| Troll Stronghold | 73 Agility and the diary / neither | Basalt to the roof / entrance and the way up inside |
| Catherby herb and fruit tree, then bank | any | Patch order by the atlas; bank walk from the last patch |
| Every enabled stop | fresh account | A way there, or listed as unreachable with what's missing |
| A recorded slow leg | elzerma's | The same route as without it (times don't choose) |
| A big run (herbs, trees, fruit trees, allotments) whose fastest ways leave the supplies 2 slots short | maxed | Ways sharing an item (one skills necklace for three legs, a staff for the runes) and no bank trip, when that's quicker overall |
| The same run when a bank stop is on the way anyway | maxed | The fastest way per leg; no slot-saving swaps |
| A run where saving slots can't avoid the bank trip | elzerma's | The fastest way per leg |

- The existing scenario suites (elzerma's expected chains) keep passing or are updated with the reason.
- **Route snapshots:** the full planned route for the three accounts, saved and diffed whenever the atlas is rebuilt.
- **Referee comparison** (section 3, step 10) runs in the tool, not in the plugin's tests.

## 8. Help-list guided tests

Added only for things elzerma can't try or values no source confirms (CLAUDE.md rules; `cannotTest` when the player
lacks what's needed):

- **`TRAVEL_NEW_JOURNEY` (Travel):** follow one journey the plugin couldn't plan before (Harmony Island by charter ship
  or Trouble Brewing then Brother Tranquility; a gnome glider; a charter ship). Captures the steps shown, whether the NPC
  or object outline appeared (ids from Shortest Path), and the arrival.
- **`TRAVEL_UNLOCKS` (Account):** unlocks the plugin newly reads from varbits taken from Shortest Path (quetzal sites,
  balloon routes, others found in T2). Captures each value with the player's Yes/No; passed ones are marked `confirmed`
  in `data/Unlock.java`.
- None for timing or personal bests: elzerma tests those in the dev client.
- When a test that needs other players is added, draft the edit to pinned issue #18's table and ask first
  (docs/ISSUES.md).

## 9. Sprints

One planner-changing sprint at a time (roadmap rule). Sizes: small is about one session, medium one or two, large two or
three.

| Sprint | What | Size | Check |
|---|---|---|---|
| T0 | Bubble spike: run Shortest Path's pathfinder headless at the pinned commit for about 10 known legs (Hosidius both ways, Harmony, Taverley rocks, Catherby, Tree Gnome Village, Kastori); compare with `TravelTimes`; first evidence list. Decide bubble or fallback E. | small | elzerma reads the list |
| T1 | Atlas builder: places, peeling, re-timing, data files, report, referee | large | The report |
| T2 | Atlas in the plugin, shadow mode: load, requirement mapping, teleports from data; the old planner still chooses; the dev log shows old against new per leg | medium | elzerma plays one run in the dev client, sends the log |
| T3 | Planner on the atlas: legs composed as in section 4, special cases and hand tables removed, learned times out of choosing, scenario tests and snapshots | large | Hand checks: three runs |
| T4 | Choosing ways with the inventory in mind (section 4, last part): the run planned from the bank the player gears up at, footprints, run-level moves, bank trips costed from the atlas, explanations | large | Hand checks: a big run |
| T5 | Journeys from atlas steps: generated step text, NPC and object outlines, walking lines to each step | medium | Hand checks: a boat or NPC journey |
| T6 | Estimate and personal best: time constants, pace, best times and splits; remove "Share your run times" | medium | Hand checks: a run, then a faster one |
| T7 | Travel tab: ways to travel, Wilderness, locations showing the way each run takes (no per-location picks from the plugin's own list) | medium | Hand checks on the tab |
| T8 | Optional dev-client second opinion: at each leg, ask Shortest Path's `query` from the player's tile and log when its way is quicker | small | Log |
| T9 | Help list, README, notices, release notes | small | None |

**When (elzerma, 2026-10-17): first.** Everything else that changes the planner waits: crop types 1b and 1c, the
types that plan leaves out, and farming contracts. Crop types 1a is built and only waits for hand checks, which can
happen at any time; its hops travel is replaced by the atlas in T3. Because the atlas already covers every patch in
the game (section 3, step 2), each later crop-type sprint switches its patches on and rebuilds nothing in travel.
Other sessions don't edit travel files (`data/travel/`, the leg code in `route/`) until T7 is done.

### T0 results (2026-10-17)

**The bubble works.** `tools/atlas` compiled Shortest Path `37abff3` (the Plugin Hub's version 1.21.3) unchanged and
ran its real pathfinder on a computer with pretend accounts: 25 legs and their next-best ways in about 45 s, the same
answers every run. No game, no Shortest Path install, nothing shipped. Fallback E isn't needed; T1 builds on this.
Full table: `tools/atlas/t0-results.md`. Times below are the walk and ride part only (tiles x 0.3 s + transport ticks
x 0.6 s), before the plugin's set-off overhead, compared with what `TravelTimes` says today.

**The plugin thinks it's slower than it is** (so it can pass over a good way):

| Leg | Plugin | Map | Why |
|---|---|---|---|
| Fairy ring CIQ to Tree Gnome Village | 70.1 s | 23.1 s | 77 tiles; the plugin adds 47 s from one recorded 118 s leg |
| Trollheim Teleport to Troll Stronghold | 62.2 s | 28.8 s | Rocks at 41, 43 and 73 Agility cut the walk from 174 to 96 tiles |
| Ardougne Teleport and boat to Brimhaven | 30.6 s | 23.1 s | The boat is 6 ticks on the map, 12 s in the plugin (the fare dialogue is worth checking) |
| Stony basalt to Troll Stronghold, 73 Agility | 10.5 s | 3.3 s | Climb the rocks, 11 tiles; the plugin's 35 tiles was an estimate |
| Falador Teleport to Taverley, 66 Agility | 26.7 s | 20.1 s | The plugin uses the long way round for everyone |

**The plugin thinks it's quicker than it is** (so it can pick a slow way):

| Leg | Plugin | Map | Why |
|---|---|---|---|
| Rada's blessing to the Farming Guild | 30.0 s | 46.8 s (68.7 s below 62 Agility) | The plugin's 100 tiles was an estimate; the way down needs two 8-tick rock climbs |
| Stony basalt to Troll Stronghold, below 73 Agility | 10.5 s | 25.2 s | In through the stronghold and up the ladder, 80 tiles |
| Skills necklace to Hosidius, below 45 Agility | 24.9 s | 36.6 s | 122 tiles without the stepping stone; the plugin counts it for everyone |
| Fairy ring AIS to Nemus Retreat | 19.5 s | 26.1 s | The stepping stones take 7 ticks |
| Quetzal to Auburnvale for Nemus, below 33 Agility, no Twilight's Promise | 21.3 s | 30.9 s | 103 tiles without the tunnel |

**Ways the plugin doesn't know:**
- **Harmony Island without its teleport:** Ectophial, the energy barrier, Bill Teach's boat to Mos Le'Harmless, then
  Brother Tranquility: 37.5 s from the Ectophial (needs Cabin Fever). Today the plugin has no way.
- **Nemus Retreat from Auburnvale with no Agility:** Renu to Quetzacalli Gorge, then follow the Mountain Guide: 19.2 s
  (needs Twilight's Promise). Worth seeing in game once.
- **Tree Gnome Village without Elkoy:** the spirit tree to the Battlefield of Khazard, then a walk: 27.6 s.

**Matching within a second or two:** Hosidius by Book of the Dead and Xeric's talisman, Catherby by Camelot, Tree
Gnome Village by spirit tree and Elkoy, Farming Guild by CIR, Port Phasmatys by Ectophial, Kastori by quetzal, the
royal seed pod, Nemus by pendant (2 s quicker on the map), Falador Park to Falador farm (3 s slower).

**For T1 to handle:**
- Some transports have 0 ticks in Shortest Path's data (Troll Stronghold and Trollheim rock climbs): give each kind a
  minimum, from the wiki or elzerma's dev logs, so they aren't free.
- Peeling without teleports can wander far (Brimhaven without coins goes by fairy ring and travel cart, 79 s): cap
  each answer at a time budget, since another teleport will beat it in game.
- Vars show as numbers (var 111 is Tree Gnome Village's progress, needed to follow Elkoy): name them from RuneLite's
  gameval `VarbitID`, and map them to `Unlock`.
- Player-planted spirit trees count as not planted in the bubble; give the maxed account grown trees.
- Speed for the full atlas: reuse one Shortest Path config per pretend account (each refresh rereads every
  transport), ask with a set of target tiles per stop, and use several threads; about 0.4 s a question today.

### T1 results (2026-10-18)

**Built:** `tools/atlas` (`../../gradlew -p . atlas`, about 8 minutes on 22 threads) writes the six atlas files (1.2 MB of
text, far less zipped in the jar) and `tools/atlas/report.md`; `../../gradlew -p . referee` writes
`tools/atlas/referee.tsv`. `tools/atlas/patches.tsv` lists every farming patch in the game (100 rows: the plugin's 54,
46 more for crop types to come), the plugin's rows checked against `PatchPoints` by `AtlasPatchListTest`.

- **Size:** 822 places (71 patch places, 124 banks, 435 landings, 192 hubs), 986 teleports, 6,935 hops; 11,429 pairs
  asked with 34,208 questions; 20,533 ways kept. No search hit the time limit.
- **Checks:** every patch the plugin runs has a way from a teleport or hop landing. No way at all to six atlas-only
  patches: the two underwater seaweed patches and the two coral nurseries (Shortest Path's map doesn't go there), the
  Farming Guild's redwood and Hespori (their climb and hole aren't in its transports). They need hand steps when
  their crop types are added.
- **Today's 89 travel methods against the atlas** (report.md): 78 within 3 s. Nine of the eleven that aren't are
  T0's findings or like them (CIQ, Trollheim, Rada's blessing, the Troll Stronghold entrance, Taverley by Falador
  Teleport, the Mining Guild ladder, Auburnvale, AIS, the Ardougne boat). The other two compare different things:
  Kastori's flower patch, which the plugin now runs, is beside the quetzal pad, and Civitas then Renu is a hop the
  plugin adds itself.
- **Referee:** the plugin's legs built from the atlas (`AtlasLegs`) agree with Shortest Path's own fastest answer,
  teleports included, within 3 s on 577 of 600 legs between the stops the plugin runs, for the pretend player like
  elzerma. The other 23 are the test's pretend vars, not the atlas (`AtlasRefereeTest`).
- **Fixed on the way:** an item need is any one of its branches (any charge of a skills necklace), not all of them;
  a hop from or to a player-planted spirit tree needs it planted (Shortest Path checks that outside its data); the
  referee leaves the house out.
- **Found:** `PatchPoints` puts the Weiss herb patch at 2847 3931, which the game cache says is the My Arm's Big
  Adventure goutweed patch beside it; the herb patch is at 2848 3934. Left as it is (a follow-up, with its guided
  test). Shortest Path's data gives 0 ticks to many climbs and stepping stones (listed in the report); they count as
  1 tick, 2 for an agility shortcut, until real durations are set.

### T2 status (2026-10-18)

**Built, shadow mode only:** `data/travel/atlas/` reads the atlas (`TravelAtlas`), checks needs for a player
(`AtlasNeed`, `AtlasAccount`: levels and quests from `AccessSnapshot`, items from `Holdings`, varbits and varplayers
read on the client thread, transport families, planted spirit trees) and builds legs (`AtlasLegs`: a way, a teleport
and a way, or a hop on foot or after a teleport). In the dev client (debug logging), every planned route is followed
in the log by `Atlas shadow:` with each leg's plugin estimate and the atlas's quickest leg for the same player, and the
totals. Nothing a player sees changes, and the atlas isn't read at all outside the dev client. The house counts too
(`AtlasHouse`, from the player's house set-up: Teleport to House in, then a nexus portal by name, the jewellery box's
exits up to its tier, or the house's fairy ring or spirit tree). Not yet: the spirit tree into the house (issue #16)
and the start leg from where the player stands (the first leg is "from anywhere").

**Check (needs elzerma):** play one run in the dev client and send the log; the `Atlas shadow:` lines are the evidence
for switching the planner in T3.

**T2 evidence (elzerma's dev-client run, 2026-10-18):** over the legs of two planned routes, the atlas's legs total
541 s against the plugin's 700 s, and 442 s against 490 s. About half the legs agree within 2 s (Hosidius, Catherby,
Lumbridge, Varrock, Port Phasmatys, Lletya, Gnome Stronghold, Falador Park, Explorer's ring). The atlas is quicker
where the hand list is wrong or short: Troll Stronghold (8-22 s), Brimhaven by the sailors' amulet, Taverley by the
skills necklace and the Crafting Guild balloon, Seers' Village by the Camelot Teleport's Seers' option, Nemus by the
Mountain Guide, the Lumbridge north farm by the Chronicle; and it shows the plugin's own guesses were optimistic (from
the Stronghold's tree patch the spirit tree is 36 tiles, not 11). T3 must still bring in what the atlas legs don't
know yet: the quetzal whistle's real wait (the plugin's measured 13 s against Shortest Path's 4 ticks; the Kastori,
Civitas and Nemus legs look 9 s too quick), charges and daily uses (Ardougne cloak, Explorer's ring), and the first
leg from where the player stands. The `Shortest Path (live)` lines used the player's own Shortest Path settings, which
use no teleport items, so they don't compare. elzerma: "i don't want to be locked to our list of travel methods".

**T3 groundwork (2026-10-18), no behaviour change:** `AtlasLinks` ties an atlas teleport to the plugin's `TravelItem`
(by the items it needs) or `Spell` (by name, or its tablet), for charges, daily uses, runes, Spellbook Swap and
highlights. `AtlasLinksTest`: every `TravelMethod`'s item or spell has an atlas teleport, and each lands within 3
tiles of where one of them does.

**T6 built (2026-10-18):** "Share your run times" is gone. Each stop's leg now saves its time at the stop and its
patches by type, and each run its bank time and the plan's estimate (`RunTimings`; old saves keep working).
`run/TypeTimes` shares each stop's time between its types by patch count, and compares like with like: the best for
this many patches of a type, the median time per patch over the last 10 runs, and the best pace (quickest per patch).
On the Run tab: "Run finished in 9:50 - new best for 9 herbs!" with each type's time, best and median on hover (and in
the game chat on a new best); before a run, "Best: 9 herbs 6:05, 4 trees 2:41" with the medians on hover; while
running, a line under the clock after each stop, e.g. "-0:12 against your best pace" (bank visits left out); the
estimate "About 9 min" at the player's own pace after three finished runs, with the plan's own estimate on hover.
Runs from before this have no per-type times, so the whole run's best shows until new runs are done. Not done: a
separate "Reset personal bests" (Settings > Reset > Everything already clears them). Learned times still steer the
route until T3.

**T8 built early (2026-10-18), as it feeds that check:** in the dev client, each newly planned route also asks the
Shortest Path plugin (when installed) for its own fastest way for every leg, with the player's own Shortest Path
settings and real account, and logs `Shortest Path (live):` beside the plugin's estimate (`run/ShortestPathSecondOpinion`).
It only asks while none of our walking lines is drawn (they switch Shortest Path to walking only, and a drawn path
cancels questions), so during a run it waits until the line is cleared.

### T3 status (2026-10-18): the planner on the atlas, first part

elzerma: "plan routes from the atlas with every teleport my account can use, keeping charges, daily uses and the
whistle's real wait, and the first leg from where I stand." Built; the hand checks (three runs) are next.

- **Every way the account can use** (`AtlasLegs`): every atlas teleport whose needs are met (items, spells, tablets,
  capes, diary items, the whistle), the house (Teleport to House in, or "Your house" on a spirit tree once one grows in
  it, then the nexus, jewellery box, fairy ring, spirit tree or front door), and hops taken on foot or after a teleport
  (fairy rings, spirit trees, Renu, balloons, gliders, charter ships, canoes, minecarts, carpets, mushtrees). Nothing into
  the Wilderness (Shortest Path's own area), no teleports with a cooldown.
- **The plugin's own rules decide its own items and spells** (`RoutePlanner.teleportExtra`): an item needs a charge to
  spare and a use left today, and nothing used up earlier in the run (pooled per item; the run is planned again without
  it when it runs out, as before); a spell needs casting (levels, quests, a spellbook it can get to) and costs a
  spellbook swap off its book; the supply list brings the runes. Daily uses are matched by where the teleport lands.
- **Times from the plugin, not Shortest Path's animations:** the whistle 13 s (its measured wait, not 4 ticks), Renu
  6 s, spirit trees 4.5 s, and since run 1 (below) fairy rings 15 s, balloons 26 s, charter ships 9 s and other menu
  hops at least 8 s (first built with 4 s, 4.5 s and their ticks), spells 3 s, items 3.6 s,
  out of the house's door when Teleport to House lands inside; walking tiles x 0.3 s and transport ticks x 0.6 s; the
  same set-off overhead as before. The referee keeps Shortest Path's ticks (577 of 600 still agree).
- **Recognised as the plugin's own methods** (`route/AtlasTravel`): where a teleport, hop or house exit lands where one
  of the plugin's travel methods does, the same way (item, spell, ring, tree...), the leg is that method, so its journey
  steps, highlights, supplies and charges carry on. `AtlasLinks` now holds every method's landing (from
  `tools/walk/methods.tsv`, checked by a test). Otherwise the leg is new (`Departure.ATLAS`, `route/AtlasLeg`): the
  teleport or hop's name, the boats and NPCs on the way as journey steps (each with its walking line), the item or
  spell to outline, and on the supply list its teleport item, runes or tablet, Teleport to House, fares, logs and any
  item the way needs that the plugin doesn't track.
- **The first leg from where the player stands:** the atlas's places within 20 tiles of the player (a bank, a landing,
  a spirit tree, a fairy ring), walked to in a straight line, as well as every teleport; so a walk or the tree beside
  them can start the run. Planned from anywhere instead when supplies are missing and no bank is near.
- **Learned times are out of choosing:** `LearnedTimes` is gone; recorded legs only feed the estimate and best times.
- **"Finish near a bank"** uses the atlas's walk from the stop's patches to its nearest bank.
- **Renu from a pad costs nothing** for the travel styles (it was counted as a whistle charge, on the old tables too).
- **Speed:** the atlas is read in the background at start-up (about 0.6 s; until then routes use the old tables). After
  warm-up a full 18-stop plan takes about 50 ms on the client thread, a second plan in the same refresh about 25 ms, the
  Travel tab's picks about 6 ms; the first plan after start-up a few hundred ms.
- **Fallback:** without the atlas the planner uses the old tables as before (minus learned times); the existing
  scenario suites still test that path. The atlas has its own suite, `AtlasRouteTest` (the section 7 scenarios that
  belong to T3, charges, daily uses, the whistle's wait, the first leg).
- **Evidence** (`AtlasRouteTest`, an account like elzerma's with coins in the bank): their full run is 482 s of travel on
  the atlas against 542 s on the old tables. Changes: Troll Stronghold 45 s (rocks at 41, 43 and 73 Agility) against
  73 s; Kastori and Nemus Retreat by Civitas illa Fortis Teleport, then Renu (24 s and 37 s, Nemus with the Mountain
  Guide) against the whistle (31 s) and AIS (40 s); the Farming Guild by Kharedst's memoirs and the Lovakengj minecart;
  Brimhaven by Ardougne Teleport and the boat, 34 s against the old 42 s; Yanille's hops by the Watchtower Teleport.
- **Dev client:** after each `Route planned:`, `The old tables' route (not used):` with the same run on the old tables.
- **Hand check, run 1 (elzerma, 2026-10-18):** 22 stops in 1:05:34 (six crop types), no errors. Every new leg's
  teleport, hop, boat and guide was spotted without a hand tick. Plain teleports and spirit trees landed on their
  estimates (Catherby 27 s against 28, Lumbridge 21 against 25, Varrock 25 against 22, Civitas illa Fortis by its
  teleport and Renu 35 against 36, Tree Gnome Village by the Grand Exchange's tree and Elkoy 46 against 41). The map
  loads in the log time each part of a leg, and showed the hops' times were Shortest Path's animations: a fairy ring took
  about 15 s and 20 s from the ring to landing (4 s planned; their CIR leg of 2026-10-15 ran 38 s over), the hot air
  balloon about 26 s (4.5), a charter ship about 9 s (3.6). `TravelTimes` now has those, from their logs as section 5
  says, and other menu hops (gliders, minecarts, carpets, mushtrees, canoes) at least 8 s until they're timed. Renu took
  6, 14 and 10 s: left at 6 until more runs (at 10 the whistle takes over Varlamore's chains). Still over: the way up
  through the Troll Stronghold without 73 Agility (44 s inside against about 15; 101 s and 109 s legs against 66 and
  75), the Ectophial's leg through the energy barrier (49 s against 29, but 29 on 2026-10-15), and every leg after a
  bank visit (7-12 s over, for T4's bank costs).
- **Hand check, run 2 (elzerma, 2026-10-18, on the 847f620 build):** 14 stops in 25:43 (trees and herbs), started
  with Start now at Gnome Stronghold without the later stops' saplings, seeds and payments, and used to look the guide
  over, so few legs are clean timings. Those that ran straight landed near their estimates: Gnome Stronghold 8 s
  against 8, Catherby 30 against 28, Hosidius 33 against 34, Taverley 30 against 30, Lumbridge 21 against 25, Varrock 18
  against 22, the Ectophial 23 against 29, the Explorer's ring 13 against 18; the Ardougne cloak 20 against 13. The
  Troll Stronghold was ticked off 14 tiles from its patch, at the rocks; Civitas illa Fortis, Falador Park, Nemus
  Retreat and the Farming Guild include pauses (3 minutes before the teleport, a right-click menu, the open door at
  Quetzacalli Gorge, fetching a skills necklace). The bank visit that came up the moment Start now was pressed, for the
  items it started without, is why Start now now counts them as skipped.
- **Hand check, run 3 (elzerma, 2026-10-18, on 3fea2fc):** 14 stops in 34:57 (trees and herbs) from the Farming
  Guild. Nothing was asked missing at the start (the vault step took the part's seeds); the bank visit after Nemus
  Retreat listed "4 x Guam seed (keep 1)" to deposit; at Varrock the Run tab asked about a mystic air staff and earth
  runes and Bank for them added the visit (an 881 s leg with the detour). Only the Troll Stronghold and Catherby were
  seen planted; every other stop was ticked off by hand seconds after arriving. Legs that ran straight: Civitas illa
  Fortis 35 s against 36, Port Phasmatys 24 against 29, Catherby 29 against 28. Over: the Troll Stronghold 82 against 66
  (the way up inside, as in run 1); the Ardougne cloak 30 against 13 and the slayer ring 26 against 13, both over in
  run 2 as well, mostly before the teleport (worth timing apart); Falador Park after the bank 48 against 23 (T4's bank
  costs); Nemus Retreat 169 s, 2 minutes of it at Civitas illa Fortis before Renu.
- **elzerma's notes from run 1, each worked through (2026-10-18):**
  - Written directions that Shortest Path's line already shows (down into the Troll Stronghold, up to the roof) are
    "Follow the line to the ... patch" while the line is drawn (2cf561a).
  - The growing vine rings the place to pick on Renu's, the whistle's and a balloon's map (847f620), twice as thick
    there and lit and shaded (2cf561a); a teleport item's right-click option is coloured, and its place ringed in the
    list (a46601d); once landed, the teleport's item and spell are no longer outlined (2cf561a).
  - The sidebar lists a stop's patches in the order the guide takes them (2cf561a).
  - Protection falls back in order: pay the gardener, then the best compost held (ultra, super, compost); paid trees
    and fruit trees get no compost, paid allotments and hops still do (e0c8a53).
  - Each part of a run carries only its own seeds and saplings: extras are outlined at a bank visit with how many to
    keep, and the later parts' go in the bank before setting off (4049ecf).
  - Getting ready is spelled out on the Run tab when the seed vault is part of it (f40d000).
  - A missing item is named on the Run tab with Bank for them or Skip, instead of the next step becoming a bank visit
    (453f675). Run 1 had two of those visits, both ticked away by hand.
  - Closed doors: Shortest Path's data counts a door as 1 tick, so its line goes through Lumbridge castle's doors. The
    atlas now counts 2 ticks more for a door or gate, and its pathfinder pays them, so a way round wins when it's as
    quick. Shortest Path's own line in game is its own and still goes through.
  - A line through tiles a permanently open door blocks (Quetzacalli Gorge, by the Mountain Guide) is Shortest Path's
    map: a report for them, drafted for elzerma's OK first.
  - The Troll Stronghold's line goes round by the second rocks; the atlas way is the same as Shortest Path's, so the
    straight way from the top rocks to the entrance is likely blocked in its map: worth trying in game.
  - "Ditch the Travel tab's list and use Shortest Path's ways for every run, simulating at the bank": the planner has
    planned from the atlas since 92d4624; the list goes in T7 (section 6), planning from the bank with the inventory in
    T4.
- **Not yet:** deleting the old tables (after the hand checks); the patch order inside a stop; journeys written from
  the atlas's steps beyond boats and NPCs (T5); spells the plugin doesn't know (Watchtower, Kourend Castle...) are
  planned only with their runes owned and on the spellbook in use, and their runes go on the supply list as items;
  planning off the client thread (T4's search will need it); the Travel tab (T7).

## 10. Risks

- **Shortest Path changes its internals:** pinned commit; a small harness; fallback E.
- **Their cost counts walking as ticks:** we re-time; a path inside one answer may rarely differ from the running
  optimum; the referee flags real cases.
- **Things the map can't model** (the gnome maze, POH instances, underwater seaweed): `extras.tsv` with source notes,
  and our own house hub.
- **Wrong requirement mapping:** shadow mode, unit tests and guided tests for new varbits.
- **Routes change for current players:** release notes, and the shadow-mode evidence before the switch.
- **Data size:** pruning by a time budget; target under 1 MB.
- **Patches reached by Sailing** (Anglers' Retreat, the Great Conch): Shortest Path searches sailing separately. T0
  checks whether the bubble can use it; if not, those patches get hand journeys in `extras.tsv` until it can.
- **The inventory search is a heuristic:** the best combination of ways for a whole run can't be checked exhaustively;
  bounded moves with scenario tests keep it predictable, and the dev log explains each choice.

## 11. Open questions for elzerma

1. ~~Approach~~ Decided: the atlas from the bubble, then our own inventory-aware choice.
2. ~~When~~ Decided: first; crop types wait and run on it; plan for all crop types.
3. ~~"Share your run times"~~ Decided: remove it.
4. ~~Default ways~~ Decided: everything except Wilderness and cooldown (minigame) teleports, each a tick box.
5. ~~Personal best~~ Decided: times per patch type, like quantities compared, a median per type at the end of each
   run (section 5).
6. ~~Splits~~ Decided: a split at each stop.
7. "Fewest items": elzerma's answer was the rule ("balance between making another bank stop and being absolute
   fastest"), now in section 4. Whether the style itself goes is confirmed at the start of T4.
