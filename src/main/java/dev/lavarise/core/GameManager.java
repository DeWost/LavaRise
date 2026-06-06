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

    /**
     * Pick a RANDOM joinable arena (random matchmaking / quick-join).
     */
    public Optional<Arena> findRandomAvailableArena() {
        final java.util.List<Arena> open = arenas.values().stream()
                .filter(a -> a.getSession() != null)
                .filter(a -> a.getSession().isJoinable())
                .filter(a -> a.getSession().getPlayerCount() < a.getConfig().maxPlayers())
                .collect(java.util.stream.Collectors.toList());
        if (open.isEmpty()) return Optional.empty();
        return Optional.of(open.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(open.size())));
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
     * Get the arena a player is currently in, or {@code null} if none.
     * <p>
     * Returns the raw nullable Arena (not an {@link Optional}) because this is
     * called from event hot paths (block place/break, damage) where avoiding
     * per-call allocation matters.
     * </p>
     */
    public Arena getArenaForPlayer(UUID playerId) {
        return playerArenaMap.get(playerId);
    }

    /**
     * True if the location is horizontally inside any arena that has an active
     * session (used to deny mob spawns inside arenas).
     */
    public boolean isInArenaBounds(org.bukkit.Location loc) {
        for (Arena arena : arenas.values()) {
            if (arena.getSession() != null && arena.getConfig().containsHorizontal(loc)) {
                return true;
            }
        }
        return false;
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
     * Attempt to add a player to an arena, sending the player feedback about
     * the outcome.
     *
     * @return true if the player was added, false if rejected (already in a
     *         game, arena not joinable, or arena full).
     */
    public boolean addPlayerToArena(Player player, Arena arena) {
        if (isInGame(player)) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>You are already in a game! Use <yellow>/lavarise leave</yellow> first."));
            return false;
        }

        final ArenaSession session = arena.getSession();
        if (session == null || !session.isJoinable()) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>Arena <yellow>" + arena.getName() + "</yellow> is not accepting players right now."));
            return false;
        }

        if (session.getPlayerCount() >= arena.getConfig().maxPlayers()) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>Arena <yellow>" + arena.getName() + "</yellow> is full!"));
            return false;
        }

        session.addPlayer(player);
        mapPlayer(player.getUniqueId(), arena);
        plugin.debug(player.getName() + " joined arena " + arena.getName());
        return true;
    }

    /**
     * Remove a player from their current arena, if any. Safe to call even when
     * the player is not in a game.
     */
    public void removePlayer(Player player) {
        final Arena arena = playerArenaMap.get(player.getUniqueId());
        if (arena == null) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>You are not currently in a game."));
            return;
        }

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
