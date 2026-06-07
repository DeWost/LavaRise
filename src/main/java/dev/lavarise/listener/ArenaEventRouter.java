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
import org.bukkit.event.entity.CreatureSpawnEvent;
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

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfigManager().isDenyMobSpawns()) return;
        // Block world/natural spawns inside an arena; allow player-driven ones
        // (eggs, breeding, commands, custom).
        switch (event.getSpawnReason()) {
            case NATURAL, SPAWNER, CHUNK_GEN, JOCKEY, PATROL, RAID,
                 REINFORCEMENTS, VILLAGE_INVASION, NETHER_PORTAL, TRAP -> {
                if (plugin.getGameManager().isInArenaBounds(event.getLocation())) {
                    event.setCancelled(true);
                }
            }
            default -> { }
        }
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

    /**
     * Single damage handler. {@link EntityDamageByEntityEvent} is itself an
     * {@link EntityDamageEvent}, so handling both in one method avoids two event
     * dispatches + two arena lookups per PvP hit (previously two listeners ran
     * for every player-vs-player blow).
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        final Arena arena = plugin.getGameManager().getArenaForPlayer(victim.getUniqueId());
        if (arena == null) return;
        final ArenaSession session = arena.getSession();
        if (session == null) return;

        final boolean running = session.getCurrentState().isGameRunning();

        // Pre-game (lobby/countdown): shield from environmental lava/fire.
        if (!running) {
            final EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
                event.setCancelled(true);
            }
            return;
        }

        // PvP rules — only for player-vs-player blows.
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player attacker) {
            if (!arena.getConfig().pvpEnabled()) {
                event.setCancelled(true);
                return;
            }
            // Grace-window gate: no fighting until the start-of-game grace ends.
            if (!session.isPvpUnlocked()) {
                event.setCancelled(true);
                return;
            }
            // Height-gated PvP: no fighting until the lava has risen enough.
            final int pvpAfter = plugin.getConfigManager().getPvpAfterHeight();
            if (pvpAfter > 0 && session.getLavaHeight() < pvpAfter) {
                event.setCancelled(true);
                return;
            }
            // Mode rules (e.g. teams disable friendly fire).
            if (!session.getModeHandler().allowFriendlyFire(session, attacker, victim)) {
                event.setCancelled(true);
            }
        }
        // Otherwise (lava/fall/etc. during the game): damage applies; on death,
        // PlayerListener#onPlayerDeath handles drops + elimination.
    }
}
