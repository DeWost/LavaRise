package dev.lavarise.listener;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles player lifecycle events: quitting, dying (elimination + item drops),
 * respawning into spectator mode, hunger control and spectator restrictions.
 *
 * @author DeWost
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
     * another player), their items drop via the normal vanilla death (unless the
     * arena enables keep-inventory). We credit the killer, then mark the player
     * eliminated; they re-enter as a spectator on respawn.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;

        final ArenaSession session = arena.getSession();
        if (session == null || !session.isAlive(player.getUniqueId())) return;
        if (!session.getCurrentState().isGameRunning()) return;

        // Credit a PvP kill, if any, and run kill-reward commands.
        final Player killer = player.getKiller();
        if (killer != null && !killer.equals(player)) {
            plugin.getStatsManager().recordKill(killer.getUniqueId(), killer.getName());
            session.recordSessionKill(killer.getUniqueId());
            killer.sendMessage(plugin.getMiniMessage().deserialize(
                    "<gray>You eliminated <red>" + player.getName() + "</red>!"));
            runCommands(plugin.getConfigManager().getKillCommands(),
                    "{killer}", killer.getName(), "{victim}", player.getName());
        }

        // Run death-reward commands.
        runCommands(plugin.getConfigManager().getDeathCommands(), "{player}", player.getName());

        // Honour the arena's keep-inventory setting (default: items drop).
        if (arena.getConfig().keepInventory()) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
        }

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

    /**
     * Disable hunger depletion during a game when the arena has hunger turned off.
     */
    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;
        if (!arena.getConfig().hunger()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    /**
     * Run a list of console commands, applying {@code key→value} placeholder
     * replacements to each.
     */
    private void runCommands(java.util.List<String> commands, String... replacements) {
        for (String command : commands) {
            if (command == null || command.isBlank()) continue;
            String resolved = command;
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                resolved = resolved.replace(replacements[i], replacements[i + 1]);
            }
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
        }
    }

    /**
     * Right-clicking the lobby compass opens the kit-vote menu.
     */
    @EventHandler
    public void onCompassUse(PlayerInteractEvent event) {
        // PlayerInteractEvent fires once per hand — only act on the main hand.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getItem() == null || event.getItem().getType() != Material.COMPASS) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        final Player player = event.getPlayer();
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;
        final ArenaSession session = arena.getSession();
        if (session != null && session.isJoinable()) {
            event.setCancelled(true);
            plugin.getVoteGUI().open(player);
        }
    }

    /**
     * Prevent eliminated spectators from picking up dropped items.
     */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) return;
        final ArenaSession session = arena.getSession();
        if (session != null && session.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
