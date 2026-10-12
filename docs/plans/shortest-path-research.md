# Shortest Path: what we can reuse

Research, 2026-10-11. Source: https://github.com/Skretzo/shortest-path (`master`; the Plugin Hub pins
`cbfc16a`, version 1.21.1, 2026-10-10). Paths below are in their repo. License: **BSD-2-Clause** (`LICENSE`),
so data can be copied with the copyright notice and license text kept beside it.

## 1. Data we could reuse

All transport data is tab-separated, documented in `docs/Transport-TSV-format.md`. Coordinates are `x y plane`,
Duration is in **game ticks**, requirements are `Skills` / `Items` (item IDs) / `Quests` / `Varbits`.

| What | Their file | Use for us |
|---|---|---|
| Spell landings, runes, levels, quests, spellbook varbit | `src/main/resources/transports/teleportation_spells.tsv` (e.g. Camelot line 48 `2757 3479 0`, Ardougne line 56, Trollheim line 67) | Landing point per `TravelMethod`, check `Spell.java` |
| Item teleport landings, item IDs, charge varbits | `transports/teleportation_items.tsv` (Ardougne cloak lines 4-6 with `6069<3`/`<5`, skills necklace 63/68, Xeric's 373, Pendant of Ates 391-397 with one unlock varbit per pad, eternal crystal 331 = item 23946, Trollheim tablet 167 = item 11747 lands where the spell does) | Landing points; settle two of our UNVERIFIED notes (`Spell.java:46`, `TravelItem.java:64`); Pendant of Ates unlock varbits for `Unlock.java` |
| Banks | `src/main/resources/destinations/game_features/bank.tsv` (297 rows, with quest/skill gates, e.g. Farming Guild north bank needs 85 Farming, line 107) | Replace `BANK_POINTS` (`TravelTimes.java:168`, UNVERIFIED). Ours already agree within a tile: Catherby, Falador, Stronghold, Varrock, Lletya (2352 3162), Port Phasmatys (3688 3466). Adds the ones we lack: Nemus Retreat 1387 3309, Hunter Guild 1542 3040, Auburnvale, Aldarin, Tal Teklan |
| Fairy rings | `transports/fairy_rings.tsv` (115 rows, 5 ticks, objectID 29495) | Ring coordinates for CIR/CIQ/AIS/ALQ/BLR/AKR/AJP walks |
| Spirit trees | `transports/spirit_trees.tsv` (origin tiles, planted-tree varbits) | Tree tiles for `SPIRIT_TREE_WALK` |
| Quetzals / Renu | `transports/quetzals.tsv` (Renu 6 ticks, `Twilight's Promise`), `quetzal_whistle.tsv` | Pad coordinates for `RENU_PAD_WALK` |
| Agility shortcuts | `transports/agility_shortcuts.tsv` (666 rows, level + duration) | Check the Taverley rocks (66) and Nemus tunnel (33) |
| Boats, gliders, minigame teleports | `boats.tsv`, `gnome_gliders.tsv`, `teleportation_minigames.tsv` | Brimhaven boat, Fishing Guild minigame landing |
| Spell widget map | `src/main/java/shortestpath/overlay/SpellbookHighlightOverlay.java:30-73` | Line 50 maps Kharyrll to `ZAROSTELEPORT3`, the same as our UNVERIFIED guess (`run/SpellHighlightOverlay.java:45`): a second source, not proof |

**Something we're missing:** `teleportation_items.tsv:419-529` lists the **Trinket of fairies** (item 28564,
not consumable) teleporting to every spirit tree, fairy ring and **tool leprechaun**, including Falador
farm, Catherby, Ardougne, Hosidius, Farming Guild, Kastori, Nemus, Ortus Farm, Lletya, Port Phasmatys, Troll
Stronghold, Weiss. For a farm run that's possibly the best item in the game. Needs wiki research (charges,
cooldown, how it's obtained) before we add it.

**Durations that disagree with ours** (`TravelTimes.java:146-165`): standard spells and nearly all jewellery are
4 ticks = 2.4 s (we use 3.0 / 3.6); fairy ring 5 ticks = 3.0 s (we use 4.0); Renu 6 ticks = 3.6 s (we use 6.0);
royal seed pod 13 ticks; **home teleport 24 ticks = 14.4 s** by default (19-25 by animation, varp 4560,
`teleportation_spells_home.tsv`), we use 10.5. Their durations are animation-only; our `LEG_OVERHEAD` (8 s)
covers menus and loading, so these refine the constants rather than replace the calibration.

Not reusable: their tables don't hold the **walk from the landing to our patch**, which is the number that
matters most. That comes from section 2.

## 2. Walking distances at runtime: yes, there is an API

The released version answers path queries over plugin messages
(`src/main/java/shortestpath/ShortestPathPlugin.java`, constants 125-135, handler 859-933; added in #565,
commit `07be279`, in the pinned 1.21.1):

