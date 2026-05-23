package dev.lavarise.listener;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles basic player events.
 */
public class PlayerListener implements Listener {
    private final LavaRisePlugin plugin;

    public PlayerListener(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Automatically remove a player from their active arena if they disconnect
        plugin.getGameManager().removePlayer(event.getPlayer());
    }
}
