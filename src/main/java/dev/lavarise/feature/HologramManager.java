package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.data.StatsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages holographic leaderboards rendered as stacked invisible {@link ArmorStand}
 * entities with custom names. No external hologram library is required.
 *
 * <p>Each hologram has an anchor {@link Location}, a stat key (wins, kills, games,
 * best_time) and a list of spawned stands. Data is persisted in
 * {@code holograms.yml}. A PersistentDataContainer tag ({@code lavarise:hologram})
 * is applied to every stand so they can be swept on restart/reload, preventing
 * duplicates.</p>
 *
 * @author DeWost
 */
public final class HologramManager {

    /** Vertical gap between adjacent hologram lines (blocks downward). */
    private static final double LINE_SPACING = 0.27;

    /** Maximum leaderboard rows (excluding the title line). */
    private static final int TOP_N = 10;

    private final LavaRisePlugin plugin;
    private final NamespacedKey hologramKey;
    private final File dataFile;

    /** All defined holograms (persisted). */
    private final List<HologramEntry> holograms = new ArrayList<>();

    /** Currently spawned armor-stand groups, parallel to {@code holograms}. */
    private final List<List<ArmorStand>> spawnedStands = new ArrayList<>();

    private BukkitTask refreshTask;
    private int refreshSeconds;

