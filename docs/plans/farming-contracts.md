# Explore: Farming Guild contracts

**Status:** option (c) built on 2026-11-02 (section 8), its game values confirmed and a first run done that day; the
hand-in and a new contract's crop done on the spot are still to be seen in a run. The tier tip (d) comes after the
release.
The explore write-up below is from 2026-10-17.
**From:** issue #7, elzerma: "the other feature I want to manage now as well is farming contracts ... where it
wouldn't touch farming guild patches that are used with wiki tables for managing the farming guild's contracts
best optimizations ... adding farming contracts will lead to more seeds that can be used for more farm runs".
The ledger has this as **Explore** under #7.

## 1. How contracts work (OSRS wiki)

Sources: wiki pages *Farming contracts*, *Seed pack*, *Farming Guild*, *Ironman Guide/Farming* (all read 2026-10-17).

- **Who and where.** Guildmaster Jane, in the guild's central area (45 Farming to enter). She gives one contract at
  a time: grow a named crop in a Farming Guild patch.
- **Tiers.** Easy (45 Farming, 31 crops), Medium (65, 22 crops), Hard (85, 18 crops). 71 entries in all. Boosts
  work for asking; you don't need the boost to claim. Jane picks with equal weight among the entries of that tier
  you meet the level for, and never the same crop twice in a row.
- **Done when.** Crops with a health check (trees, fruit trees, bushes, cactus, celastrus, redwood): when you check
  its health. Other crops (herbs, allotments, flowers): when the patch is fully harvested. A crop that was already
  planted but not yet checked or harvested still counts, so a pre-planted crop completes a matching contract at once.
- **Cancelling.** "Do you have anything easier?" swaps to a lower tier. Easy contracts can't be cancelled. Since
  March 2025 Jane offers "the same tier again" after a turn-in, and since April 2025 that option won't give a tier
  you lack the level for.
- **Reward.** A seed pack, tier 1 to 5. Easy contracts give tiers 1 to 3, Medium 2 to 4, Hard 3 to 5. A pack is
  6 to 10 rolls of low, medium and high seed tables (high = the good herb and tree seeds; a tier 5 pack has
  1 to 3 high rolls). The pack is destroyed on death. Jane also swaps a spirit seed for a tier 5 pack.

