package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

/**
 * Countdown state — game is about to start.
 * <p>
 * Counts down from the configured seconds, then transitions to {@link ActiveState}.
 * If players drop below minimum, reverts to {@link LobbyState}.
 * </p>
 */
public final class CountdownState implements GameState {

    private final LavaRisePlugin plugin;
    private final Arena arena;
    private final ArenaSession session;

    private int countdown;
    private BukkitRunnable countdownTask;

    public CountdownState(LavaRisePlugin plugin, Arena arena, ArenaSession session) {
        this.plugin = plugin;
        this.arena = arena;
        this.session = session;
        this.countdown = arena.getConfig().gameCountdown();
    }

    @Override
    public void onEnter() {
        plugin.debug("Arena " + arena.getName() + " entered COUNTDOWN state. " + countdown + "s");

        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<gold>Game starting in <yellow>" + countdown + "</yellow> seconds!"
        ));

        // Start countdown timer
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdown <= 0) {
                    this.cancel();
                    // Transition to active game!
                    session.transitionTo(new ActiveState(plugin, arena, session));
                    return;
                }

                // Show countdown to all players
                if (countdown <= 5 || countdown % 10 == 0) {
                    for (var uuid : session.getAllPlayerIds()) {
                        final Player p = plugin.getServer().getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            // Title for last 5 seconds
                            if (countdown <= 5) {
                                p.showTitle(Title.title(
                                        plugin.getMiniMessage().deserialize(getCountdownColor(countdown)
                                                + "<bold>" + countdown + "</bold>"),
                                        Component.empty(),
                                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
                                ));
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, getCountdownPitch(countdown));
                            } else {
                                p.sendMessage(plugin.getMiniMessage().deserialize(
                                        "<gold>" + countdown + " seconds..."
                                ));
                            }
                        }
                    }
                }

                countdown--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L); // Every second
    }

    @Override
    public void onExit() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    @Override
    public void onPlayerJoin(Player player) {
        // Late join during countdown is allowed
        if (arena.getConfig().lobbySpawn() != null) {
            player.teleport(arena.getConfig().lobbySpawn());
        }
        player.sendMessage(plugin.getMiniMessage().deserialize(
                "<gold>Game starts in <yellow>" + countdown + "</yellow> seconds!"
        ));
    }

    @Override
    public void onPlayerLeave(Player player) {
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>" + player.getName() + " <gray>left during countdown."
        ));

        // If below minimum, revert to lobby
        if (session.getAliveCount() < arena.getConfig().minPlayers()) {
            broadcastToArena(plugin.getMiniMessage().deserialize(
                    "<yellow>Not enough players! Returning to lobby..."
            ));
            session.transitionTo(new LobbyState(plugin, arena, session));
        }
    }

    @Override
    public void onPlayerEliminated(Player player) {
        // Can't be eliminated during countdown
    }

    @Override
    public boolean isJoinable() {
        return true; // Still joinable during countdown
    }

    @Override
    public String getDisplayName() {
        return "Starting (" + countdown + "s)";
    }

    // ── Helpers ──────────────────────────────────────────────

    private String getCountdownColor(int seconds) {
        return switch (seconds) {
            case 5 -> "<green>";
            case 4 -> "<yellow>";
            case 3 -> "<gold>";
            case 2 -> "<red>";
            case 1 -> "<dark_red>";
            default -> "<white>";
        };
    }

    private float getCountdownPitch(int seconds) {
        return switch (seconds) {
            case 5 -> 0.5f;
            case 4 -> 0.7f;
            case 3 -> 0.9f;
            case 2 -> 1.2f;
            case 1 -> 2.0f;
            default -> 1.0f;
        };
    }

    private void broadcastToArena(Component message) {
        for (var uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }
}
