package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.data.ConfigManager;
import dev.lavarise.engine.ChunkPreloader;
import dev.lavarise.engine.LavaEngine;
import dev.lavarise.feature.ParticleModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.UUID;
import org.bukkit.block.data.BlockData;

/**
 * Active game state — lava is rising!
 * <p>
 * The {@link LavaEngine} is ticked here. Beyond raising lava, this state drives
 * the full in-game experience: a configurable grace period, lava acceleration,
 * dynamic/sudden-death speed, a shrinking world border, periodic block hand-outs,
 * the starting kit, the boss bar / action bar HUD, particles, sounds and
 * proximity warnings. Elimination itself is driven by real lava damage
 * (see {@code ArenaEventRouter} / {@code PlayerListener}).
 * </p>
 */
public final class ActiveState implements GameState {

    private final LavaRisePlugin plugin;
    private final Arena arena;
    private final ArenaSession session;
    private final ConfigManager cfg;

    private LavaEngine lavaEngine;
    private BukkitRunnable gameLoop;
    private int tickCounter = 0;
    private int graceTicksLeft = 0;
    private int currentInterval;

    // World border state to restore on exit.
    private boolean borderModified = false;
    private double originalBorderSize;
    private Location originalBorderCenter;

    public ActiveState(LavaRisePlugin plugin, Arena arena, ArenaSession session) {
        this.plugin = plugin;
        this.arena = arena;
        this.session = session;
        this.cfg = plugin.getConfigManager();
    }

