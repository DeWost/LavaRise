package dev.lavarise.arena;

import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.data.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds an ephemeral, randomly-placed minigame arena somewhere in a world.
 * <p>
 * The arena is a cuboid of the configured radius centred on a random point
 * within {@code procedural.spawn-area}, registered in the {@code GameManager}
 * only (never persisted, marked {@link Arena#markTransient()}). It snapshots and
 * restores its volume like any minigame, so after the game the world is left
 * clean and the next one spawns at a fresh random spot (random rotation).
 * </p>
 *
 * @author DeWost
 */
public final class ProceduralArenaFactory {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private ProceduralArenaFactory() {}

    /**
     * Generate, register and session-init a fresh random arena.
     */
    public static Arena create(LavaRisePlugin plugin) {
        final ConfigManager cfg = plugin.getConfigManager();

        World world = plugin.getServer().getWorld(cfg.getProceduralWorld());
        if (world == null) world = plugin.getServer().getWorlds().get(0);

        // Pick a random centre, preferring a playable (non-ocean/river/frozen) biome.
        final int half = Math.max(0, cfg.getProceduralSpawnArea() / 2);
        int tx = 0, tz = 0;
        for (int attempt = 0; attempt < 24; attempt++) {
            tx = half == 0 ? 0 : ThreadLocalRandom.current().nextInt(-half, half + 1);
            tz = half == 0 ? 0 : ThreadLocalRandom.current().nextInt(-half, half + 1);
            if (half == 0 || isPlayableBiome(world, tx, tz)) break;
        }
        final int cx = tx;
        final int cz = tz;
        final int r = cfg.getProceduralRadius();

        // Lava rises from the TOP block (surface) upward — not from bedrock — so
        // it threatens players immediately and the scoreboard height matches what
        // they see. The rise span comes from the configured start/max distance.
        final int surfaceY = world.getHighestBlockYAt(cx, cz);
        final int riseHeight = Math.max(16, cfg.getProceduralLavaMaxY() - cfg.getProceduralLavaStartY());
        final int startY = surfaceY;
        final int maxY = Math.min(world.getMaxHeight() - 1, surfaceY + riseHeight);

        final Location spawn = new Location(world, cx + 0.5, surfaceY + 1, cz + 0.5);
        final Location spectator = new Location(world, cx + 0.5, maxY + 5, cz + 0.5);

        final ArenaConfig config = new ArenaConfig(
                "_random_" + COUNTER.incrementAndGet(), world,
                new Location(world, cx - r, startY, cz - r),
                new Location(world, cx + r, maxY, cz + r),
                spawn, spawn, spectator,
                1, cfg.getDefaultMaxPlayers(),
                cfg.getDefaultCountdown(), cfg.getDefaultCountdown(),
                cfg.getLavaRiseInterval(), cfg.getLavaRiseAmount(),
                startY, maxY,
                cfg.isDefaultPvp(), cfg.isDefaultBlockBreak(), cfg.isDefaultBlockPlace(),
                cfg.isDefaultKeepInventory(), cfg.isDefaultHunger(), "minigame");

        final Arena arena = new Arena(plugin, config).markTransient();
        plugin.getGameManager().registerArena(arena);
        arena.createSession();
        plugin.debug("Generated procedural arena " + config.name() + " at " + cx + "," + cz);
        return arena;
    }

    /** Avoid oceans, rivers, frozen and beach biomes for fairer arenas. */
    private static boolean isPlayableBiome(World world, int x, int z) {
        try {
            final int y = world.getHighestBlockYAt(x, z);
            final String biome = world.getBiome(x, y, z).getKey().getKey().toLowerCase(java.util.Locale.ROOT);
            return !(biome.contains("ocean") || biome.contains("river")
                    || biome.contains("frozen") || biome.contains("beach"));
        } catch (Throwable t) {
            return true; // can't determine — accept the spot
        }
    }
}
