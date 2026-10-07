# OSRS Farming Reference for a RuneLite Plugin

Sources:
- OSRS Wiki pages, read with WebFetch on 2026-10-06: Tree patch, Fruit tree patch, Herb patch, Huasca seed, Herbs, Tool Leprechaun, Compost, Bottomless compost bucket, Farming, Plant cure, Cure Plant, Resurrect Crops, Pool of Refreshment, Restoration/Revitalisation/Rejuvenation/Fancy/Ornate rejuvenation pool, Seed vault, Group storage, Auto-weed, Seedling, Magic secateurs.
- A shallow clone of `runelite/runelite` (master as of 2026-10-06). I read the `gameval` package and the `plugins/timetracking`, `plugins/banktags` and `plugins/itemcharges` folders.
- A shallow clone of `Zoinkwiz/quest-helper` (master): the `bank/` and `bank/banktab/` folders, `managers/QuestBankManager.java` and `QuestHelperPlugin.java`.

Anything marked **UNVERIFIED** was not confirmed in either source, or two sources disagree about it.

---

## 1. Crops

### 1a. Regular trees (tree patch). You plant a sapling, not a seed.

| Tree | Lvl | Growth | Protection payment | Plant xp | Check-health xp | RL Produce (tickrate x stages) |
|---|---|---|---|---|---|---|
| Oak | 15 | 2h 40m | 1 x Tomatoes(5) (basket) | 14 | 467.3 | OAK 40 min x 5 |
| Willow | 30 | 4h | 1 x Apples(5) (basket) | 25 | 1,456.5 | WILLOW 40 x 7 |
| Maple | 45 | 5h 20m | 1 x Oranges(5) (basket) | 45 | 3,403.4 | MAPLE 40 x 9 |
| Yew | 60 | 6h 40m | 10 x Cactus spine | 81 | 7,069.9 | YEW 40 x 11 |
| Magic | 75 | 8h | 25 x Coconut | 145.5 | 13,768.3 | MAGIC 40 x 13 |

Growth time is `tickrate x (stages - 1)`. That works out to 160, 240, 320, 400 and 480 minutes, which matches the wiki.

- **Saplings:** fill a plant pot with soil, then use the seed on the pot with a gardening trowel in your inventory. Water the seedling with a watering can. It becomes a sapling within about 5 minutes (one growth tick), and it keeps growing in the bank or while you are logged out. The seed vault cannot store seedlings, only finished saplings. Plant the sapling in the patch with a spade.
- **When fully grown:** you must "Check-health" for the xp before the tree can be chopped. The RuneLite TREE patch type has `healthCheckRequired = true`. The tree gives logs when chopped and leaves a stump. To clear the patch, either dig up the roots with a spade after chopping, or **pay the gardener 200 coins to remove the tree**. The wiki says the chance to clear a stump scales with Farming level and is guaranteed at 99.
- **Falador Elite diary:** the Falador tree patch can never become diseased, and the gardener protects it for free. RuneLite's `PaymentTracker` detects the diary message.
- **Patches (wiki):** Lumbridge (Fayeth), Varrock (Treznor), Falador Park (Heskel), Taverley (Alain), Gnome Stronghold (Prissy Scilla), Farming Guild (Rosie, 65 Farming), Nemus Retreat / Auburnvale (Aub).

### 1b. Fruit trees (fruit tree patch). You plant a sapling, made the same way as above.

| Tree | Lvl | Growth | Protection payment | Fruit | Regrow per fruit |
|---|---|---|---|---|---|
| Apple | 27 | 16h | 9 x Sweetcorn | 6 | 40 min |
| Banana | 33 | 16h | 4 x Apples(5) basket **UNVERIFIED** (the wiki summary just said "4x Apples") | 6 | 40 min |
| Orange | 39 | 16h | 3 x Strawberries(5) basket **UNVERIFIED** (the summary said "3x Strawberries") | 6 | 40 min |
| Curry | 42 | 16h | 5 x Bananas(5) basket **UNVERIFIED** (the summary said "5x Bananas") | 6 | 40 min |
| Pineapple | 51 | 16h | 10 x Watermelon | 6 | 40 min |
| Papaya | 57 | 16h | 10 x Pineapple | 6 | 40 min |
| Palm | 68 | 16h | 15 x Papaya fruit | 6 | 40 min |
| Dragonfruit | 81 | 16h | 15 x Coconut | 6 | 40 min |

