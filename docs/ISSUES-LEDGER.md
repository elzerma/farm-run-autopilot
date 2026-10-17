# Issue ledger

What was said in each issue, how it was judged, and what elzerma decided. The process is in [ISSUES.md](ISSUES.md). Look up an issue by searching for `#N`.

Each entry has: **Kind**, **Status**, **Reviewed through** (date and author of the last comment read), the comment table, and the decisions.

Issues #1 to #5 were answered and closed on 2026-10-15, before this ledger began.

## #5 preparation tab from last update gone (closed)

**Kind:** Question · **Status:** closed 2026-10-15 · **Reviewed through:** 2026-10-16 elzerma

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| frazzorr | The bank's farming icon is not very obvious | `bank/FarmBankTabButton.java` | It already flashes while the run needs the bank (201a725). A stronger cue may still help new players. | **Explore**: a coloured outline on the button, or an opt-in setting that opens the Farm run tab when the bank opens (needs a Plugin Hub rules check). No issue yet. |
| elwakka | Seeds are in the seed vault; a farm tab there too? | | Moved to #8 by elzerma | **Split** (done, #8) |

## #6 Seed stabber not needed

**Kind:** Bug · **Status:** open, fixed in ebe46f2, waiting on in-game check (BARBARIAN_PLANTING) · **Reviewed through:** 2026-10-16 elzerma

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| elwakka | With Strong barbarian fingers no seed dibber is needed, but the plugin asks for one | `SupplyCalculator.java:329`, `PatchGuide.java:455` | True: the unlock isn't handled anywhere. `VarbitID.BRUT_FARMING_PLANTING` exists; its values aren't checked in game. | **Fix**: an unlock read from that varbit, with a manual tick box until confirmed; skip the dibber line and reminder when met; a guided test. |
| elzerma | Will be addressed next release; the dibber counts against the leprechaun's stock meanwhile | `supply/LeprechaunItem.java:20` | Correct | Chatter |

## #7 Equiping magical secateurs not needed

**Kind:** Bug + ideas · **Status:** open, fixed in 96c706a and confirmed in game by elzerma 2026-10-16 (lava staff suggested; the fairy-ring stops came after the bank, where the dramen staff goes on); reply not yet posted; reply posted, ideas split to #16 (house) and #17 (report a problem) · **Reviewed through:** 2026-10-16 elzerma (triage reply)

