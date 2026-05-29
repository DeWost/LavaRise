package dev.lavarise.mode;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.entity.Player;

/**
 * Admin-controlled event mode: server-wide start/end broadcasts and an optional
 * Vault economy reward for the winner. Pause/resume is driven from the command
 * via {@link ArenaSession#setPaused(boolean)}.
 *
 * @author DeWost
 */
public final class AdminEventModeHandler extends GameModeHandler {

    public AdminEventModeHandler(LavaRisePlugin plugin) {
        super(plugin);
    }

    @Override
    public void onGameStart(Arena arena, ArenaSession session) {
        if (plugin.getConfigManager().isEventBroadcastStart()) {
            plugin.getServer().broadcast(plugin.getMiniMessage().deserialize(
                    plugin.getConfigManager().getMessage("event.broadcast-start")));
        }
    }

    @Override
    public void onArenaEnd(Arena arena, ArenaSession session, Player winner) {
        if (plugin.getConfigManager().isEventBroadcastEnd()) {
            plugin.getServer().broadcast(plugin.getMiniMessage().deserialize(
                    plugin.getConfigManager().getMessage("event.broadcast-end")));
        }

        if (winner != null && plugin.getConfigManager().isEventRewardEnabled()) {
            final double amount = plugin.getConfigManager().getEventRewardAmount();
            final var vault = plugin.getVaultHook();
            if (vault != null && vault.deposit(winner, amount)) {
                winner.sendMessage(plugin.getMiniMessage().deserialize(
                        "<green>You received <yellow>" + amount + "</yellow> for winning the event!"));
            }
        }
    }
}