- RuneLite's `Produce` uses a 160-minute tickrate with 7 stages, giving 6 x 160 = 960 minutes (16 hours). The regrow tickrate is 45 minutes with 7 harvest stages. The wiki says 40 minutes per fruit, so **the regrow time is UNVERIFIED** (40 vs 45 minutes).
- A fully grown tree gives **6 fruit**. You check health once, the fruit regrows, and you can chop the tree when you want to replant. Gardeners will remove a fruit tree instantly for **200 coins**, or you can chop it and dig up the stump with a spade.
- **Patches:** Gnome Stronghold (Bolongo), Catherby (Ellena), Tree Gnome Village (Gileth), Brimhaven (Garth), Lletya (Liliwen), Farming Guild (Nikkie, 85 Farming), Kastori (Ehecatl).

### 1c. Herbs (herb patch). You plant a seed directly with a seed dibber.

| Herb | Lvl | | Herb | Lvl |
|---|---|---|---|---|
| Guam | 9 | | Avantoe | 50 |
| Marrentill | 14 | | Kwuarm | 56 |
| Tarromin | 19 | | Snapdragon | 62 |
| Harralander | 26 | | **Huasca** | **65** |
| Ranarr | 32 | | Cadantine | 67 |
| Toadflax | 38 | | Lantadyme | 73 |
| Irit | 44 | | Dwarf weed | 79 |
| | | | Torstol | 85 |

