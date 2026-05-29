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

        final int half = Math.max(0, cfg.getProceduralSpawnArea() / 2);
        final int cx = half == 0 ? 0 : ThreadLocalRandom.current().nextInt(-half, half + 1);
        final int cz = half == 0 ? 0 : ThreadLocalRandom.current().nextInt(-half, half + 1);
        final int r = cfg.getProceduralRadius();
        final int startY = cfg.getProceduralLavaStartY();
        final int maxY = cfg.getProceduralLavaMaxY();

        // Spawn players on the surface above the random centre.
        final int surfaceY = world.getHighestBlockYAt(cx, cz) + 1;
        final Location spawn = new Location(world, cx + 0.5, Math.max(surfaceY, startY + 1), cz + 0.5);
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
}
