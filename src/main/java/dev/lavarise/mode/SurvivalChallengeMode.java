package dev.lavarise.mode;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.state.ActiveState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for world-wide Survival Challenge events.
 * <p>
 * Rather than duplicate the engine, it synthesises an ephemeral {@link Arena}
 * (a cuboid around the world spawn of the configured radius, mode
 * {@code survival_challenge}) and drives it straight into {@link ActiveState}.
 * These arenas live only in the {@code GameManager} registry — never in
 * {@code ArenaRepository} — so they are never persisted to disk.
 * </p>
 *
 * @author DeWost
 */
public final class SurvivalChallengeMode {

    private final LavaRisePlugin plugin;
    private final Map<String, Arena> active = new HashMap<>(); // world name → synthetic arena

    public SurvivalChallengeMode(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(World world) {
        return active.containsKey(world.getName());
    }

    public boolean startChallenge(World world) {
        if (active.containsKey(world.getName())) return false;

        final int radius = Math.max(16, plugin.getConfigManager().getSurvivalRadius());
        if (radius > 1000) {
            plugin.getLogger().warning("Survival radius " + radius + " is very large; expect heavy load. "
                    + "Raise performance.max-blocks-per-tick accordingly.");
        }
        final Location spawn = world.getSpawnLocation();
        final int cx = spawn.getBlockX();
        final int cz = spawn.getBlockZ();
        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight() - 1;

        final ArenaConfig cfg = new ArenaConfig(
                "_survival_" + world.getName(), world,
                new Location(world, cx - radius, minY, cz - radius),
                new Location(world, cx + radius, maxY, cz + radius),
                spawn,        // lobby spawn
                null,         // no game-spawn teleport — players stay put
                spawn,        // spectator spawn
                1, Integer.MAX_VALUE, 0, 0,
                plugin.getConfigManager().getLavaRiseInterval(),
                plugin.getConfigManager().getLavaRiseAmount(),
                minY, maxY,
                true, true, true, false, true,
                "survival_challenge");

        final Arena arena = new Arena(plugin, cfg);
        plugin.getGameManager().registerArena(arena);
        final ArenaSession session = arena.createSession();

        for (Player p : world.getPlayers()) {
            session.enroll(p);
            plugin.getGameManager().mapPlayer(p.getUniqueId(), arena);
        }

        session.transitionTo(new ActiveState(plugin, arena, session));
        active.put(world.getName(), arena);

        if (plugin.getConfigManager().isSurvivalAnnounce()) {
            plugin.getServer().broadcast(plugin.getMiniMessage().deserialize(
                    plugin.getConfigManager().getMessage("event.broadcast-start")));
        }
        return true;
    }

    public boolean stopChallenge(World world) {
        final Arena arena = active.remove(world.getName());
        if (arena == null) return false;
        if (arena.getSession() != null) {
            arena.getSession().forceEnd();
        }
        arena.destroySession();
        plugin.getGameManager().unregisterArena(arena.getName());
        if (plugin.getConfigManager().isSurvivalAnnounce()) {
            plugin.getServer().broadcast(plugin.getMiniMessage().deserialize(
                    plugin.getConfigManager().getMessage("event.broadcast-end")));
        }
        return true;
    }
}
