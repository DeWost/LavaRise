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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Glowing power-up pickups that spawn during the rising-lava phase. Spawn waves
 * are driven from {@link dev.lavarise.state.ActiveState}'s tick on a config
 * cadence; each pickup is a glowing dropped item tagged with a PDC key. An alive
 * player who grabs one gets a temporary buff (or instant heal); outsiders and
 * spectators can't. Outstanding pickups are cleaned up when the game ends (both
 * the normal path and {@code forceEnd}, via ActiveState's exit cleanup).
 *
 * @author DeWost
 */
public final class PowerUpModule implements Listener {

    private final LavaRisePlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey uidKey;

    private boolean enabled;
    private int intervalSeconds;
    private int perWave;
    private int despawnSeconds;
    private final List<PowerUp> types = new ArrayList<>();

    /** Live power-up entity ids per arena name, for cleanup at game end. */
    private final Map<String, Set<UUID>> active = new ConcurrentHashMap<>();

    public PowerUpModule(LavaRisePlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "powerup_type");
        this.uidKey = new NamespacedKey(plugin, "powerup_uid");
        load();
    }

    /** (Re)load config — safe to call from {@code /lr reload}. */
    public void load() {
        types.clear();
        final ConfigurationSection sec = plugin.getConfig().getConfigurationSection("gameplay.power-ups");
        if (sec == null) {
            enabled = false;
            return;
        }
        enabled = sec.getBoolean("enabled", true);
        intervalSeconds = Math.max(5, sec.getInt("interval", 45));
        perWave = Math.max(1, sec.getInt("per-wave", 1));
        despawnSeconds = Math.max(3, sec.getInt("despawn", 20));
        for (Map<?, ?> raw : sec.getMapList("types")) {
            final String id = String.valueOf(raw.get("id"));
            final Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (id == null || material == null) {
                continue;
            }
            types.add(new PowerUp(
                    id.toLowerCase(Locale.ROOT),
                    material,
                    str(raw.get("name"), id),
                    str(raw.get("effect"), "speed").toLowerCase(Locale.ROOT),
                    toInt(raw.get("duration"), 10),
                    toInt(raw.get("amplifier"), 0)));
        }
    }

    public boolean isEnabled() {
        return enabled && !types.isEmpty();
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    /** Spawn one wave of {@code per-wave} power-ups above the lava and announce it. */
    public void spawnWave(Arena arena, ArenaSession session) {
        if (!isEnabled()) {
            return;
        }
        final ArenaConfig c = arena.getConfig();
        final World world = c.world();
        if (world == null) {
            return;
        }
        final Set<UUID> tracked = active.computeIfAbsent(arena.getName(), k -> ConcurrentHashMap.newKeySet());
        for (int i = 0; i < perWave; i++) {
            final PowerUp type = types.get(ThreadLocalRandom.current().nextInt(types.size()));
            try {
                final int x = ThreadLocalRandom.current().nextInt(c.minX(), c.maxX() + 1);
                final int z = ThreadLocalRandom.current().nextInt(c.minZ(), c.maxZ() + 1);
                final int y = Math.min(c.lavaMaxY() - 1, session.getCurrentLavaY() + 2);
                final Location loc = new Location(world, x + 0.5, y, z + 0.5);
                spawnOne(world, loc, type, tracked);
            } catch (Throwable t) {
                plugin.getLogger().warning("Power-up spawn failed: " + t.getMessage());
            }
        }
        broadcast(session, plugin.getMiniMessage().deserialize(
                "<light_purple>⚡ <white>A power-up appeared in the arena!"));
        for (UUID uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                try {
                    p.playSound(p.getLocation(), "block.beacon.activate", 0.7f, 1.4f);
                } catch (Throwable ignored) {
                    // cosmetic
                }
            }
        }
    }

    private void spawnOne(World world, Location loc, PowerUp type, Set<UUID> tracked) {
        final ItemStack stack = new ItemStack(type.material());
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(plugin.getMiniMessage().deserialize(type.name()));
        // Tag the stack so it is recognizable on pickup, and give it a unique id so
        // two power-ups of the same type never merge into one entity.
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        meta.getPersistentDataContainer().set(uidKey, PersistentDataType.LONG, ThreadLocalRandom.current().nextLong());
        stack.setItemMeta(meta);

        final Item item = world.dropItem(loc, stack);
        item.setGlowing(true);
        item.customName(plugin.getMiniMessage().deserialize(type.name()));
        item.setCustomNameVisible(true);
        item.setVelocity(item.getVelocity().zero());
        item.setCanMobPickup(false);
        tracked.add(item.getUniqueId());

        final UUID entityId = item.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (item.isValid()) {
                item.remove();
            }
            tracked.remove(entityId);
        }, despawnSeconds * 20L);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        final Item entity = event.getItem();
        final ItemStack stack = entity.getItemStack();
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(typeKey, PersistentDataType.STRING)) {
            return;
        }
        // It's a power-up: never let it enter an inventory.
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        final Arena arena = plugin.getGameManager().getArenaForPlayer(player.getUniqueId());
        // Only players alive in a running game may claim power-ups.
        if (arena == null || arena.getSession() == null
                || !arena.getSession().isAlive(player.getUniqueId())) {
            return;
        }

        final String id = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        final PowerUp type = byId(id);
        if (type == null) {
            return;
        }

        entity.remove();
        final Set<UUID> tracked = active.get(arena.getName());
        if (tracked != null) {
            tracked.remove(entity.getUniqueId());
        }
        apply(player, type);

        player.sendMessage(plugin.getMiniMessage().deserialize(
                "<light_purple>⚡ <white>You grabbed <bold>" + type.name() + "</bold>!"));
        try {
            player.playSound(player.getLocation(), "entity.player.levelup", 0.8f, 1.6f);
        } catch (Throwable ignored) {
            // cosmetic
        }
        broadcast(arena.getSession(), plugin.getMiniMessage().deserialize(
                "<light_purple>⚡ <yellow>" + player.getName() + "</yellow> grabbed <light_purple>"
                        + type.name() + "</light_purple>!"));
    }

    private void apply(Player player, PowerUp type) {
        if (type.effect().equals("heal")) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 12.0));
            return;
        }
        final PotionEffectType effect = Registry.EFFECT.get(NamespacedKey.minecraft(type.effect()));
        if (effect != null) {
            player.addPotionEffect(new PotionEffect(effect, type.duration() * 20, type.amplifier(), false, true, true));
        }
    }

    /** Remove all outstanding power-up entities for an arena (called at game end). */
    public void cleanup(ArenaSession session) {
        if (session == null) {
            return;
        }
        final Set<UUID> tracked = active.remove(session.getArena().getName());
        if (tracked == null) {
            return;
        }
        for (UUID id : tracked) {
            final org.bukkit.entity.Entity e = plugin.getServer().getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
    }

    private PowerUp byId(String id) {
        for (PowerUp p : types) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    private void broadcast(ArenaSession session, net.kyori.adventure.text.Component msg) {
        if (session == null) {
            return;
        }
        for (UUID uuid : session.getAllPlayerIds()) {
            final Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.sendMessage(msg);
            }
        }
    }

    private static String str(Object o, String def) {
        return o != null ? String.valueOf(o) : def;
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** A single power-up definition. */
    public record PowerUp(String id, Material material, String name,
                          String effect, int duration, int amplifier) {
    }
}
