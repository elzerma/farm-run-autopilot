# Travel atlas (dev only)

Runs Shortest Path's own pathfinder outside the game ("in a bubble") with pretend accounts, as planned in
`docs/plans/travel-rework.md`. Sprint T0: `Bubble.java` answers the legs in `legs-t0.tsv` and writes `t0-results.md`.
Sprint T1 grows it into the atlas builder.

This is its own Gradle build (`settings.gradle` here), never part of the plugin's. Shortest Path's code (BSD-2, see
`THIRD_PARTY_NOTICES`) is compiled in unchanged from a local clone; it is not in this repository and nothing from it
reaches the plugin.

## Setup

Clone Shortest Path at the commit the Plugin Hub ships (`plugins/shortest-path` in runelite/plugin-hub; `37abff3`,
version 1.21.3, for T0) next to this repository:

```
git clone https://github.com/Skretzo/shortest-path.git ../shortest-path
git -C ../shortest-path checkout 37abff3a3345185fc11fccecae04d8170de25a11
```

## Run (from tools/atlas)

Add `-PshortestPath=<path>` to any of these when the clone is somewhere else.

- **The atlas** (sprint T1): writes the plugin's data files to `src/main/resources/com/farmrunautopilot/atlas/` and
  `report.md` here. Read the report's checks and changes before committing a rebuild.

  ```
  ../../gradlew -p . atlas
  ```

- **The referee**: Shortest Path's own fastest way with teleports, between every two stops the plugin runs, for three
  pretend players, written to `referee.tsv` (compared with the plugin's choices in sprint T2).

  ```
  ../../gradlew -p . referee
  ```

- **The T0 legs**: `legs-t0.tsv` to `t0-results.md` (about 45 s).

  ```
  ../../gradlew -p . run --args="legs-t0.tsv ../walk/patches.tsv t0-results.md"
  ```

## The atlas files

All in `src/main/resources/com/farmrunautopilot/atlas/`, tab separated, generated (never edit by hand):

- `places.tsv`: every place a way starts or ends: patches (by tile, or one place for patches of one type at one stop
  within 8 tiles; `names` lists the patches there), banks near a patch, every teleport or hop landing (within 2 tiles
  of each other counts as one), and every hub where a hop is taken.
- `ways.tsv`: from place, to place, walking tiles, transport ticks, the transports in order (`transports.tsv` ids)
  and what the way needs (`requirements.tsv` ids). Up to 6 ways a pair, quickest first; none slower than 300 s, and
  none that another kept way beats while needing no more. Ways walk, use doors, stairs, shortcuts and single boats,
  but never a hop or a teleport: the plugin adds those itself.
- `teleports.tsv`: every teleport usable from anywhere (spells, items, minigame and home teleports, the quetzal
  whistle) and the house's portals and jewellery box, with where it lands, its ticks and what it needs.
- `hops.tsv`: every hop of the transport networks (fairy rings, spirit trees, quetzals, gnome gliders, charter ships,
  magic carpets, mushtrees, canoes, minecarts, balloons, obelisks): the hub it's taken from, where it lands, its ticks
  and what it needs. A leg can be a way to a hub, the hop, and a way from where it lands, with or without a teleport
  first.
- `transports.tsv`: each transport a way uses (boats, doors, shortcuts, spirit trees...), with its ticks; `guessed`
  marks a 0 in Shortest Path's data counted as 1 tick (2 for an agility shortcut). A closed door or gate counts 2
  ticks more than its data says: it opens, then the player walks on.
- `requirements.tsv`: a level, quest, varbit or varplayer condition, items, or a transport type (also a Travel tab
  family).

`patches.tsv` here lists every farming patch in the game; the rows the plugin runs must match `PatchPoints`
(`AtlasPatchListTest`).

## How it works

- `Account`: a pretend account (levels, quests, items, locked vars, transport types off). It stands in for RuneLite's
  client and Shortest Path's settings, the way Shortest Path's own tests (`TestPathfinderConfig`) do. Teleports are
  off unless a leg asks for them, so each answer is "from here, with no further teleport".
- `BubbleConfig`: Shortest Path's `PathfinderConfig`, answering quests and vars for the pretend account, and charging
  a closed door or gate its extra time, so the pathfinder walks round when that's as quick (the referee too).
- `Requirement`: what a transport on the path needs (a level, a quest, a var, items, its type). Taking each away and
  asking again gives the next-best way ("peeling").
- `Bubble`: asks the questions, counts walking tiles and transport ticks separately (Shortest Path counts a walked
  tile as one tick; the plugin runs, 0.3 s a tile), and writes the table.

Legs start at the raw landing tile and end at any standing tile around the stop's patches (the same targets as
`tools/walk/Measure.java`).
