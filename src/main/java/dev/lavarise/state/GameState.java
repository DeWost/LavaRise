 package dev.lavarise.state;

import org.bukkit.entity.Player;

/**
 * Finite State Machine interface for game phases.
 * <p>
 * Each state handles events specific to its phase.
 * The Arena delegates all events to the current state.
 * </p>
 */
public interface GameState {

    /**
     * Called when entering this state.
     * Start timers, send messages, initialize state-specific resources.
     */
    void onEnter();

    /**
     * Called when leaving this state.
     * Cancel timers, clean up resources.
     */
    void onExit();

    /**
     * Called every game tick while this state is active.
     * Not all states need to implement this (default no-op).
     */
    default void onTick() {}

    /**
     * Called when a player joins the arena during this state.
     */
    void onPlayerJoin(Player player);

    /**
     * Called when a player voluntarily leaves during this state.
     */
    void onPlayerLeave(Player player);

    /**
     * Called when a player is eliminated (died to lava).
     */
    void onPlayerEliminated(Player player);

    /**
     * Whether new players can join during this state.
     */
    boolean isJoinable();

    /**
     * Whether the game is actively running (lava rising, players can be
     * eliminated). Only true during the active phase. Used by listeners to
     * decide when lethal lava/fire damage should convert into elimination.
     */
    default boolean isGameRunning() {
        return false;
    }

    /**
     * Get the display name of this state for scoreboards/messages.
     */
    String getDisplayName();
}
