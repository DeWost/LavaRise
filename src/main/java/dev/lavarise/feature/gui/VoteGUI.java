package dev.lavarise.feature.gui;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import dev.lavarise.feature.KitManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lobby kit-voting GUI (KteRising-style). Players vote for the kit the whole
 * lobby will play next round; the winning kit (ties broken randomly) is applied
 * to everyone at game start. Vote counts are shown live in each item's lore.
 * <p>
 * The pool is the configured kits plus, when {@code custom-kits.votable} is on,
 * any player-defined custom kit that has been <em>proposed</em> (voted for at
 * least once) — and each viewer always sees their own custom kit so they can
 * propose it. Slot → kit-key mapping is stored on the holder so clicks map
 * correctly regardless of how the pool is composed.
 * </p>
 *
 * @author DeWost
 */
public class VoteGUI implements Listener {

    private static final String CUSTOM_PREFIX = "custom:";

    private final LavaRisePlugin plugin;

    public VoteGUI(LavaRisePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        final ArenaSession session = sessionFor(player);
        if (session == null || !session.isJoinable()) {
            player.sendMessage(mm("<red>You can only vote while waiting in a lobby."));
            return;
        }
        final List<String> keys = buildVoteKeys(session, player);
        if (keys.isEmpty()) {
            player.sendMessage(mm("<red>There are no kits to vote for."));
            return;
        }

        final Map<String, Integer> tally = session.voteTally();
        final int totalVotes = session.voteCount();
        final int leadCount = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        final String myVote = session.getVote(player.getUniqueId());

        final int size = Math.max(9, ((keys.size() - 1) / 9 + 1) * 9);
        final VoteGUIHolder holder = new VoteGUIHolder();
        holder.keys = keys;
        final Inventory inv = Bukkit.createInventory(holder, size,
                mm("<dark_gray>Kit Vote <gray>— <yellow>" + totalVotes + "</yellow> voted"));
        holder.inventory = inv;

        // Place by index, not addItem — addItem would stack two byte-identical
        // icons into one slot and break the slot→key mapping used on click.
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            final int votes = tally.getOrDefault(key, 0);
            final boolean mine = key.equals(myVote);
            final boolean leading = votes > 0 && votes == leadCount;
            inv.setItem(i, buildVoteItem(key, votes, totalVotes, leading, mine));
        }
        player.openInventory(inv);
    }

    /** Config kits + proposed custom kits + the viewer's own custom kit (capped to a double chest). */
    private List<String> buildVoteKeys(ArenaSession session, Player viewer) {
        final List<String> keys = new ArrayList<>();
        final KitManager km = plugin.getKitManager();
        if (km != null) keys.addAll(km.order());

        if (plugin.getConfigManager().isCustomKitsVotable()) {
            // Already-proposed customs (someone has voted for them).
            for (String key : session.voteTally().keySet()) {
                if (key.startsWith(CUSTOM_PREFIX) && !keys.contains(key)) keys.add(key);
            }
            // The viewer's own custom kit — so they can propose it by voting.
            final String mine = CUSTOM_PREFIX + viewer.getUniqueId();
            if (plugin.getCustomKitManager().hasKit(viewer.getUniqueId()) && !keys.contains(mine)) {
                keys.add(mine);
            }
        }
        return keys.size() > 54 ? new ArrayList<>(keys.subList(0, 54)) : keys;
    }

    private ItemStack buildVoteItem(String key, int votes, int totalVotes, boolean leading, boolean mine) {
        final String label;
        final Material icon;
        final List<ItemStack> items;
        if (key.startsWith(CUSTOM_PREFIX)) {
            final UUID owner = parseOwner(key);
            label = ownerName(owner) + "'s Kit";
            icon = Material.CHEST;
            items = owner != null ? plugin.getCustomKitManager().getKit(owner) : List.of();
        } else {
            final KitManager.Kit kit = plugin.getKitManager().get(key);
            label = kit.label();
            icon = kit.icon();
            items = kit.items();
        }

        final ItemStack item = new ItemStack(icon);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm("<yellow><bold>" + label + "</bold>" + (leading ? " <gold>★" : "")));

            final List<Component> lore = new ArrayList<>();
            lore.add(mm("<dark_gray>Loadout:"));
            if (items.isEmpty()) {
                lore.add(mm("<dark_gray>  (empty)"));
            }
            int shown = 0;
            for (ItemStack stack : items) {
                if (shown++ >= 4) {
                    lore.add(mm("<dark_gray>  …and " + (items.size() - 4) + " more"));
                    break;
                }
                lore.add(mm("<gray>  • " + stack.getAmount() + "× " + prettyName(stack.getType().name())));
            }
            lore.add(Component.empty());

            final int pct = totalVotes > 0 ? Math.round(votes * 100f / totalVotes) : 0;
            lore.add(mm("<gray>Votes: <green><bold>" + votes + "</bold></green> <dark_gray>(" + pct + "%)  " + bar(pct)));
            if (leading) {
                lore.add(mm("<gold>★ Currently leading"));
            }
            if (mine) {
                lore.add(mm("<green>✔ Your vote"));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add(mm(key.startsWith(CUSTOM_PREFIX) ? "<yellow>▶ Click to propose & vote!" : "<yellow>▶ Click to vote!"));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof VoteGUIHolder) {
            event.setCancelled(true);
        }
    }

    /**
     * Mandatory voting: while the lobby is still waiting, a player who closes the
     * vote without having voted has it reopened — so they must cast a vote. Stops
     * the instant they vote or the game starts (session no longer joinable).
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof VoteGUIHolder)) return;
        if (!plugin.getConfigManager().isMandatoryVoting()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            final ArenaSession session = sessionFor(player);
            // Only enforce during the COUNTDOWN (the actual vote window) — never
            // during the open-ended lobby wait, which could trap a player who
            // merely peeked at the vote while the lobby is still filling.
            if (session != null
                    && session.getCurrentState() instanceof dev.lavarise.state.CountdownState
                    && session.getVote(player.getUniqueId()) == null) {
                open(player);
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof VoteGUIHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        final int slot = event.getRawSlot();
        if (holder.keys == null || slot < 0 || slot >= holder.keys.size()) return;

        final ArenaSession session = sessionFor(player);
        if (session == null || !session.isJoinable()) {
            player.closeInventory();
            return;
        }
        final String key = holder.keys.get(slot);
        session.voteKit(player.getUniqueId(), key);
        player.sendMessage(mm("<green>You voted for <yellow>" + voteLabel(key) + "</yellow>!"));
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

    private String voteLabel(String key) {
        if (key.startsWith(CUSTOM_PREFIX)) return ownerName(parseOwner(key)) + "'s Kit";
        final KitManager.Kit kit = plugin.getKitManager().get(key);
        return kit != null ? kit.label() : key;
    }

    private UUID parseOwner(String key) {
        try {
            return UUID.fromString(key.substring(CUSTOM_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String ownerName(UUID owner) {
        if (owner == null) return "Someone";
        final Player online = plugin.getServer().getPlayer(owner);
        if (online != null) return online.getName();
        final String name = plugin.getServer().getOfflinePlayer(owner).getName();
        return name != null ? name : "Someone";
    }

    private Component mm(String s) {
        return plugin.getMiniMessage().deserialize(s);
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
        private List<String> keys;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
