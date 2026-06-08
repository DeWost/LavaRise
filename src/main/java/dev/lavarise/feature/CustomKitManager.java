package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player custom kits. A player snapshots their current inventory into a
 * personal loadout that can then be offered to the lobby as a votable option.
 * Persisted to {@code customkits.yml}.
 *
 * @author DeWost
 */
public class CustomKitManager {

    private final LavaRisePlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, List<ItemStack>> kits = new HashMap<>();

    public CustomKitManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "customkits.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!yaml.isConfigurationSection("kits")) return;
        for (String id : yaml.getConfigurationSection("kits").getKeys(false)) {
            try {
                final UUID uuid = UUID.fromString(id);
                final List<ItemStack> items = new ArrayList<>();
                for (Object obj : yaml.getList("kits." + id, List.of())) {
                    if (obj instanceof ItemStack stack) items.add(stack);
                }
                if (!items.isEmpty()) kits.put(uuid, items);
            } catch (IllegalArgumentException ignored) {
                // Malformed UUID key — skip.
            }
        }
    }

    public boolean hasKit(UUID uuid) {
        return kits.containsKey(uuid);
    }

    /** A defensive copy of the player's custom-kit items (empty if none). */
    public List<ItemStack> getKit(UUID uuid) {
        final List<ItemStack> items = kits.get(uuid);
        if (items == null) return List.of();
        final List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack item : items) copy.add(item.clone());
        return copy;
    }

    /**
     * Snapshot the player's current inventory (non-empty stacks) as their custom
     * kit. Returns the number of item stacks saved.
     */
    public int saveFromInventory(Player player) {
        final List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) items.add(stack.clone());
        }
        kits.put(player.getUniqueId(), items);
        persist();
        return items.size();
    }

    public void clear(UUID uuid) {
        if (kits.remove(uuid) != null) persist();
    }

    private void persist() {
        yaml.set("kits", null);
        for (Map.Entry<UUID, List<ItemStack>> entry : kits.entrySet()) {
            yaml.set("kits." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save customkits.yml: " + e.getMessage());
        }
    }
}