**Fix (96c706a):** `SupplyCalculator.toWear` ranks every equipment slot in one pass, now with a weapon slot: needed-worn first (fairy-ring staff without the Elite Lumbridge diary), then whatever saves most (a staff or tome per rune type of the run it covers, a teleport item one slot), magic secateurs last. A staff the plan wields counts for its runes even while it's still in the bank; one that gives up its slot stops counting. The wiki says every Ardougne cloak tier teleports from the inventory ("When worn or in the player's inventory"), so the cloak stays an optional wear and gives way to a cape already worn. Equipping stays at the bank (`BankGuide.wear`). No help-list item: no new game values.

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| elwakka | Wearing magic secateurs pushed off the lava battlestaff, so the plan asked for fire and earth runes | `SupplyCalculator.java:341-350`, `HoldingsTracker.java:210-217` | True. The weapon slot is handled case by case, and staff runes only count once the staff is worn. | **Fix** (with the next row) |
| elzerma | Equip at the bank, never mid-run; secateurs lowest of the weapon-slot items; comb all wearables | `BankGuide.java:70-118`, REBUILD decision 8 | Good, and it matches decision 8 | **Fix**: rank every wearable slot at the bank (weapon: fairy-ring staff when needed, then a staff covering the plan's runes, then secateurs). A staff's runes count when the plan says to wield it. |
| elwakka | The Ardougne cloak doesn't need to be worn either | `SupplyCalculator.toWear` | True; the plugin only suggests wearing it to save a slot | **Fix**, as part of the wearables pass: the cape slot is ranked too (check on the wiki that each cloak tier teleports from the inventory) |
| elwakka | Use the house as a way through (Farming Guild spirit tree → house → exit portal) | `RoutePlanner.java:945` | Good idea. Arriving at the house by spirit tree, or on foot at the portal, isn't modelled. | **Split** into an idea issue |
| elzerma | Issues from the plugin could carry anonymised log data | `TestReport.java` | The Bug form now has an optional log field. A "report a problem" button could reuse `TestReport.url`. | **Split** into an idea issue |
| elzerma | More crop types; farming contracts | | Roadmap | **Explore**, in this ledger only |
| elwakka | Testing beta builds feels risky; will batch tests | | | Chatter |

## #8 explore/add bank tab support for seed vault

**Kind:** Idea · **Status:** open, exploring · **Reviewed through:** 2026-10-16 elzerma (body)

| Who | What they said | Checked against | Evaluation | Decision |
|-----|----------------|-----------------|------------|----------|
| elwakka (via #5) | Seeds in the seed vault are hard to pick out; a farm tab there too? | `RunSession.fromSeedVault()`, `HoldingsTracker.java:151` | A text reminder exists (6583e4d). There's no highlighting in the vault interface. | **Explore** |
| elzerma | Options: (1) seed vault tabs like the bank tag plugin, built into this plugin with credit; (2) highlight each tab in turn, as Easy Blast Furnace does | | Both possible. A third way, outlining the needed seeds in the vault (~100 lines), was offered and not picked. | **Explore**, keeping elzerma's two options |

## #9 Test: Fire of Nourishment at Weiss - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: arrived in region 11325, and the fire was spotted on that tile (`UnlockSpotter.java:49`). "a q p" under Anything else is noise. **Decision:** close as passed and remove WEISS_FIRE. Narrow the UNVERIFIED note in `UnlockSpotter`, which also covers the spirit trees.

## #10 Test: House scanning (beta) - works

**Kind:** Test report · **Status:** closed 2026-10-16; test fixed in b2f2ad7 (HOUSE_SCAN revision 3) · **Reviewed through:** 2026-10-16 elwakka (body)

Weak pass. "Before" is captured before the player enters, and a scan only adds, so the report doesn't show the scan filling anything in. The Kharyrll nexus change was a hand tick in Account > My house, not a scan. The other-house half was skipped (ironman). **Decision:** keep HOUSE_SCAN; fix the test so the house is cleared first and the scan has to fill it in; bump its revision; close #10 with thanks.

## #11 Test: Pendant of Ates test

**Kind:** Test feedback · **Status:** closed as duplicate of #12 · **Reviewed through:** 2026-10-16 elwakka (body)

"Doesn't go to the next step after teleporting" is true, and caused by #12's bug. **Decision:** close as a duplicate of #12.

## #12 Test: Pendant of Ates charges - needs attention

**Kind:** Test report · **Status:** open, replied; fixed in b2f2ad7, waiting on a re-run (ATES_CHARGES revision 2) · **Reviewed through:** 2026-10-16 elzerma (triage reply)

The report is right, though the player thought it was bugged. `CHARGES_PENDANT_OF_ATES_QUANTITY` reads 0 with 4 charges (`ItemChargeTracker.java:140`, UNVERIFIED), so the plugin can't count pendant teleports. Its "advance" after the spirit tree was the 6-minute fallback question. **Decision:** count teleports by click-then-move, as for the talisman; drop the varbit; bump ATES_CHARGES; reply and keep #12 open until a re-run.

## #13 Test: Xeric's talisman charges - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: 13 = 13, then a detected teleport, then 12 = 12. **Decision:** close as passed and remove TALISMAN_CHARGES.

## #14 Test: Kharyrll Teleport highlight - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: the player confirmed the outline on `ZAROSTELEPORT3`. **Decision:** close as passed; remove KHARYRLL and its UNVERIFIED note in `SpellHighlightOverlay`.

## #15 Test: Hot air balloon from the Grand Tree - works

**Kind:** Test report · **Status:** closed as passed 2026-10-16 · **Reviewed through:** 2026-10-16 elwakka (body)

Real pass: `ZEP_MULTI_GNO` = 1 with the route unlocked. Taverley's value of 2 hints that values above 1 mean something, but "above 0 = unlocked" holds. **Decision:** close as passed, remove HOT_AIR_BALLOONS, and mark BALLOON_GRAND_TREE confirmed.

## #16 Use the house as a way through (arrive by spirit tree)

**Kind:** Idea · **Status:** open · **Reviewed through:** 2026-10-16 elzerma (body)

Split from #7 (elwakka). **Explore**: arriving at the house through a spirit tree, or on foot at the portal, isn't modelled (`RoutePlanner.java:945`). Measure it against today's route first, e.g. Farming Guild → house spirit tree → Hosidius portal.

## #17 Report a problem from the plugin, with anonymised run data

**Kind:** Idea · **Status:** open · **Reviewed through:** 2026-10-16 elzerma (body)

Split from #7 (elzerma). **Explore**: could reuse `TestReport.url`, capped at 6000 characters.