    @Override
    public void onEnter() {
        plugin.debug("Arena " + arena.getName() + " entered ACTIVE state.");
        session.setStartTime(System.currentTimeMillis());
        plugin.getServer().getPluginManager().callEvent(new dev.lavarise.api.events.ArenaStartEvent(arena));

        if (cfg.isPreloadChunks()) {
            ChunkPreloader.preloadArenaChunks(arena.getConfig());
        }
        if (session.getModeHandler().shouldSnapshot()) {
            takeSnapshot();
        }

        this.currentInterval = Math.max(1, arena.getConfig().lavaRiseInterval());
        this.graceTicksLeft = Math.max(0, cfg.getGracePeriod()) * 20;
        this.lavaEngine = new LavaEngine(plugin, arena.getConfig(), session, cfg.getMaxBlocksPerTick());

        setupWorldBorder();

        for (UUID uuid : session.getAlivePlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            plugin.getStatsManager().recordGamePlayed(uuid, p.getName());

            if (arena.getConfig().gameSpawn() != null) {
                p.teleport(arena.getConfig().gameSpawn());
            }
            p.setFireTicks(0);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            giveKit(p);

            p.showTitle(Title.title(
                    plugin.getMiniMessage().deserialize("<gradient:red:gold><bold>LAVA RISING!</bold></gradient>"),
                    plugin.getMiniMessage().deserialize(graceTicksLeft > 0
                            ? "<gray>Grace period: <yellow>" + cfg.getGracePeriod() + "s</yellow> — build up!"
                            : "<gray>Survive as long as you can!"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            playSound(p, cfg.getSoundGameStart(), 0.7f, 1.5f);
        }

        session.getModeHandler().onGameStart(arena, session);

        plugin.getScoreboardModule().updateScoreboard(session);
        plugin.getBossBarModule().updateFor(session);

        gameLoop = new BukkitRunnable() {
            @Override
            public void run() { tick(); }
        };
        gameLoop.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onExit() {
        if (gameLoop != null) { gameLoop.cancel(); gameLoop = null; }
        if (lavaEngine != null) lavaEngine.shutdown();
        restoreWorldBorder();
    }

    @Override
    public void onPlayerJoin(Player player) {
        player.sendMessage(plugin.getMiniMessage().deserialize("<red>Game is already in progress!"));
    }

    @Override
    public void onPlayerLeave(Player player) {
        plugin.getBossBarModule().removeFor(player);
        plugin.getScoreboardModule().cleanup(player);
        plugin.getScoreboardModule().updateScoreboard(session);
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>" + player.getName() + " <gray>left! <dark_gray>(" + session.getAliveCount() + " alive)"));
        checkWinCondition();
    }

    @Override
    public void onPlayerEliminated(Player player) {
        plugin.getServer().getPluginManager().callEvent(new dev.lavarise.api.events.PlayerEliminatedEvent(arena, player));
        session.getModeHandler().onPlayerEliminated(arena, session, player);

        plugin.getStatsManager().recordDeath(player.getUniqueId(), player.getName());
        plugin.getStatsManager().recordSurvivalTime(player.getUniqueId(), player.getName(), session.getElapsedSeconds());
        plugin.getBossBarModule().removeFor(player);

        player.showTitle(Title.title(
                plugin.getMiniMessage().deserialize("<red><bold>ELIMINATED!</bold></red>"),
                plugin.getMiniMessage().deserialize("<gray>You were consumed by lava!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        playSound(player, "entity.blaze.death", 1.0f, 0.5f);

        plugin.getScoreboardModule().updateScoreboard(session);
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>☠ " + player.getName() + " <gray>eliminated! <dark_gray>(" + session.getAliveCount() + " alive)"));
        checkWinCondition();
    }

    @Override public boolean isJoinable() { return false; }
    @Override public boolean isGameRunning() { return true; }
    @Override public String getDisplayName() { return "Active (Lava: " + session.getCurrentLavaY() + ")"; }

    // ── Main tick ───────────────────────────────────────────

    private void tick() {
        // Admin-event pause freezes everything; startTime is offset on resume
        // so survival/acceleration timers stay continuous.
        if (session.isPaused()) return;

        tickCounter++;

        // Grace period — lava is frozen, players build up.
        if (graceTicksLeft > 0) {
            graceTicksLeft--;
            if (graceTicksLeft % 20 == 0) {
                int secs = graceTicksLeft / 20;
                if (secs > 0) {
                    broadcastActionBar(plugin.getMiniMessage().deserialize(
                            "<yellow>⏳ Grace period: <bold>" + secs + "s</bold> <gray>— build up!"));
                } else {
                    broadcastToArena(plugin.getMiniMessage().deserialize(
                            "<red><bold>🔥 The lava is now rising!</bold>"));
                }
            }
            return;
        }

        currentInterval = computeInterval();

        if (tickCounter % currentInterval == 0) {
            lavaEngine.riseLava();
            int lavaY = session.getCurrentLavaY();

            plugin.getScoreboardModule().updateScoreboard(session);
            plugin.getBossBarModule().updateFor(session);
            shrinkWorldBorder();

            broadcastActionBar(plugin.getMiniMessage().deserialize(
                    "<red>🔥 Lava height: <bold>" + session.getLavaHeight() + "</bold> <dark_gray>| <gray>"
                            + session.getLavaPercent() + "% to max"));
            for (UUID uuid : session.getAlivePlayers()) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) playSound(p, cfg.getSoundLavaRise(), 0.5f, 0.8f);
            }
        }

        lavaEngine.processBatch();

        // Cosmetic updates at configurable cadences (raise the intervals to
        // protect TPS on high-population servers).
        if (tickCounter % cfg.getHudIntervalTicks() == 0) {
            updatePerPlayerHud();
        }
        if (cfg.isParticlesEnabled() && tickCounter % cfg.getParticleIntervalTicks() == 0) {
            spawnParticles();
        }

        // Periodic block hand-out.
        if (cfg.isBlockGiveEnabled() && tickCounter % Math.max(20, cfg.getBlockGiveIntervalSeconds() * 20) == 0) {
            giveBlocks();
        }

        if (session.isLavaAtMax()) endGame(null);
    }

    /**
     * Computes the effective ticks-between-rises, factoring in acceleration over
     * time, dynamic speed by remaining players, and sudden death.
     */
    private int computeInterval() {
        int interval = arena.getConfig().lavaRiseInterval();

        if (cfg.isAccelerationEnabled()) {
            long elapsed = session.getElapsedSeconds();
            int steps = (int) (elapsed / Math.max(1, cfg.getAccelerationEverySeconds()));
            interval -= steps * cfg.getAccelerationReduceBy();
            interval = Math.max(cfg.getAccelerationMinInterval(), interval);
        }

        if (cfg.isDynamicSpeedEnabled()) {
            int max = Math.max(1, arena.getConfig().maxPlayers());
            double fraction = Math.min(1.0, (double) session.getAliveCount() / max);
            // Fewer players alive → shorter interval (down to 50% of base).
            interval = (int) Math.round(interval * (0.5 + 0.5 * fraction));
        }

        if (cfg.isSuddenDeathEnabled() && session.getAliveCount() <= cfg.getSuddenDeathPlayers()) {
            interval = Math.min(interval, cfg.getSuddenDeathInterval());
        }

        return Math.max(1, interval);
    }

    // ── Win / end ───────────────────────────────────────────

    private void checkWinCondition() {
        if (session.getModeHandler().isGameOver(arena, session)) {
            endGame(session.getModeHandler().resolveWinner(arena, session));
        }
    }

    private void endGame(Player winner) {
        session.transitionTo(new EndingState(plugin, arena, session, winner));
    }

    // ── Features ────────────────────────────────────────────

    private void giveKit(Player player) {
        if (!cfg.isKitEnabled()) return;
        for (String entry : cfg.getKitItems()) {
            ItemStack item = parseItem(entry);
            if (item != null) player.getInventory().addItem(item);
        }
    }

    private void giveBlocks() {
        ItemStack give = parseItem(cfg.getBlockGiveMaterial() + ":" + cfg.getBlockGiveAmount());
        if (give == null) return;
        final int maxStack = cfg.getBlockGiveMaxStack();
        for (UUID uuid : session.getAlivePlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (countMaterial(p, give.getType()) >= maxStack) continue;
            p.getInventory().addItem(give.clone());
        }
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private ItemStack parseItem(String entry) {
        if (entry == null || entry.isBlank()) return null;
        String[] parts = entry.split(":");
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null) {
            plugin.getLogger().warning("Unknown material in config: " + parts[0]);
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try { amount = Math.max(1, Integer.parseInt(parts[1].trim())); }
            catch (NumberFormatException ignored) {}
        }
        return new ItemStack(material, amount);
    }

    /**
     * Single per-player pass for the action-bar HUD and the lava proximity
     * warning. The HUD component is session-global, so it is parsed once and
     * reused; the warning template is only parsed for players actually in range.
     */
    private void updatePerPlayerHud() {
        final int lavaY = session.getCurrentLavaY();
        final int warnDistance = cfg.getWarningDistance();
        final boolean hudEnabled = cfg.isActionBarEnabled();

        Component hud = null;
        if (hudEnabled) {
            String template = plugin.getConfigManager().getMessage("actionbar.in-game")
                    .replace("{lava_level}", String.valueOf(session.getLavaHeight()))
                    .replace("{lava_percent}", session.getLavaPercent() + "%")
                    .replace("{lava_y}", String.valueOf(lavaY))
                    .replace("{alive}", String.valueOf(session.getAliveCount()))
                    .replace("{time}", formatTime(session.getElapsedSeconds()));
            hud = plugin.getMiniMessage().deserialize(template);
        }
        final String warnTemplate = plugin.getConfigManager().getMessage("player.warning-lava-close");

        for (UUID uuid : session.getAlivePlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            if (warnDistance > 0) {
                int delta = p.getLocation().getBlockY() - lavaY;
                if (delta > 0 && delta <= warnDistance) {
                    p.sendActionBar(plugin.getMiniMessage().deserialize(
                            warnTemplate.replace("{blocks}", String.valueOf(delta))));
                    playSound(p, cfg.getSoundWarning(), 1.0f, 2.0f);
                    continue; // warning replaces the HUD for this player
                }
            }
            if (hud != null) p.sendActionBar(hud);
        }
    }

    private void spawnParticles() {
        if (!cfg.isParticlesEnabled()) return;
        Particle particle = parseParticle(cfg.getParticleType());
        World world = arena.getConfig().world();
        int count = Math.max(5, arena.getConfig().area() / 50 * Math.max(1, cfg.getParticleDensity()));
        count = Math.min(count, 60);
        ParticleModule.spawnSurfaceParticles(world,
                arena.getConfig().minX(), arena.getConfig().maxX(),
                arena.getConfig().minZ(), arena.getConfig().maxZ(),
                session.getCurrentLavaY(), particle, count);
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Particle.LAVA;
        }
    }

    // ── World border ────────────────────────────────────────

    private void setupWorldBorder() {
        if (!cfg.isWorldBorderEnabled()) return;
        World world = arena.getConfig().world();
        WorldBorder border = world.getWorldBorder();
        originalBorderSize = border.getSize();
        originalBorderCenter = border.getCenter();
        borderModified = true;

        double centerX = (arena.getConfig().minX() + arena.getConfig().maxX()) / 2.0;
        double centerZ = (arena.getConfig().minZ() + arena.getConfig().maxZ()) / 2.0;
        double initial = Math.max(arena.getConfig().width(), arena.getConfig().depth());
        border.setCenter(centerX, centerZ);
        border.setSize(initial);
    }

    private void shrinkWorldBorder() {
        if (!borderModified) return;
        World world = arena.getConfig().world();
        int start = arena.getConfig().lavaStartY();
        int max = arena.getConfig().lavaMaxY();
        double progress = Math.min(1.0, Math.max(0.0,
                (double) (session.getCurrentLavaY() - start) / Math.max(1, max - start)));
        double initial = Math.max(arena.getConfig().width(), arena.getConfig().depth());
        double target = initial - (initial - cfg.getWorldBorderMinSize()) * progress;
        // Interpolate smoothly over the next rise interval.
        world.getWorldBorder().setSize(Math.max(cfg.getWorldBorderMinSize(), target),
                java.util.concurrent.TimeUnit.SECONDS, Math.max(1L, currentInterval / 20L));
    }

    private void restoreWorldBorder() {
        if (!borderModified) return;
        WorldBorder border = arena.getConfig().world().getWorldBorder();
        if (originalBorderCenter != null) border.setCenter(originalBorderCenter);
        border.setSize(originalBorderSize);
        borderModified = false;
    }

    // ── Helpers ─────────────────────────────────────────────

    private void playSound(Player player, String sound, float volume, float pitch) {
        if (!cfg.isSoundsEnabled() || sound == null || sound.isBlank()) return;
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {
            // Invalid sound key in config — skip silently.
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    private void broadcastToArena(Component msg) {
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
    }

    private void broadcastActionBar(Component msg) {
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendActionBar(msg);
        }
    }

    private void takeSnapshot() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int minX = arena.getConfig().minX();
            int maxX = arena.getConfig().maxX();
            int minZ = arena.getConfig().minZ();
            int maxZ = arena.getConfig().maxZ();
            int startY = arena.getConfig().lavaStartY();
            int maxY = arena.getConfig().lavaMaxY();

            // Growable primitive index buffer (no Integer boxing) paired with BlockData refs.
            int[] indices = new int[1024];
            BlockData[] blocks = new BlockData[1024];
            int n = 0;

            int index = 0;
            World world = arena.getConfig().world();

            try {
                for (int y = startY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            BlockData data = world.getBlockData(x, y, z);
                            if (!data.getMaterial().isAir()) {
                                if (n == indices.length) {
                                    indices = java.util.Arrays.copyOf(indices, n * 2);
                                    blocks = java.util.Arrays.copyOf(blocks, n * 2);
                                }
                                indices[n] = index;
                                blocks[n] = data;
                                n++;
                            }
                            index++;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Snapshot failed for arena " + arena.getName() + " — reset will fall back to clearing lava.", e);
                return;
            }

            session.setSnapshot(java.util.Arrays.copyOf(indices, n), java.util.Arrays.copyOf(blocks, n));
            plugin.debug("Arena " + arena.getName() + " snapshot taken async! Size: "
                    + n + " non-air blocks out of " + index + " total.");
        });
    }
}
