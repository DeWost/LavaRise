package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.engine.WorldResetter;
import dev.lavarise.feature.SpectatorCompassItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.FireworkEffect;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.UUID;

/**
 * Ending state — game has concluded.
 * Shows winner, launches fireworks, then resets the arena.
 */
public final class EndingState implements GameState {

    private final LavaRisePlugin plugin;
    private final Arena arena;
    private final ArenaSession session;
    private final Player winner; // nullable
    private BukkitRunnable resetTask;

    public EndingState(LavaRisePlugin plugin, Arena arena, ArenaSession session, Player winner) {
        this.plugin = plugin;
        this.arena = arena;
        this.session = session;
        this.winner = winner;
    }

    @Override
    public void onEnter() {
        plugin.debug("Arena " + arena.getName() + " entered ENDING state.");
        long elapsed = session.getElapsedSeconds();
        
        plugin.getServer().getPluginManager().callEvent(new dev.lavarise.api.events.ArenaEndEvent(arena, winner));

        // Remove every boss bar for this session's players.
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) plugin.getBossBarModule().removeFor(p);
        }

        if (winner != null && winner.isOnline()) {
            // Persist the win + best survival time, then run any reward commands.
            plugin.getStatsManager().recordWin(winner.getUniqueId(), winner.getName());
            plugin.getStatsManager().recordSurvivalTime(winner.getUniqueId(), winner.getName(), elapsed);
            plugin.getAchievementManager().checkAndAward(winner);
            plugin.getCosmeticManager().playWinEffect(winner);
            plugin.getLevelManager().addXp(winner, plugin.getLevelManager().xpPerWin(), "win");
            runWinCommands(winner);

            // Winner announcement
            broadcastToArena(plugin.getMiniMessage().deserialize(
                    "<gold><bold>\u2605 WINNER: </bold><yellow>" + winner.getName()
                            + " <gray>survived for <white>" + elapsed + "s"));
            winner.showTitle(Title.title(
                    plugin.getMiniMessage().deserialize("<gradient:gold:yellow><bold>VICTORY!</bold></gradient>"),
                    plugin.getMiniMessage().deserialize("<gray>You are the last one standing!"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            giveWinItems(winner);
            // Launch fireworks at winner
            launchFireworks(winner);
        } else {
            broadcastToArena(plugin.getMiniMessage().deserialize(
                    "<red><bold>GAME OVER!</bold> <gray>No survivors. Duration: <white>" + elapsed + "s"));
        }

        plugin.getDiscordHook().onGameEnd(arena, session, winner, elapsed);

        // Results screen: podium + each player's personal placement / kills.
        showResults();

        // Mode-specific end behaviour (event broadcasts, Vault rewards, team wins).
        session.getModeHandler().onArenaEnd(arena, session, winner);

        // Schedule arena reset — long enough for players to read the results.
        resetTask = new BukkitRunnable() {
            @Override
            public void run() {
                resetArena();
            }
        };
        resetTask.runTaskLater(plugin, 160L); // 8 seconds
    }

    /**
     * Builds the final ranking (survivors first, then most-recently eliminated),
     * broadcasts a top-3 podium, and DMs each player their own placement, survival
     * time and kills. Works for the no-winner case too (everyone still placed).
     */
    private void showResults() {
        final java.util.List<UUID> ranking = dev.lavarise.feature.MatchResults.ranking(
                session.getAlivePlayers(), session.getEliminationOrder());
        final int total = ranking.size();
        if (total == 0) return;

        final String[] medals = {"<yellow>① 1.", "<white>② 2.", "<gold>③ 3."};
        final StringBuilder podium = new StringBuilder("<gold><bold>── Results ──</bold>");
        for (int i = 0; i < Math.min(3, total); i++) {
            final UUID u = ranking.get(i);
            podium.append("<newline>").append(medals[i]).append(" <white>").append(resolveName(u))
                    .append(" <dark_gray>(<red>").append(session.getSessionKills(u))
                    .append("<gray> kills<dark_gray>, <white>").append(session.getSurvivalSeconds(u))
                    .append("s<dark_gray>)");
        }
        broadcastToArena(plugin.getMiniMessage().deserialize(podium.toString()));

        for (int i = 0; i < total; i++) {
            final Player p = plugin.getServer().getPlayer(ranking.get(i));
            if (p == null || !p.isOnline()) continue;
            final UUID u = ranking.get(i);
            p.sendMessage(plugin.getMiniMessage().deserialize(
                    "<gray>You placed <yellow><bold>#" + (i + 1) + "</bold></yellow><gray>/<white>" + total
                            + " <dark_gray>· <gray>survived <white>" + session.getSurvivalSeconds(u)
                            + "s <dark_gray>· <red>" + session.getSessionKills(u) + "<gray> kills"));
        }
    }

    private String resolveName(UUID uuid) {
        final Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) return p.getName();
        final String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name != null ? name : "Player";
    }

    @Override
    public void onExit() {
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
    }

    @Override public void onPlayerJoin(Player player) {
        player.sendMessage(plugin.getMiniMessage().deserialize("<yellow>Game is ending..."));
    }
    @Override public void onPlayerLeave(Player player) { /* OK */ }
    @Override public void onPlayerEliminated(Player player) { /* No-op */ }
    @Override public boolean isJoinable() { return false; }
    @Override public String getDisplayName() { return "Ending"; }

    private void resetArena() {
        // Teleport everyone out
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                plugin.getScoreboardModule().cleanup(p); // drop the sidebar + free its playerLines entry
                p.setWorldBorder(null); // restore the world's own border
                p.setGlowing(false);    // clear any final-showdown glow
                p.resetPlayerTime();    // restore real time/weather
                p.resetPlayerWeather();
                // Strip any spectator-phase items (compass, leave bed) before
                // returning players to the lobby so they don't carry them over.
                clearSpectatorItems(p);
                p.setGameMode(GameMode.SURVIVAL);
                if (arena.getConfig().lobbySpawn() != null) {
                    p.teleport(arena.getConfig().lobbySpawn());
                }
            }
        }

        // Unmap all players
        for (UUID uuid : session.getAllPlayerIds()) {
            plugin.getGameManager().unmapPlayer(uuid);
        }

        // Remove any live supply-drop chests (block-entities) before the bulk
        // reset so no chest leaks past the game.
        for (org.bukkit.Location drop : session.getSupplyDrops()) {
            try {
                if (drop.getWorld() != null) drop.getBlock().setType(org.bukkit.Material.AIR, false);
            } catch (Throwable ignored) {
                // Chunk unloaded / already cleared — fine.
            }
        }

        // Reset world blocks (skipped for world-wide survival, which is not snapshotted)
        if (session.getModeHandler().shouldSnapshot()) {
            WorldResetter.resetArena(plugin, arena);
        }

        // Tear down the session. Transient arenas (procedural / survival) are
        // unregistered so the next quick-join spins up a fresh random one
        // (random rotation); normal arenas return to the lobby.
        arena.destroySession();
        if (arena.isTransient()) {
            plugin.getGameManager().unregisterArena(arena.getName());
            plugin.debug("Transient arena " + arena.getName() + " removed after game.");
        } else {
            arena.createSession();
            plugin.debug("Arena " + arena.getName() + " reset complete.");
        }
    }

    /**
     * Remove the spectator compass (slot 0) and leave item (slot 8) from a
     * player's inventory so they don't carry these items back to the lobby.
     * Safe to call on survivors (who never had these items).
     */
    private void clearSpectatorItems(Player player) {
        final var inv = player.getInventory();
        if (SpectatorCompassItem.is(plugin, inv.getItem(0))) {
            inv.setItem(0, null);
        }
        if (dev.lavarise.feature.LeaveItem.is(plugin, inv.getItem(8))) {
            inv.setItem(8, null);
        }
    }

    /** Give the winner the configured reward items ({@code MATERIAL:AMOUNT}). */
    private void giveWinItems(Player winner) {
        for (String entry : plugin.getConfigManager().getWinItems()) {
            if (entry == null || entry.isBlank()) continue;
            final String[] parts = entry.split(":");
            final org.bukkit.Material mat = org.bukkit.Material.matchMaterial(parts[0].trim());
            if (mat == null) {
                plugin.getLogger().warning("Unknown material in rewards.win-items: " + parts[0]);
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try { amount = Math.max(1, Integer.parseInt(parts[1].trim())); }
                catch (NumberFormatException ignored) { }
            }
            winner.getInventory().addItem(new org.bukkit.inventory.ItemStack(mat, amount));
        }
    }

    private void runWinCommands(Player winner) {
        for (String command : plugin.getConfigManager().getWinCommands()) {
            if (command == null || command.isBlank()) continue;
            final String resolved = command
                    .replace("{winner}", winner.getName())
                    .replace("{arena}", arena.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
        }
    }

    private void launchFireworks(Player player) {
        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;
                    Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                            .withFade(Color.WHITE)
                            .withFlicker().withTrail().build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                }
            }.runTaskLater(plugin, i * 15L);
        }
    }

    private void broadcastToArena(Component msg) {
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
    }
}
