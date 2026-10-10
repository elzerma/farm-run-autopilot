# Plan: teleport preferences ("Prefer free" and friends)

Status: planning only, nothing built. For `feature/fairy-ring-access` (not the issue-fix release).

## Goal

Let the player say *how* they'd like to travel, not just *where to*: save gold and inventory space by using free teleports (portal nexus with a construction or max cape, unlimited capes and items, spirit trees, fairy rings), or stick to runes or tablets, or just go fastest. A default for every stop, plus clearly labelled overrides per location.

## Agreed so far (2026-10-10)

- **One dropdown, "Teleports (default for every stop)"**, in Setup > Run options. It replaces "Runes instead of tablets everywhere".
  - Choices: **Prefer free** (default), Prefer tablets, Prefer runes, Prefer nexus, Fastest.
- **A second dropdown under every location** in Rules > Travel. It replaces "Runes instead of tabs here".
  - First choice: **"Use default (Prefer free)"**. Anything else shows as **"Override: Prefer runes"** and so on.
  - The Travel section gets a note: "The second dropdown under each location overrides the default in Setup > Run options."
- **They're preferences, not rules.** When the preferred way can't be used, the next best is taken, as with the fairy ring dropdown and the issue #2 fix.
- **"Prefer walking" stays its own setting.**
- **Migration:** "Runes instead of tablets" ticked becomes Prefer runes; never touched becomes Prefer free. The per-location ticks become location overrides set to Prefer runes. The note on this goes in the release notes.

## How it works today

- `RoutePlanner.options()` already offers every way of using a method:
  - directly;
  - through the house via the portal nexus or jewellery box (`Departure.POH_NEXUS`, `POH_JEWELLERY_BOX`);
  - the house fairy ring or spirit tree.
- `bestLeg()` picks the cheapest by **time** only. The one exception is `teleportPreference()`, which adds 20 s to every teleport when Prefer walking is on (`RoutePlanner.PREFER_WALKING_SECONDS`).
- **Runes or tablets is decided later**, in `SupplyCalculator.addSpell()`, from `RunConfig.useRunesAt(location)`. Today the only rule is "tablet if you own one and runes aren't preferred".
- **What it costs isn't modelled.** A free cape teleport and a tablet used up count the same if they take the same time.

## Proposed model

Give every leg a **cost class**, then let the preference add a small time penalty to the classes it doesn't favour, the same trick Prefer walking uses. The fastest leg still wins unless the favoured one is within the penalty.

| Class | Examples |
|---|---|
| FREE | Walk, spirit tree, fairy ring (house or reached free), nexus or jewellery box when Teleport to House is a construction or max cape, unlimited items (see table), mounted Xeric's talisman (nexus) |
| HOUSE | Nexus or jewellery box when Teleport to House uses a tablet or runes (one house tab instead of a per-stop tab) |
| CHARGES | Charged jewellery (games necklace, skills necklace, ring of wealth, glory, combat bracelet, slayer ring), Xeric's talisman, Kharedst's memoirs or Book of the dead, quetzal whistle, pendant of Ates, teleport crystal, daily-limited uses |
| TABLET | A spell taken from a tablet |
| RUNES | A spell cast from runes |
| CONSUMED | Single-use items: stony and icy basalt |

Penalty per preference, using a starting value of 15 s to tune in game:

| Preference | No penalty | +15 s |
|---|---|---|
| Prefer free | FREE | everything else |
| Prefer nexus | FREE and HOUSE nexus or jewellery box legs | everything else |
| Prefer tablets | TABLET, FREE | RUNES |
| Prefer runes | RUNES, FREE | TABLET |
| Fastest | everything | nothing |

