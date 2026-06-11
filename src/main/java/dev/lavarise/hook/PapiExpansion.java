package dev.lavarise.hook;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PapiExpansion extends PlaceholderExpansion {

    private final LavaRisePlugin plugin;

    public PapiExpansion(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lavarise";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LavaRise Team";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // ── Per-player statistics ──────────────────────────
        if (player != null) {
            var stats = plugin.getStatsManager();
            switch (params) {
                case "wins": return String.valueOf(stats.getWins(player.getUniqueId()));
                case "games": return String.valueOf(stats.getGames(player.getUniqueId()));
                case "kills": return String.valueOf(stats.getKills(player.getUniqueId()));
                case "deaths": return String.valueOf(stats.getDeaths(player.getUniqueId()));
                case "best_time": return String.valueOf(stats.getBestTime(player.getUniqueId()));
                case "winrate": return String.format("%.1f", stats.getWinRate(player.getUniqueId()) * 100);
                case "level": return String.valueOf(plugin.getLevelManager().getLevel(player.getUniqueId()));
                case "xp": return String.valueOf(plugin.getLevelManager().getXp(player.getUniqueId()));
                case "xp_needed": return String.valueOf(plugin.getLevelManager().getXpNeeded(player.getUniqueId()));
                case "total_xp": return String.valueOf(plugin.getLevelManager().getTotalXp(player.getUniqueId()));
                case "xp_progress": {
                    final java.util.UUID id = player.getUniqueId();
                    final long xpIn = plugin.getLevelManager().getXp(id);
                    final long xpNeeded = plugin.getLevelManager().getXpNeeded(id);
                    final long levelCost = xpIn + xpNeeded;
                    if (levelCost <= 0) {
                        return "100%";
                    }
                    return String.format("%.1f%%", (double) xpIn / levelCost * 100.0);
                }
                default: break;
            }
        }

        // ── Current-match (session) stats for the requesting player ──
        if (player != null && params.startsWith("session_")) {
            final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
            final ArenaSession session = arena != null ? arena.getSession() : null;
            final java.util.UUID id = player.getUniqueId();
            if (session == null) {
                return params.equals("session_status") ? "" : "0";
            }
            switch (params) {
                case "session_kills": return String.valueOf(session.getSessionKills(id));
                case "session_survived": return String.valueOf(session.getSurvivalSeconds(id));
                case "session_status":
                    return session.isAlive(id) ? "Alive" : (session.isSpectator(id) ? "Spectating" : "");
                default: break;
            }
        }

        // ── Party / queue (player-scoped) ──
        if (player != null) {
            final java.util.UUID id = player.getUniqueId();
            switch (params) {
                case "in_party": return plugin.getPartyManager().isInParty(id) ? "Yes" : "No";
                case "party_size": {
                    final var party = plugin.getPartyManager().getParty(id);
                    return party != null ? String.valueOf(party.size()) : "0";
                }
                case "party_leader": {
                    final var party = plugin.getPartyManager().getParty(id);
                    if (party == null) return "";
                    final String name = plugin.getServer().getOfflinePlayer(party.getLeader()).getName();
                    return name != null ? name : "";
                }
                case "queued": return plugin.getQueueManager().isQueued(id) ? "Yes" : "No";
                case "queue_position": return String.valueOf(plugin.getQueueManager().positionOf(id));
                default: break;
            }
        }

        // ── Global counts (no player needed) ──
        switch (params) {
            case "playing": return String.valueOf(plugin.getGameManager().totalPlayersInGames());
            case "active_games": return String.valueOf(plugin.getGameManager().activeGames());
            case "queue_size": return String.valueOf(plugin.getQueueManager().size());
            case "arenas": return String.valueOf(plugin.getArenaRepository().getArenas().size());
            default: break;
        }

        // e.g. %lavarise_players_alive_arena1%
        if (params.startsWith("players_alive_")) {
            String arenaName = params.substring("players_alive_".length());
            Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
            if (arena != null && arena.getSession() != null) {
                return String.valueOf(arena.getSession().getAliveCount());
            }
            return "0";
        }

        // e.g. %lavarise_state_arena1%
        if (params.startsWith("state_")) {
            String arenaName = params.substring("state_".length());
            Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
            if (arena != null && arena.getSession() != null) {
                return arena.getSession().getCurrentState().getDisplayName();
            }
            return "Offline";
        }

        // %lavarise_lava_percent_arena1% — progress 0-100
        if (params.startsWith("lava_percent_")) {
            Arena arena = plugin.getArenaRepository().getArena(params.substring("lava_percent_".length())).orElse(null);
            return (arena != null && arena.getSession() != null)
                    ? String.valueOf(arena.getSession().getLavaPercent()) : "0";
        }

        // %lavarise_lava_y_arena1% — raw world Y of the lava surface
        if (params.startsWith("lava_y_")) {
            Arena arena = plugin.getArenaRepository().getArena(params.substring("lava_y_".length())).orElse(null);
            return (arena != null && arena.getSession() != null)
                    ? String.valueOf(arena.getSession().getCurrentLavaY()) : "0";
        }

        // %lavarise_lava_level_arena1% — player-friendly lava HEIGHT (blocks risen)
        if (params.startsWith("lava_level_")) {
            String arenaName = params.substring("lava_level_".length());
            Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
            if (arena != null && arena.getSession() != null) {
                return String.valueOf(arena.getSession().getLavaHeight());
            }
            return "0";
        }

        return null;
    }
}
