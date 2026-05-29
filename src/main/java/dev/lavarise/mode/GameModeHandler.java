package dev.lavarise.mode;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Strategy object that lets each {@link GameMode} override the few decisions
 * that differ between modes, without scattering {@code switch(mode)} logic
 * through the FSM states. One handler is created per {@link ArenaSession} and
 * queried at well-defined hook points in ActiveState/EndingState/ArenaEventRouter.
 *
 * <p>The default implementation reproduces the classic free-for-all minigame
 * (last player standing), so selecting {@code minigame} is behaviour-neutral.</p>
 *
 * @author DeWost
 */
public abstract class GameModeHandler {

    protected final LavaRisePlugin plugin;

    protected GameModeHandler(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    /** Called once when the game enters the active phase. */
    public void onGameStart(Arena arena, ArenaSession session) {}

    /** Called after a player is eliminated, before the win check. */
    public void onPlayerEliminated(Arena arena, ArenaSession session, Player player) {}

    /** Whether the game has reached its end condition. */
    public boolean isGameOver(Arena arena, ArenaSession session) {
        return session.getAliveCount() <= 1;
    }

    /** The winning player (nullable — e.g. nobody survived). */
    public Player resolveWinner(Arena arena, ArenaSession session) {
        if (session.getAliveCount() == 1) {
            UUID id = session.getAlivePlayers().iterator().next();
            return plugin.getServer().getPlayer(id);
        }
        return null;
    }

    /** Called when the game ends (rewards, broadcasts, multi-winner recording). */
    public void onArenaEnd(Arena arena, ArenaSession session, Player winner) {}

    /** Whether {@code attacker} may damage {@code victim} (e.g. teams disable friendly fire). */
    public boolean allowFriendlyFire(ArenaSession session, Player attacker, Player victim) {
        return true;
    }

    /** Whether the arena volume should be snapshotted for restore (survival opts out). */
    public boolean shouldSnapshot() {
        return true;
    }

    /**
     * Resolve the handler for an arena's configured mode.
     */
    public static GameModeHandler forConfig(LavaRisePlugin plugin, ArenaConfig config) {
        return switch (GameMode.fromString(config.gameMode())) {
            case SURVIVAL_CHALLENGE -> new SurvivalModeHandler(plugin);
            case ADMIN_EVENT -> new AdminEventModeHandler(plugin);
            case MINIGAME -> new MinigameModeHandler(plugin);
        };
    }
}
