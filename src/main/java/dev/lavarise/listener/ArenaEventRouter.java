package dev.lavarise.listener;

import dev.lavarise.arena.Arena;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Routes global events to specific arenas if a player is in one.
 */
public class ArenaEventRouter implements Listener {
    private final LavaRisePlugin plugin;

    public ArenaEventRouter(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena != null) {
            if (!arena.getConfig().blockBreak()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena != null) {
            if (!arena.getConfig().blockPlace()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
            if (arena != null) {
                // Ignore lava damage natively; elimination is handled by ActiveState Y-level checks
                if (event.getCause() == EntityDamageEvent.DamageCause.LAVA || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            Arena arena = plugin.getGameManager().getArenaForPlayer(victim.getUniqueId());
            if (arena != null) {
                if (!arena.getConfig().pvpEnabled()) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
