package dev.lavarise.arena;

import dev.lavarise.core.LavaRisePlugin;

/**
 * Represents a single arena — the composition root for a game instance.
 * <p>
 * Holds the immutable {@link ArenaConfig} and the mutable {@link ArenaSession}.
 * Session is null when no game is active; created when a game is queued.
 * </p>
 */
public final class Arena {

    private final LavaRisePlugin plugin;
    private final ArenaConfig config;
    private ArenaSession session;

    /** Transient arenas (procedural / survival) live only in memory and are
     *  unregistered after their game ends instead of being recreated. */
    private boolean transientArena;

    /** Optional forced kit for this arena — overrides kit voting/selection. */
    private String customKit;

    public Arena(LavaRisePlugin plugin, ArenaConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** The kit every player in this arena is forced to use, or {@code null}. */
    public String getCustomKit() {
        return customKit;
    }

    public void setCustomKit(String customKit) {
        this.customKit = (customKit == null || customKit.isBlank()) ? null : customKit;
    }

    public boolean isTransient() {
        return transientArena;
    }

    public Arena markTransient() {
        this.transientArena = true;
        return this;
    }

    // ── Accessors ───────────────────────────────────────────

    public String getName() {
        return config.name();
    }

    public ArenaConfig getConfig() {
        return config;
    }

    public ArenaSession getSession() {
        return session;
    }

    // ── Session Lifecycle ───────────────────────────────────

    /**
     * Create a new game session for this arena.
     * Initializes the FSM in LobbyState.
     */
    public ArenaSession createSession() {
        if (session != null) {
            plugin.getLogger().warning("Arena " + getName()
                    + " already has an active session! Ending it first.");
            session.forceEnd();
        }

        session = new ArenaSession(plugin, this);
        plugin.debug("Session created for arena: " + getName());
        return session;
    }

    /**
     * Destroy the current session. Called when game ends.
     */
    public void destroySession() {
        if (session != null) {
            session.cancelTask();
            session = null;
            plugin.debug("Session destroyed for arena: " + getName());
        }
    }

    /**
     * Check if this arena has an active game session.
     */
    public boolean hasActiveSession() {
        return session != null;
    }

    @Override
    public String toString() {
        return "Arena{" + getName() + ", session=" + (session != null ? "active" : "none") + '}';
    }
}