**Guild patches a contract can use** (the herb, tree and fruit tree ones are also in this plugin's runs today):

| Guild tier | Patches |
|---|---|
| Beginner, east wing (45) | 2 allotments, flower, bush, cactus |
| Intermediate, west wing (65) | **herb**, **tree** (also Hespori and anima, not contract patches) |
| Advanced, north wing (85) | **fruit tree**, celastrus, redwood (90) (also spirit tree, not a contract patch) |

**How many contracts the plugin's three run types cover today:**

| Tier | Herb | Tree | Fruit tree | Covered | Rest (needs the new crop types) |
|---|---|---|---|---|---|
| Easy | 8 | 3 | 4 | 15 of 31 | allotment 6, flower 5, bush 4, cactus 1 |
| Medium | 6 | 3 | 4 | 13 of 22 | allotment 2, bush 2, cactus 2, flower 1 |
| Hard | 5 | 3 | 2 | 10 of 18 | allotment 2, bush 2, cactus 1, flower 1, celastrus 1, redwood 1 |

### What players do (wiki "Strategy" and the Ironman guide)

1. **Pre-plant and don't check.** Grow likely contract crops in every guild patch and leave them unchecked and
   unharvested. When Jane asks for one, check or harvest it, turn in, ask again: several contracts in a row
   in minutes. The Ironman guide's Hard pre-plants: herb cadantine/lantadyme/snapdragon, tree maple/yew,
   fruit tree palm, bush poison ivy/whiteberry, cactus potato cactus, flower white lily/nasturtium,
   allotments watermelon and snape grass, plus celastrus and redwood.
2. **Always ask for the highest tier**, and downgrade only if the crop isn't pre-planted and takes longer than a
   bush (2 h 40 min), unless you'll be logged out anyway.
3. **Don't skip quick contracts** (herbs, bushes): skipping can land a 16-hour fruit tree that can't be skipped.
4. **Keep 3 to 4 seeds of every common contract crop** in the seed vault (the vault already has a "Farming
   Contract" section).

**What this means for farm runs:** the guild herb, tree and fruit tree patches are also normal run stops. A run
that checks the health of a pre-planted guild maple, or harvests a pre-planted guild snapdragon, throws away the
pre-plant. That is the clash elzerma described.

## 2. What already exists (don't duplicate it)

### RuneLite's Time Tracking plugin (core)

`timetracking/farming/FarmingContractManager.java`, `FarmingContractInfoBox.java`, `OverviewTabPanel.java`:

- **Reads** `VarbitID.FARMGUILD_CONTRACT_TYPE` (7963): which crop; each `Produce` has its contract value
  (Guam 20, Marrentill 21, ...). And `VarbitID.FARMGUILD_CONTRACT_COMPLETE` (7961): above 0 means the crop is done
  and waiting to be turned in, so it clears the contract. It remembers the contract per profile (config key
  `contract`) because the varbits only arrive near the guild.
- **Works out** the state from its own patch predictions for the guild patches of the contract's type:
  Empty, Occupied (another crop is in the patch), In progress (with time left), Diseased, Dead, Ready.
- **Shows** a "Farming Contract" line in its Overview tab (seed box icon, "Ready" or "Ready in 2h 10m"),
  an infobox with that tooltip while you're in the guild, and highlights the contract patch in its farming tabs.
- **Doesn't**: say which tier to ask for, plan supplies, fit the contract into a run, or protect pre-plants.
  It doesn't read `FARMGUILD_CONTRACT_LEVEL` (7962, probably the tier) or `FARMGUILD_CONTRACT_COUNT` (7964,
  probably contracts done). Both meanings are **UNVERIFIED**. (Confirmed by elzerma's dev check, 2026-11-02: LEVEL is
  the tier, COUNT the contracts completed; see section 8.)

### Plugin Hub plugins

| Plugin | What it does |
|---|---|
| **farming-contract-helper** (Leagen312) | Sidebar and overlay: contract crop, which patch and where, seed and payment with have/missing, patch status, time left. |
| **farming-contract-bank-seed** (MaeveMcT) | A Farming contract section at the top of the bank with the seed or sapling, plus a "Preplant seeds" section from a crop chosen per guild patch; outlines the patches and seeds. |
| **farming-guild-overview** (wouterrutgers) | The state of every guild patch at a glance, to see what needs pre-planting. |
| **farming-patch-advisor** (Seadeadmeat) | A general run helper; nothing contract-specific found in its README. |

So "show the contract, its patch, seed and payment" is done three times over (Time Tracking plus two Hub
plugins). What nobody does is **fit contracts into a farm run**: keep the run off the pre-plants, carry the
contract's supplies with the run's, and stop at Jane on the way.

## 3. Options

Sizes: **S** about a day of work, **M** a few days, **L** a week or more, each with its own in-game tests.

### (a) Leave the contract patches alone in normal runs (S)

Today a player can untick a guild patch (`RunConfig.disabledPatches`), but then the run never uses it at all,
even when it's empty. Options:

- **a1. A setting per guild patch:** "Use in runs" / "Plant only when empty" / "Leave alone". "Plant only when
  empty" plants an empty, weedy or dead patch with the run's seed, but never checks health, harvests, chops or
  pays to clear a crop that's already there (it may be a pre-plant). Simple, predictable, no game values needed.
- **a2. Automatic:** a guild patch holding a crop that isn't the run's chosen seed for it is left alone, as are
  crops on the contract list. Fewer settings, but it guesses: a player who grows ranarr in every patch and also
  pre-plants ranarr for contracts would still lose it.

**Recommended: a1**, with "Plant only when empty" as the default for players who've ever had a contract
(`FARMGUILD_CONTRACT_TYPE` seen above 0) and "Use in runs" otherwise. No new game values; the test is a run with a
grown guild tree. This is the part elzerma's words ask for first ("wouldn't touch farming guild patches").

### (b) Show the current contract and whether it fits this run (S to M)

Read the two varbits Time Tracking reads, keep our own contract-value table in the data layer (the
`Produce` enum is Time Tracking's internals, not something to build on), and show **one line** in the Run tab:

- "Contract: Snapdragon (herb). This run plants it in the guild herb patch." or
- "Contract: Whiteberries (bush). Not part of your runs." or
- "Contract crop is ready: check it during this run, then see Jane."

No countdown or patch state: Time Tracking already shows those, and the say-it-once rule applies. Size S if it
only covers herb/tree/fruit tree crops; M once the crop types plan adds the rest (the table needs every crop).

### (c) Plan the contract into the run (L)

When the contract's patch type is in the run:

- **Plant it.** The guild patch of that type gets the contract crop instead of the run's usual seed (for
  herbs, only if the patch isn't already holding the crop). Supplies follow: the seed or sapling, the tree or fruit
  tree payment (or compost, per the run's protection setting), and seed vault pick-up through the existing #8 work.
- **Turn it in.** When the crop is ready (or already pre-planted), the guild stop checks or harvests it, then a
  "Talk to Guildmaster Jane: claim the seed pack, ask for a new contract (same tier)" step. Jane is in the central
  area by the entrance, so it should add little to the guild stop (to be measured).
- **Take the next one.** If the new contract is a crop that's already pre-planted, a herb, or the run carries its
  seed, the guide offers to do it on the spot. Seed pack seeds go into the bank or vault and count for later runs
  (the "more seeds for more farm runs" in #7).
- **c2, later: pre-plant planning.** A crop chosen per guild patch (the Ironman guide's table as the default),
  planted by the run whenever that patch is empty, and kept by (a). farming-contract-bank-seed does the bank
  side of this already.

Needs new game values to test: the Jane NPC and a way to see the turn-in happened (the COMPLETE varbit
dropping, the contract type changing, or a chat message), all **UNVERIFIED**, so a guided test per piece. Needs
(a) and (b) first. (Confirmed 2026-11-02, section 8: Jane is NPC 8587, and COMPLETE goes to 2 at the turn-in.)

### (d) Suggest which tier to ask for (M)

Jane picks evenly among the tier's entries you meet the level for, so the plugin can count, for each tier,
how many of its contracts you could finish **now** (already grown in a guild patch), **this run** (a herb or bush
you have the seed for), or **only slowly** (a 16-hour fruit tree), and the seed pack tier each gives. Then one
line when you're heading to Jane, e.g. "Ask for Hard: 6 of 18 are already grown, 4 more are herbs you carry."
It also covers the downgrade rule: "Snapdragon isn't planted: grow it (1 h 20 m) rather than ask for easier."
Needs the 71-entry table (level, crop, patch, pack tier) in the data layer. Most useful for players who
pre-plant; a player who doesn't would always see "Hard".

## 4. Plugin Hub rules

Nothing here is restricted. Reading varbits and patch states, a sidebar line, and highlighting Jane or a patch
are all allowed (Quest Helper and Time Tracking do the same). To stay inside the rules:

- No menu entries that send actions, no picking Jane's dialogue options for the player, no automatic seed pack
  opening. The plugin only says what to do.
- No data leaves the client (contracts are the player's own state; nothing is shared).
- No combat content: Hespori seeds come from contracts, but the Hespori fight stays out of this.

## 5. Where this meets the new crop types

The crop types plan (another session, `docs/plans/crop-types.md`) decides when bushes, allotments, flowers,
cactus, celastrus and redwood join the runs. For contracts:

- (b)'s table can list every contract crop now, but "fits this run" only says yes for patch types the
  plugin runs. More than half of Easy contracts are allotments, flowers and bushes.
- (a) should be built per guild patch so the new guild patches (allotments, flower, bush, cactus, celastrus,
  redwood) slot in with the same three choices.
- (c) and (d) get much better with bushes and flowers: they're the quick contracts the strategy says not to skip,
  and a guild bush and flower are the cheapest pre-plants.
- Celastrus and redwood are guild-only patches whose main use is contracts, so if the crop types plan adds them,
  "Leave alone" may be the right default for them.

## 6. Open questions for elzerma

1. Which option first: (a) alone, (a) + (b), or straight to (c)?
2. For (a), the per-patch setting (a1) or the automatic guess (a2)?
3. Should the run ever take a contract on its own initiative, or only when the player has one already?
4. Is (d) wanted at all, given it mostly helps players who pre-plant?
5. Do contracts wait for the crop types work, or start with herb/tree/fruit tree and grow with it?

## 7. Decisions (elzerma, 2026-10-17)

- **First: (c)**, planning the contract into the run (supplies, the turn-in, a stop at Guildmaster Jane), building
  what it needs of (a) and (b) on the way.
- **Guild patches: a toggle, not a1 or a2.** In elzerma's words: "Runs will eventually encompass contracts with a
  toggle-able setting for if user would like the plugin to build that into a run." With the toggle on, the guild
  patches are run for contracts, using the pre-plants the wiki already sets out
  ([Ironman Guide/Farming, Farming contracts](https://oldschool.runescape.wiki/w/Ironman_Guide/Farming#Farming_contracts):
  the Medium and Hard pre-plant tables in section 1). So c2 (pre-plant planning) is part of (c), with the wiki's
  table as the plan, and the run keeps those pre-plants unchecked until a contract asks for them. With it off, runs
  use the guild patches as they do today.
- **(d), the tier suggestion: yes, after (c).**

Still open: questions 3 and 5 in section 6 (whether the run takes a new contract on its own, and whether
contracts start with herb/tree/fruit tree or wait for the crop types work). Nothing is built until elzerma
asks for the build plan.

## 8. Built (2026-11-02)

elzerma, 2026-11-02: "plan and build before release ... handling farming contracts". Settled in the picker that day:
the guild stop ends at Guildmaster Jane every time there's something to see her about (question 3: yes, the run
takes contracts); every guild contract patch is kept for contracts, the allotments too; ask for the highest tier the
Farming level allows; a blocked contract whose crop is slower than a bush is swapped ("ask for an easier one"), a quick
one gets its patch cleared; the tier tip (d) after the release. The crop types are built (question 5 is moot).

- **Setting:** Farm > Farming contracts > "Build farming contracts into runs" (`RunConfig.farmingContracts`, off by
  default, kept whichever preset is applied).
- **The contract:** `tracking/ContractTracker` reads FARMGUILD_CONTRACT_TYPE, _COMPLETE, _LEVEL and _COUNT each tick,
  as Time Tracking's FarmingContractManager does (no region check), and logs `Farming contract: <crop> (value N),
  complete N, level N, count N at tick N` as it changes. A contract is held while COMPLETE is 0 and grown at 1; at 2
  (handed in, none taken since) Jane is asked for another. LEVEL gives its tier.
- **The tables:** `data/FarmingContract`: each crop's contract value (RuneLite's `Produce`), the tiers each crop can
  come in (wiki: 31 Easy, 22 Medium, 18 Hard), the wiki's Hard and Medium pre-plants per guild patch (the first one the
  player has seeds for), and Guildmaster Jane's name and spot (1246, 3729).
- **The plan:** `route/ContractPlanner` gives each guild contract patch a role from what it holds: LEAVE (a crop
  waiting for its contract, or the contract's still growing), TAKE (the contract's crop grown: check or pick it, then
  replant; or a diseased crop to cure), PLANT (empty, weeds, dead, a stump or a tree already checked), or
  CLEAR_FOR_CONTRACT (an Easy or quick contract blocked by another crop: dig it up, plant the contract's). And Jane's
  part: HAND_IN, NEW or EASIER. `RunSelector` runs the guild's contract patches by role, whatever types are ticked, with
  one anchoring the stop when only Jane is to see; `SupplyCalculator` plants them with the planner's crop.
- **Crops off the plan** (elzerma, 2026-11-02: "We just want to pick anything that isn't in alignment with the
  pre-planting plan"): a crop that isn't one of its patch's pre-plants at the player's level (`ContractPlanner.offPlan`)
  is a TAKE once grown, picked or checked like any harvest and the pre-plant planted in its place, as long as the player
  has a seed or sapling of one (`ContractPlanner.hasPrePlant`: picking it only to leave the patch empty gains nothing,
  and on elzerma's second run, with no white lily seed, the step to pick the nasturtiums was ticked off); without one,
  the Run tab says "kept until you have white lily seeds to plant there". Still growing, it waits, on the Run tab with
  its usual status rather than "kept for a farming contract". A patch emptied that way takes the
  contract's crop first, slow or not, and one holding a crop off the plan makes way for a quick contract before a
  pre-plant does. At elzerma's guild that means the south allotment's watermelons (the Medium table has strawberries
  there) and the flower patch's nasturtiums (white lilies).
- **The guide:** at the guild stop the planned contract patches are worked by their role as they read at the time,
  unless this run is planting them; when the contract changes, those passed over are looked at again. A guild patch
  outside the plan joins the stop for the contract in hand, read since arriving: one holding its crop to take, or the
  patch its crop is to be planted in, cleared first if need be. It stays to the stop's end, so a crop taken is replanted
  with its pre-plant and a contract's crop planted, a seed only in the seed vault asked for there ("Take a snapdragon
  seed from the seed vault"), and the stop goes from contract to contract until there's nothing to do for one (elzerma,
  2026-11-03: "contract helper should guide you to clear/plant next without having to gen a new run ... and if you're
  chaining contracts bc you have preplanted it should keep going until you're in idle again"; that morning the
  snapdragon contract took a second run, to pick the irit and plant it). A pre-plant waiting for its contract never
  joins: until elzerma's first run with contracts on, 2026-11-02, every guild patch did, and the stop began at the north
  allotment, its watermelons kept for a contract, until the guild's patches were read. A patch already worked this
  stop isn't taken up again, so a pre-plant just planted isn't dug up for the next contract. A crop
  cleared for a contract is checked or picked first when grown, and dug up only while still growing. A guild patch run
  for contracts is always replanted, whatever its type's "pick and leave growing" setting, as the plan has it. The stop
  ends at Jane, outlined, with the walking line to her: "Talk to
  Guildmaster Jane: hand in your contract, then take another Hard contract", "ask for a Hard contract" or "ask for an
  easier contract (magic takes 8 h to grow)". The step clears once the contract's values show nothing to see her about.
- **Run tab:** one line, e.g. "Contract: Maple is grown: checked at the guild this run", "No farming contract: ask
  Guildmaster Jane for a Hard one"; the guild stop's hover names Jane's part.
- **Dev check:** `CONTRACT_VALUES`, passed on elzerma's run of 2026-11-02 and removed. All four values read the same
  away from the guild as at it. COMPLETE went to 1 the tick the marrentill was fully picked, with the completion
  message, and COUNT from 2 to 3; Jane's seed pack set it to 2, TYPE still the marrentill's, and the next contract
  (jangerberry) set it back to 0. LEVEL was 1 for the marrentill (Easy only) and 2 for the jangerberry (Medium, the
  only other tier open at Farming 69), so 3 is Hard. Jane is NPC 8587, seen at (1246, 3729).
- **First run** (elzerma, 2026-11-02, Farming 69 so the Medium table, the jangerberry contract held): the guild stop
  planted the jangerberry in the bush patch and paid Alan for it, and the Medium pre-plants (cactus, irit) in the
  patches found empty; the trees, flower and allotments were left alone, and there was nothing to see Jane about.
- **Carrying on, first try** (elzerma, 2026-11-03 evening): the snapdragon was picked and handed in, the herb patch
  replanted with irit, a whiteberry contract taken and done on the spot (the bush checked, picked, dug up and planted
  again with a whiteberry seed "from the seed vault"), and a potato cactus contract taken. Two dead ends with no way
  out: "Pay Alan: 8 x Mushroom" for the replanted bush, with none (the plan works out payment only for its own
  patches), and planting the potato cactus with no seed for it; ticking the guild stop left both, and Jane's step.
  Since then: a patch the contract took up is paid for only with the payment carried, a step on one whose item isn't to
  hand is left out after a moment, a contract's crop is planted (or a pre-plant cleared for it) only with a seed for it
  (the Run tab says "you have no seed for it"), and ticking the guild stop ends its contract work and Jane's step too.
  With no seed, a Medium or Hard contract is swapped, the wiki's way out of a contract that can't be done (Farming
  contracts: Jane's "anything easier?"; the Ironman Guide's advice is to keep 3–4 seeds of each common contract crop in
  the vault so it isn't needed): Jane's step says "ask for an easier contract (you have no potato cactus seed)". An Easy
  one can't be swapped, so it waits, the Run tab saying so (elzerma, 2026-11-03: "Yes, add it").
- **Second run** (elzerma, 2026-11-02, with crops off the plan picked): the south allotment's watermelons were picked,
  9 went in the compost bin to fill it and the rest were noted, and strawberries were planted with ultracompost; the
  north allotment, the maple and the growing jangerberry were left alone. The flower step (pick the nasturtiums, then
  a white lily) was ticked off, as there was no white lily seed, hence the seed check. Picking the allotment met "You
  need a spade to do that." with the spade stored with the tool leprechaun: the pick step now needs a spade for an
  allotment, so the guide says to take it from him first.
- **Tests:** `ContractPlannerTest` (roles, Jane, crops, the tables), `RunSelectorTest.contractsRunTheGuildsPatchesForThem`.
