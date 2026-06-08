package dev.lavarise.feature.gui;

import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.feature.KitManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inventory GUI for choosing a kit/loadout (KteRising-style). Uses its own
 * holder so it doesn't collide with {@link ArenaSelectorGUI}; slots map 1:1 to
 * the kit order for robust click handling.
 *
 * @author DeWost
 */
public class KitSelectorGUI implements Listener {

    private final LavaRisePlugin plugin;

    public KitSelectorGUI(LavaRisePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        open(player, false);
    }

    /** MCPvP-style: picking a kit also drops the player straight into a game. */
    public void openForPlay(Player player) {
        open(player, true);
    }

    private void open(Player player, boolean autoJoin) {
        final KitManager km = plugin.getKitManager();
        if (km == null || !km.hasKits()) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>No kits are configured."));
            return;
        }
        final List<String> order = km.order();
        final int size = Math.max(9, ((order.size() - 1) / 9 + 1) * 9);
        final KitGUIHolder holder = new KitGUIHolder();
        holder.autoJoin = autoJoin;
        final Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(autoJoin ? "Pick a kit to play" : "Select your kit"));
        holder.inventory = inv;

        // Place by index so slots map 1:1 to the kit order (addItem could stack
        // two kits that share an icon, shifting the click mapping).
        for (int i = 0; i < order.size(); i++) {
            final KitManager.Kit kit = km.get(order.get(i));
            final ItemStack item = new ItemStack(kit.icon());
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getMiniMessage().deserialize("<yellow><bold>" + kit.label() + "</bold>"));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof KitGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitGUIHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        final List<String> order = plugin.getKitManager().order();
        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= order.size()) return;

        final String key = order.get(slot);
        plugin.getKitManager().select(player.getUniqueId(), key);
        player.closeInventory();
        player.sendMessage(plugin.getMiniMessage().deserialize(
                "<green>Kit selected: <yellow>" + plugin.getKitManager().get(key).label()));

        // MCPvP flow: the kit pick is the join — drop them straight into a game.
        if (holder.autoJoin && !plugin.getGameManager().isInGame(player)) {
            plugin.getGameManager().quickJoin(player);
        }
    }

    /** Distinct holder so this GUI is never confused with the arena selector. */
    private static final class KitGUIHolder implements InventoryHolder {
        private Inventory inventory;
        private boolean autoJoin;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
