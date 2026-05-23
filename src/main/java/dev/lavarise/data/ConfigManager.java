package dev.lavarise.data;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Central configuration manager.
 * <p>
 * Wraps Bukkit's FileConfiguration for type-safe access to all
 * config values. Caches frequently accessed values to avoid
 * repeated YAML lookups during hot paths.
 * </p>
 */
public final class ConfigManager {

    private final LavaRisePlugin plugin;

    // ── Cached values (hot path) ────────────────────────────
    private boolean debug;
    private int maxBlocksPerTick;
    private int playerCheckInterval;
    private int lavaRiseInterval;
    private int lavaRiseAmount;
    private int defaultMinPlayers;
    private int defaultMaxPlayers;
    private int defaultCountdown;
    private int defaultLavaStartY;
    private int defaultLavaMaxY;
    private boolean defaultPvp;
    private boolean defaultBlockBreak;
    private boolean defaultBlockPlace;
    private String defaultGameMode;

    // ── Messages config ─────────────────────────────────────
    private FileConfiguration messagesConfig;

    public ConfigManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load all configuration values from disk.
     * Called on startup and reload.
     */
    public void loadAll() {
        final FileConfiguration config = plugin.getConfig();

        // General
        this.debug = config.getBoolean("general.debug", false);

        // Performance
        this.maxBlocksPerTick = config.getInt("performance.max-blocks-per-tick", 64);
        this.playerCheckInterval = config.getInt("performance.player-check-interval", 5);

        // Lava defaults
        this.lavaRiseInterval = config.getInt("lava.default-rise-interval", 100);
        this.lavaRiseAmount = config.getInt("lava.default-rise-amount", 1);

        // Arena defaults
        this.defaultMinPlayers = config.getInt("arena.default-min-players", 2);
        this.defaultMaxPlayers = config.getInt("arena.default-max-players", 16);
        this.defaultCountdown = config.getInt("arena.default-countdown", 10);
        this.defaultLavaStartY = config.getInt("arena.default-lava-start-y", -60);
        this.defaultLavaMaxY = config.getInt("arena.default-lava-max-y", 320);
        this.defaultPvp = config.getBoolean("arena.default-pvp", true);
        this.defaultBlockBreak = config.getBoolean("arena.default-block-break", true);
        this.defaultBlockPlace = config.getBoolean("arena.default-block-place", true);
        this.defaultGameMode = config.getString("arena.default-game-mode", "minigame");

        // Messages
        loadMessages();

        plugin.debug("Configuration loaded successfully.");
    }

    private void loadMessages() {
        final File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    // ── Getters ─────────────────────────────────────────────

    public boolean isDebug() {
        return debug;
    }

    public int getMaxBlocksPerTick() {
        return maxBlocksPerTick;
    }

    public int getPlayerCheckInterval() {
        return playerCheckInterval;
    }

    public int getLavaRiseInterval() {
        return lavaRiseInterval;
    }

    public int getLavaRiseAmount() {
        return lavaRiseAmount;
    }

    public int getDefaultMinPlayers() {
        return defaultMinPlayers;
    }

    public int getDefaultMaxPlayers() {
        return defaultMaxPlayers;
    }

    public int getDefaultCountdown() {
        return defaultCountdown;
    }

    public int getDefaultLavaStartY() {
        return defaultLavaStartY;
    }

    public int getDefaultLavaMaxY() {
        return defaultLavaMaxY;
    }

    public boolean isDefaultPvp() {
        return defaultPvp;
    }

    public boolean isDefaultBlockBreak() {
        return defaultBlockBreak;
    }

    public boolean isDefaultBlockPlace() {
        return defaultBlockPlace;
    }

    public String getDefaultGameMode() {
        return defaultGameMode;
    }

    /**
     * Get a MiniMessage-formatted message string from messages.yml.
     * Falls back to the key itself if not found.
     */
    public String getMessage(String key) {
        return messagesConfig.getString(key, "<red>Missing message: " + key);
    }

    /**
     * Get a message with placeholder replacement.
     */
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }
}
