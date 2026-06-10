package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Config-driven achievements/milestones awarded from lifetime stats. Definitions
 * live under {@code achievements.list} in config.yml; earned achievements are
 * persisted per player UUID in {@code achievements.yml}. {@link #checkAndAward}
 * is called at the natural stat-recording points (win/kill/game/elimination) and
 * grants anything newly crossed: a server broadcast, a title + sound, and any
 * configured reward commands.
 *
 * @author DeWost
 */
public final class AchievementManager {

    private final LavaRisePlugin plugin;
    private final File file;
    private final FileConfiguration store;
    private final List<Achievement> achievements = new ArrayList<>();
    private boolean enabled;

    public AchievementManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "achievements.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create achievements.yml", e);
            }
        }
        this.store = YamlConfiguration.loadConfiguration(file);
        load();
    }

    /** (Re)load definitions from config — safe to call from {@code /lr reload}. */
    public void load() {
        achievements.clear();
        this.enabled = plugin.getConfig().getBoolean("achievements.enabled", true);
        final ConfigurationSection sec = plugin.getConfig().getConfigurationSection("achievements.list");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            final ConfigurationSection a = sec.getConfigurationSection(id);
            if (a == null) {
                continue;
            }
            achievements.add(new Achievement(
                    id,
                    a.getString("name", id),
                    a.getString("description", ""),
                    a.getString("stat", "wins").trim().toLowerCase(Locale.ROOT),
                    a.getLong("threshold", 1L),
                    a.getStringList("reward-commands")));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Achievement> getAchievements() {
        return Collections.unmodifiableList(achievements);
    }

    /** Current lifetime value of {@code stat} for a player. */
    public long statValue(UUID id, String stat) {
        return switch (stat) {
            case "kills" -> plugin.getStatsManager().getKills(id);
            case "games" -> plugin.getStatsManager().getGames(id);
            case "best_time", "time", "survival" -> plugin.getStatsManager().getBestTime(id);
            default -> plugin.getStatsManager().getWins(id);
        };
    }

    public boolean hasEarned(UUID id, String achievementId) {
        return store.getStringList(id.toString()).contains(achievementId);
    }

    /** Award any not-yet-earned achievements the player now qualifies for. */
    public void checkAndAward(Player player) {
        if (!enabled || player == null) {
            return;
        }
        final UUID id = player.getUniqueId();
        for (Achievement a : achievements) {
            if (!hasEarned(id, a.id()) && statValue(id, a.stat()) >= a.threshold()) {
                award(player, a);
            }
        }
    }

    private void award(Player player, Achievement a) {
        final UUID id = player.getUniqueId();
        final List<String> earned = new ArrayList<>(store.getStringList(id.toString()));
        earned.add(a.id());
        store.set(id.toString(), earned);
        save();

        Bukkit.broadcast(plugin.getMiniMessage().deserialize(
                "<gold>🏆 <yellow>" + player.getName() + "</yellow> unlocked <gold><bold>"
                        + a.name() + "</bold></gold>!"));
        player.showTitle(Title.title(
                plugin.getMiniMessage().deserialize("<gold>🏆 <bold>Achievement!</bold>"),
                plugin.getMiniMessage().deserialize("<yellow>" + a.name()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))));
        try {
            player.playSound(player.getLocation(), "ui.toast.challenge_complete", 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Invalid sound key on some forks — cosmetic only.
        }
        for (String cmd : a.rewardCommands()) {
            if (cmd != null && !cmd.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
            }
        }
    }

    public void save() {
        try {
            store.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save achievements.yml", e);
        }
    }

    /** A single achievement definition. */
    public record Achievement(String id, String name, String description,
                              String stat, long threshold, List<String> rewardCommands) {
    }
}
