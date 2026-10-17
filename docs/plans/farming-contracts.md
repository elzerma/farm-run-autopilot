# Explore: Farming Guild contracts

**Status:** explore write-up, no code (2026-10-17). Nothing here is planned until elzerma picks an option.
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
  probably contracts done). Both meanings are **UNVERIFIED**.

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
(a) and (b) first.

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
