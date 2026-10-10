# Farm Run Autopilot — Build Spec (v1)

> **Who this is for:** Claude Code, building a RuneLite Plugin Hub plugin in the `farm-run-autopilot` repo (currently the stock RuneLite example template + `AGENTS.md`).
> **Owner:** Sean (product decisions, in-game testing). Sean is not a developer — explain anything he must do in plain language.
> **Status:** Draft 1, 2026-10-06. Items marked **UNVERIFIED** must be confirmed in-game or against the OSRS wiki before being treated as fact.

---

## 0. How to work from this spec (read first)

1. **Follow `AGENTS.md` in the repo at all times.** It contains the RuneLite coding rules and the Plugin Hub forbidden-feature list. If anything in this spec conflicts with `AGENTS.md`, `AGENTS.md` wins — stop and flag it to Sean.
2. **Build in small milestones (section 15), one at a time.** Each milestone ends with a short in-game test list for Sean. Do not start the next milestone until Sean confirms the current one works. Sean explicitly prefers small chunks over big pushes.
3. **You cannot test in-game.** Never automate or screen-control the game. Offer `./gradlew run`, tell Sean exactly what to check, and wait.
4. **Game facts live in data, not logic.** Patches, crops, teleports, rune costs, requirements and walk-time estimates go in one data layer (sections 5–8) so they can be corrected without touching planner/UI code.
5. **Background research** is in `reference/teleports.md` (travel methods, rune costs, POH, run orders) and `reference/data.md` (crops, varbits, Time Tracking and Quest Helper internals). Use them when a section here is too brief; this spec wins if they disagree.
6. **Starting from scratch.** Do not fork Farming-Helper (`Speaax/Farming-Helper`, BSD-2-Clause). You may consult it, and you may reuse coordinates/location data from it only if the BSD-2 copyright notice ("Copyright (c) 2025, JThomasDevs") is kept in a `THIRD_PARTY_NOTICES` file.

---

## 1. Product summary

A farming-run planner and guide for tree, fruit tree and herb runs that:

