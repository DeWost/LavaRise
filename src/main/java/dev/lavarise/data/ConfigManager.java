package dev.lavarise.data;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.List;

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
    private boolean preloadChunks;
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
    private boolean defaultKeepInventory;
    private boolean defaultHunger;
    private String defaultGameMode;

    // ── Gameplay rules ──────────────────────────────────────
    private int gracePeriod;
    private boolean accelerationEnabled;
    private int accelerationEverySeconds;
    private int accelerationReduceBy;
    private int accelerationMinInterval;
    private boolean dynamicSpeedEnabled;
    private boolean suddenDeathEnabled;
    private int suddenDeathPlayers;
    private int suddenDeathInterval;
    private boolean worldBorderEnabled;
    private int worldBorderMinSize;
    private boolean blockGiveEnabled;
    private int blockGiveIntervalSeconds;
    private String blockGiveMaterial;
    private int blockGiveAmount;
    private int blockGiveMaxStack;
    private boolean kitEnabled;
    private List<String> kitItems;
    private List<String> winCommands;

    // ── Effects ─────────────────────────────────────────────
    private boolean bossBarEnabled;
    private String bossBarColor;
    private String bossBarStyle;
    private boolean actionBarEnabled;
    private boolean scoreboardEnabled;
    private boolean particlesEnabled;
    private String particleType;
    private int particleDensity;
    private boolean soundsEnabled;
    private String soundLavaRise;
    private String soundGameStart;
    private String soundGameEnd;
    private String soundWarning;
    private int warningDistance;

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
        this.preloadChunks = config.getBoolean("performance.preload-chunks", true);

        // Lava defaults
        this.lavaRiseInterval = config.getInt("lava.default-rise-interval", 100);
        this.lavaRiseAmount = config.getInt("lava.default-rise-amount", 1);

        // Arena defaults (support both legacy "arena" and current "arena-defaults" sections)
        this.defaultMinPlayers = intDefault(config, "min-players", 2);
        this.defaultMaxPlayers = intDefault(config, "max-players", 16);
        this.defaultCountdown = intDefault(config, "game-countdown", 10);
        this.defaultLavaStartY = intDefault(config, "lava-start-y", -64);
        this.defaultLavaMaxY = intDefault(config, "lava-max-y", 320);
        this.defaultPvp = boolDefault(config, "pvp-enabled", "default-pvp", true);
        this.defaultBlockBreak = boolDefault(config, "block-break", "default-block-break", true);
        this.defaultBlockPlace = boolDefault(config, "block-place", "default-block-place", true);
        this.defaultKeepInventory = boolDefault(config, "keep-inventory", "keep-inventory", false);
        this.defaultHunger = boolDefault(config, "hunger", "hunger", true);
        this.defaultGameMode = config.getString("arena-defaults.game-mode",
                config.getString("arena.default-game-mode", "minigame"));

        // Gameplay rules
        this.gracePeriod = config.getInt("gameplay.grace-period", 0);
        this.accelerationEnabled = config.getBoolean("gameplay.acceleration.enabled", false);
        this.accelerationEverySeconds = config.getInt("gameplay.acceleration.speed-up-every", 30);
        this.accelerationReduceBy = config.getInt("gameplay.acceleration.reduce-by", 5);
        this.accelerationMinInterval = config.getInt("gameplay.acceleration.min-interval", 10);
        this.dynamicSpeedEnabled = config.getBoolean("gameplay.dynamic-speed.enabled", false);
        this.suddenDeathEnabled = config.getBoolean("gameplay.sudden-death.enabled", false);
        this.suddenDeathPlayers = config.getInt("gameplay.sudden-death.players", 3);
        this.suddenDeathInterval = config.getInt("gameplay.sudden-death.interval", 10);
        this.worldBorderEnabled = config.getBoolean("gameplay.world-border.enabled", false);
        this.worldBorderMinSize = config.getInt("gameplay.world-border.min-size", 20);
        this.blockGiveEnabled = config.getBoolean("gameplay.block-give.enabled", false);
        this.blockGiveIntervalSeconds = config.getInt("gameplay.block-give.interval", 15);
        this.blockGiveMaterial = config.getString("gameplay.block-give.material", "COBBLESTONE");
        this.blockGiveAmount = config.getInt("gameplay.block-give.amount", 1);
        this.blockGiveMaxStack = config.getInt("gameplay.block-give.max-stack", 64);
        this.kitEnabled = config.getBoolean("gameplay.kit.enabled", false);
        this.kitItems = config.getStringList("gameplay.kit.items");
        this.winCommands = config.getStringList("rewards.win-commands");

        // Effects
        this.bossBarEnabled = config.getBoolean("effects.bossbar.enabled", true);
        this.bossBarColor = config.getString("effects.bossbar.color", "RED");
        this.bossBarStyle = config.getString("effects.bossbar.style", "SEGMENTED_10");
        this.actionBarEnabled = config.getBoolean("effects.actionbar.enabled", true);
        this.scoreboardEnabled = config.getBoolean("effects.scoreboard.enabled", true);
        this.particlesEnabled = config.getBoolean("effects.particles.enabled", true);
        this.particleType = config.getString("effects.particles.type", "LAVA");
        this.particleDensity = config.getInt("effects.particles.density", 2);
        this.soundsEnabled = config.getBoolean("effects.sounds.enabled", true);
        this.soundLavaRise = config.getString("effects.sounds.lava-rise", "block.lava.ambient");
        this.soundGameStart = config.getString("effects.sounds.game-start", "entity.ender_dragon.growl");
        this.soundGameEnd = config.getString("effects.sounds.game-end", "ui.toast.challenge_complete");
        this.soundWarning = config.getString("effects.sounds.warning", "block.note_block.pling");
        this.warningDistance = config.getInt("effects.sounds.warning-distance", 5);

        // Messages
        loadMessages();

        plugin.debug("Configuration loaded successfully.");
    }

    private int intDefault(FileConfiguration config, String key, int def) {
        return config.getInt("arena-defaults." + key, config.getInt("arena.default-" + key, def));
    }

    private boolean boolDefault(FileConfiguration config, String newKey, String legacyKey, boolean def) {
        return config.getBoolean("arena-defaults." + newKey, config.getBoolean("arena." + legacyKey, def));
    }

    private void loadMessages() {
        final File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    // ── Getters: general / performance ──────────────────────

    public boolean isDebug() { return debug; }
    public int getMaxBlocksPerTick() { return maxBlocksPerTick; }
    public int getPlayerCheckInterval() { return playerCheckInterval; }
    public boolean isPreloadChunks() { return preloadChunks; }
    public int getLavaRiseInterval() { return lavaRiseInterval; }
    public int getLavaRiseAmount() { return lavaRiseAmount; }

    // ── Getters: arena defaults ─────────────────────────────

    public int getDefaultMinPlayers() { return defaultMinPlayers; }
    public int getDefaultMaxPlayers() { return defaultMaxPlayers; }
    public int getDefaultCountdown() { return defaultCountdown; }
    public int getDefaultLavaStartY() { return defaultLavaStartY; }
    public int getDefaultLavaMaxY() { return defaultLavaMaxY; }
    public boolean isDefaultPvp() { return defaultPvp; }
    public boolean isDefaultBlockBreak() { return defaultBlockBreak; }
    public boolean isDefaultBlockPlace() { return defaultBlockPlace; }
    public boolean isDefaultKeepInventory() { return defaultKeepInventory; }
    public boolean isDefaultHunger() { return defaultHunger; }
    public String getDefaultGameMode() { return defaultGameMode; }

    // ── Getters: gameplay rules ─────────────────────────────

    public int getGracePeriod() { return gracePeriod; }
    public boolean isAccelerationEnabled() { return accelerationEnabled; }
    public int getAccelerationEverySeconds() { return accelerationEverySeconds; }
    public int getAccelerationReduceBy() { return accelerationReduceBy; }
    public int getAccelerationMinInterval() { return accelerationMinInterval; }
    public boolean isDynamicSpeedEnabled() { return dynamicSpeedEnabled; }
    public boolean isSuddenDeathEnabled() { return suddenDeathEnabled; }
    public int getSuddenDeathPlayers() { return suddenDeathPlayers; }
    public int getSuddenDeathInterval() { return suddenDeathInterval; }
    public boolean isWorldBorderEnabled() { return worldBorderEnabled; }
    public int getWorldBorderMinSize() { return worldBorderMinSize; }
    public boolean isBlockGiveEnabled() { return blockGiveEnabled; }
    public int getBlockGiveIntervalSeconds() { return blockGiveIntervalSeconds; }
    public String getBlockGiveMaterial() { return blockGiveMaterial; }
    public int getBlockGiveAmount() { return blockGiveAmount; }
    public int getBlockGiveMaxStack() { return blockGiveMaxStack; }
    public boolean isKitEnabled() { return kitEnabled; }
    public List<String> getKitItems() { return Collections.unmodifiableList(kitItems); }
    public List<String> getWinCommands() { return Collections.unmodifiableList(winCommands); }

    // ── Getters: effects ────────────────────────────────────

    public boolean isBossBarEnabled() { return bossBarEnabled; }
    public String getBossBarColor() { return bossBarColor; }
    public String getBossBarStyle() { return bossBarStyle; }
    public boolean isActionBarEnabled() { return actionBarEnabled; }
    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public boolean isParticlesEnabled() { return particlesEnabled; }
    public String getParticleType() { return particleType; }
    public int getParticleDensity() { return particleDensity; }
    public boolean isSoundsEnabled() { return soundsEnabled; }
    public String getSoundLavaRise() { return soundLavaRise; }
    public String getSoundGameStart() { return soundGameStart; }
    public String getSoundGameEnd() { return soundGameEnd; }
    public String getSoundWarning() { return soundWarning; }
    public int getWarningDistance() { return warningDistance; }

    // ── Messages ────────────────────────────────────────────

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

    /**
     * Raw access to messages.yml (for list values such as scoreboard lines).
     */
    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }
}