- **Send** `PluginMessage("shortestpath", "query", {id, start, target})`. `start` is a `WorldPoint` (defaults
  to the player), `target` a `WorldPoint`, packed int or a **Set** of them (nearest wins, so "any patch of this
  stop" works). Does not touch the displayed path.
- **Receive** `PluginMessage("shortestpath", "result", {id, reached, start, target, path: List<WorldPoint>,
  transports: [{origin, destination, objectInfo, displayInfo}], closest, cost, reason})`
  (`postQueryResult`, around line 1085). Failures come back with `reached=false` and `reason`
  `INVALID` / `SHUTDOWN` / `ERROR` / cancelled.
- `getTarget` -> `currentTarget` (line 1143) reports what's drawn now.

Caveats from the code:
- Queries use the **shared** pathfinder settings (`queryPath`, around line 960). To get walking-only answers
  our `WALK_ONLY` overrides must be in force: a `path` message carrying only `config` applies the overrides
  and draws nothing (lines 872-894). They stay until `clear`, which also wipes them for the player.
- A new displayed path **cancels** pending queries (`cancelQueries`). Our own walking line causes that, so
  don't query while we're redrawing it.
- Searches stop after `calculationCutoff` ticks with no progress: check `reached`.
- `cost` is mixed units (section 3); with walking only it's **tiles walked** plus shortcut durations.
- It needs Shortest Path installed and enabled; answers are asynchronous (on its thread, posted back on the
  client thread). Fine under Plugin Hub rules: no reflection, no compile dependency, just messages.

**Recommendation:** don't make planning depend on it. Use it in a **dev-only measuring tool**: a "Measure
walks" button in Account > Testing & debug that, in Sean's dev client with Shortest Path installed and the
walk-only overrides set, queries every `TravelMethod` landing -> its stop's patches, every bank -> patch,
every `WALKS` pair, spirit tree / fairy ring / Renu -> patch, then logs a ready-to-paste table. We bake the
numbers into `TravelTimes` with a source comment. Later, optionally, query live for the one leg we can't
precompute: **from where the player stands** to the first stop (fallback to baked numbers if no answer in ~2 s).

Offline alternative (only if the in-client tool is awkward): copy their `collision-map.zip`,
`SplitFlagMap.java` and `CollisionMap.java` into `src/test` (BSD-2, notice kept, never shipped) and run a
BFS in a unit test. More code to keep in sync, so second choice.

## 3. Their cost model

- Walking: 1 per tile step, Chebyshev (`pathfinder/NodeGraph.java:133-143`), i.e. walking-speed ticks, not run.
- Transport: previous cost + `Duration` ticks + a per-type user "threshold" (`NodeGraph.java:152-165`,
  `PathfinderConfig.java:533-543`). Teleports are forced to at least 1 tick.
- Bank visit: an explicit costed state change when standing on a bank tile (`CollisionMap.java:310-320`,
  `costBankVisit`, default 0, `ShortestPathConfig.java:1188`). After it, items in the bank count as carried.
- Thresholds are "tiles a transport must save to be preferred" (`ShortestPathConfig.java:1037-1044`, all default 0).

What to take: (a) their tick durations as the animation part of our seconds; (b) the "must save N" idea is the
same as our tie-break preferences, so our design stands; (c) a bank visit as a state change with a fixed
cost matches `BankPlanner`. What not to take: they mix tiles and ticks (a running tile is half a tick); we stay
in seconds.

## 4. Guide features worth borrowing

- **Bank pickup**: `transport/BankPickupRequirements.java:22-28` works out which teleport item to fetch,
  treating alternatives as OR and picking the cheapest; `overlay/BankItemHighlightOverlay.java` highlights it
  in the bank. Close to our bank guide; their OR handling is a good model for "any glory works".
- **Inventory / spell highlights**: `overlay/InventoryHighlightOverlay.java`, `SpellbookHighlightOverlay.java`
  (spell name -> gameval widget table, a check on ours).
- **Wilderness**: rows carry a max wilderness level (20 / 30), `avoidWilderness` filters destinations
  (`PathfinderConfig.java:345, 505-509`). Our Kharyrll and Ectophial-free legs don't need more than a note.
- **Requirements as data**: quests/skills/varbits per row, `UNLOCK_*` tokens for state the client can't see.
  We could validate our `Requirement`s against their rows in a test.

## 5. Risks

- **License**: BSD-2 needs the copyright notice and license text with copied data. Add
  `docs/third-party/shortest-path-LICENSE` and a source comment on each copied table. Their `LICENSE` still has
  the `<YEAR>, <COPYRIGHT HOLDER>` placeholder: credit "Skretzo and Shortest Path contributors".
- **Churn**: very active (several commits a day, 2026-10-10), TSV columns are header-mapped and change. Copy
  numbers into Java, not their files; record the commit we copied from.
- **API churn**: `query` is ten days old. Treat it as optional; handle no answer.
- **Drift**: game updates move landings. A dev test that diffs our coordinates against a pinned copy of their
  TSVs catches it when we bump the pin.
- **Player's settings**: overrides are global in Shortest Path while set; always `clear` when our run stops.

## Sprint 8: Shortest Path data

1. **Attribution and pin.** Add the license file and a `SHORTEST_PATH_COMMIT` note. Check: none in game.
2. **Bank coordinates.** Replace `BANK_POINTS` from `bank.tsv`, add Nemus Retreat and Hunter Guild, drop the
   UNVERIFIED comment. Unit test: every stop with `BANK_TILES` has a point. In game: walking line to the bank
   at Catherby, Gnome Stronghold, Nemus Retreat ends at a booth/chest.
3. **Landing coordinates test.** A unit test with their landing tiles for our 76 methods (copied into the
   test) checking each `TravelMethod`'s landing; fix mismatches. Settles the Trollheim tablet and eternal
   crystal notes. Check: none.
4. **Durations.** Set spell/jewellery 2.4 s, fairy ring 3.0, Renu 3.6, home teleport 14.4, then re-check
   `LEG_OVERHEAD` against `RunTimings`. In game: Run tab leg times for 5 known legs still look right.
5. **Measure walks tool (dev only).** Account > Testing & debug button; queries Shortest Path (walk-only
   overrides) for every landing/bank/tree/ring/pad -> patch and stop -> stop; logs a table. In game: Sean
   presses it once in the dev client (logged in, Shortest Path installed), sends the log.
6. **Bake the walks.** Replace `TILES`, `WALKS`, `SPIRIT_TREE_WALK`, `RENU_PAD_WALK`, `BANK_TILES` with the
   measured values; list the big changes. In game: plan a herbs+trees run; order and times look sensible.
7. **Trinket of fairies research** (wiki), then add it as a `TravelItem` if it's what it looks like, with a
   guided test (Sean may not own it). In game: only if he has it.
8. **Optional: live first leg.** Query from the player's tile to the first stop when planning, fall back to
   baked numbers. In game: start a run from an odd spot (e.g. Lumbridge); the first leg's time is believable.

## Risk mitigations (agreed with Sean, 2026-10-11)

- **License:** a THIRD_PARTY_NOTICES file with their BSD-2 text, a README credit, and a source comment on each
  copied table naming the Shortest Path commit. Copy facts (coordinates, ticks), not code.
- **Snapshot, never live:** copy only the numbers we need into Java, pinned to the commit the Plugin Hub ships
  (not master). Nothing is downloaded or loaded at runtime.
- **Format churn:** only a dev-only script (outside the plugin build) reads their TSVs; if it breaks, players
  are unaffected.
- **Accuracy:** their values are a second opinion. A unit test compares ours with the snapshot; big
  differences are flagged for review, never taken automatically. Learned times still override estimates.
- **Staleness:** the same script prints what changed since the recorded commit, so a refresh is a short review.
- **Runtime query:** only in the dev "Measure walks" tool; results are baked into TravelTimes. Planning never
  waits on Shortest Path, and nothing changes if it's missing or silent.
- **Global overrides:** always send `clear` on run stop, logout and plugin shutdown.
- **Override keys:** a unit test checks every key and value we send against a list taken from their source
  (the "None" vs "NONE" bug); the dev client logs if one of our walk-only lines contains a teleport.

## Added to Sprint 8 (Sean, 2026-10-12)

- **Hot air balloons** as travel options: Taverley's lands 14 walking tiles from the tree patch. Worth it from the
  Gnome Stronghold fruit tree (10 tiles to the Grand Tree balloon, about 20 s to the Taverley tree against about
  30 s by house portal) and straight after a bank stop at the Castle Wars chest (31 tiles to its balloon). One
  normal log per flight to Taverley; Enlightened Journey and each balloon unlocked (Shortest Path's
  hot_air_balloons.tsv names the varbits: Taverley 2868=2, Grand Tree 2870=1, Castle Wars 2869=1). Unlocks read
  from the game need a guided test.
- **Bank distance from the last patch done at a stop**, not one number per stop: Catherby herb 36 / fruit tree 52
  walking tiles to the bank (plugin uses 28), Falador Park tree 32 (28).
- **Walking distances measured on the collision map** (a one-off BFS over collision-map.zip) for every landing,
  bank and stop-to-stop walk, e.g. Falador Park to Taverley by the gate is 114 tiles (plugin: 100).

## Start points by walking distance (Sean, 2026-10-12)

"Near a stop" is straight-line today (within 120 tiles), so Castle Wars counts as near Lletya. Plan:
- A dev-only script walks out from each stop's patches over the collision map (up to ~150 tiles) and saves
  walking tiles per 4x4-tile block as a small data file; in game the player's block gives the walk to each
  nearby stop. No runtime dependency on Shortest Path.
- Within walking range (~80-100 walking tiles, about a teleport), try each nearby stop as the start, plus
  "from anywhere", and keep the quickest whole route; beyond it, plan from anywhere.
- Keep the Kastori rule: a stop without a bank is only the start once everything is carried.
- Scenarios: Falador west bank -> Falador Park; Castle Wars -> not Lletya; Grand Tree -> Gnome Stronghold;
  Grand Exchange -> Varrock; Seers' bank -> Catherby only if the walk beats Camelot Teleport.
