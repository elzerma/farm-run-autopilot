# Plan: sidebar layout (UX/UI refactor)

Status: planning only, nothing built. Pairs with [teleport-preferences.md](teleport-preferences.md), which adds settings this layout has to make room for.

## Problems seen so far

1. **"Setup" and "Rules" don't say what's inside, so related settings are split between them.**
   - Route mode is in Setup > Run options; start, finish and Prefer walking are in Rules > Route.
   - "Runes everywhere" is in Setup; "Runes here" is in Rules > Travel.
2. **Account facts are mixed in with run choices.** My POH and Unlocks describe what you *have*; Patches and Travel are what you *want*.
3. **Travel is the longest page and about to double.** About 15 locations, each with a heading, a dropdown and a tickbox, plus the planned per-location teleport override.
4. **Detected things have nowhere to show.** Nothing says the plugin has seen your bottomless bucket and its uses, your spellbook, your fairy ring staff or your quest cape eligibility. That's why Sean went looking for a "bucket setting".
5. **Overrides are hard to spot.** You can't tell which locations or patches differ from the defaults without opening each one.

## Capability inventory (walked 2026-10-10)

Every user-facing capability, so nothing gets lost in the move.

**Run tab (today):**
- Stages: Off, Building, Armed, Running.
- Preset picker.
- Run-type ticks with **Include anyway / Skip this run** buttons per type. These are one-run overrides that reset after the run.
- "N patches aren't due" (tooltip), warnings (orange).
- Route list: drag to reorder, which switches the route mode to Off (custom order).
- Supply checklist: legend, groups, tooltips saying where items are and where to change them.
- Totals: time, coins, runes, starting slots.
- Running and armed views: clock, Skip step, Stop, Start now, Cancel, and the stop list with each stop's plan on hover.
- Off view: patch timers, last and best times. A "full run is on" notice when the debug option is set.

**Setup and Rules tabs (today), about 60 controls:**

| Section | Controls |
|---|---|
| Patches | One tickbox per patch, locked ones with requirement tooltips |
| Crops | 1st choice per type, backup 2nd/3rd, use backups, disease-free herb list and prioritise |
| Protection | Per type: pay or compost-only, pay 200 to clear, compost type. Also pay with notes, per-patch overrides, Cure Plant, Resurrect Crops, suggest yield boosts |
| Travel | Fairy rings dropdown; per location: method, runes here |
| My POH | Portal location, teleport outside, jewellery box, pool, altar, fairy ring, spirit tree, nexus destinations, Rescan |
| Unlocks | Manual unlock tickboxes; detected ones shown greyed |
| Route | Start at, finish near bank, walk when nearly as quick, **due threshold %** |
| Run options | Route mode, runes everywhere, outfit, drop reminders, **stamina doses, plant cures** |
| Storage sources | GIM storage, seed vault |
| Run guidance | Four highlight toggles, three colours |
| Presets | Save as, use, rename, delete |
| Testing and debug | Full run, patch state list |

**In game:**
- Text under the player: step, timer, reminders for dropping weeds/pots/buckets, missing items, taking items from the leprechaun, and low inventory space.
- Patch and gardener outlines, item outlines, the hint arrow.
- Bank tab: button, sectioned view, withdraw, Details.
- No settings live in game.

**Automatic (no setting):**
- Quests, diaries, levels, spellbook, quest points.
- House furniture.
- Inventory, worn items, bank cache, leprechaun storage, GIM storage, seed vault.
- Tithe Farm auto-weed.
- Bottomless bucket uses (on the fixes branch).
- Patch states (Time Tracking as a fallback).
- Learned leg times, best times.

**RuneLite config panel:** only "Click to open the sidebar". Guidance toggles and colours are hidden there and edited in the sidebar.

**In the SPEC but not built:**
- Run-energy advice (energy threshold, pool or Ferox suggestion).
- World map point while travelling.
- Spellbook highlight.

The layout leaves room for these in Travel > Defaults (energy) and Account > Display (map and spellbook highlights), but doesn't depend on them.

## Fit check: changes from the first draft

