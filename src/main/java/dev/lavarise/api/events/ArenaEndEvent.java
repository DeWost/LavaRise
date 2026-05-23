package dev.lavarise.api.events;

import dev.lavarise.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an arena session ends.
 * <p>
 * This event is called when all players have been eliminated or the minigame
 * naturally concludes. Use this to reset external states or process statistics.
 * </p>
 */
public class ArenaEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Arena arena;
    private final Player winner;

    public ArenaEndEvent(Arena arena, Player winner) {
        this.arena = arena;
        this.winner = winner;
    }

    public Arena getArena() {
        return arena;
    }

    public Player getWinner() {
        return winner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
