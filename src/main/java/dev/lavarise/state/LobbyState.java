package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

/**
 * Lobby state — waiting for players to join.
 * <p>
 * Once minimum players are present a "filling" countdown runs (the configured
 * {@code lobby-countdown}), shown as an ambient boss bar so latecomers can still
 * join. It fast-forwards when the arena fills and aborts if players drop back
 * below the minimum, then hands off to {@link CountdownState} for the final
 * tense countdown. The boss bar counts the <i>total</i> remaining pre-game time,
 * so the handoff into CountdownState's titles is seamless (no number jump).
 * </p>
 */
public final class LobbyState implements GameState {

    private final LavaRisePlugin plugin;
    private final Arena arena;
    private final ArenaSession session;

    private BukkitRunnable lobbyTask;
    private BossBar lobbyBar;
    private int lobbyTicks = -1; // seconds left in the filling window; -1 = not started

    public LobbyState(LavaRisePlugin plugin, Arena arena, ArenaSession session) {
        this.plugin = plugin;
        this.arena = arena;
        this.session = session;
    }

    @Override
    public void onEnter() {
        plugin.debug("Arena " + arena.getName() + " entered LOBBY state.");
    }

    @Override
    public void onExit() {
        stopLobbyCountdown();
    }

    @Override
    public void onPlayerJoin(Player player) {
        // Teleport to lobby spawn
        if (arena.getConfig().lobbySpawn() != null) {
            player.teleport(arena.getConfig().lobbySpawn());
        }

        // Send welcome title
        player.showTitle(Title.title(
                plugin.getMiniMessage().deserialize("<gradient:red:gold><bold>LAVA RISE</bold></gradient>"),
                plugin.getMiniMessage().deserialize("<gray>Waiting for players..."),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));

        // Broadcast join message to arena players
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<green>" + player.getName() + " <gray>joined! <dark_gray>("
                        + session.getAliveCount() + "/" + arena.getConfig().maxPlayers() + ")"
        ));

        plugin.getScoreboardModule().setupScoreboard(player, session);
        plugin.getScoreboardModule().updateScoreboard(session);

