package dev.lavarise.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounds math is order-independent of which corner is min/max. */
@ExtendWith(MockitoExtension.class)
class ArenaConfigTest {

    @Mock
    World world;
    @Mock
    World otherWorld;

    private static ArenaConfig of(Location c1, Location c2) {
        return new ArenaConfig("t", null, c1, c2, null, null, null,
                1, 16, 10, 10, 60, 1, -64, 320,
                true, true, true, false, true, "minigame");
    }

    private ArenaConfig cuboid() {
        // X 0..4, Y 60..70, Z 0..9 in `world`.
        return new ArenaConfig("t", world,
                new Location(world, 0, 60, 0), new Location(world, 4, 70, 9),
                null, null, null, 1, 16, 10, 10, 60, 1, -64, 320,
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

    @Test
    void containsHorizontalIgnoresYButRespectsXZAndWorld() {
        ArenaConfig a = cuboid();
        // Inside the X/Z footprint at any Y (corner + interior).
        assertTrue(a.containsHorizontal(new Location(world, 0, 0, 0)));
        assertTrue(a.containsHorizontal(new Location(world, 4, 1000, 9)));
        assertTrue(a.containsHorizontal(new Location(world, 2, -100, 5)));
        // Outside the footprint.
        assertFalse(a.containsHorizontal(new Location(world, 5, 65, 5)));
        assertFalse(a.containsHorizontal(new Location(world, 2, 65, 10)));
        assertFalse(a.containsHorizontal(new Location(world, -1, 65, 0)));
        // Different world is never contained.
        assertFalse(a.containsHorizontal(new Location(otherWorld, 2, 65, 5)));
    }

    @Test
    void containsAppliesVerticalBoundsToo() {
        ArenaConfig a = cuboid();
        assertTrue(a.contains(new Location(world, 2, 60, 5)));
        assertTrue(a.contains(new Location(world, 2, 70, 5)));
        assertTrue(a.contains(new Location(world, 2, 65, 5)));
        // Within footprint but outside the Y range.
        assertFalse(a.contains(new Location(world, 2, 59, 5)));
        assertFalse(a.contains(new Location(world, 2, 71, 5)));
        // Outside footprint is false regardless of Y.
        assertFalse(a.contains(new Location(world, 9, 65, 5)));
    }
}
