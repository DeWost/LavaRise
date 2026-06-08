package dev.lavarise.feature;

import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Config-driven sidebar scoreboard.
 * <p>
 * Title and lines come from {@code messages.yml} ({@code scoreboard.title} /
 * {@code scoreboard.lines}) and support the placeholders {@code {lava_level}},
 * {@code {alive}}, {@code {total}}, {@code {mode}} and {@code {arena}}. Updates
 * are flicker-free: each line is a team whose prefix is rewritten in place.
 * </p>
 *
 * @author DeWost
 */
public class ScoreboardModule {

    /** Unique invisible entries (one per possible line). */
    private static final String[] ENTRIES = new String[16];
    static {
        ChatColor[] colors = ChatColor.values();
        for (int i = 0; i < ENTRIES.length; i++) {
            ENTRIES[i] = colors[i % colors.length].toString() + ChatColor.RESET;
        }
    }

    private final LavaRisePlugin plugin;
    private final Map<UUID, Team[]> playerLines = new HashMap<>();

    public ScoreboardModule(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public void setupScoreboard(Player player, ArenaSession session) {
        if (!plugin.getConfigManager().isScoreboardEnabled()) return;

        final List<String> lines = configLines();
        final int count = Math.min(lines.size(), ENTRIES.length);

        final Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        final Objective obj = board.registerNewObjective(
                "lavarise", Criteria.DUMMY,
                plugin.getMiniMessage().deserialize(plugin.getConfigManager().getMessage("scoreboard.title")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        final Team[] teams = new Team[count];
        for (int i = 0; i < count; i++) {
            final Team team = board.registerNewTeam("line" + i);
            team.addEntry(ENTRIES[i]);
            obj.getScore(ENTRIES[i]).setScore(count - i);
            teams[i] = team;
        }

        player.setScoreboard(board);
        playerLines.put(player.getUniqueId(), teams);
        renderLines(player, session);
    }

    public void updateScoreboard(ArenaSession session) {
        final List<String> lines = configLines();
        final int count = Math.min(lines.size(), ENTRIES.length);

        // Fast path: when no line uses a per-player token ({kills}), the text is
        // identical for everyone — parse the MiniMessage templates ONCE per update
        // and reuse the components for all players (not players×lines per tick).
        final boolean perPlayer = lines.stream().anyMatch(l -> l.contains("{kills}"));
        if (!perPlayer) {
            final Component[] rendered = new Component[count];
            for (int i = 0; i < count; i++) {
                rendered[i] = render(lines.get(i), session, null);
            }
            for (UUID uuid : session.getAllPlayerIds()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                final Team[] teams = playerLines.get(p.getUniqueId());
                if (teams == null) continue;
                for (int i = 0; i < teams.length && i < rendered.length; i++) {
                    teams[i].prefix(rendered[i]);
                }
            }
            return;
        }

        // Per-player path: a line references the viewer's own stats.
        for (UUID uuid : session.getAllPlayerIds()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            final Team[] teams = playerLines.get(p.getUniqueId());
            if (teams == null) continue;
            for (int i = 0; i < teams.length && i < count; i++) {
                teams[i].prefix(render(lines.get(i), session, p));
            }
        }
    }

    private void renderLines(Player player, ArenaSession session) {
        final Team[] teams = playerLines.get(player.getUniqueId());
        if (teams == null) return;
        final List<String> lines = configLines();
        for (int i = 0; i < teams.length && i < lines.size(); i++) {
            teams[i].prefix(render(lines.get(i), session, player));
        }
    }

    private Component render(String line, ArenaSession session, Player viewer) {
        final var config = session.getArena().getConfig();
        String resolved = line
                .replace("{lava_level}", String.valueOf(session.getLavaHeight()))
                .replace("{lava_percent}", session.getLavaPercent() + "%")
                .replace("{lava_y}", String.valueOf(session.getCurrentLavaY()))
                .replace("{alive}", String.valueOf(session.getAliveCount()))
                .replace("{total}", String.valueOf(session.getPlayerCount()))
                .replace("{time}", formatTime(session.getElapsedSeconds()))
                .replace("{mode}", config.gameMode())
                .replace("{arena}", config.name());
        if (line.contains("{kills}")) {
            final int kills = viewer != null ? session.getSessionKills(viewer.getUniqueId()) : 0;
            resolved = resolved.replace("{kills}", String.valueOf(kills));
        }
        return plugin.getMiniMessage().deserialize(resolved);
    }

    private static String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private List<String> configLines() {
        return plugin.getConfigManager().getMessagesConfig().getStringList("scoreboard.lines");
    }

    public void cleanup(Player player) {
        playerLines.remove(player.getUniqueId());
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
