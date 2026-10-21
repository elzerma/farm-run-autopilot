# Farm Run Autopilot: UI branding kit

How the plugin looks and speaks, so every screen matches. Set during the Sprint 7 facelift (October 2026) from
elzerma's feedback. The code that applies it is `ui/Style.java` (controls), `ui/Card.java` (cards, group headers,
chevrons), `ui/TabIcon.java` (tab icons) and `ui/UiText.java` (wrapping). Change those, not each screen.

## 1. Principles

1. **An updated RuneLite look.** RuneLite's own colours and fonts, made calmer and more even. It should look
   like it belongs in RuneLite, not like another app.
2. **Flat and square.** No gradients, no shadows, no rounded corners (elzerma tried a soft radius and chose
   square).
3. **Say each thing in one place.** If the overlay, a tooltip or another tab already says it, don't repeat it.
   A pointer ("set in Account > My house") is fine; repeating the content is not.
4. **Explanations go in tooltips.** A control's label says what it is; hovering says why and what it does.
   Only warnings that need action stay visible. Exception: Settings > Help improve this plugin, where people
   come to read, so its explanations are visible.
5. **Rarely used things are tucked away.** Collapsible groups, one-line rows that open, and "Enable …" tick
   boxes in front of big option lists (per-patch protection).
6. **One line where possible.** Name on the left, value on the right; the detail on hover or when opened.

## 2. Colour

### Sidebar (RuneLite's `ColorScheme`, plus ours)

| Role | Colour | Hex | Code |
|---|---|---|---|
| Page background | dark grey | `#282828` | `ColorScheme.DARK_GRAY_COLOR` |
| Card, button, drop-down, tick box fill | darker grey | `#1e1e1e` | `ColorScheme.DARKER_GRAY_COLOR` |
| Hover fill, current stop's card | hover grey | `#232323` | `ColorScheme.DARK_GRAY_HOVER_COLOR` |
| Pressed button, faint outlines, done text | medium grey | `#4d4d4d` | `ColorScheme.MEDIUM_GRAY_COLOR` |
| Tick box outline | light grey, darker | `#737373` | `LIGHT_GRAY_COLOR.darker()` |
| All text, titles and headings | white | `#ffffff` | `Color.WHITE` |
| Accent: current stop's bar, bank visit's bar, hover outline, ticked by you, primary buttons, warnings | RuneLite orange | `#dc8a00` | `ColorScheme.BRAND_ORANGE` |
| Done, due, carried, locked tick (seen done / read from the game) | green | `#4cc152` | `Style.GREEN` |
| In storage (take it from the bank), changed-from-default bar | yellow | `#e8c53a` | `IN_STORAGE`, `ExpandableRow.OVERRIDE` |
| Stop button | muted red | `#8a3333` | `Style` (danger) |
| "You've run every test" | RuneLite green | `#37f046` | `ColorScheme.PROGRESS_COMPLETE_COLOR` |

Orange is an accent, not a text colour: one orange thing per area at most. Never put orange text on orange.

### In game (overlays)

| Role | Hex | Code |
|---|---|---|
| Instruction under the player | `#ffd700` gold | `GuidanceOverlay.INSTRUCTION` |
| Reminders (drop weeds, inventory full) | `#ff981f` | `GuidanceOverlay.REMINDER` |
| Timer note | white | `GuidanceOverlay.TIMER` |
| Item to wear: outline and "Wear" label | `#40c8ff` sky blue on `#000000aa` | `ItemHighlightOverlay.WEAR_COLOUR` |
| Patch / NPC / item highlights (player can change) | `#00ffff` / `#ffd700` / `#00ff00`, alpha 220 | `FarmRunAutopilotConfig` |

Wear blue is only for "put this on", so it's never confused with an item to use.

