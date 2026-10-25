# Travel atlas report

Built 2026-10-25 by `tools/atlas` from Shortest Path `37abff3a3345185fc11fccecae04d8170de25a11`. Times are walk tiles x 0.3 s + transport ticks x 0.6 s (a 0-tick transport counts as 1, an agility shortcut as 2), before the plugin's set-off overhead. Sweep budget 300 (banks 150), ways over 300 s dropped, up to 8 questions and 6 ways a pair.

## Build

| What | Count |
|---|---|
| Places: patches (by tile) | 71 |
| Places: banks near a patch | 124 |
| Places: teleport and hop landings | 435 |
| Places: hubs (where a hop is taken) | 192 |
| Teleports (anywhere, or from the house) | 986 |
| Hops (fairy rings, spirit trees, quetzals, gliders, charter ships...) | 6935 |
| Pairs asked (start within the sweep budget) | 11549 |
| Questions asked | 34980 |
| Searches stopped at the 4 s limit | 28 |
| Pairs with at least one way | 11518 |
| Ways kept | 20744 |
| Hops kept (walked to and leading somewhere) | 6935 |
| Build time (s) | 623 |
| `places.tsv` | 50 KB |
| `teleports.tsv` | 82 KB |
| `hops.tsv` | 488 KB |
| `transports.tsv` | 58 KB |
| `requirements.tsv` | 28 KB |
| `ways.tsv` | 498 KB |
| All data files | 1207 KB |

## Checks

- **No way to:** FARMING_GUILD_REDWOOD (atlas only), FARMING_GUILD_HESPORI (atlas only), CORAL_NURSERY_WEST (atlas only), CORAL_NURSERY_EAST (atlas only)
- Every patch the plugin runs has a way from a teleport or hop landing.
- Only reachable with a requirement (no "needs nothing" way within the budget): FOSSIL_ISLAND_SEAWEED_NORTH, FOSSIL_ISLAND_SEAWEED_SOUTH, ANGLERS_RETREAT_HARDWOOD (atlas only)

## Transports with 0 ticks in Shortest Path's data

Counted as 1 tick (2 for an agility shortcut). Worth a real duration from the wiki or the dev log when one decides a route.

- AGILITY_SHORTCUT: Climb Rocks (16)
- AGILITY_SHORTCUT: Climb Rough wall (4)
- AGILITY_SHORTCUT: Climb Wall (2)
- AGILITY_SHORTCUT: Climb-down Rocks (2)
- AGILITY_SHORTCUT: Climb-into Hole
- AGILITY_SHORTCUT: Climb-into Underwall tunnel (4)
- AGILITY_SHORTCUT: Climb-over Crumbling wall (2)
- AGILITY_SHORTCUT: Climb-under Castle wall
- AGILITY_SHORTCUT: Cross Stepping stone (6)
- AGILITY_SHORTCUT: Enter Big window
- AGILITY_SHORTCUT: Jump Broken Fence (2)
- AGILITY_SHORTCUT: Jump Wall (4)
- AGILITY_SHORTCUT: Jump-over Strange floor
- AGILITY_SHORTCUT: Jump-to Broken Raft (2)
- AGILITY_SHORTCUT: Jump-to Stepping stone (5)
- AGILITY_SHORTCUT: Jump-up Weathered wall
- AGILITY_SHORTCUT: Squeeze-through Broken fence (2)
- AGILITY_SHORTCUT: Squeeze-through Crevice (2)
- AGILITY_SHORTCUT: Squeeze-through Loose Railing
- AGILITY_SHORTCUT: Squeeze-through Obstacle pipe (3)
- AGILITY_SHORTCUT: Swim-to Shore (3)
- AGILITY_SHORTCUT: Swim-to Strong Tree (2)
- AGILITY_SHORTCUT: Walk-across Log balance (4)
- TRANSPORT: Board Log raft
- TRANSPORT: Climb Rocks
- TRANSPORT: Climb Rockslide (3)
- TRANSPORT: Climb Sand pile (4)
- TRANSPORT: Climb Stairs
- TRANSPORT: Climb-down Crevice
- TRANSPORT: Climb-down Manhole
- TRANSPORT: Climb-down Rock
- TRANSPORT: Climb-down Rocks (2)
- TRANSPORT: Climb-down Secret entrance (2)
- TRANSPORT: Climb-down Shelf (2)
- TRANSPORT: Climb-down Staircase (6)
- TRANSPORT: Climb-down Stairs (6)
- TRANSPORT: Climb-down Tunnel entrance
- TRANSPORT: Climb-over Mud pile
- TRANSPORT: Climb-over Stile (3)
- TRANSPORT: Climb-over Wall rubble
- TRANSPORT: Climb-up Floor
- TRANSPORT: Climb-up Shelf
- TRANSPORT: Climb-up Staircase (10)
- TRANSPORT: Climb-up Stairs (11)
- TRANSPORT: Climb-up Stone Staircase (2)
- TRANSPORT: Crawl Cave
- TRANSPORT: Cross Ledge
- TRANSPORT: Cross Little boulder (2)
- TRANSPORT: Cross-bridge Tree
- TRANSPORT: Descend Hole
- TRANSPORT: Descend Stairs (2)
- TRANSPORT: Enter Blocked tunnel (2)
- TRANSPORT: Enter Cave entrance (8)
- TRANSPORT: Enter Cave exit (2)
- TRANSPORT: Enter Cracked wall
- TRANSPORT: Enter Hole
- TRANSPORT: Enter Passage
- TRANSPORT: Enter Stronghold
- TRANSPORT: Enter Tunnel (5)
- TRANSPORT: Exit Opening
- TRANSPORT: Exit-through Air rift
- TRANSPORT: Exit-through Body rift
- TRANSPORT: Exit-through Earth rift
- TRANSPORT: Exit-through Fire rift
- TRANSPORT: Exit-through Law rift
- TRANSPORT: Exit-through Mind rift
- TRANSPORT: Exit-through Nature rift
- TRANSPORT: Go-through Cave entrance
- TRANSPORT: Go-through Entrance
- TRANSPORT: Go-through Passage (2)
- TRANSPORT: Jump on Rubber cap mushroom
- TRANSPORT: Jump-over Low fence (2)
- TRANSPORT: Jump-to Floorboards (4)
- TRANSPORT: Open Gate (9)
- TRANSPORT: Open Pyramid entrance
- TRANSPORT: Pass Fallen Tree
- TRANSPORT: Pass Magical Barrier (2)
- TRANSPORT: Pass-through Holy barrier
- TRANSPORT: Pass-through Magic barrier
- TRANSPORT: Swim River
- TRANSPORT: Teleport Mage of Zamorak
- TRANSPORT: Use Exit

## Changes since the last build

Pairs added: 1, removed: 0, fastest way moved more than 10%: 4.

- BANK 3741 3805 0 > PATCH 3733 10273 1: 11.7 s, was 13.5 s
- LANDING 3710 3838 0 > PATCH 3733 10273 1: 12.3 s, was 14.1 s
- PATCH 3715 3835 0 > PATCH 3733 10273 1: 12.3 s, was 14.1 s
- PATCH 3702 3837 0 > PATCH 3733 10273 1: 14.1 s, was 15.9 s
