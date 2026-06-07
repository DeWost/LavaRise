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
    private boolean updateCheck;
    private boolean bstatsEnabled;
    // Storage
    private String storageType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUser;
    private String mysqlPassword;
    private String mysqlTablePrefix;
    private boolean mysqlSsl;
    private int statsSyncInterval;
    private int maxBlocksPerTick;
    private int engineIntervalTicks;
    private boolean preloadChunks;
    private int pvpAfterHeight;
    private boolean pvpDuringGrace;
    private boolean autoPickup;
    private boolean autoSmelt;
    private boolean denyMobSpawns;
    private List<String> killCommands;
    private List<String> deathCommands;
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

    // ── Modes ───────────────────────────────────────────────
    private boolean teamsEnabled;
    private int teamSize;
    private boolean survivalEnabled;
    private List<String> survivalWorlds;
    private int survivalRadius;
    private boolean survivalAnnounce;
    private boolean eventEnabled;
    private boolean eventBroadcastStart;
    private boolean eventBroadcastEnd;
    private boolean eventRewardEnabled;
    private double eventRewardAmount;

    // ── Procedural / random arenas ──────────────────────────
    private boolean proceduralEnabled;
    private String proceduralWorld;
    private int proceduralRadius;
    private int proceduralLavaStartY;
    private int proceduralLavaMaxY;
    private int proceduralSpawnArea;
    private boolean proceduralAutoQuickjoin;
    // Auto-arena
    private boolean autoArenaEnabled;
    private int autoArenaCheckInterval;
    private boolean autoArenaAutoJoin;

    // ── Effects ─────────────────────────────────────────────
    private boolean bossBarEnabled;
    private String bossBarColor;
    private String bossBarStyle;
    private boolean actionBarEnabled;
    private int hudIntervalTicks;
    private boolean scoreboardEnabled;
    private boolean particlesEnabled;
    private String particleType;
    private int particleDensity;
    private int particleIntervalTicks;
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

        // Config version warning (helps admins notice an outdated config after upgrades).
        final int CURRENT_CONFIG_VERSION = 2;
        if (config.contains("config-version") && config.getInt("config-version") < CURRENT_CONFIG_VERSION) {
            plugin.getLogger().warning("Your config.yml is version " + config.getInt("config-version")
                    + " but this build expects " + CURRENT_CONFIG_VERSION
                    + ". New options will use defaults — regenerate or merge config.yml to silence this.");
        }

        // General
        this.debug = config.getBoolean("general.debug", false);
        this.updateCheck = config.getBoolean("general.update-check", true);
        this.bstatsEnabled = config.getBoolean("general.bstats", true);

        // Storage
        this.storageType = config.getString("storage.type", "yaml");
        this.mysqlHost = config.getString("storage.mysql.host", "localhost");
        this.mysqlPort = config.getInt("storage.mysql.port", 3306);
        this.mysqlDatabase = config.getString("storage.mysql.database", "lavarise");
        this.mysqlUser = config.getString("storage.mysql.username", "root");
        this.mysqlPassword = config.getString("storage.mysql.password", "");
        this.mysqlTablePrefix = config.getString("storage.mysql.table-prefix", "lavarise_");
        this.mysqlSsl = config.getBoolean("storage.mysql.ssl", false);
        this.statsSyncInterval = Math.max(15, config.getInt("storage.sync-interval", 120));

        // Performance
        this.maxBlocksPerTick = config.getInt("performance.max-blocks-per-tick", 64);
        this.engineIntervalTicks = Math.max(1, config.getInt("performance.engine-interval-ticks", 2));
        this.preloadChunks = config.getBoolean("performance.preload-chunks", true);

        // Gameplay QoL / rules
        this.pvpAfterHeight = config.getInt("gameplay.pvp-after-height", 0);
        // PvP stays locked through the start-of-game grace window unless explicitly allowed.
        this.pvpDuringGrace = config.getBoolean("gameplay.pvp-during-grace", false);
        this.autoPickup = config.getBoolean("gameplay.auto-pickup", false);
        this.autoSmelt = config.getBoolean("gameplay.auto-smelt", false);
        this.denyMobSpawns = config.getBoolean("gameplay.deny-mob-spawns", true);
        this.killCommands = config.getStringList("rewards.kill-commands");
        this.deathCommands = config.getStringList("rewards.death-commands");

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

        // Modes
        this.teamsEnabled = config.getBoolean("modes.minigame.teams-enabled", false);
        this.teamSize = Math.max(1, config.getInt("modes.minigame.team-size", 2));
        this.survivalEnabled = config.getBoolean("modes.survival.enabled", true);
        this.survivalWorlds = config.getStringList("modes.survival.worlds");
        this.survivalRadius = config.getInt("modes.survival.radius", 500);
        this.survivalAnnounce = config.getBoolean("modes.survival.announce", true);
        this.eventEnabled = config.getBoolean("modes.event.enabled", true);
        this.eventBroadcastStart = config.getBoolean("modes.event.broadcast-start", true);
        this.eventBroadcastEnd = config.getBoolean("modes.event.broadcast-end", true);
        this.eventRewardEnabled = config.getBoolean("modes.event.reward-enabled", false);
        this.eventRewardAmount = config.getDouble("modes.event.reward-amount", 1000.0);

        // Procedural / random arenas
        this.proceduralEnabled = config.getBoolean("procedural.enabled", true);
        this.proceduralWorld = config.getString("procedural.world", "world");
        this.proceduralRadius = Math.max(8, config.getInt("procedural.radius", 30));
        this.proceduralLavaStartY = config.getInt("procedural.lava-start-y", 60);
        this.proceduralLavaMaxY = config.getInt("procedural.lava-max-y", 150);
        this.proceduralSpawnArea = Math.max(0, config.getInt("procedural.spawn-area", 2000));
        this.proceduralAutoQuickjoin = config.getBoolean("procedural.auto-on-quickjoin", true);
        this.autoArenaEnabled = config.getBoolean("auto-arena.enabled", false);
        this.autoArenaCheckInterval = Math.max(5, config.getInt("auto-arena.check-interval", 15));
        this.autoArenaAutoJoin = config.getBoolean("auto-arena.auto-join", false);

        // Effects
        this.bossBarEnabled = config.getBoolean("effects.bossbar.enabled", true);
        this.bossBarColor = config.getString("effects.bossbar.color", "RED");
        this.bossBarStyle = config.getString("effects.bossbar.style", "SEGMENTED_10");
        this.actionBarEnabled = config.getBoolean("effects.actionbar.enabled", true);
        this.hudIntervalTicks = Math.max(1, config.getInt("effects.actionbar.update-interval", 10));
        this.scoreboardEnabled = config.getBoolean("effects.scoreboard.enabled", true);
        this.particlesEnabled = config.getBoolean("effects.particles.enabled", true);
        this.particleType = config.getString("effects.particles.type", "LAVA");
        this.particleDensity = config.getInt("effects.particles.density", 2);
        this.particleIntervalTicks = Math.max(1, config.getInt("effects.particles.interval", 10));
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
    public boolean isUpdateCheck() { return updateCheck; }
    public boolean isBstatsEnabled() { return bstatsEnabled; }

    public String getStorageType() { return storageType; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUser() { return mysqlUser; }
    public String getMysqlPassword() { return mysqlPassword; }
    public String getMysqlTablePrefix() { return mysqlTablePrefix; }
    public boolean isMysqlSsl() { return mysqlSsl; }
    public int getStatsSyncInterval() { return statsSyncInterval; }
    public int getMaxBlocksPerTick() { return maxBlocksPerTick; }
    public int getEngineIntervalTicks() { return engineIntervalTicks; }
    public boolean isPreloadChunks() { return preloadChunks; }
    public int getPvpAfterHeight() { return pvpAfterHeight; }
    public boolean isPvpDuringGrace() { return pvpDuringGrace; }
    public boolean isAutoPickup() { return autoPickup; }
    public boolean isAutoSmelt() { return autoSmelt; }
    public boolean isDenyMobSpawns() { return denyMobSpawns; }
    public List<String> getKillCommands() { return Collections.unmodifiableList(killCommands); }
    public List<String> getDeathCommands() { return Collections.unmodifiableList(deathCommands); }
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

    // ── Getters: modes ──────────────────────────────────────

    public boolean isTeamsEnabled() { return teamsEnabled; }
    public int getTeamSize() { return teamSize; }
    public boolean isSurvivalEnabled() { return survivalEnabled; }
    public List<String> getSurvivalWorlds() { return Collections.unmodifiableList(survivalWorlds); }
    public int getSurvivalRadius() { return survivalRadius; }
    public boolean isSurvivalAnnounce() { return survivalAnnounce; }
    public boolean isEventEnabled() { return eventEnabled; }
    public boolean isEventBroadcastStart() { return eventBroadcastStart; }
    public boolean isEventBroadcastEnd() { return eventBroadcastEnd; }
    public boolean isEventRewardEnabled() { return eventRewardEnabled; }
    public double getEventRewardAmount() { return eventRewardAmount; }

    // ── Getters: procedural / random arenas ─────────────────

    public boolean isProceduralEnabled() { return proceduralEnabled; }
    public String getProceduralWorld() { return proceduralWorld; }
    public int getProceduralRadius() { return proceduralRadius; }
    public int getProceduralLavaStartY() { return proceduralLavaStartY; }
    public int getProceduralLavaMaxY() { return proceduralLavaMaxY; }
    public int getProceduralSpawnArea() { return proceduralSpawnArea; }
    public boolean isProceduralAutoQuickjoin() { return proceduralAutoQuickjoin; }
    public boolean isAutoArenaEnabled() { return autoArenaEnabled; }
    public int getAutoArenaCheckInterval() { return autoArenaCheckInterval; }
    public boolean isAutoArenaAutoJoin() { return autoArenaAutoJoin; }

    // ── Getters: effects ────────────────────────────────────

    public boolean isBossBarEnabled() { return bossBarEnabled; }
    public String getBossBarColor() { return bossBarColor; }
    public String getBossBarStyle() { return bossBarStyle; }
    public boolean isActionBarEnabled() { return actionBarEnabled; }
    public int getHudIntervalTicks() { return hudIntervalTicks; }
    public boolean isScoreboardEnabled() { return scoreboardEnabled; }
    public boolean isParticlesEnabled() { return particlesEnabled; }
    public String getParticleType() { return particleType; }
    public int getParticleDensity() { return particleDensity; }
    public int getParticleIntervalTicks() { return particleIntervalTicks; }
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