Something to click (the spell, the item to use, a side-panel tab's icon, a button, a place on a travel map) gets the
growing vine (`run/VineFrame`) while "Growing vine highlights" is on, and a plain outline when it's off. Round an item
the vine sits just outside its slot, and only round the first slot holding it, so several buckets of compost aren't a
tangle. Items to wear keep their blue outline and "Wear" label.

### Bank tab

| Role | Hex |
|---|---|
| Section headers | `#e4d8a2` |
| Carried | `#4cc152` |
| In storage | `#e8c53a` |
| Missing | `#e05555` |
| Deposit first | `#ff981f` |

### Seed vault

The "Farm Run Autopilot" section at the top of the Farming Guild's seed vault has a header like the vault's own
categories (elzerma: the same font as the others): `#ff981f`, bold 12 with a shadow, and the dark line under it.
Under each seed is the bank tab's "/ N" label in the colours above, ticked or crossed (`bank/SeedVaultSection.java`,
sharing `FarmBankTab`'s). The vault's own items keep the vault's look, names included; a seed the vault doesn't
hold is shown faded, as on the bank tab. The view is switched on and off by a button just left of the vault's close
button that looks and calls for attention exactly as the bank's Farm run button does: the same square button and
farming icon, pressed while the view is on, flashing with the same outline round it (`VineOverlay`) while it's off
and the vault holds something the run needs. While the view is on, the category list on the left also has a
"Farm Run Autopilot" entry like the game's own: plain 12, orange, white under the mouse.

## 3. Type

All RuneScape fonts from `FontManager`. No other fonts, no added bold.

| Use | Font |
|---|---|
| Body text, labels, rows, tooltips, buttons | `getRunescapeSmallFont()` |
| Headings and section titles ("Route summary", "Patches") | `getRunescapeFont()` (`Style.titleFont()`), white |
| The current stop's name | `getRunescapeBoldFont()` (the only bold title) |
| Run clock | bold, 24 pt (`RunPanel.CLOCK_FONT_SIZE`), orange |

## 4. Spacing

| Where | Pixels |
|---|---|
| Sidebar edge | 6 |
| Inside a card | 4 top/bottom, 7 sides (+3 for an accent bar) |
| Between cards, rows and sections | 3 |
| Above / below a heading | 8 / 3 |
| Section content | 3 top, 2 sides |
| Tick box margin | 3 above and below; 5 between box and text |
| Under the Trees / Fruit / Herbs row | 5 |
| Tab bar | 5 top/bottom inside each box, 3 between boxes |

Keep to these numbers; if something looks cramped, change it here and in the code together.

## 5. Components

- **Card** (`Card`): square panel in darker grey; optional 3 px accent bar down the left edge: orange for the
  current stop or a bank visit, yellow for a row changed from its default. `hoverable()` when clickable.
- **Group header** (`Card.groupHeader`): a hoverable card with a chevron (right closed, down open), the title,
  and a count on the right ("3 of 7 due", "2 to try"). Remembers open/closed (`settings.isSectionOpen`).
- **Section** (`CollapsibleSection`): the same header style with a faint "(?)" when it has an explanation on
  hover.
- **One-line row** (`ExpandableRow`): name left, short value right, full value on hover, controls when opened.
- **Buttons** (`Style`): plain (darker grey, lighter on hover), primary (orange, dark text, slim; the main
  action on screen: Build run, Start now, Start test), danger (muted red, white text: Stop).
- **Drop-downs and number boxes** (`Style.combo`, `Style.spinner`): darker grey, medium grey outline, orange
  outline while focused, plain arrow.
- **Tick boxes** (`Style.checkBox`, 12 px, drawn by us):

  | State | Look |
  |---|---|
  | Can be ticked | darker grey, grey outline |
  | Hover | orange outline |
  | Ticked by you (can untick) | orange fill, dark tick |
  | Some of a group on, some off (a Travel tab family; `Style.partial`) | orange fill, dark dash |
  | Locked: seen done or read from the game | green fill, dark tick |
  | Not possible yet | dark grey, faint outline |

- **Links** ("Manage", "Show me", "See every report on GitHub"): small font, white, underlined, hand cursor.
- **Tab bar** (`TabBar`, `TabIcon`): five boxed buttons sharing the width (darker grey, lighter on hover), each
  with an icon (play, sprout, map pin, person, solid gear), grey; the selected one's icon orange with an orange
  line along its bottom; the tab's name on hover.
- **Tooltips**: wrapped to about the sidebar's width (`UiText.wrap(text, 250)`, route tooltips 190).

## 6. Words

- Talk to the player as "you", in plain words: "Open a bank", "Take what the Farm run tab shows".
- Short labels; the why goes in the tooltip.
- Name things by what the player sees in game (item and place names as the game spells them).
- Numbers as numbers: "3 of 7 due", "23/28 slots", "About 17m (10m travelling)".
- No "please", no exclamation marks except "Thank you!", no jargon (varbit, override, null).
- Say each thing once (principle 3).

## 7. Checklist for a new screen

1. Uses `Card`, `Style` and `UiText`, no hand-made borders or colours.
2. White text, small font; headings in the normal font; orange only as an accent.
3. Spacing from section 4.
4. Explanations in tooltips; nothing said twice.
5. Long or rarely used parts collapse or sit behind an "Enable …" box.
6. Looks right in the 225 px sidebar with the scroll bar showing.
