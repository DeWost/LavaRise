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
        float progress = (float) session.getCurrentLavaY() / session.getArena().getConfig().lavaMaxY();
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        Component title = plugin.getMiniMessage().deserialize("<red><bold>Lava Y-Level: " + session.getCurrentLavaY() + "</bold></red>");

        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                final float finalProgress = progress;
                BossBar bar = activeBars.computeIfAbsent(uuid, k -> {
                    BossBar newBar = BossBar.bossBar(title, finalProgress, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                    p.showBossBar(newBar);
                    return newBar;
                });
                bar.name(title);
                bar.progress(progress);
            }
        }
    }

    public void removeFor(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }
}
