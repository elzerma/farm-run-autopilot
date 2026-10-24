# Plan: the crop types the plugin doesn't support yet (three sprints)

Written 2026-10-17, planning only (nothing built). **Status:** Sprint 1a (allotments, flowers, hops) built 2026-10-17, waiting
for elzerma's in-game checks; Entrana hops left out (question 6). **1b and 1c wait for the travel rework**
(`travel-rework.md`, elzerma 2026-10-17): its atlas will already hold travel to every patch, so those sprints switch their
patches on instead of researching travel per stop. They start after T4 and T5; the rework's T9 (help list, README,
release notes) comes after 1c (elzerma, 2026-10-23). The plugin today runs herbs, trees and fruit trees as one
combined route with bank stops (live on the Plugin Hub at `62f3070`). SPEC section 2 "Later (not v1)" lists the
rest; elzerma asked for them to be planned as three sprints:

- **1a** allotments, flowers, hops (they share the herb patch areas, and flowers protect allotments);
- **1b** bushes, cactus, mushroom, belladonna, seaweed;
- **1c** special trees: hardwood, calquat, celastrus, redwood, spirit tree, crystal tree (added 2026-10-22, as
  promised on GitHub #30).

Section 1 is what every sprint needs from the code. Sections 2-4 are the sprints. Section 5 is the open questions
for elzerma; nothing starts until they are answered.

## Sources

Every game fact below is from one of these (checked 2026-10-17). Short names are used in the tables.

- **[RL]** RuneLite core time tracking, master branch: `timetracking/farming/FarmingWorld.java` (regions, varbits,
  gardener NPCs, bounds), `Produce.java` (growth: `tick minutes x (stages - 1)`, regrowth, harvest lives),
  `PatchImplementation.java` (varbit value ranges) and gameval `ItemID.java` (item names). This is where the
  plugin's v1 tables came from, so new tables copied from it stay consistent with them.
- **[Wiki]** OSRS wiki pages: Allotment patch, Flower patch, Hops patch, Bush patch (each with /Patches and
  /Seeds), Special patches (/Patches, /Seeds; the cactus, mushroom, belladonna, seaweed, hardwood, calquat,
  celastrus and redwood pages all redirect there), Spirit Tree (Farming), Disease (Farming), Farming, Farming
  Guild, Scarecrow, Underwater, Mernia, Seed dibber, Sapling, and the seed, sapling and location pages named
  where used. The crystal tree's lines (checked 2026-10-22) are from RuneLite's `FarmingWorld` (Prifddinas),
  `PatchImplementation.CRYSTAL_TREE` and `Produce.CRYSTAL_TREE`, and the wiki's Crystal tree, Crystal acorn and
  Crystal tree patch pages.

The full research notes, with a source on every line, are kept outside the repo in the session notes
(`crop-types-research.md`). **UNVERIFIED** below means no source settled it; those either become a guided test on
the "I NEED YOUR HELP!" list or an open question.

**New content since SPEC was written.** RuneLite already tracks patches and crops that SPEC doesn't name:
Aldarin hops, Civitas illa Fortis allotments, Kastori flower and calquat, Auburnvale belladonna, Great Conch
calquat (The Summer Shore), Anglers' Retreat hardwood (51 Sailing), the hops-patch fibres flax, hemp and cotton,
and the hardwoods camphor, ironwood and rosewood. This plan includes them where RuneLite tracks them, since
leaving them out would mean "locked" patches that a player can actually use.

Out of scope for all three sprints (say so in the README): anima, Hespori, grapes, coral, compost
bins, the Keldagrim quest hops patch and the Anglers' Retreat flax patches (RuneLite doesn't track those).

---

## 1. Groundwork every sprint needs

SPEC 2 asked for a data model where new patch types are added "without refactoring". Most of it holds:
`PatchType`, `Patch`, `Crop`, `PatchStateDecoder` tables, `MetaOrder`, `PatchPoints` and `RunConfig`'s per-type
maps are all keyed by `PatchType`. The places that assume "herb, tree or fruit tree" are below; each sprint
touches the ones it needs, and **1a does the shared changes once**.

