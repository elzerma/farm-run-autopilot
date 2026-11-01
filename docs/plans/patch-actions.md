# Plan: what's done at the patches (noting, cleaning, compost, buckets, the leprechaun)

Written 2026-10-24, planning only (nothing built). elzerma, after T4's hand checks: "add options for cleaning herbs,
noting herbs, and a compost-making feature where the watermelons from patches are added to compost bin and volcanic ash
is brought along ... saving buckets/dropping buckets, grabbing a certain amount of ultracompost/super/regular compost
from the leprechaun at a planned step rather than letting the user rely on their own steps. there really should always
be a 'Note' step for crops or herbs, I think crops really depend on if they're being used for compost or not. Start some
planning for all of these items and we can evaluate later. Other features at the patches might be good, too."

**P1 built 2026-10-27 (branch `claude/1c-plan-file-name-0cf248`), awaiting elzerma's hand checks:** at each stop every patch is
picked and cleared first (raking, checking health, clearing included), then a Note step while anything notable picked
there is still unnoted in the inventory (produce is whatever was gained since arriving that has a noted form; not weeds
or logs), then a step to take what compost the stop's patches still need from the tool leprechaun (none with a filled
bottomless bucket), then every patch treated and planted. Both steps outline the Tool Leprechaun and stand aside once
the player has started treating or planting. Farm tab "At the patches": Note produce (on), Empty buckets (Drop, Keep,
Store at the tool leprechaun; drop by default). P3 (making compost) is still to build.
**P2 built 2026-10-30** (elzerma, at Catherby: "Cleaning is a step before noting"): Farm > At the patches > Clean herbs
(off by default, as agreed): after picking herbs, "Clean your grimy irit leaf" for the herbs the player's real Herblore
level allows (OSRS Wiki levels, huasca 58 included), before the stop's leprechaun visit and before leaving. Herbs
cleaned after picking count as the stop's produce to note, with the setting on or off (the visit had left them out).
**Changed after elzerma's P1 runs (2026-10-27 to 30):** picking everything first sent them across the Farming Guild
and back, and grouping nearby patches was tried and dropped ("kinda shit"). Taking the stop's compost on arriving was
tried too, but sent them to the leprechaun before picking and again to note (2026-10-30: "it should tell you to pick
stuff first then when you're already going to note produce on the leprechaun it should then tell you to dump
buckets/grab ultracompost"). Now, at every stop, each patch is picked, **planted, then composted** (elzerma: "I have
always planted then composted") and paid for, before the next; between the first patch's picking and its planting comes
the stop's one visit to the tool leprechaun, "At the tool leprechaun: note your ..., store N empty buckets and take N
ultracompost", with whichever parts are needed (not once planting or composting has begun there); before leaving, what
was picked since is noted, with the buckets stored then. With Empty buckets on Store, there's no separate "Store N empty
buckets" line. No "Order at a stop" setting. Noting counts only what came into the inventory while picking, read with
notes as notes. "Inventory full" says what makes room: produce to note, else compost this stop won't use, to store.
"You plant ..." and "You treat the ..." count only for the stop's patches the player is next to, and "You treat the
allotment" goes to the nearer allotment not yet composted.

## 1. What the plugin does at a patch today

- **Steps:** each patch's steps come from `run/StepAdvisor` (harvest, clear, rake, compost, plant, pay, check health)
  in the order `run/PatchGuide` picks, one at a time, each with the items to use outlined (the growing vine).
- **Leprechaun:** a step's item stored at the tool leprechaun is "Take ... from the tool leprechaun" instead of
  missing (`PatchGuide.missingItemReminder`); compost there counts toward the supply list (`Holdings.Source.LEPRECHAUN`).
- **Notes:** only "Inventory full: note produce on the tool leprechaun", said when the inventory fills mid-step.
- **Buckets:** after composting, "Drop N empty buckets" is added to the step text (seen in elzerma's run); the
  bottomless compost bucket is tracked and filled.
- **Banking:** what to deposit at a bank visit (`depositNames`) includes produce, buckets and leftovers.

## 2. The workflow at a stop (elzerma, 2026-10-24, read back and confirmed)

At a stop with allotments and a compost bin:

1. **Arrive.**
2. **Empty the compost bin if it's ready.** Supercompost becomes ultracompost straight away when volcanic ash is added
   to the bin, so with "make ultracompost": add the ash, then take the compost out in the same visit with empty
   buckets or the bottomless compost bucket used on the bin. Buckets of compost go to the tool leprechaun; a
   bottomless bucket keeps its compost.
3. **Refill the bin.** Pick the watermelon allotments first and put the watermelons straight in (supercompost);
   other allotment crops can go in too, but never snape grass (worth more sold or for potions). Spare watermelons
   past the bin's 15 are noted. Close the bin.
4. **Pick the other allotments and note them** on the leprechaun. The flower patch depends: left alone while it's
   still protecting a growing crop, picked and replanted when it's being replanted (for the extra XP).
5. **Pick the herbs, clean them, then note them** on the leprechaun.
6. **Take compost from the leprechaun** for every patch at the stop (not with a bottomless bucket).
7. **Treat and plant every patch:** compost, plant, pay the gardener where needed.

Every patch at the stop is picked and noted before any is composted and planted (the guide today goes patch by
patch). Once at the stop, the steps between its patches, the compost bin and the tool leprechaun need no route from
Shortest Path or the travel atlas, only outlines on what to use next (elzerma, 2026-10-24); so the atlas's patch
order inside a stop, left over from T3, isn't needed either. A stop with herbs but no allotments or compost bin has no compost or allotment steps: pick, clean, note, take
compost, treat and plant.

**Answered:** noting is on by default (a setting); herbs are cleaned at the patch, before noting; every stop with a
compost bin uses it.

### What elzerma did on their run of 2026-10-24 (from the dev log's game chat)

Herbs, allotments and a flower, 10 stops in 34:05. What they did that the plugin doesn't plan or follow:

- **Compost bins at every stop with one:** opened and emptied (supercompost: the Farming Guild's big bin 30 before the
  run, Hosidius, Civitas illa Fortis, Ardougne), refilled with watermelons as they were picked, and closed (Hosidius,
  Civitas illa Fortis, Ardougne, Port Phasmatys, Catherby). No volcanic ash this run: the supercompost went to the
  leprechaun ("Store-All"), and the patches got ultracompost from what they carried.
- **Buckets from the leprechaun:** taken out ("Remove-All") to empty a bin, and stored back; "You haven't got any
  buckets to store" after most stops shows they tried to store buckets each time.
- **Noting while picking:** the inventory filled during almost every allotment harvest ("You have run out of inventory
  space"); they noted at the leprechaun and went back to picking, up to five times at Port Phasmatys. Watermelons went
  into the bin in the same breaks.
- **Cleaning herbs** straight after each herb patch, then noting them.
- **Noting after each patch** as well as at the end of each stop.
- **Treated after planting** at the Farming Guild and Hosidius (planted, then ultracompost), which the game allows.
- **Patch by patch:** each patch was usually picked, treated and planted before the next was started, rather than
  everything picked first (section 2). elzerma (2026-10-24): the guide leads "pick everything first", as section 2 says.
- **The Farming Guild's bin** was filled to "full of supercompostable items" with no "You close the compost bin" after
  it in the log.

## 3. The features asked for

### 3.1 A planned "Note" step

After a stop's last harvest, one step: "Note your herbs on the tool leprechaun" (use the produce on him; he notes it
for free). Always for herbs; for allotment crops, hops and flowers only when they aren't kept for compost (3.3). Ticked
when no unnoted produce of the stop's crops is left in the inventory. Saves slots for the rest of the part, so the
bank planner can count noted produce as one slot per kind rather than its harvest.

- **Questions:** whether the step is a setting (on by default) or always; whether fruit tree and tree produce count
  (fruit can be noted; logs aren't from the patch run); what to do at stops with no leprechaun nearby (**UNVERIFIED**
  which farming areas lack one; most have one).

### 3.2 Cleaning herbs

Option "Clean herbs" (off by default): after harvesting, clean each grimy herb the player's Herblore allows, before the
Note step (noted grimy herbs can't be cleaned). The step lists which herbs are cleanable at their level; the rest stay
grimy. Cleaning takes time (about a tick per herb, **UNVERIFIED**), so the route estimate adds it per herb.

- **Herblore levels to clean** (wiki, to check at the sprint): guam 3, marrentill 5, tarromin 11, harralander 20,
  ranarr 25, toadflax 30, irit 40, avantoe 48, kwuarm 54, snapdragon 59, cadantine 65, lantadyme 67, dwarf weed 70,
  torstol 75.
- **Questions:** clean at the patch (between harvest and note) or only at the end; whether it should be skipped when
  the next leg is a long one (no time to lose) or always done.

### 3.3 Making compost from the run's produce

Option "Make compost" (off by default): crops that make compost go into the compost bin at the stop instead of being
noted, and the bin is collected on a later run.

- **Supercompost** from watermelons (and pineapples, calquat, some roots); **ultracompost** by adding volcanic ash to a
  full bin of supercompost. A bin holds 15 items; ultracompost takes 25 volcanic ash per full bin (**UNVERIFIED**: the
  exact count, and whether it's per bin or per bucket); a closed bin rots in about 45-90 minutes (**UNVERIFIED**).
- **The steps:** fill the bin (15 items), close it, and on a later run open it and take the compost with 15 empty
  buckets (or the bottomless bucket). With ultracompost: add the ash when it's supercompost, before taking it.
- **What it changes:** the supply list brings volcanic ash and, for a collecting run, buckets; produce for the bin isn't
  noted (3.1); the bin's state is a varbit per farming area (RuneLite's Time Tracking tracks compost bins:
  **UNVERIFIED** which varbits), so the plugin can tell a bin is ready to collect.
- **Questions:** which bins (every stop that has one, or only some); whether to fill a bin from one stop's crops or
  carry watermelons to the next bin; whether collecting compost belongs in the run when nothing else is due there.

### 3.4 Buckets: keep or drop

A setting for empty buckets after composting: drop them (as now), keep them (to collect compost, 2.3), or store them
at the tool leprechaun (he holds up to 31, **UNVERIFIED**). With "Make compost" on, buckets are kept as needed for the
bins to collect.

### 3.5 Compost from the leprechaun at a planned step

When compost is stored at the tool leprechaun, the run gets a step "Take 8 ultracompost from the tool leprechaun" at
the first stop with one, for as much as the rest of the part needs, instead of waiting for the compost step to say
so. The supply list already counts what's stored there; the step makes the withdrawal part of the plan, and it frees
bank slots (compost stays with the leprechaun until needed).

- **Questions:** take it all at the first leprechaun, or at each stop only what that stop needs (fewer slots carried,
  more steps); how to handle the leprechaun's limit of one kind of compost per take (**UNVERIFIED**).

## 4. Other things worth doing at the patches (ideas, for elzerma to pick from)

- **Bottomless bucket:** fill it from the leprechaun's or the bin's compost as a planned step when it's low.
- **Plant cure and Resurrect Crops:** a step for a diseased patch with what's carried, rather than skipping it.
- **Leftover seeds:** at the end of a run, a step to put leftover seeds back in the seed vault (Farming Guild) or bank.
- **Fruit:** note fruit tree produce like herbs (3.1). **Yes** (elzerma).
- **Payments:** take the gardener's payment out of the leprechaun's notes? (**UNVERIFIED**: he doesn't store produce.)
- **Seaweed and giant seaweed** (crop types 1b) need the same note and compost choices.

## 5. The banking fixes from the same run (built 2026-10-24)

Re-checked by elzerma the same day: no Missing box named anything just used at a stop (passed), and the seed vault's
emptied slot no longer counts as one of its item (passed: after their last kwuarm seed it said "Take 1 more", as the
game's placeholder was counted). Not tried yet, as neither came up: a planned bank visit clearing an earlier ask, and
banking early moving the planned visit up. Merged to master with those two still to try.

- **Items wrongly asked for:** at Hosidius the run asked for the house tablet that got elzerma there and the snapdragon
  and nasturtium just planted. When a step's item was missing, the check counted the whole stop again; now it counts
  only patches not finished or planted yet, and the way there only if not arrived (the same for "Bank for them"). A
  planned bank visit clears an earlier ask (it was still showing at Varrock after the Nemus Retreat visit).
- **Banking early moves the planned visit up:** "Bank for them" does the next planned visit now when what's left of
  this part and the next fit in the inventory; that visit is then done and isn't made again later.

## 6. Where this could go

The roadmap is T5 (journeys) → crop types 1b → 1c → T9 (elzerma, 2026-10-23). These features change the steps and the
supply list, not travel, so they could run alongside T5, or as their own sprints after 1c:

| Sprint | What | Size |
|---|---|---|
| P1 | The stop workflow of section 2 (everything picked and noted before treating and planting), Note step (3.1), leprechaun compost step (3.5), buckets setting (3.4) | large |
| P2 | Cleaning herbs (3.2) | small |
| P3 | Making compost (3.3), with the bin varbits and a guided test | large |

## 7. Composting, settled (elzerma, 2026-10-24)

- **A bin holds 15 and can't be closed part-full** (the Farming Guild's big bin holds 30 and takes 30 buckets: a
  couple of trips to the tool leprechaun). Every watermelon picked goes in; an allotment of watermelons treated with
  ultracompost usually gives at least 15. Short of a full bin, it's left open to fill on a later run; watermelons are
  never brought from the bank to top it up.
- **Watermelons are the allotment crop that makes supercompost** (snape grass does too, but never goes in). Cabbage,
  potatoes, onions and the like turn a bin of watermelons into regular compost. The plugin doesn't aim at regular
  compost but doesn't stop a player whose progression calls for it: those crops can be ticked for the bin.
- **Compost taken out of a bin:** what this stop's patches need stays in the inventory (e.g. the herb and the two
  allotments) when it's the tier those patches get; the rest goes to the leprechaun.
- **A bin still rotting** takes about as long as herbs or allotments grow, so it's usually ready on the next run;
  when it isn't, the stop has no bin steps and its watermelons are noted.
- **Section 4:** noting fruit tree produce, yes (part of the Note step); the other three ideas, no.

**Answered earlier (elzerma, 2026-10-24):** after crop types 1c (T5 -> 1b -> 1c -> P1 -> P2 -> P3 -> T9); a ready bin
never adds a stop, as the bins use the crops grown in the same run; empty buckets: drop, keep or store at the
leprechaun (a setting, drop by default); noting on by default, cleaning at the patch, every bin used, compost taken
from the leprechaun at each stop for that stop (section 2).

**The settings** (elzerma liked this shape): a new Farm tab section, "At the patches", below "Compost and
cures": Note produce on the tool leprechaun (on), Clean herbs (off), Empty buckets (Drop / Keep / Store at the
leprechaun), and Make compost in the compost bins (off), which when on shows Make ultracompost (brings volcanic
ash) and the crops for the bin (watermelon ticked; snape grass never offered). Taking compost from the leprechaun,
bucket or bottomless bucket, and picking everything before planting need no setting. "Compost and cures" keeps
which compost each patch type gets (say it once).

No questions left; still to check at P3: the ash per bin and the bins' varbits (section 3.3).

## 8. P3 build plan (2026-10-30)

**Checked** (the UNVERIFIED values in section 3.3):
- **The bins and their varbits** (RuneLite's Time Tracking, `FarmingWorld`): Ardougne (region 10548), Catherby
  (11062), Civitas illa Fortis (6192), Falador (12083), Hosidius (6967) and Port Phasmatys (14391) on
  FARMING_TRANSMIT_E; Prifddinas (13151) on FARMING_TRANSMIT_D; the Farming Guild's big bin (4922) on
  FARMING_TRANSMIT_N. The wiki lists the same eight.
- **A bin's states** (RuneLite's `PatchImplementation` COMPOST and BIG_COMPOST): 0 empty; 1-15 compost filling (the
  count); 33-47 supercompost filling; 31-32 and 95-96 rotting; 94 and 126 rotted, still closed; 16-30, 48-62 and
  176-190 compost, supercompost and ultracompost to take out (buckets left = value - 15, - 47 and - 175); 129-160
  tomatoes (rotten tomatoes, not used here). The big bin has its own ranges, up to 30.
- **The wiki:** 15 items to close a bin (30 for the big one), never part-full; it rots in 35-70 minutes; supercompost
  only if every item is supercompostable (watermelons are); 25 volcanic ash per bin of supercompost (50 for the big
  one), added before any is taken out (or 2 per bucket in the inventory); taken out with empty buckets or the
  bottomless bucket, the empty buckets first.
- **The tool leprechaun's empty buckets** can be read (FARMING_TOOLS_BUCKETS with EXTRABUCKETS and EXTRA2BUCKETS).

**At a stop with a bin**, with Make compost on:
1. **The bin is ready** (rotted, or opened with compost left): it's emptied before picking, as the buckets need room:
   at the tool leprechaun, take the empty buckets (none with a bottomless bucket); open the bin; with Make
   ultracompost and supercompost in it, add 25 volcanic ash (50); take the compost out; at the tool leprechaun, store
   it, keeping what this stop's patches need. As elzerma's run of 2026-10-24 did.
2. **Watermelon allotments picked:** "Put your watermelons in the compost bin (N/15)", after each picking and when the
   inventory fills; they aren't noted while the bin has room, and those past its 15 are.
3. **Full:** "Close the compost bin".
4. **Still rotting:** no bin steps; the watermelons are noted.

**Supplies:** 25 volcanic ash (50) for each bin expected ready with supercompost, with Make ultracompost; empty
buckets for each bin to empty when the tool leprechaun holds too few and there's no bottomless bucket.
**Tracking:** each bin's value is read like a patch's while in its area and saved with when it was seen; a bin seen
closed counts as ready 70 minutes after.
**Settings:** Farm > At the patches > Make compost in the compost bins (off); when on, Make ultracompost (brings
volcanic ash) and the crops for the bin (watermelon ticked; snape grass never offered).
**Order of building:** the bins' data and tracking, logged as they change; then the steps; then the supplies.

**Built 2026-10-30** (84bf25d, cb29134, c4ef0a6), not yet tried in game: the eight bins tracked and logged ("Compost
bin at <area>: <state> (value N)"); the steps above, with the bin outlined; Farm > At the patches > Make compost
(off), Make ultracompost (on) and the crops for the bin (watermelon); the ash and buckets on the supply list, the tool
leprechaun's empty buckets read too. UNVERIFIED: the big bin's values 97-99 and 127-128 (no object IDs in RuneLite's
notes), to check from the Farming Guild's log lines.

**First run, Civitas illa Fortis (2026-10-30), from the log:** the bin read right through: rotted supercompost (126),
opened (62, then down to 48 as each bucket was filled), empty (0), filled with watermelons (33 to 47), closed (95). The
Farming Guild's big bin read 97, then 98, while rotting: as decoded (supercompost rotting), with 99 and 127-128 still
unseen. What elzerma asked to change:
- *"Highlight should go to leprechaun to remove buckets and then open and either a) withdraw or b) guide to bring
  volcanic ash and add it to the bin before removing it into buckets since when adding to bin saves volcanic ash vs
  adding it to buckets, also it's faster"*: the bin steps started only once next to a patch, and opened it before the
  buckets. Now, from arriving: the buckets from the tool leprechaun first (he's outlined and the walking line goes to
  him, or to the bin until he's loaded), then open, then the ash when carried (none once any compost is out), then
  take it out, then store it. The ash is no longer optional on the supply list with Make ultracompost on.
- *"when we've begun adding stuff to bin it should keep you at the bin til the bin is full or you're out of eligible
  items to add"* and *"it should guide you to close the bin. finish picking and note produce"*: the step went back to
  picking after the first watermelon went in (the inventory was no longer full) and never said to close it. Now, once
  the bin's count goes up, the step stays on the bin until it's full or the watermelons run out; a full bin is closed
  straight away; then picking, and the visit to the tool leprechaun, carry on.

**Second run, Civitas illa Fortis (2026-10-31), from the log and check screenshots:** the order above ran as built.
Armed at the Civitas bank, the first step was the buckets (taken from the bank, the step counting down from 15 to 5),
then open, then the ash, as the game confirmed ("You apply the volcanic ash to the supercompost in the compost bin. The
supercompost transforms into ultracompost."), 15 ultracompost out and stored; the bin kept at from 1/15 to 15/15, then
"Close the compost bin", then picking, the leprechaun visit, planting and compost, and the run finished. The
screenshots show the bin outlined with the ash, and with every watermelon. Two things fixed after it:
- With the inventory full while picking, the step stayed "Pick the watermelon" for 1:46, the patch outlined, with only
  a reminder under it (elzerma's screenshot). Now making room is the step: the crops for the bin go in it while it has
  room, else the produce is cleaned and noted with the tool leprechaun, each with what to use outlined.
- The timer started only at the first patch, so the bin's 2.5 minutes weren't timed and the walking line (drawn only
  once timing) wasn't there for the walk from the bank. Now a click on the first stop's compost bin or tool leprechaun
  starts it too, as one on a patch or gardener does.

**Third run, Hosidius (2026-10-31), from the log:** by the Hosidius portal, with 15 buckets from the bank: open, the
ash, 15 ultracompost out (the walking line to the bin, then to the leprechaun to store 13, keeping 2), pick, "Put your
watermelon in the compost bin (0/15)" as soon as the patch was picked, kept at to 15/15, "Close the compost bin";
the run finished in 10:31. Two things fixed after it:
- After closing the bin, the guide sent elzerma from it across the farm to the tool leprechaun to note 2 watermelons
  before planting, with the stop's ultracompost carried from the bin and 22 slots free (elzerma's screenshot). The
  visit before planting is now made only when it's needed: compost to take from him, or too little room for what the
  stop's other patches will bring in (8 a herb patch or allotment, as the inventory-full reminder reckons). Otherwise
  noting waits for the visit before leaving, and a full inventory while picking makes noting the step.
- The visit before leaving said "note your watermelon and store 2 empty buckets", but the stop counted as done at the
  noting, so the run finished with the buckets carried. Now the buckets stay in the visit until stored.

**Fourth run, Port Phasmatys (2026-10-31), from the log:** the build for the two fixes above broke the guide's
bookkeeping (the check for the buckets went in the middle of the chain that credits what the last step brought in):
the compost taken out of the bin went uncounted, so there was no step to store it, and the picking went uncounted
("Inventory full: unnoted produce none" with an inventory of watermelons). Fixed at once. The run also showed that the
game takes the volcanic ash after some compost is out: elzerma took 2 supercompost out, then added the ash, and the 13
left became ultracompost for the full 25. The ash step now stays while the bin's way takes no more ash than the
buckets' (2 a bucket): 13 or more left, 25 or more in the big bin.

**Fifth run, the Farming Guild's big bin (2026-10-31), from the log and elzerma's screenshots:** 25 buckets from the
bank ("take 30" didn't fit), open, 50 ash, ultracompost (205, 30 of it): the big bin's values read as decoded. Fixed
after it:
- After the ash, with 25 buckets in hand and room for 1, it said to fetch 5 from the leprechaun. Now, once the bin is
  open, the buckets in hand are used first, and the rest fetched while storing what's out; and the buckets asked for
  are no more than fit.
- The 5 taken out while that step showed went uncounted, so it said to store 21 of 26 with 2 needed. Compost taken out
  now counts whichever of the bin's steps is showing.
- The walking line for the leprechaun's steps went to the bin: the guild has three tool leprechauns in view, and only
  the last one loaded was kept. Every one is kept now; the line goes to the one nearest the bin or patch, the outline
  to the one nearest the player.
- After the last 4 came out, it sent elzerma across the guild to store 2 with 22 slots free (who stored all 4, then
  took 2 back). The bin's spare compost is now stored only when the room's needed for what the stop's patches will
  bring in; otherwise it's carried on, for later stops.

**Sixth run, the Farming Guild again (2026-10-31):** the fixes above held: the 25 buckets in hand used first, then
"store 23 ultracompost and take 5 empty buckets" (the count right), the walking line to the guild's own leprechaun. But
the room check for the spare compost came up mid-picking, as the free slots fell below what the patches would bring in:
"store 1 ultracompost", later "and 1 empty bucket". elzerma: *"Don't need to store compost til we have a second reason
to go to leprechaun"*. So the bin's spare compost no longer has a step of its own: it's stored on a visit made anyway
(noting, taking compost, fetching buckets for the bin), or when the inventory is full and storing makes room.

### 8.1 Trips to the tool leprechaun (2026-10-31)

elzerma: *"can check any instances where we would go to tool leprechaun and work through the logic of when it would be
appropriate? like we want to save trips to the leprechaun because it can be a time waste to run back and forth several
times if you don't NEED to run back and forth"*. The rule: a trip is made only for something that can't wait (what the
next steps need, or room to go on picking), and every trip does everything else that's due while there: noting, storing
the bin's spare compost and the empty buckets (Empty buckets: Store). What's left comes along to the next stop, whose
own visit, or a full inventory, takes care of it.

| Trip | Made when | Was |
|---|---|---|
| Empty buckets for a compost bin | none carried (what's carried is used first, the rest fetched while storing what's out) | also to top up before opening, a trip more when they didn't all fit |
| Before planting | compost, or the seed dibber or spade, to take for the stop's planting, or too little room for its other patches (19 an allotment, 8 a herb patch) | the dibber or spade was a trip of its own as planting began |
| Inventory full while picking | the compost bin can't take the crops | (2026-10-31) |
| After emptying a compost bin | its compost past what this stop and the stops ahead in reach use, before picking starts there (elzerma: *"we should do a trip to leprechaun to store the ultracompost before harvesting watermelons and filling the bin"*) | stored only on a visit made anyway, so at Hosidius it waited for the inventory to fill mid-picking |
| Before leaving the stop | too little room for what the next stop brings in; or empty buckets for the next stop's compost bin when it's expected ready, keeping those carried (elzerma, leaving Hosidius: *"2x ultracompost were used so we should pull 13 out for the next stop"*); never before a bank visit or at the run's end | every stop, to note what was picked; the 2 empty buckets were stored there, then 15 taken at Port Phasmatys |

**The order of the patches at a bin stop** (elzerma, 2026-10-31, at Port Phasmatys: *"we should harvest the allotment
that is closest to the bin so then we have shorter trips between allotment and bin"*, then *"no it should be patch that
is closest to leprechaun"*): while the bin is to be filled, the patches whose crops go in it come first, nearest the
stop's tool leprechaun first (the bin while he isn't loaded). The guide had gone to the north-west allotment, across the
farm, as the bin's steps had named it, the stop's first patch, and the patch named stays first.

**When the bin is filled** (elzerma, 2026-10-31, at Port Phasmatys, sent to the bin with 12 after the first allotment
and needing 3 more: *"I think it should have told me to plant and compost before putting in bin, then proceed to pick
other patch and once the patch is empty or inv is full, dump into bin"*): the crops go in once none are left to pick at
the stop, or when the inventory is full while picking; each allotment is planted and composted as it's picked. So:
pick the first, plant and compost it, pick the second, and once it's empty, into the bin (closed once full), then plant
and compost it.

Still a trip of its own: a step's item that's with the leprechaun but not carried, found only at that step (a rake for
weeds, a spade for a dead plant, a plant cure), as these can't be known ahead.

**Compost for later stops** (asked: "On a visit, take compost for the next stops too, when there's room?"; elzerma:
*"Yes, when there's room"*). Any visit made anyway leaves carried what this stop's patches still need and, stop by stop
along the route, what the next stops' need, while the free slots left still hold what this stop and those stops will
bring in (19 an allotment, 8 a herb patch: the room checks' estimate). It stops at a bank visit, the run's end, or a stop
whose compost bin is expected ready, as that bin supplies the compost from there. Compost that came out of this stop's
bin is kept for those stops before any is stored; what was brought from the bank is never stored. A stop whose compost
is carried needs no visit for it, so its visit happens only for room. The log line `Compost for the visit: ...` shows
each decision.

**The room checks' estimate** (2026-10-31): an allotment counted as 8, as a herb patch does, but elzerma's watermelon
allotments with ultracompost gave 13 to 27, 19 on average, over the runs of 2026-10-30 and 31, so the checks thought
there was room when there wasn't, and the inventory filled mid-picking instead. Asked "Count an allotment as about 19
watermelons for the room checks, not 8?", elzerma: *"Yes, use 19"*. It's `SupplyCalculator.ALLOTMENT_HARVEST`.

**The tool leprechaun's window** (elzerma, 2026-11-01: *"highlights in leprechaun menu with rt click items
highlighted?"*, then *"typing 13 isn't very convenient"*, and on how: *"guide changing the quantity buttons during the
run. e.g. 13 would change to 5, 2x click bucket remove, change to 1, 3x click bucket remove. change back to 5 ... counts
shown on top of highlighted button and updated as quickly as possible"*). The plan: `C:\Users\Sean\.claude\plans\
memoized-leaping-hammock.md`.
- **His window, researched** from the game's client scripts (github.com/Joshua-F/osrs-dumps): his store (interface 125)
  has Remove-1/5/X/All, the inventory side (126) Store-1/5/X/All, the selected quantity as the left click. Varbit
  FARMING_TOOLS_SELECTEDQUANTITY (7792): 0 = 1, 1 = 5, 2 = All, 3 = X, set by the client as the button's clicked, shared
  with other storage windows. X isn't remembered. Deposit inventory stores tools too.
- **Exact amounts, guided** (`run/LeprechaunClicks`): the moves at his quantity first, for every item, then the button
  for the others, and his quantity left as it ends for the next visit (elzerma, 2026-11-01: *"if it needs to switch
  between 1/5 can it do that at the next time the interface opens ... e.g. put in 13 compost: 5, 5, 1, 1, 1, then
  pulling buckets for next bin 1,1,1,5,5. not hardcoded, just do the needed action with currently configured # then
  leave it til it needs to be switched"*): at 5, the fives then 1 for what's left; at 1, what's past the fives then 5;
  one switch a visit at most. The quantity button to click is outlined, "Click" (`LeprechaunButtonOverlay`); then each
  item, "Click 2x" (`ItemHighlightOverlay`). A click counts at once, before the game takes the items a tick later, so the
  count drops on the click and the outline moves on to the button the moment the last of a kind's clicked; one not seen
  applied in 3 ticks
  is dropped. A right-click colours the "-5" or "-1" option; "Exchange" on him is coloured for a visit with window work.
- **Seen in game** (elzerma's run of 2026-11-01, at Hosidius: "i like the leprechaun stuff"): the menus read Remove-All,
  Remove-X, Remove-1, Remove-5 (+ Banknotes) on his side and Store-5, Store-X, Store-1, Store-All on the inventory side;
  the check screenshots show the 5 button outlined, "Click", at quantity 1 with 13 to store, and the 1 button with 3
  left at quantity 5. The dev client still logs `Leprechaun window opened at <stop>: take {...}, store {...}` and takes a
  check screenshot as the window opens during a visit.
- **The developer client froze** on that run, three seconds after "note your watermelon" at Civitas: an assertion in
  RuneLite's own GPU plugin (`ModelUploader.uploadSortedModel`, a model's depth sort), which only the developer client
  checks (`./gradlew run` passes `-ea`). The run task now leaves assertions off for `net.runelite.client.plugins.gpu`.
