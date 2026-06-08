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
        final Player player = event.getPlayer();
        // Combat-log protection: logging out mid-fight counts as a death, not a
        // clean escape — record it and announce before removing the player.
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena != null && arena.getSession() != null
                && plugin.getConfigManager().isCombatLogProtect()
                && arena.getSession().getCurrentState().isGameRunning()
                && arena.getSession().isCombatTagged(player.getUniqueId())
                && arena.getSession().isAlive(player.getUniqueId())) {
            plugin.getStatsManager().recordDeath(player.getUniqueId(), player.getName());
            broadcast(arena.getSession(), "<red>☠ <yellow>" + player.getName()
                    + "</yellow> combat-logged and was eliminated!");
        }
        // Drop them from the matchmaking queue and their party if they were in one,
        // so neither map holds a ghost reference to a disconnected player.
        plugin.getQueueManager().remove(player.getUniqueId());
        plugin.getPartyManager().leave(player.getUniqueId());
        // Automatically remove a player from their active arena if they disconnect.
        plugin.getGameManager().removePlayer(player);
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
            final int streak = session.getSessionKills(killer.getUniqueId());
            killer.sendMessage(plugin.getMiniMessage().deserialize(
                    "<gray>You eliminated <red>" + player.getName() + "</red>! "
                            + "<dark_gray>(<yellow>" + streak + "<gray> kill" + (streak == 1 ? "" : "s")
                            + "<dark_gray>)"));
            try {
                killer.playSound(killer.getLocation(), "entity.experience_orb.pickup", 1.0f, 1.2f);
            } catch (Exception ignored) {
                // Invalid sound key — ignore.
            }
            runCommands(plugin.getConfigManager().getKillCommands(),
                    "{killer}", killer.getName(), "{victim}", player.getName());

            // Reward winning a fight with some health back (extinguish fire too).
            final double heal = plugin.getConfigManager().getKillHeal();
            if (heal > 0) {
                try {
                    killer.setHealth(Math.min(killer.getMaxHealth(), killer.getHealth() + heal));
                    killer.setFireTicks(0);
                } catch (IllegalArgumentException ignored) {
                    // Health out of range (e.g. attribute-modified max) — skip the heal
                    // rather than let it abort the rest of the death handler.
                }
            }

            // ── Battle-royale combat hype ──────────────────────
            final var cfg = plugin.getConfigManager();
            if (cfg.isKillstreaksEnabled()) {
                // Multi-kill: rapid consecutive kills by the same player.
                final int combo = session.registerCombo(killer.getUniqueId(), 4000L);
                if (combo >= 2) {
                    final String label = switch (combo) {
                        case 2 -> "DOUBLE KILL";
                        case 3 -> "TRIPLE KILL";
                        case 4 -> "QUADRA KILL";
                        default -> combo + "× MULTI KILL";
                    };
                    killer.showTitle(net.kyori.adventure.title.Title.title(
                            plugin.getMiniMessage().deserialize("<gold><bold>" + label + "!</bold>"),
                            net.kyori.adventure.text.Component.empty(),
                            net.kyori.adventure.title.Title.Times.times(
                                    java.time.Duration.ofMillis(150), java.time.Duration.ofSeconds(1),
                                    java.time.Duration.ofMillis(400))));
                    try { killer.playSound(killer.getLocation(), "entity.player.levelup", 1.0f, 1.6f); }
                    catch (Exception ignored) { }
                }
                // Killstreak milestones broadcast to the whole arena.
                if (streak == 3 || streak == 5 || streak == 7 || (streak >= 10 && streak % 5 == 0)) {
                    broadcast(session, "<gold>⚔ <yellow>" + killer.getName()
                            + "</yellow> is on a <red>" + streak + "</red> killstreak!");
                }
            }
            // Bounty: the victim was on a streak → the killer claims it.
            final int victimStreak = session.getSessionKills(player.getUniqueId());
            if (victimStreak >= cfg.getBountyThreshold()) {
                final int bounty = victimStreak * cfg.getBountyPerStreak();
                broadcast(session, "<red>☠ <yellow>" + killer.getName() + "</yellow> claimed <gold>"
                        + player.getName() + "</gold>'s <yellow>" + bounty + "</yellow> bounty!");
                runCommands(cfg.getBountyCommands(), "{killer}", killer.getName(),
                        "{victim}", player.getName(), "{bounty}", String.valueOf(bounty));
            }
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

    /** Broadcast a MiniMessage line to every player in the session. */
    private void broadcast(ArenaSession session, String mini) {
        final var component = plugin.getMiniMessage().deserialize(mini);
        for (java.util.UUID id : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null && p.isOnline()) p.sendMessage(component);
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
     * Right-clicking the tagged lobby "Leave" bed quits the player's game.
     */
    @EventHandler
    public void onLeaveItem(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!dev.lavarise.feature.LeaveItem.is(plugin, event.getItem())) return;
        final Player player = event.getPlayer();
        if (plugin.getGameManager().isInGame(player)) {
            event.setCancelled(true);
            plugin.getGameManager().removePlayer(player);
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