    public HologramManager(final LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.hologramKey = new NamespacedKey(plugin, "hologram");
        this.dataFile = new File(plugin.getDataFolder(), "holograms.yml");
        this.refreshSeconds = plugin.getConfig().getInt("holograms.refresh-seconds", 30);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    /**
     * Load hologram definitions and spawn all of them (call this one tick after
     * enable so worlds are ready).
     */
    public void load() {
        this.refreshSeconds = plugin.getConfig().getInt("holograms.refresh-seconds", 30);
        sweepOrphanStands();
        loadFromDisk();
        spawnAll();
        startRefreshTask();
    }

    /**
     * Despawn all stands and cancel the refresh task (call from onDisable).
     */
    public void disable() {
        cancelRefreshTask();
        despawnAll();
    }

    /**
     * Reload configuration, despawn everything and respawn (used by /lr reload).
     */
    public void reload() {
        cancelRefreshTask();
        despawnAll();
        holograms.clear();
        spawnedStands.clear();
        load();
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Create a new hologram at the given location for the specified stat.
     *
     * @param location anchor location
     * @param stat     stat key (wins, kills, games, best_time)
     * @return the index of the newly created hologram
     */
    public int create(final Location location, final String stat) {
        final HologramEntry entry = new HologramEntry(location, stat);
        holograms.add(entry);
        spawnedStands.add(new ArrayList<>());
        final int index = holograms.size() - 1;
        spawnHologram(index);
        saveToDisk();
        return index;
    }

    /**
     * Remove a hologram by index. Despawns its stands and persists the change.
     *
     * @param index hologram index
     * @return true if removed, false if out of range
     */
    public boolean remove(final int index) {
        if (index < 0 || index >= holograms.size()) {
            return false;
        }
        despawnHologram(index);
        holograms.remove(index);
        spawnedStands.remove(index);
        saveToDisk();
        return true;
    }

    /**
     * Returns an unmodifiable view of all hologram entries.
     */
    public List<HologramEntry> getHolograms() {
        return Collections.unmodifiableList(holograms);
    }

    // ── Sweep & orphan removal ──────────────────────────────────────────────────

    /**
     * Scan every loaded world for armor stands tagged with the hologram PDC key
     * and remove them. This prevents duplicate stands after a reload/restart.
     */
    private void sweepOrphanStands() {
        for (final World world : plugin.getServer().getWorlds()) {
            try {
                for (final Entity entity : world.getEntities()) {
                    if (entity instanceof ArmorStand stand) {
                        if (stand.getPersistentDataContainer().has(hologramKey, PersistentDataType.BYTE)) {
                            stand.remove();
                        }
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Error sweeping orphan hologram stands in world " + world.getName(), ex);
            }
        }
    }

    // ── Spawn / despawn ─────────────────────────────────────────────────────────

    private void spawnAll() {
        // Ensure spawnedStands list is in sync with holograms list.
        while (spawnedStands.size() < holograms.size()) {
            spawnedStands.add(new ArrayList<>());
        }
        for (int i = 0; i < holograms.size(); i++) {
            spawnHologram(i);
        }
    }

    private void despawnAll() {
        for (int i = 0; i < spawnedStands.size(); i++) {
            despawnHologram(i);
        }
    }

    private void despawnHologram(final int index) {
        if (index >= spawnedStands.size()) {
            return;
        }
        final List<ArmorStand> stands = spawnedStands.get(index);
        for (final ArmorStand stand : stands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        stands.clear();
    }

    private void spawnHologram(final int index) {
        if (index >= holograms.size()) {
            return;
        }
        final HologramEntry entry = holograms.get(index);
        final Location anchor = entry.location();

        final World world = anchor.getWorld();
        if (world == null) {
            plugin.getLogger().warning("[Holograms] World is null for hologram #" + index
                    + " stat=" + entry.stat() + " — skipping.");
            return;
        }

        try {
            // Ensure stands list exists for this index.
            while (spawnedStands.size() <= index) {
                spawnedStands.add(new ArrayList<>());
            }
            despawnHologram(index);

            final List<Component> lines = buildLines(entry.stat());
            final List<ArmorStand> stands = spawnedStands.get(index);
            for (int line = 0; line < lines.size(); line++) {
                final double yOffset = -line * LINE_SPACING;
                final Location loc = anchor.clone().add(0, yOffset, 0);
                final ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
                configureStand(stand, lines.get(line));
                stands.add(stand);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "[Holograms] Failed to spawn hologram #" + index, ex);
        }
    }

    private void configureStand(final ArmorStand stand, final Component name) {
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setPersistent(false);
        stand.setMarker(true);
        stand.customName(name);
        stand.getPersistentDataContainer().set(hologramKey, PersistentDataType.BYTE, (byte) 1);
    }

    // ── Line rendering ──────────────────────────────────────────────────────────

    /**
     * Build the list of text lines for a leaderboard hologram.
     *
     * @param stat the stat key
     * @return list of Adventure {@link Component}s, title first
     */
    private List<Component> buildLines(final String stat) {
        final List<Component> lines = new ArrayList<>();

        // Title
        final String titleLabel = statLabel(stat);
        lines.add(plugin.getMiniMessage().deserialize(
                "<gradient:red:gold><bold>Top " + titleLabel + "</bold></gradient>"));

        // Rows
        final List<StatsManager.Entry> top = plugin.getStatsManager().top(stat, TOP_N);
        if (top.isEmpty()) {
            lines.add(plugin.getMiniMessage().deserialize("<gray>No data yet."));
        } else {
            for (int i = 0; i < top.size(); i++) {
                final StatsManager.Entry e = top.get(i);
                final String valueStr = stat.equals("best_time")
                        ? formatTime(e.value())
                        : String.valueOf(e.value());
                lines.add(plugin.getMiniMessage().deserialize(
                        "<gray>#" + (i + 1) + " <yellow>" + e.name() + " <white>- " + valueStr));
            }
        }
        return lines;
    }

    private static String statLabel(final String stat) {
        return switch (stat) {
            case "kills" -> "Kills";
            case "games" -> "Games";
            case "best_time" -> "Best Time";
            default -> "Wins";
        };
    }

    /**
     * Format a best_time value (seconds) as {@code m:ss}.
     */
    static String formatTime(final long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0:00";
        }
        final long minutes = totalSeconds / 60;
        final long seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }

    // ── Refresh task ────────────────────────────────────────────────────────────

    private void startRefreshTask() {
        cancelRefreshTask();
        final long intervalTicks = Math.max(1L, (long) refreshSeconds * 20L);
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll,
                intervalTicks, intervalTicks);
    }

    private void cancelRefreshTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * Re-render all holograms from current stats. If the number of lines has
     * changed (e.g. new players on the board), the stands for that hologram are
     * fully respawned. Otherwise only the custom names are updated in-place.
     */
    private void refreshAll() {
        for (int i = 0; i < holograms.size(); i++) {
            try {
                refreshHologram(i);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[Holograms] Refresh failed for hologram #" + i, ex);
            }
        }
    }

    private void refreshHologram(final int index) {
        if (index >= holograms.size()) {
            return;
        }
        final HologramEntry entry = holograms.get(index);
        final List<Component> lines = buildLines(entry.stat());

        if (index >= spawnedStands.size()) {
            spawnHologram(index);
            return;
        }
        final List<ArmorStand> stands = spawnedStands.get(index);

        // If line count changed, respawn fully.
        if (stands.size() != lines.size()) {
            spawnHologram(index);
            return;
        }

        // Otherwise just update names.
        for (int line = 0; line < lines.size(); line++) {
            final ArmorStand stand = stands.get(line);
            if (stand == null || stand.isDead()) {
                // Stand disappeared — do a full respawn.
                spawnHologram(index);
                return;
            }
            stand.customName(lines.get(line));
        }
    }

    // ── Persistence ─────────────────────────────────────────────────────────────

    private void loadFromDisk() {
        if (!dataFile.exists()) {
            return;
        }
        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            final ConfigurationSection list = yaml.getConfigurationSection("holograms");
            if (list == null) {
                return;
            }
            for (final String key : list.getKeys(false)) {
                final ConfigurationSection sec = list.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                try {
                    final String worldName = sec.getString("world", "world");
                    final double x = sec.getDouble("x");
                    final double y = sec.getDouble("y");
                    final double z = sec.getDouble("z");
                    final String stat = sec.getString("stat", "wins");
                    final World world = plugin.getServer().getWorld(worldName);
                    if (world == null) {
                        plugin.getLogger().warning("[Holograms] World '" + worldName
                                + "' not found for hologram '" + key + "' — skipping.");
                        continue;
                    }
                    final Location loc = new Location(world, x, y, z);
                    holograms.add(new HologramEntry(loc, stat));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING,
                            "[Holograms] Could not read hologram entry '" + key + "'", ex);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "[Holograms] Failed to load holograms.yml", ex);
        }
    }

    private void saveToDisk() {
        try {
            final YamlConfiguration yaml = new YamlConfiguration();
            for (int i = 0; i < holograms.size(); i++) {
                final HologramEntry entry = holograms.get(i);
                final Location loc = entry.location();
                final String path = "holograms." + i;
                yaml.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
                yaml.set(path + ".x", loc.getX());
                yaml.set(path + ".y", loc.getY());
                yaml.set(path + ".z", loc.getZ());
                yaml.set(path + ".stat", entry.stat());
            }
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[Holograms] Failed to save holograms.yml", ex);
        }
    }

    // ── Data model ──────────────────────────────────────────────────────────────

    /**
     * Immutable definition of a single hologram leaderboard.
     *
     * @param location anchor (top of the hologram stack)
     * @param stat     stat key
     */
    public record HologramEntry(Location location, String stat) {}
}
