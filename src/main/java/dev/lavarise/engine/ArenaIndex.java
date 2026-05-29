package dev.lavarise.engine;

import dev.lavarise.arena.ArenaConfig;

/**
 * Shared linear-index math for the arena volume.
 * <p>
 * The snapshot ({@code ActiveState.takeSnapshot}) walks the volume in
 * {@code y → z → x} order incrementing a counter; {@link WorldResetter} restores
 * in the same order using {@link #index}. Both MUST agree byte-for-byte or
 * restores corrupt — this single source of truth guarantees that and is covered
 * by {@code ArenaIndexTest}.
 * </p>
 *
 * @author DeWost
 */
public final class ArenaIndex {

    private ArenaIndex() {}

    /**
     * Linear index of a block within the arena volume, matching the snapshot's
     * {@code y → z → x} iteration order.
     */
    public static int index(ArenaConfig config, int x, int y, int z) {
        final int width = config.maxX() - config.minX() + 1;
        final int depth = config.maxZ() - config.minZ() + 1;
        final int localX = x - config.minX();
        final int localZ = z - config.minZ();
        final int localY = y - config.lavaStartY();
        return localY * (depth * width) + localZ * width + localX;
    }
}
