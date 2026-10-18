# Travel atlas report

Built 2026-10-18 by `tools/atlas` from Shortest Path `37abff3a3345185fc11fccecae04d8170de25a11`. Times are walk tiles x 0.3 s + transport ticks x 0.6 s (a 0-tick transport counts as 1, an agility shortcut as 2), before the plugin's set-off overhead. Sweep budget 300 (banks 150), ways over 300 s dropped, up to 8 questions and 6 ways a pair.

## Build

| What | Count |
|---|---|
| Places: patches (by tile) | 71 |
| Places: banks near a patch | 124 |
| Places: teleport and hop landings | 435 |
| Places: hubs (where a hop is taken) | 192 |
| Teleports (anywhere, or from the house) | 986 |
| Hops (fairy rings, spirit trees, quetzals, gliders, charter ships...) | 6935 |
| Pairs asked (start within the sweep budget) | 11429 |
| Questions asked | 34208 |
| Searches stopped at the 4 s limit | 7 |
| Pairs with at least one way | 11398 |
| Ways kept | 20533 |
| Hops kept (walked to and leading somewhere) | 6935 |
| Build time (s) | 464 |
| `places.tsv` | 50 KB |
| `teleports.tsv` | 82 KB |
| `hops.tsv` | 488 KB |
| `transports.tsv` | 58 KB |
| `requirements.tsv` | 28 KB |
| `ways.tsv` | 491 KB |
| All data files | 1199 KB |

## Checks

- **No way to:** FOSSIL_ISLAND_SEAWEED_NORTH (atlas only), FOSSIL_ISLAND_SEAWEED_SOUTH (atlas only), FARMING_GUILD_REDWOOD (atlas only), FARMING_GUILD_HESPORI (atlas only), CORAL_NURSERY_WEST (atlas only), CORAL_NURSERY_EAST (atlas only)
- Every patch the plugin runs has a way from a teleport or hop landing.
- Only reachable with a requirement (no "needs nothing" way within the budget): ANGLERS_RETREAT_HARDWOOD (atlas only)

## Today's travel methods against the atlas

Each `TravelMethod` from its landing (`tools/walk/methods.tsv`) to the nearest patch the plugin runs at its stop. Plugin = `TravelTimes` tiles x 0.3 s + its extra seconds. Flagged when 3 s or more apart.