- **Growth:** every herb takes 80 minutes (4 stages x 20 minutes). In RuneLite's `Produce` this is tickrate 20 with 5 stages, and harvest stages 3.
- **Huasca exists.** It was released on 25 September 2024 with *Varlamore: The Rising Darkness*. It needs level 65, gives 86.5 xp to plant and 110 xp per herb picked. It comes from drops and chests, not shops. RuneLite has `Produce.HUASCA`.
- **Protection:** herb patches **cannot** be protected by a gardener. Disease can only be avoided by using compost (which reduces the chance) or a disease-free patch:
  - Troll Stronghold (My Arm's Big Adventure)
  - Weiss (Making Friends with My Arm)
  - Harmony Island (Elite Morytania diary) **UNVERIFIED**
  - Hosidius (Easy Kourend & Kebos diary) **UNVERIFIED**
  - Civitas illa Fortis (Champion rank at the Fortis Colosseum) **UNVERIFIED**

  The last three come from the WebFetch summary and should be double-checked.
- **Yield:** herbs are harvested in "lives". The base is 3 lives, and compost adds +1, supercompost +2 and ultracompost +3. This is common knowledge, **UNVERIFIED** by exact wiki text. The wiki only states compost raises the herb minimum to 4. Expected herbs per patch at 99 Farming with the best yield boosters (magic secateurs, Farming cape, diary) are below. For lower levels, use about 6 to 8 herbs per patch as a rough estimate.

  | Compost | Expected herbs |
  |---|---|
  | None | 4.712 |
  | Compost | 6.282 |
  | Supercompost | 7.853 |
  | Ultracompost | 9.423 |
- **Magic secateurs** add 10% to the chance of extra yield on herbs, allotments, hops, bushes and similar crops. They do **not** affect tree or fruit tree yields. You get them during Fairytale I - Growing Pains.
- **Herb patches in RuneLite:** Ardougne, Catherby, Civitas illa Fortis, Falador, Harmony, Kourend (Hosidius), Morytania, Troll Stronghold, Weiss, Farming Guild. The wiki also lists Ortus Farm. **UNVERIFIED** whether RuneLite has Ortus Farm under another region name.

### 1d. Gardener payment rules
- Gardeners **accept the payment items in noted form**. The wiki Farming page says: "They will also accept your payment-items in noted form."
- **UNVERIFIED** whether a gardener can take payment directly from tool leprechaun storage. Assume no: the items must be in the inventory, noted is fine.
- Protection guarantees the crop will not become diseased. **UNVERIFIED** whether there is a dedicated "protected" varbit. RuneLite does not use one. It tracks protection itself by reading the gardener's chat dialogue (see 4f).

---

## 2. Tools, the tool leprechaun, and their varbits

**Tools needed for each crop:**
- **Herbs:** rake, seed dibber, spade (to clear dead plants), and secateurs or magic secateurs (optional, for yield).
- **Trees and fruit trees:** rake and spade to plant the sapling. You also need a plant pot, gardening trowel and watering can **only to grow the sapling**. You do **not** need a watering can on the patch itself for trees or herbs.
- Secateurs can also be used to prune diseased trees. **UNVERIFIED** in this pass.

**What the tool leprechaun stores (wiki):**
- Tools: 100 rakes, 100 spades, 100 seed dibbers, 100 secateurs (magic secateurs go in the same slot), 100 gardening trowels.
- 1 watering can (any level, or Gricoller's can).
- 1 bottomless compost bucket.
- 1,000 each of: plant cure, empty buckets, compost, supercompost, ultracompost.
- It will turn produce into bank notes, but not logs or untradeables.

The wiki Plant cure page does not list plant cure as leprechaun storage. That conflicts with the Tool Leprechaun page and with the `FARMING_TOOLS_PLANTCURE` varbit below, so plant cure storage is treated as real.

**RuneLite `net.runelite.api.gameval.VarbitID` (stored leprechaun items):**

| Constant | ID |
|---|---|
| FARMING_TOOLS_RAKE | 1435 |
| FARMING_TOOLS_DIBBER | 1436 |
| FARMING_TOOLS_SPADE | 1437 |
| FARMING_TOOLS_SECATEURS | 1438 |
| FARMING_TOOLS_FAIRYSECATEURS (magic secateurs flag) | 1848 |
| FARMING_TOOLS_WATERINGCAN | 1439 |
| FARMING_TOOLS_TROWEL | 1440 |
| FARMING_TOOLS_BUCKETS | 1441 |
| FARMING_TOOLS_EXTRABUCKETS | 4731 |
| FARMING_TOOLS_EXTRA2BUCKETS | 6265 |
| FARMING_TOOLS_COMPOST | 1442 |
| FARMING_TOOLS_EXTRACOMPOST | 6266 |
| FARMING_TOOLS_SUPERCOMPOST | 1443 |
| FARMING_TOOLS_EXTRASUPERCOMPOST | 6267 |
| FARMING_TOOLS_ULTRACOMPOST | 5732 |
| FARMING_TOOLS_PLANTCURE | 6268 |
| FARMING_TOOLS_EXTRARAKES | 8357 |
| FARMING_TOOLS_EXTRADIBBERS | 8358 |
| FARMING_TOOLS_EXTRASECATEURS | 8359 |
| FARMING_TOOLS_EXTRATROWELS | 8360 |
| FARMING_TOOLS_EXTRASPADES | 8361 |
| FARMING_TOOLS_SELECTEDQUANTITY | 7792 (a UI setting, not a stored item) |
| FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE | 7915 |
| FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY | 7916 |

- **How the counts are split:** the parent varps are `VarPlayerID.FARMING_TOOLS = 615` and `FARMING_TOOLS2 = 2084`. Counts are split across a base varbit and an "EXTRA" overflow varbit, because one varbit cannot hold up to 1,000. The total is probably `base + (extra << baseBits)`, but **the bit-combining formula is UNVERIFIED**. Confirm it in-game with the varbit inspector before relying on it.
- **The two bottomless bucket varbits:** these describe the bucket stored in the leprechaun. **UNVERIFIED** whether they also reflect a bucket held in the inventory. Core RuneLite's itemcharges plugin does **not** track bottomless bucket charges, so no item-charges code exists to copy. The fallback is to parse the bucket's "Check" chat message.
- **Leprechaun interface and container:**
  - `InterfaceID.FARMING_TOOLS = 125` (the storage window)
  - `InterfaceID.FARMING_TOOLS_SIDE = 126` (the inventory side)
  - `InventoryID.FARMING_TOOLS_FAIRYVERSION = 354` (an item container for the fairy version; **purpose UNVERIFIED**)
- **Tithe Farm auto-weed:** `VarbitID.FARMING_BLOCKWEEDS = 5557`. Values are 0 = not owned, 1 = off, 2 = on, matching RuneLite's `Autoweed` enum order (UNOWNED, OFF, ON). Auto-weed costs 50 Tithe points from Farmer Gricoller and is toggled in his reward shop. When it is on, empty patches don't regrow weeds, which is how RuneLite treats it in `predictPatch`.
- **Per-patch compost varbits exist:** for example `FARMING_COMPOST_VARBIT_HERB_1..8_TRANSMIT` (15966 to 15973) and `FARMING_COMPOST_VARBIT_TREE_1..4_TRANSMIT` (15990 to 15993), plus `FARMING_TREE_COMPOST_5..7_TRANSMIT` and `FARMING_COMPOST_VARBIT_FRUIT_TREE_1..7_TRANSMIT` (15939 to 15945). The core Time Tracking plugin does **not** use them yet; it still reads chat messages. **UNVERIFIED** which number maps to which patch and what the values mean.

---

## 3. Compost

| Type | Disease chance reduction | Herb lives bonus | How it's made |
|---|---|---|---|
| Compost | -50% | +1 (min 4 herbs) | 15 items in a compost bin, 60 minutes |
| Supercompost | -80% | +2 | Compost potion on compost, or higher-tier items in the bin |
| Ultracompost | -90% | +3 | 25 volcanic ash on a supercompost bin (50 for the giant bin) |

- Applying any compost gives 18 Farming xp.
- The Arceuus spell **Fertile Soil** applies compost without a bucket. RuneLite's `CompostTracker` handles it.
- **Bottomless compost bucket:**
  - Holds one compost type at a time, up to 10,000 uses.
  - Each bucket you pour in adds 2 uses, so it doubles your compost.
  - It can be filled from a compost bin, or from noted or unnoted buckets (up to 5,000 at once).
  - It has a "Check" option to show what's inside.
  - It is a 1/35 drop from Hespori, and the leprechaun stores one.
- **Compost vs protection:** compost only *reduces* disease. Paying the gardener *prevents* disease, so for trees and fruit trees payment replaces the need for compost as protection. Compost on those patches only matters for the small chance of disease before the payment is made. Herbs cannot be protected, so compost is the only defence.

---

## 4. RuneLite core Time Tracking: how it models patches

Package: `runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming`

### 4a. Main classes
- **`FarmingWorld`** (@Singleton)
  - Builds a `Multimap<Integer regionId, FarmingRegion>`.
  - Each `add(region, extraRegionIds...)` also registers that region under neighbouring region IDs, because the game sends patch varbits while you're nearby, not just on the patch's own tile region.
  - Some regions override `isInBounds(WorldPoint)`. Catherby is one: its upstairs area sends different varbits.
  - `getRegionsForLocation(wp)` returns the regions for `wp.getRegionID()` whose bounds include the player.
- **`FarmingRegion(name, regionID, definite, FarmingPatch...)`**
  - `definite` marks a region whose varbits are only ever about its own patches.
- **`FarmingPatch(name, @Varbit int varbit, PatchImplementation impl, int farmerNpcId, int patchNumber)`**
  - The **same generic varbits** (`FARMING_TRANSMIT_A..P`, IDs 4771 to 4775, 4953 to 4964 and 7904 to 7914) are reused in every region. **What a varbit means depends on which region you are in.** That's why storage keys include the region ID.
- **`PatchImplementation`**
  - An enum: TREE, FRUIT_TREE, HERB, ALLOTMENT, and so on.
  - Each value has `PatchState forVarbitValue(int)`, a `Tab`, and a `healthCheckRequired` flag (true for TREE and FRUIT_TREE, false for HERB).
- **`PatchState(Produce, CropState, stage)`**
  - `getStages()` returns `harvestStages` when the crop is HARVESTABLE or FILLING, otherwise `stages`.
  - `getTickRate()` returns `regrowTickrate` when HARVESTABLE, `tickrate` when GROWING, otherwise 0.
- **`CropState`** enum: HARVESTABLE, GROWING, DISEASED, DEAD, EMPTY, FILLING.
- **`Produce`** enum, constructed as `(name, contractVarbitValue, PatchImplementation, itemId, tickrate(min), stages, regrowTickrate, harvestStages)`. WEEDS is tickrate 5 with 4 stages.
- **`FarmingTracker`**
  - Runs from `TimeTrackingPlugin.onGameTick`, which skips the welcome screen and ticks where the player crosses a region boundary, then calls `farmingTracker.updateData(loc, ticksSinceModalClose)`.
  - Returns early if any modal widget is open, because varbits aren't sent while one is open.
  - Detects the farming tick offset by watching an exact growth step, and stores it in `farmTickOffset` / `farmTickOffsetPrecision`.
  - Leagues worlds use `tickrate / 5`.
  - `predictPatch()` works forward from the stored value and timestamp to the current stage and finish time.
- **Other helpers:** `CompostTracker`, `PaymentTracker`, `PatchPrediction`, `ProfilePatch`, `FarmingContractManager`.

### 4b. Varbit value maps (from PatchImplementation)

**HERB:**
- Weeds: 0 to 3.
- Each herb uses a block of 4 growing values followed by 3 harvestable values, in this order:
  - Guam 4–7 / 8–10
  - Marrentill 11–14 / 15–17
  - Tarromin 18–21 / 22–24
  - Harralander 25–28 / 29–31
  - Ranarr 32–35 / 36–38
  - Toadflax 39–42 / 43–45
  - Irit 46–49 / 50–52
  - Avantoe 53–56 / 57–59
  - **Huasca 60–63 / 64–66**
  - Value 67 is weeds
  - Kwuarm 68–71 / 72–74
  - Snapdragon 75–78 / 79–81
  - Cadantine 82–85 / 86–88
  - Lantadyme 89–92 / 93–95
  - Dwarf weed 96–99 / 100–102
  - Torstol 103–106 / 107–109
- Diseased, 3 values each in the same herb order (Huasca's is out of order):
  - 128–130 Guam, 131 Marrentill, 134 Tarromin, 137 Harralander, 140 Ranarr, 143 Toadflax, 146 Irit, 149 Avantoe, 152 Kwuarm, 155 Snapdragon, 158 Cadantine, 161 Lantadyme, 164 Dwarf weed, 167–169 Torstol
  - **173–175 Huasca**
- Dead (any herb): 170–172.
- Goutweed: 192 to 203.
- Other values are weeds.

**TREE:**
- Weeds: 0 to 7.
- Each tree's GROWING values run up to and including the "check-health" value. The next value is HARVESTABLE (healthy and choppable), and the one after that is the stump, which RuneLite also maps to HARVESTABLE.
  - Oak 8–12, then 13, 14
  - Willow 15–21, then 22, 23
  - Maple 24–32, then 33, 34
  - Yew 35–45, then 46, 47
  - Magic 48–60, then 61, 62
- Diseased: Oak 73–75 and 77, Willow 80–84 and 86, Maple 89–95 and 97, Yew 100–108 and 110, Magic 113–123 and 125.
- Dead: Oak 137–139 and 141, Willow 144–148 and 150, Maple 153–159 and 161, Yew 164–172 and 174, Magic 177–187 and 189.
- 192 to 197 are WILLOW HARVESTABLE (a variant).

**FRUIT_TREE:** each tree uses a block of 6 GROWING, 7 HARVESTABLE (fruit count 0 to 6), 6 DISEASED, 6 DEAD, then one extra HARVESTABLE value (the check-health state) and one extra GROWING value.

| Tree | Growing | Harvestable | Diseased | Dead | Check-health | Extra growing |
|---|---|---|---|---|---|---|
| Apple | 8–13 | 14–20 | 21–26 | 27–32 | 33 | 34 |
| Banana | 35–40 | 41–47 | 48–53 | 54–59 | 60 | 61 |
| Orange | 72–77 | 78–84 | 85–90 | 91–96 | 97 | 98 |
| Curry | 99–104 | 105–111 | 112–117 | 118–123 | 124 | 125 |
| Pineapple | 136–141 | 142–148 | 149–154 | 155–160 | 161 | 162 |
| Papaya | 163–168 | 169–175 | 176–181 | 182–187 | 188 | 189 |
| Palm | 200–205 | 206–212 | 213–218 | 219–224 | 225 | 226 |
| Dragonfruit | 227–232 | 233–239 | 240–245 | 246–251 | 252 | 253 |

Values not listed are weeds. **UNVERIFIED** which of the trailing values is the stump and which is the check-health state; read the comments in PatchImplementation to confirm.

**Disease has no separate varbit.** It is encoded inside the patch varbit value as shown above. The plugin should copy these value ranges (they're facts about the game) rather than import RuneLite's package-private enums.

### 4c. Patches the plugin needs (region, varbit, type)

| Region (ID) | Varbit | Type |
|---|---|---|
| Ardougne (10548) | TRANSMIT_D | HERB |
| Auburnvale (5427) | TRANSMIT_A | TREE |
| Brimhaven (11058) | TRANSMIT_A | FRUIT_TREE |
| Catherby (11062) | TRANSMIT_D | HERB |
| Catherby (11317) | TRANSMIT_A | FRUIT_TREE |
| Civitas illa Fortis (6192) | TRANSMIT_D | HERB |
| Falador (11828) | TRANSMIT_A | TREE |
| Falador (12083) | TRANSMIT_D | HERB |
| Gnome Stronghold (9781) | TRANSMIT_A | TREE |
| Gnome Stronghold (9781) | TRANSMIT_B | FRUIT_TREE |
| Harmony (15148) | TRANSMIT_B | HERB |
| Kastori (5423) | TRANSMIT_B | FRUIT_TREE |
| Kourend (6967) | TRANSMIT_D | HERB |
| Lletya (9265) | TRANSMIT_A | FRUIT_TREE |
| Lumbridge (12594) | TRANSMIT_A | TREE |
| Morytania (14391) | TRANSMIT_D | HERB |
| Taverley (11573) | TRANSMIT_A | TREE |
| Tree Gnome Village (9777) | TRANSMIT_A | FRUIT_TREE |
| Troll Stronghold (11321) | TRANSMIT_A | HERB |
| Varrock (12854) | TRANSMIT_A | TREE |
| Weiss (11325) | TRANSMIT_A | HERB |
| Farming Guild (4922) | TRANSMIT_G | TREE |
| Farming Guild (4922) | TRANSMIT_E | HERB |
| Farming Guild (4922) | TRANSMIT_K | FRUIT_TREE |

The extra region IDs for each `add(...)` call are at the end of that call in `FarmingWorld.java`. Copy them too, so the plugin can read patches from neighbouring regions.

### 4d. Config storage
- **Config group:** `"timetracking"` (`TimeTrackingConfig.CONFIG_GROUP`). Everything is per account, written with `ConfigManager.setRSProfileConfiguration`.
- **Patch state key:** `<regionID>.<varbitID>`, with the value `"<varbitValue>:<unixSeconds>"`. The source comment shows it as `timetracking.<rsprofile>.<regionID>.<VarbitID>=<varbitValue>:<unix time>`.
  - The value is rewritten when it changes, or every 5 minutes if it hasn't.
  - When the patch becomes DEAD, HARVESTABLE or EMPTY, the compost and protected flags for that patch are cleared.
- **Compost key:** `<regionID>.<varbitID>.compost`, holding a CompostState: COMPOST, SUPERCOMPOST or ULTRACOMPOST.
- **Protection key:** `<regionID>.<varbitID>.protected`, a boolean.
- **Other keys:**
  - `autoweed`: the FARMING_BLOCKWEEDS value as a string
  - `farmTickOffset`, `farmTickOffsetPrecision`
  - `notify.<regionID>.<varbitID>`, `preferSoonest`, `birdhouse...`
- **To read another account's data:** use `configManager.getConfiguration(group, profileKey, key)`.

### 4e. CompostTracker
Builds a pending "compost action" when the player uses one of these on a patch object (30-second timeout): BUCKET_COMPOST, BUCKET_SUPERCOMPOST, BUCKET_ULTRACOMPOST, BOTTOMLESS_COMPOST_BUCKET_FILLED, or the Fertile Soil spell. It then confirms the action with these chat regexes:
- `You treat the .+ with (?<compostType>ultra|super|)compost\.`
- `^The .+ has been treated with (?<compostType>ultra|super|)compost` (Fertile Soil)
- `This .+ has already been (treated|fertilised) with (ultra|super|)compost...`
- `This is an? .+\. The soil has been treated with (ultra|super|)compost\..*` (Inspect)

### 4f. PaymentTracker
- Records which option the player picked when talking to a farmer:
  - from `MenuOptionClicked` on the NPC's "Pay" option (NPC_THIRD_OPTION or NPC_FOURTH_OPTION),
  - or from the dialogue option, chosen by click or by keypress.
- Then matches the NPC's chat-head dialogue against:
  - "That'll do nicely, sir/madam/… Leave it with me - I'll make sure that patch grows for you."
  - "Alright, leave it with me. I'll look after that nursery for you."
  - The Falador diary text: "The gardener protects your tree for you, free of charge, as a token of gratitude for completing the Falador elite diary."
- Uses the patch's `farmer` NPC ID and `patchNumber` to work out which patch was paid for.

### 4g. What to copy into our own plugin (don't import; these classes are package-private)
1. A static `PatchDef(regionId, int[] extraRegions, boundsPredicate, varbitId, PatchType, farmerNpcId, patchIndex)` table for the 24 tree, fruit tree and herb patches above.
2. A `PatchType.decode(int value) -> {crop, state, stage}` function per type, using the ranges in 4b. Huasca must be included.
3. On `GameTick`:
   - skip if a modal widget is open, or if the player crossed a region boundary on this tick;
   - for every patch definition matching the player's region and bounds, read `client.getVarbitValue(varbit)`;
   - save `value:epochSeconds` under our own config group, using RS-profile config and the key `<region>.<varbit>`.
4. Predict growth with `tickrate`, `stages` and `regrowTickrate`, aligned to 5-minute farming ticks. Copying the tick-offset logic is optional.
5. Copy the compost and payment chat-regex approach, or try the new `FARMING_COMPOST_VARBIT_*_TRANSMIT` varbits (meaning UNVERIFIED).
6. Read `FARMING_BLOCKWEEDS` to decide whether empty patches regrow weeds.

---

## 5. Quest Helper: generated bank tab pattern

Repo `Zoinkwiz/quest-helper`, package `com.questhelper.bank.banktab`. Key classes:

- **`QuestBankTabInterface`** (the button in the bank title bar):
  - On `WidgetLoaded` for `InterfaceID.BANKMAIN`, `init()` gets `InterfaceID.Bankmain.UNIVERSE` and calls `createChild(-1, WidgetType.GRAPHIC)` twice:
    - a background button (sprite `Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL`, 25x25 at x=408, y=5, name "quest-helper"),
    - an icon (`AchievementDiaryIcons.BLUE_QUESTS`).
  - The button has the action `setAction(1, "View tab ")` and an `OnOpListener` JavaScriptCallback. That callback:
    - sets `VarbitID.BANK_CURRENTTAB` to 0,
    - toggles a `questTabActive` flag,
    - swaps the background sprite to `_SELECTED`,
    - calls `bankSearch.reset(true)` (RuneLite `BankSearch`) to rebuild the bank.
  - It closes the potion store first if it was open (bank tab 15).
  - It clears the search button's timer and sprite, copying what `bankmain_search_setbutton` does.
  - `handleClick(MenuOptionClicked)` closes the tab when the player clicks a real tab, "View all items", "View tag tab" or "Potion store". `handleSearch()` closes it when bank search is toggled.
- **`QuestBankTab`** (@Subscribe event handlers; does the layout):
  - `ScriptCallbackEvent "getSearchingTagTab"`: writes `1` to the top of the int stack while the tab is active.
  - `ScriptPostFired(ScriptID.BANKMAIN_SEARCHING)`: forces the return value to 1, so the bank behaves as if it is searching and shows every item.
  - `ScriptPreFired(BANKMAIN_FINISHBUILDING)`: sets `Bankmain.TITLE` to "Tab <col=ff0000>{quest name}</col>".
  - `ScriptPostFired(BANKMAIN_FINISHBUILDING)`: in `clientThread.invokeAtTickEnd`, runs `sortBankTabItems`, which:
    - hides all existing item widgets in `Bankmain.ITEMS`,
    - reuses those child widgets in order to draw each section: a header text widget plus a separator graphic, then the items (`setItemId`, `setItemQuantity`, withdraw actions rebuilt to match `BANK_QUANTITY_TYPE`, `setOpacity(120)` for missing items or placeholders, and a "Details" action on fake items),
    - adds "/ N" text with a tick or cross sprite for the required quantity,
    - adds a leftover "Non-quest items" section,
    - runs `ScriptID.UPDATE_SCROLLBAR`.
  - `MenuOptionClicked` (priority -1) rewrites `menu.setParam0(bank.find(itemId))` so withdrawing works from the moved widgets, and consumes "Details" clicks on fake items.
  - The class also counts items held in the bank's potion store (its `PotionStorage` class).
  - It also has a GE search hook (`QuestGrandExchangeInterface`).
- **`QuestHelperBankTagService`**: not a RuneLite `BankTagsService`. It builds a `List<BankTabItems>` (named sections, each holding `BankTabItem`s and recommended items) from the selected quest's panels and item requirements. It can return only the missing items, and the result is cached per game tick.
- **Supporting classes:** `BankTabItems`, `BankTabItem`, `BankText`, `BankWidget`, `BankSlotIcons`. It reads constants from `BankTagsPlugin.*` (static import) for item sizes and spacing.
- **Item checklist and cached bank:**
  - `QuestBank` and `GroupBank` (extends QuestBank) save the last-seen bank as JSON `int[]` (id, qty pairs) under group `QuestHelperConfig.QUEST_HELPER_GROUP` with RS-profile keys `"bankitems"` and `"groupbankitems"`.
  - `QuestBankManager` handles loading and saving.
  - Requirements are checked with `ItemRequirement.check()` / `checkWithAllContainers()`, which look at the inventory, worn items, the cached bank, and so on.

**Pattern summary for us:**
1. Add a button to `Bankmain.UNIVERSE` on WidgetLoaded.
2. On click, set a flag, reset `BANK_CURRENTTAB`, and call `bankSearch.reset(true)`.
3. While the flag is set, answer the `getSearchingTagTab` callback and the `BANKMAIN_SEARCHING` return value with 1, so the whole bank is in the widget list.
4. In `BANKMAIN_FINISHBUILDING` post, hide the item widgets and reposition and reuse them in sections, then fix the scrollbar.
5. Fix up the menu `param0` so withdrawing works.

**A simpler alternative in core RuneLite** (`net.runelite.client.plugins.banktags`):
- Inject `TagManager` and call `registerTag(name, BankTag)`. `BankTag` is a single method, `contains(int itemId)`.
- Optionally `LayoutManager.saveLayout(new Layout(tag, int[]))`, or `registerAutoLayout(plugin, name, AutoLayout)`.
- Then call `BankTagsService.openBankTag(tag, OPTION_HIDE_TAG_NAME | OPTION_ITEMS_NOT_IN_LAYOUT_AT_BOTTOM ...)`. The option flags are ALLOW_MODIFICATIONS 0x1, HIDE_TAG_NAME 0x2, NO_LAYOUT 0x4, ITEMS_NOT_IN_LAYOUT_AT_BOTTOM 0x8.
- These services are bound by `BankTagsPlugin`, so the Bank Tags plugin must be enabled. Add `@PluginDependency(BankTagsPlugin.class)`.
- Quest Helper does not use this API for its tab. It predates it, and it needs section headers and the quantity text.

---

## 6. Healing pools

- **Pool of Refreshment (Ferox Enclave chapel, free-to-play, 2 pools; also Daimon's Crater):** fully restores hitpoints, prayer and run energy, cures poison, venom and disease, and resets all boosted or lowered stats. It also switches off active prayers. It does **not** restore special attack at Ferox; the Daimon's Crater pool does (since 24 May 2023).

POH superior garden pools (Construction level). Each tier includes everything the one before it does:

| Pool | Con | What it restores |
|---|---|---|
| Restoration pool | 65 | Special attack |
| Revitalisation pool | 70 | Special attack and run energy |
| Rejuvenation pool | 80 | Special attack, run energy and prayer |
| Fancy rejuvenation pool | 85 | All of the above, plus reduced stats (not Hitpoints) |
| Ornate rejuvenation pool | 90 | All of the above, plus Hitpoints, surge potion cooldown, and cures poison, venom, disease and bleed |

- The POH pools can't be used right after PvP combat.
- **UNVERIFIED** whether the frozen ornate pool variant differs (assumed cosmetic).

---

## 7. Group storage (GIM) and seed vault: how to read them in RuneLite

These containers can only be read while their interface is open. `ItemContainerChanged` fires when they open and when they change. Cache the last-seen contents per RS profile in config, the way Quest Helper does.

- **Group storage:**
  - `net.runelite.api.gameval.InventoryID.INV_GROUP_TEMP = 659` is the group storage contents. Legacy name `InventoryID.GROUP_STORAGE(659)`.
  - `INV_PLAYER_TEMP = 660` is the player's inventory while the group storage window is open. Legacy name `GROUP_STORAGE_INV(660)`.
  - Interface IDs: `InterfaceID.SHARED_BANK = 724`, `SHARED_BANK_SIDE = 725`.
  - `VarbitID.GIM_SHARED_BANK_HASEDITED = 4602` is 1 while there are unsaved edits. Quest Helper holds the snapshot until the inventory matches the final state, and only then saves it as the real group bank (`QuestBankManager.updateLocalGroupBank`, `GroupBank`).
  - Only one group member can have group storage open at a time.
  - 80 slots at the start, up to 200 with unlocks.
  - Members' items can't be moved on free-to-play worlds.
  - It is opened from the bank.
- **Seed vault:**
  - `InventoryID.SEED_VAULT = 626` (legacy `InventoryID.SEED_VAULT(626)`).
  - Interface IDs: `InterfaceID.SEED_VAULT = 631`, `SEED_VAULT_DEPOSIT = 630`.
  - Favourite and category varbits: `SEED_VAULT_CATEGORY 8171`, `SEED_VAULT_FAVE1..8` (8172 to 8179).
  - It is in the Farming Guild, west of the bank chest, and can't be opened remotely. Ultimate ironmen can't use it.
  - It stores seeds and saplings (not quest seeds, and not unfinished seedlings).
- **Bank:** `InventoryID.BANK = 95`. The tool leprechaun is covered by the varbits in section 2 (they are sent at all times, so it doesn't need to be open). **UNVERIFIED** whether the leprechaun varbits are sent while the player is away from a patch; they are player varps, so they should be.

---

## 8. Disease, Plant Cure and Resurrect Crops

- **Disease state** is part of the patch varbit value (section 4b). There is no separate disease varbit.
  - A diseased crop stops growing.
  - If it isn't cured, it dies at the end of that growth cycle.
  - Compost lowers the chance by 50%, supercompost by 80%, ultracompost by 90%.
  - Gardener protection prevents disease (trees and fruit trees only, not herbs).
- **Plant cure:** an item, not a spell. Used on a diseased plant, it makes the plant healthy again. It doesn't work on dead plants. It costs about 40 coins from farming shops (one wiki page says 25 coins from the gardener) and around 293 gp on the GE. The tool leprechaun stores up to 1,000 (varbit `FARMING_TOOLS_PLANTCURE 6268`).
- **Cure Plant** (Lunar spellbook):
  - Magic 66; costs 1 astral and 8 earth runes.
  - Gives 60 Magic xp and 91.5 Farming xp.
  - Needs Lunar Diplomacy.
  - Cures a diseased patch but can't revive a dead one.
  - Widget: `InterfaceID.MagicSpellbook.CURE_PLANT`.
- **Resurrect Crops** (Arceuus spellbook):
  - Magic 78; costs 25 earth, 8 blood, 12 nature and 8 soul runes; gives 90 Magic xp; no quest needed.
  - Brings a dead patch back. The success chance goes from 50% at level 78 to 75% at level 99.
  - It doesn't work on the anima patch, Hespori or Tithe Farm, and a patch can only be resurrected once.
  - The WebFetch summary also said it can't be used on tree patches. **UNVERIFIED**: this conflicts with the main exclusion list.
  - Widget: `InterfaceID.MagicSpellbook.RESURRECT_CROPS`.
- **Fertile Soil** (Arceuus, compost without a bucket): `InterfaceID.MagicSpellbook.FERTILE_SOIL`. **Its level and runes are UNVERIFIED** (not fetched).
