package dev.lavarise.arena;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable arena configuration.
 * <p>
 * Loaded from YAML and never mutated during gameplay.
 * Uses Java record for automatic equals/hashCode/toString.
 * </p>
 *
 * @param name         Unique arena identifier
 * @param world        World this arena is in
 * @param corner1      First corner of the arena cuboid
 * @param corner2      Second corner of the arena cuboid
 * @param lobbySpawn   Where players spawn when joining the lobby
 * @param gameSpawn    Where players spawn when game starts
 * @param spectatorSpawn Where spectators spawn
 * @param minPlayers   Minimum players to start
 * @param maxPlayers   Maximum players allowed
 * @param lobbyCountdown Lobby countdown in seconds
 * @param gameCountdown  Pre-game countdown in seconds
 * @param lavaRiseInterval Ticks between each lava rise
 * @param lavaRiseAmount Y-levels per rise
 * @param lavaStartY   Starting Y-level of lava
 * @param lavaMaxY     Maximum Y-level of lava
 * @param pvpEnabled   Whether PvP is enabled
 * @param blockBreak   Whether block breaking is allowed
 * @param blockPlace   Whether block placing is allowed
 * @param keepInventory Whether players keep their items on death
 * @param hunger       Whether hunger/food depletion is enabled during the game
 * @param gameMode     Game mode for this arena
 */
public record ArenaConfig(
        String name,
        World world,
        Location corner1,
        Location corner2,
        Location lobbySpawn,
        Location gameSpawn,
        Location spectatorSpawn,
        int minPlayers,
        int maxPlayers,
        int lobbyCountdown,
        int gameCountdown,
        int lavaRiseInterval,
        int lavaRiseAmount,
        int lavaStartY,
        int lavaMaxY,
        boolean pvpEnabled,
        boolean blockBreak,
        boolean blockPlace,
        boolean keepInventory,
        boolean hunger,
        String gameMode
) {

    /**
     * Get the minimum X coordinate of the arena bounds.
     */
    public int minX() {
        return Math.min(corner1.getBlockX(), corner2.getBlockX());
    }

    /**
     * Get the maximum X coordinate of the arena bounds.
     */
    public int maxX() {
        return Math.max(corner1.getBlockX(), corner2.getBlockX());
    }

    /**
     * Get the minimum Z coordinate of the arena bounds.
     */
    public int minZ() {
        return Math.min(corner1.getBlockZ(), corner2.getBlockZ());
    }

    /**
     * Get the maximum Z coordinate of the arena bounds.
     */
    public int maxZ() {
        return Math.max(corner1.getBlockZ(), corner2.getBlockZ());
    }

    /**
     * Get the width (X-axis) of the arena.
     */
    public int width() {
        return maxX() - minX() + 1;
    }

    /**
     * Get the depth (Z-axis) of the arena.
     */
    public int depth() {
        return maxZ() - minZ() + 1;
    }

    /**
     * Get total horizontal area in blocks.
     */
    public int area() {
        return width() * depth();
    }

    /**
     * Check if a location is within the arena bounds (horizontal).
     */
    public boolean containsHorizontal(Location loc) {
        if (!loc.getWorld().equals(world)) return false;
        final int x = loc.getBlockX();
        final int z = loc.getBlockZ();
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }

    /**
     * Check if a location is fully within the arena cuboid (3D).
     */
    public boolean contains(Location loc) {
        if (!containsHorizontal(loc)) return false;
        final int y = loc.getBlockY();
        final int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        final int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        return y >= minY && y <= maxY;
    }
}
