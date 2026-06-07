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
    private final Set<UUID> alivePlayersView = Collections.unmodifiableSet(alivePlayers);

    /** Eliminated players watching as spectators */
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spectatorsView = Collections.unmodifiableSet(spectators);

    /** Current game state (FSM) */
    private GameState currentState;

    /** Strategy for the arena's game mode (teams, survival, admin event). */
    private final dev.lavarise.mode.GameModeHandler modeHandler;

    /** Player UUID → team id (only used in teams mode). */
    private final Map<UUID, Integer> playerTeam = new ConcurrentHashMap<>();

    /** Player UUID → voted kit key (kit voting in the lobby). */
    private final Map<UUID, String> kitVotes = new ConcurrentHashMap<>();

    /** Eliminated players in elimination order (first out = index 0) — for placement. */
    private final List<UUID> eliminationOrder = Collections.synchronizedList(new ArrayList<>());

    /** Per-match kill counts (reset every game, distinct from career stats). */
    private final Map<UUID, Integer> sessionKills = new ConcurrentHashMap<>();

    /** Seconds each player had survived at the moment they were eliminated. */
    private final Map<UUID, Long> survivalSeconds = new ConcurrentHashMap<>();

    /** Whether the game loop is paused (admin event mode). */
    private volatile boolean paused = false;
    private long pauseStart = 0L;

    /** Whether PvP is currently allowed. Locked during the start-of-game grace window. */
    private volatile boolean pvpUnlocked = true;

    /** Current lava Y-level */
    private int currentLavaY;

    /** Scheduler task ID for the main game loop */
    private int taskId = -1;

    /** Game start timestamp (for duration tracking) */
    private long startTime;

    /** Kaizen Memory Snapshot of the arena (written async, read on main thread → volatile) */
    private volatile int[] snapshotIndices;
    private volatile BlockData[] snapshotBlocks;
    private volatile boolean snapshotReady;

    public ArenaSession(LavaRisePlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.currentLavaY = arena.getConfig().lavaStartY();
        this.modeHandler = dev.lavarise.mode.GameModeHandler.forConfig(plugin, arena.getConfig());
        // Start in lobby state
        this.currentState = new LobbyState(plugin, arena, this);
        this.currentState.onEnter();
    }

    public dev.lavarise.mode.GameModeHandler getModeHandler() {
        return modeHandler;
    }

    // ── Teams ───────────────────────────────────────────────

    public void assignTeam(UUID playerId, int teamId) {
        playerTeam.put(playerId, teamId);
    }

    public int getTeam(UUID playerId) {
        return playerTeam.getOrDefault(playerId, -1);
    }

    public boolean sameTeam(UUID a, UUID b) {
        int ta = getTeam(a);
        return ta != -1 && ta == getTeam(b);
    }

    /** Distinct team ids that still have at least one alive member. */
    public Set<Integer> aliveTeams() {
        final Set<Integer> teams = new HashSet<>();
        for (UUID id : alivePlayers) {
            int t = getTeam(id);
            if (t != -1) teams.add(t);
        }
        return teams;
    }

    // ── Kit voting ──────────────────────────────────────────

    public void voteKit(UUID playerId, String kitKey) {
        kitVotes.put(playerId, kitKey);
    }

    /** The kit key this player voted for, or null if they haven't voted. */
    public String getVote(UUID playerId) {
        return kitVotes.get(playerId);
    }

    /** Total number of players who have cast a vote. */
    public int voteCount() {
        return kitVotes.size();
    }

    public Map<String, Integer> voteTally() {
        final Map<String, Integer> tally = new HashMap<>();
        for (String kit : kitVotes.values()) tally.merge(kit, 1, Integer::sum);
        return tally;
    }

    /** The winning voted kit (ties broken randomly), or null if nobody voted. */
    public String getVotedKit() {
        final Map<String, Integer> tally = voteTally();
        if (tally.isEmpty()) return null;
        final int max = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        final List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Integer> e : tally.entrySet()) {
            if (e.getValue() == max) winners.add(e.getKey());
        }
        return winners.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(winners.size()));
    }

    // ── PvP gate (grace window) ─────────────────────────────

    /** True once the start-of-game grace window has elapsed (or if PvP isn't gated). */
    public boolean isPvpUnlocked() {
        return pvpUnlocked;
    }

    public void setPvpUnlocked(boolean unlocked) {
        this.pvpUnlocked = unlocked;
    }

    // ── Results / placement ─────────────────────────────────

    /** Elimination order (first eliminated first); snapshot copy, safe to read. */
    public List<UUID> getEliminationOrder() {
        synchronized (eliminationOrder) {
            return new ArrayList<>(eliminationOrder);
        }
    }

    /** Credit a kill to a player for this match (separate from career stats). */
    public void recordSessionKill(UUID killer) {
        sessionKills.merge(killer, 1, Integer::sum);
    }

    public int getSessionKills(UUID playerId) {
        return sessionKills.getOrDefault(playerId, 0);
    }

    /** Seconds the player survived (their elimination time, or the live elapsed
     * time if they are still alive). */
    public long getSurvivalSeconds(UUID playerId) {
        final Long recorded = survivalSeconds.get(playerId);
        return recorded != null ? recorded : getElapsedSeconds();
    }

    // ── Pause (admin event) ─────────────────────────────────

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        if (paused && !this.paused) {
            this.pauseStart = System.currentTimeMillis();
        } else if (!paused && this.paused) {
            // Shift start time forward by the paused duration so elapsed stays continuous.
            this.startTime += System.currentTimeMillis() - pauseStart;
        }
        this.paused = paused;
    }

    /**
     * Add a player straight into the alive set without routing through the
     * current state's join hook. Used by world-wide survival, which skips the
     * lobby/countdown phases.
     */
    public void enroll(Player player) {
        alivePlayers.add(player.getUniqueId());
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
     * Mark a player as eliminated without moving them: removes them from the
     * alive set, adds them to spectators, and fires the state's elimination
     * hook (effects, broadcast, win-condition check).
     * <p>
     * Used by the death-driven path: when a player actually dies (to lava or
     * PvP) their items have already dropped via the vanilla death, and the
     * spectator gamemode/teleport is applied later on respawn.
     * </p>
     *
     * @return true if the player was alive and is now eliminated, false otherwise.
     */
    public boolean markEliminated(Player player) {
        final UUID uuid = player.getUniqueId();
        if (!alivePlayers.remove(uuid)) return false;

        spectators.add(uuid);
        eliminationOrder.add(uuid);
        survivalSeconds.put(uuid, getElapsedSeconds());
        currentState.onPlayerEliminated(player);

        plugin.debug(player.getName() + " eliminated in " + arena.getName()
                + " — " + alivePlayers.size() + " alive");
        return true;
    }

    /**
     * Eliminate a still-living player immediately — marks them eliminated and
     * moves them to spectator mode/spawn (no death, no item drop).
     */
    public void eliminatePlayer(Player player) {
        if (!markEliminated(player)) return;

        player.setGameMode(GameMode.SPECTATOR);
        if (arena.getConfig().spectatorSpawn() != null) {
            player.teleport(arena.getConfig().spectatorSpawn());
        }
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
        return alivePlayersView;
    }

    public Set<UUID> getSpectators() {
        return spectatorsView;
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

    /** Blocks the lava has risen from its start — a positive, player-friendly
     *  "lava height" (vs. the raw, possibly-negative world Y). */
    public int getLavaHeight() {
        return Math.max(0, currentLavaY - arena.getConfig().lavaStartY());
    }

    /** Total blocks the lava will rise over the whole game. */
    public int getLavaMaxHeight() {
        return Math.max(1, arena.getConfig().lavaMaxY() - arena.getConfig().lavaStartY());
    }

    /** Progress of the lava toward its maximum, 0–100. */
    public int getLavaPercent() {
        return Math.max(0, Math.min(100, Math.round(getLavaHeight() * 100.0f / getLavaMaxHeight())));
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
        // Publish data before flipping the ready flag so readers never see
        // half-initialised arrays (happens-before via the volatile write).
        this.snapshotIndices = indices;
        this.snapshotBlocks = blocks;
        this.snapshotReady = (indices != null && blocks != null && indices.length == blocks.length);
    }

    public int[] getSnapshotIndices() {
        return snapshotIndices;
    }

    public BlockData[] getSnapshotBlocks() {
        return snapshotBlocks;
    }

    /** True only once a consistent snapshot has been published. */
    public boolean isSnapshotReady() {
        return snapshotReady;
    }
}
