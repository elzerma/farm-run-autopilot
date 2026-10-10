# Farm Run Autopilot

Plans and guides combined tree, fruit tree and herb farming runs: what's due, the fastest route, exactly what
to bring, and step-by-step guidance at every patch.

The plugin never clicks, types or acts for you. It only shows information.

![Paying a gardener: the gardener and the payment are outlined, with the step under the player](docs/guidance-gardener.webp)

## Features

**Run tab**
- Shows which run types are due (trees, fruit trees, herbs) and counts down to the ones that aren't, with
  buttons to include or skip a type for this run.
- Plans one route across every due patch, using the teleports, items and unlocks you actually have. Pick the
  fastest route, the wiki order, or drag stops into your own order.
- A supply checklist: seeds and saplings, gardener payments (noted is fine), compost, tools, coins, runes or
  tablets, and travel items. Green is carried, yellow is in your bank or at the tool leprechaun, red is
  missing.
- Backup crop choices for when you run out, and your most valuable herbs sent to disease-free patches first.

<p>
  <img src="docs/run-tab.png" alt="Run tab: what's due and the route" width="300">
  <img src="docs/supplies.png" alt="Supply checklist with carried, in storage and missing colours" width="300">
</p>

**Bank**
- After you press Build run, a "Farm run" button in the bank shows just the items you need, with how many
  of each. It stays hidden while you aren't planning a run.

<img src="docs/bank-tab.png" alt="The Farm run bank tab" width="500">

**During a run**
- Once you have everything (tools at the tool leprechaun count), the run starts itself with step-by-step
  instructions under your character: where to go next, then
  what to do at each patch (pick, check health, clear, rake, compost, plant, pay the gardener).
- The patch is outlined with a hint arrow over it; the gardener is outlined when it's time to pay; the seed,
  compost, tool or teleport to use next is outlined in your inventory.
- Reminders to drop weeds, empty plant pots and buckets, to take an item from the tool leprechaun, or to note
  produce when your inventory is nearly full.
- Steps tick off on their own (planting, composting, paying) and the run finishes itself after the last
  patch, showing your time and your best three times for that kind of run.
- Route time estimates learn from your own runs.

![The patch outlined with the next step under the player](docs/guidance-patch.webp)

![Composting: the compost is outlined in the inventory and finished stops are struck through](docs/guidance-compost.webp)

**Setup**
- Choose your patches, crops, protection (pay the gardener or compost only), compost, travel method per
  location, outfit (Graceful or Farmer's), house (portal, nexus, jewellery box, spirit tree, fairy ring,
  spellbook altar) and unlocks.
- Quests, diaries, levels and house furniture are detected automatically; anything you haven't unlocked is
  greyed out with what it needs.
- Save settings as presets (e.g. "Quick herbs") and switch between them from the Run tab.

<img src="docs/setup-crops.png" alt="Setup tab: crop choices and disease-free herbs" width="300">

## How to use

1. Open the Farm Run Autopilot sidebar (the seedling icon).
2. On the Setup tab, pick your crops and run options. The Rules tab has patches, protection, travel, your
   house, unlocks and the run guidance highlights.
3. Tick the run types on the Run tab, which also shows what's due and your patch timers, and press
   **Build run**.
4. Open your bank and click the "Farm run" button to withdraw what you need. The run starts itself once you
   have everything; the timer starts when you teleport or click your first patch (or press **Start now**).
5. Follow the instructions under your character.

Patches are tracked as you visit them. Until then, the plugin uses RuneLite's Time Tracking data if it has
any.

## Privacy

Everything is stored in your RuneLite profile. The plugin makes no network requests.

## Credits

- Generated bank tab approach adapted from [Quest Helper](https://github.com/Zoinkwiz/quest-helper).
- Patch coordinates from [Farming-Helper](https://github.com/Speaax/Farming-Helper).
- Built on [RuneLite](https://github.com/runelite/runelite).

See `THIRD_PARTY_NOTICES` for their licences.

## Licence

BSD 2-Clause, see `LICENSE`.
