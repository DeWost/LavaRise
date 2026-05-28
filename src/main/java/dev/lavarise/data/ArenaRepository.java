package dev.lavarise.data;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Persistence layer for arena configurations.
 * <p>
 * Each arena is stored as a separate YAML file under
 * {@code plugins/LavaRise/arenas/<name>.yml}.
 * </p>
 */
public final class ArenaRepository {

    private final LavaRisePlugin plugin;
    private final File arenasDir;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaRepository(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.arenasDir = new File(plugin.getDataFolder(), "arenas");
        if (!arenasDir.exists()) {
            arenasDir.mkdirs();
        }
    }

    // ── Load ────────────────────────────────────────────────

    /**
     * Load all arena configs from disk and register them.
     */
    public void loadArenas() {
        arenas.clear();

        final File[] files = arenasDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No arenas found in arenas/ directory.");
            return;
        }

        for (File file : files) {
            try {
                final Arena arena = loadArena(file);
                if (arena != null) {
                    arenas.put(arena.getName().toLowerCase(), arena);
                    plugin.getGameManager().registerArena(arena);
                    // Auto-create session so arena is joinable
                    arena.createSession();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to load arena from " + file.getName(), e);
            }
        }

        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    private Arena loadArena(File file) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        final String name = yaml.getString("name");
        if (name == null) {
            plugin.getLogger().warning("Arena file " + file.getName() + " missing 'name' field.");
            return null;
        }

        final String worldName = yaml.getString("world");
        if (worldName == null) {
            plugin.getLogger().warning("Arena " + name + " missing 'world' field.");
            return null;
        }

        final World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Arena " + name + ": world '" + worldName + "' not loaded.");
            return null;
        }

        final ConfigManager cfg = plugin.getConfigManager();

        final ArenaConfig config = new ArenaConfig(
                name,
                world,
                deserializeLocation(yaml, "corner1", world),
                deserializeLocation(yaml, "corner2", world),
                deserializeLocation(yaml, "lobby-spawn", world),
                deserializeLocation(yaml, "game-spawn", world),
                deserializeLocation(yaml, "spectator-spawn", world),
                yaml.getInt("min-players", cfg.getDefaultMinPlayers()),
                yaml.getInt("max-players", cfg.getDefaultMaxPlayers()),
                yaml.getInt("lobby-countdown", cfg.getDefaultCountdown()),
                yaml.getInt("game-countdown", cfg.getDefaultCountdown()),
                yaml.getInt("lava-rise-interval", cfg.getLavaRiseInterval()),
                yaml.getInt("lava-rise-amount", cfg.getLavaRiseAmount()),
                yaml.getInt("lava-start-y", cfg.getDefaultLavaStartY()),
                yaml.getInt("lava-max-y", cfg.getDefaultLavaMaxY()),
                yaml.getBoolean("pvp", cfg.isDefaultPvp()),
                yaml.getBoolean("block-break", cfg.isDefaultBlockBreak()),
                yaml.getBoolean("block-place", cfg.isDefaultBlockPlace()),
                yaml.getBoolean("keep-inventory", cfg.isDefaultKeepInventory()),
                yaml.getBoolean("hunger", cfg.isDefaultHunger()),
                yaml.getString("game-mode", cfg.getDefaultGameMode())
        );

        return new Arena(plugin, config);
    }

    // ── Save ────────────────────────────────────────────────

    /**
     * Save a single arena to disk.
     */
    public void saveArena(Arena arena) {
        final File file = new File(arenasDir, arena.getName().toLowerCase() + ".yml");
        final YamlConfiguration yaml = new YamlConfiguration();
        final ArenaConfig c = arena.getConfig();

        yaml.set("name", c.name());
        yaml.set("world", c.world().getName());

        serializeLocation(yaml, "corner1", c.corner1());
        serializeLocation(yaml, "corner2", c.corner2());
        serializeLocation(yaml, "lobby-spawn", c.lobbySpawn());
        serializeLocation(yaml, "game-spawn", c.gameSpawn());
        serializeLocation(yaml, "spectator-spawn", c.spectatorSpawn());

        yaml.set("min-players", c.minPlayers());
        yaml.set("max-players", c.maxPlayers());
        yaml.set("lobby-countdown", c.lobbyCountdown());
        yaml.set("game-countdown", c.gameCountdown());
        yaml.set("lava-rise-interval", c.lavaRiseInterval());
        yaml.set("lava-rise-amount", c.lavaRiseAmount());
        yaml.set("lava-start-y", c.lavaStartY());
        yaml.set("lava-max-y", c.lavaMaxY());
        yaml.set("pvp", c.pvpEnabled());
        yaml.set("block-break", c.blockBreak());
        yaml.set("block-place", c.blockPlace());
        yaml.set("keep-inventory", c.keepInventory());
        yaml.set("hunger", c.hunger());
        yaml.set("game-mode", c.gameMode());

        try {
            yaml.save(file);
            plugin.debug("Saved arena: " + arena.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save arena " + arena.getName(), e);
        }
    }

    /**
     * Save all arenas to disk.
     */
    public void saveAll() {
        for (Arena arena : arenas.values()) {
            saveArena(arena);
        }
    }

    /**
     * Delete an arena from disk and memory.
     */
    public void deleteArena(String name) {
        final Arena removed = arenas.remove(name.toLowerCase());
        if (removed != null) {
            plugin.getGameManager().unregisterArena(name);
            final File file = new File(arenasDir, name.toLowerCase() + ".yml");
            if (file.exists()) {
                file.delete();
            }
        }
    }

    // ── Queries ─────────────────────────────────────────────

    public Collection<Arena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public Optional<Arena> getArena(String name) {
        return Optional.ofNullable(arenas.get(name.toLowerCase()));
    }

    /**
     * Add a newly created arena to the repository and save it.
     */
    public void addArena(Arena arena) {
        arenas.put(arena.getName().toLowerCase(), arena);
        plugin.getGameManager().registerArena(arena);
        saveArena(arena);
    }

    // ── Location Helpers ────────────────────────────────────

    private void serializeLocation(YamlConfiguration yaml, String key, Location loc) {
        if (loc == null) return;
        yaml.set(key + ".x", loc.getX());
        yaml.set(key + ".y", loc.getY());
        yaml.set(key + ".z", loc.getZ());
        yaml.set(key + ".yaw", loc.getYaw());
        yaml.set(key + ".pitch", loc.getPitch());
    }

    private Location deserializeLocation(YamlConfiguration yaml, String key, World world) {
        if (!yaml.contains(key + ".x")) return null;
        return new Location(
                world,
                yaml.getDouble(key + ".x"),
                yaml.getDouble(key + ".y"),
                yaml.getDouble(key + ".z"),
                (float) yaml.getDouble(key + ".yaw", 0),
                (float) yaml.getDouble(key + ".pitch", 0)
        );
    }
}
