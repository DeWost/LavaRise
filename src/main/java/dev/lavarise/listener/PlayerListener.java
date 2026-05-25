package dev.lavarise.listener;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public class PlayerListener implements Listener {

    private final LavaRisePlugin plugin;

    public PlayerListener(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getGameManager().removePlayer(event.getPlayer());
    }

    /**
     * Cancel vanilla lava/fire damage for arena players — we apply it manually
     * in ActiveState so rate and elimination logic stay under our control.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.LAVA
                && cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK) return;

        if (plugin.getGameManager().isInGame(player)) {
            event.setCancelled(true);
        }
    }
}
