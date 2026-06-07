package dev.lavarise.command;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.arena.ProceduralArenaFactory;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.data.StatsManager;
import dev.lavarise.engine.nms.FastBlockSetter;
import dev.lavarise.mode.SurvivalChallengeMode;
import dev.lavarise.state.ActiveState;
import dev.lavarise.state.CountdownState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main command executor for /lavarise.
 * <p>
 * Player commands: join, leave, list, stats, top. Admin commands: start, stop,
 * forcestart, reload, stress, and an arena setup wizard (create, pos1, pos2,
 * setlobby, setgamespawn, setspectator, save, delete).
 * </p>
 *
 * @author DeWost
 */
public class LavaRiseCommand implements CommandExecutor, TabCompleter {

    private final LavaRisePlugin plugin;

    /** Per-admin in-progress arena setup sessions. */
    private final Map<UUID, SetupSession> setups = new HashMap<>();

    /** World-wide survival challenge controller. */
    private final SurvivalChallengeMode survival;

    public LavaRiseCommand(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.survival = new SurvivalChallengeMode(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "join" -> handleJoin(sender, args);
            case "random" -> handleRandom(sender);
            case "kit" -> handleKit(sender);
            case "vote" -> handleVote(sender);
            case "leave" -> handleLeave(sender);
            case "list" -> handleList(sender);
            case "stats" -> handleStats(sender, args);
            case "top" -> handleTop(sender, args);
            case "info" -> handleInfo(sender, args);
            case "start", "forcestart" -> handleStart(sender, args);
            case "skip" -> handleSkip(sender, args);
            case "freeze" -> handleFreeze(sender, args);
            case "stop" -> handleStop(sender, args);
            case "reload" -> handleReload(sender);
            case "survival" -> handleSurvival(sender, args);
            case "event" -> handleEvent(sender, args);
            case "create" -> handleCreate(sender, args);
            case "setup" -> handleQuickSetup(sender, args);
            case "pos1" -> handleSetCorner(sender, 1);
            case "pos2" -> handleSetCorner(sender, 2);
            case "setlobby" -> handleSetSpawn(sender, "lobby");
            case "setgamespawn" -> handleSetSpawn(sender, "game");
            case "setspectator" -> handleSetSpawn(sender, "spectator");
            case "save" -> handleSave(sender);
            case "delete" -> handleDelete(sender, args);
            case "stress" -> handleStress(sender, args);
            default -> { sendHelp(sender); yield true; }
        };
    }

    // ── Player commands ─────────────────────────────────────

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        // No arena named → quick-join: a RANDOM open arena, or a fresh
        // procedural one if none are free (random matchmaking + rotation).
        if (args.length < 2) {
            Arena arena = plugin.getGameManager().findRandomAvailableArena().orElse(null);
            final var cfg = plugin.getConfigManager();
            if (arena == null && cfg.isProceduralEnabled() && cfg.isProceduralAutoQuickjoin()) {
                arena = ProceduralArenaFactory.create(plugin);
                msg(player, "<gray>No open arenas — spun up a fresh random one!");
            }
            if (arena == null) { msg(player, "<red>No arenas available to quick-join."); return true; }
            plugin.getGameManager().addPlayerToArena(player, arena);
            return true;
        }

