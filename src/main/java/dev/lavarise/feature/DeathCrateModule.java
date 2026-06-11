package dev.lavarise.feature;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death Crates — battle-royale tombstones that capture a dead player's drops
 * into a chest placed at (or near) their death location. The chest persists for
 * a configurable number of seconds, then despawns. All crates for an arena are
 * also removed when the game ends so none leak into the next match.
 *
 * <p>Config path: {@code gameplay.death-crates} with keys
 * {@code enabled}, {@code despawn} (seconds), and {@code glow}.</p>
 *
 * @author DeWost
 */
public final class DeathCrateModule implements Listener {

    private final LavaRisePlugin plugin;

    private boolean enabled;
    private int despawnSeconds;
    private boolean glow;

    /**
     * Per-arena set of active chest locations; keyed by arena name.
     * Concurrent because the despawn task fires on the main thread but
     * cleanup() may be called from any state-transition path.
     */
    private final Map<String, Set<Location>> activeCrates = new ConcurrentHashMap<>();

    /**
     * Construct and load config.
     *
     * @param plugin the LavaRise plugin instance
     */
    public DeathCrateModule(final LavaRisePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** (Re)load config — safe to call from {@code /lr reload}. */
    public void load() {
        final ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("gameplay.death-crates");
        if (sec == null) {
            enabled = false;
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        despawnSeconds = Math.max(5, sec.getInt("despawn", 30));
        glow = sec.getBoolean("glow", true);
    }

    /**
     * On player death during a running LavaRise game (non-keep-inventory arena
     * with item drops), capture those drops into a chest tombstone.
     *
     * @param event the death event (HIGH priority so kill-credit in PlayerListener
     *              runs at NORMAL first, this module fires after)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }
        final Player player = event.getEntity();
        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        if (arena == null) {
            return;
        }
        final ArenaSession session = arena.getSession();
        if (session == null || !session.getCurrentState().isGameRunning()) {
            return;
        }
        // Honour keep-inventory arenas — no crate when items aren't dropped.
        if (arena.getConfig().keepInventory()) {
            return;
        }
        final List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }

        // Copy the drops before clearing so we hold them for the chest.
        final List<ItemStack> loot = new ArrayList<>(drops);
        drops.clear();

        try {
            placeCrate(player, arena, session, loot);
        } catch (Throwable t) {
            // Never let crate placement crash the death event.
            plugin.getLogger().warning("[DeathCrateModule] Crate placement failed for "
                    + player.getName() + ": " + t.getMessage());
        }
    }

    /**
     * Place the death-crate chest and schedule its despawn.
     *
     * @param player  the dead player (for coordinates and broadcast name)
     * @param arena   the arena the game is running in
     * @param session the current game session
     * @param loot    the items to place in the chest
     */
    private void placeCrate(final Player player, final Arena arena,
                            final ArenaSession session, final List<ItemStack> loot) {
        final ArenaConfig c = arena.getConfig();
        final World world = c.world();
        if (world == null) {
            return;
        }

        final Location deathLoc = player.getLocation().clone();
        final Location crateLoc = findSafeLocation(deathLoc, session, c, world);
        if (crateLoc == null) {
            // Could not find a safe spot — drop items back so they don't vanish.
            for (final ItemStack item : loot) {
                if (item != null) {
                    world.dropItemNaturally(deathLoc, item);
                }
            }
            return;
        }

        final Block block = world.getBlockAt(crateLoc);
        block.setType(Material.CHEST, false);

        if (block.getState() instanceof Chest chest) {
            for (final ItemStack item : loot) {
                if (item != null) {
                    chest.getInventory().addItem(item);
                }
            }
            chest.update();
        }

        // Track this crate for the arena so we can bulk-clean on game end.
        final Set<Location> tracked =
                activeCrates.computeIfAbsent(arena.getName(), k -> ConcurrentHashMap.newKeySet());
        tracked.add(crateLoc);

        // Optional glow: spawn SOUL_FIRE_FLAME particles once above the crate.
        if (glow) {
            try {
                world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME,
                        crateLoc.clone().add(0.5, 1.0, 0.5), 30, 0.3, 0.5, 0.3, 0.02);
            } catch (Throwable ignored) {
                // Particle failure is cosmetic — never abort.
            }
        }

        // Announce to all players in the arena.
        broadcastToArena(session,
                "<gold>⚰ <yellow>" + player.getName() + "</yellow>'s loot crate dropped!");

        // Schedule despawn.
        final Location finalLoc = crateLoc;
        final String arenaName = arena.getName();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removeCrate(world, finalLoc, arenaName);
        }, despawnSeconds * 20L);
    }

    /**
     * Find a safe block location for the crate near the death spot.
     * If the death block is lava, air-over-void, or below the current lava
     * surface, relocate to just above the lava — mirroring {@code dropSupply()} in
     * {@code ActiveState}.
     *
     * @param deathLoc the raw death location
     * @param session  used to read the current lava Y
     * @param c        arena config for bounds and lava max
     * @param world    the world
     * @return a safe {@link Location} or {@code null} if one cannot be found
     */
    private Location findSafeLocation(final Location deathLoc, final ArenaSession session,
                                      final ArenaConfig c, final World world) {
        final int deathY = deathLoc.getBlockY();
        final int lavaY = session.getCurrentLavaY();
        final Material deathMaterial = world.getBlockAt(deathLoc).getType();

        // Use the death block directly if it is solid and above the lava.
        if (deathY > lavaY
                && deathMaterial != Material.LAVA
                && !deathMaterial.isAir()) {
            return deathLoc.clone();
        }

        // Otherwise, pick a spot just above the lava inside the arena bounds.
        final int safeY = Math.min(c.lavaMaxY() - 1, lavaY + 2);
        final int x = Math.max(c.minX(), Math.min(c.maxX(), deathLoc.getBlockX()));
        final int z = Math.max(c.minZ(), Math.min(c.maxZ(), deathLoc.getBlockZ()));
        final Location candidate = new Location(world, x, safeY, z);

        // Walk up until we find a non-lava block (or hit the bounds ceiling).
        for (int offset = 0; offset <= 5; offset++) {
            final Location check = candidate.clone().add(0, offset, 0);
            if (check.getBlockY() >= c.lavaMaxY()) {
                break;
            }
            final Material mat = world.getBlockAt(check).getType();
            if (mat != Material.LAVA) {
                return check;
            }
        }

        // Last resort: just above the lava at the clamped X/Z.
        final Location lastResort = new Location(world, x, safeY, z);
        if (lastResort.getBlockY() < c.lavaMaxY()) {
            return lastResort;
        }
        return null;
    }

    /**
     * Remove a single crate block and stop tracking it.
     *
     * @param world     the world containing the crate
     * @param loc       the chest's location
     * @param arenaName the arena name for tracking removal
     */
    private void removeCrate(final World world, final Location loc, final String arenaName) {
        try {
            if (world != null && world.getBlockAt(loc).getType() == Material.CHEST) {
                world.getBlockAt(loc).setType(Material.AIR, false);
            }
        } catch (Throwable ignored) {
            // Chunk unloaded or already cleared — fine.
        }
        final Set<Location> tracked = activeCrates.get(arenaName);
        if (tracked != null) {
            tracked.remove(loc);
        }
    }

    /**
     * Remove ALL crates for a session. Called by
     * {@code ActiveState.cleanupSessionEffects()} on every game-end path so
     * no chest leaks into the next match or the reset pass.
     *
     * @param session the ending game session
     */
    public void cleanup(final ArenaSession session) {
        if (session == null) {
            return;
        }
        final String arenaName = session.getArena().getName();
        final Set<Location> tracked = activeCrates.remove(arenaName);
        if (tracked == null) {
            return;
        }
        for (final Location loc : tracked) {
            try {
                final World world = loc.getWorld();
                if (world != null && world.getBlockAt(loc).getType() == Material.CHEST) {
                    world.getBlockAt(loc).setType(Material.AIR, false);
                }
            } catch (Throwable ignored) {
                // Best-effort; chunk may be unloaded.
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private void broadcastToArena(final ArenaSession session, final String miniMessage) {
        final net.kyori.adventure.text.Component msg =
                plugin.getMiniMessage().deserialize(miniMessage);
        for (final UUID uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }
}
