package dev.lavarise.api.events;

import dev.lavarise.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player is eliminated from an active arena.
 * <p>
 * This happens when a player comes into contact with the rising lava
 * or dies due to other hazards while the minigame is in progress.
 * </p>
 */
public class PlayerEliminatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Arena arena;
    private final Player player;

    public PlayerEliminatedEvent(Arena arena, Player player) {
        this.arena = arena;
        this.player = player;
    }

    public Arena getArena() {
        return arena;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
