package dev.lavarise.core;

import dev.lavarise.command.LavaRiseCommand;
import dev.lavarise.data.ArenaRepository;
import dev.lavarise.data.ConfigManager;
import dev.lavarise.data.StatsManager;
import dev.lavarise.listener.ArenaEventRouter;
import dev.lavarise.listener.PlayerListener;
import dev.lavarise.feature.gui.ArenaSelectorGUI;
import dev.lavarise.feature.gui.KitSelectorGUI;
import dev.lavarise.feature.gui.SpectatorMenu;
import dev.lavarise.feature.gui.VoteGUI;
import dev.lavarise.feature.BossBarModule;
import dev.lavarise.feature.KitManager;
import dev.lavarise.feature.ScoreboardModule;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * LavaRise — Premium Rising Lava Plugin
 * <p>
 * Main entry point. Initializes all services, loads arenas,
 * and registers commands/listeners.
 * </p>
 *
 * @author DeWost
 */
public final class LavaRisePlugin extends JavaPlugin {

    private static LavaRisePlugin instance;

    /** bStats service id — register the plugin at https://bstats.org and replace this. */
    private static final int BSTATS_PLUGIN_ID = 27000;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private ConfigManager configManager;
    private GameManager gameManager;
    private ArenaRepository arenaRepository;
    private ArenaSelectorGUI guiManager;
    private ScoreboardModule scoreboardModule;
    private BossBarModule bossBarModule;
    private StatsManager statsManager;
    private dev.lavarise.feature.AchievementManager achievementManager;
    private dev.lavarise.feature.PowerUpModule powerUpModule;
    private KitManager kitManager;
    private dev.lavarise.feature.CustomKitManager customKitManager;
    private dev.lavarise.party.PartyManager partyManager;
    private dev.lavarise.party.QueueManager queueManager;
    private KitSelectorGUI kitSelectorGUI;
    private VoteGUI voteGUI;
    private dev.lavarise.feature.gui.AdminGUI adminGUI;
    private SpectatorMenu spectatorMenu;
    private dev.lavarise.hook.VaultHook vaultHook;
    private dev.lavarise.hook.DiscordHook discordHook;
    private AutoArenaController autoArena;
    private dev.lavarise.feature.CosmeticManager cosmeticManager;
    private dev.lavarise.feature.DoubleJumpModule doubleJumpModule;
    private dev.lavarise.feature.DeathCrateModule deathCrateModule;
    private dev.lavarise.feature.LevelManager levelManager;
    private dev.lavarise.feature.ChaosEventModule chaosEventModule;

    @Override
    public void onEnable() {
        instance = this;
        final long start = System.currentTimeMillis();

        getLogger().info("");
        getLogger().info("  _                     _____ _         ");
        getLogger().info(" | |                   |  __ (_)        ");
        getLogger().info(" | |     __ ___   ____ | |__) |___  ___ ");
        getLogger().info(" | |    / _` \\ \\ / / _`|  _  / / __|/ _ \\");
        getLogger().info(" | |___| (_| |\\ V / (_| | | \\ \\ \\__ \\  __/");
        getLogger().info(" |______\\__,_| \\_/ \\__,_|_|  \\_\\|___/\\___|");
        getLogger().info("");
        getLogger().info("    Ultra-Optimized Paper 1.21.11 Engine");
        getLogger().info("    by DeWost");
        getLogger().info("");
        // ── 1. Configuration ────────────────────────────────
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.configManager.loadAll();

        // ── 2. Data Layer ───────────────────────────────────
        this.statsManager = new StatsManager(this);
        this.achievementManager = new dev.lavarise.feature.AchievementManager(this);
        this.levelManager = new dev.lavarise.feature.LevelManager(this);
        this.cosmeticManager = new dev.lavarise.feature.CosmeticManager(this);
        this.arenaRepository = new ArenaRepository(this);

        // ── 3. Game Manager ─────────────────────────────────
        this.gameManager = new GameManager(this);

        // Arenas must be loaded after the GameManager exists (they auto-register).
        this.arenaRepository.loadArenas();

        // ── 4. Commands ─────────────────────────────────────
        final var command = getCommand("lavarise");
        if (command != null) {
            final var lavaCmd = new LavaRiseCommand(this);
            command.setExecutor(lavaCmd);
            command.setTabCompleter(lavaCmd);
        }

        // ── 5. Listeners ────────────────────────────────────
        final var pm = getServer().getPluginManager();
        pm.registerEvents(new ArenaEventRouter(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        this.guiManager = new ArenaSelectorGUI(this);
        this.scoreboardModule = new ScoreboardModule(this);
        this.bossBarModule = new BossBarModule(this);
        this.powerUpModule = new dev.lavarise.feature.PowerUpModule(this);
        pm.registerEvents(this.powerUpModule, this);
        this.chaosEventModule = new dev.lavarise.feature.ChaosEventModule(this);
        this.doubleJumpModule = new dev.lavarise.feature.DoubleJumpModule(this);
        pm.registerEvents(this.doubleJumpModule, this);
        this.deathCrateModule = new dev.lavarise.feature.DeathCrateModule(this);
        pm.registerEvents(this.deathCrateModule, this);
        this.kitManager = new KitManager(this);
        this.customKitManager = new dev.lavarise.feature.CustomKitManager(this);
        this.kitSelectorGUI = new KitSelectorGUI(this);
        this.voteGUI = new VoteGUI(this);
        this.adminGUI = new dev.lavarise.feature.gui.AdminGUI(this);
        this.spectatorMenu = new SpectatorMenu(this);
        this.partyManager = new dev.lavarise.party.PartyManager(this);
        this.queueManager = new dev.lavarise.party.QueueManager(this);

        // ── 6. Integrations ─────────────────────────────────
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new dev.lavarise.hook.PapiExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        this.vaultHook = new dev.lavarise.hook.VaultHook(this);
        if (vaultHook.setup()) {
            getLogger().info("Vault economy hooked — reward payouts enabled.");
        }

        this.discordHook = new dev.lavarise.hook.DiscordHook(this);
        this.discordHook.load();

        if (configManager.isUpdateCheck()) {
            new UpdateChecker(this).checkAsync();
        }

        // bStats metrics (anonymous; toggle via general.bstats).
        if (configManager.isBstatsEnabled()) {
            try {
                final org.bstats.bukkit.Metrics metrics = new org.bstats.bukkit.Metrics(this, BSTATS_PLUGIN_ID);
                metrics.addCustomChart(new org.bstats.charts.SingleLineChart(
                        "active_games", () -> gameManager.activeGames()));
                metrics.addCustomChart(new org.bstats.charts.SingleLineChart(
                        "players_in_games", () -> gameManager.totalPlayersInGames()));
                metrics.addCustomChart(new org.bstats.charts.SimplePie(
                        "configured_arenas", () -> String.valueOf(arenaRepository.getArenas().size())));
            } catch (Throwable t) {
                getLogger().warning("bStats init failed: " + t.getMessage());
            }
        }

        // Periodically persist stats (matches the configured sync interval).
        final long syncTicks = configManager.getStatsSyncInterval() * 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (statsManager != null) statsManager.save();
        }, syncTicks, syncTicks);

        // Autonomous arena rotation (opt-in via auto-arena.enabled)
        this.autoArena = new AutoArenaController(this);
        this.autoArena.start();

        // ── 7. Done ─────────────────────────────────────────
        final long elapsed = System.currentTimeMillis() - start;
        getLogger().info("LavaRise v" + getPluginMeta().getVersion() + " enabled in " + elapsed + "ms");
        getLogger().info("Loaded " + arenaRepository.getArenas().size() + " arena(s).");
    }

