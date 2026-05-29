package dev.lavarise.data;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persistent per-player statistics, backed by {@code stats.yml}.
 * <p>
 * Tracks games played, wins, kills, deaths and the player's best (longest)
 * survival time. Writes are debounced behind {@link #save()} and flushed on
 * disable; the in-memory {@link FileConfiguration} is the source of truth at
 * runtime so reads are cheap.
 * </p>
 */
public final class StatsManager {

    private final LavaRisePlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private boolean dirty = false;

    public StatsManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create stats.yml", e);
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    // ── Recording ───────────────────────────────────────────

    public void recordGamePlayed(UUID id, String name) {
        setName(id, name);
        increment(id, "games", 1);
    }

    public void recordWin(UUID id, String name) {
        setName(id, name);
        increment(id, "wins", 1);
    }

    public void recordKill(UUID id, String name) {
        setName(id, name);
        increment(id, "kills", 1);
    }

    public void recordDeath(UUID id, String name) {
        setName(id, name);
        increment(id, "deaths", 1);
    }

    /**
     * Update the player's best survival time if {@code seconds} beats their record.
     */
    public void recordSurvivalTime(UUID id, String name, long seconds) {
        setName(id, name);
        if (seconds > getBestTime(id)) {
            data.set(path(id, "best-time"), seconds);
            dirty = true;
        }
    }

    // ── Queries ─────────────────────────────────────────────

    public int getWins(UUID id) { return data.getInt(path(id, "wins"), 0); }
    public int getGames(UUID id) { return data.getInt(path(id, "games"), 0); }
    public int getKills(UUID id) { return data.getInt(path(id, "kills"), 0); }
    public int getDeaths(UUID id) { return data.getInt(path(id, "deaths"), 0); }
    public long getBestTime(UUID id) { return data.getLong(path(id, "best-time"), 0L); }
    public String getName(UUID id) { return data.getString(path(id, "name"), "?"); }

    /**
     * Win/loss ratio expressed as wins per game played (0..1).
     */
    public double getWinRate(UUID id) {
        int games = getGames(id);
        return games == 0 ? 0.0 : (double) getWins(id) / games;
    }

    /**
     * Top players by a given stat key ("wins", "kills", "best-time"), descending.
     */
    public List<Entry> top(String stat, int limit) {
        final List<Entry> entries = new ArrayList<>();
        if (data.getConfigurationSection("players") == null) return entries;
        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            final String base = "players." + key;
            entries.add(new Entry(
                    data.getString(base + ".name", "?"),
                    data.getLong(base + "." + stat, 0L)));
        }
        entries.sort(Comparator.comparingLong(Entry::value).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    // ── Persistence ─────────────────────────────────────────

    public void save() {
        if (!dirty) return;
        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stats.yml", e);
        }
    }

    // ── Internals ───────────────────────────────────────────

    private void setName(UUID id, String name) {
        data.set(path(id, "name"), name);
        dirty = true;
    }

    private void increment(UUID id, String stat, int by) {
        data.set(path(id, stat), data.getInt(path(id, stat), 0) + by);
        dirty = true;
    }

    private String path(UUID id, String key) {
        return "players." + id + "." + key;
    }

    /** A single leaderboard row. */
    public record Entry(String name, long value) {}
}
