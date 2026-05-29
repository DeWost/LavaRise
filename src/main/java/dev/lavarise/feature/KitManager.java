package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the configurable {@code kits} (loadouts) and tracks each player's
 * chosen kit, applied at game start. Inspired by KteRising's multi-kit model —
 * any number of kits can be defined in {@code config.yml}; players pick one with
 * {@code /lr kit}.
 *
 * @author DeWost
 */
public final class KitManager {

    private final LavaRisePlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final List<String> order = new ArrayList<>();
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();

    public KitManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)load kits from config — called on startup and reload. */
    public void load() {
        kits.clear();
        order.clear();
        final ConfigurationSection section = plugin.getConfig().getConfigurationSection("kits");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            final String base = "kits." + key;
            final Material icon = matchMaterial(plugin.getConfig().getString(base + ".icon", "CHEST"), Material.CHEST);
            final String label = plugin.getConfig().getString(base + ".label", key);
            final List<ItemStack> items = new ArrayList<>();
            for (String entry : plugin.getConfig().getStringList(base + ".items")) {
                ItemStack item = parseItem(entry);
                if (item != null) items.add(item);
            }
            kits.put(key, new Kit(key, label, icon, items));
            order.add(key);
        }
        plugin.debug("Loaded " + kits.size() + " kit(s).");
    }

    public boolean hasKits() {
        return !kits.isEmpty();
    }

    /** Kit keys in config order (stable — used for GUI slot mapping). */
    public List<String> order() {
        return order;
    }

    public Kit get(String name) {
        return name == null ? null : kits.get(name);
    }

    public void select(UUID playerId, String name) {
        if (kits.containsKey(name)) selected.put(playerId, name);
    }

    /** The player's chosen kit key, defaulting to the first defined kit. */
    public String selectedName(UUID playerId) {
        return selected.getOrDefault(playerId, order.isEmpty() ? null : order.get(0));
    }

    /** Give the player their selected kit (no-op if none defined). */
    public void applyKit(Player player) {
        final Kit kit = get(selectedName(player.getUniqueId()));
        if (kit == null) return;
        for (ItemStack item : kit.items()) {
            player.getInventory().addItem(item.clone());
        }
    }

    private ItemStack parseItem(String entry) {
        if (entry == null || entry.isBlank()) return null;
        final String[] parts = entry.split(":");
        final Material material = Material.matchMaterial(parts[0].trim());
        if (material == null) {
            plugin.getLogger().warning("Unknown material in kit: " + parts[0]);
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try { amount = Math.max(1, Integer.parseInt(parts[1].trim())); }
            catch (NumberFormatException ignored) {}
        }
        return new ItemStack(material, amount);
    }

    private Material matchMaterial(String name, Material fallback) {
        final Material m = Material.matchMaterial(name);
        return m != null ? m : fallback;
    }

    /** An immutable kit definition. */
    public record Kit(String name, String label, Material icon, List<ItemStack> items) {}
}
