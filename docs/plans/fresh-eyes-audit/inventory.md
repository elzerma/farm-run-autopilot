# Fresh-eyes audit: inventory (phase 1)

Every place the plugin explains itself or asks for a choice, as it was on 2026-11-02 (`origin/master` at 83fe88c), read
from the code alone. Companion to the proposal, [`../fresh-eyes-audit.md`](../fresh-eyes-audit.md).

**How it was measured.** Each tab was built offscreen from made-up data and painted at the sidebar's width
(`PluginPanel.PANEL_WIDTH`, 225 px, scroll bar showing) with RuneLite's own look and feel. The account is mid-game:
every quest, 90 in every skill, two medium diaries, fairy rings, spirit trees, a house in Rimmington and a bank like the
scenario tests'. Run types ticked: trees, fruit trees, herbs, allotments and flowers. Two saved presets. Heights are in
pixels as painted. The renders are in [`before/`](before/).

**Columns.** *Saved as* is the field in the account's saved run settings (`runConfig` JSON), account settings
(`account` JSON) or a RuneLite config item (group `farmrunautopilot`), with its default. *Presets*: whether applying a
preset changes it (`SettingsStore.farmAndTravel`). *Who*: every run, some players, rarely, or developer only.

Section open/closed states are kept for the whole client in `ui.openSections`, keyed by section title. Open the first
time: Crops and Ways to travel (the third default, "Detected", is no longer a section title, so it never matches).

## Tab bar

Five boxed icons sharing the width (34 px): Run (play), Farm (sprout), Travel (map pin), Account (person), Settings
(gear). The tab's name shows on hover.

## Run tab (`ui/RunPanel.java`)

What your next run covers, and the run itself. Height: off 458, building 886, running 685, logged out 185.

