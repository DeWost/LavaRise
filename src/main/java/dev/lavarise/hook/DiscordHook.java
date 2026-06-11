package dev.lavarise.hook;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Discord webhook integration for LavaRise.
 * <p>
 * Posts an embed to a configured Discord webhook when a game ends.
 * All HTTP calls are fully asynchronous and never block the main thread.
 * Disabled by default — enable via {@code discord.enabled: true} in config.yml.
 * </p>
 *
 * @author DeWost
 */
public final class DiscordHook {

    private static final int EMBED_COLOR_GREEN = 0x2ECC71;

    private final LavaRisePlugin plugin;
    private final HttpClient httpClient;

    // ── Cached config values (re-read on load()) ─────────────
    private boolean enabled;
    private String webhookUrl;
    private String username;
    private String avatarUrl;
    private boolean onGameEnd;

    /**
     * Constructs the hook and initialises the shared {@link HttpClient}.
     * Config values are not read here — call {@link #load()} immediately after.
     *
     * @param plugin the owning plugin
     */
    public DiscordHook(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * (Re-)reads all {@code discord.*} config keys from the live config.
     * Safe to call on every {@code /lr reload}.
     */
    public void load() {
        final var config = plugin.getConfig();
        this.enabled = config.getBoolean("discord.enabled", false);
        this.webhookUrl = config.getString("discord.webhook-url", "");
        this.username = config.getString("discord.username", "LavaRise");
        this.avatarUrl = config.getString("discord.avatar-url", "");
        this.onGameEnd = config.getBoolean("discord.on-game-end", true);
        plugin.debug("DiscordHook loaded — enabled=" + enabled + ", onGameEnd=" + onGameEnd);
    }

    /**
     * Called when an arena game ends. Builds and POSTs a rich embed to the
     * configured webhook, entirely on a background thread.
     *
     * @param arena          the arena that just finished
     * @param session        the session with kill/placement data
     * @param winner         the winning player, or {@code null} if no one survived
     * @param elapsedSeconds game duration in seconds
     */
    public void onGameEnd(Arena arena, ArenaSession session, Player winner, long elapsedSeconds) {
        if (!enabled || webhookUrl == null || webhookUrl.isBlank() || !onGameEnd) {
            return;
        }
        // Capture data on the calling (main) thread before handing off.
        final String arenaName = arena.getName();
        final String winnerName = (winner != null) ? winner.getName() : null;
        final String top3 = buildTop3(session);
        final String duration = formatDuration(elapsedSeconds);
        final String url = webhookUrl;
        final String uname = (username != null && !username.isBlank()) ? username : "LavaRise";
        final String avatar = (avatarUrl != null) ? avatarUrl : "";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final String json = buildPayload(arenaName, winnerName, duration, top3, uname, avatar);
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .whenComplete((response, ex) -> {
                            if (ex != null) {
                                plugin.getLogger().log(Level.WARNING,
                                        "Discord webhook send failed: " + ex.getMessage());
                            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                                plugin.getLogger().log(Level.FINE,
                                        "Discord webhook returned HTTP " + response.statusCode());
                            }
                        });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Discord webhook error: " + t.getMessage());
            }
        });
    }

    // ── Private helpers ──────────────────────────────────────

    /**
     * Builds the top-3 kill leaderboard string from the session.
     */
    private String buildTop3(ArenaSession session) {
        final List<UUID> allIds = new ArrayList<>(session.getAllPlayerIds());
        allIds.sort(Comparator.comparingInt(
                (UUID u) -> session.getSessionKills(u)).reversed());

        final StringBuilder sb = new StringBuilder();
        final int limit = Math.min(3, allIds.size());
        for (int i = 0; i < limit; i++) {
            final UUID u = allIds.get(i);
            final String name = resolveName(u);
            final int kills = session.getSessionKills(u);
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(i + 1).append(". ").append(name).append(" — ").append(kills).append(" kills");
        }
        if (sb.length() == 0) {
            sb.append("No players.");
        }
        return sb.toString();
    }

    private String resolveName(UUID uuid) {
        final Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) {
            return p.getName();
        }
        final String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return (name != null) ? name : "Unknown";
    }

    private static String formatDuration(long totalSeconds) {
        final long minutes = totalSeconds / 60;
        final long seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }

    /**
     * Assembles the Discord webhook JSON payload by hand (no external library).
     */
    private String buildPayload(String arenaName, String winnerName, String duration,
                                String top3, String uname, String avatar) {
        final String winner = (winnerName != null) ? winnerName : "No winner";
        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"username\":\"").append(jsonEscape(uname)).append("\"");
        if (!avatar.isBlank()) {
            sb.append(",\"avatar_url\":\"").append(jsonEscape(avatar)).append("\"");
        }
        sb.append(",\"embeds\":[{");
        sb.append("\"title\":\"").append(jsonEscape("🏆 Match Ended — " + arenaName)).append("\"");
        sb.append(",\"color\":").append(EMBED_COLOR_GREEN);

        final String desc = "**Winner:** " + winner
                + "\n**Duration:** " + duration
                + "\n\n**Top Kills:**\n" + top3;
        sb.append(",\"description\":\"").append(jsonEscape(desc)).append("\"");
        sb.append("}]}");
        return sb.toString();
    }

    /**
     * Minimal JSON string escaper — handles backslash, double-quote, and control
     * characters (newline, carriage-return, tab). Sufficient for user-controlled
     * strings that enter the payload (arena names, player names, configured text).
     */
    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
