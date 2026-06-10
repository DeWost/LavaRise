package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The spectator "Teleporter" compass — given to eliminated players so they can
 * right-click to open the {@link dev.lavarise.feature.gui.SpectatorMenu} and
 * teleport to any surviving player.
 * <p>
 * Identified by a PDC tag so it cannot be spoofed by a normal compass from a
 * kit or supply drop.
 * </p>
 *
 * @author DeWost
 */
public final class SpectatorCompassItem {

    private SpectatorCompassItem() {}

    private static NamespacedKey key(LavaRisePlugin plugin) {
        return new NamespacedKey(plugin, "spectator_compass");
    }

    /**
     * Build a fresh tagged spectator compass for the given plugin instance.
     */
    public static ItemStack create(LavaRisePlugin plugin) {
        final ItemStack item = new ItemStack(Material.COMPASS);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize(
                    "<!italic><aqua>Teleporter <gray>(right-click)"));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Return {@code true} if {@code item} is a tagged spectator compass.
     */
    public static boolean is(LavaRisePlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(key(plugin), PersistentDataType.BYTE);
    }
}