| # | Shows | Label / text | Hover | Saved as (default) | Read by | Who | Height |
|---|---|---|---|---|---|---|---|
| 1 | During a guided test, on top | Orange-barred card: "Test: …", "Step N of M", the step, "Bring:", Yes/No, Skip/Cancel; at the end "Open GitHub issues with prefilled test results" and Close | – | – | TestRunner | testers | ~170 |
| 2 | Presets saved; not while a run is armed or under way | "Preset" + drop-down ("Custom (changed)" once edited) + "Manage" link | "Switch to a saved preset" / "Save, rename and delete presets in Settings" | `presets` (none) | SettingsStore | some | 21 |
| 3 | Always | Run types, three to a row: Trees, Fruit, Herbs, Allotments, Flowers, Hops, Bushes, Specials, Sp. trees | "Include tree patches in this run" etc.; Flowers: "…Flowers that protect your allotments come with them anyway"; Specials and Sp. trees list their types | `enabledTypes` (trees, fruit trees, herbs); presets: yes | RunSelector, RunService, AccessChecker | every run | 59 |
| 4 | Logged out | "Log in to see your next run." | – | – | – | – | 15 |
| 5 | Off | **Build run** (orange) | "Show the farm run bank tab and gather supplies. The run starts itself once you have them." | – | RunSession | every run | 20 |
| 6 | Off, after a run | "Run finished in 14:05 - new best for 9 herbs" (or the best times) | each type's time, or best times per type | run history | RunTimings | every run | 14–28 |
| 7 | Off and building | One row per ticked type not in this run: "Trees: ready in 3h 20m [Include]", "…: skipped this run [Undo]", "…: included anyway [Undo]", "…: no patches available" | – | in memory (RunOverrides) | RunSelector | every run | 24 each |
| 8 | Off | "Patch timers" heading, then a group per ticked type, "Trees  0 of 7 due", opening to a line per patch: place, state (green when due) | "Lumbridge: Magic - ready in 3h 20m" | open state `Patch timers: TREE` | PatchTracker | every run | 27 + 27 per type + 14 per patch |
| 9 | Until checked | "Check your unlocks: 5 not seen yet" (orange, opens Account > Unlocks) | "Click to open Account > Unlocks" | `account.unlocksReviewed` | AccessChecker | some | 15 |
| 10 | Seed vault on, never opened | "Open your seed vault once" | "At the Farming Guild, so the seeds and saplings in it count" | – | HoldingsTracker | some | 15 |
| 11 | Building | **Start now** (orange), Cancel | "Start the run and its timer without everything: steps that need what's missing are skipped" | – | RunSession | every run | 20 |
| 12 | Building, when the seed vault or seeds for later are part of it | Getting ready card: each step, ticked by the plugin | – | – | RunSession | some | varies |
| 13 | Building | "Route summary"; "About 18m (6m travelling)"; "Counting every patch" (orange, with Settings > Debug's full run on); per inventory "3 herbs, 6 allotments, 4 flowers (24/28 slots)" with "Bank near …" (orange) between; "1 patch isn't due yet"; warnings (orange); the farming contract line | time: "The plan's estimate is 16m; your last runs took 10% more than their estimates"; parts: "Patches done with this inventory, and the slots it needs"; not due: the list | – | RunService | every run | ~150 |
| 14 | Building | "Complete route" | "Hover a stop for its plan. Drag a stop to reorder it (that switches to your own order)." | – | – | every run | 27 |
| 15 | Building | "Order: auto (fastest)" / "Order: the wiki's" / "Order: your own. Use the fastest" (orange link) | "Change it in Travel > Route" / "Your own order ignores where you're standing and the quickest chains. Click to plan the fastest order again (your order is kept for later in Travel > Route)." | `routeMode`, `customOrder` | RoutePlanner | every run | 14 |
| 16 | Building | Route: a card per stop ("1. Kastori", "Quetzal whistle (Kastori)", yellow when supplies come first), bank visits between with an orange bar. Drag to reorder; right-click "Don't use …" | leg time, why, the plan per patch, "Right-click: don't use its way" | `customOrder`, `waysOff`, `travelFamilies` | RoutePlanner | every run | ~38 per stop |
| 17 | Armed | "Armed" (orange), Start now, Cancel | "The timer starts when you teleport or click your first patch" | – | RunSession | every run | ~45 |
| 18 | Running | Clock (24 pt, orange) and **Stop** (red) | "End the run now. It ends by itself after the last stop" | – | RunSession | every run | 30 |
| 19 | Running | "4:12 - 0:12 ahead of your best pace" | "Your time so far, bank visits left out, against your quickest time per patch for each type done so far" | – | RunTimings | every run | 14 |
| 20 | Running, items gone | Orange-barred box: "Missing: …", **Bank for them**, Skip | "Adds a bank visit: before finishing this stop if its step needs them, else after it" / "Carry on without them until your next bank visit: a step that needs them is skipped" | – | RunSession | some | ~50 |
| 21 | Running | "Complete route"; each stop with a tick box, the current one lighter with an orange bar and all its steps | "Ticks itself as you go (green). If it misses something, tick it yourself (orange); a box you ticked can be unticked to go back to it" | – | RunSession | every run | ~30 per stop |
| 22 | Every tab but Settings | "Help improve this plugin: try a few quick things in game, no coding needed. Show me" (opens Settings > Help improve this plugin); hidden once a player has run every test | "17 features I couldn't test, with steps to try them" | `help.*` | HelpWanted | some | 44 |

## Farm tab (`ui/FarmPage.java`)

Which patches, what to plant, and what's done at them. Height: as first opened (Crops open) 769; every section open
4,711.

At the top when it applies: "Log in to load and save your setup." or "Checking your quests, diaries and levels...
nothing is greyed out until that's done." (15–28).

| # | Section > item | Label / text | Hover | Saved as (default); presets | Read by | Who | Height |
|---|---|---|---|---|---|---|---|
| 1 | **Patches** (closed; "80 of 80 ticked") | – | (?) "Untick patches you don't want in your runs; locked ones show what they need on hover. Choose the run types at the top of the Run tab: each joins a run once its patches are due." | – | – | – | 27 closed, 1,694 open |
| 2 | Patches > a group per patch type (17), each with a tick box for the whole type | "Tree patches  7 of 7" | box: "Tick or untick every tree patch" | `disabledPatches` (none); yes | RunSelector, AccessChecker, FlowerProtection, SupplyCalculator | most, once | 27 each closed |
| 3 | Patches > type > patches, two to a row | "Lumbridge", "Falador farm (south) (north-west)"… locked ones greyed | locked: "Locked: Needs Elite Morytania diary" | `disabledPatches` | as above | most, once | 14–38 per row |
| 4 | **Crops** (open first time) | "Use backup choices if I run out" | "Plant your 2nd, then 3rd choice once you run out of your 1st. Disease-free and protected patches get the best crops first." | `useBackupCrops` (off); yes | RunConfig.cropChoices → SupplyCalculator | some | 18 |
| 5 | Crops > per type with a choice (9: Tree, Fruit tree, Herb, Allotment, Flower, Hops, Bush, Cactus, Hardwood tree) | type name above a full-width drop-down, "Magic (75)"; above your level greyed. Backups on: "1st", "2nd", "3rd" rows ("None" clears) | greyed: "Needs 75 Farming" | `crops` (highest your level allows), `backupCrops`; yes | RunSelector, PatchGuide, SupplyCalculator, RunPanel | every player, once | 46 per type |
| 6 | **Disease-free herbs** (closed; "off" or "N picked") | "Prioritise herbs for disease-free patches" | "Pick herbs that only go in disease-free patches" | `prioritiseDiseaseFreeHerbs` (off); yes | SupplyCalculator | some | 30 |
| 7 | Disease-free herbs, when on | (?) "Troll Stronghold, Weiss and Harmony Island, plus Hosidius and Civitas once their unlock is done. They get these herbs first, most valuable first (by GE price); other patches never do." 15 herb tick boxes "Guam (9)" … "Torstol (85)" | greyed: "Needs N Farming" | `diseaseFreeHerbs` (none); yes | SupplyCalculator | some | 270 |
| 8 | **Protection** (closed; "N overrides") | – | (?) "Pay gardener: when there's not enough of the payment, the patch gets the best compost you have instead (ultracompost, then supercompost, then compost). Paid trees and fruit trees get no compost; paid allotments and hops still do, as it raises the harvest." | – | – | – | 27 closed, 1,271 open |
| 9 | Protection | "Bring gardener payments noted" | "Gardeners accept noted payment; one inventory slot per item type" | `payWithNotes` (on); yes | PatchGuide, SupplyCalculator | rarely | 18 |
| 10 | Protection > per protectable type (12) | "Tree patches" heading above a drop-down: Pay gardener / Compost only (allotments also Flower) | – | `protection` (pay gardener; allotments Flower; hops Compost only); yes | RunConfig.protectionFor → SupplyCalculator, FlowerProtection | some | 46 per type |
| 11 | Protection > Allotments > **Each set** (closed; "N changed") | per set: name above a drop-down, "Same as all allotments" / … | (?) "Flower: the set's flower patch gets the flower that protects what's planted (a scarecrow for sweetcorn, white lily for anything), and a grown one is left in place. Where no flower you can plant covers the crop, the gardener is paid instead." | `protectionOverrides` (allotments); yes | as #10 | rarely | 343 open |
| 12 | Protection > fruit trees, bushes, cacti, calquats | "Pick grown fruit trees and leave them growing" | "Check health and pick each run, without clearing and replanting; a dead one is replanted" | `keepGrowing` (none); yes | RunSelector, RunService, PatchGuide, StepAdvisor, SupplyCalculator, RunPanel | some | 30 each |
| 13 | Protection > spirit trees | "Keep grown spirit trees for travel" | "Check their health once grown, then leave them standing rather than digging them up to replant" | `keepGrowing`; yes | as #12 | some | 18 |
| 14 | Protection > trees, fruit trees, hardwood trees, calquats, celastrus | "Pay 200 coins to clear grown trees" | "The gardener removes the old tree, so no axe is needed" | `payToClear` (all five); yes | PatchGuide, SupplyCalculator | some | 30 each |
| 15 | Protection > redwoods | "A grown redwood is cleared by paying Alexandra 2,000 coins: nobody else can" (text, not a setting) | – | – | – | – | 39 |
| 16 | **Compost and cures** (closed) > per type (17) | type name above a drop-down: No compost / Compost / Supercompost / Ultracompost | – | `compost` (ultracompost on herbs, allotments, hops, mushrooms, belladonna, seaweed, crystal tree; none elsewhere); yes | PatchGuide, StepAdvisor, SupplyCalculator, trackers | some | 39 per type (663) |
| 17 | Compost and cures > "Disease" | "Use Cure Plant (Lunar)" | – (none) | `useCurePlant` (off); yes | PatchGuide, SupplyCalculator | some | 18 |
| 18 | Compost and cures | "Plant cures to bring (backup)" + number 0–40 | – (none) | `plantCureDoses` (0); yes | SupplyCalculator | some | 26 |
| 19 | **At the patches** (closed) | – | (?) "Each patch is harvested, then planted and composted before the next. The tool leprechaun is visited only when needed: for compost, a tool or room, to store a compost bin's compost, or for the next bin's buckets. Each visit also notes the produce, stores the empty buckets and takes compost for the next stops when there's room." | – | – | – | 27 closed, 136 open |
| 20 | At the patches | "Clean herbs" | "After picking herbs, a step to clean the ones your Herblore level allows, before noting them" | `cleanHerbs` (off); yes | PatchGuide | some | 18 |
| 21 | At the patches | "Note produce on the tool leprechaun" | "The produce is noted on visits to the leprechaun, saving inventory slots" | `noteProduce` (on); yes | PatchGuide | most | 30 |
| 22 | At the patches | "Empty buckets" above a drop-down: Drop them / Keep them / Store them at the tool leprechaun | – | `emptyBuckets` (drop); yes | PatchGuide, RunSession | some | 39 |
| 23 | At the patches | "Make compost in the compost bins" | "At stops with a compost bin: empty it when it's ready, then fill it with the crops below as they're picked" | `makeCompost` (off); yes | PatchGuide, RunSession, SupplyCalculator | some | 18 |
| 24 | At the patches, making compost | "Make ultracompost (brings volcanic ash)"; "Crops for the bin" + a tick box per allotment crop but snape grass | "A bin of supercompost gets 25 volcanic ash (50 in the Farming Guild's big bin) before it's emptied"; watermelon "Makes supercompost, as long as nothing else goes in the bin", others "Makes the bin regular compost" | `makeUltracompost` (on), `binCrops` (watermelon); yes | PatchGuide, SupplyCalculator | some | ~160 |
| 25 | **Farming contracts** (closed) | "Build farming contracts into runs" | box: "The guild's patches are kept for contracts instead of your usual crops, and a run stops at the guild whenever a contract needs it"; (?) seven lines on pre-plants, Jane and contract tiers | `farmingContracts` (off); no | RunSelector, ContractPlanner | some | 49 open |
| 26 | **Extras to bring** (closed) | "Outfit" above a drop-down: None / Graceful outfit / Farmer's outfit | – | `outfit` (none); yes | RunAssembly, RunSession, SupplyCalculator | some | 39 |
| 27 | Extras to bring | "Suggest yield boosts" | "Magic secateurs (+10% herb, allotment and hops yield) as an optional item; worn when nothing else needs the weapon slot" | `recommendEquipmentBoosts` (on); yes | SupplyCalculator | most | 18 |
| 28 | **Advanced** (closed; "N overrides") | "Include a run type when this % of its patches are due" + number 1–100 | – (none) | `dueThresholdPercent` (100); yes | RunSelector | rarely | 38 |
| 29 | Advanced | "Different protection for single patches"; when on, a row per changed patch ("Same as its type" / …) and "Add a patch..." | "Choose another protection for a patch than its type has in Protection. Ticking this off keeps your choices but ignores them." | `useProtectionOverrides` (off), `protectionOverrides`; yes | RunConfig.protectionFor | rarely | 30 + 39 per patch |
| 30 | Bottom | Help link, as Run #22 | | | | | 44 |

## Travel tab (`ui/TravelPage.java`)

How the route gets from stop to stop. Height: as first opened (Ways to travel open) 602; every family and section open
5,703.

| # | Section > item | Label / text | Hover | Saved as (default); presets | Read by | Who | Height |
|---|---|---|---|---|---|---|---|
| 1 | **Ways to travel** (open first time) | – | (?) "Every stop's way is the quickest the route can use. Untick a family, or open one and untick a way you can't or won't use: the route is planned again without it. Kept for your account, whichever preset is in use." | – | – | – | 27 |
| 2 | Ways to travel > 11 family rows: tick box, name, right side | Spells "9 usable", Teleport items, Tablets and scrolls, **House "set in Account > My house"** (name cut to "H…" at 225 px), Fairy rings, Spirit trees, Quetzals, Boats and ships, Gliders, balloons, carts, Home and minigame teleports, Agility shortcuts "by your level" | each family's description (what it covers, what Off does) | `travelFamilies` (all on but home and minigame teleports); no | AtlasLegs, AtlasWays, RoutePlanner, RunService, SupplyCalculator | most glance, some untick | 27–39 each |
| 3 | A family, opened | a tick box per way, greyed while it can't be used | what it needs, its charges or uses left, why not | `waysOff` (none); no | RunService, AtlasWays | some | 18–30 each (Spells 753, Teleport items 843) |
| 4 | Teleport items, opened | "Keep the last charge of my jewellery" | "Off: every charge is used. On: the last charge of your last skills necklace, glory, ring of wealth, combat bracelet or teleport crystal is kept back, and another way is used instead" | `keepLastCharge` (off); yes | RoutePlanner, TravelWays | some | 30 |
| 5 | Tablets and scrolls, opened | "Cast from runes, not tablets" | "Off: a teleport's tablet is used when you have one. On: teleports you can cast are cast from runes, and a tablet is only used for one you can't" | `useRunesNotTabs` (off); yes | SupplyCalculator | some | 18 |
| 6 | Ways to travel | "Avoid the Wilderness" | "On: no teleport, hop or walk into the Wilderness (the Ferox Enclave is safe). Off: the route may go through it, where other players can attack you" | `avoidWilderness` (on); no | RunService | rarely | 18 |
| 7 | Ways to travel | "Switch spellbook to cast" | "On: a spell from another spellbook can be cast after Spellbook Swap (on Lunar) or a trip to your house altar, when that's quicker. Off: only spells on the spellbook you're on are cast" | `switchSpellbooks` (off); no | RoutePlanner, TravelWays, SupplyCalculator | some | 18 |
| 8 | Ways to travel | "Travel style" above a drop-down: Fastest / Prefer free / Save charges | each choice: "The quickest way, whatever it uses up" / "Capes, unlimited items, spirit trees, fairy rings, and your house … when only a little slower" / "Avoid using up jewellery charges and daily teleports when something else is only a little slower" | `travelStyle` (fastest); yes | RoutePlanner | some | 39 |
| 9 | Ways to travel | "Walk when it's nearly as quick as teleporting" | "A walk up to about 20 seconds slower is used instead of a teleport, saving charges and clicks (e.g. one Falador Teleport for Falador Park and Taverley)" | `preferWalking` (on); yes | RoutePlanner | rarely | 30 |
| 10 | **Route** (closed) | "Order" above a drop-down: On - Autopilot (default) / On - Meta (wiki order) / Off (my own order) | "Autopilot finds the quickest order; Meta follows the wiki's order; Off keeps your own: drag the stops in the route list on the Run tab." | `routeMode` (AUTOPILOT); yes | RoutePlanner, RunService | some | 39 |
| 11 | Route | "Start at" above a drop-down: Anywhere (quickest) / every place | "Where the run starts. Anywhere lets the plugin choose; if you're standing at or near one of the run's stops, the run starts there either way." | `startLocation` (anywhere); yes | RoutePlanner, RunService | some | 39 |
| 12 | Route | "Finish near a bank" | – (none) | `endNearBank` (on); yes | RoutePlanner | rarely | 18 |
| 13 | Bottom | Help link, as Run #22 | | | | | 44 |

## Account tab (`ui/AccountPage.java`)

What you have that the plugin can't read. Height: closed 171; every section open 797.

| # | Section > item | Label / text | Hover | Saved as (default) | Read by | Who | Height |
|---|---|---|---|---|---|---|---|
| 1 | **My house** (closed) | – | (?) "Where your house is comes from the game (Settings > Debug > Detected). Set the nexus destinations by hand." | – | – | – | 27 closed, 398 open |
| 2 | My house | "Detect furniture when I enter my house (beta)" | "Fills in the jewellery box, pool, altar, fairy ring and spirit tree below from your house. Beta: … if it gets your house wrong, please report it." | `account.autoDetectHouse` (off) | PohDetector | some | 30 |
| 3 | My house, detecting | **Report a problem** (opens the GitHub issues page), "Last scanned 03/11/26 14:05" or "Enter your house to scan it", **Rescan house** | "Opens the plugin's GitHub issues page" / "Detection only adds furniture. If you removed or downgraded something, rescan: …" | `poh.lastDetected` | PohDetector | some | 59 |
| 4 | My house | "Jewellery box" above a drop-down: None / Basic (81 Con) / Fancy (86 Con) / Ornate (91 Con) | – | `poh.jewelleryBox` (none) | AtlasHouse, AtlasTravel | some | 39 |
| 5 | My house | "Pool" above a drop-down: None / Restoration (no run energy) / … / Ornate rejuvenation | – | `poh.pool` (none) | **nothing but the house scan** | – | 39 |
| 6 | My house | "Spellbook altar" above a drop-down: None / Ancient altar / Lunar altar / Dark altar (Arceuus) / Occult altar (all) | – | `poh.altar` (none) | AccessChecker | some | 39 |
| 7 | My house | "Fairy ring", "Spirit tree" | – | `poh.fairyRing`, `poh.spiritTree` (off) | AtlasHouse, SupplyCalculator | some | 36 |
| 8 | My house | "Portal nexus destinations" + 9 tick boxes, one a row | – | `poh.nexusDestinations` (none) | AtlasHouse | some | 184 |
| 9 | **Unlocks** (closed; "N ticked") | – | (?) "Ticked for you where the game shows it, or once you pass a planted spirit tree or the Weiss fire. Tick the rest that you have; the plugin can't see them until you do." | – | – | – | 27 closed, 203 open |
| 10 | Unlocks | a tick box per unlock the game can't confirm (planted spirit trees ×3, Fire of Nourishment, Champion rank), "(check)" when the run needs it | "Your run has a patch or a way to one that needs this" | `account.manualUnlocks` (none) | AccessChecker, UnlockSpotter | some, once | 18–30 each |
| 11 | Unlocks, until pressed | **These are right** | "Stop asking on the Run tab; you can still change these any time" | `account.unlocksReviewed` (off) | AccessChecker | some, once | 22 |
| 12 | **Storage sources** (closed) | – | (?) "Bank, inventory, equipment and the tool leprechaun are always counted." | – | – | – | 27 closed, 106 open |
| 13 | Storage sources | "Group storage (GIM)", "Seed vault" | – (none) | `useGroupStorage`, `useSeedVault` (on); no | HoldingsTracker, RunService, RunSession | some | 18 each |
| 14 | Storage sources, seed vault on | "When a run banks partway" above a drop-down: Bank at the Farming Guild / Take all seeds at the start | label: "For seeds you keep in the seed vault, on a run too big for one inventory."; each choice explained | `vaultBanking` (take all at the start); no | RunService, RunSession | some | 39 |
| 15 | Bottom | Help link, as Run #22 | | | | | 44 |

## Settings tab (`ui/SettingsPage.java`, `PresetsPage.java`, `PatchDebugPanel.java`, `HelpWanted.java`)

How the plugin behaves and looks. Height: closed 181; every section open 5,314.

| # | Section > item | Label / text | Hover | Saved as (default) | Read by | Who | Height |
|---|---|---|---|---|---|---|---|
| 1 | **Presets** (closed) | **Save current settings as...** (asks a name; confirms a replace); per preset its name ("(in use)") and Use / Rename / Delete | (?) "Save these settings under a name (e.g. "Quick herbs") and switch between them at the top of the Run tab. Presets cover the Farm and Travel tabs; your house and unlocks are shared." | `presets` | SettingsStore | some | 27 + 22 + 44 per preset |
| 2 | **Display** (closed) | "Highlight the patch", "Hint arrow to the patch", "Highlight NPCs", "Highlight items to use", "Highlight the spell or place to pick", "Growing vine highlights", "Walking lines (Shortest Path)" | each explained (what's outlined, where) | config `highlightPatch`, `hintArrow`, `highlightGardener`, `highlightItems`, `highlightSpell`, `vineHighlights`, `shortestPathLines` (all on); client-wide | the overlays, ShortestPathBridge, ChatOptions, FairyRingLog, menu highlighters | rarely | 18 each |
| 3 | Display | "Patch colour", "NPC colour", "Item colour", each a swatch on its own row opening RuneLite's colour picker | "Change the patch colour" … | config `patchColour` (cyan), `npcColour` (gold), `itemColour` (green); `itemColour` also colours the spell, the tab icon, menu options and travel-map places | overlays | rarely | 22 each |
| 4 | Display | "Remind me to drop weeds and pots" | "During a run, a reminder under your character while you carry weeds or empty plant pots" | `remindToDrop` (on); no | RunSession | rarely | 18 |
| 5 | **Debug** (closed) > "Detected" | read-only lines, name left (80 px column), value right: Spellbook, Fairy rings, Spirit trees, House, Teleport to House, confirmed unlocks (Yes/No), Quest points, Bottomless bucket, item charges, daily teleports left, Tool leprechaun, Auto-weed | how each was worked out (Spellbook: "Read from the game; teleports on another spellbook are used as tablets") | – | AccessChecker, HoldingsTracker | some, rarely | 26–62 each |
| 6 | Debug > "Tools" | "Count every patch (full run)" | "Supplies for every selected patch, not just the ones that are due. Handy for checking numbers." | `supplyFullRun` (off); no | RunService, RunPanel | developer, rarely | 18 |
| 7 | Debug > "Patch details" | every patch (80) as a two-line card: place, state; a small heading per type | "Lumbridge tree / seen 11m ago / planted 8h 21m ago / from RuneLite Time Tracking" | – | PatchTracker | developer | ~2,950 |
| 8 | **Reset** (closed) | **Reset to defaults...** → "Run settings: the Farm and Travel tabs and the highlights. Everything: also your house, unlocks, presets, tracked charges and run times. This can't be undone." Run settings / Everything / Cancel | "Put this plugin's settings back to how they were when it was installed" | – | SettingsStore | rarely | 53 open |
| 9 | **Help improve this plugin** (closed) | "Everything here ends the same way: a GitHub issue opens in your browser with the details filled in. You read it, and nothing is sent unless you submit it (a free GitHub account is needed). It never includes your name or account." | – | – | – | some | 87 |
| 10 | Help improve > "Report a problem" | "Something wrong with a run? The report includes your current plan: the stops, how you travel, and what you need and carry. You describe what happened." + **Report a problem** | "Opens GitHub's bug form with the plugin version and your current plan filled in. No bank counts, levels or location. Nothing is sent unless you submit it." | – | TestReport | some | 107 |
| 11 | Help improve > "Guided tests" | "Features I couldn't try myself. Each test walks you through it on the Run tab: it sets what it needs (your settings come back after), says what to bring, and asks Yes or No as you go. If you don't have what a test needs, it says so and stops, and nothing is reported. A few minutes each, no coding." | – | – | – | some | 133 |
| 12 | Help improve > Guided tests | dev client only: "Preview as a player" | "Show the help list and test reports the way players see them (dev client only)" | client value `help.previewAsPlayer` | TestRunner | developer | 18 |
| 13 | Help improve > Guided tests | a group per tab ("Run tab  6 to try"), a row per test ("Spellbook Swap  Not yet" / "Ready"), opened: the steps, then **Start test** or "You need to: …"; "Done" group; "See every report on GitHub" | row: title, steps, needs; Start: "Walks you through it on the Run tab" | `help.<ITEM>[.vN]` client values | HelpWanted, TestRunner | some | 27 per group, 23–35 per row |
| 14 | **Dev checks** (dev client only) | "Your own checks, never shown to players. …" + the DEV list, or "No dev checks right now." | – | – | HelpWanted | developer | – |

## RuneLite's config panel (`FarmRunAutopilotConfig`)

| Key | Name | Description | Default | Shown | Read by |
|---|---|---|---|---|---|
| `openSidebar` | Click to open the sidebar | "All Farm Run Autopilot settings are in its sidebar panel. Each click here opens it." | off (any click opens the panel) | yes | the plugin's config listener |
| `highlightPatch` | Highlight the patch | "Outline the patch the current step is about" | on | hidden (Settings > Display) | HighlightOverlay |
| `hintArrow` | Hint arrow to the patch | "Point the game's hint arrow (also on the minimap) at the patch" | on | hidden | HintArrowController |
| `highlightGardener` | Highlight NPCs | "Outline the gardener when paying them, and the quetzal after the Civitas teleport" (older than the sidebar's) | on | hidden | HighlightOverlay |
| `highlightItems` | Highlight items to use | as the sidebar | on | hidden | item, menu, chat and leprechaun highlighters |
| `highlightSpell` | Highlight the spell or place to pick | "…where to go on a travel map" | on | hidden | SpellHighlightOverlay, TravelMenuOverlay |
| `vineHighlights` | Vine highlights (sidebar: "Growing vine highlights") | older wording than the sidebar's | on | hidden | the vine overlays |
| `shortestPathLines` | Walking lines (Shortest Path) | "Ask the Shortest Path plugin, if installed, to draw the walk for each "walk to" step" | on | hidden | ShortestPathBridge |
| `patchColour` | Patch colour | "Outline colour for the patch" | cyan, alpha 220 | hidden | HighlightOverlay |
| `npcColour` | Gardener colour (sidebar: "NPC colour") | "Outline colour for the gardener" | gold, alpha 220 | hidden | HighlightOverlay |
| `itemColour` | Item colour | "Outline colour for items to use" | green, alpha 220 | hidden | item, spell, tab, menu and map highlighters |

Also the plugin's own description, shown in RuneLite's plugin list and on the Plugin Hub
(`@PluginDescriptor`, `runelite-plugin.properties`): "Plans and guides combined tree, fruit tree and herb farming runs";
tags farming, farm, run, herb, tree, fruit, route, planner.

## The help list (`testing/TestItem.java`, `GuidedTests.java`)

| Constant (rev.) | Group | Title | What the steps ask |
|---|---|---|---|
| SPELLBOOK_SWAP (2) | Run | Spellbook Swap | Lunar book, no standard altar: the warning, the runes on the bank tab, the spell outlined |
| SEED_VAULT_SECTION (4) | Run | Farm run section in the seed vault | the flashing button, the section, its list entry, Favourite Slots, turning it off |
| VAULT_SEEDS_FOR_LATER (2) | Run | Seed vault seeds for later in the run | "Take all seeds at the start": Getting ready from the vault, Deposit inventory, the bank visit |
| VAULT_BANK_AT_GUILD | Run | Banking at the Farming Guild | "Bank at the Farming Guild": the bank visit at the guild, the vault step |
| STAFF_SWAPS | Run | Swapping staves on the way | the wield step, the blue outline; asks "Does the Run tab's list have one staff to wield…" (the list is on the bank tab now) |
| ALLOTMENT_PAYMENT | Run | Paying for one allotment | "Pay Elstan for the north-west allotment"; set in Farm > Protection |
| ALLOTMENT_FLOWER_PROTECTION | Farm | Flowers protecting allotments | marigold first, never paying Elstan; asks "Does the Run tab's list have a Marigold seed…" |
| FORTIS_CHAMPION (2) | Farm | Civitas herbs and the Colosseum | no plant cure for Civitas with the Champion rank |
| BUCKET_COMPOST (2) | Farm | Bottomless bucket at the tool leprechaun | Check, store, compare with Detected |
| ICY_BASALT | Travel | Icy basalt to Weiss | the stop says "Icy basalt", landing by the patch |
| HOUSE_BY_TREE | Travel | Spirit tree into your house | "Spirit tree to your house, then...", the guide moving on |
| HOUSE_TURNED_OFF | Travel | Turning your house off | unticking House on the Travel tab |
| SEAWEED_DIVE | Travel | Diving to the seaweed patches | the rowboat, Dive, one spore |
| SPIRIT_TREES (2) | Account | Planted spirit trees | the unlock ticking itself |
| HOUSE_SCAN (4) | Account | House scanning (beta) | jewellery box, pool, altar, fairy ring, spirit tree filled in; not changed in someone else's house |
| ETCETERIA_ACCESS | Account | Etceteria's bush patch | rake or plant with only The Fremennik Trials |
| GREAT_CONCH_ACCESS (2) | Account | The Great Conch's calquat patch | rake or plant with Troubled Tortugans started |

No dev checks at present.
