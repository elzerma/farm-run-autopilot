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

```
../../gradlew -p . run --args="legs-t0.tsv ../walk/patches.tsv t0-results.md"
```

Add `-PshortestPath=<path>` when the clone is somewhere else. Loading takes about 3 s; the T0 legs with their peeled
alternatives take about 45 s in all, and give the same answers every run.

## How it works

- `Account`: a pretend account (levels, quests, items, locked vars, transport types off). It stands in for RuneLite's
  client and Shortest Path's settings, the way Shortest Path's own tests (`TestPathfinderConfig`) do. Teleports are
  off unless a leg asks for them, so each answer is "from here, with no further teleport".
- `BubbleConfig`: Shortest Path's `PathfinderConfig`, answering quests and vars for the pretend account.
- `Requirement`: what a transport on the path needs (a level, a quest, a var, items, its type). Taking each away and
  asking again gives the next-best way ("peeling").
- `Bubble`: asks the questions, counts walking tiles and transport ticks separately (Shortest Path counts a walked
  tile as one tick; the plugin runs, 0.3 s a tile), and writes the table.

Legs start at the raw landing tile and end at any standing tile around the stop's patches (the same targets as
`tools/walk/Measure.java`).
