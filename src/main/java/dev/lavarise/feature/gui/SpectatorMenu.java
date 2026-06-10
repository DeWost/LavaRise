package dev.lavarise.feature.gui;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Spectator teleport menu — a chest GUI showing every alive player in the
 * spectator's arena as a player head. Clicking a head teleports the spectator
 * to that player and closes the menu. A refresh item re-opens the menu with
 * the current player list.
 * <p>
 * Only spectators of the same arena may use the menu; both the spectator
 * status and the target's alive status are re-validated on every click.
 * </p>
 *
 * @author DeWost
 */
public final class SpectatorMenu implements Listener {

    private final LavaRisePlugin plugin;
    /** PDC key storing the target alive player's UUID string on each head item. */
    private final NamespacedKey targetKey;
    /** PDC key marking the refresh button. */
    private final NamespacedKey actionKey;

    public SpectatorMenu(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.targetKey = new NamespacedKey(plugin, "spectator_target");
        this.actionKey = new NamespacedKey(plugin, "spectator_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Public API ──────────────────────────────────────────

    /**
     * Open the spectator teleport menu for {@code spectator}. Does nothing if the
     * config toggle is off, if the player is not an in-game spectator, or if the
     * game is no longer running.
     */
    public void open(Player spectator) {
        if (!plugin.getConfigManager().isSpectatorMenuEnabled()) {
            return;
        }
        final Arena arena = plugin.getGameManager().getArenaForPlayer(spectator.getUniqueId());
        if (arena == null) {
            return;
        }
        final ArenaSession session = arena.getSession();
        if (session == null || !session.isSpectator(spectator.getUniqueId())
                || !session.getCurrentState().isGameRunning()) {
            return;
        }
        buildAndOpen(spectator, session);
    }

    // ── GUI construction ────────────────────────────────────

    private void buildAndOpen(Player spectator, ArenaSession session) {
        final Set<UUID> alive = session.getAlivePlayers();
        // Size: rows of 9 for the heads + 1 utility row, capped at 54 (6 rows).
        final int headSlots = alive.size();
        final int rows = Math.min(6, Math.max(2, (int) Math.ceil((headSlots + 9) / 9.0)));
        final int size = rows * 9;

        final SpectatorMenuHolder holder = new SpectatorMenuHolder(spectator.getUniqueId());
        final Inventory inv = Bukkit.createInventory(holder, size,
                plugin.getMiniMessage().deserialize("<!italic><dark_gray>Spectating <gray>— choose a player"));
        holder.setInventory(inv);

        if (alive.isEmpty()) {
            inv.setItem(13, named(Material.BARRIER,
                    "<red>No players to spectate",
                    List.of("<gray>All players have been eliminated."),
                    null, null));
        } else {
            int slot = 0;
            for (UUID uuid : alive) {
                if (slot >= size - 9) {
                    break; // leave the last row for utility items
                }
                final Player target = plugin.getServer().getPlayer(uuid);
                if (target == null || !target.isOnline()) {
                    continue;
                }
                inv.setItem(slot++, headItem(target, session));
            }
        }

        // Utility row: refresh on the left, close on the right.
        final int utilRow = size - 9;
        inv.setItem(utilRow + 4, named(Material.CLOCK,
                "<aqua>Refresh",
                List.of("<gray>Reload the player list"),
                null, "refresh"));
        inv.setItem(utilRow + 8, named(Material.BARRIER,
                "<red>Close",
                List.of(),
                null, "close"));

        spectator.openInventory(inv);
    }

    // ── Item builders ───────────────────────────────────────

    private ItemStack headItem(Player target, ArenaSession session) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            meta.displayName(plugin.getMiniMessage().deserialize(
                    "<!italic><yellow>" + target.getName()));
            final List<Component> lore = new ArrayList<>();
            final int kills = session.getSessionKills(target.getUniqueId());
            lore.add(plugin.getMiniMessage().deserialize(
                    "<!italic><gray>Kills this match: <red>" + kills));
            lore.add(plugin.getMiniMessage().deserialize(
                    "<!italic><dark_gray>Click to teleport"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(
                    targetKey, PersistentDataType.STRING, target.getUniqueId().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack named(Material mat, String name, List<String> lore,
                             String targetTag, String actionTag) {
        final ItemStack item = new ItemStack(mat);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize("<!italic>" + name));
            final List<Component> lines = new ArrayList<>();
            for (String l : lore) {
                lines.add(plugin.getMiniMessage().deserialize("<!italic>" + l));
            }
            meta.lore(lines);
            if (targetTag != null) {
                meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, targetTag);
            }
            if (actionTag != null) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionTag);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Event handlers ──────────────────────────────────────

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SpectatorMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpectatorMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player spectator)) {
            return;
        }

        final ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        final var pdc = item.getItemMeta().getPersistentDataContainer();

        // ── Utility actions ──────────────────────────────
        final String action = pdc.get(actionKey, PersistentDataType.STRING);
        if (action != null) {
            switch (action) {
                case "refresh" -> open(spectator);
                case "close"   -> spectator.closeInventory();
                default        -> { /* unknown */ }
            }
            return;
        }

        // ── Teleport to target ───────────────────────────
        final String targetUuidStr = pdc.get(targetKey, PersistentDataType.STRING);
        if (targetUuidStr == null) {
            return;
        }

        // Re-validate that the spectator is still in the arena and a spectator.
        final Arena arena = plugin.getGameManager().getArenaForPlayer(spectator.getUniqueId());
        if (arena == null) {
            spectator.closeInventory();
            return;
        }
        final ArenaSession session = arena.getSession();
        if (session == null || !session.isSpectator(spectator.getUniqueId())) {
            spectator.closeInventory();
            return;
        }

        // Re-validate that the target is still alive.
        final UUID targetUuid;
        try {
            targetUuid = UUID.fromString(targetUuidStr);
        } catch (IllegalArgumentException ignored) {
            return;
        }

        if (!session.isAlive(targetUuid)) {
            // Target eliminated since the menu was opened — refresh.
            spectator.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>That player was just eliminated! Refreshing…"));
            open(spectator);
            return;
        }

        final Player target = plugin.getServer().getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            spectator.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>That player went offline. Refreshing…"));
            open(spectator);
            return;
        }

        spectator.teleport(target.getLocation());
        spectator.closeInventory();
    }
}
