package dev.lavarise.data;

import java.util.List;
import java.util.UUID;

/**
 * Pluggable persistence backend for player statistics. Two implementations:
 * {@link YamlStatsStorage} (default, single-server) and {@link MySqlStatsStorage}
 * (network-wide). {@link StatsManager} delegates to whichever the config selects.
 *
 * @author DeWost
 */
public interface StatsStorage {

    void recordGamePlayed(UUID id, String name);
    void recordWin(UUID id, String name);
    void recordKill(UUID id, String name);
    void recordDeath(UUID id, String name);

    /** Update the player's best (longest) survival time if {@code seconds} beats it. */
    void recordSurvivalTime(UUID id, String name, long seconds);

    int getWins(UUID id);
    int getGames(UUID id);
    int getKills(UUID id);
    int getDeaths(UUID id);
    long getBestTime(UUID id);
    String getName(UUID id);

    /** Top players by stat key ("wins", "kills", "best_time"), descending. */
    List<StatsManager.Entry> top(String stat, int limit);

    /** Persist any pending changes. */
    void flush();

    /** Release resources (DB connections, etc.). */
    default void close() {}
}
