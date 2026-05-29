package dev.lavarise.feature.gui;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.feature.KitManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Lobby kit-voting GUI (KteRising-style). Players vote for the kit the whole
 * lobby will play next round; the winning kit (ties broken randomly) is applied
 * to everyone at game start. Vote counts are shown live in each item's lore.
 *
 * @author DeWost
 */
public class VoteGUI implements Listener {

    private final LavaRisePlugin plugin;

    public VoteGUI(LavaRisePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        final ArenaSession session = sessionFor(player);
        if (session == null || !session.isJoinable()) {
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>You can only vote while waiting in a lobby."));
            return;
        }
        final KitManager km = plugin.getKitManager();
        if (km == null || !km.hasKits()) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>There are no kits to vote for."));
            return;
        }

        final List<String> order = km.order();
        final Map<String, Integer> tally = session.voteTally();
        final int size = Math.max(9, ((order.size() - 1) / 9 + 1) * 9);
        final VoteGUIHolder holder = new VoteGUIHolder();
        final Inventory inv = Bukkit.createInventory(holder, size, Component.text("Vote for a kit"));
        holder.inventory = inv;

        for (String key : order) {
            final KitManager.Kit kit = km.get(key);
            final ItemStack item = new ItemStack(kit.icon());
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getMiniMessage().deserialize("<yellow><bold>" + kit.label() + "</bold>"));
                meta.lore(List.of(
                        plugin.getMiniMessage().deserialize("<gray>Votes: <green>" + tally.getOrDefault(key, 0)),
                        plugin.getMiniMessage().deserialize("<yellow>Click to vote!")));
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof VoteGUIHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        final List<String> order = plugin.getKitManager().order();
        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= order.size()) return;

        final ArenaSession session = sessionFor(player);
        if (session == null || !session.isJoinable()) {
            player.closeInventory();
            return;
        }
        final String key = order.get(slot);
        session.voteKit(player.getUniqueId(), key);
        player.sendMessage(plugin.getMiniMessage().deserialize(
                "<green>You voted for <yellow>" + plugin.getKitManager().get(key).label() + "</yellow>!"));
        // Reopen next tick to show the updated tally.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player);
        });
    }

    private ArenaSession sessionFor(Player player) {
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        return arena == null ? null : arena.getSession();
    }

    private static final class VoteGUIHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
