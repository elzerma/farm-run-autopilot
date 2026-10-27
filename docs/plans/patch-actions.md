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
Store at the tool leprechaun; drop by default). P2 (cleaning herbs) and P3 (making compost) are still to build.

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
