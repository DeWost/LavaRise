package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.engine.LavaEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.data.BlockData;

public final class ActiveState implements GameState {

    private final LavaRisePlugin plugin;
    private final Arena arena;
    private final ArenaSession session;
    private LavaEngine lavaEngine;
    private BukkitRunnable gameLoop;
    private int tickCounter = 0;
    private int playerCheckInterval;

    public ActiveState(LavaRisePlugin plugin, Arena arena, ArenaSession session) {
        this.plugin = plugin;
        this.arena = arena;
        this.session = session;
    }

    @Override
    public void onEnter() {
        plugin.debug("Arena " + arena.getName() + " entered ACTIVE state.");
        session.setStartTime(System.currentTimeMillis());

        plugin.getServer().getPluginManager().callEvent(new dev.lavarise.api.events.ArenaStartEvent(arena));

        takeSnapshot();

        this.lavaEngine = new LavaEngine(plugin, arena.getConfig(), session,
                plugin.getConfigManager().getMaxBlocksPerTick());

        this.playerCheckInterval = plugin.getConfigManager().getPlayerCheckInterval();

        for (UUID uuid : session.getAlivePlayers()) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (arena.getConfig().gameSpawn() != null) {
                    p.teleport(arena.getConfig().gameSpawn());
                }
                p.showTitle(Title.title(
                        plugin.getMiniMessage().deserialize("<gradient:red:gold><bold>LAVA RISING!</bold></gradient>"),
                        plugin.getMiniMessage().deserialize("<gray>Survive as long as you can!"),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
                ));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.5f);
            }
        }

        gameLoop = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;

                if (tickCounter % arena.getConfig().lavaRiseInterval() == 0) {
                    lavaEngine.riseLava();
                    plugin.getScoreboardModule().updateScoreboard(session);
                    int lavaY = session.getCurrentLavaY();
                    broadcastActionBar(plugin.getMiniMessage().deserialize(
                            "<red>🔥 Lava: <bold>" + lavaY + "</bold> <dark_gray>| <gray>Max: "
                                    + arena.getConfig().lavaMaxY()));
                    for (UUID uuid : session.getAlivePlayers()) {
                        Player p = plugin.getServer().getPlayer(uuid);
                        if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_LAVA_POP, 0.5f, 0.8f);
                    }
                }

                lavaEngine.processBatch();

                // Main-thread player check — avoids async + anti-cheat fly false positives
                if (tickCounter % playerCheckInterval == 0) {
                    checkAndEliminatePlayers();
                }

                if (session.isLavaAtMax()) endGame(null);
            }
        };
        gameLoop.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void onExit() {
        if (gameLoop != null) { gameLoop.cancel(); gameLoop = null; }
        if (lavaEngine != null) lavaEngine.shutdown();
    }

    @Override
    public void onPlayerJoin(Player player) {
        player.sendMessage(plugin.getMiniMessage().deserialize("<red>Game is already in progress!"));
    }

    @Override
    public void onPlayerLeave(Player player) {
        plugin.getScoreboardModule().cleanup(player);
        plugin.getScoreboardModule().updateScoreboard(session);
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>" + player.getName() + " <gray>left! <dark_gray>(" + session.getAliveCount() + " alive)"));
        checkWinCondition();
    }

    @Override
    public void onPlayerEliminated(Player player) {
        plugin.getServer().getPluginManager().callEvent(new dev.lavarise.api.events.PlayerEliminatedEvent(arena, player));

        player.showTitle(Title.title(
                plugin.getMiniMessage().deserialize("<red><bold>ELIMINATED!</bold></red>"),
                plugin.getMiniMessage().deserialize("<gray>You were consumed by lava!"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1.0f, 0.5f);

        plugin.getScoreboardModule().updateScoreboard(session);

        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>☠ " + player.getName() + " <gray>eliminated! <dark_gray>(" + session.getAliveCount() + " alive)"));
        checkWinCondition();
    }

    @Override public boolean isJoinable() { return false; }
    @Override public String getDisplayName() { return "Active (Lava: " + session.getCurrentLavaY() + ")"; }

    /**
     * Runs on main thread every playerCheckInterval ticks.
     *
     * NMS-placed lava has no fluid physics, so vanilla lava damage never fires.
     * We must: deal damage manually, push the player downward to prevent the
     * client from sending swim-up packets (which Paper's movement check reads
     * as illegal flight), then eliminate once health is gone.
     */
    private void checkAndEliminatePlayers() {
        int lavaY = session.getCurrentLavaY();

        for (UUID uuid : new ArrayList<>(session.getAlivePlayers())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            int playerY = player.getLocation().getBlockY();
            if (playerY > lavaY) continue;

            // Suppress upward velocity so Paper's anti-cheat doesn't
            // interpret lava-swim packets as flight.
            Vector vel = player.getVelocity();
            if (vel.getY() > 0) {
                player.setVelocity(new Vector(vel.getX(), -0.4, vel.getZ()));
            }

            // Burn the player — NMS lava has no fire tick, so we set it manually.
            player.setFireTicks(Math.max(player.getFireTicks(), 80));

            // Deal damage; if dead, eliminatePlayer handles the rest.
            player.damage(4.0);

            if (player.getHealth() <= 0) {
                session.eliminatePlayer(player);
            }
        }
    }

    private void checkWinCondition() {
        if (session.getAliveCount() <= 1) {
            Player winner = null;
            if (session.getAliveCount() == 1) {
                UUID wid = session.getAlivePlayers().iterator().next();
                winner = plugin.getServer().getPlayer(wid);
            }
            endGame(winner);
        }
    }

    private void endGame(Player winner) {
        session.transitionTo(new EndingState(plugin, arena, session, winner));
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

            List<Integer> indicesList = new ArrayList<>();
            List<BlockData> blocksList = new ArrayList<>();

            int index = 0;
            org.bukkit.World world = arena.getConfig().world();

            for (int y = startY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        BlockData data = world.getBlockData(x, y, z);
                        if (!data.getMaterial().isAir()) {
                            indicesList.add(index);
                            blocksList.add(data);
                        }
                        index++;
                    }
                }
            }

            int[] indices = indicesList.stream().mapToInt(i -> i).toArray();
            BlockData[] blocks = blocksList.toArray(new BlockData[0]);

            session.setSnapshot(indices, blocks);
            plugin.debug("Snapshot taken: " + blocks.length + " non-air blocks.");
        });
    }
}
