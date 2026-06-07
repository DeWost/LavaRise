package dev.lavarise.feature;

import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles rendering the lava height as a BossBar for all players in an arena.
 */
public class BossBarModule {
    private final LavaRisePlugin plugin;
    private final Map<UUID, BossBar> activeBars = new HashMap<>();

    public BossBarModule(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public void updateFor(ArenaSession session) {
        if (!plugin.getConfigManager().isBossBarEnabled()) return;

        final int start = session.getArena().getConfig().lavaStartY();
        final int max = session.getArena().getConfig().lavaMaxY();
        final int range = Math.max(1, max - start);
        float progress = (float) (session.getCurrentLavaY() - start) / range;
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        final float finalProgress = progress;

        final BossBar.Color color = parseColor(plugin.getConfigManager().getBossBarColor());
        final BossBar.Overlay overlay = parseOverlay(plugin.getConfigManager().getBossBarStyle());

        final String template = plugin.getConfigManager().getMessage("bossbar.title")
                .replace("{lava_level}", String.valueOf(session.getLavaHeight()))
                .replace("{lava_percent}", session.getLavaPercent() + "%")
                .replace("{lava_y}", String.valueOf(session.getCurrentLavaY()))
                .replace("{alive}", String.valueOf(session.getAliveCount()));
        final Component title = plugin.getMiniMessage().deserialize(template);

        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            BossBar bar = activeBars.computeIfAbsent(uuid, k -> {
                BossBar newBar = BossBar.bossBar(title, finalProgress, color, overlay);
                p.showBossBar(newBar);
                return newBar;
            });
            bar.name(title);
            bar.progress(finalProgress);
            bar.color(color);
            bar.overlay(overlay);
        }
    }

    private BossBar.Color parseColor(String name) {
        try {
            return BossBar.Color.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay parseOverlay(String style) {
        return switch (style == null ? "" : style.toUpperCase()) {
            case "SOLID" -> BossBar.Overlay.PROGRESS;
            case "SEGMENTED_6" -> BossBar.Overlay.NOTCHED_6;
            case "SEGMENTED_12" -> BossBar.Overlay.NOTCHED_12;
            case "SEGMENTED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.NOTCHED_10;
        };
    }

    /**
     * Show the start-of-game grace countdown on the boss bar (green, draining)
     * instead of the lava bar. Mutates the same cached per-player bar, so the
     * later switch back to the lava bar via {@link #updateFor} is seamless.
     */
    public void showGrace(ArenaSession session, int secondsLeft, int graceTotal) {
        if (!plugin.getConfigManager().isBossBarEnabled()) return;
        final float progress = graceTotal <= 0 ? 1f
                : Math.max(0f, Math.min(1f, (float) secondsLeft / graceTotal));
        final Component title = plugin.getMiniMessage().deserialize(
                "<green>🛡 Grace period: <bold>" + secondsLeft + "s</bold> <gray>— gear up, no PvP!");
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) continue;
            BossBar bar = activeBars.computeIfAbsent(uuid, k -> {
                BossBar nb = BossBar.bossBar(title, progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
                p.showBossBar(nb);
                return nb;
            });
            bar.name(title);
            bar.progress(progress);
            bar.color(BossBar.Color.GREEN);
            bar.overlay(BossBar.Overlay.PROGRESS);
        }
    }

    public void removeFor(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }
}