1. **The due threshold % goes to Farm, not Travel.** It decides *what* is in a run, not how you get there. It sits with the run-type list.
2. **Plant cures and the Cure Plant and Resurrect Crops spells go to Farm > Protection** (disease handling). **Yield boosts and the farming outfit** go to a Farm > "Extras to bring" subsection.
3. **Stamina doses go to Travel > Defaults** (they're about running between stops, and run-energy advice can join them later).
4. **"Remind me to drop weeds and pots" goes to Account > Display**, with the other in-run guidance options.
5. **Presets cover both Farm and Travel**, so managing them sits on the **Run tab**, under the preset picker ("Manage presets", collapsed), not inside Farm.
6. **Include anyway / Skip this run stay on the Run tab.** They're one-run choices, unlike the saved overrides elsewhere, and are labelled "this run only" so they aren't mistaken for settings.
7. **Route mode Off means "my own order"**, set by dragging the route list on the Run tab. Travel > Route says so and links the two. Dragging the list keeps switching the mode, as today.
8. **Unlocks only lists manual ones.** Detected unlocks (fairy rings, spirit trees) move to Account > Detected.
9. **The patch state list in Testing and debug** overlaps the Run tab's patch timers. It stays as the detailed view (stage, when seen) but is labelled "Patch details".

## Principle: one tab per question

| Tab | Question it answers | Sections |
|---|---|---|
| **Run** | What's due, and go | Stages (Off, Building, Armed, Running), run-type ticks with "this run only" Include/Skip, Build run, due summary, patch timers, times, route list (drag), checklist, totals, preset picker with "Manage presets" |
| **Farm** | What do I grow, when, and how do I protect it? | Run types and due threshold %, Patches (with per-patch overrides), Crops (backups, disease-free herbs), Protection and compost (plant cures, Cure Plant, Resurrect Crops), Extras to bring (outfit, yield boosts) |
| **Travel** | How do I get around? | Defaults for every stop (Teleports, Fairy rings, Walk when nearly as quick, stamina doses), Route (mode, start, finish near bank), Locations (one compact row each, overrides badged) |
| **Account** | What do I have, and what has the plugin seen? | Detected (read-only status with actions, including detected unlocks), My house, Unlocks (manual only), Storage sources, Display (highlights, colours, drop reminders), Testing and debug (full run, patch details) |

**Rules of thumb:**
- **Defaults come first, overrides second.** An override is always marked with an orange "Override" badge, so the defaults page shows at a glance what's different.
- **Detected things are shown, not asked for.** A setting only exists when the plugin can't find something out itself.
- **Every section header says what's set when it's collapsed**, for example "Patches: 18 of 24, 2 overrides".

## Old place to new place

| Setting | Now | Proposed |
|---|---|---|
| Crops (incl. backups, disease-free herbs) | Setup | Farm |
| Run options: route mode | Setup | Travel > Route |
| Run options: runes everywhere | Setup | Travel > Defaults > Teleports (becomes the preference dropdown) |
| Run options: outfit | Setup | Farm > Extras to bring |
| Run options: plant cures | Setup | Farm > Protection |
| Run options: stamina doses | Setup | Travel > Defaults |
| Run options: drop reminders | Setup | Account > Display |
| Presets | Setup | Run tab: picker plus "Manage presets" |
| Patches, Protection, per-patch overrides, Cure Plant, Resurrect Crops | Rules | Farm |
| Suggest yield boosts | Rules > Protection | Farm > Extras to bring |
| Route: due threshold % | Rules | Farm > Run types |
| Travel (per location), Fairy rings | Rules | Travel |
| Route (start, finish, walk) | Rules | Travel |
| My POH, Unlocks (manual), Storage sources | Rules | Account |
| Unlocks (detected) | Rules | Account > Detected |
| Run guidance (highlights, colours) | Rules | Account > Display |
| Testing and debug (full run, patch details) | Rules | Account (collapsed, at the bottom) |

## The Locations list (the big one)

- **One line per location, for example "Catherby — Auto · default".** It shows the teleport and the preference in grey, or an orange **Override** badge.
- **Clicking a row opens its two dropdowns**: which teleport, and the override, defaulting to "Use default (Prefer free)". Only one row is open at a time.
- **A "Show only stops in my runs" filter**, on by default, hides locations with no ticked patches.
- **Locked teleports keep their tooltips** saying what they need. A spell you can't cast says why, for example "(Lunar spellbook)".

## The Detected section

Read-only lines, each with a short "how it knows" tooltip and, where it makes sense, an action:

| Line | Example | Action |
|---|---|---|
| Spellbook | Standard | — |
| Bottomless compost bucket | 120 uses of ultracompost / "uses unknown" | "Right-click > Check to update" |
| Fairy ring staff | Dramen staff / Elite diary (not needed) | — |
| Quest point cape | Usable / "needs every quest (343/345)" | — |
| House | Nexus: 5 destinations, last scanned 10 Oct | Rescan house |
| Tool leprechaun | Spade, rake, 30 ultracompost stored | — |

## Phasing (each phase is its own commit and in-game check)

1. **Move sections into the four tabs.** No logic changes; settings and their saved keys stay the same, so nothing resets.
2. **Collapsed summaries and Override badges** on Patches and Locations, plus the "Show only stops in my runs" filter.
3. **Detected section.**
4. **Teleport preference dropdowns** (from teleport-preferences.md), dropped into the new Travel defaults and location rows.

## Constraints

- **The RuneLite sidebar is narrow (about 225 px).** Four tab labels must fit: Run, Farm, Travel, Account. "Account" could become "You" if it's tight.
- **Saved settings mustn't reset.** Only the panels move; config keys stay. The runes migration belongs to phase 4.
- **The Plugin Hub README screenshots** (`docs/*.png`) will need retaking after phase 1.

## Questions for Sean

1. Are the tab names right (Run, Farm, Travel, Account)? Or would "Setup" still feel familiar for Farm?
2. Should the Locations list show only stops in your runs by default, with a toggle to see all?
3. Should Run guidance (highlights, colours, drop reminders) go in Account > Display, or stay its own section?
4. Should preset management on the Run tab be a collapsed "Manage presets" under the picker? Presets cover Farm and Travel, so neither tab is a natural home.
5. Is the phase order OK? Phase 1 is low-risk and makes the rest easier to place.
