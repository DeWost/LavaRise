package dev.lavarise.core;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ProceduralArenaFactory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Fully-autonomous arena rotation, modelled as a durable scheduled workflow.
 * When {@code auto-arena.enabled}, a periodic task keeps exactly one open
 * procedural arena available at all times:
 * <ul>
 *   <li>if no open arena exists, it generates a fresh one (random location);</li>
 *   <li>lobbies auto-start once min players join (handled by {@code LobbyState});</li>
 *   <li>finished arenas are transient — they restore their terrain and unregister,
 *       so the next check spins up a new one — a self-sustaining loop, no commands.</li>
 * </ul>
 * With {@code auto-arena.auto-join}, online players not already in a game are
 * pulled into the open arena automatically.
 *
 * <h2>Reliability (workflow patterns)</h2>
 * Each tick is an idempotent checkpoint — it only generates when no open arena
 * exists, so a missed or replayed tick never double-spawns. The generation step
 * is isolated in a try/catch; a failure triggers <b>exponential backoff</b>
 * (1→2→4→8→16 cycles) instead of hammering a broken world, and after
 * {@link #MAX_CONSECUTIVE_FAILURES} failures the workflow <b>dead-letters</b>
 * (halts with a clear admin log) rather than spamming forever. The auto-join
 * step is isolated separately so it can never take down generation. Counters are
 * exposed for observability.
 *
 * @author DeWost
 */
public final class AutoArenaController {

    private static final String AUTO_PREFIX = "_random_";

    /** Halt the workflow after this many consecutive generation failures. */
    static final int MAX_CONSECUTIVE_FAILURES = 5;

    /** Largest backoff window, in controller cycles. */
    static final int MAX_BACKOFF_CYCLES = 16;

    private final LavaRisePlugin plugin;
    private BukkitTask task;

    // ── Workflow state / observability ──────────────────────
    private long generatedCount = 0L;
    private long failureCount = 0L;
    private int consecutiveFailures = 0;
    private int backoffCyclesRemaining = 0;
    private volatile boolean deadLettered = false;

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

    /**
     * Reset the workflow's failure state. Called on {@code /lavarise reload} so an
     * admin can clear a dead-lettered controller without restarting the server.
     */
    public void onReload() {
        if (deadLettered) {
            plugin.getLogger().info("Auto-arena workflow re-armed after reload.");
        }
        deadLettered = false;
        consecutiveFailures = 0;
        backoffCyclesRemaining = 0;
    }

    private void tick() {
        if (deadLettered) return; // workflow halted — see onReload()

        // Exponential backoff: skip cycles after a failure so we never hammer a
        // broken world every check-interval.
        if (backoffCyclesRemaining > 0) {
            backoffCyclesRemaining--;
            return;
        }

        // Idempotent checkpoint: generate only when there is no open arena.
        if (findOpenAutoArena() == null) {
            try {
                ProceduralArenaFactory.create(plugin);
                generatedCount++;
                if (consecutiveFailures > 0) {
                    plugin.getLogger().info("Auto-arena recovered after "
                            + consecutiveFailures + " failed attempt(s).");
                }
                consecutiveFailures = 0;
            } catch (Throwable t) {
                onGenerationFailed(t);
                return; // don't run auto-join on a failed cycle
            }
        }

        // Isolated step — a join hiccup must never break the generation loop.
        if (plugin.getConfigManager().isAutoArenaAutoJoin()) {
            try {
                autoEnroll();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Auto-arena auto-join step failed (continuing).", t);
            }
        }
    }

    private void onGenerationFailed(Throwable t) {
        failureCount++;
        consecutiveFailures++;
        final int backoff = backoffCycles(consecutiveFailures);
        backoffCyclesRemaining = backoff - 1; // this cycle already counts as the first

        plugin.getLogger().log(Level.WARNING, "Auto-arena generation failed (attempt "
                + consecutiveFailures + "/" + MAX_CONSECUTIVE_FAILURES + ") — backing off "
                + backoff + " cycle(s).", t);

        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            deadLettered = true;
            plugin.getLogger().severe("Auto-arena workflow halted after " + MAX_CONSECUTIVE_FAILURES
                    + " consecutive failures. Check the procedural world/config, then run "
                    + "/lavarise reload to restart the rotation.");
        }
    }

    /**
     * Exponential backoff window (in controller cycles) for the Nth consecutive
     * failure: 1, 2, 4, 8, 16, 16, … (capped at {@link #MAX_BACKOFF_CYCLES}).
     * Pure function — unit tested.
     */
    static int backoffCycles(int consecutiveFailures) {
        if (consecutiveFailures <= 1) return 1;
        final int shift = Math.min(consecutiveFailures - 1, 4); // 2^4 = 16 cap
        return Math.min(MAX_BACKOFF_CYCLES, 1 << shift);
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

    // ── Observability ───────────────────────────────────────

    /** Total arenas successfully generated by the workflow this session. */
    public long getGeneratedCount() {
        return generatedCount;
    }

    /** Total generation failures observed this session. */
    public long getFailureCount() {
        return failureCount;
    }

    /** True once the workflow has halted after too many consecutive failures. */
    public boolean isDeadLettered() {
        return deadLettered;
    }
}
