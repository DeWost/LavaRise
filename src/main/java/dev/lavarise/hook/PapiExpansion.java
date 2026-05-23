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
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
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

        // e.g. %lavarise_lava_level_arena1%
        if (params.startsWith("lava_level_")) {
            String arenaName = params.substring("lava_level_".length());
            Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
            if (arena != null && arena.getSession() != null) {
                return String.valueOf(arena.getSession().getCurrentLavaY());
            }
            return "0";
        }

        return null;
    }
}
