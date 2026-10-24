# Plan: what's done at the patches (noting, cleaning, compost, buckets, the leprechaun)

Written 2026-10-24, planning only (nothing built). elzerma, after T4's hand checks: "add options for cleaning herbs,
noting herbs, and a compost-making feature where the watermelons from patches are added to compost bin and volcanic ash
is brought along ... saving buckets/dropping buckets, grabbing a certain amount of ultracompost/super/regular compost
from the leprechaun at a planned step rather than letting the user rely on their own steps. there really should always
be a 'Note' step for crops or herbs, I think crops really depend on if they're being used for compost or not. Start some
planning for all of these items and we can evaluate later. Other features at the patches might be good, too."

Nothing here starts until elzerma has looked it over and answered section 6.

## 1. What the plugin does at a patch today

- **Steps:** each patch's steps come from `run/StepAdvisor` (harvest, clear, rake, compost, plant, pay, check health)
  in the order `run/PatchGuide` picks, one at a time, each with the items to use outlined (the growing vine).
- **Leprechaun:** a step's item stored at the tool leprechaun is "Take ... from the tool leprechaun" instead of
  missing (`PatchGuide.missingItemReminder`); compost there counts toward the supply list (`Holdings.Source.LEPRECHAUN`).
- **Notes:** only "Inventory full: note produce on the tool leprechaun", said when the inventory fills mid-step.
- **Buckets:** after composting, "Drop N empty buckets" is added to the step text (seen in elzerma's run); the
  bottomless compost bucket is tracked and filled.
- **Banking:** what to deposit at a bank visit (`depositNames`) includes produce, buckets and leftovers.

## 2. The features asked for

### 2.1 A planned "Note" step

After a stop's last harvest, one step: "Note your herbs on the tool leprechaun" (use the produce on him; he notes it
for free). Always for herbs; for allotment crops, hops and flowers only when they aren't kept for compost (2.3). Ticked
when no unnoted produce of the stop's crops is left in the inventory. Saves slots for the rest of the part, so the
bank planner can count noted produce as one slot per kind rather than its harvest.

- **Questions:** whether the step is a setting (on by default) or always; whether fruit tree and tree produce count
  (fruit can be noted; logs aren't from the patch run); what to do at stops with no leprechaun nearby (**UNVERIFIED**
  which farming areas lack one; most have one).

### 2.2 Cleaning herbs

Option "Clean herbs" (off by default): after harvesting, clean each grimy herb the player's Herblore allows, before the
Note step (noted grimy herbs can't be cleaned). The step lists which herbs are cleanable at their level; the rest stay
grimy. Cleaning takes time (about a tick per herb, **UNVERIFIED**), so the route estimate adds it per herb.

- **Herblore levels to clean** (wiki, to check at the sprint): guam 3, marrentill 5, tarromin 11, harralander 20,
  ranarr 25, toadflax 30, irit 40, avantoe 48, kwuarm 54, snapdragon 59, cadantine 65, lantadyme 67, dwarf weed 70,
  torstol 75.
- **Questions:** clean at the patch (between harvest and note) or only at the end; whether it should be skipped when
  the next leg is a long one (no time to lose) or always done.

### 2.3 Making compost from the run's produce

Option "Make compost" (off by default): crops that make compost go into the compost bin at the stop instead of being
noted, and the bin is collected on a later run.

- **Supercompost** from watermelons (and pineapples, calquat, some roots); **ultracompost** by adding volcanic ash to a
  full bin of supercompost. A bin holds 15 items; ultracompost takes 25 volcanic ash per full bin (**UNVERIFIED**: the
  exact count, and whether it's per bin or per bucket); a closed bin rots in about 45-90 minutes (**UNVERIFIED**).
- **The steps:** fill the bin (15 items), close it, and on a later run open it and take the compost with 15 empty
  buckets (or the bottomless bucket). With ultracompost: add the ash when it's supercompost, before taking it.
- **What it changes:** the supply list brings volcanic ash and, for a collecting run, buckets; produce for the bin isn't
  noted (2.1); the bin's state is a varbit per farming area (RuneLite's Time Tracking tracks compost bins:
  **UNVERIFIED** which varbits), so the plugin can tell a bin is ready to collect.
- **Questions:** which bins (every stop that has one, or only some); whether to fill a bin from one stop's crops or
  carry watermelons to the next bin; whether collecting compost belongs in the run when nothing else is due there.

### 2.4 Buckets: keep or drop

A setting for empty buckets after composting: drop them (as now), keep them (to collect compost, 2.3), or store them
at the tool leprechaun (he holds up to 31, **UNVERIFIED**). With "Make compost" on, buckets are kept as needed for the
bins to collect.

### 2.5 Compost from the leprechaun at a planned step

When compost is stored at the tool leprechaun, the run gets a step "Take 8 ultracompost from the tool leprechaun" at
the first stop with one, for as much as the rest of the part needs, instead of waiting for the compost step to say
so. The supply list already counts what's stored there; the step makes the withdrawal part of the plan, and it frees
bank slots (compost stays with the leprechaun until needed).

- **Questions:** take it all at the first leprechaun, or at each stop only what that stop needs (fewer slots carried,
  more steps); how to handle the leprechaun's limit of one kind of compost per take (**UNVERIFIED**).

## 3. Other things worth doing at the patches (ideas, for elzerma to pick from)

- **Bottomless bucket:** fill it from the leprechaun's or the bin's compost as a planned step when it's low.
- **Plant cure and Resurrect Crops:** a step for a diseased patch with what's carried, rather than skipping it.
- **Leftover seeds:** at the end of a run, a step to put leftover seeds back in the seed vault (Farming Guild) or bank.
- **Fruit:** note fruit tree produce like herbs (2.1).
- **Payments:** take the gardener's payment out of the leprechaun's notes? (**UNVERIFIED**: he doesn't store produce.)
- **Seaweed and giant seaweed** (crop types 1b) need the same note and compost choices.

## 4. The banking fixes from the same run (built 2026-10-24)

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

## 5. Where this could go

The roadmap is T5 (journeys) → crop types 1b → 1c → T9 (elzerma, 2026-10-23). These features change the steps and the
supply list, not travel, so they could run alongside T5, or as their own sprints after 1c:

| Sprint | What | Size |
|---|---|---|
| P1 | Note step (2.1), leprechaun compost step (2.5), buckets setting (2.4) | medium |
| P2 | Cleaning herbs (2.2) | small |
| P3 | Making compost (2.3), with the bin varbits and a guided test | large |

## 6. Open questions for elzerma

1. Is P1-P3 the right split, and where do they go: before 1b, between 1b and 1c, or after 1c?
2. The Note step: a setting on by default, or always?
3. Cleaning herbs: at the patch (before noting), or once at the end of the run?
4. Making compost: every stop with a bin, or bins you choose? And should a bin ready to collect add a stop to the run
   on its own?
5. Empty buckets: drop by default (as now), keep, or store at the leprechaun?
6. Leprechaun compost: all at the first leprechaun, or each stop only its own?
7. Which of section 3's ideas are worth planning?
