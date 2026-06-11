package dev.lavarise.feature;

import dev.lavarise.arena.Arena;
import dev.lavarise.arena.ArenaConfig;
import dev.lavarise.arena.ArenaSession;
import dev.lavarise.core.LavaRisePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sky Loot Chests — tiered loot chests scattered at increasing heights inside
 * the arena. Higher altitude = better loot, rewarding players who climb to
 * survive the rising lava. Chests are placed once when a game starts and
 * cleared (set to AIR) when the game ends.
 *
 * <p>Configuration path: {@code gameplay.sky-loot} in {@code config.yml}.</p>
 *
 * @author DeWost
 */
public final class SkyLootModule {

    private final LavaRisePlugin plugin;

    /** Whether the module is enabled in config. */
    private boolean enabled;

    /** Whether to show a brief particle glow above each chest. */
    private boolean glowEnabled;

    /** Loaded tier definitions. */
    private final List<Tier> tiers = new ArrayList<>();

    /**
     * Tracks all chest + platform locations placed for each arena session
     * (keyed by arena name).  Both types live in the same set so a single loop
     * in {@link #clear(ArenaSession)} handles both.
     */
    private final Map<String, Set<Location>> placed = new ConcurrentHashMap<>();

    public SkyLootModule(final LavaRisePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Config loading ─────────────────────────────────────────────────────────

    /**
     * (Re)load the module config from {@code gameplay.sky-loot}.
     * Safe to call on plugin reload — clears and re-populates the tier list.
     */
    public void load() {
        tiers.clear();
        final org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();

        this.enabled = cfg.getBoolean("gameplay.sky-loot.enabled", true);
        this.glowEnabled = cfg.getBoolean("gameplay.sky-loot.glow", true);

        final ConfigurationSection tiersSection = cfg.getConfigurationSection("gameplay.sky-loot.tiers");
        if (tiersSection == null) {
            plugin.debug("SkyLootModule: no tiers section found — module disabled.");
            return;
        }

        for (final String key : tiersSection.getKeys(false)) {
            final ConfigurationSection ts = tiersSection.getConfigurationSection(key);
            if (ts == null) {
                continue;
            }
            final String name = ts.getString("name", "<white>" + key);
            final int count = Math.max(1, ts.getInt("count", 1));
            final int minHeight = ts.getInt("min-height", 5);
            final int maxHeight = Math.max(minHeight + 1, ts.getInt("max-height", minHeight + 10));
            final List<String> itemEntries = ts.getStringList("items");
            tiers.add(new Tier(name, count, minHeight, maxHeight, itemEntries));
        }

        plugin.debug("SkyLootModule: loaded " + tiers.size() + " tier(s), enabled=" + enabled);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** @return true when the module is enabled in config. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Place all tiered sky loot chests for the given arena and session.
     * Called once from {@code ActiveState.onEnter()} after players are teleported in.
     * Every failure is caught so a bad config entry can never abort game start.
     *
     * @param arena   the arena whose bounds / lava range constrain placement
     * @param session the current game session (used as the placement key)
     */
    public void placeChests(final Arena arena, final ArenaSession session) {
        if (!enabled || tiers.isEmpty()) {
            return;
        }
        try {
            doPlaceChests(arena, session);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SkyLoot] Placement failed for arena "
                    + arena.getName() + ": " + t.getMessage());
        }
    }

    /**
     * Remove every chest and platform block placed for this session and drop
     * the tracking entry.  Idempotent — safe to call even if placement never
     * ran or the session was never keyed.
     *
     * @param session the session whose blocks should be cleared
     */
    public void clear(final ArenaSession session) {
        final String key = session.getArena().getName();
        final Set<Location> locs = placed.remove(key);
        if (locs == null) {
            return;
        }
        for (final Location loc : locs) {
            try {
                if (loc.getWorld() != null) {
                    loc.getBlock().setType(Material.AIR, false);
                }
            } catch (Throwable ignored) {
                // Chunk may be unloaded — silently skip.
            }
        }
    }

    // ── Internal placement logic ───────────────────────────────────────────────