- **Runes versus tablets stays a supply-list decision.** `addSpell()` reads the stop's effective preference instead of `useRunesAt()`. A spell you can't cast still always asks for the tablet (issue #2).
- **The leg's class is worked out from what you own.** The planner already has `Holdings`, so it can tell, for example, an eternal glory (FREE) from a glory(4) (CHARGES).

## Item data needed

The "free" checks below are from the wiki (checked 2026-10-10). Each needs its own item IDs, because one `TravelItem` groups charged and unlimited versions.

| Item | Free? | Wiki says |
|---|---|---|
| Construction cape | Yes | Unlimited teleports to house and house portals |
| Farming cape, Max cape, Achievement diary cape | Yes (to check per destination) | Skill and diary capes |
| Hunter cape | Hunter Guild only | Feldip and Wilderness: 5 a day; Hunter Guild unlimited |
| Ardougne cloak 1–4: Monastery | Yes | Unlimited (all tiers) |
| Ardougne cloak 2: farm | No | 3 a day |
| Ardougne cloak 3: farm | No | 5 a day |
| Ardougne cloak 4: farm | Yes | Unlimited |
| Explorer's ring 2: cabbage patch | No | 3 a day |
| Explorer's ring 3: cabbage patch | Unknown | Wiki page didn't say; treat as not free |
| Explorer's ring 4: cabbage patch | Yes | Unlimited |
| Rada's blessing 3: Mount Karuulm | No | 3 a day (Kourend Woodland unlimited) |
| Rada's blessing 4 | Yes | Both unlimited |
| Xeric's talisman | No, unless mounted | Lizardman fang charges; mounted in the nexus is unlimited |
| Book of the dead / Kharedst's memoirs | No | Charges, up to 250 |
| Ectophial | Yes | Refills itself |
| Royal seed pod | Yes | Unlimited |
| Quetzal whistle | Only perfected (i) | Perfected holds 50 charges; (i) is unlimited |
| Pendant of Ates | No | Frozen tear charges, up to 1,000 |
| Teleport crystal | Only the eternal one | Charged versions run out |
| Stony and icy basalt | No (CONSUMED) | Used up on teleport |
| Amulet of glory | Only eternal | Charged versions run out |
| Slayer ring | Only eternal | Charged versions run out |

Anything not confirmed counts as **not free**, so the plugin never plans around a free teleport that turns out to run out.

**Possible follow-up:** daily limits (cloak 2 or 3, Explorer's 2, Rada's 3) could be tracked from the game's "you have N teleports left today" messages. That's out of scope here.

## Changes (rough)

- `settings/TeleportPreference.java` (new enum): `FREE`, `TABLETS`, `RUNES`, `NEXUS`, `FASTEST`.
- `settings/RunConfig.java`:
  - Add `teleportPreference` (default `FREE`) and `Map<Location, TeleportPreference> teleportOverrides`.
  - `sanitise()` migrates `useRunesNotTabs` and `runesNotTabsAt`, then clears them.
  - Add a `preferenceAt(location)` helper.
- `data/travel/TravelItem.java`: mark which item IDs are unlimited (a `freeItemIds` set, or an enum per variant).
- `route/RoutePlanner.java`:
  - Work out each leg's cost class from method, departure and holdings.
  - Add the preference penalty next to `teleportPreference()`.
- `supply/SupplyCalculator.java`: `addSpell()` uses `preferenceAt(location)`.
- `ui/SetupPanel.java`:
  - Run options: the Teleports dropdown.
  - Travel: an override dropdown under every location, with labels and a section note.
  - Prefer nexus is greyed out without a nexus destination in My POH.
- Tests: migration, cost class per item variant, penalty ordering, and the issue #2 fallback still holding.

## Questions for Sean

1. **Prefer nexus:** should "a house tab plus nexus" count as preferred, or only the free version (with a construction or max cape)? Right now the plan says both.
2. **Penalty size:** 15 s per leg, compared with Prefer walking's 20 s? We can tune it in game against your learned times.
3. **Learned times:** routes chosen by preference record times too. That's fine, just noting it.
4. **Presets:** the preference is part of the run settings, so each preset can have its own. OK?
5. **Charged jewellery:** "Prefer free" treats a games necklace as not free (it runs out). Do you agree, or should charged jewellery you own count as free?

## Checking it in game (once built)

1. With a construction cape and Catherby on the nexus, Prefer free plans the Catherby leg through the nexus. Fastest casts Camelot or Catherby directly.
2. Prefer runes asks for runes, not tablets, at stops without an override. A location set to "Override: Prefer tablets" asks for its tablet.
3. Someone who had "Runes instead of tablets" ticked sees Prefer runes after updating.
4. Without a house, Prefer nexus is greyed out and Prefer free falls back to tablets or runes.
