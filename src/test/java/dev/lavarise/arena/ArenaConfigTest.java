package dev.lavarise.arena;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Bounds math is order-independent of which corner is min/max. */
class ArenaConfigTest {

    private static ArenaConfig of(Location c1, Location c2) {
        return new ArenaConfig("t", null, c1, c2, null, null, null,
                1, 16, 10, 10, 60, 1, -64, 320,
                true, true, true, false, true, "minigame");
    }

    @Test
    void boundsNormaliseRegardlessOfCornerOrder() {
        ArenaConfig a = of(new Location(null, 10, 0, 20), new Location(null, -5, 0, -8));
        assertEquals(-5, a.minX());
        assertEquals(10, a.maxX());
        assertEquals(-8, a.minZ());
        assertEquals(20, a.maxZ());
    }

    @Test
    void widthDepthAndAreaAreInclusive() {
        ArenaConfig a = of(new Location(null, 0, 0, 0), new Location(null, 4, 0, 9));
        assertEquals(5, a.width());
        assertEquals(10, a.depth());
        assertEquals(50, a.area());
    }
}
