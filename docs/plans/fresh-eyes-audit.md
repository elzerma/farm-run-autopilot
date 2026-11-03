# Fresh-eyes audit: settings, tabs and help text

**Status:** every group approved by elzerma (2026-11-03, see [Decisions](#decisions)); built on
`claude/fresh-eyes-audit-a7eeca`.

An outside read of every place the plugin explains itself or asks for a choice: the five sidebar tabs, every hover and
help line, RuneLite's config panel and the "Help improve this plugin" list. It was done from the code,
`docs/BRANDING.md`, `CLAUDE.md` and `AGENTS.md` only, without the project's history, so nothing here leans on why
something is where it is. Where a proposal would undo something a code comment says elzerma asked for, the comment is
quoted beside it.

- The full inventory (every item, its hover, saved key and default, what reads it, who needs it, its height) is in
  [`fresh-eyes-audit/inventory.md`](fresh-eyes-audit/inventory.md).
- The renders are offscreen paintings of each tab at the sidebar's width (225 px) with RuneLite's look and feel, from a
  made-up mid-game account: [`fresh-eyes-audit/before/`](fresh-eyes-audit/before/). After renders come with the build.

## What each tab is for

| Tab | For |
|---|---|
| Run | Your next run: which run types and preset, building it, and following it stop by stop. |
| Farm | What happens at the patches: which ones, what's planted, protection, compost and what's done there. |
| Travel | How the route gets between stops: the ways it may use and how it orders and chooses them. |
| Account | What you have that the plugin can't read for itself: your house, unlocks and storage. |
| Settings | How the plugin looks and behaves: presets, highlights, what it detected, reset, and testing. |

Each tab mostly does this already. The proposals below are about wording, placing and space, not a new layout.

## Heights

Pixels at the sidebar's width. "First opened" is a new player's view (Crops and Ways to travel open, everything else
closed). After figures are estimates until the build.

| Tab, state | Before | After (est.) | Mostly from |
|---|---|---|---|
| Run, off | 458 | ~430 | H1 |
| Run, run built | 886 | ~855 | H1 |
| Run, run under way | 685 | ~595 | R1, H1 |
| Farm, first opened | 769 | ~560 | F1, H1 |
| Farm, every section open | 4,711 | ~3,860 | F1–F3, H1 |
| Travel, first opened | 602 | ~505 | T2, H1 |
| Account, every section open | 797 | ~660 | A1, A2, H1 |
| Settings, every section open | 5,314 | ~2,600 | S1, S2, S3, S6 |

## The change groups

Each group can be approved on its own. Saved values never change name: a moved or relabelled setting keeps its key, and
nothing is merged. One setting's control is cut (A2, the house pool); its saved value stays where it is, so nothing
needs carrying over.

### Group 1: Run tab

| # | Item | Verdict | Now | Proposed | Why |
|---|---|---|---|---|---|
| R1 | Run type tick boxes, while a run is armed or under way | hide then | always shown (59 px) | shown off and while building, hidden once the run is armed, back when it ends | A run under way follows the plan it was armed with (`RunService.setRunUnderWay`), so ticking them mid-run does nothing, and the screen used most during a run gets 59 px back. The preset picker above them already hides then. |
| R2 | The unlocks line | reword | "Check your unlocks: 5 not seen yet"; hover "Click to open Account > Unlocks" | "Check 5 unlocks in Account"; hover "Your run may need these, and the plugin can't read them from the game. Click to open Account > Unlocks." | "Not seen yet" reads as if the plugin should have seen them. The hover now says why it matters. |
| R3 | Build run's hover | reword | "Show the farm run bank tab and gather supplies. The run starts itself once you have them." | "Shows what to take on the bank's Farm run tab. The run starts by itself once you have it all." | The bank tab is "the Farm run tab" everywhere else. |
| R4 | The order line over the route | reword | "Order: auto (fastest)" | "Order: fastest" | Matches Travel > Route's names (T3). |

Kept: the preset row, Build run / Start now / Stop, the last run line, the run type rows with Include and Undo, the
patch timers, the route list with its drag and right-click, the missing-items box, and the guided test card. Each is
used every run, is in one place, and says what it is.

### Group 2: Farm tab

| # | Item | Verdict | Now | Proposed | Why |
|---|---|---|---|---|---|
| F1 | Crops, Compost and cures, Protection's choice per type, and Outfit | one line each | the type's name on one line, a full-width drop-down under it ("Tree" / "Magic (75)") | name on the left, drop-down on the right ("Trees  [Magic (75)]"); type names plural, as on the Run tab. With backups on, Crops keeps the name as a heading over its 1st, 2nd and 3rd rows, as now | BRANDING principle 6. Crops is open the first time: 180 px less there; Compost 221, Protection 240. Measured in the sidebar font: the longest name, "Hardwood trees" (79 px), and the longest crop, "Potato cactus (64)" (89 px), fit side by side. Empty buckets keeps its full width ("Store them at the tool leprechaun" wouldn't fit). |
| F2 | "Pick grown … and leave them growing" (×4), "Keep grown spirit trees for travel", "Pay 200 coins to clear grown …" (×5) and the redwood line | move to At the patches | under Protection, one per type (9 boxes and a line, 327 px) | At the patches gets two short lists, two to a row: **"Leave growing after picking"** Fruit trees, Bushes, Cacti, Calquats, Spirit trees; **"Pay 200 coins to clear grown"** Trees, Fruit trees, Hardwood trees, Calquats, Celastrus trees. The redwood line becomes that heading's hover: "A grown redwood is always cleared by paying Alexandra 2,000 coins: nobody else can." | They decide what's done to a grown crop, not how it's kept from disease, and At the patches is where the rest of that lives. About 160 px less. Hovers unchanged. |
| F3 | "Different protection for single patches" and its rows | move into Protection | under Advanced | under Protection, after Each set; still behind its own tick box | All protection in one place, as its own hover already points there ("than its type has in Protection"). The code says "Rarely used, so behind a tick box (elzerma)": the tick box stays. |
| F4 | Disease-free herbs | reword, two to a row | "Prioritise herbs for disease-free patches"; hover "Pick herbs that only go in disease-free patches"; 15 herbs one a row | "Save herbs for disease-free patches"; hover "The herbs you tick go only in disease-free herb patches, most valuable first; your other herb patches never get them." Herbs two to a row | It reserves herbs rather than prioritising them (`CropAllocator`). About 120 px less when on. |
| F5 | Patches: types not ticked on the Run tab | say so | "Tree patches  7 of 7" whether or not Trees is ticked on the Run tab; "Patches  80 of 80 ticked" counts every type | an unticked type's group says "not in runs", hover "Tick Trees at the top of the Run tab to put them in your runs"; the Patches count covers the run types in your runs | The Run tab's Trees box and this tab's Tree patches box look like one switch and aren't. Nothing changes what's saved. |
| F6 | Use Cure Plant (Lunar); Plant cures to bring (backup) | hovers, shorter label | no hovers | "Use Cure Plant (Lunar)", hover "On a diseased patch, cast Cure Plant instead of using a plant cure. A run with herbs brings runes for one cast."; "Plant cures to bring", hover "In case a patch is diseased. None are brought when every patch on the run is disease-free or protected." | Neither said what it does or when it applies. |
| F7 | "Include a run type when this % of its patches are due" | hover | no hover | "A run type joins your run once this share of its patches is due. 100: only when all of them are." | The default and what lowering it does weren't said. |

Kept: Bring gardener payments noted, Each set, Use backup choices, the At the patches tick boxes, Make compost and its
crops, Farming contracts (its own section: it's a big feature people will look for by name), Extras to bring. Their
hovers already explain them.

### Group 3: Travel tab

| # | Item | Verdict | Now | Proposed | Why |
|---|---|---|---|---|---|
| T1 | House's row in Ways to travel | fix | right side "set in Account > My house", which cuts the name to "H…" at 225 px (render) | right side says where your house is, read from the game: "Rimmington", "No house" | A bug at sidebar width, and the place is more use on the line. The pointer stays in the hover ("…as set in Account > My house"). BRANDING uses "set in Account > My house" as its example of a pointer; here it moves to the hover. |
| T2 | Travel style; "Walk when it's nearly as quick as teleporting" | move to Route | in Ways to travel, after the eleven families, pushed far down once a family is opened | in Route, after Finish near a bank | They're how the route chooses, like Order. Ways to travel then holds what the route may use; its hover says that's kept whatever the preset, which was never true of these two. 69 px less on the first view. |
| T3 | Order's choices | rename | "On - Autopilot (default)", "On - Meta (wiki order)", "Off (my own order)"; hover "Autopilot finds the quickest order; Meta follows the wiki's order; Off keeps your own: drag the stops in the route list on the Run tab." | "Fastest (default)", "The wiki's order", "My own order"; hover "Fastest: the quickest order from where you are. The wiki's order: the OSRS Wiki's. My own order: drag the stops in the route list on the Run tab." | "On/Off" and "Meta" are jargon, and the Run tab already says "fastest" and "the wiki's". Saved values unchanged (AUTOPILOT, META, OFF). "Autopilot" is the plugin's own name, so this one is your call. |
| T4 | Order and Travel style | one line each | name above a full-width drop-down | name left, drop-down right; Start at keeps its full width (place names are long) | BRANDING principle 6; 30 px less in Route. |
| T5 | "Walk when it's nearly as quick as teleporting" | shorten | two lines | "Walk when nearly as quick"; hover unchanged | The hover has the detail. |
| T6 | Finish near a bank | hover | none | "Counts the trip from the last stop to a bank when choosing the order, so the run ends near one." | It didn't say what it does. |
| T7 | Ways to travel's hover | reword | "Every stop's way is the quickest the route can use. Untick a family, or open one and untick a way you can't or won't use: the route is planned again without it. Kept for your account, whichever preset is in use." | "Each stop uses the quickest way you allow. Untick a family, or open one and untick a way you can't or won't use: the route is planned again without it. The families and ways you untick stay the same whichever preset you use." | The last sentence covered the whole section, including two settings presets do change. |

Kept: the eleven families and their lists, Keep the last charge (inside Teleport items) and Cast from runes (inside
Tablets), each next to what it affects; Avoid the Wilderness; Switch spellbook to cast; Start at.

### Group 4: Account tab

| # | Item | Verdict | Now | Proposed | Why |
|---|---|---|---|---|---|
| A1 | Portal nexus destinations; Fairy ring and Spirit tree | two to a row | one a row | two to a row, as the Farm tab's patches | About 70 px less. |
| A2 | Pool | cut | a drop-down (39 px) | gone; the saved value and the house scan stay, so it can come back when something uses it | Nothing reads it: the planner and guide never use the pool (only the house scan sets it). |
| A3 | While detecting furniture: Report a problem, Last scanned, Rescan house | tidy | three rows: a Report a problem button (opens the issues page), the date, a Rescan house button | one line, "Last scanned 03/11/26 14:05  Rescan" (link); the button goes | Settings > Help improve this plugin > Report a problem is the plugin's one report button, with the bug form filled in; this one only opened the issues page under the same name. |
| A4 | Detect furniture's hover | reword | "…Beta: it skips other players' houses by how you got in, which can't be fully checked yet; if it gets your house wrong, please report it." | "Fills in the jewellery box, altar, fairy ring and spirit tree below when you enter your house. Beta: it tells your house from someone else's by how you got in, which can't be fully checked yet. If it gets yours wrong, use Report a problem in Settings > Help improve this plugin." | No "please" (BRANDING, words); says where to report; no pool (A2). |
| A5 | Group storage (GIM), Seed vault | hovers | none | "Counts what's in your group's shared storage, once you've opened it." / "Counts what's in the Farming Guild's seed vault, once you've opened it." | Neither said that it's only read once opened. |

Kept: Jewellery box, Spellbook altar, Unlocks with its "(check)" marks and These are right, When a run banks partway.

### Group 5: Settings tab

| # | Item | Verdict | Now | Proposed | Why |
|---|---|---|---|---|---|
| S1 | Display's colours | onto their rows | three rows of their own: "Patch colour", "NPC colour", "Item colour" | each swatch at the right of its highlight's row: Highlight the patch, Highlight NPCs, Highlight items to use. Swatch hovers: "Patch colour: the patch, its tile and the compost bin", "NPC colour: gardeners, the tool leprechaun and what to use on the way", "Item colour: items, spells, menu options and places on a travel map". Highlight the spell's hover adds "In the item colour." | Each colour next to what it colours, and "Item colour" said only items though it colours spells, menus and maps too. 66 px less. |
| S2 | Debug > Patch details | group by type | 80 two-line cards under small headings (~2,950 px) | a closed group per patch type ("Trees  7 seen"), a line per patch when opened (place, state), the detail on hover, as Run > Patch timers | Opening Debug for one detected value meant a 3,800 px page. The code says "elzerma: two headings, and these floating in the middle, read as confusing": the groups replace both headings, as the timers do ("elzerma: 22 two-line cards was too long"). |
| S3 | Help improve this plugin's three explanations | shorten | 87, 63 and 133 px (see inventory) | "Each opens a GitHub issue in your browser with the details filled in. Nothing is sent until you submit it (a free GitHub account is needed), and it never has your name or account in it." / "Something wrong with a run? The report has your current plan (stops, travel, supplies); you add what happened." / "Features I couldn't try myself, a few minutes each. The Run tab walks you through: it sets what it needs (your settings come back after), says what to bring and asks Yes or No. If you're missing something, it stops and nothing is reported." | Same facts, about 60 px less. Headings kept: the code says "(elzerma): what everything in this section has in common, then each part under its own heading with its own explanation". |
| S4 | Presets' hover | reword | "…Presets cover the Farm and Travel tabs; your house and unlocks are shared." | "Save your run types and the Farm and Travel tabs under a name (e.g. "Quick herbs") and switch at the top of the Run tab. A preset never changes the ways you turned off, Avoid the Wilderness, Switch spellbook, farming contracts or the Account tab." | It didn't say presets carry the run types, and those five aren't in them (`SettingsStore.farmAndTravel`). |
| S5 | Detected > Spellbook's hover | correct | "Read from the game; teleports on another spellbook are used as tablets" | "Read from the game. Teleports on another spellbook use tablets, or are cast after a switch with Travel > Switch spellbook to cast on." | It predates switching spellbooks. |
| S6 | Detected's names | more room | the name column is 80 px, so long unlock names take four or five lines | the value column takes only what its text needs, the name the rest | About 100 px less, fewer broken lines. |

Also, with no visible change: "Detected" comes out of the sections open by default, as it's no longer a section.

Kept: Reset (rarely used, already closed), Count every patch, Preview as a player and Dev checks (developer client
only).

### Group 6: RuneLite's config panel

| # | Item | Verdict | Now | Proposed | Why |
|---|---|---|---|---|---|
| C1 | "Click to open the sidebar" | reword the description | "All Farm Run Autopilot settings are in its sidebar panel. Each click here opens it." | "Every setting is in the Farm Run Autopilot panel. Click here, or its icon in RuneLite's sidebar, to open it." | Tells a new player where the panel's button is for next time. |
| C2 | The plugin's description (plugin list and Plugin Hub) and tags | update | "Plans and guides combined tree, fruit tree and herb farming runs"; tags farming, farm, run, herb, tree, fruit, route, planner | "Plans and guides your farm runs: every patch type, the route, what to bring and each step"; tags add allotment and guide | It still described the first three run types. Public text: it reaches the Hub with the next release. |
| C3 | The ten hidden items (edited in Settings > Display) | match the sidebar | names and descriptions older than the sidebar's ("Gardener colour", "Vine highlights") | the sidebar's labels and hovers; class comments that still say "Account tab" fixed | No visible change; one wording for each setting. |

Kept: everything else stays hidden in the config panel, as Settings > Display has it (one place).

### Group 7: The help list

| # | Item | Verdict | Now | Proposed | Why |
|---|---|---|---|---|---|
| H1 | The link at the bottom of each tab | one line | "Help improve this plugin: try a few quick things in game, no coding needed. Show me" (3 lines, 44 px) | "Quick tests in game, no coding: Show me" (1 line); hover "Help improve this plugin: 17 features I couldn't test, with steps to try them" | 29 px back on Run, Farm, Travel and Account. Still says it's quick and needs no coding, as the code asks ("Says it's easy (elzerma: no coding or heavy lifting…)"), and stays a quiet link ("elzerma: a quiet link rather than a big button"). |
| H2 | "Paying for one allotment" (ALLOTMENT_PAYMENT) | move | Run tab group | Farm tab group, beside "Flowers protecting allotments" | Both are set in Farm > Protection. |
| H3 | Steps naming settings that change | reword | "Farm > Protection > Allotment patches" (ALLOTMENT_PAYMENT, ALLOTMENT_FLOWER_PROTECTION); "your jewellery box, pool, altar…" (HOUSE_SCAN, and its guided question) | "Farm > Protection > Allotments"; no pool (with A2) | Follow F1 and A2. Earlier results still count, so no revision bump. |
| H4 | Two guided questions | correct | "Does the Run tab's list have …" (STAFF_SWAPS, ALLOTMENT_FLOWER_PROTECTION) | "Does the bank's Farm run tab have …" | The supply list moved to the bank tab; the Run tab has none. No revision bump. |

Kept: every title, the groups by tab, "Ready" / "Not yet", the Done group and the GitHub link. No test is added or
removed, so pinned issue #18's table doesn't change.

### Group 8 (a tool, not the plugin): the sidebar renderer

The renders here came from a small program that builds each tab from stub data and paints it at the sidebar's width,
with a text inventory of every label, hover and height. Kept in `src/test` (not run by the build), it can make the
after renders and check a layout without a game client. Proposed: keep it there.

## Ideas beyond organisation

Not proposed for building here; each changes what the plugin does.

1. One switch per run type: the Run tab's run type box and Farm > Patches' type box could be the same thing.
2. Use the house pool to restore run energy on a house visit, or stop scanning it.
3. Make Keep the last charge and Cast from runes account-wide like the ways, so all of Ways to travel ignores presets.
4. Debug > Patch details could show only what the patch timers don't (patches not in your runs, where each value came
   from).
5. Show what's in a family before it's opened: e.g. "9 usable, 2 off" on the Ways to travel rows.

## Decisions

elzerma's answers in the picker, 2026-11-03:

| Group | Answer |
|---|---|
| 1. Run tab | All of it (R1–R4) |
| 2. Farm tab | All of it (F1–F7) |
| 3. Travel tab | All of it (T1–T7), the Order renaming included |
| 4. Account tab | All of it (A1–A5), cutting Pool included |
| 5. Settings tab | All of it (S1–S6) |
| 6. Config panel | All of it (C1–C3), the Hub description included |
| 7. Help list | All of it (H1–H4) |
| 8. Sidebar renderer | Keep it in `src/test` |
