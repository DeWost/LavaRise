package dev.lavarise.feature.gui;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.state.ActiveState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /lr admin} arena control panel. One item per arena, colour-coded by
 * state; left/right/shift clicks force-start, stop, freeze, or open a delete
 * confirmation — each reusing the exact same code paths as the matching
 * {@code /lr} admin command. Refreshes after every action so state stays live.
 *
 * @author DeWost
 */
public class AdminGUI implements Listener {

    private final LavaRisePlugin plugin;
    private final NamespacedKey arenaKey;
    private final NamespacedKey actionKey;

    public AdminGUI(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.arenaKey = new NamespacedKey(plugin, "admin_arena");
        this.actionKey = new NamespacedKey(plugin, "admin_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        final List<Arena> arenas = new ArrayList<>(plugin.getArenaRepository().getArenas());
        final AdminGUIHolder holder = new AdminGUIHolder(null);
        final int rows = Math.min(5, Math.max(1, (arenas.size() / 9) + 1));
        final int size = (rows + 1) * 9;
        final Inventory inv = Bukkit.createInventory(holder, size,
                plugin.getMiniMessage().deserialize("<dark_red>LavaRise Admin Panel"));
        holder.setInventory(inv);

        if (arenas.isEmpty()) {
            inv.setItem(0, named(Material.BARRIER,
                    "<red>No arenas configured", List.of("<gray>Create one with <yellow>/lr setup <name>"), null, null));
        } else {
            int slot = 0;
            for (Arena arena : arenas) {
                if (slot >= rows * 9) {
                    break;
                }
                inv.setItem(slot++, arenaItem(arena));
            }
        }

        inv.setItem(size - 5, named(Material.CLOCK, "<aqua>Refresh", List.of("<gray>Reload the panel"), null, "refresh"));
        inv.setItem(size - 1, named(Material.BARRIER, "<red>Close", List.of(), null, "close"));
        player.openInventory(inv);
    }

    private void openConfirm(Player player, String arenaName) {
        final AdminGUIHolder holder = new AdminGUIHolder(arenaName);
        final Inventory inv = Bukkit.createInventory(holder, 9,
                plugin.getMiniMessage().deserialize("<dark_red>Delete '" + arenaName + "'?"));
        holder.setInventory(inv);
        inv.setItem(2, named(Material.LIME_CONCRETE, "<green><bold>Confirm Delete",
                List.of("<gray>Permanently delete <yellow>" + arenaName), null, "confirm"));
        inv.setItem(6, named(Material.RED_CONCRETE, "<red><bold>Cancel",
                List.of("<gray>Back to the panel"), null, "cancel"));
        player.openInventory(inv);
    }

    private ItemStack arenaItem(Arena arena) {
        final ArenaSession session = arena.getSession();
        final String state = session != null ? session.getCurrentState().getDisplayName() : "—";
        final Material mat = materialFor(session);
        final int alive = session != null ? session.getAliveCount() : 0;
        final int lava = session != null ? session.getLavaHeight() : 0;
        final List<String> lore = new ArrayList<>();
        lore.add("<gray>State: <white>" + state);
        lore.add("<gray>Players: <yellow>" + alive + "<gray>/<yellow>" + arena.getConfig().maxPlayers());
        lore.add("<gray>Lava height: <red>" + lava);
        lore.add("<gray>Mode: <gold>" + arena.getConfig().gameMode());
        lore.add("");
        lore.add("<green>Left <gray>▸ force-start");
        lore.add("<yellow>Right <gray>▸ stop & reset");
        lore.add("<aqua>Shift-Left <gray>▸ freeze/resume lava");
        lore.add("<red>Shift-Right <gray>▸ delete");
        return named(mat, "<white><bold>" + arena.getName(), lore, arena.getName(), null);
    }

    private Material materialFor(ArenaSession session) {
        if (session == null) {
            return Material.GRAY_CONCRETE;
        }
        if (session.getCurrentState().isGameRunning()) {
            return Material.RED_CONCRETE;
        }
        if (session.getCurrentState().isJoinable()) {
            return Material.LIME_CONCRETE;
        }
        if (session.getCurrentState().getDisplayName().startsWith("Ending")) {
            return Material.GRAY_CONCRETE;
        }
        return Material.ORANGE_CONCRETE;
    }

    private ItemStack named(Material mat, String name, List<String> lore, String arenaTag, String actionTag) {
        final ItemStack item = new ItemStack(mat);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize("<!italic>" + name));
            final List<Component> lines = new ArrayList<>();
            for (String l : lore) {
                lines.add(plugin.getMiniMessage().deserialize("<!italic>" + l));
            }
            meta.lore(lines);
            if (arenaTag != null) {
                meta.getPersistentDataContainer().set(arenaKey, PersistentDataType.STRING, arenaTag);
            }
            if (actionTag != null) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionTag);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminGUIHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("lavarise.admin")) {
            return;
        }
        final ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        final var pdc = item.getItemMeta().getPersistentDataContainer();
        final String action = pdc.get(actionKey, PersistentDataType.STRING);
        final String arenaName = pdc.get(arenaKey, PersistentDataType.STRING);

        if (action != null) {
            handleAction(player, holder, action);
            return;
        }
        if (arenaName != null) {
            handleArenaClick(player, arenaName, event.getClick());
        }
    }

    private void handleAction(Player player, AdminGUIHolder holder, String action) {
        switch (action) {
            case "refresh" -> open(player);
            case "close" -> player.closeInventory();
            case "cancel" -> open(player);
            case "confirm" -> {
                final String arena = holder.getConfirmArena();
                if (arena != null && plugin.getArenaRepository().getArena(arena).isPresent()) {
                    plugin.getArenaRepository().deleteArena(arena);
                    msg(player, "<red>Deleted arena <yellow>" + arena + "</yellow>.");
                }
                open(player);
            }
            default -> { /* unknown */ }
        }
    }

    private void handleArenaClick(Player player, String arenaName, ClickType click) {
        final Arena arena = plugin.getArenaRepository().getArena(arenaName).orElse(null);
        if (arena == null) {
            msg(player, "<red>Arena no longer exists.");
            open(player);
            return;
        }
        final ArenaSession session = arena.getSession();

        if (click == ClickType.SHIFT_RIGHT) {
            openConfirm(player, arenaName);
            return;
        }
        if (click == ClickType.LEFT) { // force-start
            if (session == null || session.getCurrentState().isGameRunning()) {
                msg(player, "<red>That game is already running.");
            } else if (session.getAliveCount() < 1) {
                msg(player, "<red>No players in that arena.");
            } else {
                session.transitionTo(new ActiveState(plugin, arena, session));
                msg(player, "<green>Force-started <yellow>" + arenaName + "</yellow>.");
            }
        } else if (click == ClickType.RIGHT) { // stop & reset
            if (session != null) {
                session.forceEnd();
            }
            arena.destroySession();
            arena.createSession();
            msg(player, "<green>Stopped & reset <yellow>" + arenaName + "</yellow>.");
        } else if (click == ClickType.SHIFT_LEFT) { // freeze/resume
            if (session == null || !session.getCurrentState().isGameRunning()) {
                msg(player, "<red>That game is not running.");
            } else {
                final boolean frozen = !session.isPaused();
                session.setPaused(frozen);
                msg(player, frozen ? "<aqua>❄ Lava frozen in <yellow>" + arenaName
                        : "<green>🔥 Lava resumed in <yellow>" + arenaName);
            }
        }
        open(player); // refresh so the new state shows
    }

    private void msg(Player player, String mini) {
        player.sendMessage(plugin.getMiniMessage().deserialize(mini));
    }
}
