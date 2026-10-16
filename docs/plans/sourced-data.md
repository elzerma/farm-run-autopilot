# Plan: Sprint 9, source what we can, crowdsource only what we can't

**Sprint order (elzerma):** 7 UI facelift (in progress), 8 Shortest Path data, **9 this (sourced data and
crowdsourcing)**, **10 tidy and release** (last, only once everything works). First step: renumber in
`docs/REBUILD.md` (tidy and release → Sprint 10, this plan added as Sprint 9, the "merges into master in
Sprint 9" line → Sprint 10), `docs/plans/shortest-path-research.md` if it names them, and memory
`rebuild-plan.md`; commit and push. The rest is built when Sprint 9 comes up (after 7 and 8), unless elzerma
says to do the two bug fixes (Karamja diary, bottomless bucket) sooner.

## Context

elzerma asked what data players should help with, then whether any of it can come from the wiki instead. Looking
it up (read-only) answered most of the guesses (UNVERIFIED in the code) without players, from three sources:

- **The game's own client scripts** (cs2, published as runelite/cs2-scripts): authoritative for varbit meanings.
- **Shortest Path's data** (BSD-2, already planned for Sprint 8 with credit): teleport landings, boats, POH
  portals, item IDs.
- **RuneLite's own code and the wiki**: object/item naming and order.

Two of the guesses turned out **wrong**, so this is also a bug fix. Players are then only needed for things
no source can tell us, plus real run times (elzerma chose an opt-in GitHub report for those).

## 1. Answered by sources: fix the code, drop UNVERIFIED (no players needed)

| Guess (file) | Source says | Change |
|---|---|---|
| Karamja easy/med/hard done when non-zero (`data/AchievementDiary.java`) | game script `area_task_complete`: `atjun_*_done = 2` means complete | **Bug**: complete only at 2 (1 is likely "started"); per-tier "done value" in AchievementDiary |
| Bottomless bucket type 1/2/3 = compost/super/ultra (`settings/Compost.java`) | game script `farming_tools_getstored`: 1 = empty bucket stored, >1 = filled | **Bug**: 1 means empty; filled values' compost type still unknown → one small player test (below) |
| Leprechaun counts `base + (extra << width)` (`supply/LeprechaunItem.java`) | same script: tools `base + 2*extra`, compost/super `base + 256*extra`, ultracompost and plant cure single | use the script's fixed multipliers per item instead of reading varbit widths |
| Statues of Ates varbits non-zero = activated (`data/Unlock.java`) | game script `pendant_of_ates_teleport_unlocked`: `= 1` means unlocked | mark both `confirmed` (they move to Detected); remove `ATES_STATUES` test item |
| Trollheim tablet lands where the spell does (`data/travel/Spell.java`) | Shortest Path: tablet 11747 and the spell both land at 2890, 3679 | remove UNVERIFIED |
| Eternal teleport crystal is `PRIF_TELEPORT_CRYSTAL` (`data/travel/TravelItem.java`) | Shortest Path: eternal crystal = item 23946 = `PRIF_TELEPORT_CRYSTAL` | remove UNVERIFIED |
| Captain Barnaby's spot ~2683, 3272 (`TravelMethod.ARDOUGNE_TELEPORT_AND_BOAT`) | Shortest Path ships: boat from 2683, 3271 | fix to 2683, 3271 |
| Nexus Troll Stronghold portal lands at the basalt entrance (`TravelMethod.STONY_BASALT_ENTRANCE`) | Shortest Path POH portals: lands at 2845, 3694 | set the landing point; check the journey step still fits |
| Fancy/ornate pools are "recovery"/"regeneration" (`access/PohDetector.java`) | RuneLite's POH plugin lists the five pools in tier order; wiki tiers match | keep; confirm the two object IDs on the wiki pages, then drop UNVERIFIED |

Left as is: fruit regrow 45 min (`data/Crop.java`): the wiki says 40, RuneLite's farming tracker uses 45, and the
plugin's timers come from RuneLite's, so it stays consistent with them.

Credits: add Shortest Path to the README's credits now (it's used here, ahead of Sprint 8); the cs2 scripts are
the game's own data.

## 2. Still needs players (the help list)

Behaviour no source can show stays on the list: Spellbook Swap, Weiss fire, Civitas + Colosseum, Pendant of
Ates charges, Xeric's talisman charges, Kharyrll highlight, planted spirit trees, house scanning.
`ATES_STATUES` is removed (answered above).

New, one small test:
- `BUCKET_COMPOST` (Farm): "Store your bottomless bucket with the tool leprechaun. Which compost is in it?"
  (cannotTest without one) → captures the bucket varbit value with their answer, so 2/3/4 can be mapped.

## 3. Layout of Settings > Help improve this plugin (elzerma)

Two features, clearly divided, under one shared explanation:

