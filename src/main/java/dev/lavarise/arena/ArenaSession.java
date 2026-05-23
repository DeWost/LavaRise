package dev.lavarise.arena;

import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.state.GameState;
import dev.lavarise.state.LobbyState;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.data.BlockData;

/**
 * Mutable game session state for an active arena.
 * <p>
 * Tracks players, spectators, current FSM state, lava Y-level,
 * and timing. Created when a game is queued, destroyed when ended.
 * </p>
 */
public final class ArenaSession {

    private final LavaRisePlugin plugin;
    private final Arena arena;

    public Arena getArena() {
        return arena;
    }

    /** Active players still alive */
    private final Set<UUID> alivePlayers = ConcurrentHashMap.newKeySet();

    /** Eliminated players watching as spectators */
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();

    /** Current game state (FSM) */
    private GameState currentState;

    /** Current lava Y-level */
    private int currentLavaY;

    /** Scheduler task ID for the main game loop */
    private int taskId = -1;

    /** Game start timestamp (for duration tracking) */
    private long startTime;

    /** Kaizen Memory Snapshot of the arena */
    private int[] snapshotIndices;
    private BlockData[] snapshotBlocks;

    public ArenaSession(LavaRisePlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.currentLavaY = arena.getConfig().lavaStartY();
        // Start in lobby state
        this.currentState = new LobbyState(plugin, arena, this);
        this.currentState.onEnter();
    }

    // ── State Machine ───────────────────────────────────────

    /**
     * Transition to a new game state.
     * Calls onExit() on the old state and onEnter() on the new one.
     */
    public void transitionTo(GameState newState) {
        plugin.debug("Arena " + arena.getName() + ": "
                + currentState.getClass().getSimpleName() + " → "
                + newState.getClass().getSimpleName());
        currentState.onExit();
        currentState = newState;
        currentState.onEnter();
    }

    public GameState getCurrentState() {
        return currentState;
    }

    // ── Player Management ───────────────────────────────────

    /**
     * Add a player to the game. Called by GameManager.
     */
    public void addPlayer(Player player) {
        alivePlayers.add(player.getUniqueId());
        currentState.onPlayerJoin(player);
    }

    /**
     * Remove a player from the game (voluntary leave).
     */
    public void removePlayer(Player player) {
        final UUID uuid = player.getUniqueId();
        alivePlayers.remove(uuid);
        spectators.remove(uuid);
        currentState.onPlayerLeave(player);
    }

    /**
     * Eliminate a player (died to lava) — move to spectator.
     */
    public void eliminatePlayer(Player player) {
        final UUID uuid = player.getUniqueId();
        if (!alivePlayers.remove(uuid)) return;

        spectators.add(uuid);

        // Set to spectator mode
        player.setGameMode(GameMode.SPECTATOR);

        // Teleport to spectator spawn if set
        if (arena.getConfig().spectatorSpawn() != null) {
            player.teleport(arena.getConfig().spectatorSpawn());
        }

        currentState.onPlayerEliminated(player);

        plugin.debug(player.getName() + " eliminated in " + arena.getName()
                + " — " + alivePlayers.size() + " alive");
    }

    /**
     * Check if this session is accepting new players.
     */
    public boolean isJoinable() {
        return currentState.isJoinable();
    }

    // ── Queries ─────────────────────────────────────────────

    public int getPlayerCount() {
        return alivePlayers.size() + spectators.size();
    }

    public int getAliveCount() {
        return alivePlayers.size();
    }

    public Set<UUID> getAlivePlayers() {
        return Collections.unmodifiableSet(alivePlayers);
    }

    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public boolean isAlive(UUID playerId) {
        return alivePlayers.contains(playerId);
    }

    public boolean isSpectator(UUID playerId) {
        return spectators.contains(playerId);
    }

    /**
     * Get all UUIDs in this session (alive + spectators).
     */
    public Set<UUID> getAllPlayerIds() {
        final Set<UUID> all = new HashSet<>(alivePlayers);
        all.addAll(spectators);
        return Collections.unmodifiableSet(all);
    }

    // ── Lava Level ──────────────────────────────────────────

    public int getCurrentLavaY() {
        return currentLavaY;
    }

    public void setCurrentLavaY(int y) {
        this.currentLavaY = y;
    }

    public void raiseLava(int amount) {
        this.currentLavaY = Math.min(
                currentLavaY + amount,
                arena.getConfig().lavaMaxY()
        );
    }

    public boolean isLavaAtMax() {
        return currentLavaY >= arena.getConfig().lavaMaxY();
    }

    // ── Timing ──────────────────────────────────────────────

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getElapsedSeconds() {
        if (startTime == 0) return 0;
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    // ── Task Management ─────────────────────────────────────

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public void cancelTask() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    // ── Lifecycle ───────────────────────────────────────────

    /**
     * Force-end the game. Called on plugin disable or admin command.
     */
    public void forceEnd() {
        cancelTask();
        currentState.onExit();

        // Restore all players
        for (UUID uuid : getAllPlayerIds()) {
            final Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setGameMode(GameMode.SURVIVAL);
                if (arena.getConfig().lobbySpawn() != null) {
                    player.teleport(arena.getConfig().lobbySpawn());
                }
            }
        }

        alivePlayers.clear();
        spectators.clear();
        plugin.debug("Session force-ended for " + arena.getName());
    }

    // ── Kaizen Snapshot ─────────────────────────────────────
    
    public void setSnapshot(int[] indices, BlockData[] blocks) {
        this.snapshotIndices = indices;
        this.snapshotBlocks = blocks;
    }
    
    public int[] getSnapshotIndices() {
        return snapshotIndices;
    }
    
    public BlockData[] getSnapshotBlocks() {
        return snapshotBlocks;
    }
}
