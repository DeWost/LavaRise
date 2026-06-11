package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Config-driven XP and level progression system. Total XP per player is
 * persisted in {@code levels.yml} (one long per UUID). Level and progress are
 * derived on the fly by walking the exponential curve defined in config:
 * {@code xpForLevel(n) = round(base-xp * growth^(n-1))} — XP needed to advance
 * from level n to level n+1. Stat hooks in listener, state and PAPI call
 * {@link #addXp} at natural game events.
 *
 * @author DeWost
 */
public final class LevelManager {

    private final LavaRisePlugin plugin;
    private final File file;
    private final FileConfiguration store;

    /** In-memory cache of UUID → total XP. Written through to store on every change. */
    private final Map<UUID, Long> totalXp = new HashMap<>();

    // ── Config cache ────────────────────────────────────────────
    private boolean enabled;
    private long xpPerKill;
    private long xpPerWin;
    private long xpPerGame;
    private long xpPerSurvivalSecond;
    private long survivalXpCap;
    private double baseXp;
    private double growth;
    private List<String> levelUpCommands;

    /**
     * Constructs the manager and loads persisted data from disk. The config
     * values are read via {@link #load()}.
     *
     * @param plugin owning plugin instance
     */
    public LevelManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "levels.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create levels.yml", e);
            }
        }
        this.store = YamlConfiguration.loadConfiguration(file);
        load();
        loadStoredXp();
    }

    // ── Load / save ──────────────────────────────────────────────

    /** (Re)load config values — safe to call from {@code /lr reload}. */
    public void load() {
        this.enabled = plugin.getConfig().getBoolean("levels.enabled", true);
        this.xpPerKill = plugin.getConfig().getLong("levels.xp-per-kill", 25L);
        this.xpPerWin = plugin.getConfig().getLong("levels.xp-per-win", 100L);
        this.xpPerGame = plugin.getConfig().getLong("levels.xp-per-game", 10L);
        this.xpPerSurvivalSecond = plugin.getConfig().getLong("levels.xp-per-survival-second", 1L);
        this.survivalXpCap = plugin.getConfig().getLong("levels.survival-xp-cap", 300L);
        this.baseXp = plugin.getConfig().getDouble("levels.base-xp", 100.0);
        this.growth = plugin.getConfig().getDouble("levels.growth", 1.15);
        this.levelUpCommands = plugin.getConfig().getStringList("levels.level-up-commands");
    }

    /** Read all UUID→totalXp entries from the YAML store into memory. */
    private void loadStoredXp() {
        totalXp.clear();
        for (String key : store.getKeys(false)) {
            try {
                final UUID uuid = UUID.fromString(key);
                totalXp.put(uuid, store.getLong(key, 0L));
            } catch (IllegalArgumentException ignored) {
                // Non-UUID key — skip.
            }
        }
    }

    /** Persist current in-memory XP values to disk. */
    public void save() {
        for (Map.Entry<UUID, Long> entry : totalXp.entrySet()) {
            store.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            store.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save levels.yml", e);
        }
    }

    // ── Core logic ───────────────────────────────────────────────

    /**
     * Returns the XP required to advance from level {@code n} to level {@code n+1}.
     * Uses the formula {@code round(base-xp * growth^(n-1))}.
     *
     * @param n the current level (1-based)
     * @return XP threshold for that level
     */
    public long xpForLevel(int n) {
        if (n < 1) {
            n = 1;
        }
        return Math.round(baseXp * Math.pow(growth, n - 1));
    }

    /**
     * Derives the level a player is at given their cumulative total XP.
     *
     * @param total cumulative XP earned
     * @return current level (1-based)
     */
    public int levelFromTotalXp(long total) {
        if (total < 0) {
            total = 0;
        }
        int level = 1;
        long remaining = total;
        while (remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level);
            level++;
        }
        return level;
    }

    /**
     * XP earned so far within the current level (i.e. progress towards next level).
     *
     * @param total cumulative XP
     * @return XP into current level
     */
    public long xpIntoLevel(long total) {
        if (total < 0) {
            total = 0;
        }
        long remaining = total;
        int level = 1;
        while (remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level);
            level++;
        }
        return remaining;
    }

    // ── Public API ───────────────────────────────────────────────

    /**
     * Award {@code amount} XP to a player, handling level-ups (including
     * multi-level crossings). No-ops when disabled or amount ≤ 0.
     *
     * @param player the recipient
     * @param amount XP to add
     * @param reason human-readable reason for debug logging
     */
    public void addXp(Player player, long amount, String reason) {
        if (!enabled) {
            return;
        }
        if (amount <= 0) {
            return;
        }

        final UUID uuid = player.getUniqueId();
        final int levelBefore = levelFromTotalXp(getTotalXp(uuid));
        final long newTotal = getTotalXp(uuid) + amount;
        totalXp.put(uuid, newTotal);
        store.set(uuid.toString(), newTotal);
        save();

        plugin.debug("[LevelManager] " + player.getName() + " +" + amount + " XP (" + reason + ") → total " + newTotal);

        // Handle one or more level-ups that the XP gain may have crossed.
        final int levelAfter = levelFromTotalXp(newTotal);
        for (int lvl = levelBefore + 1; lvl <= levelAfter; lvl++) {
            announceAndRunCommands(player, lvl);
        }
    }

    /**
     * Returns the current level of the player.
     *
     * @param uuid player UUID
     * @return level (≥ 1)
     */
    public int getLevel(UUID uuid) {
        return levelFromTotalXp(getTotalXp(uuid));
    }

    /**
     * Returns the XP earned so far within the current level.
     *
     * @param uuid player UUID
     * @return XP into current level
     */
    public long getXp(UUID uuid) {
        return xpIntoLevel(getTotalXp(uuid));
    }

    /**
     * Returns the XP still needed to reach the next level.
     *
     * @param uuid player UUID
     * @return XP needed for next level
     */
    public long getXpNeeded(UUID uuid) {
        final int level = getLevel(uuid);
        return xpForLevel(level) - getXp(uuid);
    }

    /**
     * Returns the total cumulative XP ever earned by the player.
     *
     * @param uuid player UUID
     * @return total XP
     */
    public long getTotalXp(UUID uuid) {
        return totalXp.getOrDefault(uuid, 0L);
    }

    // ── Config accessors (used at stat hook sites) ───────────────

    /** XP awarded per PvP kill. */
    public long xpPerKill() {
        return xpPerKill;
    }

    /** XP awarded for winning a game. */
    public long xpPerWin() {
        return xpPerWin;
    }

    /** XP awarded for participating in any game. */
    public long xpPerGame() {
        return xpPerGame;
    }

    /** XP awarded per second of survival (subject to {@link #survivalXpCap()}). */
    public long xpPerSurvivalSecond() {
        return xpPerSurvivalSecond;
    }

    /** Maximum XP that can be earned from survival time in a single game. */
    public long survivalXpCap() {
        return survivalXpCap;
    }

    /** Whether the level system is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    // ── Level-up notification ────────────────────────────────────

    /**
     * Broadcast the level-up, show a title + sound to the player, and run any
     * configured level-up commands.
     */
    private void announceAndRunCommands(Player player, int newLevel) {
        Bukkit.broadcast(plugin.getMiniMessage().deserialize(
                "<aqua>★ <yellow>" + player.getName() + "</yellow> reached <aqua>Level "
                        + newLevel + "</aqua>!"));

        player.showTitle(Title.title(
                plugin.getMiniMessage().deserialize("<aqua><bold>LEVEL UP!</bold></aqua>"),
                plugin.getMiniMessage().deserialize("<yellow>You reached <aqua>Level " + newLevel + "</aqua>!"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(700))));

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // Sound may not be available on all forks — cosmetic only.
        }

        for (String cmd : levelUpCommands) {
            if (cmd != null && !cmd.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        cmd.replace("{player}", player.getName())
                                .replace("{level}", String.valueOf(newLevel)));
            }
        }
    }
}
