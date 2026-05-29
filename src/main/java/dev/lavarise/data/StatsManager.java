package dev.lavarise.data;

import dev.lavarise.core.LavaRisePlugin;

import java.util.List;
import java.util.UUID;

/**
 * Public stats API. Delegates to a {@link StatsStorage} backend chosen by
 * {@code storage.type} — YAML (default, single-server) or MySQL (network-wide,
 * with graceful fallback to YAML if the connection fails).
 *
 * @author DeWost
 */
public final class StatsManager {

    private final StatsStorage storage;

    public StatsManager(LavaRisePlugin plugin) {
        StatsStorage chosen;
        if ("mysql".equalsIgnoreCase(plugin.getConfigManager().getStorageType())) {
            try {
                chosen = new MySqlStatsStorage(plugin);
            } catch (Exception e) {
                plugin.getLogger().warning("MySQL stats unavailable (" + e.getMessage()
                        + ") — falling back to YAML. Ensure the MySQL driver and credentials are correct.");
                chosen = new YamlStatsStorage(plugin);
            }
        } else {
            chosen = new YamlStatsStorage(plugin);
        }
        this.storage = chosen;
    }

    public void recordGamePlayed(UUID id, String name) { storage.recordGamePlayed(id, name); }
    public void recordWin(UUID id, String name)        { storage.recordWin(id, name); }
    public void recordKill(UUID id, String name)       { storage.recordKill(id, name); }
    public void recordDeath(UUID id, String name)      { storage.recordDeath(id, name); }
    public void recordSurvivalTime(UUID id, String name, long seconds) { storage.recordSurvivalTime(id, name, seconds); }

    public int getWins(UUID id)     { return storage.getWins(id); }
    public int getGames(UUID id)    { return storage.getGames(id); }
    public int getKills(UUID id)    { return storage.getKills(id); }
    public int getDeaths(UUID id)   { return storage.getDeaths(id); }
    public long getBestTime(UUID id) { return storage.getBestTime(id); }
    public String getName(UUID id)  { return storage.getName(id); }

    public double getWinRate(UUID id) {
        int games = getGames(id);
        return games == 0 ? 0.0 : (double) getWins(id) / games;
    }

    public List<Entry> top(String stat, int limit) {
        return storage.top(stat, limit);
    }

    /** Persist pending changes (called periodically + on disable). */
    public void save() {
        storage.flush();
    }

    /** Flush and release backend resources. */
    public void close() {
        storage.close();
    }

    /** A single leaderboard row. */
    public record Entry(String name, long value) {}
}
