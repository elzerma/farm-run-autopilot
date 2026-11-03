# Release notes

## Next update (since the release of 2026-10-17)

### New run types and patches
- Bushes, cacti, mushrooms, belladonna, seaweed and the special trees: hardwoods, calquats (#22), celastrus, redwoods,
  spirit trees and the crystal tree. More allotment, flower and hops patches too.
- The Run tab has nine run type boxes. Specials covers the cactus, mushroom, belladonna and seaweed, and Sp. trees the
  special trees.
- Each set of allotments can be protected by its flower (the default), the gardener, or compost only.
- Fruit trees, bushes, cacti and calquats can be picked and left growing (#39), and spirit trees kept for travel.
- Not covered yet: anima, Hespori, grapes, coral, the Entrana and Keldagrim hops patches and the Anglers' Retreat flax
  patches.

### Farming contracts
- Turn on Farm > Farming contracts > "Build farming contracts into runs". The Farming Guild's patches then hold the
  wiki's pre-plants and are left alone until Guildmaster Jane's contract asks for one. Anything else growing there is
  picked once grown, and the pre-plant planted in its place once you have its seed.
- The contract's crop is checked or picked once grown and the patch replanted. A contract whose crop isn't growing gets
  it planted; if its patch holds another crop, a quick one (herbs, flowers, bushes) takes its place, picked first if
  it's grown, and for a slow one the guide says to ask Jane for an easier contract.
- The guild stop ends at Jane, outlined: hand in your contract and take another (the highest tier your level allows),
  or ask for one. If the new crop is already grown, it's done on the spot. The Run tab says where the contract stands.

### Travel
- Every leg is planned from a travel map worked out ahead of time with Shortest Path's data. It covers teleports,
  fairy rings, spirit trees, boats and ships, gliders, balloons, carts, magic carpets and Agility shortcuts, checked
  against your levels, quests and items. There's no teleport to pick per stop any more.
- Travel > Ways to travel lists the ways by family, and each can be turned off. On the Run tab, right-click a stop for
  "Don't use ...". Home and minigame teleports are off to start with; Avoid the Wilderness is on.
- "Cast from runes, not tablets" is one tick box, Teleport to House included (#27). "Fewest items to bring" is gone; a
  saved choice loads as Fastest.
- Journeys are spelled out step by step, with the boat, NPC or object to use outlined and its option coloured. A fairy
  ring's code is coloured in its travel log, and a teleport item's option in its right-click menu.
- Where your house is, and where Teleport to House lands, are read from the game.
- A second staff can come along when it saves inventory slots, and the rune pouch counts as one slot (#31).

### At the patches
- One patch at a time: pick, plant, compost and pay, then the next.
- The tool leprechaun is visited only when a stop needs it: for compost, a tool or room. Each visit notes your produce,
  stores empty buckets and takes compost for the next stops. In his window, what to click is outlined with how many
  clicks are left, and so is the quantity button to use, so there's nothing to type.
- Farm > At the patches: note produce, clean herbs, drop, keep or store empty buckets, and make compost (and
  ultracompost) in the compost bins.
- The walking line and hint arrow go to the patch's tile nearest you, with that part of the patch marked.

### Run tab, bank and seed vault
- Getting ready is a list in order: the seed vault first, then the bank.
- The Farming Guild's seed vault has a Farm Run Autopilot section with the seeds to take (#8).
- Run times are kept per type of patch: a new best is said in the game chat, and after each stop the Run tab shows
  your pace against your best. Times never change the route, and "Share your run times" is gone.
- Anything that goes missing mid-run shows on the Run tab, with Bank for them or Skip.
- A run under way shows on the Run tab again after you log back in.

### Fixes
- Taverley's tree passed over after the balloon from the Gnome Stronghold (#35): for a moment after a balloon lands,
  the game still reports the patch where you set off. Patches are now read only once the new area has loaded, which
  should stop it. Please say on the issue if it happens again.
- The Spellbook Swap guided test read the Run tab a moment too early; it's fixed and asked again (#32).
- Planting your last seed of a kind you chose to do without no longer skips composting that patch.
- Picking an allotment needs a spade: with yours stored with the tool leprechaun, the guide now says to take it first.

### Help improve this plugin
- New guided tests in Settings > Help improve this plugin, among them the seed vault, staves, allotment protection,
  your house and the new patches. Each walks you through it and puts your settings back after.