        // Lobby compass — right-click opens the kit-vote menu. Put it in the
        // player's hand and prompt them so voting is impossible to miss.
        if (plugin.getKitManager() != null && plugin.getKitManager().hasKits()) {
            final ItemStack compass = new ItemStack(Material.COMPASS);
            final ItemMeta meta = compass.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getMiniMessage().deserialize(
                        "<gradient:yellow:gold><bold>✦ Kit Voting ✦</bold></gradient>"));
                meta.lore(java.util.List.of(
                        plugin.getMiniMessage().deserialize("<gray>Right-click to <yellow>vote</yellow> for the"),
                        plugin.getMiniMessage().deserialize("<gray>kit the whole lobby will play."),
                        plugin.getMiniMessage().deserialize("<dark_gray>The winning kit is given to everyone.")));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                        org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                compass.setItemMeta(meta);
            }
            player.getInventory().setItem(4, compass);
            player.getInventory().setHeldItemSlot(4);

            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<gold>✦ <yellow>Kit voting is open!</yellow> Right-click the "
                            + "<gold>compass</gold> to pick your loadout."));
            playSound(player, "block.note_block.pling");
        }

        // A tagged "Leave" bed so players can quit without typing a command.
        // Guarded: a cosmetic item must never block a player from joining.
        try {
            player.getInventory().setItem(8, dev.lavarise.feature.LeaveItem.create(plugin));
        } catch (Throwable ignored) {
            // Item creation unavailable in this context — non-critical.
        }

        // Show the lobby bar to the joiner, then (re)evaluate the start. While
        // still below the minimum the bar shows a "waiting for players" status.
        ensureBar();
        lobbyBar.addPlayer(player);
        checkMinPlayers();
        if (lobbyTicks < 0) showWaiting();
    }

    @Override
    public void onPlayerLeave(Player player) {
        plugin.getScoreboardModule().cleanup(player);
        plugin.getScoreboardModule().updateScoreboard(session);
        if (lobbyBar != null) lobbyBar.removePlayer(player);
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>" + player.getName() + " <gray>left! <dark_gray>("
                        + session.getAliveCount() + "/" + arena.getConfig().maxPlayers() + ")"
        ));

        // Dropped below the minimum mid-countdown → abort and keep waiting.
        if (lobbyTicks >= 0 && session.getAliveCount() < arena.getConfig().minPlayers()) {
            stopCounting();
            broadcastToArena(plugin.getMiniMessage().deserialize(
                    "<yellow>Not enough players — waiting again..."));
        }
        if (session.getAliveCount() == 0) {
            stopLobbyCountdown();
        } else if (lobbyTicks < 0) {
            showWaiting();
        }
    }

    @Override
    public void onPlayerEliminated(Player player) {
        // Can't be eliminated in lobby
    }

    @Override
    public boolean isJoinable() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return lobbyTicks >= 0 ? "Starting" : "Waiting";
    }

    // ── Lobby countdown ─────────────────────────────────────

    private void checkMinPlayers() {
        if (lobbyTicks >= 0) return; // already filling
        if (session.getAliveCount() >= arena.getConfig().minPlayers()) {
            startLobbyCountdown();
        }
    }

    private void startLobbyCountdown() {
        this.lobbyTicks = Math.max(1, arena.getConfig().lobbyCountdown());
        ensureBar();
        lobbyBar.setColor(BarColor.GREEN);

        final int lobbyTotal = lobbyTicks;
        final int gameCountdown = Math.max(1, arena.getConfig().gameCountdown());
        lobbyTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Fast-forward to launch the moment the arena is full.
                if (session.getAliveCount() >= arena.getConfig().maxPlayers()) {
                    lobbyTicks = 0;
                }
                if (lobbyTicks <= 0) {
                    stopLobbyCountdown();
                    session.transitionTo(new CountdownState(plugin, arena, session));
                    return;
                }

                final int totalRemaining = lobbyTicks + gameCountdown;
                if (lobbyBar != null) {
                    lobbyBar.setTitle("Starting in " + totalRemaining + "s  —  "
                            + session.getAliveCount() + "/" + arena.getConfig().maxPlayers() + " players");
                    lobbyBar.setProgress(Math.max(0.0, Math.min(1.0, (double) lobbyTicks / lobbyTotal)));
                }
                if (lobbyTicks % 15 == 0 || lobbyTicks == 5) {
                    broadcastToArena(plugin.getMiniMessage().deserialize(
                            "<gold>Game starts in <yellow>" + totalRemaining + "</yellow>s "
                                    + "<dark_gray>(<gray>" + session.getAliveCount() + "/"
                                    + arena.getConfig().maxPlayers() + "<dark_gray>)"));
                }
                lobbyTicks--;
            }
        };
        lobbyTask.runTaskTimer(plugin, 0L, 20L);
    }

    /** Lazily create the shared lobby boss bar (reused across waiting → counting). */
    private void ensureBar() {
        if (lobbyBar == null) {
            lobbyBar = plugin.getServer().createBossBar("Waiting…", BarColor.YELLOW, BarStyle.SOLID);
        }
    }

    /** Ambient "waiting for players" bar shown before the minimum is reached. */
    private void showWaiting() {
        ensureBar();
        final int min = Math.max(1, arena.getConfig().minPlayers());
        final int alive = session.getAliveCount();
        lobbyBar.setColor(BarColor.YELLOW);
        lobbyBar.setTitle("Waiting for players — " + alive + "/" + min + " to start");
        lobbyBar.setProgress(Math.max(0.0, Math.min(1.0, (double) alive / min)));
    }

    /** Stop the filling countdown but keep the bar (we may fall back to waiting). */
    private void stopCounting() {
        if (lobbyTask != null) {
            lobbyTask.cancel();
            lobbyTask = null;
        }
        lobbyTicks = -1;
    }

    /** Full teardown — stop counting and remove the bar (state exit / empty lobby). */
    private void stopLobbyCountdown() {
        stopCounting();
        if (lobbyBar != null) {
            lobbyBar.removeAll();
            lobbyBar = null;
        }
    }

    private void broadcastToArena(Component message) {
        for (var uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    private void playSound(Player player, String sound) {
        try {
            player.playSound(player.getLocation(), sound, 0.7f, 1.4f);
        } catch (Exception ignored) {
            // Invalid sound key — ignore.
        }
    }
}
