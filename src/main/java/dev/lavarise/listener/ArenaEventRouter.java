package dev.lavarise.listener;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
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

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;

        final ArenaSession session = arena.getSession();
        if (session == null) return;

        // Before the game starts (lobby / countdown), shield players from
        // environmental lava and fire so a pre-placed pool can't kill them.
        if (!session.getCurrentState().isGameRunning()) {
            final EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
                event.setCancelled(true);
            }
        }
        // While the game is running: damage applies normally. Players burn in
        // the lava for real and, on death, drop their items (handled by the
        // vanilla death + PlayerListener#onPlayerDeath).
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            Arena arena = plugin.getGameManager().getArenaForPlayer(victim.getUniqueId());
            if (arena != null) {
                if (!arena.getConfig().pvpEnabled()) {
                    event.setCancelled(true);
                    return;
                }
                // Mode rules (e.g. teams disable friendly fire).
                ArenaSession session = arena.getSession();
                if (session != null && !session.getModeHandler().allowFriendlyFire(session, attacker, victim)) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
