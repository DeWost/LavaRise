package dev.lavarise.hook;

import dev.lavarise.arena.Arena;
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
                default: break;
            }
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
