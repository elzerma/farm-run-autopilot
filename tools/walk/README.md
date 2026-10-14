# Walking measurements (dev only)

One-off tools for Sprint 8, not part of the plugin build. They read Shortest Path's collision map and door
list (BSD-2, see `THIRD_PARTY_NOTICES`) from a local clone and write numbers the plugin ships with:

- `Measure.java`: walking tiles from each teleport landing to its stop's nearest patch, from each patch to its
  bank, spirit tree, fairy ring, quetzal pad and hot air balloon, and named walks. The results were copied into
  `TravelTimes.java` by hand.
- `Grid.java`: walking tiles from each stop's patches out to 150 tiles, per 4x4 block, written to
  `src/main/resources/com/farmrunautopilot/walk-grid.bin` (read by `WalkGrid.java`).
- `Walk.java`: loads the collision map; the shared walking rules (8 directions, the game's diagonal rule).

The `.tsv` files are the coordinates used (Shortest Path commit 7e81a40, plus `PatchPoints`).

To refresh after a Shortest Path update (clone it next to this folder first):

```
javac -d out Walk.java Measure.java Grid.java
java -Xmx2g -cp out Grid <shortest-path>/src/main/resources/collision-map.zip patches.tsv ../../src/main/resources/com/farmrunautopilot/walk-grid.bin
java -Xmx2g -cp out Measure <shortest-path>/src/main/resources/collision-map.zip .
```

Review big changes before copying any number into `TravelTimes.java`.
