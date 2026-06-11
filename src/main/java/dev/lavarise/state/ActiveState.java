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
    private int graceTotalSeconds = 0;
    private int currentInterval;
    private int engineInterval = 1;
    /** Last lava height pushed to the HUD; drives flicker-free, in-sync refreshes. */
    private int lastDisplayedLavaY = Integer.MIN_VALUE;
    /** True once the final-showdown climax has fired (only once per game). */
    private boolean showdownTriggered = false;
    /** The kit that won the lobby vote, resolved once at game start (ties are random). */
    private String resolvedVotedKit;

    // World border state to restore on exit.
    private boolean borderModified = false;
    private double originalBorderSize;
    private Location originalBorderCenter;

    // Per-arena containment border (per-player) + void floor.
    private double containCenterX;
    private double containCenterZ;
    private double containSize;
    private int floorY;

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
        // Grace = seconds before the lava rises. KteRising-style: the voted kit can
        // override it (fair kits get a long build, OP kits start fast).
        int graceSeconds = cfg.getGracePeriod();
        // Resolve the winning kit ONCE — getVotedKit() breaks ties randomly, so
        // calling it per-player would hand different players different kits.
        this.resolvedVotedKit = session.getVotedKit();
        if (resolvedVotedKit != null && plugin.getKitManager() != null) {
            final var kit = plugin.getKitManager().get(resolvedVotedKit);
            if (kit != null && kit.countdown() >= 0) graceSeconds = kit.countdown();
        }
        this.graceTicksLeft = Math.max(0, graceSeconds) * 20;
        this.graceTotalSeconds = Math.max(0, graceSeconds);
        // Lock PvP for the grace window so players can gear up untouched; it opens
        // (with an announcement) the moment the lava starts rising. Setting
        // gameplay.pvp-during-grace: true skips the lock.
        session.setPvpUnlocked(graceTicksLeft <= 0 || cfg.isPvpDuringGrace());
        // Run the fill engine every N ticks with N× the budget — same blocks/sec,
        // but fewer scheduler passes and chunk packets.
        this.engineInterval = cfg.getEngineIntervalTicks();
        this.lavaEngine = new LavaEngine(plugin, arena.getConfig(), session,
                cfg.getMaxBlocksPerTick() * engineInterval);

        setupWorldBorder();

        // Per-arena containment geometry: a square per-player border covering the
        // whole arena footprint, and the void floor (the lowest corner, minus the
        // configured buffer).
        final var bounds = arena.getConfig();
        this.containCenterX = (bounds.minX() + bounds.maxX()) / 2.0 + 0.5;
        this.containCenterZ = (bounds.minZ() + bounds.maxZ()) / 2.0 + 0.5;
        this.containSize = Math.max(bounds.width(), bounds.depth());
        this.floorY = Math.min(bounds.corner1().getBlockY(), bounds.corner2().getBlockY());

        for (UUID uuid : session.getAlivePlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            plugin.getStatsManager().recordGamePlayed(uuid, p.getName());
            plugin.getLevelManager().addXp(p, plugin.getLevelManager().xpPerGame(), "game");
            plugin.getAchievementManager().checkAndAward(p);
            p.closeInventory(); // dismiss any open lobby vote GUI

            if (arena.getConfig().gameSpawn() != null) {
                p.teleport(arena.getConfig().gameSpawn());
            }
            applyArenaBorder(p);
            // Per-player clear daytime sky for visibility (doesn't touch the world).
            if (cfg.isClearVisibility()) {
                p.setPlayerTime(6000L, false);
                p.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
            }
            p.setFireTicks(0);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            if (plugin.getDoubleJumpModule().isEnabled()) {
                p.setAllowFlight(true);
            }
            // Clear the lobby loadout (kit-vote compass etc.) before handing out
            // the real kit so players start the match with exactly their kit.
            p.getInventory().clear();
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

        // Seed the HUD baseline so the first in-tick refresh only fires once the
        // lava actually rises (no spurious "height 0" pulse when the game starts).
        this.lastDisplayedLavaY = session.getCurrentLavaY();
        plugin.getScoreboardModule().updateScoreboard(session);
        // Show the grace bar straight away if there's a grace window; otherwise the
        // lava height bar.
        if (graceTotalSeconds > 0) {
            plugin.getBossBarModule().showGrace(session, graceTotalSeconds, graceTotalSeconds);
        } else {
            plugin.getBossBarModule().updateFor(session);
        }

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
        cleanupSessionEffects();
    }

    /**
     * Remove supply-drop chests and clear per-player glow/time/weather. Runs on
     * every exit path — the normal end AND {@code forceEnd()} (admin stop / plugin
     * disable) — so none of these effects leak past the match.
     */
    private void cleanupSessionEffects() {
        plugin.getPowerUpModule().cleanup(session); // remove outstanding glowing pickups
        for (Location drop : session.getSupplyDrops()) {
            try {
                if (drop.getWorld() != null) drop.getBlock().setType(Material.AIR, false);
            } catch (Throwable ignored) {
                // Chunk unloaded / already cleared — fine.
            }
        }
        for (UUID uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            try {
                p.setGlowing(false);
                p.resetPlayerTime();
                p.resetPlayerWeather();
                plugin.getDoubleJumpModule().reset(p);
            } catch (Throwable ignored) {
                // Best-effort per-player cleanup.
            }
        }
    }

    @Override
    public void onPlayerJoin(Player player) {
        player.sendMessage(plugin.getMiniMessage().deserialize("<red>Game is already in progress!"));
    }

    @Override
    public void onPlayerLeave(Player player) {
        clearArenaBorder(player);
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
        plugin.getAchievementManager().checkAndAward(player);
        plugin.getCosmeticManager().playDeathCry(player, session.getAllPlayerIds());
        plugin.getLevelManager().addXp(player, Math.min(plugin.getLevelManager().survivalXpCap(), session.getElapsedSeconds() * plugin.getLevelManager().xpPerSurvivalSecond()), "survival");
        plugin.getBossBarModule().removeFor(player);

        // Hypixel-style elimination summary: placement, kills and survival time.
        final int placement = session.getAliveCount() + 1;
        final int kills = session.getSessionKills(player.getUniqueId());
        final String survived = formatTime(session.getElapsedSeconds());

        player.showTitle(Title.title(
                plugin.getMiniMessage().deserialize("<red><bold>ELIMINATED!</bold></red>"),
                plugin.getMiniMessage().deserialize("<gray>You placed <yellow>#" + placement + "</yellow>!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        player.sendMessage(plugin.getMiniMessage().deserialize("<dark_gray><strikethrough>                              </strikethrough>"));
        player.sendMessage(plugin.getMiniMessage().deserialize(
                "  <red><bold>ELIMINATED</bold> <dark_gray>— <gray>you placed <yellow>#" + placement));
        player.sendMessage(plugin.getMiniMessage().deserialize(
                "  <gray>Kills <yellow>" + kills + " <dark_gray>·<gray> Survived <yellow>" + survived));
        player.sendMessage(plugin.getMiniMessage().deserialize("<dark_gray><strikethrough>                              </strikethrough>"));
        playSound(player, "entity.blaze.death", 1.0f, 0.5f);

        // Death burst — a configurable particle puff where they were eliminated.
        if (cfg.isDeathEffectEnabled() && player.isOnline()) {
            try {
                player.getWorld().spawnParticle(parseParticle(cfg.getDeathParticle()),
                        player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
            } catch (Exception ignored) {
                // Invalid particle name — skip.
            }
        }

        plugin.getScoreboardModule().updateScoreboard(session);
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>☠ " + player.getName() + " <gray>eliminated! <dark_gray>(" + session.getAliveCount() + " alive)"));
        maybeTriggerShowdown();
        checkWinCondition();
    }

    /**
     * Fire the final-showdown climax once, when the field narrows to the configured
     * count (default 2): announce it, make the survivors glow so they can find each
     * other for the finish, and play a dramatic cue.
     */
    private void maybeTriggerShowdown() {
        if (showdownTriggered || !cfg.isFinalShowdownEnabled()) return;
        final int alive = session.getAliveCount();
        if (alive < 2 || alive > cfg.getFinalShowdownPlayers()) return;
        showdownTriggered = true;

        final java.util.List<String> names = new java.util.ArrayList<>();
        for (UUID uuid : session.getAlivePlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            p.setGlowing(true);
            playSound(p, "entity.ender_dragon.growl", 1.0f, 0.8f);
            names.add(p.getName());
        }
        broadcastToArena(plugin.getMiniMessage().deserialize("<red><bold>⚔ FINAL SHOWDOWN! ⚔</bold>"));
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<gray>Last standing: <yellow>" + String.join(" <gray>vs <yellow>", names)));
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
                    plugin.getBossBarModule().showGrace(session, secs, graceTotalSeconds);
                } else {
                    broadcastToArena(plugin.getMiniMessage().deserialize(
                            "<red><bold>🔥 The lava is now rising!</bold>"));
                    // Hand the boss bar back to the lava height display.
                    plugin.getBossBarModule().updateFor(session);
                    // Grace over → open PvP (only announce if it was actually gated).
                    if (!session.isPvpUnlocked()) {
                        session.setPvpUnlocked(true);
                        broadcastToArena(plugin.getMiniMessage().deserialize(
                                "<red><bold>⚔ PvP is now enabled — fight!</bold>"));
                        for (UUID uuid : session.getAlivePlayers()) {
                            Player p = plugin.getServer().getPlayer(uuid);
                            if (p != null) playSound(p, "entity.ender_dragon.growl", 0.6f, 1.4f);
                        }
                    }
                }
            }
            return;
        }

        currentInterval = computeInterval();

        // Commit the next rise on schedule — this advances the engine's target
        // only; session.currentLavaY does not move until the fill catches up.
        if (tickCounter % currentInterval == 0) {
            lavaEngine.riseLava();
        }

        // Fill lava + flush chunk packets on the engine cadence (every N ticks,
        // N× budget) — same blocks/sec with fewer passes and packets. This is the
        // step that actually advances session.currentLavaY.
        if (tickCounter % engineInterval == 0) {
            lavaEngine.processBatch();
        }

        // Refresh the lava HUD the instant the *real* height changes — after the
        // fill, never before it. Driving this off the actual height (rather than
        // the rise-schedule tick) keeps the scoreboard / boss bar in exact lockstep
        // with the blocks just placed, no matter how the rise and fill cadences
        // line up. Previously the HUD updated before processBatch ran, so it sat a
        // full level behind the lava players could see.
        final int lavaY = session.getCurrentLavaY();
        if (lavaY != lastDisplayedLavaY) {
            lastDisplayedLavaY = lavaY;
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

        // Cosmetic updates at configurable cadences (raise the intervals to
        // protect TPS on high-population servers).
        if (tickCounter % cfg.getHudIntervalTicks() == 0) {
            updatePerPlayerHud();
            // Refresh the sidebar on the HUD cadence so the {time} clock ticks
            // smoothly between lava rises (cheap on the shared-render fast path).
            plugin.getScoreboardModule().updateScoreboard(session);
        }
        if (cfg.isParticlesEnabled() && tickCounter % cfg.getParticleIntervalTicks() == 0) {
            spawnParticles();
        }

        // Periodic block hand-out.
        if (cfg.isBlockGiveEnabled() && tickCounter % Math.max(20, cfg.getBlockGiveIntervalSeconds() * 20) == 0) {
            giveBlocks();
        }

        // Supply drops (battle-royale loot) on a fixed cadence after the first delay.
        if (cfg.isSupplyDropsEnabled()) {
            final int firstTicks = cfg.getSupplyDropFirstDelay() * 20;
            final int intervalTicks = Math.max(20, cfg.getSupplyDropInterval() * 20);
            if (tickCounter >= firstTicks && (tickCounter - firstTicks) % intervalTicks == 0) {
                dropSupply();
            }
        }

        // Power-up waves on a fixed cadence (skip tick 0 so they don't all land at once).
        final var pum = plugin.getPowerUpModule();
        if (pum.isEnabled()) {
            final int interval = Math.max(20, pum.getIntervalSeconds() * 20);
            if (tickCounter > 0 && tickCounter % interval == 0) {
                pum.spawnWave(arena, session);
            }
        }

        if (session.isLavaAtMax()) endGame(null);
    }

    /**
     * Drop a single supply chest at a random reachable spot above the lava, fill
     * it with the configured loot and announce it. Isolated in a try/catch so a
     * placement failure can never break the game loop; the chest is tracked on the
     * session and removed when the game ends.
     */
    private void dropSupply() {
        final java.util.List<String> loot = cfg.getSupplyDropItems();
        if (loot.isEmpty()) return;
        try {
            final var c = arena.getConfig();
            final World world = c.world();
            final int x = java.util.concurrent.ThreadLocalRandom.current().nextInt(c.minX(), c.maxX() + 1);
            final int z = java.util.concurrent.ThreadLocalRandom.current().nextInt(c.minZ(), c.maxZ() + 1);
            final int y = Math.min(c.lavaMaxY() - 1, session.getCurrentLavaY() + 2);
            final Location loc = new Location(world, x, y, z);
            final org.bukkit.block.Block block = world.getBlockAt(loc);
            block.setType(Material.CHEST, false);
            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                for (String entry : loot) {
                    ItemStack item = parseItem(entry);
                    if (item != null) chest.getInventory().addItem(item);
                }
                chest.update();
            }
            session.addSupplyDrop(loc);
            broadcastToArena(plugin.getMiniMessage().deserialize(
                    "<aqua>📦 <white>A supply drop landed at <yellow>" + x + ", " + y + ", " + z + "</yellow>!"));
            for (UUID uuid : session.getAlivePlayers()) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) playSound(p, "entity.firework_rocket.large_blast", 0.8f, 1.0f);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Supply drop failed: " + t.getMessage());
        }
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
        // A custom-kit arena forces one kit on everyone, overriding vote/selection.
        if (arena.getCustomKit() != null && plugin.getKitManager() != null
                && plugin.getKitManager().get(arena.getCustomKit()) != null) {
            plugin.getKitManager().applyKit(player, arena.getCustomKit());
            return;
        }
        // A player's custom kit won the lobby vote → everyone gets it, UNLESS the
        // owner cleared it after voting (empty) — then fall through to a real kit
        // so the lobby never spawns with nothing.
        final String voted = resolvedVotedKit;
        if (voted != null && voted.startsWith("custom:") && giveCustomVotedKit(player, voted)) {
            return;
        }
        // Prefer the multi-kit system. A non-custom voted kit applies to everyone;
        // otherwise (no vote, or an empty custom winner) each player gets their own
        // selected loadout.
        if (plugin.getKitManager() != null && plugin.getKitManager().hasKits()) {
            if (voted != null && !voted.startsWith("custom:")) {
                plugin.getKitManager().applyKit(player, voted);
            } else {
                plugin.getKitManager().applyKit(player);
            }
            return;
        }
        if (!cfg.isKitEnabled()) return;
        for (String entry : cfg.getKitItems()) {
            ItemStack item = parseItem(entry);
            if (item != null) player.getInventory().addItem(item);
        }
    }

    /**
     * Give the player the items of the custom kit (keyed {@code custom:<uuid>}) that
     * won the vote. Returns {@code false} if the kit is gone/empty so the caller can
     * fall back to a normal kit.
     */
    private boolean giveCustomVotedKit(Player player, String key) {
        try {
            final UUID owner = UUID.fromString(key.substring("custom:".length()));
            final java.util.List<ItemStack> items = plugin.getCustomKitManager().getKit(owner);
            if (items.isEmpty()) return false;
            for (ItemStack item : items) {
                player.getInventory().addItem(item);
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            return false; // malformed key
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

        // Vertical containment: anyone who drops below the arena floor (dug out or
        // knocked into the void) is eliminated so they can't hide under the map.
        final boolean voidGuard = cfg.isVoidEliminationEnabled();
        final double voidY = floorY - cfg.getVoidBuffer();

        for (UUID uuid : session.getAlivePlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            if (voidGuard && p.getLocation().getY() < voidY) {
                session.eliminatePlayer(p);
                // Distinct from the lava-death title so players know why (shown
                // last, overriding the generic elimination title from the same tick).
                p.showTitle(net.kyori.adventure.title.Title.title(
                        plugin.getMiniMessage().deserialize("<red><bold>OUT OF BOUNDS!</bold>"),
                        plugin.getMiniMessage().deserialize("<gray>You fell out of the arena!"),
                        net.kyori.adventure.title.Title.Times.times(
                                Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                continue;
            }

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

        // Keep eliminated spectators informed of the live game (one shared
        // component — spectators see the lava/alive/time, not a warning).
        if (hudEnabled) {
            final Component specHud = plugin.getMiniMessage().deserialize(
                    "<gray>👁 Spectating <dark_gray>| <red>🔥 " + session.getLavaHeight() + "m "
                            + "<dark_gray>(" + session.getLavaPercent() + "%) <dark_gray>| <green>"
                            + session.getAliveCount() + " alive <dark_gray>| <white>"
                            + formatTime(session.getElapsedSeconds()));
            for (UUID uuid : session.getSpectators()) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null && p.isOnline()) p.sendActionBar(specHud);
            }
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
        double rawProgress = Math.min(1.0, Math.max(0.0,
                (double) (session.getCurrentLavaY() - start) / Math.max(1, max - start)));
        // Hold the border at full size until the lava passes the shrink-start
        // threshold, then ramp from there to the minimum at the top.
        final double startAt = cfg.getWorldBorderShrinkStartPercent() / 100.0;
        final double progress = rawProgress <= startAt ? 0.0
                : (rawProgress - startAt) / Math.max(1e-6, 1.0 - startAt);
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

    // ── Per-arena containment ───────────────────────────────

    /**
     * Give the player a per-player world border matching the arena footprint, so
     * they physically cannot walk out of the arena. Per-player borders are
     * independent, so every arena in the same world is contained separately.
     */
    private void applyArenaBorder(Player p) {
        if (!cfg.isArenaBorderEnabled()) return;
        final WorldBorder wb = plugin.getServer().createWorldBorder();
        wb.setCenter(containCenterX, containCenterZ);
        wb.setSize(containSize);
        wb.setWarningDistance(2);
        wb.setDamageAmount(0.0); // the border just blocks movement; no edge damage
        wb.setDamageBuffer(0.0);
        p.setWorldBorder(wb);
    }

    /** Restore the player's view of the world's own border. */
    private void clearArenaBorder(Player p) {
        if (!cfg.isArenaBorderEnabled()) return;
        p.setWorldBorder(null);
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
        final int minX = arena.getConfig().minX();
        final int maxX = arena.getConfig().maxX();
        final int minZ = arena.getConfig().minZ();
        final int maxZ = arena.getConfig().maxZ();
        final int startY = arena.getConfig().lavaStartY();
        final int maxY = arena.getConfig().lavaMaxY();
        final World world = arena.getConfig().world();

        // Capture chunk snapshots on the MAIN thread — reading the live world off
        // the main thread throws "Asynchronous block access!" on Paper. A
        // ChunkSnapshot is a thread-safe copy that we can read from the async task.
        final java.util.Map<Long, org.bukkit.ChunkSnapshot> snapshots = new java.util.HashMap<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    snapshots.put(org.bukkit.Chunk.getChunkKey(cx, cz),
                            world.getChunkAt(cx, cz).getChunkSnapshot());
                }
            }
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Growable primitive index buffer (no Integer boxing) paired with BlockData refs.
            int[] indices = new int[1024];
            BlockData[] blocks = new BlockData[1024];
            int n = 0;
            int index = 0;

            try {
                for (int y = startY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            final org.bukkit.ChunkSnapshot cs =
                                    snapshots.get(org.bukkit.Chunk.getChunkKey(x >> 4, z >> 4));
                            if (cs != null) {
                                final BlockData data = cs.getBlockData(x & 15, y, z & 15);
                                if (!data.getMaterial().isAir()) {
                                    if (n == indices.length) {
                                        indices = java.util.Arrays.copyOf(indices, n * 2);
                                        blocks = java.util.Arrays.copyOf(blocks, n * 2);
                                    }
                                    indices[n] = index;
                                    blocks[n] = data;
                                    n++;
                                }
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
            plugin.debug("Arena " + arena.getName() + " snapshot taken (chunk-snapshot, async)! Size: "
                    + n + " non-air blocks out of " + index + " total.");
        });
    }
}