| Method | Plugin | Atlas fastest | Needs | Atlas, needs nothing | Gap |
|---|---|---|---|---|---|
| LUMBRIDGE_TELEPORT | 13.5 s | 13.8 s (walk 42 tiles; Open Large door (1t) > Open Door (1t)) | nothing | 13.8 s | +0.3 s |
| LUMBRIDGE_HOME_TELEPORT | 13.5 s | 13.8 s (walk 42 tiles; Open Large door (1t) > Open Door (1t)) | nothing | 13.8 s | +0.3 s |
| DIARY_CAPE_LUMBRIDGE | 15.3 s | 15.6 s (walk 52) | nothing | 15.6 s | +0.3 s |
| VARROCK_TELEPORT | 11.1 s | 11.4 s (walk 38) | nothing | 11.4 s | +0.3 s |
| DIARY_CAPE_VARROCK | 13.8 s | 14.4 s (walk 48) | nothing | 14.4 s | +0.6 s |
| SKILLS_NECKLACE_COOKS_GUILD | 27.9 s | 28.2 s (walk 94) | nothing | 28.2 s | +0.3 s |
| RING_OF_WEALTH_GRAND_EXCHANGE | 24.6 s | 24.9 s (walk 83) | nothing | 24.9 s | +0.3 s |
| SPIRIT_TREE_GRAND_EXCHANGE | 24.6 s | 25.2 s (walk 84) | nothing | 25.2 s | +0.6 s |
| RING_OF_WEALTH_FALADOR_PARK | 2.1 s | 2.4 s (walk 8) | nothing | 2.4 s | +0.3 s |
| FALADOR_TELEPORT_TO_PARK | 11.7 s | 12.0 s (walk 40) | nothing | 12.0 s | +0.3 s |
| SKILLS_NECKLACE_MINING_GUILD | 33.3 s | 29.1 s (walk 95 tiles; Climb-up Staircase (0t)) | nothing | 29.1 s | -4.2 s ** |
| HOUSE_PORTAL_TAVERLEY | 14.1 s | 14.4 s (walk 48) | nothing | 14.4 s | +0.3 s |
| TAVERLEY_TABLET | 14.1 s | 14.4 s (walk 48) | nothing | 14.4 s | +0.3 s |
| CONSTRUCTION_CAPE_TAVERLEY | 14.4 s | 14.4 s (walk 48) | nothing | 14.4 s | +0.0 s |
| FALADOR_TELEPORT_TO_TAVERLEY | 26.7 s | 20.1 s (walk 65 tiles; Climb-up Climbing rocks (1t)) | 66 Agility | 27.0 s | -6.6 s ** |
| GAMES_NECKLACE_BURTHORPE | 35.1 s | 35.4 s (walk 118) | nothing | 35.4 s | +0.3 s |
| COMBAT_BRACELET_WARRIORS_GUILD | 34.5 s | 34.8 s (walk 116) | nothing | 34.8 s | +0.3 s |
| SLAYER_RING_STRONGHOLD_CAVE | 1.5 s | 1.8 s (walk 6) | nothing | 1.8 s | +0.3 s |
| SPIRIT_TREE_GNOME_STRONGHOLD | 3.6 s | 3.9 s (walk 13) | nothing | 3.9 s | +0.3 s |
| ROYAL_SEED_POD | 15.0 s | 14.1 s (walk 45 tiles; Open Tree Door (1t)) | nothing | 14.1 s | -0.9 s |
| FARMING_CAPE | 2.4 s | 2.7 s (walk 9) | nothing | 2.7 s | +0.3 s |
| SKILLS_NECKLACE_FARMING_GUILD | 2.4 s | 2.7 s (walk 9) | nothing | 2.7 s | +0.3 s |
| MAX_CAPE_FARMING_GUILD | 2.4 s | 2.7 s (walk 9) | nothing | 2.7 s | +0.3 s |
| SPIRIT_TREE_FARMING_GUILD | 2.1 s | 2.4 s (walk 8) | nothing | 2.4 s | +0.3 s |
| FAIRY_RING_CIR | 24.6 s | 24.6 s (walk 82) | nothing | 24.6 s | +0.0 s |
| BATTLEFRONT_TELEPORT | 36.3 s | 36.6 s (walk 122) | nothing | 36.6 s | +0.3 s |
| RADAS_BLESSING_MOUNT_KARUULM | 30.0 s | 46.8 s (walk 124 tiles; Climb Rocks (8t) > Climb Rocks (8t)) | 62 Agility, 29 Agility | 68.7 s | +16.8 s ** |
| PENDANT_OF_ATES_NEMUS_RETREAT | 16.2 s | 14.1 s (walk 43 tiles; Climb-over Broken wall (2t)) | 24 Agility | 16.5 s | -2.1 s |
| QUETZAL_AUBURNVALE | 21.3 s | 24.9 s (walk 73 tiles; Climb-into Tunnel (5t)) | 33 Agility | 30.9 s | +3.6 s ** |
| FAIRY_RING_AIS | 19.5 s | 26.1 s (walk 69 tiles; Cross Stepping stone (7t) > Climb-over Broken wall (2t)) | 36 Agility, 24 Agility | 32.7 s | +6.6 s ** |
| SPIRIT_TREE_TREE_GNOME_VILLAGE | 16.5 s | 16.8 s (walk 46 tiles; Follow Elkoy (5t)) | TREEQUEST > 0 | 73.8 s | +0.3 s |
| FAIRY_RING_CIQ | 70.1 s | 23.1 s (walk 77) | nothing | 23.1 s | -47.0 s ** |
| CAMELOT_TELEPORT | 17.1 s | 16.5 s (walk 55) | nothing | 16.5 s | -0.6 s |
| CATHERBY_TELEPORT | 5.7 s | 5.7 s (walk 19) | nothing | 5.7 s | +0.0 s |
| HOUSE_PORTAL_BRIMHAVEN | 9.6 s | 9.9 s (walk 33) | nothing | 9.9 s | +0.3 s |
| BRIMHAVEN_TABLET | 9.3 s | 9.9 s (walk 33) | nothing | 9.9 s | +0.6 s |
| CONSTRUCTION_CAPE_BRIMHAVEN | 10.2 s | 9.9 s (walk 33) | nothing | 9.9 s | -0.3 s |
| SPIRIT_TREE_BRIMHAVEN | 9.9 s | 10.2 s (walk 34) | nothing | 10.2 s | +0.3 s |
| ARDOUGNE_TELEPORT_AND_BOAT | 30.6 s | 23.1 s (walk 65 tiles; Brimhaven (6t)) | COINS x30, any ship |  | -7.5 s ** |
| GLORY_KARAMJA | 49.2 s | 49.5 s (walk 165) | nothing | 49.5 s | +0.3 s |
| TELEPORT_CRYSTAL_LLETYA | 4.5 s | 4.8 s (walk 16) | nothing | 4.8 s | +0.3 s |
| QUETZAL_KASTORI | 9.6 s | 2.4 s (walk 8) | nothing | 2.4 s | -7.2 s ** |
| PENDANT_OF_ATES_NORTH_KASTORI | 9.9 s | 10.2 s (walk 34) | nothing | 10.2 s | +0.3 s |
| EXPLORERS_RING_CABBAGE_PATCH | 5.7 s | 5.1 s (walk 17) | nothing | 5.1 s | -0.6 s |
| DRAYNOR_MANOR_TELEPORT | 22.5 s | 22.8 s (walk 76) | nothing | 22.8 s | +0.3 s |
| SPIRIT_TREE_PORT_SARIM | 16.2 s | 15.6 s (walk 52) | nothing | 15.6 s | -0.6 s |
| GLORY_DRAYNOR | 28.5 s | 27.9 s (walk 81 tiles; Climb-over Stile (6t)) | nothing | 27.9 s | -0.6 s |
| FALADOR_TELEPORT_TO_FARM | 38.1 s | 38.4 s (walk 128) | nothing | 38.4 s | +0.3 s |
| FAIRY_RING_ALQ | 12.3 s | 12.0 s (walk 40) | nothing | 12.0 s | -0.3 s |
| ECTOPHIAL | 17.1 s | 17.4 s (walk 58) | nothing | 17.4 s | +0.3 s |
| FENKENSTRAINS_CASTLE_TELEPORT | 16.5 s | 16.8 s (walk 56) | nothing | 16.8 s | +0.3 s |
| KHARYRLL_TELEPORT | 40.2 s | 40.5 s (walk 135) | nothing | 40.5 s | +0.3 s |
| ARDOUGNE_CLOAK_FARM | 0.9 s | 0.9 s (walk 3) | nothing | 0.9 s | +0.0 s |
| SKILLS_NECKLACE_FISHING_GUILD | 18.0 s | 17.4 s (walk 58) | nothing | 17.4 s | -0.6 s |
| MAX_CAPE_FISHING_GUILD | 21.9 s | 21.3 s (walk 71) | nothing | 21.3 s | -0.6 s |
| FISHING_GUILD_TELEPORT | 17.4 s | 17.4 s (walk 58) | nothing | 17.4 s | +0.0 s |
| COMBAT_BRACELET_RANGING_GUILD | 20.7 s | 21.0 s (walk 70) | nothing | 21.0 s | +0.3 s |
| FAIRY_RING_BLR | 21.3 s | 21.3 s (walk 71) | nothing | 21.3 s | +0.0 s |
| ARDOUGNE_TELEPORT | 28.5 s | 28.8 s (walk 96) | nothing | 28.8 s | +0.3 s |
| XERICS_TALISMAN_GLADE | 6.3 s | 6.6 s (walk 22) | nothing | 6.6 s | +0.3 s |
| HOUSE_PORTAL_HOSIDIUS | 9.3 s | 9.6 s (walk 32) | nothing | 9.6 s | +0.3 s |
| HOSIDIUS_TABLET | 9.3 s | 9.6 s (walk 32) | nothing | 9.6 s | +0.3 s |
| CONSTRUCTION_CAPE_HOSIDIUS | 9.3 s | 9.6 s (walk 32) | nothing | 9.6 s | +0.3 s |
| KHAREDSTS_MEMOIRS_LANCALLIUMS | 21.6 s | 20.4 s (walk 68) | nothing | 20.4 s | -1.2 s |
| FAIRY_RING_AKR | 26.1 s | 26.1 s (walk 87) | nothing | 26.1 s | +0.0 s |
| SKILLS_NECKLACE_WOODCUTTING_GUILD | 24.9 s | 24.9 s (walk 79 tiles; Cross Stepping stone (2t)) | 45 Agility | 36.6 s | +0.0 s |
| STONY_BASALT_ROOF | 2.1 s | 2.4 s (walk 8) | nothing | 2.4 s | +0.3 s |
| STONY_BASALT_ENTRANCE | 10.5 s | 4.5 s (walk 11 tiles; Climb Rocks (0t)) | 73 Agility | 25.8 s | -6.0 s ** |
| TROLLHEIM_TELEPORT | 62.2 s | 33.6 s (walk 96 tiles; Climb Rocks (0t) > Climb Rocks (0t) > Climb Rocks (0t) > Climb Rocks (0t)) | 43 Agility, 41 Agility, 73 Agility | 66.0 s | -28.6 s ** |
| HARMONY_ISLAND_TELEPORT | 7.5 s | 7.8 s (walk 26) | nothing | 7.8 s | +0.3 s |
| ICY_BASALT | 0.9 s | 1.2 s (walk 4) | nothing | 1.2 s | +0.3 s |
| QUETZAL_HUNTER_GUILD | 13.8 s | 14.1 s (walk 47) | nothing | 14.1 s | +0.3 s |
| HUNTER_CAPE | 21.9 s | 22.2 s (walk 74) | nothing | 22.2 s | +0.3 s |
| MAX_CAPE_HUNTER_GUILD | 21.9 s | 22.2 s (walk 74) | nothing | 22.2 s | +0.3 s |
| CIVITAS_ILLA_FORTIS_TELEPORT | 26.0 s | 32.1 s (walk 107) | nothing | 32.1 s | +6.1 s ** |
| FAIRY_RING_AJP | 27.3 s | 27.9 s (walk 93) | nothing | 27.9 s | +0.6 s |
| LUMBRIDGE_TELEPORT_HOPS | 35.7 s | 36.0 s (walk 120) | nothing | 36.0 s | +0.3 s |
| VARROCK_TELEPORT_HOPS | 32.7 s | 33.0 s (walk 110) | nothing | 33.0 s | +0.3 s |
| COMBAT_BRACELET_CHAMPIONS_GUILD | 16.8 s | 17.1 s (walk 57) | nothing | 17.1 s | +0.3 s |
| CAMELOT_TELEPORT_SEERS_HOPS | 27.3 s | 27.6 s (walk 92) | nothing | 27.6 s | +0.3 s |
| FAIRY_RING_ALS | 24.6 s | 25.2 s (walk 84) | nothing | 25.2 s | +0.6 s |
| COMBAT_BRACELET_RANGING_GUILD_HOPS | 29.4 s | 29.7 s (walk 99) | nothing | 29.7 s | +0.3 s |
| HOUSE_PORTAL_YANILLE | 9.9 s | 10.2 s (walk 34) | nothing | 10.2 s | +0.3 s |
| FAIRY_RING_CIQ_HOPS | 24.6 s | 24.6 s (walk 82) | nothing | 24.6 s | +0.0 s |
| HOUSE_PORTAL_ALDARIN | 18.3 s | 18.6 s (walk 62) | nothing | 18.6 s | +0.3 s |
| FAIRY_RING_CKQ | 1.2 s | 1.8 s (walk 6) | nothing | 1.8 s | +0.6 s |
| QUETZAL_ALDARIN | 12.9 s | 13.2 s (walk 44) | nothing | 13.2 s | +0.3 s |
| TELEPORT_CRYSTAL_PRIFDDINAS | 17.7 s | 18.0 s (walk 60) | nothing | 18.0 s | +0.3 s |
| HOUSE_PORTAL_PRIFDDINAS | 22.5 s | 22.8 s (walk 76) | nothing | 22.8 s | +0.3 s |

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
- TRANSPORT: Open Gate (6)
- TRANSPORT: Open Pyramid entrance
- TRANSPORT: Pass Fallen Tree
- TRANSPORT: Pass Magical Barrier (2)
- TRANSPORT: Pass-through Holy barrier
- TRANSPORT: Pass-through Magic barrier
- TRANSPORT: Swim River
- TRANSPORT: Teleport Mage of Zamorak
- TRANSPORT: Use Exit

## Changes since the last build

First build.