```
Help improve this plugin
  [shared] Everything here ends the same way: a GitHub issue opens in your browser with the details filled in.
           You read it, and nothing is sent unless you submit it (a free GitHub account is needed). Never your
           name, account or where you've been.
  ── Share your run times ─────────────────────────────
  [own explanation] Your measured times between stops help set better estimates for everyone.
  [Share your run times]      (greyed until you've done a run, with why on hover)
  ── Guided tests ─────────────────────────────────────
  [own explanation] Features I couldn't try myself. Each test walks you through it on the Run tab: it sets
  what it needs (your settings come back after), lists anything to bring, and asks Yes/No as you go. If you
  don't have what a test needs, it says so and stops; nothing is reported. A few minutes each, no coding.
  ...the test list as now, by tab
```

- Share your run times goes **first**; guided tests second, with the extra explanation the tests need.
- Each part is its own sub-heading (the existing `subheader` style with a divider line above), so they read
  as separate features.
- The shared text and each part's explanation are visible lines here (not tooltips): this is the place
  people come to read them.
- Implemented in `ui/SettingsPage.testingSection()` and `ui/HelpWanted.list(...)` (which currently builds the
  heading and test list; the heading/intro move to SettingsPage, HelpWanted keeps the list).

### Guided tests brought in line with Sprint 7 (elzerma asked whether they match: they don't)

Today `HelpWanted.list` is a long text page: full how-to paragraphs in the larger font, a second
"I NEED YOUR HELP!" heading inside the already-titled section, plain "Run tab"/"Farm tab" labels, unstyled
Start test / Open GitHub issues buttons, "Needs: …" both on the button's hover and as a line under it, and a
plain greyed Done list. Redesign, reusing the Sprint 7 parts:

- **No inner heading**: the section title is the heading; `HOW_IT_WORKS` becomes the guided-tests explanation
  in section 3 (shortened).
- **Groups by tab** with the timer-style headers (`RunPanel.timerGroupHeader` pattern, chevron + "3 to try"),
  remembered open/closed via `settings.isSectionOpen`.
- **Each test is one line** (`ExpandableRow`): title on the left, on the right either "Start" or what it needs
  ("Needs Lunar spellbook", short); hover shows the full how-to; opening it shows the how-to text in the small
  font and a **Start test** button (`Style.primary`, greyed when something's missing). "Needs" said once, on
  the row.
- **Done**: one closed group "Done (n)" with greyed one-line rows ("Weiss fire: passed"); in the dev client each
  has Run again (`Style.button`).
- **Open GitHub issues** becomes a small underlined link at the end (like the help line), not a button.
- **Run tab test box** (`RunPanel.addTest`): keep the orange-bar card; title white like other titles (orange
  only as the bar); Yes/No/Skip/Cancel already `Style.button`; text in the small font.
- All text white, small font, square corners, the same padding as the rest (Card, `Style`).

## 4. Share your run times (opt-in)

- The **Share your run times** button (section 3) opens a prefilled GitHub issue; nothing is sent unless the
  player submits it. Built with `TestReport.url(title, body)` (`testing/TestReport.java`, 6000-char cap).
- Body: plugin version, then one line per learned leg from `route/LearnedTimes` (`from > to, method,
  departure: median s (n)`), plus the unlocks that change times (fairy rings, spirit trees, diary tiers,
  Graceful). No name, account or location history. The button's tooltip says exactly that.
- Their medians later become defaults in `data/travel/TravelTimes.java` (with Sprint 8's measured walks).

## Files

- `data/AchievementDiary.java`, `settings/Compost.java`, `supply/LeprechaunItem.java` + `supply/HoldingsTracker.java`,
  `data/Unlock.java`, `data/travel/{Spell,TravelItem,TravelMethod}.java`, `access/PohDetector.java`
- `testing/TestItem.java`, `testing/GuidedTests.java` (remove ATES_STATUES, add BUCKET_COMPOST)
- `ui/SettingsPage.java` (shared intro, two divided parts, the button), `ui/HelpWanted.java` (list only,
  redesigned as above), `ui/RunPanel.java` (test box title colour, small font),
  `route/LearnedTimes.java` (summary), README credits
- Now (before Sprint 9 is built): copy this revised plan over `docs/plans/sourced-data.md` and commit.
- Unit tests: Karamja done only at 2; bucket 1 = empty; leprechaun totals with the script's multipliers;
  run-times summary format and cap.

## Verification

- `./gradlew compileJava test`.
- Dev client: Settings > Debug > Detected shows Karamja tiers right for elzerma's account and the statues of Ates
  (if they have them); leprechaun counts match the leprechaun's own window; an empty stored bucket shows as empty.
- Help list: Statues of Ates gone; Bottomless bucket test asks first and captures the value.
- Share my run times opens a GitHub issue page with the summary.
- Report the list changes (removed ATES_STATUES, added BUCKET_COMPOST) per CLAUDE.md.