| Assumption today | Where | Change |
|---|---|---|
| One seed or sapling per patch | `SupplyCalculator` (`n = places.size()`), `CropAllocator` | `Crop.seedsPerPatch` (allotment 3, barley/hops 4, fibres 3, everything else 1). Stock checks and backups count seeds, not patches. |
| "Seeds stack, saplings don't" decided by `type == HERB` | `SupplyCalculator` | `Crop.stackable` (true for seeds and spores, false for saplings). |
| One payment item per crop | `Crop.paymentItemId/Quantity` | A list of `Payment(item, qty)`: the spirit tree takes 5 monkey nuts + 1 monkey bar + 1 ground tooth [Wiki: Spirit sapling]. |
| Gardener per patch; protection = pay or compost | `Patch`, `Protection` | Allotment gardeners protect each allotment separately (two payments per set) [Wiki: Disease]. New `Protection.FLOWER` for allotments (1a). Some patch types have no gardener at all (flowers, mushroom, belladonna, Harmony Island allotment). |
| Harvest = "pick herbs" or "pick fruit, clear tree" | `StepAdvisor`, `PatchGuide.expectedHarvest` | Per-type harvest behaviour: `HarvestKind` = PICK_ALL (allotment, hops, flower, herb, mushroom, belladonna, seaweed), PICK_REGROWS (bush, cactus, fruit tree, calquat), CHOP (tree, hardwood, celastrus, redwood, crystal tree), NONE (spirit tree: check health only). |
| Clearing = axe or 200 coins | `SupplyCalculator`, `StepAdvisor` | Per-type clearing: spade (spirit tree, bushes, dead crops), axe or 200 coins (trees), **2,000 coins only** (redwood: the player can't clear it [Wiki: Redwood sapling]), nothing (crystal tree: chopping removes the tree and stump [Wiki: Crystal tree]). |
| Message parsing "fruit tree / herb / tree patch" | `PatchGuide.patchNamed` | Word table per type ("allotment", "flower", "hops", "bush", "cactus", ...). Two allotments at one stop can't be told apart by the message: fall back to the patch being worked on (as today for "two of that type"). |
| Planting tool: dibber for herbs, spade otherwise | `PatchGuide.plantingToolReminder` | `Crop.stackable` decides: seeds need a dibber (unless Barbarian planting), saplings a spade. |
| Run-type toggles in one row | `RunPanel.runTypeToggles` (`GridLayout(1, n)`) | Breaks past 3-4 types. See "UI" below. |
| `enabledTypes` defaults to every type | `RunConfig` | Existing players keep their saved set (Gson leaves new types out), but **new players would get every new type ticked**. Default new types to off; question Q4. |
| Meta order has three lists | `MetaOrder.forType` | One list per new type (wiki run orders), merged by `combined()` as today. |

**UI, said once (BRANDING 1.3).** With up to 16 run types, the Run tab's top row becomes **families**:
Herbs, Trees, Fruit trees (as today), then one tick box per new family: Allotments & flowers, Hops, Bushes,
Specials (cactus, mushroom, belladonna, seaweed), Special trees. Three per row, the same tick box style. Which
patch types inside a family run is set once, on the Farm tab (its Patches section already has one collapsible
group per type). The Farm tab's Crops, Protection and Compost sections get one subheader per type, as herbs and
trees have now, so nothing appears twice. The run-type status lines ("Allotments: due in 22 min") stay per type.

**Travel.** Each new stop needs a `Location`, `PatchPoints` entry, travel methods in `TravelMethod`, and walks in
`TravelTimes` (the one travel graph, REBUILD rule 2). Stops that already exist get the new patches for free:
Falador farm, Catherby, Ardougne farm, Port Phasmatys, Hosidius, Farming Guild, Civitas, Harmony Island,
Brimhaven, Kastori, Nemus Retreat (RuneLite calls that region Auburnvale; whether the belladonna patch is close
enough to count as the same stop is **UNVERIFIED**). Patch coordinates for new patches come from Farming-Helper
(as v1's did) and are corrected from the game log, as in October.

**Tests that apply to every sprint.**
- `PatchDataTest`: every new patch's point is inside its region; varbit and gardener match RuneLite.
- `CropDataTest`: growth minutes match the RuneLite `Produce` table; seeds per patch and payments as listed here.
- `PatchStateDecoderTest`: for each new table, one value per state copied from RuneLite's ranges (growing,
  harvestable, diseased, dead, check-health), and that unknown values decode to UNKNOWN.
- `MetaOrderTest`, `RunConfigTest` (saved config without the new types loads with them off).
- One `ScenarioTest` per sprint (below), using `ElzermaAccount`.

---

## 2. Sprint 1a: allotments, flowers, hops

### Patches [RL FarmingWorld, Wiki: Allotment patch/Patches, Hops patch/Patches]

**Allotment sets** (two allotments + one flower patch, next to the herb patch at most). Allotments are
`FARMING_TRANSMIT_A` and `_B`, flower `_C`, unless noted.

| Stop | Region (extra) | Gardener (allotments) | Access | Notes |
|---|---|---|---|---|
| Falador farm (existing) | 12083 | Elstan | none | same `y >= 3272` bounds as the herb |
| Catherby (existing) | 11062 (11061, 11318, 11317) | Dantaera | none | same upstairs bounds as the herb |
| Ardougne farm (existing) | 10548 | Kragen | none | |
| Port Phasmatys (existing) | 14391 (14390) | Lyra | Priest in Peril as for the herb (**UNVERIFIED**: the wiki lists none) | |
| Hosidius (existing) | 6967 (6711) | Marisi | none | Easy Kourend & Kebos diary makes them disease-free [Wiki: Disease] |
| Farming Guild (existing) | 4922 (guild regions) | Alan | 45 Farming | allotments `_C`/`_D`, flower `_H` |
| Civitas illa Fortis (existing) | 6192 (...) | Harminia | Children of the Sun | |
| Harmony Island (existing) | 15148 | **none** | The Great Brain Robbery started | one allotment only (`_A`), no flower patch, can't be protected |
| **Prifddinas** (new stop) | 13151 (+ 10 more) | Oswallt | Song of the Elves | flower `_C`; no herb patch |
| Kastori (existing) | 5423 (5167, 5424) | none | Children of the Sun | flower patch only, `_C` |

**Hops** (all `FARMING_TRANSMIT_A`, each a new stop): Lumbridge (12851, Vasquen), Seers' Village
(10551 + 10550, Rhonen), Yanille (10288, Selena), Entrana (11060 + 11316, Francis), Aldarin (5421 + 5 regions,
Ercos, Children of the Sun).

Entrana bans weapons and armour on the boat. Whether that still applies, and so whether magic secateurs, a
fairy ring staff or worn teleport jewellery must be banked first, is **UNVERIFIED**. Until it's settled, plan
Entrana as off by default and keep it last in its part of the route.

### Crops and seeds [RL Produce, Wiki: /Seeds pages]

- **Allotments, 3 seeds each:** potato 1, onion 5, cabbage 7, tomato 12 (40 min); sweetcorn 20, strawberry 31
  (60 min); snape grass 61 (70 min); watermelon 47 (80 min).
- **Flowers, 1 seed:** marigold 2, rosemary 11, nasturtium 24, woad 25, limpwurt 26, white lily 58 (all 20 min).
  Scarecrow (made at 23 Farming) also goes in the flower patch, for sweetcorn.
- **Hops, 4 seeds:** barley 3, hammerstone 4 (40 min), asgarnian 8 (50), yanillian 16 (60), krandorian 21 (70),
  wildblood 28 (80). **3 seeds:** jute 13 (50), flax 18 (60), hemp 37 (80), cotton 71 (100).

### Compost and protection

- **Gardener payments**, per allotment or hops patch, noted accepted [Wiki: Disease]: allotments take compost x2,
  a sack of potatoes, a sack of onions, 2 sacks of cabbages, 10 jute fibre, a basket of apples, 10 curry leaves or
  5 jangerberries (by crop); hops take compost x3, 1 marigold, a sack of onions, 6 barley malt, a basket of
  tomatoes, 6 grain, 3 sacks of cabbages, 1 nasturtium, 6 flax or 6 hemp. Flowers can't be protected
  [Wiki: Farming].
- **Flower protection** [Wiki: Flower patch, Disease]: a fully grown flower protects both allotments of its set
  for as long as it stays in the patch. Marigold covers potato, onion, tomato; rosemary covers cabbage;
  nasturtium covers watermelon; a scarecrow covers sweetcorn; white lily covers every allotment crop. Strawberry
  and snape grass are covered by white lily only. Woad and limpwurt protect nothing.
  - The plugin picks the flower from the allotment crop (white lily if the player has the seeds and chose it).
    Protection then costs one flower seed per set instead of two payments.
  - **Timing is UNVERIFIED**: the wiki only says protection starts "once the flower is fully grown". Crops can't
    be diseased in their first growth stage, and flowers take 20 min against the allotment's first 10-min
    tick, so planting both on the same visit most likely leaves only a small window. Safe rule for the plugin:
    plant the flower first at each stop, and say on hover that the first cycle isn't fully covered.
  - Harvesting the flower ends the protection, so the guide **doesn't pick a protecting flower** while its
    allotments are growing; it is replaced only when it's dead or the allotments are done (question Q1 decides
    whether flowers are grown for protection or for their own produce).
