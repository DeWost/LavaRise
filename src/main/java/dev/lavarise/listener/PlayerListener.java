package dev.lavarise.listener;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player lifecycle events: quitting, dying (elimination + item drops),
 * and respawning into spectator mode.
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

    /**
     * When a player dies during a running game (burned by lava or killed by
     * another player), their items drop via the normal vanilla death. We then
     * mark them eliminated; they re-enter as a spectator on respawn.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;

        final ArenaSession session = arena.getSession();
        if (session == null || !session.isAlive(player.getUniqueId())) return;
        if (!session.getCurrentState().isGameRunning()) return;

        // Keep the vanilla drops (items on the player fall to the ground), then
        // convert the death into an elimination.
        session.markEliminated(player);
    }

    /**
     * Respawn eliminated players as spectators at the arena's spectator spawn.
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;

        final ArenaSession session = arena.getSession();
        if (session == null || !session.isSpectator(player.getUniqueId())) return;

        if (arena.getConfig().spectatorSpawn() != null) {
            event.setRespawnLocation(arena.getConfig().spectatorSpawn());
        }

        // Gamemode can't be reliably changed inside the respawn event; defer a tick.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }
}
