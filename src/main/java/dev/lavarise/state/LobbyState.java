package dev.lavarise.state;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;

/**
 * Lobby state — waiting for players to join.
 * <p>
 * Transitions to {@link CountdownState} when minimum players reached.
 * Players can freely join and leave.
 * </p>
 */
public final class LobbyState implements GameState {

    private final LavaRisePlugin plugin;
    private final Arena arena;
    private final ArenaSession session;

    public LobbyState(LavaRisePlugin plugin, Arena arena, ArenaSession session) {
        this.plugin = plugin;
        this.arena = arena;
        this.session = session;
    }

    @Override
    public void onEnter() {
        plugin.debug("Arena " + arena.getName() + " entered LOBBY state.");
    }

    @Override
    public void onExit() {
        // Nothing to clean up
    }

    @Override
    public void onPlayerJoin(Player player) {
        // Teleport to lobby spawn
        if (arena.getConfig().lobbySpawn() != null) {
            player.teleport(arena.getConfig().lobbySpawn());
        }

        // Send welcome title
        player.showTitle(Title.title(
                plugin.getMiniMessage().deserialize("<gradient:red:gold><bold>LAVA RISE</bold></gradient>"),
                plugin.getMiniMessage().deserialize("<gray>Waiting for players..."),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));

        // Broadcast join message to arena players
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<green>" + player.getName() + " <gray>joined! <dark_gray>("
                        + session.getAliveCount() + "/" + arena.getConfig().maxPlayers() + ")"
        ));
        
        plugin.getScoreboardModule().setupScoreboard(player, session);
        plugin.getScoreboardModule().updateScoreboard(session);

        // Lobby compass — right-click opens the kit-vote menu. Put it in the
        // player's hand and prompt them so voting is impossible to miss.
        if (plugin.getKitManager() != null && plugin.getKitManager().hasKits()) {
            final ItemStack compass = new ItemStack(Material.COMPASS);
            final ItemMeta meta = compass.getItemMeta();
            if (meta != null) {
                meta.displayName(plugin.getMiniMessage().deserialize(
                        "<gradient:yellow:gold><bold>✦ Kit Voting ✦</bold></gradient>"));
                meta.lore(java.util.List.of(
                        plugin.getMiniMessage().deserialize("<gray>Right-click to <yellow>vote</yellow> for the"),
                        plugin.getMiniMessage().deserialize("<gray>kit the whole lobby will play."),
                        plugin.getMiniMessage().deserialize("<dark_gray>The winning kit is given to everyone.")));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                        org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                compass.setItemMeta(meta);
            }
            player.getInventory().setItem(4, compass);
            player.getInventory().setHeldItemSlot(4);

            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<gold>✦ <yellow>Kit voting is open!</yellow> Right-click the "
                            + "<gold>compass</gold> to pick your loadout."));
            playSound(player, "block.note_block.pling");
        }

        // Check if we have enough players to start countdown
        checkMinPlayers();
    }

    @Override
    public void onPlayerLeave(Player player) {
        plugin.getScoreboardModule().cleanup(player);
        plugin.getScoreboardModule().updateScoreboard(session);
        broadcastToArena(plugin.getMiniMessage().deserialize(
                "<red>" + player.getName() + " <gray>left! <dark_gray>("
                        + session.getAliveCount() + "/" + arena.getConfig().maxPlayers() + ")"
        ));
    }

    @Override
    public void onPlayerEliminated(Player player) {
        // Can't be eliminated in lobby
    }

    @Override
    public boolean isJoinable() {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Waiting";
    }

    // ── Private ─────────────────────────────────────────────

    private void checkMinPlayers() {
        if (session.getAliveCount() >= arena.getConfig().minPlayers()) {
            // Transition to countdown
            session.transitionTo(new CountdownState(plugin, arena, session));
        }
    }

    private void broadcastToArena(Component message) {
        for (var uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    private void playSound(Player player, String sound) {
        try {
            player.playSound(player.getLocation(), sound, 0.7f, 1.4f);
        } catch (Exception ignored) {
            // Invalid sound key — ignore.
        }
    }
}
