package dev.lavarise.engine;

import dev.lavarise.arena.ArenaConfig;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the engine's critical invariant: {@link ArenaIndex#index} must
 * match, byte-for-byte, the running counter used by the snapshot's y→z→x walk.
 * If these ever diverge, arena resets corrupt blocks.
 */
class ArenaIndexTest {

    private static ArenaConfig config(int minX, int minZ, int maxX, int maxZ, int startY, int maxY) {
        // World is null — only getBlockX/Y/Z are exercised, which don't need a world.
        Location c1 = new Location(null, minX, startY, minZ);
        Location c2 = new Location(null, maxX, maxY, maxZ);
        return new ArenaConfig("t", null, c1, c2, null, null, null,
                1, 16, 10, 10, 60, 1, startY, maxY,
                true, true, true, false, true, "minigame");
    }

    @Test
    void indexMatchesSnapshotIterationOrder() {
        ArenaConfig cfg = config(0, 0, 4, 9, -2, 3); // 5 (x) * 10 (z) * 6 (y) = 300 cells
        int counter = 0;
        for (int y = cfg.lavaStartY(); y <= cfg.lavaMaxY(); y++) {
            for (int z = cfg.minZ(); z <= cfg.maxZ(); z++) {
                for (int x = cfg.minX(); x <= cfg.maxX(); x++) {
                    assertEquals(counter, ArenaIndex.index(cfg, x, y, z),
                            "index diverged from snapshot counter at " + x + "," + y + "," + z);
                    counter++;
                }
            }
        }
        assertEquals(5 * 10 * 6, counter);
    }

    @Test
    void indicesAreUniqueAndZeroBased() {
        ArenaConfig cfg = config(-3, -3, 3, 3, 0, 5);
        Set<Integer> seen = new HashSet<>();
        for (int y = cfg.lavaStartY(); y <= cfg.lavaMaxY(); y++) {
            for (int z = cfg.minZ(); z <= cfg.maxZ(); z++) {
                for (int x = cfg.minX(); x <= cfg.maxX(); x++) {
                    assertTrue(seen.add(ArenaIndex.index(cfg, x, y, z)), "duplicate index");
                }
            }
        }
        assertEquals(0, ArenaIndex.index(cfg, cfg.minX(), cfg.lavaStartY(), cfg.minZ()));
    }
}