    private void doPlaceChests(final Arena arena, final ArenaSession session) {
        final ArenaConfig c = arena.getConfig();
        final World world = c.world();
        final int lavaStart = c.lavaStartY();
        final int lavaMax = c.lavaMaxY();
        final ThreadLocalRandom rng = ThreadLocalRandom.current();

        final String arenaKey = arena.getName();
        final Set<Location> locations = ConcurrentHashMap.newKeySet();
        placed.put(arenaKey, locations);

        int totalPlaced = 0;

        for (final Tier tier : tiers) {
            for (int i = 0; i < tier.count(); i++) {
                try {
                    // Random X/Z within arena bounds.
                    final int x = rng.nextInt(c.minX(), c.maxX() + 1);
                    final int z = rng.nextInt(c.minZ(), c.maxZ() + 1);

                    // Y = lavaStartY + random offset in [minHeight, maxHeight),
                    // clamped so the chest is never at or above lavaMaxY-1.
                    final int rawY = lavaStart + tier.minHeight()
                            + rng.nextInt(tier.maxHeight() - tier.minHeight() + 1);
                    final int y = Math.min(lavaMax - 2, rawY); // -2 keeps platform + chest inside

                    // Place a stone platform block directly under the chest.
                    final Location platformLoc = new Location(world, x, y, z);
                    final Block platform = world.getBlockAt(platformLoc);
                    platform.setType(Material.STONE, false);
                    locations.add(platformLoc.clone());

                    // Place the chest one block above the platform.
                    final Location chestLoc = new Location(world, x, y + 1, z);
                    final Block chestBlock = world.getBlockAt(chestLoc);
                    chestBlock.setType(Material.CHEST, false);
                    locations.add(chestLoc.clone());

                    // Fill the chest with the tier's loot.
                    if (chestBlock.getState() instanceof org.bukkit.block.Chest chest) {
                        for (final String entry : tier.items()) {
                            final ItemStack item = parseItem(entry);
                            if (item != null) {
                                chest.getInventory().addItem(item);
                            }
                        }
                        chest.update();
                    }

                    // Optional: brief particle glow column above the chest.
                    if (glowEnabled) {
                        spawnGlowParticles(world, chestLoc);
                    }

                    totalPlaced++;
                } catch (Throwable t) {
                    plugin.getLogger().warning("[SkyLoot] Failed to place chest for tier '"
                            + tier.name() + "': " + t.getMessage());
                }
            }
        }

        if (totalPlaced > 0) {
            // Announce once to the whole arena.
            final net.kyori.adventure.text.Component msg = plugin.getMiniMessage().deserialize(
                    "<gold>📦 Loot chests have appeared — climb higher for better loot!");
            for (final java.util.UUID uuid : session.getAllPlayerIds()) {
                final org.bukkit.entity.Player p = plugin.getServer().getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(msg);
                }
            }
        }

        plugin.debug("SkyLoot: placed " + totalPlaced + " chest(s) in arena " + arenaKey);
    }

    /**
     * Spawn a short FLAME particle burst above the chest location to make it
     * visible from a distance.  Failures are silently swallowed.
     */
    private void spawnGlowParticles(final World world, final Location chestLoc) {
        try {
            world.spawnParticle(org.bukkit.Particle.FLAME,
                    chestLoc.clone().add(0.5, 1.5, 0.5),
                    12, 0.2, 0.4, 0.2, 0.02);
        } catch (Throwable ignored) {
            // Particle API unavailable or bad location — skip.
        }
    }

    // ── Item parsing ───────────────────────────────────────────────────────────

    /**
     * Parse a {@code MATERIAL:AMOUNT[:ENCHANT:LEVEL...]} loot string into an
     * {@link ItemStack}, mirroring the approach used in {@code KitManager}.
     * Levels above the vanilla cap are applied unsafely (OP loot).
     *
     * @param entry the raw config entry, e.g. {@code "DIAMOND_SWORD:1:sharpness:3"}
     * @return the parsed stack, or {@code null} if the material is unknown
     */
    private ItemStack parseItem(final String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        final String[] parts = entry.split(":");
        final Material material = Material.matchMaterial(parts[0].trim());
        if (material == null) {
            plugin.getLogger().warning("[SkyLoot] Unknown material in tier config: " + parts[0]);
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        final ItemStack item = new ItemStack(material, amount);
        for (int i = 2; i + 1 < parts.length; i += 2) {
            final Enchantment ench = resolveEnchant(parts[i].trim());
            if (ench == null) {
                plugin.getLogger().warning("[SkyLoot] Unknown enchantment: " + parts[i]);
                continue;
            }
            int level = 1;
            try {
                level = Math.max(1, Integer.parseInt(parts[i + 1].trim()));
            } catch (NumberFormatException ignored) {
            }
            item.addUnsafeEnchantment(ench, level);
        }
        return item;
    }

    @SuppressWarnings("deprecation")
    private Enchantment resolveEnchant(final String name) {
        final NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
        try {
            final Enchantment fromRegistry = Registry.ENCHANTMENT.get(key);
            if (fromRegistry != null) {
                return fromRegistry;
            }
        } catch (Throwable ignored) {
        }
        // Legacy fallback
        try {
            return Enchantment.getByKey(key);
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    /**
     * Immutable description of one loot tier loaded from config.
     *
     * @param name      MiniMessage display label
     * @param count     number of chests to place per game
     * @param minHeight blocks above lavaStartY for the lower bound
     * @param maxHeight blocks above lavaStartY for the upper bound
     * @param items     raw loot entries ({@code MATERIAL:AMOUNT[:ENCHANT:LEVEL...]})
     */
    private record Tier(
            String name,
            int count,
            int minHeight,
            int maxHeight,
            List<String> items
    ) {
        private Tier {
            items = Collections.unmodifiableList(new ArrayList<>(items));
        }
    }
}
