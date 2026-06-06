package dev.lavarise.core;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ProceduralArenaFactory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Fully-autonomous arena rotation. When {@code auto-arena.enabled}, a periodic
 * task keeps exactly one open procedural arena available at all times:
 * <ul>
 *   <li>if no open arena exists, it generates a fresh one (random location);</li>
 *   <li>lobbies auto-start once min players join (handled by {@code LobbyState});</li>
 *   <li>finished arenas are transient — they restore their terrain and unregister,
 *       so the next check spins up a new one — a self-sustaining loop, no commands.</li>
 * </ul>
 * With {@code auto-arena.auto-join}, online players not already in a game are
 * pulled into the open arena automatically.
 *
 * @author DeWost
 */
public final class AutoArenaController {

    private static final String AUTO_PREFIX = "_random_";

    private final LavaRisePlugin plugin;
    private BukkitTask task;

    public AutoArenaController(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().isAutoArenaEnabled()) return;
        if (!plugin.getConfigManager().isProceduralEnabled()) {
            plugin.getLogger().warning("auto-arena requires procedural arenas enabled — skipping.");
            return;
        }
        final long period = plugin.getConfigManager().getAutoArenaCheckInterval() * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
        plugin.getLogger().info("Auto-arena enabled — maintaining a continuous arena rotation.");
        tick(); // ensure one is ready immediately
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (findOpenAutoArena() == null) {
            ProceduralArenaFactory.create(plugin);
        }
        if (plugin.getConfigManager().isAutoArenaAutoJoin()) {
            autoEnroll();
        }
    }

    private Arena findOpenAutoArena() {
        for (Arena arena : plugin.getGameManager().getAllArenas()) {
            if (arena.getName().startsWith(AUTO_PREFIX)
                    && arena.getSession() != null
                    && arena.getSession().isJoinable()
                    && arena.getSession().getPlayerCount() < arena.getConfig().maxPlayers()) {
                return arena;
            }
        }
        return null;
    }

    private void autoEnroll() {
        final Arena open = findOpenAutoArena();
        if (open == null) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.getGameManager().isInGame(player)) {
                plugin.getGameManager().addPlayerToArena(player, open);
            }
        }
    }
}