- **Compost** per type as today (`RunConfig.compost`), defaults: allotments and hops ultracompost, flowers none.
- **Watering** is optional and only lowers disease risk [Wiki: Disease]. Not added to the supply list; the patch
  decoder must treat the watered growing values (allotment 70-134, flower 72+, hops 132+ [RL]) as growing.

### Tools

Seed dibber (unless Barbarian planting), rake (unless Auto-weed), spade (dead crops, scarecrow), magic secateurs
as an optional boost (allotments and hops, not flowers). Watering can not required.

### Timing and the combined route

All of these grow in 20-100 min, about the herb cycle (80 min), so in practice they are due on every herb run.
Each type keeps its own due rule (`RunSelector`, due threshold per type). The allotment, flower and herb patches
are a few tiles apart, so a stop costs only the extra patch time; the route search doesn't change. Hops add
five new stops (Lumbridge, Seers', Yanille, Entrana, Aldarin), which do change the route.

`RunTimings` needs a patch-time estimate per type (allotment ~ herb + raking; hops the same). These start as
estimates and self-learn like the others.

### Supply list and bank tab

- New Seeds lines with seed counts (e.g. "Potato seed x 24, 8 allotments"); flower seeds on their own line with
  "Protects: ..." on hover.
- New Payments lines per allotment and hops crop (noted when "Pay with noted items" is on).
- **Inventory:** harvests are large (allotments and hops give at least 3 each, often 10+), so produce is noted
  at the tool leprechaun. `PatchGuide.spaceReminder` already says so when the inventory is full;
  `expectedHarvest` gets numbers for the new types. `BankPlanner` counts seed lines as one slot each.
- The bank tab and seed vault section list the new seeds the same way (the seed vault holds them).

### Guide steps

Per allotment set, in this order: flower patch (rake, compost, plant the protecting flower or scarecrow), then
each allotment (rake, compost, plant 3 seeds, pay the gardener if not flower-protected), then the herb.
`StepAdvisor` grows: "Plant the marigold first: it protects both allotments", "Pay Elstan for the north-west
allotment: 2 x compost", "Pick the potatoes (note them at the leprechaun)".

Gardeners at allotment sets ask which allotment to protect: that dialog choice is **UNVERIFIED** in wording.

### UI

Farm tab: "Allotment patches", "Flower patches", "Hops patches" groups in Patches; Crops gets Allotment, Flower
(only if flowers aren't set to "protection only") and Hops subheaders; Protection gets **Allotments: Flower /
Pay gardener / Compost only / Per patch** and Hops: Pay gardener / Compost only. Run tab: one "Allotments &
flowers" box and one "Hops" box.

### Tests

- Unit: `CropAllocator` with 3 seeds per patch (24 potato seeds for 8 allotments, backups when only 20 held);
  flower chosen per allotment crop (cabbage -> rosemary, strawberry -> white lily or pay, sweetcorn ->
  scarecrow); `SupplyCalculator` payments per allotment patch and none when flower-protected; Harmony Island never
  asks for payment; decoder treats watered values as growing.
- Scenario: herbs + allotments + flowers due together → one combined route visiting each herb stop once, with
  flower → allotments → herb at each; with hops ticked, Lumbridge and Seers' join the route without a second trip.

### UNVERIFIED (help-list guided tests)

1. **ALLOTMENT_FLOWER_PROTECTION** (Farm tab): plant a marigold and potatoes on one visit, look at the gardener's
   "protected" message or check the patch with Look-at; capture the chat message text.
2. **ALLOTMENT_PAYMENT_DIALOG** (Run tab): pay a gardener for one allotment; capture the dialog option text for
   "north/south" so the guide can name the right one.
3. **NEW_PATCH_POINTS** (Run tab): walk past each new patch; the log records the object position (as October's
   patch-point fix did).
4. **ENTRANA_EQUIPMENT** (Farm tab): only if Entrana stays in: board the boat with magic secateurs in the
   inventory; capture whether it's refused.
5. Morytania allotment access: covered by the Port Phasmatys herb's requirement unless a test shows otherwise.

### Risks and size

- Allotments double the patch count of a herb run; runs get long. Mitigation: off by default, separate toggles.
- The flower-protection timing is the one gameplay assumption; it is visible on hover and tested.
- Two gardeners' payments per set, plus flower seeds: the most supply lines of any sprint.
- **Size: large** (biggest of the three: shared groundwork + 3 types + 5 new hops stops + flower logic).

---

## 3. Sprint 1b: bushes, cactus, mushroom, belladonna, seaweed

### Patches [RL FarmingWorld, Wiki: Bush patch/Patches, Special patches/Patches]

| Type | Stop | Region (extra), varbit | Gardener | Access |
|---|---|---|---|---|
| Bush | Champions' Guild (new) | 12596, `_A` | Dreven | none |
| Bush | Rimmington (new) | 11570 (11826), `_A` | Taria | none |
| Bush | Ardougne south / monastery (new) | 10290 (10546), `_A` | Torrell | none |
| Bush | Etceteria (new) | 10300, `_A` | Rhazien | The Fremennik Trials; **UNVERIFIED** whether Throne of Miscellania is also needed |
| Bush | Farming Guild (existing) | 4922, `_B` | Alan | 45 Farming |
| Cactus | Al Kharid (new) | 13106 (13362, 13105), `_A` | Ayesha | none |
| Cactus | Farming Guild (existing) | 4922, `_F` | Alan | 45 Farming |
| Mushroom | Canifis (new) | 13622 (13878), `_A` | none | Priest in Peril |
| Belladonna | Draynor Manor (new) | 12340, `_A` | none | none |
| Belladonna | Auburnvale / Nemus Retreat (existing?) | 5427 (5428, 5684), `_B` | none | Children of the Sun |
| Seaweed | Fossil Island underwater (new) | 15008, north `_A`, south `_B` | Mernia | Bone Voyage |

### Crops and seeds [RL Produce, Wiki: Bush patch/Seeds, Special patches/Seeds]

- **Bushes, 1 seed:** redberry 10 (100 min), cadavaberry 22 (120), dwellberry 36 (140), jangerberry 48 (160),
  whiteberry 59 (160), poison ivy 70 (160). Berries regrow every 20 min, up to 4 [RL: 5 harvest stages]. A health
  check comes before the first pick; the bush stays planted (dig it up with a spade to change crop).
- **Cactus:** cactus 55 (560 min, regrows 20 min), potato cactus 64 (70 min, regrows 5 min). 1 seed
  (**UNVERIFIED**, not stated on the wiki pages).
- **Mushroom:** bittercap spore 53 (240 min), 6 mushrooms, doesn't regrow. 1 spore (**UNVERIFIED**).
- **Belladonna:** 63 (320 min), 1 seed, at least 6 nightshade.
- **Seaweed:** giant seaweed spore 23 (40 min), 3 lives + 1 per compost tier. 1 spore (**UNVERIFIED**).

### Compost and protection [Wiki: Bush patch/Seeds, Special patches/Seeds, Mernia, Disease]

- Bush payments: 4 sacks of cabbages, 3 baskets of tomatoes, 3 baskets of strawberries, 6 watermelons, 8
  bittercap mushrooms; poison ivy is immune and takes no payment.
- Cactus: 6 cadava berries; potato cactus: 8 snape grass.
- Seaweed: Mernia, 200 numulites per patch.
- Mushroom and belladonna can't be protected: compost only (default ultracompost) and plant cure as a backup.

### Tools

Spade (dig up bushes and dead crops), secateurs (prune diseased bushes and cacti), dibber for seeds (spores:
**UNVERIFIED** whether a dibber is needed), **gloves for belladonna** (worn; without them picking hurts and
fails [Wiki: Belladonna]). Fishbowl helmet and diving apparatus are **not** required underwater; without them
oxygen runs down and drowning costs mermaid's tears [Wiki: Underwater]. Suggest them on hover, don't require them.

### Timing and the combined route

These cycles vary: seaweed 40 min, potato cactus 70 min (both fit a herb run), bushes 100-160 min plus regrowth,
mushroom 4 h, belladonna 5 h 20, cactus 9 h 20. Each type keeps its own due rule, so on most runs only some
join. Picking regrowing crops (bushes, cacti) is "pick what's there, leave it": the patch counts as done after
picking and isn't replanted unless dead or the player changed crop. `PatchPredictor` needs a regrowth model for
bushes and cacti (RuneLite has it [RL Produce]).

The new stops are spread out (Varrock, Rimmington, Ardougne south, Etceteria, Al Kharid, Canifis, Draynor,
Fossil Island). The route search handles them like any stop. Fossil Island underwater is the awkward one: a
rowboat and a dive, with a bank on the island first; plan it as its own leg type ("Dive") in the travel graph.

### Supply list and bank tab

Seeds and payments as 1a. Gloves appear as a worn item (wear blue) when a belladonna patch is in the run. Numulites
are a payment line. Mushrooms and nightshade are small harvests; berries and cactus spines can fill the
inventory (note at the leprechaun; not all of these spots have one: **UNVERIFIED** per patch).

### Guide steps

New advice: "Check the bush's health", "Pick the redberries (4 left)", "Dig up the bush to change crop", "Wear
gloves to pick the belladonna", "Dive" at the Fossil Island anchor rope, "Pay Mernia: 200 numulites".

### UI

Run tab: "Bushes" and "Specials" boxes. Farm tab: one group per type in Patches, Crops and Protection. Belladonna
and mushroom show "Can't be protected" as a greyed line under Protection, said once there.

### Tests

- Unit: regrowth prediction for a bush picked to 1 berry; poison ivy needs no payment; gloves line only when
  belladonna is in the run; decoder bush "grown, health not checked" values 250-255 [RL] decode to CHECK_HEALTH.
- Scenario: herbs + bushes due → Champions' Guild and Rimmington slot into the herb route near Falador;
  belladonna + herbs → Draynor joins without a bank stop.

### UNVERIFIED (help-list guided tests)

1. **SEAWEED_DIVE** (Run tab): dive without the fishbowl helmet, plant a spore, pay Mernia; capture the varbit
   values, the dibber need and the payment message.
2. **ETCETERIA_ACCESS** (Account > Unlocks): only for players with The Fremennik Trials and not Throne of
   Miscellania; capture whether the boat takes them (`cannotTest` otherwise).
3. **SPORE_COUNTS** (Farm tab): plant a cactus seed, mushroom spore and seaweed spore; capture how many left the
   inventory each time.
4. **BELLADONNA_STOP** (Run tab): stand at the Auburnvale belladonna and the Nemus Retreat tree; capture positions
   so we know whether they are one stop.
5. Patch points for each new patch, as 1a.

### Risks and size

- Many scattered new stops, each needing travel data (fairy rings, teleports, walks): this is where the time goes.
- Fossil Island underwater travel is new to the travel graph.
- **Size: medium-large** (five small types, but eight new stops).

---

## 4. Sprint 1c: special trees (hardwood, calquat, celastrus, redwood, spirit tree, crystal tree)

### Patches [RL FarmingWorld, Wiki: Special patches/Patches, Spirit Tree (Farming)/Patches, Farming Guild, Crystal tree]

| Type | Stop | Region, varbit | Gardener | Access |
|---|---|---|---|---|
| Hardwood x3 | Fossil Island, Mushroom Forest (new) | 14651 (+8), east `_A`, middle `_B`, west `_C` | squirrels | Bone Voyage; bounds exceptions on two staircases [RL] |
| Hardwood | Locus Oasis, Avium Savannah (new) | 6702 (6446), `_A` | Marcellus | The Ribbiting Tale of a Lily Pad Labour Dispute (not just Children of the Sun) [Wiki: Locus Oasis] |
| Hardwood | Anglers' Retreat (new) | 9770, `_A` | Argo | 51 Sailing to moor |
| Calquat | Tai Bwo Wannai (new) | 11056, `_A` | Imiago | none |
| Calquat | Kastori (existing) | 5423, `_A` | Tziuhtla | Children of the Sun |
| Calquat | Great Conch, The Summer Shore (new) | 12581 (+13), `_C` | Guppa | part of Troubled Tortugans (**UNVERIFIED** which quest step) |
| Celastrus | Farming Guild (existing) | 4922, `_L` | Taylor | 85 Farming |
| Redwood | Farming Guild (existing) | 4922, `_I` | Alexandra | 85 to enter, 90 to plant |
| Spirit tree | Port Sarim (new), Etceteria (1b), Brimhaven (existing), Hosidius (existing), Farming Guild (existing) | 12082 `_A` (y < 3272), 10300 `_B`, 11058 `_B`, 6967 `_F`, 4922 `_A` | five gardeners | Farming Guild 85; Etceteria as 1b |
| Crystal tree | Prifddinas, north-east of the city (existing from 1a) | 13151 (+ 10 more, as the 1a patches), `_E` | **none** (RuneLite gives the patch no NPC; the Crystal tree page names none; Pennant, nearby, only trades crystal seeds for acorns [Wiki: Crystal acorn]) | Song of the Elves to enter Prifddinas (the patch's own pages list only 74 Farming) |

The crystal tree is the only one of its kind, at the Prifddinas stop 1a already added (allotments `_A`/`_B`,
flower `_C`, compost bin `_D`). RuneLite files it under its fruit tree tab; the plugin puts it in the Special
trees family with the others here.

### Crops and saplings [RL Produce, Wiki: sapling pages]

| Tree | Level | Growth | Payment (noted accepted) |
|---|---|---|---|
| Teak | 35 | 4,480 min (3 d 2 h 40) | 15 limpwurt roots |
| Mahogany | 55 | 5,120 min | 25 yanillian hops |
| Camphor | 66 | 5,120 min | 10 white berries |
| Ironwood | 80 | 5,120 min | 10 curry leaves |
| Rosewood | 92 | 5,760 min (RuneLite and the seed page; one wiki table says 6,400: **conflict**, use RuneLite's) | 8 dragonfruit |
| Calquat | 72 | 1,280 min (21 h 20) | 8 poison ivy berries |
| Celastrus | 85 | 800 min (13 h 20) | 8 potato cacti |
| Redwood | 90 | 6,400 min (4 d 10 h 40) | 6 dragonfruit |
| Spirit tree | 83 | 3,840 min (2 d 16 h) | 5 monkey nuts + 1 monkey bar + 1 ground tooth |
| Crystal tree | 74 | 480 min (8 h: RuneLite 80 min x 6, the wiki's "6 x 80 mins") | none: can't get diseased [Wiki: Crystal acorn] |

All are planted as **saplings** (one per patch, don't stack, spade to plant). The crystal sapling is grown from a
crystal acorn (plant pot, trowel, water, 74 Farming) [Wiki: Crystal acorn]; as for the others, the plugin only
lists the sapling (Q5). The plugin lists saplings it
needs; it doesn't guide growing them from seeds (plant pot, trowel, watering can) unless elzerma wants that
(Q5).

**Spirit tree limit** [Wiki: Spirit Tree (Farming)/Patches]: 1 tree at 83 Farming, 2 at 88, 3 at 93, 4 at 96, any
number at 99 (boosts count; the POH spirit tree doesn't). The plugin counts spirit trees already growing and only
plants up to the limit; the Farm tab shows "2 of 3 allowed" on the spirit tree group.

### Compost, protection and clearing

Payments above, one per patch, pay-with-notes as today. Clearing differs per tree: hardwood chopped (axe, logs)
then stump dug or 200 coins (**UNVERIFIED** that the 200-coin service applies to hardwood gardeners); celastrus
checked then chopped for bark, doesn't regrow; redwood **only** cleared by paying Alexandra 2,000 coins; spirit
trees dug up with a spade, can't be chopped; calquat picked (6 fruit, RuneLite has no regrowth timer though the
wiki says 1 per 20 min: use "pick what's there" like fruit trees).

**Crystal tree** [Wiki: Crystal tree, Crystal acorn; RL PatchImplementation]: no payment and no protection (it can't
get diseased, so RuneLite's table has no diseased or dead values). Compost still matters, because the harvest
depends on it, not on Farming level: crystal shards 16-20 with none, 20-24 compost, 24-28 supercompost, 28-32
ultracompost. The plugin's compost choice for the crystal tree should default to the best compost the player
uses elsewhere. Check health when grown (13,240 xp; planting gives 126), then Chop-down with an axe; magic
secateurs and the Farming cape do nothing. Chopping gives the shards (and sometimes bird nests) and removes the
tree and stump together, so the patch is empty at once: no clearing step, no regrowth.

### Tools

Spade, secateurs (prune diseased), axe (hardwood, celastrus, redwood, if not paying to clear; always for the
crystal tree, which has no one to pay), 2,000 coins for a redwood clear, magic secateurs not useful.

### Timing and the combined route

These cycles are 8 hours (crystal tree) to 4.5 days, so they are due rarely. The per-type due rule already skips them until
ready, so they can join the main combined route when due (the default answer to Q2). A spirit tree run that is
only "check health" is short; Fossil Island (three hardwoods + two seaweeds from 1b) is one long detour, worth
taking only when several of its patches are due. The route search decides that by cost, not by special case.

A grown spirit tree can't be used for travel until its health is checked [Wiki: Spirit sapling]: once checked,
`AccessSnapshot` should count it as a spirit tree destination for the travel graph (the Etceteria, Port Sarim,
Brimhaven, Hosidius and Farming Guild spirit trees), which can shorten later runs.

The crystal tree needs no new travel: Prifddinas is already in the travel atlas and a 1a stop. The wiki's ways
there are a teleport crystal, the Prifddinas house portal (Teleport to House or a redirected house tab) and the
city's north-west spirit tree, the route the patch page suggests [Wiki: Crystal tree patch]. The atlas
picks between them by cost, as for every stop. When the allotments,
flower and crystal tree are due together they are one Prifddinas visit.

### Supply list and bank tab

Sapling lines (one slot each), payment lines, axe/coins only when a tree needs clearing. Special trees often
need the Farming Guild's advanced tier: show it as a requirement, said once in Patches.

### Guide steps

"Check the teak's health", "Chop the teak (or pay the squirrel to clear it, 200 coins)", "Pay Alexandra to clear
the redwood: 2,000 coins", "Dig up the spirit tree", "Plant the spirit sapling (2 of 3 allowed)", "Check the
crystal tree's health", "Chop down the crystal tree (axe)", "Plant the crystal sapling".

### UI

Run tab: one "Special trees" box. Farm tab: one Patches group per tree type, a sapling choice per type in Crops
(level-limited), protection/clearing per type in Protection.

### Tests

- Unit: spirit tree limit by level (83 → 1, 88 → 2, 99 → all); redwood clear always costs 2,000 coins and never
  an axe; calquat counts as picked-then-done; sapling slots don't stack; the crystal tree has no payment, no
  clearing step and needs an axe; its decoder reads 0-3 weeds, 8-13 growing, 14 check-health, 15 chop-down
  [RL PatchImplementation.CRYSTAL_TREE] and anything else as UNKNOWN.
- Scenario: herbs + one ready spirit tree → the spirit tree joins the herb route with no extra bank stop; three
  Fossil Island hardwoods due → one Fossil Island visit, not three; Prifddinas allotments + a ready crystal tree
  → one Prifddinas visit, axe on the supply list.

### UNVERIFIED (help-list guided tests)

1. **GREAT_CONCH_ACCESS** (Account > Unlocks): the quest step that opens the calquat patch.
2. **HARDWOOD_CLEAR_FEE** (Farm tab): ask a squirrel to clear a teak stump; capture the price.
3. **SPIRIT_TREE_CHECKED_TRAVEL** (Travel tab): after checking a grown spirit tree's health, travel from it;
   capture that the plugin then offers it as a destination.
4. Anglers' Retreat: `Skill.SAILING` exists in RuneLite's API (Sailing launched) but the plugin has never read
   it; **UNVERIFIED** that the level reads correctly. Guided test with `cannotTest` for players under 51.
5. Patch points for each new patch.
6. **CRYSTAL_TREE_STATES** (Farm tab): `FARMING_TRANSMIT_E` in region 13151 and its values (8-13 growing, 14
   check-health, 15 chop-down) are RuneLite's and have never been read by this plugin; capture the value after
   planting, at check-health and after chopping (when the patch should read as weeds or empty, not
   UNKNOWN; **UNVERIFIED** that it goes back to 0-3). Also capture the shard count against the compost used.
   `cannotTest` without 74 Farming, Song of the Elves or a crystal sapling.
7. Crystal tree patch point: the wiki says only "north-east Prifddinas"; take it from Farming-Helper if it has
   one, else from the game log, as in October.

### Risks and size

- Multi-day cycles mean in-game testing takes days; guided tests carry most of the checking.
- Spirit tree travel feeding back into the travel graph is new.
- New-content gates (Sailing, Troubled Tortugans, the frog quest) are recent and need `Requirement` types.
- **Size: medium** (fewer stops, but more per-type rules).

---

## 5. Open questions for elzerma

1. **Flower protection for allotments.** Plant the matching flower by default (one seed per set instead of two
   payments), pay the gardener by default, or let the player choose per set (default: flower)?
2. **Special trees: join the main run, or their own run?** Recommended: join the main combined route when due,
   like trees and fruit trees, with their own tick box (SPEC's "one combined route" rule).
3. **Sprint order.** Recommended 1a → 1b → 1c (1a builds the shared groundwork and fits existing stops).
4. **New run types on or off for new players?** Recommended off, so the plugin keeps today's herb/tree/fruit tree
   run until the player ticks more.
5. Grow saplings: should 1c also guide making saplings (plant pot, trowel, water) or only list the saplings to
   bring? Recommended: only list them.
6. Entrana hops: include (with an equipment check) or leave out until the equipment rule is confirmed?
   Recommended: leave out at first.
7. New content RuneLite tracks but SPEC didn't name (Aldarin hops, flax/hemp/cotton, camphor/ironwood/rosewood,
   Anglers' Retreat, Great Conch): include? Recommended: yes.

Questions 1-4 are asked first; 5-7 can wait until their sprint starts.

**Answered by elzerma (2026-10-17):**
1. Allotment protection: **the player picks per set** (Flower / Pay gardener / Compost only), starting on Flower.
2. Special trees **join the main combined run** when due, with their own tick box. The crystal tree (added
   2026-10-22) follows the same answer: it is in the Special trees family and joins the run at the Prifddinas
   stop when due.
3. Sprint order **1a → 1b → 1c**.
4. New run types are **off** for new players.