- lets the player choose **which patches they have**, **what to plant**, **how to protect**, and **how to travel** to each location;
- works out **exactly what to bring** — coins, runes (or tabs), seeds/saplings, gardener payments, compost, tools — and shows it as a live checklist plus a **generated bank tab** (Quest Helper style);
- builds a **single combined route** across all due run types, grouping locations that have several patches (Gnome Stronghold, Farming Guild, Catherby);
- **automatically decides which run types are due** (e.g. trees and herbs are ready but fruit trees aren't, so fruit trees are skipped this run);
- guides the player step by step with highlights and arrows, and suggests a **run-energy restore** (POH pool or Ferox Enclave) or stamina dose when needed.

**The niche:** Farming-Helper covers the patches but runs one type at a time against a fixed item list, has no route optimisation, no coin/rune totals, no bank tab and no "what's due" logic.

### Non-goals (v1)
- No automation of any kind: no clicking, no input, no menu entries that send actions to the server (Hub rule).
- No notifications (Sean's decision — leave them to the core Time Tracking plugin).
- No run types beyond tree / fruit tree / herb (see "Later" in section 2).

---

## 2. Locked decisions (from Sean)

| Topic | Decision |
|---|---|
| Name | Keep **Farm Run Autopilot**. Rename only if Plugin Hub review objects. |
| Release target | **Plugin Hub** — all Hub rules apply from day one. |
| v1 run types | **Herb, tree, fruit tree.** |
| Later (not v1) | Hardwood/special trees (Fossil Island, Avium Savannah, calquat, celastrus, redwood, spirit tree), allotments/flowers/hops/bushes, seaweed/cactus/mushroom/belladonna. Design the data model so these can be added as new patch types without refactoring. |
| Combined runs | **One combined route** across all due run types. |
| Patch access | **Auto-detect** (quests, diaries, Farming level) and grey out locked patches; player can **untick any patch** manually. |
| Seed choice | **One seed/sapling per run type** by default. Optional **backup choices** (1st/2nd/3rd) used when the player runs out of the 1st; disease-free and protected patches get the best crops first. Optional herb list **"Seeds for disease-free patches"** (multi-select): disease-free patches get those herbs first, most valuable first by GE price, and other patches never get them. (Changed by Sean, 2026-10-07.) |
| Tree/fruit tree protection | Options: **Pay gardener**, **Compost only**, **Per-patch override**, **Pay with noted items** (gardeners accept noted payment — verified on the wiki Farming page). |
| Clearing | Option to **pay gardener 200 coins** to remove a grown tree/fruit tree (verified on the wiki). Adds 200 coins per relevant patch. |
| Herb extras | **Compost per herb patch** (with bottomless bucket charge counting), **equipment boosts** as optional "recommended" items (magic secateurs, farming cape/outfit), **plant cure / Cure Plant / Resurrect Crops** as optional backups. |
| Auto-weed | If Tithe Farm **Auto-weed** is owned and switched on, empty patches stay weed-free once raked → **don't require a rake**. If off/unowned, require a rake. |
| Teleports | **Dropdown per location + "Auto (best)"** option that picks the fastest owned and unlocked method. |
| Runes | Support rune pouch / divine rune pouch, elemental + combination runes, elemental/combination staves, tabs and jewellery. **Prefer tabs** (save slots), with a **manual "use runes instead of tabs" override** (global + per location). |
| POH | **Auto-detect on visit + manual edit** in a "My POH" section (nexus destinations, jewellery box tier, pool tier, fairy ring, spirit tree, house portal location). |
| Storage sources | Default: **bank + inventory + equipment + tool leprechaun**. Settings toggles (default **off**) for **GIM group storage** and **seed vault**. |
| Borrowed features (v1) | Quest Helper **bank tab + item checklist**, Quest Helper **step guidance** (highlights + arrows), **Time Tracking awareness** (patch states), **Presets**. |
| Route optimisation modes | **On – Autopilot** (our time-based algorithm), **On – Meta** (wiki order), **Off** (player's own order). **Drag to reorder** in the sidebar in every mode (a manual reorder switches the run to "Off/custom" for that run). |
| Route rules | Default start **Farming Guild**; end at the location **nearest a bank**. Make sure there are enough **free inventory slots before fruit tree stops**; if not, insert a **bank stop** or recommend a **bank teleport** (ring of dueling, crafting cape, max cape, etc.) or noting produce at the tool leprechaun. |
| Due detection | **Auto-skip run types that aren't ready** (e.g. fruit trees on their 16h cycle when only trees/herbs are due). |
| Run energy | **Live threshold**: if energy is **below 30%** before a walk of **15+ tiles**, the next step becomes "drink a stamina dose" if you have one, otherwise a restore stop (POH pool or Ferox — whichever is quicker from there). Optional stamina doses in the supply list. |
| Leg timings | **Ship estimates, then self-learn** from the player's actual leg times. |
| Sidebar | **Run, Farm, Travel and Account tabs** (Sean, 2026-10-10; layout in docs/plans/sidebar-ux.md). Run: run types, stages, route, checklist, presets. Farm: what to grow, when, and how to protect it. Travel: defaults for every stop, route, per-location overrides. Account: what the plugin detected, house, unlocks, storage, display. Checklist colours: green carried, yellow in storage, red missing. |
| Notifications | **None.** |

---

## 3. Glossary (plain language)

- **Patch** — one farming plot (e.g. "Gnome Stronghold tree patch").
- **Location / stop** — a place you travel to; may contain several patches (Farming Guild has a tree, fruit tree and herb patch).
- **Run type** — tree, fruit tree or herb.
- **Due** — a patch is ready to harvest/check (or dead/diseased/empty and needs replanting).
- **Leg** — travel from one stop to the next, using one travel method.
- **Travel method** — any way of getting to a location: a spell, tab, jewellery, fairy ring, spirit tree, POH portal, etc.
- **Varbit** — a number the game sends to the client describing state (e.g. what's growing in a patch). RuneLite lets plugins read these.

---

## 4. Hub compliance checklist (must stay true)

- No input injection, no automation, no menu entries that send actions to the server. The bank-title button is client-side only (same as Quest Helper's).
- No reflection, no external processes, no dynamic code loading, Java 11, BSD-2 licence.
- No network calls in v1. (If GE prices are ever added, use RuneLite's `ItemManager`, not a third-party server.)
- Use `net.runelite.api.gameval` constants (`ItemID`, `VarbitID`, `InterfaceID`, `ObjectID`, `NpcID`). No magic numbers where a gameval constant exists.
- Track scene objects via spawn/despawn events — never scan the whole scene every tick.
- Keep overlay `render()` work minimal; precompute on `GameTick`.
- Config group: `farmrunautopilot`. Never rename config keys without a migration.
- Rename everything from the template (`com.example`, `ExamplePlugin`, etc.) — see milestone M0.

---

## 5. Data model

All game data is static and lives in `data/` (Java enums/records or a bundled JSON resource loaded with the injected `Gson` — Claude Code's choice; JSON is easier for Sean to correct). Suggested types:

```
PatchType        { TREE, FRUIT_TREE, HERB }            // extensible: HARDWOOD, CALQUAT, ...
Location         { id, name, worldPoint, regionIds[], hasBank, nearestBankTiles, hasToolLeprechaun, patches[] }
Patch            { id, locationId, type, regionId, extraRegionIds[], varbitId, gardenerNpcId, gardenerName,
                   requirements[], diseaseFreeRequirement?, worldPoint, objectIds[] }
Crop             { id, type, name, farmingLevel, growthMinutes, seedItemId, saplingItemId?,
                   payment {itemId, qty, notedOk}, yieldNote }
TravelMethod     { id, name, kind (SPELL|TAB|JEWELLERY|CAPE|DIARY_ITEM|FAIRY_RING|SPIRIT_TREE|POH_PORTAL|
                   POH_NEXUS|JEWELLERY_BOX|GLIDER|QUETZAL|MINECART|WALK|OTHER),
                   destinationLocationId, arrivalPoint, requirements[], cost {runes[], items[], coins, charges},
                   spellbook?, tabItemId?, estWalkTiles, estSeconds, isWikiPrimary }
Requirement      { kind (QUEST|DIARY|SKILL|ITEM|POH|VARBIT|UNLOCK), ref, level?, note }
PohSetup         { portalLocation, nexusDestinations[], jewelleryBoxTier, poolTier, hasFairyRing,
                   hasSpiritTree, mountedXerics, mountedDigsite, lastDetected }
RunConfig        { enabledTypes[], seedPerType{}, protectionPerType{}, protectionOverrides{patchId},
                   payToClear{type}, compostPerType{}, teleportChoice{locationId -> methodId|AUTO},
                   useRunesNotTabs (global + per location), staminaDoses, energyThreshold=30, energyMinTiles=15,
                   routeMode (AUTOPILOT|META|OFF), customOrder[], startLocation=FARMING_GUILD, endNearBank=true,
                   storageSources {groupStorage=false, seedVault=false} }
Preset           { name, RunConfig }
```

### 5.1 Location groups (multi-patch stops)

| Location | Patches |
|---|---|
| Farming Guild | Tree (65 Farming), Herb (65), Fruit tree (85) — bank chest + seed vault + leprechaun on site |
| Gnome Stronghold | Tree + Fruit tree |
| Catherby | Herb (north farm) + Fruit tree (east beach) — close enough to treat as one stop with an internal walk |
| Falador | Herb (south farm) + Tree (park) — **separate stops** (different arrival teleports), but the planner may link them with a walk edge |

---

## 6. Patch catalogue (v1: 24 patches)

Region/varbit data comes from RuneLite core `timetracking/farming/FarmingWorld.java` (copy the values — those classes are package-private; also copy each region's **extra region IDs** and any `isInBounds` overrides, e.g. Catherby). `TRANSMIT_x` = `VarbitID.FARMING_TRANSMIT_x`.

### Trees
| Patch | Region | Varbit | Gardener | Requirement |
|---|---|---|---|---|
| Lumbridge (west of castle) | 12594 | TRANSMIT_A | Fayeth | — |
| Varrock (palace courtyard) | 12854 | TRANSMIT_A | Treznor | — |
| Falador Park | 11828 | TRANSMIT_A | Heskel | — (Elite Falador diary: disease-free + free protection) |
| Taverley | 11573 | TRANSMIT_A | Alain | — |
| Gnome Stronghold | 9781 | TRANSMIT_A | Prissy Scilla | — |
| Farming Guild | 4922 | TRANSMIT_G | Rosie | 65 Farming |
| Nemus Retreat (Auburnvale) | 5427 | TRANSMIT_A | Aub | — (Varlamore access) |

### Fruit trees
| Patch | Region | Varbit | Gardener | Requirement |
|---|---|---|---|---|
| Gnome Stronghold | 9781 | TRANSMIT_B | Bolongo | — |
| Tree Gnome Village (west of maze) | 9777 | TRANSMIT_A | Gileth | — |
| Catherby (east beach) | 11317 | TRANSMIT_A | Ellena | — |
| Brimhaven (north) | 11058 | TRANSMIT_A | Garth | — |
| Lletya | 9265 | TRANSMIT_A | Liliwen | Started Mourning's End Part I (for teleport crystal) |
| Farming Guild | 4922 | TRANSMIT_K | Nikkie | 85 Farming |
| Kastori | 5423 | TRANSMIT_B | Ehecatl (**UNVERIFIED** — one wiki page says "Master Farmer") | — |

### Herbs (gardeners cannot protect herbs — compost or disease-free unlocks only)
| Patch | Region | Varbit | Requirement | Disease-free when |
|---|---|---|---|---|
| Falador (south) | 12083 | TRANSMIT_D | — | never |
| Port Phasmatys (Morytania) | 14391 | TRANSMIT_D | Priest in Peril | never |
| Catherby | 11062 | TRANSMIT_D | — | never |
| Ardougne (north) | 10548 | TRANSMIT_D | — | never |
| Hosidius | 6967 | TRANSMIT_D | — | Easy Kourend & Kebos diary (**UNVERIFIED**) |
| Troll Stronghold | 11321 | TRANSMIT_A | My Arm's Big Adventure | always |
| Harmony Island | 15148 | TRANSMIT_B | Elite Morytania diary | always (**UNVERIFIED**) |
| Weiss | 11325 | TRANSMIT_A | Making Friends with My Arm + Fire of Nourishment built | always |
| Farming Guild | 4922 | TRANSMIT_E | 65 Farming | never |
| Civitas illa Fortis / Ortus Farm | 6192 | TRANSMIT_D | Varlamore access | Champion rank, Fortis Colosseum (**UNVERIFIED**) |

**Herb yield bonuses worth showing in the UI only (not v1 logic):** Catherby (Elite Kandarin, up to +15%), Hosidius (Kourend diary tiers), Farming Guild (+5% Hard Kourend), Falador (+10% xp, Medium Falador).

### 6.1 Patch state decoding
Copy the varbit value ranges from RuneLite core `PatchImplementation` (TREE, FRUIT_TREE, HERB — **including Huasca 60–63/64–66, diseased 173–175**) into our own `PatchStateDecoder.decode(type, value) -> {crop, state, stage}` where state ∈ `EMPTY, WEEDS, GROWING, DISEASED, DEAD, CHECK_HEALTH, HARVESTABLE, STUMP`. Do not import RuneLite's classes. Unit-test every boundary value.

---

## 7. Crop catalogue

### Trees (plant a **sapling** with a spade; pay or compost; check health when grown)
| Tree | Lvl | Growth | Gardener payment |
|---|---|---|---|
| Oak | 15 | 2h 40m | 1 × Tomatoes(5) |
| Willow | 30 | 4h | 1 × Apples(5) |
| Maple | 45 | 5h 20m | 1 × Oranges(5) |
| Yew | 60 | 6h 40m | 10 × Cactus spine |
| Magic | 75 | 8h | 25 × Coconut |

### Fruit trees (sapling; 16h growth; 6 fruit when grown; fruit regrows ~40 min each — RuneLite uses 45, **UNVERIFIED**)
| Tree | Lvl | Gardener payment |
|---|---|---|
| Apple | 27 | 9 × Sweetcorn |
| Banana | 33 | 4 × Apples(5) (**UNVERIFIED** basket vs loose) |
| Orange | 39 | 3 × Strawberries(5) (**UNVERIFIED**) |
| Curry | 42 | 5 × Bananas(5) (**UNVERIFIED**) |
| Pineapple | 51 | 10 × Watermelon |
| Papaya | 57 | 10 × Pineapple |
| Palm | 68 | 15 × Papaya fruit |
| Dragonfruit | 81 | 15 × Coconut |

### Herbs (seed + seed dibber; 80 min growth; no protection possible)
Guam 9, Marrentill 14, Tarromin 19, Harralander 26, Ranarr 32, Toadflax 38, Irit 44, Avantoe 50, Kwuarm 56, Snapdragon 62, **Huasca 65**, Cadantine 67, Lantadyme 73, Dwarf weed 79, Torstol 85.

### 7.1 Rules
- **Gardeners accept noted payment** (wiki). Noted payment = 1 slot per item type.
- Payment must be **in the inventory** (not leprechaun storage — **UNVERIFIED**, assume inventory).
- **Clearing:** a grown tree/fruit tree must be cleared before replanting. If **pay to clear** is on, the gardener removes it for **200 coins** (wiki). If it's off, the plan is **chop the tree, then dig up the stump with a spade** — this needs an axe and produces logs/roots (see 11.4, 11.5).
- **Fruit trees:** always pick the fruit **before** clearing (paying to clear destroys any fruit left on the tree).
- **Trees need check-health** before they can be cleared/replanted.
- **Falador Park tree** with Elite Falador diary: free protection — never count a payment there.
- **Compost:** compost −50%, supercompost −80%, ultracompost −90% disease chance; herbs get +1/+2/+3 harvest "lives". Bottomless bucket holds one type, up to 10,000 uses.
- **Disease cures:** Plant cure (item, leprechaun-storable); Cure Plant (Lunar 66, 1 astral + 8 earth, Lunar Diplomacy); Resurrect Crops (Arceuus 78, 25 earth + 8 blood + 12 nature + 8 soul, dead patches only, 50–75% success; whether it works on trees is **UNVERIFIED**). Fertile Soil (Lunar 83, 15 earth + 3 astral + 2 nature) can replace compost buckets.

---

## 8. Travel catalogue

Walk estimates: **S** ≈ 0–15 tiles, **M** ≈ 15–40, **L** > 40 or needs obstacles. All are starting estimates (**UNVERIFIED**); self-learning (section 12.4) replaces them. ★ = wiki primary choice (used by Meta mode and as the default when the player owns it).

### 8.1 Spell rune costs (needed for rune maths)
| Spell | Book | Magic | Runes | Requirement |
|---|---|---|---|---|
| Varrock Teleport | Standard | 25 | 3 air, 1 fire, 1 law | — |
| Lumbridge Teleport | Standard | 31 | 3 air, 1 earth, 1 law | — |
| Falador Teleport | Standard | 37 | 3 air, 1 water, 1 law | — |
| Teleport to House | Standard | 40 | 1 air, 1 earth, 1 law | — |
| Camelot Teleport | Standard | 45 | 5 air, 1 law | — |
| Kourend Castle Teleport | Standard | 48 | 1 fire, 1 water, 2 law | Client of Kourend |
| Ardougne Teleport | Standard | 51 | 2 water, 2 law | Plague City |
| Civitas illa Fortis Teleport | Standard | 54 | 1 earth, 1 fire, 2 law | Twilight's Promise |
| Trollheim Teleport | Standard | 61 | 2 fire, 2 law | Eadgar's Ruse |
| Draynor Manor Teleport | Arceuus | 17 | 1 earth, 1 water, 1 law | — |
| Battlefront Teleport | Arceuus | 23 | 1 earth, 1 fire, 1 law | — |
| Fenkenstrain's Castle Teleport | Arceuus | 48 | 1 earth, 1 soul, 1 law | Priest in Peril |
| Harmony Island Teleport | Arceuus | 65 | 1 law, 1 nature, 1 soul | The Great Brain Robbery |
| Kharyrll Teleport | Ancient | 66 | 1 blood, 2 law | Desert Treasure I |
| Fishing Guild Teleport | Lunar | 85 | 10 water, 3 astral, 3 law | Lunar Diplomacy |
| Catherby Teleport | Lunar | 87 | 10 water, 3 astral, 3 law | Lunar Diplomacy |

Spellbook mismatch: if a leg needs a spell from a book other than the active one, prefer the tab; if `useRunesNotTabs` is on, warn "needs <book> spellbook" (Spellbook Swap / altar not planned in v1).

### 8.2 Methods per location

**Trees**
- **Lumbridge** — ★Lumbridge Teleport/tab (M); Home Teleport (M, slow cast); Achievement diary cape (M).
- **Varrock** — ★Varrock Teleport/tab (M); diary cape → Toby (S/M); Skills necklace → Cooks' Guild (M); spirit tree/ring of wealth → GE (L).
- **Falador Park** — ★Ring of wealth → Falador Park (S); Falador Teleport (M); Skills necklace → Mining Guild (M).
- **Taverley** — ★Teleport to House with portal in Taverley / Taverley tab (S); Falador Teleport (L); Games necklace → Burthorpe (L); Warriors' Guild via combat bracelet (L); balloon (S, unlock **UNVERIFIED**).
- **Gnome Stronghold** — ★Slayer ring → Stronghold Slayer Cave (S); spirit tree (M); royal seed pod (M, MM2); gnome glider Ta Quir Priw (M).
- **Farming Guild** — ★Farming cape / ★Skills necklace (S/M; necklace lands inside with 45 Farming); guild spirit tree (85 Farming); fairy ring **CIR** (M); Lovakengj minecart 20 coins (M); Battlefront Teleport (L); Rada's blessing 3/4 (M).
- **Nemus Retreat** — ★Pendant of Ates → Nemus Retreat (S, statue activated); Quetzal → Auburnvale (M); fairy ring **AIS** + agility shortcuts 24/36 (M). Bank buffalo on site.

**Fruit trees**
- **Gnome Stronghold** — ★spirit tree (S/M); slayer ring (M); seed pod / glider (M).
- **Tree Gnome Village** — ★spirit tree → Tree Gnome Village, through loose railing, follow Elkoy (M) — **natural link from Gnome Stronghold**; fairy ring **CIQ** (M).
- **Catherby** — ★Camelot Teleport (L); Catherby Teleport/tab, lands by bank (M); glider White Wolf Mtn (L); charter ship (M).
- **Brimhaven** — ★Teleport to House with portal in Brimhaven / tab (S/M); planted spirit tree (S, 83 Farming); Ardougne tele + boat (L); Karamja gloves 3+ / glory (L).
- **Lletya** — ★Teleport crystal / eternal (S). Only practical option.
- **Farming Guild** — as trees; fruit tree is in the advanced tier (85).
- **Kastori** — ★Quetzal → Kastori (S; landing site must be built); Pendant of Ates → North of Kastori (S).

**Herbs**
- **Falador (south)** — ★Explorer's ring 2/3/4 → cabbage patch (S; ring 2 = 3/day); Draynor Manor Teleport (M); spirit tree Port Sarim (M); glory → Draynor (M/L); Falador Teleport (L).
- **Port Phasmatys** — ★Fairy ring **ALQ** (S/M); Ectophial (M, unlimited); Fenkenstrain's Castle Teleport (M); Kharyrll (L).
- **Catherby** — ★Catherby Teleport/tab (S); POH nexus Catherby (S); Camelot Teleport (L).
- **Ardougne** — ★Ardougne cloak 2/3/4 farm teleport (S; cloak 2 = 3/day, 3 = 5/day, 4 = unlimited); Skills necklace → Fishing Guild (M); Fishing Guild Teleport (M); combat bracelet → Ranging Guild (M); fairy ring **BLR** (M); Ardougne Teleport (L).
- **Hosidius** — ★Xeric's talisman → Xeric's Glade (S); House portal in Hosidius (S/M); Kharedst's memoirs (S); fairy ring **AKR** (M); Skills necklace → Woodcutting Guild + 45 Agility stones (M).
- **Troll Stronghold** — ★Stony basalt to roof (S; Hard Fremennik + 73 Agility); stony basalt entrance (M/L); Trollheim Teleport (L); POH portal.
- **Harmony Island** — ★Harmony Island Teleport/tab (S); POH portal/nexus (S).
- **Weiss** — ★Icy basalt (S); POH portal/nexus (S).
- **Farming Guild** — as trees.
- **Civitas / Ortus Farm** — ★Quetzal whistle → Hunter Guild (S/M); Hunter cape (S/M); Civitas Teleport + Quetzal (M); fairy ring **AJP** (M).

### 8.3 POH options
- **House portal locations** (Teleport to House / house tabs land *inside* the house unless the house is set to "teleport outside" or the player uses a redirected tab such as the Taverley teleport tablet — add exit-portal time when neither applies, and a "My POH" setting for it): Rimmington 1, **Taverley 10**, Pollnivneach 20, **Hosidius 25**, Rellekka 30, Aldarin 35, **Brimhaven 40**, Yanille 50, Prifddinas 70 (Construction).
- **Portal nexus**: marble 72 (4 dests), gilded 82 (8), crystalline 92 (41). Farming-relevant: Catherby, Kharyrll, Harmony, Fishing Guild, Troll Stronghold, Weiss, Fenkenstrain's, Kourend, Civitas, Ardougne.
- **Jewellery box**: basic 81 (dueling, games), fancy 86 (+ combat bracelet, skills necklace), ornate 91 (+ glory, ring of wealth). Unlimited charges.
- **Pools**: restoration 65 = spec only (**no run energy**); revitalisation 70 = + run energy; rejuvenation 80 = + prayer; fancy 85; ornate 90.
- **POH fairy ring** 85; **POH spirit tree** 75 Con + 83 Farming; combined 95.
- Leaving POH via nexus/jewellery box/fairy ring/spirit tree counts as the leg's travel method; "Teleport to House" is a separate cost (runes or house tab, or Construction cape/max cape).

### 8.4 Run energy and bank helpers
- **Ferox Enclave** (ring of dueling, minigame teleport): Pool of Refreshment restores run energy, HP, prayer; bank chest beside it.
- **Explorer's ring** run restore: ring 2 = 50% ×3/day, ring 3 = 50% ×4/day, ring 4 = 100% ×3/day.
- **Quick-to-bank**: crafting cape / max cape (Crafting Guild bank), ring of dueling → Castle Wars, Ferox, Camelot → Seers' bank toggle (Hard Kandarin), Varrock → GE toggle (Medium Varrock), Catherby Teleport, Farming Guild bank chest, Nemus Retreat bank buffalo.

### 8.5 Meta (wiki) orders
- Trees: Lumbridge → Varrock → Falador → Taverley → Gnome Stronghold → Farming Guild → Nemus Retreat.
- Fruit trees: Gnome Stronghold → Tree Gnome Village → Catherby → Farming Guild → Lletya → Brimhaven → Kastori.
- Herbs: Falador → Port Phasmatys → Ardougne → Catherby → Hosidius → Farming Guild → Civitas → Troll Stronghold → Weiss → Harmony Island.
- Combined Meta order: merge these by grouping shared locations at the position of their first appearance, then apply the start/end rules. Keep it editable (data file).

---

## 9. Access detection (what the player can use)

- **Skills:** `client.getRealSkillLevel(Skill.FARMING)` etc. (boosts don't count unless a requirement is boostable — Farming Guild 65 is boostable per the wiki; treat it as real-level by default with a tooltip).
- **Quests:** `Quest.X.getState(client)` on the client thread. Cache results; refresh on `VarbitChanged`/login, not every tick.
- **Diaries:** diary completion varbits from `gameval.VarbitID` (look up exact names, e.g. Ardougne/Falador/Kandarin/Morytania/Fremennik/Kourend/Lumbridge tiers).
- **Owned items:** item is in inventory, equipment, cached bank, leprechaun (tools only) or (if enabled) group storage / seed vault.
- **Charges/daily limits:** Ardougne cloak and Explorer's ring daily uses — if the remaining count can't be read reliably, show the method with a "limited per day" note rather than guessing (**UNVERIFIED** varbits).
- **Unlocks:** fairy rings (Fairytale II started + dramen/lunar staff unless Elite Lumbridge diary), spirit trees (Tree Gnome Village; The Grand Tree), Quetzal landing sites, Pendant of Ates statues, Fire of Nourishment. Where no varbit is known, fall back to a manual toggle in Setup.
- **Tithe Farm Auto-weed:** `VarbitID.FARMING_BLOCKWEEDS` (5557): 0 = not owned, 1 = off, 2 = on.
- **UI behaviour:** locked patches/methods are shown greyed with the missing requirement as a tooltip ("Needs Elite Morytania diary"). The player can untick any patch or method; a manual untick always wins over detection.

---

## 10. Patch state tracking and "what's due"

Build our own tracker (mirroring core Time Tracking — don't read its private config as the main source):

1. `PatchDef` table (section 6) including extra region IDs and bounds overrides.
2. On `GameTick`: skip if a modal widget is open or the player just crossed a region boundary; for patches whose region matches the player's location, read `client.getVarbitValue(varbit)`, decode (6.1), and store `value:epochSeconds` with `setRSProfileConfiguration("farmrunautopilot", "patch.<region>.<varbit>", …)` when it changes (or every 5 minutes).
3. Predict growth from crop growth time and the 5-minute farming tick. Store "planted at" when we see the patch move from empty → first growing stage.
4. Track compost and payment via the same chat-message patterns core uses (`CompostTracker`, `PaymentTracker`) so protection/compost state per patch is known.
5. **Optional fallback:** if we have never seen a patch but core Time Tracking has data (`timetracking` group, key `<regionID>.<varbitID>` = `value:unixSeconds`), read it via `ConfigManager.getRSProfileConfiguration`. Treat this as best-effort; it may change without notice.

**Due rules:**
- A patch is **due** if it is predicted HARVESTABLE / CHECK_HEALTH / STUMP, or DISEASED/DEAD/EMPTY/WEEDS, or **unknown** (never seen — show "?").
- A **run type is included** in the next run if **all its enabled patches are due** (default). Setting: "Include run type when at least N% of its patches are due" (default 100%). Show the excluded type in the Run tab with a countdown: "Fruit trees ready in 5h 12m".
- The player can force-include or force-skip any run type for this run.
- Within an included run type, patches that aren't due are skipped with a reason ("Taverley: magic still growing, 1h 40m").

---

## 11. Supply calculator

Runs whenever config, inventory, equipment, bank cache or route changes (debounce to once per tick). Output: a list of `SupplyLine {item, quantityNeeded, have{inv, equip, bank, leprechaun, groupStorage, seedVault}, status, slotsUsed, reason}`.

### 11.1 Seeds, saplings, payment
- Per due patch of each type: 1 sapling (trees/fruit) or 1 seed (herbs). With backup choices, patches are assigned crops by CropAllocator: safe patches first, each getting the best choice still in stock.
- Payment per patch if protection = Pay (skip Falador tree with Elite Falador diary; skip if the patch is already protected — e.g. paid earlier and still growing).
- "Pay with notes" → request noted payment items; slot count 1 per item type.

### 11.2 Coins
- 200 × number of tree/fruit-tree patches where **pay to clear** is on and the patch is predicted to need clearing.
- Plus travel coin costs (Lovakengj minecart 20, charter ships, etc.) per chosen method.
- Round up and show a breakdown on hover.

### 11.3 Runes vs tabs
For each leg that uses a spell:
1. If `useRunesNotTabs` is off and the tab is owned (inv/bank) → request 1 tab per leg.
2. Otherwise request runes per 8.1.
3. Sum all rune needs, then subtract **free sources**: equipped elemental/combination staff or tome (infinite of that element), **rune pouch / divine rune pouch** contents (read pouch varbits as core `RunepouchPlugin` does), and **combination runes** (dust = air+earth, mist = air+water, mud = water+earth, lava = earth+fire, steam = water+fire, smoke = air+fire) — allocate combo runes to cover the largest shortfall first.
4. Add cure/resurrect/fertile soil runes if those options are enabled.
5. Show "Bring 7 law, 3 water (pouch already has 20 air)".

### 11.4 Tools and other items
- **Spade** — always (planting saplings, clearing dead herbs, digging stumps).
- **Axe** — only if pay-to-clear is off for any due tree/fruit tree (best owned axe; can be worn).
- **Rake** — only if Auto-weed is not ON, or any due patch is predicted WEEDS.
- **Seed dibber** — if herbs are in the run.
- **Secateurs / magic secateurs** — optional "recommended" (herb yield); magic secateurs can be wielded.
- **Compost** — per patch per the type's compost choice; bottomless bucket: check its charges (from the "Check" chat message — core has no tracker) and type; ultracompost buckets = 1 slot each.
- **Plant cure** — optional N doses (leprechaun-stored counts via `FARMING_TOOLS_PLANTCURE`).
- **Stamina potions** — optional N doses.
- **Travel items** — jewellery, capes, talisman, basalts, teleport crystal, etc., for the chosen methods; check charges where readable.
- Tools and compost stored at the **tool leprechaun** count as "have", with the note "withdraw from the leprechaun at your first stop" (tools must be in the inventory to use; confirm the first stop has a leprechaun via `Location.hasToolLeprechaun`).
- Leprechaun counts use `FARMING_TOOLS_*` varbits; large counts split into base + `EXTRA*` varbits — **combination formula UNVERIFIED**, confirm with the varbit inspector.

### 11.5 Inventory slot budget
- Compute the starting slot count of everything requested; warn if it's over 28.
- Simulate the route: saplings/seeds/payments are used up, harvest is added (herbs ≈ 6–10 per patch, fruit 6 per tree; logs and roots when a tree is chopped instead of paid to clear).
- Before each **fruit tree** stop, if free slots < fruit expected: suggest (in order of time cost) noting produce at the tool leprechaun, a bank stop already on the route, or a quick-to-bank teleport from 8.4.

---

## 12. Route planner

### 12.1 Modes
- **Autopilot** — minimise total estimated time (12.2).
- **Meta** — wiki order (8.5) filtered to due/enabled stops, plus start/end rules.
- **Off** — player's saved custom order.
- In every mode the Run tab list can be **dragged to reorder**; doing so saves a custom order and switches this run to Off.

### 12.2 Autopilot algorithm
- **Stops** = locations with ≥1 due, enabled patch (multi-patch locations are one stop; Catherby's two patches are one stop with an internal walk).
- **Leg cost(A → B)** = min over B's allowed methods of (cast/animation time + estWalkSeconds), plus **link edges** that depend on where you are (e.g. Gnome Stronghold → Tree Gnome Village by spirit tree; Farming Guild spirit tree to other spirit trees; walking between Falador park and Falador south farm).
- **Constraints:** start = configured start (default Farming Guild); end penalty = time from the last stop to the nearest bank (so it finishes near a bank); inventory feasibility from 11.5 (insert a bank stop if no ordering fits).
- **Solver:** stops are few (≤ ~20). Use exact dynamic programming (Held–Karp) up to 14 stops, else nearest-neighbour + 2-opt. Run off the client thread; it's pure data.
- **Auto teleport choice:** for each location, "Auto" picks the cheapest owned + unlocked method given leg context.

### 12.3 Run energy (live)
**Dropped (Sean, 2026-10-08):** not wanted for v1; the reminder and its two Route settings were removed. Kept here for reference.

At each step transition: if run energy < **30%** (setting) and the next leg's walk ≥ **15 tiles** (setting):
1. If stamina potion in inventory → step "Drink a stamina dose".
2. Else insert a restore step: POH pool (only if pool tier ≥ revitalisation) or Ferox Enclave (ring of dueling / POH jewellery box) — whichever adds less time; Explorer's ring charge if available.

### 12.4 Self-learning timings
Record each leg from the moment the player leaves the previous stop (teleport animation / region change) to entering the patch's area. Keep a rolling average per (location, method), ignore outliers (> 3× estimate), and blend with the estimate until at least 3 samples exist. Store per RS profile in config.

---

## 13. User interface

### 13.1 Sidebar — Run tab
- Stages: **Off** (default; due summary, run-type status and patch timers; nothing shown in game) → **Build run** → **Building** (bank tab button, checklist, "get N items" under the player) → **Armed** automatically once every required item is carried or at the leprechaun (plan frozen, first step shown) → **Running** when the player teleports or clicks a route patch or its gardener (or presses **Start now**); the timer starts here. Finishing or **Stop** returns to Off. **Cancel** leaves Building/Armed. Logging out drops Building/Armed.
- Preset picker + **Build run** / **Start now** / **Cancel** / **Skip step** / **Stop** buttons.
- **Due summary**: "This run: Trees (7), Herbs (9). Fruit trees ready in 5h 12m [include anyway]".
- **Route list** (drag to reorder): each stop shows the travel method icon, patches there with state icons, and estimated time; current step highlighted.
- **Supply checklist** (Quest Helper style): green ✔ / red ✘ "have 3 / need 5", grouped Travel · Runes · Seeds & saplings · Payments · Tools · Optional; hover shows where items are (bank / inventory / leprechaun / group storage / vault).
- **Totals footer**: coins, runes, estimated run time, starting inventory slots.

### 13.2 Sidebar — Farm, Travel and Account tabs (original Setup tab list; see docs/plans/sidebar-ux.md for where each item lives now)
Collapsible sections:
1. **Run types** — enable tree / fruit tree / herb; due threshold.
2. **Patches** — per type, list with checkbox, lock icon + requirement tooltip.
3. **Crops** — one seed/sapling per run type, or 1st/2nd/3rd choices; herb multi-select for disease-free patches (see section 2).
4. **Protection** — per type: Pay / Compost only; per-patch override; pay with notes; pay to clear; compost type; plant cure / Cure Plant / Resurrect Crops toggles; equipment boosts toggle.
5. **Travel** — per location dropdown (Auto + owned/unlocked methods, locked ones greyed); "use runes instead of tabs" global + per location.
6. **My POH** — portal location, nexus destinations, jewellery box tier, pool tier, fairy ring, spirit tree; "last detected on <date>" + Edit.
7. **Route** — mode (Autopilot / Meta / Off), start location, end near bank, energy threshold and tile distance, stamina doses.
8. **Storage sources** — group storage (off), seed vault (off).

### 13.3 Generated bank tab (Quest Helper pattern)
- Add a button to the bank title bar (`InterfaceID.Bankmain.UNIVERSE` on `WidgetLoaded`), styled like Quest Helper's (25×25 small square button + plugin icon).
- Clicking toggles a "Farm run" view: hides normal items and lays out the needed items in sections (Travel, Runes, Seeds & saplings, Payments, Tools, Optional) with "/ N needed" text and ✔/✘ icons; missing items shown faded as placeholders.
- Implementation reference: Quest Helper `QuestBankTabInterface` + `QuestBankTab` (`getSearchingTagTab` callback, `BANKMAIN_SEARCHING` / `BANKMAIN_FINISHBUILDING` script hooks, reuse item widgets, fix menu `param0` so withdraw works, update scrollbar). Close the view when a real tab, "View all", tag tab, potion store or search is clicked.
- Simpler fallback if the above proves fragile: core Bank Tags API (`TagManager.registerTag` + `BankTagsService.openBankTag`, `@PluginDependency(BankTagsPlugin.class)`) — loses section headers/quantity text.
- Also highlight needed items in the normal bank view (outline) and show ✔/✘ next to the checklist.
- Sean has a screenshot of Quest Helper's needed-items tab to use as the visual reference.

### 13.4 In-run step guidance (Quest Helper style)
Step engine with steps: TRAVEL → (WALK) → per patch: CHECK_HEALTH → HARVEST (herbs, fruit) → CLEAR (pay 200, or chop + dig stump) → RAKE (if weeds) → COMPOST → PLANT → PAY → next. Each step knows its **completion signal** (region entered, patch varbit changed, payment chat message, item count changed).
- **Highlights:** teleport item in inventory/equipment, spell in spellbook, nexus/jewellery box option, fairy ring code reminder, the patch object, the gardener NPC, and the inventory item to use next.
- **Arrows:** in-world hint arrow / tile marker to the patch, minimap arrow, world map point while travelling.
- **Info box / overlay panel**: current step text, e.g. "Pay Prissy Scilla: 25 coconuts (noted ok)".
- Auto-advance on completion signal; manual "skip step" button in sidebar.
- Colours configurable (left-click / use-item / NPC) with transparency.

### 13.5 Presets
Save, rename, duplicate, delete named `RunConfig`s (e.g. "Full tree + fruit", "Quick herbs"). Stored as JSON (injected `Gson`) in config. One-click switch in the Run tab.

---

## 14. Persistence

- Config group `farmrunautopilot`; RuneLite `@ConfigItem`s only for simple global settings (colours, thresholds). Complex state (RunConfig, presets, POH setup, patch states, timings, cached bank/group storage/vault) as JSON strings via `ConfigManager.setRSProfileConfiguration` — per account.
- Cache the bank on `ItemContainerChanged` (bank `InventoryID.BANK`); group storage `INV_GROUP_TEMP` (659) only when enabled and only after `VarbitID.GIM_SHARED_BANK_HASEDITED` (4602) is 0; seed vault `SEED_VAULT` (626) when enabled.
- No file I/O needed in v1. If added later, use `Filepath` per `AGENTS.md`.

---

## 15. Milestones (small, testable chunks)

Each milestone ends with: `./gradlew run`, a short "what to test" list for Sean, and waiting for his OK.

| # | Milestone | Sean tests |
|---|---|---|
| M0 | Rename template (package `com.farmrunautopilot`, `FarmRunAutopilotPlugin`, config group, `build.gradle`, `settings.gradle`, `runelite-plugin.properties`, README, BSD-2 licence, THIRD_PARTY_NOTICES). Empty sidebar with Run/Setup tabs. | Plugin appears, sidebar opens, no errors. |
| M1 | Data layer: patches, crops, travel methods, POH options, meta orders. Unit tests for data integrity and patch-state decoding. | Nothing in-game; review the data tables. |
| M2 | Patch state tracker + debug list in Setup tab. | Visit 3–4 patches; states and timers look right. |
| M3 | Access detection + Setup tab (patches, crops, protection, travel dropdowns, POH, storage toggles). | Locked items match what he has; overrides stick after relog. |
| M4 | Supply calculator + Run tab checklist (inv/equip/bank/leprechaun; optional group storage/vault). | Numbers match a hand count for one tree run and one herb run. |
| M5 | Generated bank tab. | Button appears, sections + quantities correct, withdrawing works, closes properly. |
| M6 | Route planner (Autopilot/Meta/Off) + drag reorder + due detection. | Route order sensible; fruit trees skipped when not due. |
| M7 | Step engine + highlights + arrows. | Full run guided start to finish. |
| M8 | Run-energy restore + self-learning timings + inventory slot budget/bank stop. | Restore suggested at the right time; fruit stop warns when full. |
| M9 | Presets, polish, Hub checklist, README screenshots, submit PR to `runelite/plugin-hub` (with "Generated-by: Claude Code" footer). | Final full run. |

---

## 16. Open questions / to verify

1. Seed vault toggle — default off (Sean said "maybe seed vault too"); confirm default.
2. Fruit regrow time 40 vs 45 minutes; banana/orange/curry payment item form.
3. Disease-free status for Hosidius, Harmony, Civitas; Kastori gardener name.
4. Leprechaun base+EXTRA varbit combination formula; bottomless bucket charge tracking.
5. Varbits for Ardougne cloak / Explorer's ring daily uses, Pendant of Ates statues, Quetzal landing sites, fairy ring unlock.
6. Whether Resurrect Crops works on tree patches.
7. Walk-tile estimates for every method (self-learning will correct them).
8. Ardougne cloak 1 has no farm teleport; balloon transport unlocks; necklace of passage destinations.

---

## 17. References

- OSRS Wiki: Tree patch, Fruit tree patch, Herb patch, Farming (payments, noted items, 200-coin clearing), Farming runs (meta orders), Farming Guild, Auto-weed, Tool Leprechaun, Compost, pools, Ferox Enclave.
- RuneLite core: `plugins/timetracking/farming` (FarmingWorld, PatchImplementation, Produce, FarmingTracker, CompostTracker, PaymentTracker), `plugins/runepouch`, `plugins/banktags`.
- Quest Helper (`Zoinkwiz/quest-helper`): `bank/banktab` (QuestBankTabInterface, QuestBankTab, QuestHelperBankTagService), `managers/QuestBankManager`.
- Farming-Helper (`Speaax/Farming-Helper`, BSD-2): reference only for location coordinates and teleport lists.
