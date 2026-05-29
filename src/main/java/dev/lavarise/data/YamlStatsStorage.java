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
 * Default single-server stats backend, persisted to {@code stats.yml}.
 *
 * @author DeWost
 */
public final class YamlStatsStorage implements StatsStorage {

    private final LavaRisePlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private boolean dirty = false;

    public YamlStatsStorage(LavaRisePlugin plugin) {
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

    @Override public void recordGamePlayed(UUID id, String name) { setName(id, name); increment(id, "games", 1); }
    @Override public void recordWin(UUID id, String name)        { setName(id, name); increment(id, "wins", 1); }
    @Override public void recordKill(UUID id, String name)       { setName(id, name); increment(id, "kills", 1); }
    @Override public void recordDeath(UUID id, String name)      { setName(id, name); increment(id, "deaths", 1); }

    @Override
    public void recordSurvivalTime(UUID id, String name, long seconds) {
        setName(id, name);
        if (seconds > getBestTime(id)) {
            data.set(path(id, "best_time"), seconds);
            dirty = true;
        }
    }

    @Override public int getWins(UUID id)     { return data.getInt(path(id, "wins"), 0); }
    @Override public int getGames(UUID id)    { return data.getInt(path(id, "games"), 0); }
    @Override public int getKills(UUID id)    { return data.getInt(path(id, "kills"), 0); }
    @Override public int getDeaths(UUID id)   { return data.getInt(path(id, "deaths"), 0); }
    @Override public long getBestTime(UUID id) { return data.getLong(path(id, "best_time"), 0L); }
    @Override public String getName(UUID id)  { return data.getString(path(id, "name"), "?"); }

    @Override
    public List<StatsManager.Entry> top(String stat, int limit) {
        final List<StatsManager.Entry> entries = new ArrayList<>();
        if (data.getConfigurationSection("players") == null) return entries;
        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            final String base = "players." + key;
            entries.add(new StatsManager.Entry(
                    data.getString(base + ".name", "?"),
                    data.getLong(base + "." + stat, 0L)));
        }
        entries.sort(Comparator.comparingLong(StatsManager.Entry::value).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    @Override
    public void flush() {
        if (!dirty) return;
        try {
            data.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save stats.yml", e);
        }
    }

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
}