        Arena arena = plugin.getArenaRepository().getArena(args[1]).orElse(null);
        if (arena == null) { msg(player, "<red>Arena not found."); return true; }
        plugin.getGameManager().addPlayerToArena(player, arena);
        return true;
    }

    private boolean handleKit(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        plugin.getKitSelectorGUI().open(player);
        return true;
    }

    private boolean handleVote(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        plugin.getVoteGUI().open(player);
        return true;
    }

    private boolean handleRandom(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (!plugin.getConfigManager().isProceduralEnabled()) {
            msg(player, "<red>Procedural random arenas are disabled in config.");
            return true;
        }
        Arena arena = ProceduralArenaFactory.create(plugin);
        plugin.getGameManager().addPlayerToArena(player, arena);
        msg(player, "<green>Generated a fresh random arena — good luck!");
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        plugin.getGameManager().removePlayer(player);
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.getGuiManager().open(player);
            return true;
        }
        String names = plugin.getArenaRepository().getArenas().stream()
                .map(Arena::getName).collect(Collectors.joining(", "));
        msg(sender, "<green>Arenas: " + names);
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        final StatsManager stats = plugin.getStatsManager();
        final UUID target;
        final String name;
        if (args.length >= 2) {
            // Look up by name without requiring the player to be online — works for
            // anyone who has played on this server (resolved from the local cache,
            // no blocking web request).
            final org.bukkit.OfflinePlayer off = plugin.getServer().getOfflinePlayerIfCached(args[1]);
            if (off == null) {
                msg(sender, "<red>Unknown player — never seen on this server.");
                return true;
            }
            target = off.getUniqueId();
            name = off.getName() != null ? off.getName() : args[1];
        } else if (sender instanceof Player p) {
            target = p.getUniqueId();
            name = p.getName();
        } else {
            msg(sender, "<red>Usage: /lavarise stats <player>");
            return true;
        }

        msg(sender, "<gradient:red:gold><bold>Stats — " + name + "</bold></gradient>");
        msg(sender, "<gray>Wins: <yellow>" + stats.getWins(target));
        msg(sender, "<gray>Games: <yellow>" + stats.getGames(target));
        msg(sender, "<gray>Kills: <yellow>" + stats.getKills(target));
        msg(sender, "<gray>Deaths: <yellow>" + stats.getDeaths(target));
        msg(sender, "<gray>Best survival: <yellow>" + stats.getBestTime(target) + "s");
        msg(sender, "<gray>Win rate: <yellow>" + String.format("%.1f%%", stats.getWinRate(target) * 100));
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        String stat = args.length >= 2 ? args[1].toLowerCase() : "wins";
        String key = switch (stat) {
            case "kills" -> "kills";
            case "time", "best", "survival" -> "best_time";
            default -> "wins";
        };
        final String label = switch (key) {
            case "kills" -> "Kills";
            case "best_time" -> "Best Survival";
            default -> "Wins";
        };
        List<StatsManager.Entry> top = plugin.getStatsManager().top(key, 10);
        msg(sender, "<gradient:red:gold><bold>Top 10 — " + label + "</bold></gradient>");
        if (top.isEmpty()) { msg(sender, "<gray>No data yet."); return true; }
        int rank = 1;
        for (StatsManager.Entry e : top) {
            final String value = key.equals("best_time") ? e.value() + "s" : String.valueOf(e.value());
            msg(sender, "<yellow>#" + rank++ + " <white>" + e.name() + " <dark_gray>— <gray>" + value);
        }
        return true;
    }

    /** Read-only arena status — all arenas, or details for one. */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            final Arena arena = plugin.getArenaRepository().getArena(args[1]).orElse(null);
            if (arena == null) { msg(sender, "<red>Arena not found."); return true; }
            final ArenaSession s = arena.getSession();
            msg(sender, "<gradient:red:gold><bold>Arena — " + arena.getName() + "</bold></gradient>");
            msg(sender, "<gray>World: <yellow>" + (arena.getConfig().world() != null
                    ? arena.getConfig().world().getName() : "?")
                    + " <dark_gray>· <gray>Mode: <yellow>" + arena.getConfig().gameMode());
            if (s == null) { msg(sender, "<gray>State: <red>no session"); return true; }
            msg(sender, "<gray>State: <yellow>" + s.getCurrentState().getDisplayName());
            msg(sender, "<gray>Players: <yellow>" + s.getAliveCount() + "<gray>/<yellow>"
                    + arena.getConfig().maxPlayers() + " <dark_gray>(" + s.getPlayerCount() + " total)");
            msg(sender, "<gray>Lava: <yellow>" + s.getLavaHeight() + "m <dark_gray>("
                    + s.getLavaPercent() + "%, y=" + s.getCurrentLavaY() + ")");
            return true;
        }
        final var arenas = plugin.getGameManager().getAllArenas();
        if (arenas.isEmpty()) { msg(sender, "<gray>No arenas exist."); return true; }
        msg(sender, "<gradient:red:gold><bold>Arenas (" + arenas.size() + ")</bold></gradient>");
        for (Arena arena : arenas) {
            final ArenaSession s = arena.getSession();
            final String state = s != null ? s.getCurrentState().getDisplayName() : "—";
            final int alive = s != null ? s.getAliveCount() : 0;
            msg(sender, "<yellow>" + arena.getName() + " <dark_gray>— <gray>" + state
                    + " <dark_gray>(" + alive + "/" + arena.getConfig().maxPlayers() + ")");
        }
        return true;
    }

    // ── Admin: lifecycle ────────────────────────────────────

    private boolean handleStart(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 2) { msg(sender, "<red>Usage: /lavarise start <arena>"); return true; }
        Arena arena = plugin.getArenaRepository().getArena(args[1]).orElse(null);
        if (arena == null) { msg(sender, "<red>Arena not found."); return true; }
        ArenaSession session = arena.getSession();
        if (session == null) { msg(sender, "<red>That arena has no active session."); return true; }
        if (session.getCurrentState().isGameRunning()) { msg(sender, "<red>That game is already running."); return true; }
        if (session.getAliveCount() < 1) { msg(sender, "<red>No players in that arena."); return true; }
        session.transitionTo(new ActiveState(plugin, arena, session));
        msg(sender, "<green>Force-started <yellow>" + arena.getName() + "</yellow>.");
        return true;
    }

    private boolean handleSkip(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 2) { msg(sender, "<red>Usage: /lavarise skip <arena>"); return true; }
        Arena arena = plugin.getArenaRepository().getArena(args[1]).orElse(null);
        if (arena == null) { msg(sender, "<red>Arena not found."); return true; }
        ArenaSession session = arena.getSession();
        if (session == null) { msg(sender, "<red>That arena has no active session."); return true; }
        if (session.getCurrentState().isGameRunning()) { msg(sender, "<red>That game is already running."); return true; }
        if (session.getAliveCount() < 1) { msg(sender, "<red>No players in that arena."); return true; }
        session.transitionTo(new CountdownState(plugin, arena, session, 3));
        msg(sender, "<green>Skipped to a 3-second countdown for <yellow>" + arena.getName() + "</yellow>.");
        return true;
    }

    private boolean handleFreeze(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 2) { msg(sender, "<red>Usage: /lavarise freeze <arena>"); return true; }
        Arena arena = plugin.getArenaRepository().getArena(args[1]).orElse(null);
        if (arena == null) { msg(sender, "<red>Arena not found."); return true; }
        ArenaSession session = arena.getSession();
        if (session == null || !session.getCurrentState().isGameRunning()) {
            msg(sender, "<red>That game is not running.");
            return true;
        }
        boolean frozen = !session.isPaused();
        session.setPaused(frozen);
        msg(sender, frozen ? "<yellow>❄ Lava frozen in <white>" + arena.getName()
                : "<green>🔥 Lava resumed in <white>" + arena.getName());
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 2) { msg(sender, "<red>Usage: /lavarise stop <arena>"); return true; }
        Arena arena = plugin.getArenaRepository().getArena(args[1]).orElse(null);
        if (arena == null) { msg(sender, "<red>Arena not found."); return true; }
        if (arena.getSession() != null) arena.getSession().forceEnd();
        arena.destroySession();
        arena.createSession();
        msg(sender, "<green>Stopped and reset <yellow>" + arena.getName() + "</yellow>.");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (notAdmin(sender)) return true;
        plugin.reload();
        msg(sender, plugin.getConfigManager().getMessage("general.reload-success"));
        return true;
    }

    // ── Admin: survival challenge (world-wide) ──────────────

    private boolean handleSurvival(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 2) { msg(sender, "<red>Usage: /lavarise survival <start|stop> [world]"); return true; }

        final World world;
        if (args.length >= 3) {
            world = plugin.getServer().getWorld(args[2]);
        } else if (sender instanceof Player p) {
            world = p.getWorld();
        } else {
            world = null;
        }
        if (world == null) { msg(sender, "<red>Specify a valid world."); return true; }

        switch (args[1].toLowerCase()) {
            case "start" -> {
                if (survival.startChallenge(world)) {
                    msg(sender, "<green>Survival challenge started in <yellow>" + world.getName() + "</yellow>.");
                } else {
                    msg(sender, "<red>A survival challenge is already running in that world.");
                }
            }
            case "stop" -> {
                if (survival.stopChallenge(world)) {
                    msg(sender, "<green>Survival challenge stopped in <yellow>" + world.getName() + "</yellow>.");
                } else {
                    msg(sender, "<red>No survival challenge running in that world.");
                }
            }
            default -> msg(sender, "<red>Usage: /lavarise survival <start|stop> [world]");
        }
        return true;
    }

    // ── Admin: event control (start/pause/resume/stop) ──────

    private boolean handleEvent(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 3) { msg(sender, "<red>Usage: /lavarise event <start|pause|resume|stop> <arena>"); return true; }
        Arena arena = plugin.getArenaRepository().getArena(args[2]).orElse(null);
        if (arena == null) { msg(sender, "<red>Arena not found."); return true; }
        ArenaSession session = arena.getSession();
        if (session == null) { msg(sender, "<red>That arena has no active session."); return true; }

        switch (args[1].toLowerCase()) {
            case "start" -> {
                if (session.getCurrentState().isGameRunning()) { msg(sender, "<red>Already running."); return true; }
                if (session.getAliveCount() < 1) { msg(sender, "<red>No players in that arena."); return true; }
                session.transitionTo(new ActiveState(plugin, arena, session));
                msg(sender, "<green>Event started in <yellow>" + arena.getName() + "</yellow>.");
            }
            case "pause" -> {
                session.setPaused(true);
                msg(sender, plugin.getConfigManager().getMessage("event.pause"));
            }
            case "resume" -> {
                session.setPaused(false);
                msg(sender, plugin.getConfigManager().getMessage("event.resume"));
            }
            case "stop" -> {
                session.forceEnd();
                arena.destroySession();
                arena.createSession();
                msg(sender, "<green>Event stopped in <yellow>" + arena.getName() + "</yellow>.");
            }
            default -> msg(sender, "<red>Usage: /lavarise event <start|pause|resume|stop> <arena>");
        }
        return true;
    }

    // ── Admin: setup wizard ─────────────────────────────────

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 2) { msg(player, "<red>Usage: /lavarise create <name>"); return true; }
        String name = args[1];
        // Arena names become file names — reject anything that could escape the
        // arenas directory (path traversal) or break YAML keys.
        if (!dev.lavarise.arena.ArenaNames.isValid(name)) {
            msg(player, "<red>Invalid name. Use 1-32 letters, digits, '_' or '-' only.");
            return true;
        }
        if (plugin.getArenaRepository().getArena(name).isPresent()) {
            msg(player, "<red>An arena named <yellow>" + name + "</yellow> already exists.");
            return true;
        }
        setups.put(player.getUniqueId(), new SetupSession(name));
        msg(player, plugin.getConfigManager().getMessage("setup.arena-created", "{arena}", name));
        msg(player, "<gray>Now set <yellow>pos1</yellow>, <yellow>pos2</yellow> and spawns, then <yellow>/lavarise save</yellow>.");
        return true;
    }

    /**
     * One-shot arena setup: build a ready-to-play arena centred on where the admin
     * is standing — no pos1/pos2/spawn steps. {@code /lavarise setup <name> [radius]}.
     */
    private boolean handleQuickSetup(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 2) { msg(player, "<red>Usage: /lavarise setup <name> [radius]"); return true; }
        final String name = args[1];
        if (!dev.lavarise.arena.ArenaNames.isValid(name)) {
            msg(player, "<red>Invalid name. Use 1-32 letters, digits, '_' or '-' only.");
            return true;
        }
        if (plugin.getArenaRepository().getArena(name).isPresent()) {
            msg(player, "<red>An arena named <yellow>" + name + "</yellow> already exists.");
            return true;
        }
        int radius = 30;
        if (args.length >= 3) {
            try { radius = Math.max(5, Math.min(200, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ignored) { }
        }

        final Location center = player.getLocation();
        final World world = center.getWorld();
        final int cx = center.getBlockX();
        final int cy = center.getBlockY();
        final int cz = center.getBlockZ();
        final int floorY = Math.max(world.getMinHeight(), cy - 5);
        final int ceilY = Math.min(world.getMaxHeight() - 1, cy + 60);

        final Location corner1 = new Location(world, cx - radius, floorY, cz - radius);
        final Location corner2 = new Location(world, cx + radius, ceilY, cz + radius);
        final Location spawn = new Location(world, cx + 0.5, cy, cz + 0.5);
        final Location spectator = new Location(world, cx + 0.5, ceilY, cz + 0.5);

        final var cfg = plugin.getConfigManager();
        final ArenaConfig config = new ArenaConfig(
                name, world, corner1, corner2, spawn, spawn, spectator,
                cfg.getDefaultMinPlayers(), cfg.getDefaultMaxPlayers(),
                cfg.getDefaultCountdown(), cfg.getDefaultCountdown(),
                cfg.getLavaRiseInterval(), cfg.getLavaRiseAmount(),
                cy, ceilY, // lava rises from the admin's feet up to the ceiling
                cfg.isDefaultPvp(), cfg.isDefaultBlockBreak(), cfg.isDefaultBlockPlace(),
                cfg.isDefaultKeepInventory(), cfg.isDefaultHunger(), cfg.getDefaultGameMode());

        final Arena arena = new Arena(plugin, config);
        plugin.getArenaRepository().addArena(arena);
        arena.createSession();
        msg(player, "<green>✔ Arena <yellow>" + name + "</yellow> created around you — "
                + (radius * 2 + 1) + "×" + (radius * 2 + 1) + ", lava <gray>" + cy + "→" + ceilY
                + "<green>. It's open to join now!");
        return true;
    }

    private boolean handleSetCorner(CommandSender sender, int corner) {
        if (notAdmin(sender)) return true;
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        SetupSession setup = setups.get(player.getUniqueId());
        if (setup == null) { msg(player, "<red>Start with /lavarise create <name> first."); return true; }
        Location loc = player.getLocation();
        if (corner == 1) setup.corner1 = loc; else setup.corner2 = loc;
        msg(player, plugin.getConfigManager().getMessage("setup.pos" + corner + "-set",
                "{x}", String.valueOf(loc.getBlockX()),
                "{y}", String.valueOf(loc.getBlockY()),
                "{z}", String.valueOf(loc.getBlockZ())));
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String type) {
        if (notAdmin(sender)) return true;
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        SetupSession setup = setups.get(player.getUniqueId());
        if (setup == null) { msg(player, "<red>Start with /lavarise create <name> first."); return true; }
        Location loc = player.getLocation();
        switch (type) {
            case "lobby" -> { setup.lobby = loc; msg(player, plugin.getConfigManager().getMessage("setup.lobby-set", "{arena}", setup.name)); }
            case "game" -> { setup.game = loc; msg(player, plugin.getConfigManager().getMessage("setup.spawn-set", "{arena}", setup.name)); }
            case "spectator" -> { setup.spectator = loc; msg(player, "<green>Spectator spawn set for <yellow>" + setup.name + "</yellow>."); }
        }
        return true;
    }

    private boolean handleSave(CommandSender sender) {
        if (notAdmin(sender)) return true;
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        SetupSession setup = setups.get(player.getUniqueId());
        if (setup == null) { msg(player, "<red>Nothing to save. Start with /lavarise create <name>."); return true; }
        if (setup.corner1 == null || setup.corner2 == null) {
            msg(player, "<red>Set both <yellow>pos1</yellow> and <yellow>pos2</yellow> before saving.");
            return true;
        }
        var cfg = plugin.getConfigManager();
        ArenaConfig config = new ArenaConfig(
                setup.name,
                setup.corner1.getWorld(),
                setup.corner1,
                setup.corner2,
                setup.lobby,
                setup.game,
                setup.spectator,
                cfg.getDefaultMinPlayers(),
                cfg.getDefaultMaxPlayers(),
                cfg.getDefaultCountdown(),
                cfg.getDefaultCountdown(),
                cfg.getLavaRiseInterval(),
                cfg.getLavaRiseAmount(),
                cfg.getDefaultLavaStartY(),
                cfg.getDefaultLavaMaxY(),
                cfg.isDefaultPvp(),
                cfg.isDefaultBlockBreak(),
                cfg.isDefaultBlockPlace(),
                cfg.isDefaultKeepInventory(),
                cfg.isDefaultHunger(),
                cfg.getDefaultGameMode());

        Arena arena = new Arena(plugin, config);
        plugin.getArenaRepository().addArena(arena);
        arena.createSession();
        setups.remove(player.getUniqueId());
        msg(player, plugin.getConfigManager().getMessage("setup.saved", "{arena}", setup.name));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 2) { msg(sender, "<red>Usage: /lavarise delete <arena>"); return true; }
        if (plugin.getArenaRepository().getArena(args[1]).isEmpty()) {
            msg(sender, "<red>Arena not found.");
            return true;
        }
        plugin.getArenaRepository().deleteArena(args[1]);
        msg(sender, plugin.getConfigManager().getMessage("setup.arena-deleted", "{arena}", args[1]));
        return true;
    }

    // ── Admin: stress test ──────────────────────────────────

    private boolean handleStress(CommandSender sender, String[] args) {
        if (notAdmin(sender)) return true;
        if (args.length < 3) {
            msg(sender, "<red>Usage: /lavarise stress <arena> <blocksPerTick>");
            return true;
        }
        Arena arena = plugin.getArenaRepository().getArena(args[1]).orElse(null);
        if (arena == null) { msg(sender, "<red>Arena not found."); return true; }
        final int blocksPerTick;
        try {
            blocksPerTick = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            msg(sender, "<red>Invalid number.");
            return true;
        }
        msg(sender, "<gold>Starting stress test on " + arena.getName() + " at " + blocksPerTick + " blocks/tick...");
        new BukkitRunnable() {
            int cx = arena.getConfig().minX();
            int cy = arena.getConfig().lavaStartY();
            int cz = arena.getConfig().minZ();
            final FastBlockSetter setter = new FastBlockSetter(arena.getConfig().world(), Material.LAVA.createBlockData());

            @Override
            public void run() {
                long startNanos = System.nanoTime();
                int processed = 0;
                while (processed < blocksPerTick) {
                    if (cy > arena.getConfig().lavaMaxY()) {
                        msg(sender, "<green>Stress test finished.");
                        this.cancel();
                        return;
                    }
                    setter.setBlock(cx, cy, cz);
                    processed++;
                    cx++;
                    if (cx > arena.getConfig().maxX()) {
                        cx = arena.getConfig().minX();
                        cz++;
                        if (cz > arena.getConfig().maxZ()) {
                            cz = arena.getConfig().minZ();
                            cy++;
                        }
                    }
                }
                double ms = (System.nanoTime() - startNanos) / 1_000_000.0;
                if (ms > 10.0) {
                    plugin.getLogger().warning("Stress test tick took " + ms + "ms for " + blocksPerTick + " blocks.");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ── Helpers ─────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        msg(sender, "<gradient:red:gold><bold>LavaRise Commands</bold></gradient>");
        msg(sender, "<yellow>/lr join [arena] <gray>- Join (no name = quick-join a random arena)");
        msg(sender, "<yellow>/lr random <gray>- Generate & join a fresh random arena");
        msg(sender, "<yellow>/lr kit <gray>- Choose your kit/loadout");
        msg(sender, "<yellow>/lr vote <gray>- Vote for the lobby's kit");
        msg(sender, "<yellow>/lr leave <gray>- Leave your game");
        msg(sender, "<yellow>/lr list <gray>- Browse arenas");
        msg(sender, "<yellow>/lr stats [player] <gray>- View statistics");
        msg(sender, "<yellow>/lr top [wins|kills|time] <gray>- Leaderboard");
        msg(sender, "<yellow>/lr info [arena] <gray>- Arena status");
        if (sender.hasPermission("lavarise.admin")) {
            msg(sender, "<gold>Admin: <yellow>setup <name> [radius] <gray>- one-command arena where you stand");
            msg(sender, "<gold>Admin: <yellow>create/pos1/pos2/setlobby/setgamespawn/setspectator/save/delete");
            msg(sender, "<gold>Admin: <yellow>start/skip/freeze/stop/reload/stress");
            msg(sender, "<gold>Admin: <yellow>survival <start|stop> [world]");
            msg(sender, "<gold>Admin: <yellow>event <start|pause|resume|stop> <arena>");
        }
    }

    private boolean notAdmin(CommandSender sender) {
        if (!sender.hasPermission("lavarise.admin")) {
            msg(sender, plugin.getConfigManager().getMessage("general.no-permission"));
            return true;
        }
        return false;
    }

    private void msg(CommandSender sender, String mini) {
        sender.sendMessage(plugin.getMiniMessage().deserialize(mini));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("join", "random", "kit", "vote", "leave", "list", "stats", "top", "info"));
            if (sender.hasPermission("lavarise.admin")) {
                completions.addAll(List.of("setup", "create", "pos1", "pos2", "setlobby", "setgamespawn",
                        "setspectator", "save", "delete", "start", "skip", "freeze", "stop", "reload", "stress",
                        "survival", "event"));
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("join", "start", "skip", "freeze", "stop", "delete", "stress").contains(sub)) {
                plugin.getArenaRepository().getArenas().forEach(a -> completions.add(a.getName()));
            } else if (sub.equals("top")) {
                completions.addAll(List.of("wins", "kills", "time"));
            } else if (sub.equals("survival")) {
                completions.addAll(List.of("start", "stop"));
            } else if (sub.equals("event")) {
                completions.addAll(List.of("start", "pause", "resume", "stop"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("event")) {
            plugin.getArenaRepository().getArenas().forEach(a -> completions.add(a.getName()));
        }
        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    /** Mutable holder for an in-progress arena setup. */
    private static final class SetupSession {
        final String name;
        Location corner1, corner2, lobby, game, spectator;
        SetupSession(String name) { this.name = name; }
    }
}