    @Override
    public void onDisable() {
        if (autoArena != null) {
            autoArena.stop();
        }

        // Gracefully end all running games
        if (gameManager != null) {
            gameManager.shutdownAll();
        }

        // Save arena data
        if (arenaRepository != null) {
            arenaRepository.saveAll();
        }

        // Flush player statistics and release backend resources
        if (statsManager != null) {
            statsManager.close();
        }

        // Persist earned achievements
        if (achievementManager != null) {
            achievementManager.save();
        }

        // Persist level XP
        if (levelManager != null) {
            levelManager.save();
        }

        // Persist cosmetic selections
        if (cosmeticManager != null) {
            cosmeticManager.save();
        }

        getLogger().info("LavaRise disabled. All games ended gracefully.");
        instance = null;
    }

    // ── Accessors ───────────────────────────────────────────

    public static LavaRisePlugin getInstance() {
        return instance;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public dev.lavarise.party.PartyManager getPartyManager() {
        return partyManager;
    }

    public dev.lavarise.party.QueueManager getQueueManager() {
        return queueManager;
    }

    public ArenaRepository getArenaRepository() {
        return arenaRepository;
    }

    public ArenaSelectorGUI getGuiManager() {
        return guiManager;
    }

    public ScoreboardModule getScoreboardModule() {
        return scoreboardModule;
    }

    public BossBarModule getBossBarModule() {
        return bossBarModule;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public dev.lavarise.feature.AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public dev.lavarise.feature.LevelManager getLevelManager() {
        return levelManager;
    }

    public dev.lavarise.feature.CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }

    public dev.lavarise.feature.PowerUpModule getPowerUpModule() {
        return powerUpModule;
    }

    public dev.lavarise.feature.DoubleJumpModule getDoubleJumpModule() {
        return doubleJumpModule;
    }

    public dev.lavarise.feature.DeathCrateModule getDeathCrateModule() {
        return deathCrateModule;
    }

    public dev.lavarise.feature.ChaosEventModule getChaosEventModule() {
        return chaosEventModule;
    }

    public dev.lavarise.hook.VaultHook getVaultHook() {
        return vaultHook;
    }

    public dev.lavarise.hook.DiscordHook getDiscordHook() {
        return discordHook;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public dev.lavarise.feature.CustomKitManager getCustomKitManager() {
        return customKitManager;
    }

    public KitSelectorGUI getKitSelectorGUI() {
        return kitSelectorGUI;
    }

    public VoteGUI getVoteGUI() {
        return voteGUI;
    }

    public dev.lavarise.feature.gui.AdminGUI getAdminGUI() {
        return adminGUI;
    }

    public SpectatorMenu getSpectatorMenu() {
        return spectatorMenu;
    }

    /**
     * Reloads the entire plugin configuration and arenas.
     */
    public void reload() {
        reloadConfig();
        configManager.loadAll();
        arenaRepository.loadArenas();
        if (achievementManager != null) achievementManager.load();
        if (levelManager != null) levelManager.load();
        if (cosmeticManager != null) cosmeticManager.load();
        if (powerUpModule != null) powerUpModule.load();
        if (chaosEventModule != null) chaosEventModule.load();
        if (doubleJumpModule != null) doubleJumpModule.load();
        if (deathCrateModule != null) deathCrateModule.load();
        if (kitManager != null) kitManager.load();
        if (discordHook != null) discordHook.load();
        if (autoArena != null) autoArena.onReload();
        getLogger().info("LavaRise configuration reloaded.");
    }

    /**
     * Log a debug message (only if debug mode is enabled in config).
     */
    public void debug(String message) {
        if (configManager.isDebug()) {
            getLogger().log(Level.INFO, "[DEBUG] " + message);
        }
    }
}
