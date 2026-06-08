package dev.lavarise.party;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ProceduralArenaFactory;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Solo matchmaking queue. Players {@code /lr queue}; once enough are waiting,
 * they are dropped together into an open arena (or a fresh procedural one).
 *
 * @author DeWost
 */
public class QueueManager {

    private final LavaRisePlugin plugin;
    private final Set<UUID> queue = new LinkedHashSet<>();

    public QueueManager(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isQueued(UUID uuid) {
        return queue.contains(uuid);
    }

    public int size() {
        return queue.size();
    }

    /** Remove a player from the queue (e.g. on quit or /lr queue leave). */
    public boolean remove(UUID uuid) {
        return queue.remove(uuid);
    }

    /**
     * Add a player to the queue and attempt to form a match. Returns the new
     * queue size, or -1 if they were already queued.
     */
    public int enqueue(Player player) {
        if (!queue.add(player.getUniqueId())) return -1;
        tryFormMatch();
        return queue.size();
    }

    /** If enough players are waiting, drop them into an arena together. */
    public void tryFormMatch() {
        final int min = plugin.getConfigManager().getQueueMinPlayers();
        if (queue.size() < min) return;

        final var cfg = plugin.getConfigManager();
        Arena arena = plugin.getGameManager().findRandomAvailableArena().orElse(null);
        if (arena == null) {
            if (!cfg.isProceduralEnabled()) return; // nowhere to put them yet
            arena = ProceduralArenaFactory.create(plugin);
        }

        final int capacity = arena.getConfig().maxPlayers();
        final List<UUID> batch = new ArrayList<>(queue);
        int placed = 0;
        for (UUID id : batch) {
            if (placed >= capacity) break;
            final Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline()) { queue.remove(id); continue; }
            if (plugin.getGameManager().addPlayerToArena(p, arena)) {
                queue.remove(id);
                placed++;
                p.sendMessage(plugin.getMiniMessage().deserialize(
                        "<green>⚔ Match found — joined <yellow>" + arena.getName() + "</yellow>!"));
            }
        }
    }
}
