package dev.lavarise.feature;

import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The lobby "Leave" item — a tagged bed players can right-click to quit the game
 * without typing a command. Identified by a persistent-data tag so it can't be
 * spoofed by a same-material item from a kit or supply drop.
 *
 * @author DeWost
 */
public final class LeaveItem {

    private LeaveItem() {}

    private static NamespacedKey key(LavaRisePlugin plugin) {
        return new NamespacedKey(plugin, "leave_item");
    }

    public static ItemStack create(LavaRisePlugin plugin) {
        final ItemStack item = new ItemStack(Material.RED_BED);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize("<red><bold>⟵ Leave</bold>"));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean is(LavaRisePlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(key(plugin), PersistentDataType.BYTE);
    }
}
