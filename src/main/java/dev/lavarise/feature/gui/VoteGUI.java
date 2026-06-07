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
import org.bukkit.event.inventory.InventoryDragEvent;
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
        final int totalVotes = session.voteCount();
        final int leadCount = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        final String myVote = session.getVote(player.getUniqueId());

        final int size = Math.max(9, ((order.size() - 1) / 9 + 1) * 9);
        final VoteGUIHolder holder = new VoteGUIHolder();
        final Inventory inv = Bukkit.createInventory(holder, size,
                plugin.getMiniMessage().deserialize(
                        "<dark_gray>Kit Vote <gray>— <yellow>" + totalVotes + "</yellow> voted"));
        holder.inventory = inv;

        for (String key : order) {
            final KitManager.Kit kit = km.get(key);
            final int votes = tally.getOrDefault(key, 0);
            final boolean mine = key.equals(myVote);
            final boolean leading = votes > 0 && votes == leadCount;

            final ItemStack item = new ItemStack(kit.icon());
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getMiniMessage().deserialize(
                        "<yellow><bold>" + kit.label() + "</bold>" + (leading ? " <gold>★" : "")));

                final List<Component> lore = new java.util.ArrayList<>();
                lore.add(plugin.getMiniMessage().deserialize("<dark_gray>Loadout:"));
                int shown = 0;
                for (ItemStack stack : kit.items()) {
                    if (shown++ >= 4) {
                        lore.add(plugin.getMiniMessage().deserialize(
                                "<dark_gray>  …and " + (kit.items().size() - 4) + " more"));
                        break;
                    }
                    lore.add(plugin.getMiniMessage().deserialize(
                            "<gray>  • " + stack.getAmount() + "× " + prettyName(stack.getType().name())));
                }
                lore.add(Component.empty());

                final int pct = totalVotes > 0 ? Math.round(votes * 100f / totalVotes) : 0;
                lore.add(plugin.getMiniMessage().deserialize(
                        "<gray>Votes: <green><bold>" + votes + "</bold></green> "
                                + "<dark_gray>(" + pct + "%)  " + bar(pct)));
                if (leading) {
                    lore.add(plugin.getMiniMessage().deserialize("<gold>★ Currently leading"));
                }
                if (mine) {
                    lore.add(plugin.getMiniMessage().deserialize("<green>✔ Your vote"));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add(plugin.getMiniMessage().deserialize("<yellow>▶ Click to vote!"));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof VoteGUIHolder) {
            event.setCancelled(true);
        }
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
        try {
            player.playSound(player.getLocation(), "ui.button.click", 0.7f, 1.6f);
        } catch (Exception ignored) {
            // Invalid sound key — ignore.
        }
        // Reopen next tick to show the updated tally.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player);
        });
    }

    private ArenaSession sessionFor(Player player) {
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        return arena == null ? null : arena.getSession();
    }

    /** "NETHERITE_PICKAXE" → "Netherite Pickaxe". */
    private static String prettyName(String raw) {
        final String[] parts = raw.toLowerCase(java.util.Locale.ROOT).split("_");
        final StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /** A 10-segment progress bar for a percentage. */
    private static String bar(int pct) {
        final int filled = Math.round(pct / 10f);
        final StringBuilder sb = new StringBuilder("<green>");
        for (int i = 0; i < 10; i++) {
            if (i == filled) sb.append("<dark_gray>");
            sb.append('|');
        }
        return sb.toString();
    }

    private static final class VoteGUIHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
