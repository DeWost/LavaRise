package dev.lavarise.feature.gui;

import dev.lavarise.arena.Arena;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ArenaSelectorGUI implements Listener {

    private final LavaRisePlugin plugin;
    private final String title = "LavaRise Arenas";

    public ArenaSelectorGUI(LavaRisePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        LavaRiseGUIHolder holder = new LavaRiseGUIHolder();
        int size = Math.max(9, ((plugin.getArenaRepository().getArenas().size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(holder, size, Component.text(title));
        holder.setInventory(inv);

        for (Arena arena : plugin.getArenaRepository().getArenas()) {
            ItemStack item = new ItemStack(Material.LAVA_BUCKET);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getMiniMessage().deserialize("<red><bold>" + arena.getName() + "</bold></red>"));
                List<Component> lore = new ArrayList<>();
                lore.add(plugin.getMiniMessage().deserialize("<gray>Players: <white>" + arena.getSession().getAliveCount() + "/" + arena.getConfig().maxPlayers()));
                lore.add(plugin.getMiniMessage().deserialize("<gray>State: <white>" + arena.getSession().getCurrentState().getDisplayName()));
                lore.add(Component.empty());
                lore.add(plugin.getMiniMessage().deserialize("<yellow>Click to join!</yellow>"));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LavaRiseGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LavaRiseGUIHolder) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            ItemStack item = event.getCurrentItem();
            ItemMeta meta = item.getItemMeta();
            
            if (meta != null && meta.hasDisplayName()) {
                // Not ideal for i18n, but since we set it explicitly, we parse the raw text
                String arenaName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                
                Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
                if (arena != null) {
                    player.closeInventory();
                    plugin.getGameManager().addPlayerToArena(player, arena);
                }
            }
        }
    }
}
