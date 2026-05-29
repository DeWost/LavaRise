package dev.lavarise.listener;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routes global events to specific arenas if a player is in one.
 */
public class ArenaEventRouter implements Listener {
    private final LavaRisePlugin plugin;

    /** Ore drops → smelted result for auto-smelt. */
    private static final Map<Material, Material> SMELT = Map.of(
            Material.RAW_IRON, Material.IRON_INGOT,
            Material.RAW_GOLD, Material.GOLD_INGOT,
            Material.RAW_COPPER, Material.COPPER_INGOT,
            Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP,
            Material.SAND, Material.GLASS,
            Material.COBBLESTONE, Material.STONE);

    public ArenaEventRouter(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;

        if (!arena.getConfig().blockBreak()) {
            event.setCancelled(true);
            return;
        }

        final boolean smelt = plugin.getConfigManager().isAutoSmelt();
        final boolean pickup = plugin.getConfigManager().isAutoPickup();
        if (!smelt && !pickup) return;

        // Compute drops for the tool in hand, optionally smelt them.
        final List<ItemStack> drops = new ArrayList<>(
                event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player));
        if (smelt) {
            for (ItemStack stack : drops) {
                Material cooked = SMELT.get(stack.getType());
                if (cooked != null) stack.setType(cooked);
            }
        }

        event.setDropItems(false); // we deliver them ourselves
        for (ItemStack stack : drops) {
            if (pickup) {
                player.getInventory().addItem(stack).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            } else {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), stack);
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
                ArenaSession session = arena.getSession();
                // Height-gated PvP: no fighting until the lava has risen enough.
                final int pvpAfter = plugin.getConfigManager().getPvpAfterHeight();
                if (session != null && pvpAfter > 0
                        && session.getCurrentState().isGameRunning()
                        && session.getLavaHeight() < pvpAfter) {
                    event.setCancelled(true);
                    return;
                }
                // Mode rules (e.g. teams disable friendly fire).
                if (session != null && !session.getModeHandler().allowFriendlyFire(session, attacker, victim)) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
