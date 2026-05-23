package dev.lavarise.api.events;

import dev.lavarise.arena.Arena;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an arena successfully starts its active phase.
 * <p>
 * This event is called immediately after the countdown finishes
 * and players are released to play the minigame.
 * </p>
 */
public class ArenaStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Arena arena;

    public ArenaStartEvent(Arena arena) {
        this.arena = arena;
    }

    public Arena getArena() {
        return arena;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
