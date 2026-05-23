package dev.lavarise.core;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all arena instances and active game sessions.
 * <p>
 * Thread-safe: uses ConcurrentHashMap for player lookups.
 * O(1) lookup for player-to-arena mapping.
 * </p>
 */
public final class GameManager {

    private final LavaRisePlugin plugin;

    /** Arena name (lowercase) → Arena instance */
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    /** Player UUID → Arena they are currently in (O(1) lookup) */
    private final Map<UUID, Arena> playerArenaMap = new ConcurrentHashMap<>();

    public GameManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    // ── Arena Registration ──────────────────────────────────

    /**
     * Register an arena. Called during startup or when a new arena is created.
     */
    public void registerArena(Arena arena) {
        arenas.put(arena.getName().toLowerCase(), arena);
        plugin.debug("Registered arena: " + arena.getName());
    }

    /**
     * Unregister an arena. Ends any active game first.
     */
    public void unregisterArena(String name) {
        final Arena arena = arenas.remove(name.toLowerCase());
        if (arena != null && arena.getSession() != null) {
            arena.getSession().forceEnd();
        }
    }

    // ── Arena Lookup ────────────────────────────────────────

    public Optional<Arena> getArena(String name) {
        return Optional.ofNullable(arenas.get(name.toLowerCase()));
    }

    public Collection<Arena> getAllArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    /**
     * Find an arena with available slots for joining.
     */
    public Optional<Arena> findAvailableArena() {
        return arenas.values().stream()
                .filter(a -> a.getSession() != null)
                .filter(a -> a.getSession().isJoinable())
                .filter(a -> a.getSession().getPlayerCount() < a.getConfig().maxPlayers())
                .findFirst();
    }

    // ── Player Management ───────────────────────────────────

    /**
     * Map a player to an arena. O(1) lookup on future calls.
     */
    public void mapPlayer(UUID playerId, Arena arena) {
        playerArenaMap.put(playerId, arena);
    }

    /**
     * Remove player from arena mapping.
     */
    public void unmapPlayer(UUID playerId) {
        playerArenaMap.remove(playerId);
    }

    /**
     * Get the arena a player is currently in. O(1).
     */
    public Optional<Arena> getPlayerArena(UUID playerId) {
        return Optional.ofNullable(playerArenaMap.get(playerId));
    }

    /**
     * Check if a player is in any game.
     */
    public boolean isInGame(UUID playerId) {
        return playerArenaMap.containsKey(playerId);
    }

    /**
     * Check if a player is in any game (convenience overload).
     */
    public boolean isInGame(Player player) {
        return isInGame(player.getUniqueId());
    }

    // ── Player Join/Leave ───────────────────────────────────

    /**
     * Attempt to add a player to an arena.
     * Returns true if successful, false if arena is full or not joinable.
     */
    public boolean joinArena(Player player, Arena arena) {
        if (isInGame(player)) {
            return false;
        }

        final ArenaSession session = arena.getSession();
        if (session == null || !session.isJoinable()) {
            return false;
        }

        if (session.getPlayerCount() >= arena.getConfig().maxPlayers()) {
            return false;
        }

        session.addPlayer(player);
        mapPlayer(player.getUniqueId(), arena);
        plugin.debug(player.getName() + " joined arena " + arena.getName());
        return true;
    }

    /**
     * Remove a player from their current arena.
     */
    public void leaveArena(Player player) {
        final Arena arena = playerArenaMap.get(player.getUniqueId());
        if (arena == null) return;

        final ArenaSession session = arena.getSession();
        if (session != null) {
            session.removePlayer(player);
        }
        unmapPlayer(player.getUniqueId());
        plugin.debug(player.getName() + " left arena " + arena.getName());
    }

    // ── Lifecycle ───────────────────────────────────────────

    /**
     * Gracefully shut down all running games. Called on plugin disable.
     */
    public void shutdownAll() {
        for (Arena arena : arenas.values()) {
            if (arena.getSession() != null) {
                arena.getSession().forceEnd();
            }
        }
        playerArenaMap.clear();
        plugin.getLogger().info("All games shut down.");
    }
}
